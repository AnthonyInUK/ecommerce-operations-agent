package com.alibaba.assistant.agent.start.service;

import com.alibaba.assistant.agent.start.persistence.entity.AnalysisTask;

import java.time.Instant;

/**
 * 对外返回的分析任务视图（不直接暴露 JPA 实体）。
 */
public record AnalysisTaskView(
        String taskId,
        String sessionId,
        String question,
        String status,
        String answer,
        String toolChain,
        String errorMessage,
        Instant submittedAt,
        Instant finishedAt,
        Long elapsedMs
) {
    public static AnalysisTaskView from(AnalysisTask t) {
        return new AnalysisTaskView(
                t.getTaskId(),
                t.getSessionId(),
                t.getQuestion(),
                t.getStatus().name(),
                t.getResultText(),
                t.getToolChain(),
                t.getErrorMessage(),
                t.getSubmittedAt(),
                t.getFinishedAt(),
                t.getElapsedMs()
        );
    }
}
