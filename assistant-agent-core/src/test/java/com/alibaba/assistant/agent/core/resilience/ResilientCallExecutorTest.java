package com.alibaba.assistant.agent.core.resilience;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 组合执行器测试：验证限流 / 并发隔离 / 熔断 / 重试 / 超时 / 降级各路径与指标统计。
 */
class ResilientCallExecutorTest {

    @Test
    void success_passesThroughAndCountsSuccess() {
        ResilientCallExecutor exec = ResilientCallExecutor.builder().name("t").build();
        String r = exec.execute(() -> "ok", null);
        assertEquals("ok", r);
        assertEquals(1, exec.getMetrics().getSuccess());
        assertEquals(0, exec.getMetrics().getFailure());
    }

    @Test
    void failureWithoutFallback_throws() {
        ResilientCallExecutor exec = ResilientCallExecutor.builder().build();
        assertThrows(RuntimeException.class, () ->
                exec.execute(() -> { throw new RuntimeException("boom"); }, null));
        assertEquals(1, exec.getMetrics().getFailure());
    }

    @Test
    void failureWithFallback_returnsFallback() {
        ResilientCallExecutor exec = ResilientCallExecutor.builder().build();
        String r = exec.execute(() -> { throw new RuntimeException("boom"); }, () -> "degraded");
        assertEquals("degraded", r);
        assertEquals(1, exec.getMetrics().getFallback());
        assertEquals(1, exec.getMetrics().getFailure());
    }

    @Test
    void timeout_isCountedAndFallsBack() {
        try (ResilientCallExecutor exec = ResilientCallExecutor.builder()
                .perCallTimeoutMillis(50)
                .build()) {
            String r = exec.execute(() -> {
                Thread.sleep(300);
                return "too-slow";
            }, () -> "timeout-fallback");
            assertEquals("timeout-fallback", r);
            assertEquals(1, exec.getMetrics().getTimeout());
        }
    }

    @Test
    void retry_recoversAndCountsRetries() {
        // 无等待的重试策略（同包可用包级构造）
        RetryPolicy retry = new RetryPolicy(3, 5, 2.0, 100, 0.0, t -> true, millis -> { });
        ResilientCallExecutor exec = ResilientCallExecutor.builder()
                .retryPolicy(retry)
                .build();

        AtomicInteger calls = new AtomicInteger();
        String r = exec.execute(() -> {
            if (calls.incrementAndGet() < 3) {
                throw new RuntimeException("transient");
            }
            return "recovered";
        }, null);

        assertEquals("recovered", r);
        assertEquals(2, exec.getMetrics().getRetries());
        assertEquals(1, exec.getMetrics().getSuccess());
    }

    @Test
    void circuitBreaker_opensAndShortCircuitsWithoutCallingSupplier() {
        CircuitBreaker cb = new CircuitBreaker(10, 2, 0.5, 60_000, 1);
        ResilientCallExecutor exec = ResilientCallExecutor.builder()
                .circuitBreaker(cb)
                .build();

        AtomicInteger supplierCalls = new AtomicInteger();
        // 触发两次失败把熔断打开
        for (int i = 0; i < 2; i++) {
            exec.execute(() -> {
                supplierCalls.incrementAndGet();
                throw new RuntimeException("fail");
            }, () -> "fb");
        }
        assertEquals(CircuitBreaker.State.OPEN, cb.getState());

        int before = supplierCalls.get();
        String r = exec.execute(() -> {
            supplierCalls.incrementAndGet();
            return "should-not-run";
        }, () -> "short-circuit-fallback");

        assertEquals("short-circuit-fallback", r);
        assertEquals(before, supplierCalls.get(), "OPEN 态不应再调用底层");
        assertEquals(1, exec.getMetrics().getShortCircuited());
    }

    @Test
    void rateLimiter_rejectsOverBudget() {
        RateLimiter limiter = new RateLimiter(1, 1); // 1/s，桶 1
        ResilientCallExecutor exec = ResilientCallExecutor.builder()
                .rateLimiter(limiter, 0)
                .build();

        assertEquals("ok", exec.execute(() -> "ok", () -> "fb"));
        // 紧接着第二次：令牌已耗尽，0 等待 → 被限流
        assertEquals("fb", exec.execute(() -> "ok", () -> "fb"));
        assertEquals(1, exec.getMetrics().getRateLimited());
    }

    @Test
    void bulkhead_rejectsWhenFull() throws Exception {
        ResilientCallExecutor exec = ResilientCallExecutor.builder()
                .bulkhead(1, 0)
                .build();

        CountDownLatch holding = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Thread blocker = new Thread(() -> exec.execute(() -> {
            holding.countDown();
            release.await();
            return "held";
        }, () -> "fb"));
        blocker.start();

        assertTrue(holding.await(2, TimeUnit.SECONDS), "后台调用应已占住唯一名额");

        // 主线程再调：名额已满，0 等待 → 被隔板拒绝
        String r = exec.execute(() -> "second", () -> "bulkhead-fallback");
        assertEquals("bulkhead-fallback", r);
        assertEquals(1, exec.getMetrics().getBulkheadRejected());

        release.countDown();
        blocker.join(2000);
    }
}
