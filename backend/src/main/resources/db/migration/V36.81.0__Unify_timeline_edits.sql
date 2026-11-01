ALTER TABLE timeline_stays ADD COLUMN IF NOT EXISTS visit_name VARCHAR(500);

ALTER TABLE timeline_trip_stay_split_overrides
    ADD COLUMN IF NOT EXISTS migrated_to_timeline_edits BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE timeline_trip_movement_overrides
    ADD COLUMN IF NOT EXISTS migrated_to_timeline_edits BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE timeline_data_gap_stay_overrides
    ADD COLUMN IF NOT EXISTS migrated_to_timeline_edits BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE timeline_stay_name_overrides
    ADD COLUMN IF NOT EXISTS migrated_to_timeline_edits BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE timeline_edits (
    id BIGSERIAL PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    seq BIGINT NOT NULL,
    kind VARCHAR(40) NOT NULL,
    anchor_start TIMESTAMPTZ NOT NULL,
    anchor_end TIMESTAMPTZ NOT NULL,
    affected_from TIMESTAMPTZ NOT NULL,
    affected_to TIMESTAMPTZ NOT NULL,
    payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    place_favorite_id BIGINT REFERENCES favorite_locations(id) ON DELETE SET NULL,
    place_geocoding_id BIGINT REFERENCES reverse_geocoding_location(id) ON DELETE SET NULL,
    place_latitude DOUBLE PRECISION,
    place_longitude DOUBLE PRECISION,
    place_name VARCHAR(500),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    superseded_by BIGINT REFERENCES timeline_edits(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ux_timeline_edits_user_seq UNIQUE (user_id, seq),
    CONSTRAINT chk_timeline_edits_anchor CHECK (anchor_end > anchor_start),
    CONSTRAINT chk_timeline_edits_affected CHECK (affected_to > affected_from),
    CONSTRAINT chk_timeline_edits_status CHECK (status IN ('ACTIVE', 'SUPERSEDED', 'REVERTED')),
    CONSTRAINT chk_timeline_edits_kind CHECK (kind IN (
        'SET_MOVEMENT_TYPE', 'SET_PLACE', 'SET_VISIT_NAME', 'FORCE_STAY', 'FORCE_TRIP',
        'SET_TIMES', 'SPLIT_STAY', 'MERGE', 'REMOVE_EVENT'))
);

CREATE INDEX idx_timeline_edits_user_interval
    ON timeline_edits (user_id, affected_from, affected_to);
CREATE INDEX idx_timeline_edits_user_status_seq
    ON timeline_edits (user_id, status, seq);

CREATE TABLE timeline_edit_applications (
    edit_id BIGINT PRIMARY KEY REFERENCES timeline_edits(id) ON DELETE CASCADE,
    target_type VARCHAR(20) NOT NULL,
    target_id BIGINT NOT NULL,
    applied_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_timeline_edit_applications_type CHECK (target_type IN ('STAY', 'TRIP', 'DATA_GAP'))
);

CREATE INDEX idx_timeline_edit_applications_target
    ON timeline_edit_applications (target_type, target_id);

WITH legacy AS (
    SELECT user_id, 1 AS kind_order, created_at, id,
           'FORCE_STAY'::varchar AS kind,
           stay_start_time AS anchor_start, stay_end_time AS anchor_end,
           source_trip_timestamp AS affected_from,
           source_trip_timestamp + make_interval(secs => source_trip_duration_seconds) AS affected_to,
           jsonb_build_object(
               'latitude', stay_latitude, 'longitude', stay_longitude, 'locationName', stay_location_name,
               'movementType', source_trip_movement_type, 'locationNameCustom', stay_location_name_custom) AS payload,
           favorite_id, geocoding_id, stay_latitude, stay_longitude, stay_location_name
      FROM timeline_trip_stay_split_overrides
    UNION ALL
    SELECT user_id, 2, created_at, id, 'SET_MOVEMENT_TYPE',
           source_trip_timestamp,
           source_trip_timestamp + make_interval(secs => source_trip_duration_seconds),
           source_trip_timestamp,
           source_trip_timestamp + make_interval(secs => source_trip_duration_seconds),
           jsonb_build_object('movementType', movement_type,
               'sourceStartLatitude', source_start_latitude, 'sourceStartLongitude', source_start_longitude,
               'sourceEndLatitude', source_end_latitude, 'sourceEndLongitude', source_end_longitude),
           NULL, NULL, NULL, NULL, NULL
      FROM timeline_trip_movement_overrides
    UNION ALL
    SELECT user_id, 3, created_at, id, 'FORCE_STAY',
           source_gap_start_time, source_gap_end_time, source_gap_start_time, source_gap_end_time,
           jsonb_strip_nulls(jsonb_build_object(
               'latitude', COALESCE(selected_latitude, source_before_latitude),
               'longitude', COALESCE(selected_longitude, source_before_longitude),
               'locationName', selected_location_name)),
           selected_favorite_id, selected_geocoding_id,
           COALESCE(selected_latitude, source_before_latitude),
           COALESCE(selected_longitude, source_before_longitude), selected_location_name
      FROM timeline_data_gap_stay_overrides
    UNION ALL
    SELECT user_id, 4, created_at, id, 'SET_VISIT_NAME',
           stay_start_time, stay_end_time, stay_start_time, stay_end_time,
           jsonb_build_object('visitName', location_name),
           NULL, NULL, NULL, NULL, NULL
      FROM timeline_stay_name_overrides
), ordered AS (
    SELECT legacy.*, row_number() OVER (
        PARTITION BY user_id ORDER BY kind_order, created_at, id) AS edit_seq
    FROM legacy
)
INSERT INTO timeline_edits (
    user_id, seq, kind, anchor_start, anchor_end, affected_from, affected_to, payload,
    place_favorite_id, place_geocoding_id, place_latitude, place_longitude, place_name, created_at, updated_at)
SELECT user_id, edit_seq, kind, anchor_start, anchor_end, affected_from, affected_to, payload,
       favorite_id, geocoding_id, stay_latitude, stay_longitude, stay_location_name, created_at, created_at
FROM ordered;

-- Keep the legacy rows for rollback/equivalence checks, but never replay them beside their migrated edit.
UPDATE timeline_trip_stay_split_overrides SET migrated_to_timeline_edits = TRUE;
UPDATE timeline_trip_movement_overrides SET migrated_to_timeline_edits = TRUE;
UPDATE timeline_data_gap_stay_overrides SET migrated_to_timeline_edits = TRUE;
UPDATE timeline_stay_name_overrides SET migrated_to_timeline_edits = TRUE;
