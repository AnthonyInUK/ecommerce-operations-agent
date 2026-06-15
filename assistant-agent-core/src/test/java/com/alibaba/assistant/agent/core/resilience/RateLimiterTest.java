package com.alibaba.assistant.agent.core.resilience;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 令牌桶限流器测试。用虚拟纳秒时钟驱动补充速率，避免真实等待。
 */
class RateLimiterTest {

    @Test
    void burstAllowsUpToBucketSize_thenRejects() {
        AtomicLong nanos = new AtomicLong(0);
        // 10 permits/s，桶容量 5：初始可连取 5 个
        RateLimiter limiter = new RateLimiter(10, 5, nanos::get);

        for (int i = 0; i < 5; i++) {
            assertTrue(limiter.tryAcquire(), "前 5 个令牌应可取（突发额度）");
        }
        assertFalse(limiter.tryAcquire(), "桶空后立即拒绝");
    }

    @Test
    void tokensRefillOverTime() {
        AtomicLong nanos = new AtomicLong(0);
        RateLimiter limiter = new RateLimiter(10, 5, nanos::get); // 10/s = 每 100ms 补 1 个

        for (int i = 0; i < 5; i++) {
            assertTrue(limiter.tryAcquire());
        }
        assertFalse(limiter.tryAcquire());

        // 前进 300ms → 应补回约 3 个令牌
        nanos.addAndGet(300_000_000L);
        assertTrue(limiter.tryAcquire());
        assertTrue(limiter.tryAcquire());
        assertTrue(limiter.tryAcquire());
        assertFalse(limiter.tryAcquire(), "只补了 3 个，第 4 个应被拒");
    }

    @Test
    void refillIsCappedAtBucketSize() {
        AtomicLong nanos = new AtomicLong(0);
        RateLimiter limiter = new RateLimiter(10, 5, nanos::get);

        // 不取令牌，长时间不动 → 补充也不会超过桶容量 5
        nanos.addAndGet(10_000_000_000L); // 10s
        for (int i = 0; i < 5; i++) {
            assertTrue(limiter.tryAcquire());
        }
        assertFalse(limiter.tryAcquire(), "桶容量封顶在 5，不会无限累积");
    }
}
