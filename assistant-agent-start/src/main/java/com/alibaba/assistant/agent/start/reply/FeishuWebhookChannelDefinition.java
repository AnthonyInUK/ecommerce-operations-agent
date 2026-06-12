package com.alibaba.assistant.agent.start.reply;

import com.alibaba.assistant.agent.extension.reply.model.ChannelExecutionContext;
import com.alibaba.assistant.agent.extension.reply.model.ParameterSchema;
import com.alibaba.assistant.agent.extension.reply.model.ReplyResult;
import com.alibaba.assistant.agent.extension.reply.spi.ReplyChannelDefinition;
import com.alibaba.assistant.agent.start.config.AppReplyProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class FeishuWebhookChannelDefinition implements ReplyChannelDefinition {

    private static final Logger log = LoggerFactory.getLogger(FeishuWebhookChannelDefinition.class);

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private final AppReplyProperties properties;
    private final NotificationDeliveryGuard deliveryGuard;
    private final PersistentNotificationDeliveryStore persistentDeliveryStore;

    public FeishuWebhookChannelDefinition(AppReplyProperties properties,
                                          NotificationDeliveryGuard deliveryGuard,
                                          ObjectProvider<PersistentNotificationDeliveryStore> persistentDeliveryStoreProvider) {
        this.properties = properties;
        this.deliveryGuard = deliveryGuard;
        this.persistentDeliveryStore = persistentDeliveryStoreProvider.getIfAvailable();
    }

    @Override
    public String getChannelCode() {
        return "FEISHU_WEBHOOK";
    }

    @Override
    public String getDescription() {
        return "Send report or alert text to Feishu webhook";
    }

    @Override
    public ParameterSchema getSupportedParameters() {
        return ParameterSchema.builder()
                .parameter("text", ParameterSchema.ParameterType.STRING, true, "飞书消息正文")
                .parameter("title", ParameterSchema.ParameterType.STRING, false, "飞书消息标题")
                .build();
    }

    @Override
    public ReplyResult execute(ChannelExecutionContext context, Map<String, Object> params) {
        String webhook = properties.getFeishuWebhook();
        String text = String.valueOf(params.getOrDefault("text", "")).trim();
        if (text.isEmpty()) {
            return ReplyResult.failure("Feishu text is empty");
        }
        String title = String.valueOf(params.getOrDefault("title", "电商数据分析通知")).trim();
        String fingerprint = deliveryGuard.fingerprint(title, text);
        if (properties.isNotificationDedupEnabled()) {
            String suppressionSource = suppressionSource(fingerprint);
            if (suppressionSource != null) {
                ReplyResult result = ReplyResult.success("Feishu webhook suppressed by dedup guard");
                result.putMetadata("channelCode", getChannelCode());
                result.putMetadata("title", title);
                result.putMetadata("dedupSuppressed", true);
                result.putMetadata("dedupSuppressionSource", suppressionSource);
                result.putMetadata("dedupWindowSeconds", properties.getNotificationDedupWindowSeconds());
                result.putMetadata("repliedToUser", true);
                return result;
            }
        }

        if (webhook == null || webhook.isBlank()) {
            return degradeToIdeLog("feishu webhook not configured", context, title, text, fingerprint);
        }

        try {
            Map<String, Object> content = new LinkedHashMap<>();
            content.put("text", title + "\n" + text);

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("msg_type", "text");
            body.put("content", content);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(webhook))
                    .timeout(Duration.ofSeconds(properties.getFeishuWebhookTimeoutSeconds()))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                log.error("FeishuWebhookChannelDefinition#execute - reason=feishu webhook failed, status={}, body={}",
                        response.statusCode(), response.body());
                return degradeToIdeLog("feishu webhook failed with status " + response.statusCode(), context, title, text, fingerprint);
            }
            if (!isFeishuSuccess(response.body())) {
                log.error("FeishuWebhookChannelDefinition#execute - reason=feishu webhook business failed, status={}, body={}",
                        response.statusCode(), response.body());
                return degradeToIdeLog("feishu webhook business failed: " + response.body(), context, title, text, fingerprint);
            }

            if (properties.isNotificationDedupEnabled()) {
                markDelivered(fingerprint, title, text);
            }

            ReplyResult result = ReplyResult.success("Feishu webhook sent");
            result.putMetadata("channelCode", getChannelCode());
            result.putMetadata("title", title);
            result.putMetadata("feishuStatusCode", response.statusCode());
            result.putMetadata("repliedToUser", true);
            return result;
        }
        catch (Exception e) {
            log.error("FeishuWebhookChannelDefinition#execute - reason=feishu webhook execution failed", e);
            return degradeToIdeLog("failed to send Feishu webhook: " + e.getMessage(), context, title, text, fingerprint);
        }
    }

    @Override
    public boolean supportsAsync() {
        return true;
    }

    private String suppressionSource(String fingerprint) {
        long dedupWindowSeconds = properties.getNotificationDedupWindowSeconds();
        if (deliveryGuard.shouldSuppress(fingerprint, dedupWindowSeconds)) {
            return "in_memory";
        }
        if (properties.isPersistentNotificationDedupEnabled()
                && persistentDeliveryStore != null
                && persistentDeliveryStore.isDeliveredWithinWindow(fingerprint, dedupWindowSeconds)) {
            return "persistent_store";
        }
        return null;
    }

    private void markDelivered(String fingerprint, String title, String text) {
        deliveryGuard.markDelivered(fingerprint);
        if (properties.isPersistentNotificationDedupEnabled() && persistentDeliveryStore != null) {
            persistentDeliveryStore.recordDelivery(fingerprint, title, text);
        }
    }

    private boolean isFeishuSuccess(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return true;
        }
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            if (root.has("StatusCode")) {
                return root.path("StatusCode").asInt(-1) == 0;
            }
            if (root.has("code")) {
                return root.path("code").asInt(-1) == 0;
            }
            if (root.has("errcode")) {
                return root.path("errcode").asInt(-1) == 0;
            }
            return true;
        }
        catch (Exception e) {
            log.warn("FeishuWebhookChannelDefinition#isFeishuSuccess - reason=unable to parse feishu response body, body={}",
                    responseBody);
            return true;
        }
    }

    private ReplyResult degradeToIdeLog(String reason,
                                        ChannelExecutionContext context,
                                        String title,
                                        String text,
                                        String fingerprint) {
        if (!properties.isNotificationFallbackToIdeLogEnabled()) {
            return ReplyResult.failure(reason);
        }
        log.warn("FeishuWebhookChannelDefinition#execute - reason={}, toolName={}, fallback=IDE_LOG, title={}, text={}",
                reason, context.getToolName(), title, text);
        if (properties.isNotificationDedupEnabled() && shouldMarkFallbackDelivery(reason)) {
            markDelivered(fingerprint, title, text);
        }
        ReplyResult result = ReplyResult.success("Feishu webhook degraded to IDE log");
        result.putMetadata("channelCode", "IDE_TEXT");
        result.putMetadata("title", title);
        result.putMetadata("degraded", true);
        result.putMetadata("degradeReason", reason);
        result.putMetadata("repliedToUser", true);
        return result;
    }

    private boolean shouldMarkFallbackDelivery(String reason) {
        return reason != null && reason.contains("not configured");
    }
}
