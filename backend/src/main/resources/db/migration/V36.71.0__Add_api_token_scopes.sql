-- What a personal API token may do (comma-separated scopes, fixed at creation). Tokens created before
-- scopes existed keep full access.
ALTER TABLE user_api_tokens
    ADD COLUMN scopes VARCHAR(200) NOT NULL DEFAULT 'all';
