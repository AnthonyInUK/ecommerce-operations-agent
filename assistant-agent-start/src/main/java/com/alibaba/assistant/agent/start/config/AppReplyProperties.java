package com.alibaba.assistant.agent.start.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.reply")
public class AppReplyProperties {

    private String feishuWebhook;
    private boolean notificationDedupEnabled = true;
    private long notificationDedupWindowSeconds = 1800;
    private boolean persistentNotificationDedupEnabled = true;
    private boolean notificationFallbackToIdeLogEnabled = true;
    private int feishuWebhookTimeoutSeconds = 8;

    public String getFeishuWebhook() {
        return feishuWebhook;
    }

    public void setFeishuWebhook(String feishuWebhook) {
        this.feishuWebhook = feishuWebhook;
    }

    public boolean isNotificationDedupEnabled() {
        return notificationDedupEnabled;
    }

    public void setNotificationDedupEnabled(boolean notificationDedupEnabled) {
        this.notificationDedupEnabled = notificationDedupEnabled;
    }

    public long getNotificationDedupWindowSeconds() {
        return notificationDedupWindowSeconds;
    }

    public void setNotificationDedupWindowSeconds(long notificationDedupWindowSeconds) {
        this.notificationDedupWindowSeconds = notificationDedupWindowSeconds;
    }

    public boolean isPersistentNotificationDedupEnabled() {
        return persistentNotificationDedupEnabled;
    }

    public void setPersistentNotificationDedupEnabled(boolean persistentNotificationDedupEnabled) {
        this.persistentNotificationDedupEnabled = persistentNotificationDedupEnabled;
    }

    public boolean isNotificationFallbackToIdeLogEnabled() {
        return notificationFallbackToIdeLogEnabled;
    }

    public void setNotificationFallbackToIdeLogEnabled(boolean notificationFallbackToIdeLogEnabled) {
        this.notificationFallbackToIdeLogEnabled = notificationFallbackToIdeLogEnabled;
    }

    public int getFeishuWebhookTimeoutSeconds() {
        return feishuWebhookTimeoutSeconds;
    }

    public void setFeishuWebhookTimeoutSeconds(int feishuWebhookTimeoutSeconds) {
        this.feishuWebhookTimeoutSeconds = feishuWebhookTimeoutSeconds;
    }
}
