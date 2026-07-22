-- Add first-class GeoPulse notes and live Memos integration preferences.

ALTER TABLE users
    ADD COLUMN memos_preferences JSONB;

COMMENT ON COLUMN users.memos_preferences IS 'JSON object containing Memos integration configuration: serverUrl, apiKey, enabled, defaultSaveDestination, defaultVisibility';

ALTER TABLE shared_link
    ADD COLUMN show_notes BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN shared_link.show_notes IS 'Whether shared timeline links should include GeoPulse and Memos notes for the shared date range';

CREATE TABLE timeline_notes (
    id BIGSERIAL PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title VARCHAR(255),
    content_markdown TEXT NOT NULL,
    snippet VARCHAR(500),
    event_time TIMESTAMPTZ NOT NULL,
    location geometry(Point, 4326),
    location_source VARCHAR(40) NOT NULL DEFAULT 'NONE',
    anchor_type VARCHAR(24) NOT NULL DEFAULT 'TIMESTAMP',
    stay_id BIGINT REFERENCES timeline_stays(id) ON DELETE SET NULL,
    trip_id BIGINT REFERENCES timeline_trips(id) ON DELETE SET NULL,
    source_item_start_time TIMESTAMPTZ,
    source_item_duration_seconds BIGINT,
    source_start_latitude DOUBLE PRECISION,
    source_start_longitude DOUBLE PRECISION,
    source_end_latitude DOUBLE PRECISION,
    source_end_longitude DOUBLE PRECISION,
    source_distance_meters BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ,
    CONSTRAINT chk_timeline_notes_anchor_type
        CHECK (anchor_type IN ('TIMESTAMP', 'STAY', 'TRIP')),
    CONSTRAINT chk_timeline_notes_location_source
        CHECK (location_source IN ('EXPLICIT', 'DERIVED_STAY', 'DERIVED_TRIP_GPS', 'DERIVED_TRIP_INTERPOLATED', 'NONE')),
    CONSTRAINT chk_timeline_notes_single_anchor
        CHECK (
            (anchor_type = 'STAY' AND stay_id IS NOT NULL AND trip_id IS NULL)
            OR (anchor_type = 'TRIP' AND trip_id IS NOT NULL AND stay_id IS NULL)
            OR (anchor_type = 'TIMESTAMP')
            OR (stay_id IS NULL AND trip_id IS NULL)
        )
);

CREATE INDEX idx_timeline_notes_user_event_time
    ON timeline_notes(user_id, event_time)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_timeline_notes_stay
    ON timeline_notes(stay_id)
    WHERE stay_id IS NOT NULL AND deleted_at IS NULL;

CREATE INDEX idx_timeline_notes_trip
    ON timeline_notes(trip_id)
    WHERE trip_id IS NOT NULL AND deleted_at IS NULL;

CREATE INDEX idx_timeline_notes_location
    ON timeline_notes USING GIST(location)
    WHERE location IS NOT NULL AND deleted_at IS NULL;

CREATE INDEX idx_timeline_notes_unmatched_anchors
    ON timeline_notes(user_id, anchor_type, source_item_start_time)
    WHERE deleted_at IS NULL AND (stay_id IS NULL OR trip_id IS NULL);
