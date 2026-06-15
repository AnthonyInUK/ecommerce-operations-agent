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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.LongAdder;

/**
 * 韧性层运行指标。用 LongAdder 做无锁累加，适合高并发计数。
 *
 * <p>这些计数是压测“出真实数字”的数据来源，也是线上可观测性的基础：
 * 一眼看出有多少请求被限流挡掉、多少被熔断短路、重试了多少次、超时多少。
 */
public class ResilienceMetrics {

    private final LongAdder totalCalls = new LongAdder();
    private final LongAdder success = new LongAdder();
    private final LongAdder failure = new LongAdder();
    private final LongAdder timeout = new LongAdder();
    private final LongAdder retries = new LongAdder();
    private final LongAdder shortCircuited = new LongAdder();
    private final LongAdder rateLimited = new LongAdder();
    private final LongAdder bulkheadRejected = new LongAdder();
    private final LongAdder fallback = new LongAdder();

    void incTotal() { totalCalls.increment(); }
    void incSuccess() { success.increment(); }
    void incFailure() { failure.increment(); }
    void incTimeout() { timeout.increment(); }
    void incRetry() { retries.increment(); }
    void incShortCircuited() { shortCircuited.increment(); }
    void incRateLimited() { rateLimited.increment(); }
    void incBulkheadRejected() { bulkheadRejected.increment(); }
    void incFallback() { fallback.increment(); }

    public long getTotalCalls() { return totalCalls.sum(); }
    public long getSuccess() { return success.sum(); }
    public long getFailure() { return failure.sum(); }
    public long getTimeout() { return timeout.sum(); }
    public long getRetries() { return retries.sum(); }
    public long getShortCircuited() { return shortCircuited.sum(); }
    public long getRateLimited() { return rateLimited.sum(); }
    public long getBulkheadRejected() { return bulkheadRejected.sum(); }
    public long getFallback() { return fallback.sum(); }

    public Map<String, Object> snapshot() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("total_calls", getTotalCalls());
        m.put("success", getSuccess());
        m.put("failure", getFailure());
        m.put("timeout", getTimeout());
        m.put("retries", getRetries());
        m.put("short_circuited", getShortCircuited());
        m.put("rate_limited", getRateLimited());
        m.put("bulkhead_rejected", getBulkheadRejected());
        m.put("fallback", getFallback());
        return m;
    }
}
