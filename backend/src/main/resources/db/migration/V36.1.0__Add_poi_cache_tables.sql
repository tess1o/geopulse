-- Provider-agnostic POI cache tables.
-- Keeps Trip domain independent from any specific external POI service.

CREATE TABLE poi_cache (
    id BIGSERIAL PRIMARY KEY,
    source_key VARCHAR(64) NOT NULL,
    external_ref VARCHAR(255) NOT NULL,
    display_name VARCHAR(500) NOT NULL,
    category VARCHAR(128),
    latitude DOUBLE PRECISION,
    longitude DOUBLE PRECISION,
    address_short VARCHAR(500),
    image_url VARCHAR(1000),
    attribution VARCHAR(2000),
    metadata_json TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    CONSTRAINT uk_poi_cache_source_ref UNIQUE (source_key, external_ref)
);

CREATE INDEX idx_poi_cache_source_key
    ON poi_cache(source_key);

CREATE INDEX idx_poi_cache_display_name
    ON poi_cache(display_name);

CREATE TABLE poi_image_cache (
    id BIGSERIAL PRIMARY KEY,
    source_key VARCHAR(64) NOT NULL,
    external_ref VARCHAR(255) NOT NULL,
    image_url VARCHAR(1000) NOT NULL,
    thumbnail_url VARCHAR(1000),
    attribution VARCHAR(2000),
    license VARCHAR(255),
    metadata_json TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    CONSTRAINT uk_poi_image_cache_source_ref UNIQUE (source_key, external_ref)
);

