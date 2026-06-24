package com.alibaba.assistant.agent.start.web;

import java.util.LinkedHashMap;
import java.util.Map;

import com.alibaba.assistant.agent.start.service.AnalysisAsyncRunner;
import com.alibaba.assistant.agent.start.service.AnalysisTaskService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 异步分析任务端点：提交即返回任务号，客户端轮询拿结果。
 *
 * <p>与同步的 {@code /answer} 不同，这里把长耗时分析丢到后台线程池执行：
 * <ul>
 *   <li>{@code POST /api/ecommerce/analysis-tasks}  body {"question","session_id"} → 202 + task_id</li>
 *   <li>{@code GET  /api/ecommerce/analysis-tasks/{taskId}} → 200 状态+结果 / 404 不存在</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/ecommerce/analysis-tasks")
public class AnalysisTaskController {

    private static final Logger log = LoggerFactory.getLogger(AnalysisTaskController.class);

    private final AnalysisTaskService taskService;
    private final AnalysisAsyncRunner asyncRunner;

    public AnalysisTaskController(AnalysisTaskService taskService, AnalysisAsyncRunner asyncRunner) {
        this.taskService = taskService;
        this.asyncRunner = asyncRunner;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> submit(@RequestBody Map<String, String> body) {
        String question = body.getOrDefault("question", "").trim();
        String sessionId = body.getOrDefault("session_id", "demo-ui-session").trim();
        if (question.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "question 不能为空"));
        }
        if (sessionId.isEmpty()) {
            sessionId = "demo-ui-session";
        }

        String taskId = taskService.createTask(question, sessionId, body.getOrDefault("created_by", "api"));
        try {
            asyncRunner.run(taskId, sessionId, question);
        }
        catch (TaskRejectedException ex) {
            // 线程池有界队列已满：明确告知繁忙，并把任务标记为失败，不留悬空状态。
            taskService.markFailed(taskId, "分析线程池繁忙，已拒绝；请稍后重试");
            log.warn("AnalysisTaskController#submit - reason=executor saturated, taskId={}", taskId);
            Map<String, Object> busy = new LinkedHashMap<>();
            busy.put("success", false);
            busy.put("task_id", taskId);
            busy.put("message", "服务繁忙，请稍后重试");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(busy);
        }

        Map<String, Object> accepted = new LinkedHashMap<>();
        accepted.put("success", true);
        accepted.put("task_id", taskId);
        accepted.put("status", "SUBMITTED");
        accepted.put("poll_url", "/api/ecommerce/analysis-tasks/" + taskId);
        return ResponseEntity.accepted().body(accepted);
    }

    @GetMapping("/{taskId}")
    public ResponseEntity<?> get(@PathVariable String taskId) {
        return taskService.getView(taskId)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("success", false, "message", "task not found: " + taskId)));
    }
}
