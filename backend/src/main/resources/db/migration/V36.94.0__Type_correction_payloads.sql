-- Corrections become typed records: the operation selects the JSON payload subtype, and before_values binds to a
-- record as well. A row that cannot be read that way would break loading the user's corrections and every rebuild,
-- so this migration first repairs or removes such rows, then adds the discriminator.
--
-- This migration must never fail on data: the backend cannot start if it does. Every check below only inspects JSON
-- types and matches text patterns; nothing is cast unless the pattern guarantees the cast succeeds.

ALTER TABLE timeline_edits ADD COLUMN IF NOT EXISTS payload_version INTEGER NOT NULL DEFAULT 1;

-- JSON value checks matching what the typed records accept. Session-local helpers, dropped at the end; CREATE OR
-- REPLACE keeps the migration independent of anything earlier in the same database session. A missing key and JSON null are both "absent".
CREATE OR REPLACE FUNCTION pg_temp.gp_absent(v jsonb) RETURNS boolean LANGUAGE sql IMMUTABLE AS $$
    SELECT v IS NULL OR jsonb_typeof(v) = 'null'
$$;
-- java.time.Instant: an ISO-8601 instant in UTC (what the application writes) or epoch seconds.
CREATE OR REPLACE FUNCTION pg_temp.gp_instant(v jsonb) RETURNS boolean LANGUAGE sql IMMUTABLE AS $$
    SELECT pg_temp.gp_absent(v)
        OR jsonb_typeof(v) = 'number'
        OR (jsonb_typeof(v) = 'string'
            AND (v #>> '{}') ~ '^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}(:[0-9]{2}(\.[0-9]{1,9})?)?Z$')
$$;
-- Double/Long fields: a number, or text Jackson coerces to a number.
CREATE OR REPLACE FUNCTION pg_temp.gp_number(v jsonb) RETURNS boolean LANGUAGE sql IMMUTABLE AS $$
    SELECT pg_temp.gp_absent(v)
        OR jsonb_typeof(v) = 'number'
        OR (jsonb_typeof(v) = 'string' AND (v #>> '{}') ~ '^-?[0-9]+(\.[0-9]+)?([eE][-+]?[0-9]+)?$')
$$;
-- String fields accept any scalar (Jackson coerces numbers and booleans to text), never an object or array.
CREATE OR REPLACE FUNCTION pg_temp.gp_text(v jsonb) RETURNS boolean LANGUAGE sql IMMUTABLE AS $$
    SELECT pg_temp.gp_absent(v) OR jsonb_typeof(v) IN ('string', 'number', 'boolean')
$$;
CREATE OR REPLACE FUNCTION pg_temp.gp_bool(v jsonb) RETURNS boolean LANGUAGE sql IMMUTABLE AS $$
    SELECT pg_temp.gp_absent(v)
        OR jsonb_typeof(v) = 'boolean'
        OR (jsonb_typeof(v) = 'string' AND (v #>> '{}') IN ('true', 'false'))
$$;
CREATE OR REPLACE FUNCTION pg_temp.gp_enum(v jsonb, allowed text[]) RETURNS boolean LANGUAGE sql IMMUTABLE AS $$
    SELECT pg_temp.gp_absent(v) OR (jsonb_typeof(v) = 'string' AND (v #>> '{}') = ANY (allowed))
$$;
CREATE OR REPLACE FUNCTION pg_temp.gp_present(v jsonb) RETURNS boolean LANGUAGE sql IMMUTABLE AS $$
    SELECT NOT pg_temp.gp_absent(v)
$$;
-- List<Long>: an array of integral numbers. CASE (unlike AND) guarantees array functions only see arrays.
CREATE OR REPLACE FUNCTION pg_temp.gp_sequences(v jsonb) RETURNS boolean LANGUAGE sql IMMUTABLE AS $$
    SELECT CASE
        WHEN pg_temp.gp_absent(v) THEN TRUE
        WHEN jsonb_typeof(v) <> 'array' THEN FALSE
        ELSE NOT EXISTS (SELECT 1 FROM jsonb_array_elements(v) element
                         WHERE jsonb_typeof(element) <> 'number' OR (element #>> '{}') !~ '^-?[0-9]+$')
    END
$$;
-- List<TripReconstructionWaypointDto>: objects with numeric latitude and longitude.
CREATE OR REPLACE FUNCTION pg_temp.gp_waypoints(v jsonb) RETURNS boolean LANGUAGE sql IMMUTABLE AS $$
    SELECT CASE
        WHEN pg_temp.gp_absent(v) THEN TRUE
        WHEN jsonb_typeof(v) <> 'array' THEN FALSE
        ELSE NOT EXISTS (SELECT 1 FROM jsonb_array_elements(v) element
                         WHERE jsonb_typeof(element) <> 'object'
                            OR NOT pg_temp.gp_number(element -> 'latitude')
                            OR NOT pg_temp.gp_number(element -> 'longitude'))
    END
$$;
-- A drawable route: at least two readable points.
CREATE OR REPLACE FUNCTION pg_temp.gp_route(v jsonb) RETURNS boolean LANGUAGE sql IMMUTABLE AS $$
    SELECT CASE
        WHEN v IS NULL OR jsonb_typeof(v) <> 'array' THEN FALSE
        WHEN jsonb_array_length(v) < 2 THEN FALSE
        ELSE pg_temp.gp_waypoints(v)
    END
$$;

-- 1. Remove corrections the fold can never read, as V36.85.0 did for invalid movement types and names. Each kind
--    requires exactly what its typed payload and the applier require.
DELETE FROM timeline_edits e
WHERE CASE WHEN jsonb_typeof(e.payload) IS DISTINCT FROM 'object' THEN TRUE ELSE NOT CASE e.kind
       WHEN 'SET_MOVEMENT_TYPE' THEN
           pg_temp.gp_present(e.payload -> 'movementType')
           AND pg_temp.gp_enum(e.payload -> 'movementType', ARRAY['WALK', 'BICYCLE', 'RUNNING', 'CAR', 'MOTORCYCLE',
               'PUBLIC_TRANSPORT', 'TRAIN', 'FLIGHT', 'BOAT', 'UNKNOWN'])
       WHEN 'SET_VISIT_NAME' THEN
           pg_temp.gp_present(e.payload -> 'visitName')
           AND pg_temp.gp_text(e.payload -> 'visitName')
           AND length(e.payload ->> 'visitName') <= 500
       WHEN 'SET_PLACE' THEN
           pg_temp.gp_text(e.payload -> 'locationName')
           AND pg_temp.gp_number(e.payload -> 'latitude') AND pg_temp.gp_number(e.payload -> 'longitude')
           AND pg_temp.gp_number(e.payload -> 'favoriteId') AND pg_temp.gp_number(e.payload -> 'geocodingId')
       WHEN 'FORCE_STAY' THEN
           pg_temp.gp_text(e.payload -> 'locationName')
           AND pg_temp.gp_number(e.payload -> 'latitude') AND pg_temp.gp_number(e.payload -> 'longitude')
           AND pg_temp.gp_number(e.payload -> 'favoriteId') AND pg_temp.gp_number(e.payload -> 'geocodingId')
           AND pg_temp.gp_text(e.payload -> 'visitName')
           AND pg_temp.gp_bool(e.payload -> 'locationNameCustom') AND pg_temp.gp_bool(e.payload -> 'resolveLocationName')
           AND pg_temp.gp_text(e.payload -> 'explanation') AND pg_temp.gp_text(e.payload -> 'carveExplanation')
           AND pg_temp.gp_bool(e.payload -> 'activityCorrection') AND pg_temp.gp_bool(e.payload -> 'reconstruction')
           AND pg_temp.gp_number(e.payload -> 'tripId')
       WHEN 'FORCE_TRIP' THEN
           pg_temp.gp_number(e.payload -> 'startFavoriteId') AND pg_temp.gp_number(e.payload -> 'startGeocodingId')
           AND pg_temp.gp_number(e.payload -> 'startLatitude') AND pg_temp.gp_number(e.payload -> 'startLongitude')
           AND pg_temp.gp_text(e.payload -> 'startLocationName')
           AND pg_temp.gp_number(e.payload -> 'endFavoriteId') AND pg_temp.gp_number(e.payload -> 'endGeocodingId')
           AND pg_temp.gp_number(e.payload -> 'endLatitude') AND pg_temp.gp_number(e.payload -> 'endLongitude')
           AND pg_temp.gp_text(e.payload -> 'endLocationName')
           AND pg_temp.gp_enum(e.payload -> 'movementType', ARRAY['WALK', 'BICYCLE', 'RUNNING', 'CAR', 'MOTORCYCLE',
               'PUBLIC_TRANSPORT', 'TRAIN', 'FLIGHT', 'BOAT', 'UNKNOWN'])
           AND pg_temp.gp_number(e.payload -> 'distanceMeters')
           AND pg_temp.gp_waypoints(e.payload -> 'waypoints')
           AND pg_temp.gp_bool(e.payload -> 'activityCorrection') AND pg_temp.gp_bool(e.payload -> 'reconstruction')
           AND pg_temp.gp_number(e.payload -> 'tripId')
           -- A manual route needs at least two points to be drawn.
           AND (COALESCE(e.payload ->> 'activityCorrection', 'false') <> 'true' OR pg_temp.gp_route(e.payload -> 'waypoints'))
       WHEN 'SET_TIMES' THEN
           pg_temp.gp_present(e.payload -> 'start') AND pg_temp.gp_instant(e.payload -> 'start')
           AND pg_temp.gp_present(e.payload -> 'end') AND pg_temp.gp_instant(e.payload -> 'end')
       WHEN 'SET_ROUTE' THEN
           pg_temp.gp_route(e.payload -> 'waypoints')
       WHEN 'SPLIT_STAY' THEN
           pg_temp.gp_present(e.payload -> 'splitAt') AND pg_temp.gp_instant(e.payload -> 'splitAt')
           AND pg_temp.gp_text(e.payload -> 'rightLocationName')
           AND pg_temp.gp_number(e.payload -> 'rightLatitude') AND pg_temp.gp_number(e.payload -> 'rightLongitude')
           AND pg_temp.gp_number(e.payload -> 'rightFavoriteId') AND pg_temp.gp_number(e.payload -> 'rightGeocodingId')
       WHEN 'MERGE' THEN
           pg_temp.gp_enum(e.payload -> 'direction', ARRAY['PREVIOUS', 'NEXT'])
       WHEN 'REMOVE_EVENT' THEN
           pg_temp.gp_present(e.payload -> 'resolution')
           AND pg_temp.gp_enum(e.payload -> 'resolution', ARRAY['GAP', 'EXTEND_PREVIOUS', 'EXTEND_NEXT', 'MERGE', 'DISMISS'])
       ELSE FALSE
   END END;

-- 2. before_values is presentation plus the stored dependencies. Keep it, dropping only the keys that cannot be read.
UPDATE timeline_edits SET before_values = NULL
WHERE before_values IS NOT NULL AND jsonb_typeof(before_values) <> 'object';

UPDATE timeline_edits e
SET before_values = e.before_values - ARRAY(
    SELECT entry.key FROM jsonb_each(e.before_values) entry
    WHERE NOT CASE entry.key
        WHEN 'type' THEN pg_temp.gp_enum(entry.value, ARRAY['STAY', 'TRIP', 'DATA_GAP'])
        WHEN 'start' THEN pg_temp.gp_instant(entry.value)
        WHEN 'end' THEN pg_temp.gp_instant(entry.value)
        WHEN 'visitName' THEN pg_temp.gp_text(entry.value)
        WHEN 'locationName' THEN pg_temp.gp_text(entry.value)
        WHEN 'movementType' THEN pg_temp.gp_enum(entry.value, ARRAY['WALK', 'BICYCLE', 'RUNNING', 'CAR', 'MOTORCYCLE',
            'PUBLIC_TRANSPORT', 'TRAIN', 'FLIGHT', 'BOAT', 'UNKNOWN'])
        WHEN 'prerequisiteSequences' THEN pg_temp.gp_sequences(entry.value)
        WHEN 'cascadeUndoneSequences' THEN pg_temp.gp_sequences(entry.value)
        WHEN 'favoriteId' THEN pg_temp.gp_number(entry.value)
        WHEN 'geocodingId' THEN pg_temp.gp_number(entry.value)
        WHEN 'latitude' THEN pg_temp.gp_number(entry.value)
        WHEN 'longitude' THEN pg_temp.gp_number(entry.value)
        WHEN 'startLatitude' THEN pg_temp.gp_number(entry.value)
        WHEN 'startLongitude' THEN pg_temp.gp_number(entry.value)
        WHEN 'endLatitude' THEN pg_temp.gp_number(entry.value)
        WHEN 'endLongitude' THEN pg_temp.gp_number(entry.value)
        ELSE TRUE -- other keys are ignored when read
    END)
WHERE e.before_values IS NOT NULL;

-- 3. The operation selects the JSON payload subtype; old rows predate the discriminator.
UPDATE timeline_edits SET payload = payload || jsonb_build_object('payloadType', CASE kind
    WHEN 'FORCE_STAY' THEN 'PLACE_STAY' WHEN 'FORCE_TRIP' THEN 'PLACE_TRIP'
    WHEN 'REMOVE_EVENT' THEN 'CLEAR' WHEN 'SET_TIMES' THEN 'RESIZE'
    WHEN 'SET_MOVEMENT_TYPE' THEN 'SET_TRIP_TYPE' WHEN 'SET_VISIT_NAME' THEN 'SET_LABEL'
    ELSE kind END)
WHERE jsonb_typeof(payload) = 'object';

DROP FUNCTION pg_temp.gp_route(jsonb);
DROP FUNCTION pg_temp.gp_waypoints(jsonb);
DROP FUNCTION pg_temp.gp_sequences(jsonb);
DROP FUNCTION pg_temp.gp_present(jsonb);
DROP FUNCTION pg_temp.gp_enum(jsonb, text[]);
DROP FUNCTION pg_temp.gp_bool(jsonb);
DROP FUNCTION pg_temp.gp_text(jsonb);
DROP FUNCTION pg_temp.gp_number(jsonb);
DROP FUNCTION pg_temp.gp_instant(jsonb);
DROP FUNCTION pg_temp.gp_absent(jsonb);
