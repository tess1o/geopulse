CREATE TABLE IF NOT EXISTS admin_restore_operations
(
    id                 UUID PRIMARY KEY,
    operation          VARCHAR(64)  NOT NULL,
    status             VARCHAR(32)  NOT NULL,
    file_name          VARCHAR(255),
    size_bytes         BIGINT,
    phase              VARCHAR(100),
    message            TEXT,
    progress_percent   INTEGER,
    processed_users    INTEGER,
    total_users        INTEGER,
    current_user_id    UUID,
    current_user_email VARCHAR(254),
    started_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    completed_at       TIMESTAMPTZ,
    error              TEXT
);

CREATE INDEX IF NOT EXISTS idx_admin_restore_operations_status_updated
    ON admin_restore_operations (status, updated_at DESC);

CREATE INDEX IF NOT EXISTS idx_admin_restore_operations_started
    ON admin_restore_operations (started_at DESC);
