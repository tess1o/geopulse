-- Simplified telemetry model:
-- - Keep telemetry payload on gps_points.telemetry.
-- - Remove per-source-instance telemetry config and source_config linkage from points.
-- - Add global telemetry mapping per user + source type.
-- - Add timeline display toggle for current-location telemetry popup.

CREATE TABLE IF NOT EXISTS gps_source_type_telemetry_config (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    source_type VARCHAR(32) NOT NULL,
    mapping JSONB NOT NULL,
    CONSTRAINT fk_gps_source_type_telemetry_config_user
        FOREIGN KEY (user_id) REFERENCES users (id)
            ON DELETE CASCADE,
    CONSTRAINT uq_gps_source_type_telemetry_config_user_source
        UNIQUE (user_id, source_type)
);

CREATE INDEX IF NOT EXISTS idx_gps_source_type_telemetry_config_user_id
    ON gps_source_type_telemetry_config (user_id);

ALTER TABLE gps_points
    DROP CONSTRAINT IF EXISTS fk_gps_points_source_config;

DROP INDEX IF EXISTS idx_gps_points_source_config_id;

ALTER TABLE gps_points
    ADD COLUMN IF NOT EXISTS telemetry JSONB;

ALTER TABLE gps_points
    DROP COLUMN IF EXISTS source_config_id;

ALTER TABLE gps_source_config
    DROP COLUMN IF EXISTS telemetry_enabled;

ALTER TABLE gps_source_config
    DROP COLUMN IF EXISTS telemetry_mapping;

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS timeline_display_show_current_location_telemetry BOOLEAN NOT NULL DEFAULT true;
