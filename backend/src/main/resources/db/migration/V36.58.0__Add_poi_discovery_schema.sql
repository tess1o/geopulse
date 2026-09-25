-- POI discovery for trips: places worth visiting in an area the user may never have been to.
--
-- Source: Wikidata (items carrying a P18 image, within a bounding box), with images served
-- from Wikimedia Commons. Reintroduces the POI persistence removed in V36.3.0, but keyed on
-- (provider, external_id) and deliberately NOT user-scoped: a place in Paris is the same fact
-- for every user, so sharing the cache is what keeps outbound traffic polite.
--
-- Data licensing: Wikidata is CC0. Commons media is per-file licensed, so the licence
-- metadata below is mandatory before any image is served to a client.

CREATE TABLE IF NOT EXISTS poi_cache (
    id              BIGSERIAL PRIMARY KEY,
    provider        VARCHAR(30)  NOT NULL,        -- WIKIDATA
    external_id     VARCHAR(80)  NOT NULL,        -- Q42
    name            VARCHAR(400) NOT NULL,
    description     VARCHAR(1000),
    latitude        DOUBLE PRECISION NOT NULL,
    longitude       DOUBLE PRECISION NOT NULL,
    image_file      VARCHAR(400),                 -- Commons file name, if any
    fetched_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    expires_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ux_poi_cache_provider_external UNIQUE (provider, external_id),
    CONSTRAINT chk_poi_cache_lat CHECK (latitude BETWEEN -90 AND 90),
    CONSTRAINT chk_poi_cache_lon CHECK (longitude BETWEEN -180 AND 180)
);

CREATE INDEX IF NOT EXISTS idx_poi_cache_bbox    ON poi_cache (latitude, longitude);
CREATE INDEX IF NOT EXISTS idx_poi_cache_expires ON poi_cache (expires_at);

-- Tracks which bounding boxes have already been pulled from the provider, so a repeat
-- lookup for the same area is a local read rather than another SPARQL round trip
-- (a bbox query takes ~8s upstream). Keyed on the area only - intentionally not linked
-- to a user, so nobody's travel intent is recorded here.
CREATE TABLE IF NOT EXISTS poi_area_query (
    id           BIGSERIAL PRIMARY KEY,
    area_hash    VARCHAR(64) NOT NULL,
    poi_count    INTEGER     NOT NULL,
    fetched_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    expires_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ux_poi_area_query_hash UNIQUE (area_hash)
);

CREATE INDEX IF NOT EXISTS idx_poi_area_query_expires ON poi_area_query (expires_at);

-- Image bytes fetched from Wikimedia Commons, served to the browser through our own
-- endpoint rather than hotlinked. The licence columns are required before serving:
-- Commons licences are per-file, so attribution cannot be derived later.
CREATE TABLE IF NOT EXISTS poi_image_cache (
    id              BIGSERIAL PRIMARY KEY,
    image_key       VARCHAR(400) NOT NULL,        -- '<file>@<width>'
    file_name       VARCHAR(400) NOT NULL,
    remote_url      TEXT         NOT NULL,
    file_page_url   TEXT,
    content_type    VARCHAR(80)  NOT NULL,
    content_length  BIGINT       NOT NULL,
    image_bytes     BYTEA        NOT NULL,
    author          VARCHAR(300),
    license_name    VARCHAR(120),
    license_url     TEXT,
    fetched_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    expires_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ux_poi_image_cache_key UNIQUE (image_key),
    -- A row without licence metadata must never be served, so it must not be storable.
    CONSTRAINT chk_poi_image_cache_license CHECK (license_name IS NOT NULL),
    CONSTRAINT chk_poi_image_cache_size CHECK (content_length > 0 AND content_length <= 2097152)
);

CREATE INDEX IF NOT EXISTS idx_poi_image_cache_expires ON poi_image_cache (expires_at);

COMMENT ON TABLE poi_cache IS
    'POIs worth visiting, from Wikidata (CC0). Global, not per-user, by design.';
COMMENT ON TABLE poi_image_cache IS
    'Wikimedia Commons image bytes. Per-row licence metadata is mandatory before serving.';
COMMENT ON COLUMN poi_area_query.area_hash IS
    'sha256 of the rounded bounding box; no user id is stored, so intent is not recorded.';
