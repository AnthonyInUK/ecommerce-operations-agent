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
import java.util.function.Predicate;

/**
 * 重试策略：指数退避 + 抖动（jitter）。
 *
 * <p>为何要退避+抖动：LLM provider 抖动时立刻重试往往还是失败，且大量客户端同步重试会形成
 * “重试风暴”把 provider 二次打垮。指数退避拉开间隔，抖动打散并发重试的时间点。
 *
 * <p>只对“可重试”的异常重试（如超时、5xx、限流），对确定性错误（如参数非法）不重试，避免做无用功。
 */
public class RetryPolicy {

    private final int maxAttempts;
    private final long initialBackoffMillis;
    private final double multiplier;
    private final long maxBackoffMillis;
    private final double jitterFactor;
    private final Predicate<Throwable> retryable;
    private final Sleeper sleeper;

    /** 可注入的 sleep，测试里替换为无等待，避免真实拖慢测试。 */
    interface Sleeper {
        void sleep(long millis) throws InterruptedException;
    }

    public RetryPolicy(int maxAttempts, long initialBackoffMillis, double multiplier,
                       long maxBackoffMillis, double jitterFactor, Predicate<Throwable> retryable) {
        this(maxAttempts, initialBackoffMillis, multiplier, maxBackoffMillis, jitterFactor, retryable,
                Thread::sleep);
    }

    RetryPolicy(int maxAttempts, long initialBackoffMillis, double multiplier, long maxBackoffMillis,
                double jitterFactor, Predicate<Throwable> retryable, Sleeper sleeper) {
        this.maxAttempts = Math.max(1, maxAttempts);
        this.initialBackoffMillis = Math.max(0, initialBackoffMillis);
        this.multiplier = multiplier <= 0 ? 1.0 : multiplier;
        this.maxBackoffMillis = maxBackoffMillis;
        this.jitterFactor = Math.max(0, Math.min(1.0, jitterFactor));
        this.retryable = retryable == null ? t -> true : retryable;
        this.sleeper = sleeper;
    }

    /**
     * 执行带重试的调用。每次尝试调用 onAttempt（用于指标统计：记录重试次数）。
     *
     * @param call      实际业务调用
     * @param onRetry   每次“准备重试”前回调一次（attempt 从 1 开始，表示第几次重试）
     * @return 调用结果
     * @throws Exception 最后一次尝试的异常（或不可重试异常）
     */
    public <T> T execute(Callable<T> call, java.util.function.IntConsumer onRetry) throws Exception {
        Throwable last = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return call.call();
            } catch (Exception e) {
                last = e;
                boolean canRetry = attempt < maxAttempts && retryable.test(e);
                if (!canRetry) {
                    throw e;
                }
                if (onRetry != null) {
                    onRetry.accept(attempt);
                }
                sleeper.sleep(backoffFor(attempt));
            }
        }
        // 理论上不会到这；保险起见
        if (last instanceof Exception ex) {
            throw ex;
        }
        throw new IllegalStateException("retry exhausted", last);
    }

    long backoffFor(int attempt) {
        double base = initialBackoffMillis * Math.pow(multiplier, attempt - 1);
        if (maxBackoffMillis > 0) {
            base = Math.min(base, maxBackoffMillis);
        }
        // 对称抖动：base * (1 ± jitterFactor*rand)
        double jitter = base * jitterFactor * (ThreadLocalRandomHolder.nextDouble() * 2 - 1);
        long result = (long) Math.max(0, base + jitter);
        return result;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    // 包一层便于测试时不直接依赖静态随机
    static final class ThreadLocalRandomHolder {
        static double nextDouble() {
            return java.util.concurrent.ThreadLocalRandom.current().nextDouble();
        }
    }
}
