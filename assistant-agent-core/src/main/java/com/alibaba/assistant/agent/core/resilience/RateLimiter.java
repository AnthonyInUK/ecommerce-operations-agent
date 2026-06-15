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
 * 令牌桶限流器：按固定速率补充令牌，请求取到令牌才放行。
 *
 * <p>为何需要：LLM provider 有每分钟请求数/Token 配额。客户端侧主动限流，既避免触发 provider 的
 * 429（被动限流体验更差），又能直接控制调用成本。令牌桶允许一定突发（桶容量），比固定窗口更平滑。
 *
 * <p>{@link #tryAcquire()} 非阻塞；{@link #acquire(long)} 最多等待给定毫秒。
 */
public class RateLimiter {

    private final double permitsPerSecond;
    private final double maxTokens;
    private double tokens;
    private long lastRefillNanos;

    private final java.util.function.LongSupplier nanoClock;

    public RateLimiter(double permitsPerSecond, int burst) {
        this(permitsPerSecond, burst, System::nanoTime);
    }

    RateLimiter(double permitsPerSecond, int burst, java.util.function.LongSupplier nanoClock) {
        if (permitsPerSecond <= 0) {
            throw new IllegalArgumentException("permitsPerSecond must be > 0");
        }
        this.permitsPerSecond = permitsPerSecond;
        this.maxTokens = Math.max(1, burst);
        this.tokens = this.maxTokens;
        this.nanoClock = nanoClock;
        this.lastRefillNanos = nanoClock.getAsLong();
    }

    /** 非阻塞获取一个令牌；成功返回 true。 */
    public synchronized boolean tryAcquire() {
        refill();
        if (tokens >= 1.0) {
            tokens -= 1.0;
            return true;
        }
        return false;
    }

    /**
     * 在 maxWaitMillis 内尝试获取令牌；获取到返回 true，超时返回 false。
     * 用轮询 + 短 sleep 实现，逻辑简单且对低频 LLM 调用足够。
     */
    public boolean acquire(long maxWaitMillis) throws InterruptedException {
        long deadline = System.nanoTime() + maxWaitMillis * 1_000_000L;
        while (true) {
            if (tryAcquire()) {
                return true;
            }
            if (System.nanoTime() >= deadline) {
                return false;
            }
            Thread.sleep(2);
        }
    }

    private void refill() {
        long now = nanoClock.getAsLong();
        double elapsedSeconds = (now - lastRefillNanos) / 1_000_000_000.0;
        if (elapsedSeconds > 0) {
            tokens = Math.min(maxTokens, tokens + elapsedSeconds * permitsPerSecond);
            lastRefillNanos = now;
        }
    }
}
