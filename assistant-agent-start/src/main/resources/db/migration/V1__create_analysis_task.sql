-- 分析任务表（operational 库）。语法在 H2(MySQL 模式) 与 MySQL 上均可执行。
CREATE TABLE analysis_task (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    task_id       VARCHAR(64)  NOT NULL,
    session_id    VARCHAR(128),
    question      VARCHAR(2000) NOT NULL,
    status        VARCHAR(32)  NOT NULL,
    result_text   TEXT,
    error_message VARCHAR(2000),
    tool_chain    VARCHAR(1000),
    created_by    VARCHAR(128),
    submitted_at  TIMESTAMP    NOT NULL,
    started_at    TIMESTAMP    NULL,
    finished_at   TIMESTAMP    NULL,
    elapsed_ms    BIGINT,
    version       BIGINT,
    PRIMARY KEY (id),
    CONSTRAINT uk_analysis_task_task_id UNIQUE (task_id)
);

CREATE INDEX idx_analysis_task_status ON analysis_task (status);
CREATE INDEX idx_analysis_task_session ON analysis_task (session_id);
