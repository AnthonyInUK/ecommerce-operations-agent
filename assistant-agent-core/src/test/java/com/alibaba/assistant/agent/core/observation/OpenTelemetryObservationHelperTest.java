package com.alibaba.assistant.agent.core.observation;

import com.alibaba.assistant.agent.core.observation.context.HookObservationContext;
import com.alibaba.assistant.agent.core.observation.context.InterceptorObservationContext;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Tracer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * OpenTelemetryObservationHelper 覆盖测试。
 * 使用 OTel 内置的 noop 实现，不需要额外依赖。
 * 验证 Span 生命周期管理（计数、清理）和执行包装方法。
 */
class OpenTelemetryObservationHelperTest {

    private OpenTelemetryObservationHelper helper;

    @BeforeEach
    void setUp() {
        Tracer tracer = OpenTelemetry.noop().getTracer("test-instrumentation");
        helper = new OpenTelemetryObservationHelper(tracer);
    }

    // ------------------------------------------------------------------
    // Hook Span 生命周期
    // ------------------------------------------------------------------

    @Test
    void startHookSpan_incrementsActiveSpanCount() {
        HookObservationContext ctx = new HookObservationContext();
        ctx.setHookName("AfterAgentLearningHook");

        helper.startHookSpan("session1:hook", ctx);

        assertEquals(1, helper.getActiveSpanCount());
    }

    @Test
    void endHookSpan_decrementsActiveSpanCount() {
        HookObservationContext ctx = new HookObservationContext();
        ctx.setHookName("AfterAgentLearningHook");
        ctx.setSuccess(true);
        helper.startHookSpan("session1:hook", ctx);

        helper.endHookSpan("session1:hook", ctx, null);

        assertEquals(0, helper.getActiveSpanCount());
    }

    @Test
    void endHookSpan_unknownKey_doesNotThrow() {
        HookObservationContext ctx = new HookObservationContext();
        ctx.setSuccess(true);

        // 没有对应的 startHookSpan 调用，不应抛异常
        assertDoesNotThrow(() -> helper.endHookSpan("nonexistent:key", ctx, null));
    }

    @Test
    void endHookSpan_withError_doesNotThrow() {
        HookObservationContext ctx = new HookObservationContext();
        ctx.setHookName("TestHook");
        ctx.setSuccess(false);
        helper.startHookSpan("session1:hook", ctx);

        assertDoesNotThrow(() ->
                helper.endHookSpan("session1:hook", ctx, new RuntimeException("模拟错误")));
        assertEquals(0, helper.getActiveSpanCount());
    }

    // ------------------------------------------------------------------
    // Interceptor Span 生命周期
    // ------------------------------------------------------------------

    @Test
    void startAndEndInterceptorSpan_managedCorrectly() {
        InterceptorObservationContext ctx = new InterceptorObservationContext();
        ctx.setInterceptorName("ExperienceDisclosureInterceptor");
        ctx.setInterceptorType(InterceptorObservationContext.InterceptorType.MODEL);
        ctx.setSuccess(true);

        helper.startInterceptorSpan("session1:interceptor", ctx);
        assertEquals(1, helper.getActiveSpanCount());

        helper.endInterceptorSpan("session1:interceptor", ctx, null);
        assertEquals(0, helper.getActiveSpanCount());
    }

    // ------------------------------------------------------------------
    // 多 Span 并发管理
    // ------------------------------------------------------------------

    @Test
    void multipleSpans_trackedIndependently() {
        HookObservationContext ctx = new HookObservationContext();
        ctx.setHookName("Hook");
        ctx.setSuccess(true);

        helper.startHookSpan("session1:hook", ctx);
        helper.startHookSpan("session2:hook", ctx);
        helper.startHookSpan("session3:hook", ctx);

        assertEquals(3, helper.getActiveSpanCount());

        helper.endHookSpan("session2:hook", ctx, null);
        assertEquals(2, helper.getActiveSpanCount());
    }

    // ------------------------------------------------------------------
    // Session 清理
    // ------------------------------------------------------------------

    @Test
    void cleanupSession_removesOnlyMatchingPrefix() {
        HookObservationContext ctx = new HookObservationContext();
        ctx.setHookName("Hook");

        helper.startHookSpan("sessionA:hook", ctx);
        helper.startHookSpan("sessionA:interceptor", ctx);
        helper.startHookSpan("sessionB:hook", ctx);

        helper.cleanupSession("sessionA");

        // sessionA 的两个 Span 被清理，sessionB 的保留
        assertEquals(1, helper.getActiveSpanCount());
    }

    // ------------------------------------------------------------------
    // withSpan 包装执行
    // ------------------------------------------------------------------

    @Test
    void withSpan_executesActionAndReturnsResult() {
        io.opentelemetry.api.common.Attributes attrs = io.opentelemetry.api.common.Attributes.empty();

        String result = helper.withSpan("test.operation", attrs, () -> "done");

        assertEquals("done", result);
    }

    @Test
    void withSpan_actionThrows_rethrowsException() {
        io.opentelemetry.api.common.Attributes attrs = io.opentelemetry.api.common.Attributes.empty();

        assertThrows(RuntimeException.class, () ->
                helper.withSpan("test.error", attrs, () -> {
                    throw new RuntimeException("操作失败");
                }));
    }

    @Test
    void withSpanVoid_executesAction() {
        io.opentelemetry.api.common.Attributes attrs = io.opentelemetry.api.common.Attributes.empty();
        boolean[] executed = {false};

        helper.withSpanVoid("test.void", attrs, () -> executed[0] = true);

        assertTrue(executed[0]);
    }
}
