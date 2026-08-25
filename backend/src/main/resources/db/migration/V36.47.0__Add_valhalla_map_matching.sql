ALTER TABLE users
    ADD COLUMN IF NOT EXISTS timeline_display_map_matching_enabled BOOLEAN NOT NULL DEFAULT false;

COMMENT ON COLUMN users.timeline_display_map_matching_enabled IS
    'Display-only: enable cached Valhalla map matching for timeline trip paths';

CREATE TABLE IF NOT EXISTS timeline_trip_path_matches (
    id BIGSERIAL PRIMARY KEY,
    trip_id BIGINT REFERENCES timeline_trips(id) ON DELETE SET NULL,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    provider VARCHAR(40) NOT NULL,
    profile VARCHAR(40) NOT NULL,
    config_hash VARCHAR(128) NOT NULL,
    input_hash VARCHAR(128) NOT NULL,
    status VARCHAR(40) NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_attempt_at TIMESTAMPTZ,
    locked_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    last_error TEXT,
    matched_segments_json TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_timeline_trip_path_matches_current
        UNIQUE (user_id, provider, profile, config_hash, input_hash)
);

CREATE INDEX IF NOT EXISTS idx_timeline_trip_path_matches_user_status
    ON timeline_trip_path_matches (user_id, status, next_attempt_at);

CREATE INDEX IF NOT EXISTS idx_timeline_trip_path_matches_trip
    ON timeline_trip_path_matches (trip_id);

CREATE INDEX IF NOT EXISTS idx_timeline_trip_path_matches_cache_key
    ON timeline_trip_path_matches (user_id, provider, profile, config_hash, input_hash);
