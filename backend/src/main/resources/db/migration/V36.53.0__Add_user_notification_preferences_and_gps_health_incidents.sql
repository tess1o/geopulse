ALTER TABLE users
    ADD COLUMN IF NOT EXISTS notification_preferences JSONB;

ALTER TABLE user_notifications
    ADD COLUMN IF NOT EXISTS in_app_enabled BOOLEAN NOT NULL DEFAULT TRUE;

CREATE TABLE IF NOT EXISTS gps_health_incidents (
    user_id UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    opened_at TIMESTAMPTZ,
    last_recovered_at TIMESTAMPTZ
);
