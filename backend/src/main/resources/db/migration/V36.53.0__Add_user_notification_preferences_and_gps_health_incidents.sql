ALTER TABLE users
    ADD COLUMN IF NOT EXISTS notification_preferences JSONB;

ALTER TABLE user_notifications
    ADD COLUMN IF NOT EXISTS in_app_enabled BOOLEAN NOT NULL DEFAULT TRUE;

CREATE TABLE IF NOT EXISTS incidents (
    id VARCHAR(128) PRIMARY KEY,
    type VARCHAR(64) NOT NULL,
    user_id UUID REFERENCES users(id) ON DELETE CASCADE,
    opened_at TIMESTAMPTZ,
    last_recovered_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_gps_points_user_created_at
    ON gps_points (user_id, created_at DESC);
