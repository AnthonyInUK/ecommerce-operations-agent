-- Learning session log table (H2 / PostgreSQL compatible)
-- Created on first startup by JdbcLearningSessionRepository.
-- Each row represents one completed agent session available for offline review.

CREATE TABLE IF NOT EXISTS assistant_learning_session (
    id                   VARCHAR(64)  NOT NULL PRIMARY KEY,
    session_id           VARCHAR(128),
    tenant_id            VARCHAR(128),
    conversation_summary TEXT,
    tool_calls_json      TEXT,
    model_calls_json     TEXT,
    created_at           TIMESTAMP    NOT NULL,
    offline_processed    BOOLEAN      NOT NULL DEFAULT FALSE,
    offline_processed_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_learning_session_created_at     ON assistant_learning_session (created_at);
CREATE INDEX IF NOT EXISTS idx_learning_session_offline_processed ON assistant_learning_session (offline_processed);
