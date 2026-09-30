-- token_hash was created as CHAR(64); the entity maps it as VARCHAR(64).
-- SHA-256 hex hashes are always 64 chars, so the conversion is lossless.
ALTER TABLE user_api_tokens ALTER COLUMN token_hash TYPE VARCHAR(64);
