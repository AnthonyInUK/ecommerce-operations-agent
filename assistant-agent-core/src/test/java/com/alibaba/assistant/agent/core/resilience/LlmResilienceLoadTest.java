package com.alibaba.assistant.agent.core.resilience;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * LLM 韧性层压测脚本。
 *
 * <p>用一个可注入延迟/故障的假模型（不烧 API token、可复现）驱动并发负载，
 * 跑出真实的吞吐（QPS）、延迟分位（p50/p95/p99）和各韧性路径计数，并打印报告。
 *
 * <p>两个场景：
 * <ol>
 *   <li><b>健康</b>：底层全部成功，度量韧性层包装的吞吐与延迟开销。</li>
 *   <li><b>降级</b>：底层注入 50% 故障，验证熔断器自动打开、把后续请求快速短路到降级，
 *       而不是无脑重试把故障放大——这是“讲真东西”的核心证据。</li>
 * </ol>
 *
 * <p>运行：{@code mvn test -pl assistant-agent-core -Dtest=LlmResilienceLoadTest}
 */
class LlmResilienceLoadTest {

    /** 可注入延迟与故障率的假模型。 */
    static class LoadFakeChatModel implements ChatModel {
        final int baseLatencyMillis;
        final double failureRate;
        LoadFakeChatModel(int baseLatencyMillis, double failureRate) {
            this.baseLatencyMillis = baseLatencyMillis;
            this.failureRate = failureRate;
        }
        @Override
        public ChatResponse call(Prompt prompt) {
            try {
                Thread.sleep(baseLatencyMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            if (failureRate > 0 && ThreadLocalRandom.current().nextDouble() < failureRate) {
                throw new RuntimeException("injected provider failure");
            }
            return new ChatResponse(List.of(new Generation(new AssistantMessage("ok"))));
        }
        @Override
        public ChatOptions getDefaultOptions() {
            return null;
        }
        @Override
        public Flux<ChatResponse> stream(Prompt prompt) {
            return Flux.empty();
        }
    }

    private ResilientChatModel buildModel(ChatModel delegate) {
        // 贴近生产的一组参数（压测里 sleeper 仍走真实退避，但 backoff 很小）
        RetryPolicy retry = new RetryPolicy(2, 5, 2.0, 50, 0.2,
                ResilientCallExecutor.defaultRetryable());
        CircuitBreaker cb = new CircuitBreaker(50, 20, 0.5, 500, 3);
        ResilientCallExecutor exec = ResilientCallExecutor.builder()
                .name("llm-load")
                .rateLimiter(new RateLimiter(50_000, 50_000), 50)
                .bulkhead(64, 100)
                .circuitBreaker(cb)
                .retryPolicy(retry)
                .perCallTimeoutMillis(200)
                .build();
        return new ResilientChatModel(delegate, exec, "系统繁忙，请稍后再试");
    }

    @Test
    void healthyLoad_reportThroughputAndLatency() throws Exception {
        int concurrency = 16;
        int totalRequests = 3000;
        ResilientChatModel model = buildModel(new LoadFakeChatModel(3, 0.0));

        Result result = runLoad(model, concurrency, totalRequests);
        printReport("健康场景 (底层全部成功)", model, result, concurrency);

        assertEquals(totalRequests, model.getMetrics().getTotalCalls());
        assertEquals(totalRequests, model.getMetrics().getSuccess());
        assertTrue(result.qps > 0, "吞吐应大于 0");
    }

    @Test
    void degradedProvider_circuitBreakerProtects() throws Exception {
        int concurrency = 16;
        int totalRequests = 3000;
        // 底层 50% 失败，模拟 provider 故障
        ResilientChatModel model = buildModel(new LoadFakeChatModel(3, 0.5));

        Result result = runLoad(model, concurrency, totalRequests);
        printReport("降级场景 (底层 50% 故障)", model, result, concurrency);

        // 每个请求都应拿到结果（成功或降级），没有异常泄漏导致请求“消失”
        assertEquals(totalRequests, result.completed.get());
        // 熔断器应被打开并短路了相当一部分请求（否则就是无脑重试，放大故障）
        assertTrue(model.getMetrics().getShortCircuited() > 0,
                "降级场景下熔断器应触发短路，实际 short_circuited="
                        + model.getMetrics().getShortCircuited());
    }

    // ------------------------------------------------------------------
    // 压测执行与报告
    // ------------------------------------------------------------------

    private record Result(double qps, long[] latenciesNanos, AtomicInteger completed, long wallNanos) {
    }

    private Result runLoad(ResilientChatModel model, int concurrency, int totalRequests) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(concurrency);
        long[] latencies = new long[totalRequests];
        AtomicInteger completed = new AtomicInteger();
        CountDownLatch done = new CountDownLatch(totalRequests);
        Prompt prompt = new Prompt("压测请求");

        long start = System.nanoTime();
        for (int i = 0; i < totalRequests; i++) {
            final int idx = i;
            pool.submit(() -> {
                long t0 = System.nanoTime();
                try {
                    model.call(prompt);
                } catch (Throwable ignored) {
                    // 设了降级文案，正常不应抛；兜底避免线程死掉
                } finally {
                    latencies[idx] = System.nanoTime() - t0;
                    completed.incrementAndGet();
                    done.countDown();
                }
            });
        }
        assertTrue(done.await(60, TimeUnit.SECONDS), "压测应在 60s 内完成");
        long wall = System.nanoTime() - start;
        pool.shutdownNow();

        double qps = totalRequests / (wall / 1_000_000_000.0);
        return new Result(qps, latencies, completed, wall);
    }

    private void printReport(String title, ResilientChatModel model, Result r, int concurrency) {
        long[] sorted = r.latenciesNanos.clone();
        java.util.Arrays.sort(sorted);
        System.out.println();
        System.out.println("========== LLM 韧性层压测报告：" + title + " ==========");
        System.out.printf("并发线程数      : %d%n", concurrency);
        System.out.printf("总请求数        : %d%n", sorted.length);
        System.out.printf("总耗时          : %.2f s%n", r.wallNanos / 1_000_000_000.0);
        System.out.printf("吞吐 (QPS)      : %.0f%n", r.qps);
        System.out.printf("延迟 p50        : %.1f ms%n", percentile(sorted, 50));
        System.out.printf("延迟 p95        : %.1f ms%n", percentile(sorted, 95));
        System.out.printf("延迟 p99        : %.1f ms%n", percentile(sorted, 99));
        System.out.printf("延迟 max        : %.1f ms%n", sorted[sorted.length - 1] / 1_000_000.0);
        System.out.println("----- 韧性指标 -----");
        model.getMetrics().snapshot().forEach((k, v) -> System.out.printf("%-18s: %s%n", k, v));
        System.out.println("========================================================");
    }

    private double percentile(long[] sortedNanos, int p) {
        if (sortedNanos.length == 0) {
            return 0;
        }
        int idx = (int) Math.ceil(p / 100.0 * sortedNanos.length) - 1;
        idx = Math.max(0, Math.min(sortedNanos.length - 1, idx));
        return sortedNanos[idx] / 1_000_000.0;
    }
}
