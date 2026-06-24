package com.alibaba.assistant.agent.start.service;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import com.alibaba.assistant.agent.start.config.AsyncConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 在专用线程池里真正执行分析任务。
 *
 * <p>刻意做成<b>独立的 bean</b>而不是 {@link AnalysisTaskService} 的方法：Spring 的 @Async
 * 和 @Transactional 都基于代理，类内部自调用不会生效。这里由它编排"标记运行中 → 跑分析 →
 * 标记成功/失败"，每一步状态变更都委托给 {@link AnalysisTaskService} 的独立短事务。
 */
@Component
public class AnalysisAsyncRunner {

    private static final Logger log = LoggerFactory.getLogger(AnalysisAsyncRunner.class);

    private final AnalysisTaskService taskService;
    private final EcommerceQuestionAnswerService questionAnswerService;

    public AnalysisAsyncRunner(AnalysisTaskService taskService,
                               EcommerceQuestionAnswerService questionAnswerService) {
        this.taskService = taskService;
        this.questionAnswerService = questionAnswerService;
    }

    @Async(AsyncConfig.ANALYSIS_EXECUTOR)
    public CompletableFuture<Void> run(String taskId, String sessionId, String question) {
        taskService.markRunning(taskId);
        try {
            // 真正的分析放在事务之外，不长时间占着数据库连接。
            Map<String, Object> result = questionAnswerService.answer(sessionId, question);
            String answer = String.valueOf(result.getOrDefault("answer", ""));
            String toolChain = joinToolChain(result.get("tool_chain"));
            taskService.markSucceeded(taskId, answer, toolChain);
        }
        catch (Exception e) {
            log.error("AnalysisAsyncRunner#run - reason=analysis failed, taskId={}", taskId, e);
            taskService.markFailed(taskId, e.getMessage());
        }
        return CompletableFuture.completedFuture(null);
    }

    private String joinToolChain(Object toolChain) {
        if (toolChain instanceof Collection<?> c) {
            return c.stream().map(String::valueOf).collect(Collectors.joining(","));
        }
        return toolChain == null ? null : String.valueOf(toolChain);
    }
}
