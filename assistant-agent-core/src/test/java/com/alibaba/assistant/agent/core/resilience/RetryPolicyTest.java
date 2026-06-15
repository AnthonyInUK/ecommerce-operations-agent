package com.alibaba.assistant.agent.core.resilience;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 重试策略测试。注入无等待的 sleeper，避免真实退避拖慢测试。
 */
class RetryPolicyTest {

    private RetryPolicy policy(int maxAttempts) {
        // 退避参数不影响逻辑断言；sleeper 置空跳过等待
        return new RetryPolicy(maxAttempts, 10, 2.0, 1000, 0.1, t -> true, millis -> { });
    }

    @Test
    void succeedsFirstTry_noRetry() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        AtomicInteger retries = new AtomicInteger();

        String result = policy(3).execute(() -> {
            calls.incrementAndGet();
            return "ok";
        }, attempt -> retries.incrementAndGet());

        assertEquals("ok", result);
        assertEquals(1, calls.get());
        assertEquals(0, retries.get());
    }

    @Test
    void retriesUntilSuccess() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        AtomicInteger retries = new AtomicInteger();

        String result = policy(3).execute(() -> {
            if (calls.incrementAndGet() < 3) {
                throw new RuntimeException("transient");
            }
            return "recovered";
        }, attempt -> retries.incrementAndGet());

        assertEquals("recovered", result);
        assertEquals(3, calls.get());
        assertEquals(2, retries.get(), "前两次失败各触发一次重试");
    }

    @Test
    void exhaustsAttemptsThenThrows() {
        AtomicInteger calls = new AtomicInteger();

        Exception ex = assertThrows(Exception.class, () ->
                policy(3).execute(() -> {
                    calls.incrementAndGet();
                    throw new RuntimeException("always fails");
                }, null));

        assertEquals("always fails", ex.getMessage());
        assertEquals(3, calls.get(), "最多尝试 3 次");
    }

    @Test
    void nonRetryableException_notRetried() {
        AtomicInteger calls = new AtomicInteger();
        RetryPolicy p = new RetryPolicy(3, 10, 2.0, 1000, 0.0,
                t -> !(t instanceof IllegalArgumentException), millis -> { });

        assertThrows(IllegalArgumentException.class, () ->
                p.execute(() -> {
                    calls.incrementAndGet();
                    throw new IllegalArgumentException("bad input");
                }, null));

        assertEquals(1, calls.get(), "不可重试异常只尝试一次");
    }

    @Test
    void backoffGrowsExponentiallyAndIsCapped() {
        RetryPolicy p = new RetryPolicy(10, 100, 2.0, 500, 0.0, t -> true, millis -> { });
        assertEquals(100, p.backoffFor(1));
        assertEquals(200, p.backoffFor(2));
        assertEquals(400, p.backoffFor(3));
        assertEquals(500, p.backoffFor(4), "超过上限被封顶到 500");
        assertEquals(500, p.backoffFor(5));
    }
}
