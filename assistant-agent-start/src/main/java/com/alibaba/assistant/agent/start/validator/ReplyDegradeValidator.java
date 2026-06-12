package com.alibaba.assistant.agent.start.validator;

import com.alibaba.assistant.agent.extension.reply.model.ChannelExecutionContext;
import com.alibaba.assistant.agent.extension.reply.model.ReplyResult;
import com.alibaba.assistant.agent.start.config.AppReplyProperties;
import com.alibaba.assistant.agent.start.reply.FeishuWebhookChannelDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;

@Component
@Order(221)
public class ReplyDegradeValidator implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ReplyDegradeValidator.class);

    private final AppReplyProperties replyProperties;
    private final FeishuWebhookChannelDefinition feishuWebhookChannelDefinition;

    public ReplyDegradeValidator(AppReplyProperties replyProperties,
                                 FeishuWebhookChannelDefinition feishuWebhookChannelDefinition) {
        this.replyProperties = replyProperties;
        this.feishuWebhookChannelDefinition = feishuWebhookChannelDefinition;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!replyProperties.isNotificationFallbackToIdeLogEnabled()) {
            return;
        }
        if (replyProperties.getFeishuWebhook() != null && !replyProperties.getFeishuWebhook().isBlank()) {
            log.info("ReplyDegradeValidator#run - reason=skip degrade validation because feishu webhook is configured");
            return;
        }

        ReplyResult result = feishuWebhookChannelDefinition.execute(
                ChannelExecutionContext.builder()
                        .toolName("send_notification")
                        .source(ChannelExecutionContext.ExecutionSource.MANUAL)
                        .traceId("reply-degrade-validator")
                        .build(),
                Map.of(
                        "title", "Week E 降级校验 " + Instant.now(),
                        "text", "验证飞书 webhook 缺失时会自动降级到 IDE/log。"
                )
        );

        if (!result.isSuccess()) {
            throw new IllegalStateException("Reply degrade validation failed: " + result.getMessage());
        }
        Object degraded = result.getMetadata().get("degraded");
        if (!Boolean.TRUE.equals(degraded)) {
            throw new IllegalStateException("Reply degrade validation expected degraded=true");
        }
        log.info("ReplyDegradeValidator#run - reason=reply degrade path validated, channelCode={}, degradeReason={}",
                result.getMetadata().get("channelCode"),
                result.getMetadata().get("degradeReason"));
    }
}
