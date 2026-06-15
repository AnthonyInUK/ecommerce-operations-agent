package com.alibaba.assistant.agent.start.config;

import com.alibaba.assistant.agent.core.resilience.CircuitBreaker;
import com.alibaba.assistant.agent.core.resilience.RateLimiter;
import com.alibaba.assistant.agent.core.resilience.ResilienceMetrics;
import com.alibaba.assistant.agent.core.resilience.ResilientCallExecutor;
import com.alibaba.assistant.agent.core.resilience.RetryPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * LLM 韧性层执行器的 Spring 装配。
 *
 * <p>把限流/并发隔离/熔断/重试/超时组合成一个 {@link ResilientCallExecutor} 单例 Bean，
 * 由 {@code CodeactAgentConfig}（包装 ChatModel）和 {@code LlmSelfTestController}（探活）
 * <b>共用同一个实例</b>——同一个熔断器、同一份指标，行为与统计完全一致。
 *
 * <p>默认参数偏保守：超时默认关闭（真实 LLM 调用本就耗时较长），熔断仅在持续高失败率时触发。
 * 可通过 {@code app.llm.resilience.*} 调参或用 {@code enabled=false} 关闭整层。
 */
@Configuration
public class LlmResilienceConfig {

    private static final Logger log = LoggerFactory.getLogger(LlmResilienceConfig.class);

    @Bean
    @ConditionalOnProperty(prefix = "app.llm.resilience", name = "enabled", havingValue = "true", matchIfMissing = true)
    public ResilientCallExecutor llmResilientCallExecutor(Environment environment, ResilienceMetrics metrics) {
        double ratePerSecond = prop(environment, "app.llm.resilience.rate-per-second", 1000.0);
        int burst = (int) prop(environment, "app.llm.resilience.burst", ratePerSecond);
        int maxConcurrent = (int) prop(environment, "app.llm.resilience.max-concurrent", 64);
        long bulkheadWaitMillis = (long) prop(environment, "app.llm.resilience.bulkhead-wait-millis", 200);
        int retryMaxAttempts = (int) prop(environment, "app.llm.resilience.retry-max-attempts", 2);
        long retryBackoffMillis = (long) prop(environment, "app.llm.resilience.retry-backoff-millis", 500);
        long timeoutMillis = (long) prop(environment, "app.llm.resilience.timeout-millis", 0);
        int cbWindow = (int) prop(environment, "app.llm.resilience.cb-window", 20);
        int cbMinCalls = (int) prop(environment, "app.llm.resilience.cb-min-calls", 10);
        double cbFailureRate = prop(environment, "app.llm.resilience.cb-failure-rate", 0.7);
        long cbOpenMillis = (long) prop(environment, "app.llm.resilience.cb-open-millis", 30000);
        int cbHalfOpenTrials = (int) prop(environment, "app.llm.resilience.cb-half-open-trials", 2);

        RetryPolicy retryPolicy = new RetryPolicy(retryMaxAttempts, retryBackoffMillis, 2.0,
                retryBackoffMillis * 8, 0.2, ResilientCallExecutor.defaultRetryable());
        CircuitBreaker circuitBreaker = new CircuitBreaker(cbWindow, cbMinCalls, cbFailureRate,
                cbOpenMillis, cbHalfOpenTrials);
        ResilientCallExecutor executor = ResilientCallExecutor.builder()
                .name("llm")
                .rateLimiter(new RateLimiter(ratePerSecond, burst), 200)
                .bulkhead(maxConcurrent, bulkheadWaitMillis)
                .circuitBreaker(circuitBreaker)
                .retryPolicy(retryPolicy)
                .perCallTimeoutMillis(timeoutMillis)
                .metrics(metrics)
                .build();

        log.info("LlmResilienceConfig - reason=LLM 韧性层执行器已创建, "
                + "ratePerSecond={}, maxConcurrent={}, retryMaxAttempts={}, timeoutMillis={}, cbFailureRate={}",
                ratePerSecond, maxConcurrent, retryMaxAttempts, timeoutMillis, cbFailureRate);
        return executor;
    }

    private double prop(Environment environment, String key, double defaultValue) {
        if (environment == null) {
            return defaultValue;
        }
        return environment.getProperty(key, Double.class, defaultValue);
    }
}
