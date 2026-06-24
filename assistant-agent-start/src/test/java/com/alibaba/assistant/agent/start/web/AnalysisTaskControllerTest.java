package com.alibaba.assistant.agent.start.web;

import java.util.Map;
import java.util.Optional;

import com.alibaba.assistant.agent.start.service.AnalysisAsyncRunner;
import com.alibaba.assistant.agent.start.service.AnalysisTaskService;
import com.alibaba.assistant.agent.start.service.AnalysisTaskView;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * AnalysisTaskController 单测（mock 服务与异步 runner，不依赖真实库/线程池，可复现）。
 * 覆盖：提交受理并派发、空问题拒绝、线程池饱和降级、轮询命中/404。
 */
class AnalysisTaskControllerTest {

    @Test
    @SuppressWarnings("unchecked")
    void submit_acceptsAndDispatches() {
        AnalysisTaskService service = mock(AnalysisTaskService.class);
        AnalysisAsyncRunner runner = mock(AnalysisAsyncRunner.class);
        when(service.createTask(anyString(), anyString(), anyString())).thenReturn("task-123");

        AnalysisTaskController controller = new AnalysisTaskController(service, runner);
        ResponseEntity<Map<String, Object>> resp =
                controller.submit(Map.of("question", "查GMV", "session_id", "s1"));

        assertEquals(HttpStatus.ACCEPTED, resp.getStatusCode());
        assertEquals("task-123", resp.getBody().get("task_id"));
        assertEquals("SUBMITTED", resp.getBody().get("status"));
        verify(runner).run("task-123", "s1", "查GMV");
    }

    @Test
    void submit_emptyQuestion_rejectedWith400() {
        AnalysisTaskController controller =
                new AnalysisTaskController(mock(AnalysisTaskService.class), mock(AnalysisAsyncRunner.class));
        ResponseEntity<Map<String, Object>> resp = controller.submit(Map.of("question", "   "));

        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
        assertEquals(false, resp.getBody().get("success"));
    }

    @Test
    void submit_executorSaturated_returns503AndMarksFailed() {
        AnalysisTaskService service = mock(AnalysisTaskService.class);
        AnalysisAsyncRunner runner = mock(AnalysisAsyncRunner.class);
        when(service.createTask(anyString(), anyString(), anyString())).thenReturn("task-busy");
        when(runner.run(anyString(), anyString(), anyString()))
                .thenThrow(new TaskRejectedException("queue full"));

        AnalysisTaskController controller = new AnalysisTaskController(service, runner);
        ResponseEntity<Map<String, Object>> resp =
                controller.submit(Map.of("question", "查GMV", "session_id", "s1"));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, resp.getStatusCode());
        assertEquals(false, resp.getBody().get("success"));
        verify(service).markFailed(eq("task-busy"), anyString());
    }

    @Test
    void get_found_returns200() {
        AnalysisTaskService service = mock(AnalysisTaskService.class);
        AnalysisTaskView view = new AnalysisTaskView("t1", "s1", "查GMV", "SUCCEEDED",
                "GMV 是 1390", "GmvQueryTool", null, null, null, 12L);
        when(service.getView("t1")).thenReturn(Optional.of(view));

        AnalysisTaskController controller = new AnalysisTaskController(service, mock(AnalysisAsyncRunner.class));
        ResponseEntity<?> resp = controller.get("t1");

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertSame(view, resp.getBody());
    }

    @Test
    void get_missing_returns404() {
        AnalysisTaskService service = mock(AnalysisTaskService.class);
        when(service.getView(anyString())).thenReturn(Optional.empty());

        AnalysisTaskController controller = new AnalysisTaskController(service, mock(AnalysisAsyncRunner.class));
        ResponseEntity<?> resp = controller.get("nope");

        assertEquals(HttpStatus.NOT_FOUND, resp.getStatusCode());
    }
}
