-- Boat travel classification support.
-- Water surface polygons are stored here so trip classification can derive
-- positive water evidence without depending on external network access at runtime.

CREATE TABLE IF NOT EXISTS geo_dataset_metadata
(
    dataset_name   VARCHAR(100) PRIMARY KEY,
    source_url     TEXT         NOT NULL,
    source_version VARCHAR(100),
    license        TEXT,
    attribution    TEXT,
    feature_count  INTEGER      NOT NULL DEFAULT 0,
    imported_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS water_surface_polygons
(
    id         BIGSERIAL PRIMARY KEY,
    source     VARCHAR(100) NOT NULL,
    source_id  TEXT,
    name       TEXT,
    water_type VARCHAR(50),
    geom       GEOMETRY(MultiPolygon, 4326) NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_water_surface_polygons_geom
    ON water_surface_polygons USING GIST (geom);

CREATE INDEX IF NOT EXISTS idx_water_surface_polygons_source
    ON water_surface_polygons (source);

CREATE TABLE IF NOT EXISTS gps_point_environment
(
    gps_point_id                BIGINT PRIMARY KEY REFERENCES gps_points (id) ON DELETE CASCADE,
    environment_dataset_version TEXT         NOT NULL,
    on_water                   BOOLEAN       NOT NULL,
    water_source               VARCHAR(100),
    classified_at              TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_gps_point_environment_dataset
    ON gps_point_environment (environment_dataset_version);

CREATE TABLE IF NOT EXISTS water_dataset_state
(
    dataset_key         VARCHAR(100) PRIMARY KEY,
    status              VARCHAR(30)  NOT NULL,
    phase               TEXT,
    progress_percentage INTEGER      NOT NULL DEFAULT 0,
    downloaded_bytes    BIGINT,
    total_bytes         BIGINT,
    artifact_url        TEXT,
    local_path          TEXT,
    sha256              VARCHAR(128),
    dataset_version     TEXT,
    feature_count       INTEGER      NOT NULL DEFAULT 0,
    error_code          VARCHAR(100),
    error_message       TEXT,
    started_at          TIMESTAMPTZ,
    completed_at        TIMESTAMPTZ,
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS boat_environment_jobs
(
    job_id                  UUID PRIMARY KEY,
    user_id                 UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    status                  VARCHAR(30)  NOT NULL,
    dataset_status          VARCHAR(30)  NOT NULL,
    user_environment_status VARCHAR(30)  NOT NULL,
    phase                   TEXT,
    progress_percentage     INTEGER      NOT NULL DEFAULT 0,
    downloaded_bytes        BIGINT,
    total_bytes             BIGINT,
    processed_gps_points    BIGINT,
    total_gps_points        BIGINT,
    error_code              VARCHAR(100),
    error_message           TEXT,
    docs_url                TEXT,
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    completed_at            TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_boat_environment_jobs_user_status
    ON boat_environment_jobs (user_id, status, updated_at DESC);

ALTER TABLE timeline_trips
    ADD COLUMN IF NOT EXISTS water_distance_meters DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS water_distance_ratio DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS longest_water_segment_meters DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS water_sample_count INTEGER,
    ADD COLUMN IF NOT EXISTS water_evidence_available BOOLEAN;

COMMENT ON TABLE geo_dataset_metadata IS
    'Imported geospatial dataset metadata used for cache invalidation and attribution.';
COMMENT ON TABLE water_surface_polygons IS
    'Positive water-surface polygons used for boat classification, such as lakes, reservoirs, rivers, canals, seas, and oceans.';
COMMENT ON TABLE gps_point_environment IS
    'Derived GPS point environmental evidence. Populated lazily in bulk during timeline reconciliation when boat detection is enabled.';
COMMENT ON TABLE water_dataset_state IS
    'Global import/download state for the Boat water-surface reference dataset.';
COMMENT ON TABLE boat_environment_jobs IS
    'Per-user Boat setup and GPS water-evidence enrichment job progress.';
COMMENT ON COLUMN gps_point_environment.on_water IS
    'True when the point is inside an imported water-surface polygon.';
COMMENT ON COLUMN gps_point_environment.water_source IS
    'Source dataset of the matched water surface, when on_water is true.';
COMMENT ON COLUMN gps_point_environment.environment_dataset_version IS
    'Combined water dataset version used to calculate this row.';
COMMENT ON COLUMN timeline_trips.water_distance_meters IS
    'Total usable trip segment distance classified as water during automatic classification.';
COMMENT ON COLUMN timeline_trips.water_distance_ratio IS
    'Ratio of usable trip distance classified as water, from 0.0 to 1.0.';
COMMENT ON COLUMN timeline_trips.longest_water_segment_meters IS
    'Longest continuous usable water segment in meters.';
COMMENT ON COLUMN timeline_trips.water_sample_count IS
    'Number of usable GPS segments sampled for water evidence.';
COMMENT ON COLUMN timeline_trips.water_evidence_available IS
    'True when water evidence was calculated from sufficient GPS/path data.';
