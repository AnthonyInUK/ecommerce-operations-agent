/*
 * Copyright 2024-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.assistant.agent.core.resilience;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * 韧性调用执行器：把限流、并发隔离、熔断、重试、超时按固定顺序组合起来包住一次外部调用。
 *
 * <p><b>编排顺序（由外到内）</b>，以及为什么是这个顺序：
 * <ol>
 *   <li><b>RateLimiter（限流）</b>：最先做准入控制。挡掉就最便宜，直接保护配额与成本。</li>
 *   <li><b>Bulkhead（并发上限）</b>：限制在途调用数，防止慢调用堆积耗尽线程/内存。</li>
 *   <li><b>CircuitBreaker（熔断）</b>：provider 不健康时直接快速失败，连尝试都不做。</li>
 *   <li><b>Retry（重试）</b>：对“通过熔断”的一次逻辑调用，做带退避的瞬时错误重试。</li>
 *   <li><b>TimeLimiter（超时）</b>：最内层，给每一次尝试套一个时间预算。</li>
 * </ol>
 *
 * <p>注意：重试在熔断<b>内层</b>——即“一次逻辑调用（含若干次重试）= 熔断器的一个样本”。
 * 这样熔断失败率统计的是“最终失败的逻辑调用占比”，且 provider 故障时，少量逻辑调用很快把熔断打开，
 * 之后连重试都不再发生（彻底止血），而不是无脑重试风暴。
 *
 * <p>所有被挡下/最终失败的请求：若提供了 fallback，返回降级值（计入 fallback 指标）；否则抛出对应异常。
 */
public class ResilientCallExecutor implements AutoCloseable {

    private final RateLimiter rateLimiter;
    private final long rateLimiterWaitMillis;

    private final Semaphore bulkhead;
    private final long bulkheadWaitMillis;

    private final CircuitBreaker circuitBreaker;
    private final RetryPolicy retryPolicy;

    private final long perCallTimeoutMillis;
    private final ExecutorService timeoutExecutor;
    private final boolean ownsExecutor;

    private final ResilienceMetrics metrics;
    private final String name;

    private ResilientCallExecutor(Builder b) {
        this.name = b.name;
        this.rateLimiter = b.rateLimiter;
        this.rateLimiterWaitMillis = b.rateLimiterWaitMillis;
        this.bulkhead = b.maxConcurrentCalls > 0 ? new Semaphore(b.maxConcurrentCalls, true) : null;
        this.bulkheadWaitMillis = b.bulkheadWaitMillis;
        this.circuitBreaker = b.circuitBreaker;
        this.retryPolicy = b.retryPolicy;
        this.perCallTimeoutMillis = b.perCallTimeoutMillis;
        this.metrics = b.metrics != null ? b.metrics : new ResilienceMetrics();
        if (this.perCallTimeoutMillis > 0) {
            if (b.timeoutExecutor != null) {
                this.timeoutExecutor = b.timeoutExecutor;
                this.ownsExecutor = false;
            } else {
                this.timeoutExecutor = Executors.newCachedThreadPool(daemonFactory(this.name));
                this.ownsExecutor = true;
            }
        } else {
            this.timeoutExecutor = null;
            this.ownsExecutor = false;
        }
    }

    /**
     * 执行一次受保护的调用。
     *
     * @param supplier 实际业务调用
     * @param fallback 降级值提供者，可为 null（为 null 时被挡下/失败会抛异常）
     */
    public <T> T execute(Callable<T> supplier, Supplier<T> fallback) {
        metrics.incTotal();

        // 1. 限流准入
        if (rateLimiter != null) {
            boolean acquired;
            try {
                acquired = rateLimiter.acquire(rateLimiterWaitMillis);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                acquired = false;
            }
            if (!acquired) {
                metrics.incRateLimited();
                return fallbackOrThrow(fallback, new ResilienceException.RateLimited(
                        tag() + "rate limit exceeded"));
            }
        }

        // 2. 并发隔离
        boolean bulkheadAcquired = false;
        if (bulkhead != null) {
            try {
                bulkheadAcquired = bulkhead.tryAcquire(bulkheadWaitMillis, TimeUnit.MILLISECONDS);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
            if (!bulkheadAcquired) {
                metrics.incBulkheadRejected();
                return fallbackOrThrow(fallback, new ResilienceException.BulkheadFull(
                        tag() + "bulkhead is full"));
            }
        }

        try {
            // 3. 熔断准入
            if (circuitBreaker != null && !circuitBreaker.tryAcquire()) {
                metrics.incShortCircuited();
                return fallbackOrThrow(fallback, new ResilienceException.CircuitOpen(
                        tag() + "circuit is OPEN"));
            }

            try {
                // 4 + 5. 重试包住「带超时的单次尝试」
                T result;
                if (retryPolicy != null) {
                    result = retryPolicy.execute(() -> callWithTimeout(supplier), attempt -> metrics.incRetry());
                } else {
                    result = callWithTimeout(supplier);
                }
                if (circuitBreaker != null) {
                    circuitBreaker.onSuccess();
                }
                metrics.incSuccess();
                return result;
            } catch (Exception e) {
                if (circuitBreaker != null) {
                    circuitBreaker.onFailure();
                }
                if (e instanceof ResilienceException.Timeout) {
                    metrics.incTimeout();
                }
                metrics.incFailure();
                if (fallback != null) {
                    metrics.incFallback();
                    return fallback.get();
                }
                if (e instanceof RuntimeException re) {
                    throw re;
                }
                throw new ResilienceException(tag() + "call failed", e);
            }
        } finally {
            if (bulkheadAcquired) {
                bulkhead.release();
            }
        }
    }

    private <T> T callWithTimeout(Callable<T> supplier) throws Exception {
        if (timeoutExecutor == null) {
            return supplier.call();
        }
        Future<T> future = timeoutExecutor.submit(supplier);
        try {
            return future.get(perCallTimeoutMillis, TimeUnit.MILLISECONDS);
        } catch (TimeoutException te) {
            future.cancel(true);
            throw new ResilienceException.Timeout(tag() + "call timed out after "
                    + perCallTimeoutMillis + "ms", te);
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause() != null ? ee.getCause() : ee;
            if (cause instanceof Exception ex) {
                throw ex;
            }
            throw new ResilienceException(tag() + "call failed", cause);
        }
    }

    private <T> T fallbackOrThrow(Supplier<T> fallback, RuntimeException ex) {
        if (fallback != null) {
            metrics.incFallback();
            return fallback.get();
        }
        throw ex;
    }

    public ResilienceMetrics getMetrics() {
        return metrics;
    }

    public CircuitBreaker getCircuitBreaker() {
        return circuitBreaker;
    }

    @Override
    public void close() {
        if (ownsExecutor && timeoutExecutor != null) {
            timeoutExecutor.shutdownNow();
        }
    }

    private String tag() {
        return name == null ? "" : "[" + name + "] ";
    }

    private static java.util.concurrent.ThreadFactory daemonFactory(String name) {
        AtomicLong seq = new AtomicLong();
        String prefix = (name == null ? "resilient" : name) + "-timeout-";
        return r -> {
            Thread t = new Thread(r, prefix + seq.incrementAndGet());
            t.setDaemon(true);
            return t;
        };
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String name = "llm";
        private RateLimiter rateLimiter;
        private long rateLimiterWaitMillis = 0;
        private int maxConcurrentCalls = 0;
        private long bulkheadWaitMillis = 0;
        private CircuitBreaker circuitBreaker;
        private RetryPolicy retryPolicy;
        private long perCallTimeoutMillis = 0;
        private ExecutorService timeoutExecutor;
        private ResilienceMetrics metrics;

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder rateLimiter(RateLimiter rateLimiter, long maxWaitMillis) {
            this.rateLimiter = rateLimiter;
            this.rateLimiterWaitMillis = maxWaitMillis;
            return this;
        }

        public Builder bulkhead(int maxConcurrentCalls, long maxWaitMillis) {
            this.maxConcurrentCalls = maxConcurrentCalls;
            this.bulkheadWaitMillis = maxWaitMillis;
            return this;
        }

        public Builder circuitBreaker(CircuitBreaker circuitBreaker) {
            this.circuitBreaker = circuitBreaker;
            return this;
        }

        public Builder retryPolicy(RetryPolicy retryPolicy) {
            this.retryPolicy = retryPolicy;
            return this;
        }

        public Builder perCallTimeoutMillis(long perCallTimeoutMillis) {
            this.perCallTimeoutMillis = perCallTimeoutMillis;
            return this;
        }

        public Builder timeoutExecutor(ExecutorService executor) {
            this.timeoutExecutor = executor;
            return this;
        }

        public Builder metrics(ResilienceMetrics metrics) {
            this.metrics = metrics;
            return this;
        }

        public ResilientCallExecutor build() {
            return new ResilientCallExecutor(this);
        }
    }

    /** 便捷工厂：默认可重试谓词（超时与运行时异常可重试，参数类错误不重试）。 */
    public static Predicate<Throwable> defaultRetryable() {
        return t -> !(t instanceof IllegalArgumentException);
    }
}
