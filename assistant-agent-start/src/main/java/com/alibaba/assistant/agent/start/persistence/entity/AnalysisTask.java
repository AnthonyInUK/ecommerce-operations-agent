package com.alibaba.assistant.agent.start.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;

/**
 * 一条电商运营分析任务的落库记录。
 *
 * <p>这是 operational 库(可写)里的核心表,和只读数仓分开:每次"提交一个分析问题"
 * 就生成一行,异步执行过程中更新状态与结果。对金融/运营类系统而言,这就是可追溯的
 * 审计凭证——谁、什么时候、问了什么、跑了哪些工具、结果如何、耗时多久,全部留痕。
 */
@Entity
@Table(name = "analysis_task", indexes = {
        @Index(name = "idx_analysis_task_status", columnList = "status"),
        @Index(name = "idx_analysis_task_session", columnList = "session_id")
})
public class AnalysisTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 对外暴露的任务标识(UUID),客户端用它轮询,不暴露自增主键。 */
    @Column(name = "task_id", nullable = false, unique = true, length = 64)
    private String taskId;

    @Column(name = "session_id", length = 128)
    private String sessionId;

    @Column(name = "question", nullable = false, length = 2000)
    private String question;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private AnalysisTaskStatus status;

    @Column(name = "result_text", columnDefinition = "TEXT")
    private String resultText;

    @Column(name = "error_message", length = 2000)
    private String errorMessage;

    /** 命中的工具链,如 ["GmvQueryTool","RefundAnalysisTool"],逗号拼接落库便于排障。 */
    @Column(name = "tool_chain", length = 1000)
    private String toolChain;

    @Column(name = "created_by", length = 128)
    private String createdBy;

    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "elapsed_ms")
    private Long elapsedMs;

    /** 乐观锁:异步线程并发更新同一任务时防止丢更新。 */
    @Version
    @Column(name = "version")
    private Long version;

    protected AnalysisTask() {
        // for JPA
    }

    public AnalysisTask(String taskId, String sessionId, String question, String createdBy) {
        this.taskId = taskId;
        this.sessionId = sessionId;
        this.question = question;
        this.createdBy = createdBy;
        this.status = AnalysisTaskStatus.SUBMITTED;
        this.submittedAt = Instant.now();
    }

    /** 标记开始执行。 */
    public void markRunning() {
        this.status = AnalysisTaskStatus.RUNNING;
        this.startedAt = Instant.now();
    }

    /** 标记成功，记录结果、工具链与耗时。 */
    public void markSucceeded(String resultText, String toolChain) {
        this.status = AnalysisTaskStatus.SUCCEEDED;
        this.resultText = resultText;
        this.toolChain = toolChain;
        finish();
    }

    /** 标记失败，记录错误信息。 */
    public void markFailed(String errorMessage) {
        this.status = AnalysisTaskStatus.FAILED;
        this.errorMessage = truncate(errorMessage, 2000);
        finish();
    }

    private void finish() {
        this.finishedAt = Instant.now();
        Instant from = this.startedAt != null ? this.startedAt : this.submittedAt;
        if (from != null) {
            this.elapsedMs = this.finishedAt.toEpochMilli() - from.toEpochMilli();
        }
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    public Long getId() {
        return id;
    }

    public String getTaskId() {
        return taskId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getQuestion() {
        return question;
    }

    public AnalysisTaskStatus getStatus() {
        return status;
    }

    public String getResultText() {
        return resultText;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public String getToolChain() {
        return toolChain;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public Long getElapsedMs() {
        return elapsedMs;
    }

    public Long getVersion() {
        return version;
    }
}
