CREATE TABLE geocoding_provider_configs (
    id BIGINT PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE,
    display_name VARCHAR(100) NOT NULL,
    type VARCHAR(30) NOT NULL,
    url VARCHAR(500) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    language VARCHAR(50),
    headers_json TEXT,
    headers_key_id VARCHAR(20),
    delay_ms INTEGER,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_geocoding_provider_type CHECK (type IN ('photon', 'nominatim')),
    CONSTRAINT chk_geocoding_provider_delay CHECK (delay_ms IS NULL OR delay_ms >= 0)
);

CREATE SEQUENCE geocoding_provider_configs_seq START WITH 1 INCREMENT BY 50;

CREATE INDEX idx_geocoding_provider_configs_enabled ON geocoding_provider_configs(enabled);
CREATE INDEX idx_geocoding_provider_configs_type ON geocoding_provider_configs(type);
