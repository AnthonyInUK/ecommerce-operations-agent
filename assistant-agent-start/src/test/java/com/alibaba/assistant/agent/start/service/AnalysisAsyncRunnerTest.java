package com.alibaba.assistant.agent.start.service;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * AnalysisAsyncRunner 状态机单测（直接调用，mock 依赖；不经过 @Async 代理，验证编排逻辑）。
 */
class AnalysisAsyncRunnerTest {

    @Test
    void run_success_marksRunningThenSucceeded_withJoinedToolChain() {
        AnalysisTaskService service = mock(AnalysisTaskService.class);
        EcommerceQuestionAnswerService qa = mock(EcommerceQuestionAnswerService.class);
        when(qa.answer("s1", "查GMV")).thenReturn(Map.of(
                "answer", "GMV 是 1390",
                "tool_chain", List.of("GmvQueryTool", "RefundAnalysisTool")
        ));

        new AnalysisAsyncRunner(service, qa).run("t1", "s1", "查GMV").join();

        verify(service).markRunning("t1");
        verify(service).markSucceeded("t1", "GMV 是 1390", "GmvQueryTool,RefundAnalysisTool");
        verify(service, never()).markFailed(anyString(), anyString());
    }

    @Test
    void run_analysisThrows_marksFailed() {
        AnalysisTaskService service = mock(AnalysisTaskService.class);
        EcommerceQuestionAnswerService qa = mock(EcommerceQuestionAnswerService.class);
        when(qa.answer(anyString(), anyString())).thenThrow(new RuntimeException("warehouse down"));

        new AnalysisAsyncRunner(service, qa).run("t1", "s1", "查GMV").join();

        verify(service).markRunning("t1");
        verify(service).markFailed(eq("t1"), eq("warehouse down"));
        verify(service, never()).markSucceeded(anyString(), anyString(), anyString());
    }
}
