CREATE TABLE IF NOT EXISTS timeline_trip_stay_split_overrides (
    id BIGSERIAL PRIMARY KEY,
    user_id UUID NOT NULL,
    stay_id BIGINT,
    stay_start_time TIMESTAMP WITH TIME ZONE NOT NULL,
    stay_end_time TIMESTAMP WITH TIME ZONE NOT NULL,
    anchor_timestamp TIMESTAMP WITH TIME ZONE NOT NULL,
    stay_latitude DOUBLE PRECISION NOT NULL,
    stay_longitude DOUBLE PRECISION NOT NULL,
    stay_location_name VARCHAR(500) NOT NULL,
    stay_location_source VARCHAR(30) NOT NULL DEFAULT 'HISTORICAL',
    favorite_id BIGINT,
    geocoding_id BIGINT,
    source_trip_timestamp TIMESTAMP WITH TIME ZONE NOT NULL,
    source_trip_duration_seconds BIGINT NOT NULL,
    source_distance_meters BIGINT NOT NULL,
    source_start_latitude DOUBLE PRECISION NOT NULL,
    source_start_longitude DOUBLE PRECISION NOT NULL,
    source_end_latitude DOUBLE PRECISION NOT NULL,
    source_end_longitude DOUBLE PRECISION NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_timeline_trip_stay_split_overrides_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_timeline_trip_stay_split_overrides_stay
        FOREIGN KEY (stay_id) REFERENCES timeline_stays(id) ON DELETE SET NULL,
    CONSTRAINT fk_timeline_trip_stay_split_overrides_favorite
        FOREIGN KEY (favorite_id) REFERENCES favorite_locations(id) ON DELETE SET NULL,
    CONSTRAINT fk_timeline_trip_stay_split_overrides_geocoding
        FOREIGN KEY (geocoding_id) REFERENCES reverse_geocoding_location(id) ON DELETE SET NULL,
    CONSTRAINT chk_timeline_trip_stay_split_overrides_time_order
        CHECK (stay_end_time > stay_start_time),
    CONSTRAINT chk_timeline_trip_stay_split_overrides_source_duration
        CHECK (source_trip_duration_seconds > 0),
    CONSTRAINT chk_timeline_trip_stay_split_overrides_location_source
        CHECK (stay_location_source IN ('FAVORITE', 'GEOCODING', 'HISTORICAL'))
);

CREATE INDEX IF NOT EXISTS idx_timeline_trip_stay_split_overrides_user
    ON timeline_trip_stay_split_overrides (user_id);

CREATE INDEX IF NOT EXISTS idx_timeline_trip_stay_split_overrides_user_stay_start
    ON timeline_trip_stay_split_overrides (user_id, stay_start_time);

CREATE INDEX IF NOT EXISTS idx_timeline_trip_stay_split_overrides_stay
    ON timeline_trip_stay_split_overrides (stay_id)
    WHERE stay_id IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS ux_timeline_trip_stay_split_overrides_user_source_stay
    ON timeline_trip_stay_split_overrides (user_id, source_trip_timestamp, stay_start_time, stay_end_time);

COMMENT ON TABLE timeline_trip_stay_split_overrides IS
    'Manual trip split overrides that insert one stay and survive timeline regeneration';
