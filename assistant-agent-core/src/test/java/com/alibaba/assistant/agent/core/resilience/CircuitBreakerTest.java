package com.alibaba.assistant.agent.core.resilience;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 熔断器状态机测试。用注入的虚拟时钟驱动时间，避免真实 sleep。
 */
class CircuitBreakerTest {

    private AtomicLong clock;

    private CircuitBreaker newBreaker() {
        clock = new AtomicLong(0);
        // 窗口10 / 最少5次 / 失败率50% / OPEN 持续1000ms / 半开试探2次
        return new CircuitBreaker(10, 5, 0.5, 1000, 2, clock::get);
    }

    @Test
    void startsClosedAndAllows() {
        CircuitBreaker cb = newBreaker();
        assertEquals(CircuitBreaker.State.CLOSED, cb.getState());
        assertTrue(cb.tryAcquire());
    }

    @Test
    void belowMinimumCalls_doesNotTrip() {
        CircuitBreaker cb = newBreaker();
        // 4 次失败，未达最少 5 次样本，不熔断
        for (int i = 0; i < 4; i++) {
            cb.tryAcquire();
            cb.onFailure();
        }
        assertEquals(CircuitBreaker.State.CLOSED, cb.getState());
    }

    @Test
    void failureRateOverThreshold_tripsToOpen() {
        CircuitBreaker cb = newBreaker();
        cb.tryAcquire(); cb.onSuccess();
        cb.tryAcquire(); cb.onSuccess();
        cb.tryAcquire(); cb.onFailure();
        cb.tryAcquire(); cb.onFailure();
        cb.tryAcquire(); cb.onFailure(); // 5 次里 3 次失败 = 60% >= 50%

        assertEquals(CircuitBreaker.State.OPEN, cb.getState());
        assertFalse(cb.tryAcquire(), "OPEN 态应短路");
    }

    @Test
    void afterOpenDuration_movesToHalfOpenAndAllowsTrials() {
        CircuitBreaker cb = trippedBreaker();

        clock.addAndGet(1000); // 到达 OPEN 持续时间
        assertTrue(cb.tryAcquire(), "应进入 HALF_OPEN 并放行第 1 次试探");
        assertEquals(CircuitBreaker.State.HALF_OPEN, cb.getState());
        assertTrue(cb.tryAcquire(), "放行第 2 次试探");
        assertFalse(cb.tryAcquire(), "试探名额用尽后继续短路");
    }

    @Test
    void halfOpen_allTrialsSucceed_closesCircuit() {
        CircuitBreaker cb = trippedBreaker();
        clock.addAndGet(1000);

        cb.tryAcquire(); cb.onSuccess();
        cb.tryAcquire(); cb.onSuccess(); // 2 次试探全成功

        assertEquals(CircuitBreaker.State.CLOSED, cb.getState());
        assertTrue(cb.tryAcquire());
    }

    @Test
    void halfOpen_anyTrialFails_reopensCircuit() {
        CircuitBreaker cb = trippedBreaker();
        clock.addAndGet(1000);

        cb.tryAcquire();
        cb.onFailure(); // 试探失败立即回到 OPEN

        assertEquals(CircuitBreaker.State.OPEN, cb.getState());
        assertFalse(cb.tryAcquire());
    }

    private CircuitBreaker trippedBreaker() {
        CircuitBreaker cb = newBreaker();
        cb.tryAcquire(); cb.onSuccess();
        cb.tryAcquire(); cb.onSuccess();
        cb.tryAcquire(); cb.onFailure();
        cb.tryAcquire(); cb.onFailure();
        cb.tryAcquire(); cb.onFailure();
        assertEquals(CircuitBreaker.State.OPEN, cb.getState());
        return cb;
    }
}
