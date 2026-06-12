package com.alibaba.assistant.agent.start.validator;

import com.alibaba.assistant.agent.start.config.AppDataSourceProperties;
import com.alibaba.assistant.agent.start.config.AppReplyProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@Order(220)
@ConditionalOnBean(JdbcTemplate.class)
public class WeekERobustnessValidator implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(WeekERobustnessValidator.class);

    private final JdbcTemplate jdbcTemplate;
    private final AppReplyProperties replyProperties;
    private final AppDataSourceProperties dataSourceProperties;

    public WeekERobustnessValidator(JdbcTemplate jdbcTemplate,
                                    AppReplyProperties replyProperties,
                                    AppDataSourceProperties dataSourceProperties) {
        this.jdbcTemplate = jdbcTemplate;
        this.replyProperties = replyProperties;
        this.dataSourceProperties = dataSourceProperties;
    }

    @Override
    public void run(ApplicationArguments args) {
        Integer dedupTableCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE UPPER(TABLE_NAME) = 'NOTIFICATION_DELIVERY_LOG'",
                Integer.class
        );
        if (dedupTableCount == null || dedupTableCount == 0) {
            throw new IllegalStateException("Week E robustness table notification_delivery_log is missing");
        }
        Integer auditTableCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE UPPER(TABLE_NAME) = 'PROMPT_SECURITY_AUDIT'",
                Integer.class
        );
        if (auditTableCount == null || auditTableCount == 0) {
            throw new IllegalStateException("Week E robustness table prompt_security_audit is missing");
        }
        if (replyProperties.getNotificationDedupWindowSeconds() <= 0) {
            throw new IllegalStateException("Notification dedup window must be positive");
        }
        if (replyProperties.getFeishuWebhookTimeoutSeconds() <= 0) {
            throw new IllegalStateException("Feishu webhook timeout must be positive");
        }
        if (dataSourceProperties.isReadCacheEnabled() && dataSourceProperties.getReadCacheTtlSeconds() <= 0) {
            throw new IllegalStateException("Read cache ttl must be positive when cache is enabled");
        }
        if (dataSourceProperties.isReadCacheEnabled() && dataSourceProperties.getReadCacheMaxEntries() <= 0) {
            throw new IllegalStateException("Read cache max entries must be positive when cache is enabled");
        }
        log.info("WeekERobustnessValidator#run - reason=week e robustness validated, persistentDedup={}, fallbackToIdeLog={}, readCacheEnabled={}, readCacheTtlSeconds={}, readCacheMaxEntries={}, promptAuditTable=true",
                replyProperties.isPersistentNotificationDedupEnabled(),
                replyProperties.isNotificationFallbackToIdeLogEnabled(),
                dataSourceProperties.isReadCacheEnabled(),
                dataSourceProperties.getReadCacheTtlSeconds(),
                dataSourceProperties.getReadCacheMaxEntries());
    }
}
