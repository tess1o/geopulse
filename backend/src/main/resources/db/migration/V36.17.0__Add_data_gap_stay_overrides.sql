CREATE TABLE IF NOT EXISTS timeline_data_gap_stay_overrides (
    id BIGSERIAL PRIMARY KEY,
    user_id UUID NOT NULL,
    data_gap_id BIGINT,
    stay_id BIGINT,
    location_strategy VARCHAR(40) NOT NULL,
    selected_favorite_id BIGINT,
    selected_geocoding_id BIGINT,
    selected_latitude DOUBLE PRECISION,
    selected_longitude DOUBLE PRECISION,
    selected_location_name VARCHAR(500),
    source_gap_start_time TIMESTAMP WITH TIME ZONE NOT NULL,
    source_gap_end_time TIMESTAMP WITH TIME ZONE NOT NULL,
    source_gap_duration_seconds BIGINT NOT NULL,
    source_before_latitude DOUBLE PRECISION NOT NULL,
    source_before_longitude DOUBLE PRECISION NOT NULL,
    source_after_latitude DOUBLE PRECISION NOT NULL,
    source_after_longitude DOUBLE PRECISION NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_timeline_data_gap_stay_overrides_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_timeline_data_gap_stay_overrides_data_gap
        FOREIGN KEY (data_gap_id) REFERENCES timeline_data_gaps(id) ON DELETE SET NULL,
    CONSTRAINT fk_timeline_data_gap_stay_overrides_stay
        FOREIGN KEY (stay_id) REFERENCES timeline_stays(id) ON DELETE SET NULL,
    CONSTRAINT fk_timeline_data_gap_stay_overrides_favorite
        FOREIGN KEY (selected_favorite_id) REFERENCES favorite_locations(id) ON DELETE SET NULL,
    CONSTRAINT fk_timeline_data_gap_stay_overrides_geocoding
        FOREIGN KEY (selected_geocoding_id) REFERENCES reverse_geocoding_location(id) ON DELETE SET NULL,
    CONSTRAINT chk_timeline_data_gap_stay_overrides_strategy
        CHECK (location_strategy IN ('LATEST_POINT', 'SELECTED_LOCATION')),
    CONSTRAINT chk_timeline_data_gap_stay_overrides_source_time_order
        CHECK (source_gap_end_time > source_gap_start_time),
    CONSTRAINT chk_timeline_data_gap_stay_overrides_source_duration
        CHECK (source_gap_duration_seconds > 0)
);

CREATE INDEX IF NOT EXISTS idx_timeline_data_gap_stay_overrides_user
    ON timeline_data_gap_stay_overrides (user_id);

CREATE INDEX IF NOT EXISTS idx_timeline_data_gap_stay_overrides_user_source_start
    ON timeline_data_gap_stay_overrides (user_id, source_gap_start_time);

CREATE INDEX IF NOT EXISTS idx_timeline_data_gap_stay_overrides_stay
    ON timeline_data_gap_stay_overrides (stay_id)
    WHERE stay_id IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS ux_timeline_data_gap_stay_overrides_user_source_gap
    ON timeline_data_gap_stay_overrides (user_id, source_gap_start_time, source_gap_end_time);

COMMENT ON TABLE timeline_data_gap_stay_overrides IS
    'Manual Data Gap -> Stay overrides that can be re-applied after timeline regeneration';
