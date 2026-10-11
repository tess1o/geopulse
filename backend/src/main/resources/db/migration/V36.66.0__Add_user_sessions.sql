-- Server-side sessions replace signed JWT access/refresh tokens.
-- Every sign-in (browser, API session, mobile app) is one row; only SHA-256 hashes of the
-- credentials are stored. Revoking a session takes effect on the next request.
CREATE TABLE user_sessions (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    client_type VARCHAR(16) NOT NULL,
    auth_method VARCHAR(16) NOT NULL,

    -- Browser: hash of the session cookie. API: hash of the current access token.
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    access_expires_at TIMESTAMP WITH TIME ZONE,
    refresh_token_hash VARCHAR(64) UNIQUE,
    previous_refresh_token_hash VARCHAR(64),
    refresh_rotated_at TIMESTAMP WITH TIME ZONE,

    -- Identity provider session, set for OIDC sign-ins (back-channel and RP-initiated logout).
    oidc_provider VARCHAR(50),
    idp_subject VARCHAR(255),
    idp_session_id VARCHAR(255),
    idp_id_token TEXT,

    user_agent VARCHAR(500),
    ip_address VARCHAR(45),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    last_seen_at TIMESTAMP WITH TIME ZONE NOT NULL,
    idle_expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    absolute_expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked_at TIMESTAMP WITH TIME ZONE,
    revoked_reason VARCHAR(32)
);

CREATE INDEX idx_user_sessions_user_id ON user_sessions(user_id);
CREATE INDEX idx_user_sessions_previous_refresh ON user_sessions(previous_refresh_token_hash)
    WHERE previous_refresh_token_hash IS NOT NULL;
CREATE INDEX idx_user_sessions_idp_session ON user_sessions(oidc_provider, idp_session_id)
    WHERE idp_session_id IS NOT NULL;
CREATE INDEX idx_user_sessions_idp_subject ON user_sessions(oidc_provider, idp_subject)
    WHERE idp_subject IS NOT NULL;
CREATE INDEX idx_user_sessions_expiry ON user_sessions(idle_expires_at, absolute_expires_at);
