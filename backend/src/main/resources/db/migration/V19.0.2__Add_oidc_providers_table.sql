-- OIDC providers managed via DB with encryption support
CREATE TABLE oidc_providers (
    name VARCHAR(50) PRIMARY KEY,
    display_name VARCHAR(100) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT true,
    client_id VARCHAR(255) NOT NULL,
    client_secret_encrypted TEXT NOT NULL,
    client_secret_key_id VARCHAR(50),
    discovery_url VARCHAR(500) NOT NULL,
    icon VARCHAR(100),
    scopes VARCHAR(255) DEFAULT 'openid profile email',

    -- Cached metadata from discovery document
    authorization_endpoint VARCHAR(500),
    token_endpoint VARCHAR(500),
    userinfo_endpoint VARCHAR(500),
    jwks_uri VARCHAR(500),
    issuer VARCHAR(500),
    metadata_cached_at TIMESTAMP WITHOUT TIME ZONE,
    metadata_valid BOOLEAN DEFAULT false,

    -- Audit fields
    created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    created_by UUID REFERENCES users(id),
    updated_at TIMESTAMP WITHOUT TIME ZONE,
    updated_by UUID REFERENCES users(id)
);

CREATE INDEX idx_oidc_providers_enabled ON oidc_providers(enabled);
