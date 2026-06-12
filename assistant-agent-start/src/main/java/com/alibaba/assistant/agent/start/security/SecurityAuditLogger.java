package com.alibaba.assistant.agent.start.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;

@Component
public class SecurityAuditLogger {

    private static final Logger log = LoggerFactory.getLogger(SecurityAuditLogger.class);

    private final JdbcTemplate jdbcTemplate;

    public SecurityAuditLogger(ObjectProvider<JdbcTemplate> jdbcTemplateProvider) {
        this.jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
    }

    public void recordBlockedPrompt(String sessionId, String question, PromptInjectionGuard.PromptRisk risk) {
        log.warn("SecurityAuditLogger#recordBlockedPrompt - reason=prompt blocked, sessionId={}, riskType={}, matchedPattern={}, question={}",
                sessionId, risk.riskType(), risk.matchedPattern(), question);
        if (jdbcTemplate == null) {
            return;
        }
        try {
            jdbcTemplate.update(
                    """
                            INSERT INTO prompt_security_audit (session_id, risk_type, matched_pattern, question, created_at)
                            VALUES (?, ?, ?, ?, ?)
                            """,
                    sessionId,
                    risk.riskType(),
                    risk.matchedPattern(),
                    question,
                    Timestamp.from(Instant.now())
            );
        }
        catch (Exception ex) {
            log.warn("SecurityAuditLogger#recordBlockedPrompt - reason=audit table unavailable, riskType={}", risk.riskType());
        }
    }
}
