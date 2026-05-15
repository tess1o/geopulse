CREATE TABLE mobile_auth_codes (
    id UUID PRIMARY KEY,
    code VARCHAR(64) NOT NULL UNIQUE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMPTZ NOT NULL,
    deleted_at TIMESTAMPTZ
);

CREATE INDEX idx_mobile_auth_codes_expires_at ON mobile_auth_codes(expires_at);
CREATE INDEX idx_mobile_auth_codes_deleted_at ON mobile_auth_codes(deleted_at);
