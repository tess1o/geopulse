-- Add movement type source marker to trips (AUTO vs MANUAL)
ALTER TABLE timeline_trips
    ADD COLUMN IF NOT EXISTS movement_type_source VARCHAR(20) NOT NULL DEFAULT 'AUTO';

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'chk_timeline_trips_movement_type_source'
    ) THEN
        ALTER TABLE timeline_trips
            ADD CONSTRAINT chk_timeline_trips_movement_type_source
                CHECK (movement_type_source IN ('AUTO', 'MANUAL'));
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_timeline_trips_user_movement_type_source
    ON timeline_trips (user_id, movement_type_source);

COMMENT ON COLUMN timeline_trips.movement_type_source IS
    'Source of movement type value: AUTO from classifier or MANUAL from user override';

-- Persist user movement-type overrides so they can be re-applied after timeline rebuilds
CREATE TABLE IF NOT EXISTS timeline_trip_movement_overrides (
    id BIGSERIAL PRIMARY KEY,
    user_id UUID NOT NULL,
    trip_id BIGINT,
    movement_type VARCHAR(50) NOT NULL,
    source_trip_timestamp TIMESTAMP WITH TIME ZONE NOT NULL,
    source_trip_duration_seconds BIGINT NOT NULL,
    source_distance_meters BIGINT NOT NULL,
    source_start_latitude DOUBLE PRECISION NOT NULL,
    source_start_longitude DOUBLE PRECISION NOT NULL,
    source_end_latitude DOUBLE PRECISION NOT NULL,
    source_end_longitude DOUBLE PRECISION NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_timeline_trip_movement_overrides_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_timeline_trip_movement_overrides_trip
        FOREIGN KEY (trip_id) REFERENCES timeline_trips(id) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_timeline_trip_movement_overrides_user_id
    ON timeline_trip_movement_overrides (user_id);

CREATE INDEX IF NOT EXISTS idx_timeline_trip_movement_overrides_user_timestamp
    ON timeline_trip_movement_overrides (user_id, source_trip_timestamp);

CREATE UNIQUE INDEX IF NOT EXISTS ux_timeline_trip_movement_overrides_trip_id
    ON timeline_trip_movement_overrides (trip_id)
    WHERE trip_id IS NOT NULL;

COMMENT ON TABLE timeline_trip_movement_overrides IS
    'User manual movement-type overrides that can be reattached to regenerated trips';
