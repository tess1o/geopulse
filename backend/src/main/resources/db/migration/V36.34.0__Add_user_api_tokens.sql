CREATE TABLE user_api_tokens (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name VARCHAR(120) NOT NULL,
    token_hash CHAR(64) NOT NULL UNIQUE,
    token_prefix VARCHAR(16) NOT NULL,
    token_suffix VARCHAR(8) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    expires_at TIMESTAMP WITH TIME ZONE,
    revoked_at TIMESTAMP WITH TIME ZONE,
    revoked_by UUID REFERENCES users(id) ON DELETE SET NULL,
    last_used_at TIMESTAMP WITH TIME ZONE,
    last_used_ip VARCHAR(45)
);

CREATE INDEX idx_user_api_tokens_user_id ON user_api_tokens(user_id);
CREATE INDEX idx_user_api_tokens_status ON user_api_tokens(user_id, revoked_at, expires_at);
CREATE INDEX idx_user_api_tokens_hash ON user_api_tokens(token_hash);
