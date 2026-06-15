package com.alibaba.assistant.agent.start.config;

import com.alibaba.assistant.agent.core.resilience.ResilienceMetrics;
import com.alibaba.assistant.agent.start.audit.SqlAuditTrail;
import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 可观测性配置：把 LLM 韧性层指标和 SQL 审计指标接入 Micrometer，
 * 通过 {@code /actuator/prometheus} 暴露，供 Prometheus 抓取、Grafana 展示。
 *
 * <p>用 Java 原生的 Micrometer / OpenTelemetry 体系，而不是外部 SaaS——这是 Spring AI 的标准，
 * 也便于在国内自托管（Prometheus + Grafana / Jaeger / Langfuse 均可对接）。
 */
@Configuration
public class ObservabilityConfig {

    /** 单例指标实例：CodeactAgentConfig 构建韧性层时复用它，这里再暴露给监控。 */
    @Bean
    public ResilienceMetrics llmResilienceMetrics() {
        return new ResilienceMetrics();
    }

    /** 韧性层指标 → Prometheus 计数器（单调递增的累计量用 FunctionCounter 更符合 Prometheus 语义）。 */
    @Bean
    public MeterBinder llmResilienceMeterBinder(ResilienceMetrics m) {
        return registry -> {
            counter(registry, "llm.resilience.total", m, ResilienceMetrics::getTotalCalls);
            counter(registry, "llm.resilience.success", m, ResilienceMetrics::getSuccess);
            counter(registry, "llm.resilience.failure", m, ResilienceMetrics::getFailure);
            counter(registry, "llm.resilience.timeout", m, ResilienceMetrics::getTimeout);
            counter(registry, "llm.resilience.retries", m, ResilienceMetrics::getRetries);
            counter(registry, "llm.resilience.short_circuited", m, ResilienceMetrics::getShortCircuited);
            counter(registry, "llm.resilience.rate_limited", m, ResilienceMetrics::getRateLimited);
            counter(registry, "llm.resilience.bulkhead_rejected", m, ResilienceMetrics::getBulkheadRejected);
            counter(registry, "llm.resilience.fallback", m, ResilienceMetrics::getFallback);
        };
    }

    /** SQL 审计 → Prometheus 仪表（当前缓存的最近查询条数）。 */
    @Bean
    public MeterBinder sqlAuditMeterBinder(SqlAuditTrail trail) {
        return registry -> Gauge.builder("sql.audit.recent_count", trail, SqlAuditTrail::recentCount)
                .description("Number of recent warehouse SQL queries retained in the audit ring buffer")
                .register(registry);
    }

    private void counter(io.micrometer.core.instrument.MeterRegistry registry, String name,
                         ResilienceMetrics m, java.util.function.ToDoubleFunction<ResilienceMetrics> fn) {
        FunctionCounter.builder(name, m, fn).register(registry);
    }
}
