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
 * 韧性层异常基类及其子类型。当没有配置降级返回值（fallback）时，被韧性层挡下的请求会抛出对应子类。
 */
public class ResilienceException extends RuntimeException {

    public ResilienceException(String message) {
        super(message);
    }

    public ResilienceException(String message, Throwable cause) {
        super(message, cause);
    }

    /** 熔断器处于 OPEN（或试探名额已满），请求被快速失败。 */
    public static class CircuitOpen extends ResilienceException {
        public CircuitOpen(String message) {
            super(message);
        }
    }

    /** 超出限流速率，且在最大等待时间内未取到令牌。 */
    public static class RateLimited extends ResilienceException {
        public RateLimited(String message) {
            super(message);
        }
    }

    /** 并发在途调用已达上限（隔板/Bulkhead 满）。 */
    public static class BulkheadFull extends ResilienceException {
        public BulkheadFull(String message) {
            super(message);
        }
    }

    /** 单次调用超过时间预算。 */
    public static class Timeout extends ResilienceException {
        public Timeout(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
