CREATE TABLE IF NOT EXISTS notification_delivery_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    fingerprint VARCHAR(256) NOT NULL,
    title VARCHAR(512) NOT NULL,
    text CLOB NOT NULL,
    delivered_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_notification_delivery_fingerprint_time
    ON notification_delivery_log (fingerprint, delivered_at);

CREATE TABLE IF NOT EXISTS prompt_security_audit (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id VARCHAR(128) NOT NULL,
    risk_type VARCHAR(128) NOT NULL,
    matched_pattern VARCHAR(256),
    question CLOB NOT NULL,
    created_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_prompt_security_audit_session_time
    ON prompt_security_audit (session_id, created_at);
