CREATE TABLE IF NOT EXISTS anomaly_workflow (
    anomaly_id VARCHAR(128) PRIMARY KEY,
    process_status VARCHAR(32) NOT NULL,
    notification_status VARCHAR(32) NOT NULL,
    confirmed_by VARCHAR(128),
    assignee_role VARCHAR(128),
    assignee_user VARCHAR(128),
    final_reason CLOB,
    close_note CLOB,
    is_false_positive BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS anomaly_workflow_events (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    anomaly_id VARCHAR(128) NOT NULL,
    actor VARCHAR(128) NOT NULL,
    action VARCHAR(256) NOT NULL,
    from_status VARCHAR(32),
    to_status VARCHAR(32),
    note CLOB,
    created_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_anomaly_workflow_events_anomaly_time
    ON anomaly_workflow_events (anomaly_id, created_at);
