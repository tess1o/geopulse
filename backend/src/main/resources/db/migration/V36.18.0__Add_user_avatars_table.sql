CREATE TABLE user_avatars (
    user_id UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    content_type VARCHAR(64) NOT NULL,
    size_bytes INTEGER NOT NULL CHECK (size_bytes > 0),
    image_data BYTEA NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_user_avatars_updated_at ON user_avatars(updated_at DESC);
