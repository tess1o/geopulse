-- Cached end_session_endpoint from the provider's discovery document (RP-initiated logout).
ALTER TABLE oidc_providers ADD COLUMN end_session_endpoint VARCHAR(500);

-- Cached metadata predates this column; clear it so every provider is rediscovered on next use
-- and picks up its end_session_endpoint.
UPDATE oidc_providers
SET authorization_endpoint = NULL,
    token_endpoint = NULL,
    userinfo_endpoint = NULL,
    jwks_uri = NULL,
    issuer = NULL,
    metadata_cached_at = NULL,
    metadata_valid = FALSE;
