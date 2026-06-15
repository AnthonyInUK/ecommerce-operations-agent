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

/**
 * 熔断器：三态状态机（CLOSED / OPEN / HALF_OPEN），基于计数滑动窗口的失败率触发。
 *
 * <p>语义：
 * <ul>
 *   <li><b>CLOSED</b>：正常放行。当最近 {@code windowSize} 次调用达到 {@code minimumCalls} 且
 *       失败率 ≥ {@code failureRateThreshold} 时，切到 OPEN。</li>
 *   <li><b>OPEN</b>：直接拒绝（快速失败），不再打底层 LLM。经过 {@code openDurationMillis} 后
 *       进入 HALF_OPEN 试探。</li>
 *   <li><b>HALF_OPEN</b>：放行最多 {@code halfOpenTrials} 次试探调用；若全部成功则恢复 CLOSED，
 *       任一失败立即回到 OPEN。</li>
 * </ul>
 *
 * <p>为何这样设计：LLM provider 抖动/限流时，盲目重试会放大故障并烧钱。熔断让系统在 provider
 * 不健康时快速失败、走降级，恢复后再用少量试探流量探活，避免一恢复就被打垮。
 *
 * <p>方法用 synchronized 保证状态转换的原子性。Agent 的 LLM 调用并发量不高，锁开销可忽略，
 * 换来的是状态机推理的简单与正确。
 */
public class CircuitBreaker {

    public enum State {
        CLOSED, OPEN, HALF_OPEN
    }

    private final int windowSize;
    private final int minimumCalls;
    private final double failureRateThreshold;
    private final long openDurationMillis;
    private final int halfOpenTrials;

    // CLOSED 态的计数滑动窗口（环形缓冲，true=失败）
    private final boolean[] window;
    private int windowIndex = 0;
    private int windowCount = 0;
    private int failureCount = 0;

    private State state = State.CLOSED;
    private long openedAtMillis = 0L;

    // HALF_OPEN 态的试探计数
    private int halfOpenPermitsLeft = 0;
    private int halfOpenResults = 0;
    private int halfOpenFailures = 0;

    private final java.util.function.LongSupplier clock;

    public CircuitBreaker(int windowSize, int minimumCalls, double failureRateThreshold,
                          long openDurationMillis, int halfOpenTrials) {
        this(windowSize, minimumCalls, failureRateThreshold, openDurationMillis, halfOpenTrials,
                System::currentTimeMillis);
    }

    /** 测试用构造：可注入虚拟时钟，避免 sleep。 */
    CircuitBreaker(int windowSize, int minimumCalls, double failureRateThreshold,
                   long openDurationMillis, int halfOpenTrials, java.util.function.LongSupplier clock) {
        if (windowSize <= 0) {
            throw new IllegalArgumentException("windowSize must be > 0");
        }
        this.windowSize = windowSize;
        this.minimumCalls = Math.max(1, minimumCalls);
        this.failureRateThreshold = failureRateThreshold;
        this.openDurationMillis = openDurationMillis;
        this.halfOpenTrials = Math.max(1, halfOpenTrials);
        this.window = new boolean[windowSize];
        this.clock = clock;
    }

    /**
     * 是否允许本次调用通过。OPEN 且未到试探时间返回 false（短路）。
     */
    public synchronized boolean tryAcquire() {
        if (state == State.OPEN) {
            if (clock.getAsLong() - openedAtMillis >= openDurationMillis) {
                toHalfOpen();
            } else {
                return false;
            }
        }
        if (state == State.HALF_OPEN) {
            if (halfOpenPermitsLeft <= 0) {
                return false; // 试探名额用完，其余请求继续短路
            }
            halfOpenPermitsLeft--;
            return true;
        }
        return true; // CLOSED
    }

    public synchronized void onSuccess() {
        if (state == State.HALF_OPEN) {
            recordHalfOpen(false);
        } else if (state == State.CLOSED) {
            recordWindow(false);
        }
    }

    public synchronized void onFailure() {
        if (state == State.HALF_OPEN) {
            recordHalfOpen(true);
        } else if (state == State.CLOSED) {
            recordWindow(true);
            evaluateTrip();
        }
    }

    public synchronized State getState() {
        // 读取时若 OPEN 已到期，对外反映为 HALF_OPEN 的可试探状态
        if (state == State.OPEN && clock.getAsLong() - openedAtMillis >= openDurationMillis) {
            toHalfOpen();
        }
        return state;
    }

    private void recordWindow(boolean failure) {
        if (windowCount == windowSize && window[windowIndex]) {
            failureCount--; // 即将被覆盖的旧槽位是失败，先扣回
        }
        window[windowIndex] = failure;
        if (failure) {
            failureCount++;
        }
        windowIndex = (windowIndex + 1) % windowSize;
        if (windowCount < windowSize) {
            windowCount++;
        }
    }

    private void evaluateTrip() {
        if (windowCount >= minimumCalls) {
            double rate = (double) failureCount / windowCount;
            if (rate >= failureRateThreshold) {
                toOpen();
            }
        }
    }

    private void recordHalfOpen(boolean failure) {
        halfOpenResults++;
        if (failure) {
            halfOpenFailures++;
            toOpen(); // 试探期任一失败立刻回到 OPEN
            return;
        }
        if (halfOpenResults >= halfOpenTrials) {
            if (halfOpenFailures == 0) {
                toClosed();
            } else {
                toOpen();
            }
        }
    }

    private void toOpen() {
        state = State.OPEN;
        openedAtMillis = clock.getAsLong();
        halfOpenPermitsLeft = 0;
        halfOpenResults = 0;
        halfOpenFailures = 0;
    }

    private void toHalfOpen() {
        state = State.HALF_OPEN;
        halfOpenPermitsLeft = halfOpenTrials;
        halfOpenResults = 0;
        halfOpenFailures = 0;
    }

    private void toClosed() {
        state = State.CLOSED;
        windowIndex = 0;
        windowCount = 0;
        failureCount = 0;
        java.util.Arrays.fill(window, false);
    }
}
