package com.alibaba.assistant.agent.start.reply;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;

@Component
@ConditionalOnBean(JdbcTemplate.class)
public class PersistentNotificationDeliveryStore {

    private final JdbcTemplate jdbcTemplate;

    public PersistentNotificationDeliveryStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean isDeliveredWithinWindow(String fingerprint, long dedupWindowSeconds) {
        Timestamp threshold = Timestamp.from(Instant.now().minusSeconds(dedupWindowSeconds));
        Integer count = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM notification_delivery_log
                        WHERE fingerprint = ? AND delivered_at >= ?
                        """,
                Integer.class,
                fingerprint,
                threshold
        );
        return count != null && count > 0;
    }

    public void recordDelivery(String fingerprint, String title, String text) {
        jdbcTemplate.update(
                """
                        INSERT INTO notification_delivery_log (fingerprint, title, text, delivered_at)
                        VALUES (?, ?, ?, ?)
                        """,
                fingerprint,
                title,
                text,
                Timestamp.from(Instant.now())
        );
    }
}
