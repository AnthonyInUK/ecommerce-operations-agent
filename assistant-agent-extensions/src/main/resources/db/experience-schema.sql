-- Experience table schema (H2 / PostgreSQL compatible)
-- Created on first startup by JdbcExperienceRepository when the table does not exist.

CREATE TABLE IF NOT EXISTS assistant_experience (
    id                      VARCHAR(64)   NOT NULL PRIMARY KEY,
    type                    VARCHAR(32)   NOT NULL,
    name                    VARCHAR(512),
    description             TEXT,
    content                 TEXT,
    disclosure_strategy     VARCHAR(32),
    tags_json               TEXT,
    associated_tools_json   TEXT,
    related_experiences_json TEXT,
    metadata_json           TEXT,
    artifact_json           TEXT,
    fast_intent_config_json TEXT,
    references_json         TEXT,
    assets_json             TEXT,
    created_at              TIMESTAMP     NOT NULL,
    updated_at              TIMESTAMP     NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_experience_type ON assistant_experience (type);
