package com.alibaba.assistant.agent.start.config;

import com.alibaba.assistant.agent.core.resilience.ResilienceMetrics;
import com.alibaba.assistant.agent.core.resilience.ResilientCallExecutor;
import com.alibaba.assistant.agent.start.audit.SqlAuditEntry;
import com.alibaba.assistant.agent.start.audit.SqlAuditTrail;
import io.micrometer.core.instrument.binder.MeterBinder;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证 LLM 韧性层指标和 SQL 审计指标正确接入 Micrometer（用内存 registry，无需启动整个应用）。
 */
class ObservabilityMetricsTest {

    private final ObservabilityConfig config = new ObservabilityConfig();

    @Test
    void resilienceMetrics_areExposedToRegistry() {
        ResilienceMetrics metrics = new ResilienceMetrics();
        // 用执行器真实产生几次成功/降级，驱动指标
        ResilientCallExecutor exec = ResilientCallExecutor.builder().metrics(metrics).build();
        exec.execute(() -> "ok", null);
        exec.execute(() -> "ok", null);
        exec.execute(() -> { throw new RuntimeException("boom"); }, () -> "fallback");

        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        MeterBinder binder = config.llmResilienceMeterBinder(metrics);
        binder.bindTo(registry);

        assertEquals(3.0, registry.get("llm.resilience.calls").functionCounter().count(), 0.001);
        assertEquals(2.0, registry.get("llm.resilience.success").functionCounter().count(), 0.001);
        assertEquals(1.0, registry.get("llm.resilience.failure").functionCounter().count(), 0.001);
        assertEquals(1.0, registry.get("llm.resilience.fallback").functionCounter().count(), 0.001);
    }

    @Test
    void sqlAuditCount_isExposedAsGauge() {
        SqlAuditTrail trail = new SqlAuditTrail(200);
        trail.record(new SqlAuditEntry("SELECT 1", null, 1, 2, true, "2026-06-15T00:00:00Z"));
        trail.record(new SqlAuditEntry("SELECT 2", null, 1, 3, true, "2026-06-15T00:00:01Z"));

        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        config.sqlAuditMeterBinder(trail).bindTo(registry);

        assertEquals(2.0, registry.get("sql.audit.recent_count").gauge().value(), 0.001);
    }
}
