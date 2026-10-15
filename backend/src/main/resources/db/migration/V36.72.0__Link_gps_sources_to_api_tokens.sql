-- A GPS source can belong to a personal API token instead of having its own credentials: points sent with an
-- API token (scope gps:ingest) are stored under the source linked to that token, created on first use.
ALTER TABLE gps_source_config
    ADD COLUMN api_token_id UUID NULL,
    ADD CONSTRAINT fk_gps_source_config_api_token FOREIGN KEY (api_token_id)
        REFERENCES user_api_tokens (id) ON DELETE CASCADE;

CREATE UNIQUE INDEX uq_gps_source_config_api_token_type
    ON gps_source_config (api_token_id, source_type)
    WHERE api_token_id IS NOT NULL;
