-- Update users table to make password_hash nullable for OIDC-only users
ALTER TABLE users ALTER COLUMN password_hash DROP NOT NULL;

-- User-to-OIDC provider connections
-- Using composite primary key (user_id, provider_name) for efficiency
-- Removed redundant email column to prevent data inconsistency
CREATE TABLE user_oidc_connections (
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    provider_name VARCHAR(50) NOT NULL, -- 'google', 'microsoft', 'okta', etc.
    external_user_id VARCHAR(255) NOT NULL, -- Provider's user ID
    display_name VARCHAR(255), -- Name from provider  
    avatar_url VARCHAR(500), -- Avatar from provider
    linked_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    last_login_at TIMESTAMP WITHOUT TIME ZONE,
    CONSTRAINT pk_user_oidc_connections PRIMARY KEY (user_id, provider_name),
    CONSTRAINT uk_provider_external_id UNIQUE(provider_name, external_user_id)
);

-- OIDC session state tracking (for security)
-- Using provider_name instead of provider_id since we don't have provider table
CREATE TABLE oidc_session_states (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    state_token VARCHAR(255) NOT NULL UNIQUE,
    nonce VARCHAR(255),
    provider_name VARCHAR(50) NOT NULL, -- Which provider this session is for
    redirect_uri VARCHAR(500), -- Where to redirect after authentication
    linking_user_id UUID REFERENCES users(id), -- For account linking flows
    created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP WITHOUT TIME ZONE NOT NULL
);

-- Indexes for performance
CREATE INDEX idx_user_oidc_connections_user_id ON user_oidc_connections(user_id);
CREATE INDEX idx_user_oidc_connections_external_id ON user_oidc_connections(external_user_id);
CREATE INDEX idx_oidc_session_states_token ON oidc_session_states(state_token);
CREATE INDEX idx_oidc_session_states_expires ON oidc_session_states(expires_at);

-- Cleanup task: Remove expired session states (run periodically)
-- DELETE FROM oidc_session_states WHERE expires_at < CURRENT_TIMESTAMP;