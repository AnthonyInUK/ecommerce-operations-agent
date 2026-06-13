package com.alibaba.assistant.agent.extension.learning.model;

import java.time.Instant;
import java.util.UUID;

/**
 * 一次 Agent 对话的学习快照，供离线学习任务批量复盘。
 *
 * <p>在线学习（AfterAgentLearningHook）当场判断是否值得记录，门槛较高。
 * 离线学习从这张表里拿原始会话数据，对多次会话做横向对比，门槛可以更低。
 */
public class LearningSessionRecord {

    private String id;
    private String sessionId;
    private String tenantId;
    private String conversationSummary;
    private String toolCallsJson;
    private String modelCallsJson;
    private Instant createdAt;
    private boolean offlineProcessed;
    private Instant offlineProcessedAt;

    public LearningSessionRecord() {
        this.id = UUID.randomUUID().toString();
        this.createdAt = Instant.now();
        this.offlineProcessed = false;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public String getConversationSummary() { return conversationSummary; }
    public void setConversationSummary(String conversationSummary) { this.conversationSummary = conversationSummary; }

    public String getToolCallsJson() { return toolCallsJson; }
    public void setToolCallsJson(String toolCallsJson) { this.toolCallsJson = toolCallsJson; }

    public String getModelCallsJson() { return modelCallsJson; }
    public void setModelCallsJson(String modelCallsJson) { this.modelCallsJson = modelCallsJson; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public boolean isOfflineProcessed() { return offlineProcessed; }
    public void setOfflineProcessed(boolean offlineProcessed) { this.offlineProcessed = offlineProcessed; }

    public Instant getOfflineProcessedAt() { return offlineProcessedAt; }
    public void setOfflineProcessedAt(Instant offlineProcessedAt) { this.offlineProcessedAt = offlineProcessedAt; }
}
