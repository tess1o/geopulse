-- Finish the move from the per-type override tables to timeline_edits.
--
-- V36.81 copied the rows that existed at that time and marked them migrated. The movement-type and data-gap
-- endpoints kept writing new legacy rows afterwards, and imported legacy rows were never marked migrated. This
-- migration copies those remaining rows, repairs the two losses in V36.81 (split movement types and unnamed
-- LATEST_POINT gap stays) and leaves the legacy tables read-only for one release.
--
-- This migration must never fail on data: the backend cannot start if it does. Legacy tables only require a positive
-- duration, so durations are capped at one year (the bound V36.80.1 applied) before any interval is built, and a
-- legacy row that cannot become a valid correction (no positive duration, unknown movement type, blank name) is left
-- in the archive instead of violating a constraint of timeline_edits.

-- 1. Convert every legacy row that is still outside timeline_edits, in the runtime order the old re-apply used:
--    split -> movement -> gap -> stay name. Sequence continues after the user's existing corrections.
WITH max_seq AS (
    SELECT user_id, COALESCE(MAX(seq), 0) AS max_seq
    FROM timeline_edits
    GROUP BY user_id
), legacy AS (
    SELECT user_id, 1 AS kind_order, created_at, id,
           'FORCE_STAY'::varchar AS kind,
           stay_start_time AS anchor_start, stay_end_time AS anchor_end,
           source_trip_timestamp AS affected_from,
           source_trip_timestamp + make_interval(secs => LEAST(source_trip_duration_seconds, 31536000)) AS affected_to,
           jsonb_strip_nulls(jsonb_build_object(
               'latitude', stay_latitude, 'longitude', stay_longitude, 'locationName', stay_location_name,
               'movementType', source_trip_movement_type, 'locationNameCustom', stay_location_name_custom)) AS payload,
           favorite_id, geocoding_id, stay_latitude, stay_longitude, stay_location_name
    FROM timeline_trip_stay_split_overrides
    WHERE migrated_to_timeline_edits = FALSE
      AND source_trip_duration_seconds > 0
      AND stay_end_time > stay_start_time
    UNION ALL
    SELECT user_id, 2, created_at, id, 'SET_MOVEMENT_TYPE',
           source_trip_timestamp,
           source_trip_timestamp + make_interval(secs => LEAST(source_trip_duration_seconds, 31536000)),
           source_trip_timestamp,
           source_trip_timestamp + make_interval(secs => LEAST(source_trip_duration_seconds, 31536000)),
           jsonb_strip_nulls(jsonb_build_object(
               'sourceType', 'TRIP', 'movementType', movement_type,
               'sourceStartLatitude', source_start_latitude, 'sourceStartLongitude', source_start_longitude,
               'sourceEndLatitude', source_end_latitude, 'sourceEndLongitude', source_end_longitude)),
           NULL, NULL, NULL, NULL, NULL
    FROM timeline_trip_movement_overrides
    WHERE migrated_to_timeline_edits = FALSE
      AND source_trip_duration_seconds > 0
      AND movement_type IN ('WALK', 'BICYCLE', 'RUNNING', 'CAR', 'MOTORCYCLE',
                            'PUBLIC_TRANSPORT', 'TRAIN', 'FLIGHT', 'BOAT', 'UNKNOWN')
    UNION ALL
    SELECT user_id, 3, created_at, id, 'FORCE_STAY',
           source_gap_start_time, source_gap_end_time, source_gap_start_time, source_gap_end_time,
           jsonb_strip_nulls(jsonb_build_object(
               'latitude', COALESCE(selected_latitude, source_before_latitude),
               'longitude', COALESCE(selected_longitude, source_before_longitude),
               'locationName', selected_location_name,
               'resolveLocationName', CASE WHEN selected_location_name IS NULL THEN TRUE END)),
           selected_favorite_id, selected_geocoding_id,
           COALESCE(selected_latitude, source_before_latitude),
           COALESCE(selected_longitude, source_before_longitude), selected_location_name
    FROM timeline_data_gap_stay_overrides
    WHERE migrated_to_timeline_edits = FALSE
      AND source_gap_end_time > source_gap_start_time
    UNION ALL
    SELECT user_id, 4, created_at, id, 'SET_VISIT_NAME',
           stay_start_time, stay_end_time, stay_start_time, stay_end_time,
           jsonb_build_object('visitName', location_name),
           NULL, NULL, NULL, NULL, NULL
    FROM timeline_stay_name_overrides
    WHERE migrated_to_timeline_edits = FALSE
      AND location_name ~ '[^[:space:]]'
      AND length(location_name) <= 500
      AND stay_end_time > stay_start_time
), ordered AS (
    SELECT legacy.*,
           ROW_NUMBER() OVER (PARTITION BY user_id ORDER BY kind_order, created_at, id)
           + COALESCE(max_seq.max_seq, 0) AS edit_seq
    FROM legacy
    LEFT JOIN max_seq USING (user_id)
)
INSERT INTO timeline_edits (
    user_id, seq, kind, anchor_start, anchor_end, affected_from, affected_to, payload,
    place_favorite_id, place_geocoding_id, place_latitude, place_longitude, place_name,
    status, created_at, updated_at)
SELECT user_id, edit_seq, kind, anchor_start, anchor_end, affected_from, affected_to, payload,
       favorite_id, geocoding_id, stay_latitude, stay_longitude, stay_location_name,
       'ACTIVE', created_at, created_at
FROM ordered;

UPDATE timeline_trip_stay_split_overrides SET migrated_to_timeline_edits = TRUE
WHERE migrated_to_timeline_edits = FALSE;
UPDATE timeline_trip_movement_overrides SET migrated_to_timeline_edits = TRUE
WHERE migrated_to_timeline_edits = FALSE;
UPDATE timeline_data_gap_stay_overrides SET migrated_to_timeline_edits = TRUE
WHERE migrated_to_timeline_edits = FALSE;
UPDATE timeline_stay_name_overrides SET migrated_to_timeline_edits = TRUE
WHERE migrated_to_timeline_edits = FALSE;

-- 2. V36.81 put a split's movement type into the FORCE_STAY payload, which the applier ignores. Add one
--    SET_MOVEMENT_TYPE edit per resulting leg. The split is already in place when these run, so the legs are the
--    events those intervals contain. A leg is repaired only while its split is still in effect, and never when an
--    active movement-type correction already covers it: these rows run after every later user correction, so they
--    must not override a type the user chose afterwards, nor retype a trip whose split was undone.
WITH max_seq AS (
    SELECT user_id, COALESCE(MAX(seq), 0) AS max_seq
    FROM timeline_edits
    GROUP BY user_id
), legs AS (
    SELECT user_id, source_trip_movement_type AS movement_type, created_at, id, 1 AS leg_order,
           stay_start_time, stay_end_time,
           source_trip_timestamp AS anchor_start, stay_start_time AS anchor_end,
           source_start_latitude, source_start_longitude, source_end_latitude, source_end_longitude
    FROM timeline_trip_stay_split_overrides
    WHERE source_trip_movement_type IN ('WALK', 'BICYCLE', 'RUNNING', 'CAR', 'MOTORCYCLE',
                                        'PUBLIC_TRANSPORT', 'TRAIN', 'FLIGHT', 'BOAT', 'UNKNOWN')
      AND stay_start_time > source_trip_timestamp
      AND stay_end_time < source_trip_timestamp + make_interval(secs => LEAST(source_trip_duration_seconds, 31536000))
    UNION ALL
    SELECT user_id, source_trip_movement_type, created_at, id, 2,
           stay_start_time, stay_end_time,
           stay_end_time,
           source_trip_timestamp + make_interval(secs => LEAST(source_trip_duration_seconds, 31536000)),
           source_start_latitude, source_start_longitude, source_end_latitude, source_end_longitude
    FROM timeline_trip_stay_split_overrides
    WHERE source_trip_movement_type IN ('WALK', 'BICYCLE', 'RUNNING', 'CAR', 'MOTORCYCLE',
                                        'PUBLIC_TRANSPORT', 'TRAIN', 'FLIGHT', 'BOAT', 'UNKNOWN')
      AND stay_start_time > source_trip_timestamp
      AND stay_end_time < source_trip_timestamp + make_interval(secs => LEAST(source_trip_duration_seconds, 31536000))
), missing AS (
    SELECT legs.*
    FROM legs
    WHERE EXISTS (
        SELECT 1 FROM timeline_edits split
        WHERE split.user_id = legs.user_id
          AND split.kind = 'FORCE_STAY'
          AND split.status = 'ACTIVE'
          AND split.anchor_start = legs.stay_start_time
          AND split.anchor_end = legs.stay_end_time
    )
    AND NOT EXISTS (
        SELECT 1 FROM timeline_edits edit
        WHERE edit.user_id = legs.user_id
          AND edit.kind = 'SET_MOVEMENT_TYPE'
          AND edit.status = 'ACTIVE'
          AND edit.anchor_start < legs.anchor_end
          AND edit.anchor_end > legs.anchor_start
    )
), ordered AS (
    SELECT missing.*,
           ROW_NUMBER() OVER (PARTITION BY user_id ORDER BY created_at, id, leg_order)
           + COALESCE(max_seq.max_seq, 0) AS edit_seq
    FROM missing
    LEFT JOIN max_seq USING (user_id)
)
INSERT INTO timeline_edits (
    user_id, seq, kind, anchor_start, anchor_end, affected_from, affected_to, payload,
    status, created_at, updated_at)
SELECT user_id, edit_seq, 'SET_MOVEMENT_TYPE', anchor_start, anchor_end, anchor_start, anchor_end,
       jsonb_strip_nulls(jsonb_build_object(
           'sourceType', 'TRIP', 'movementType', movement_type,
           'sourceStartLatitude', source_start_latitude, 'sourceStartLongitude', source_start_longitude,
           'sourceEndLatitude', source_end_latitude, 'sourceEndLongitude', source_end_longitude)),
       'ACTIVE', created_at, created_at
FROM ordered;

-- 3. LATEST_POINT gap stays had no name. Let the post-edit geocoding pass resolve them instead of showing
--    "Unknown location".
UPDATE timeline_edits edit
SET payload = edit.payload || jsonb_build_object('resolveLocationName', TRUE)
FROM timeline_data_gap_stay_overrides override
WHERE edit.user_id = override.user_id
  AND edit.kind = 'FORCE_STAY'
  AND edit.status = 'ACTIVE'
  AND edit.anchor_start = override.source_gap_start_time
  AND edit.anchor_end = override.source_gap_end_time
  AND override.location_strategy = 'LATEST_POINT'
  AND jsonb_typeof(edit.payload) = 'object'
  AND COALESCE(edit.payload ->> 'locationName', '') = '';

-- 4. The legacy tables are now a read-only archive. The next release drops them together with the re-apply
--    services that no longer run.
COMMENT ON TABLE timeline_trip_stay_split_overrides IS
    'Read-only archive. Corrections live in timeline_edits, see V36.89.0.';
COMMENT ON TABLE timeline_trip_movement_overrides IS
    'Read-only archive. Corrections live in timeline_edits, see V36.89.0.';
COMMENT ON TABLE timeline_data_gap_stay_overrides IS
    'Read-only archive. Corrections live in timeline_edits, see V36.89.0.';
COMMENT ON TABLE timeline_stay_name_overrides IS
    'Read-only archive. Corrections live in timeline_edits, see V36.89.0.';
