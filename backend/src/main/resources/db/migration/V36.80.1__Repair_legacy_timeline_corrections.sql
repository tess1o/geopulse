-- V36.81.0 turns every legacy correction into a timeline edit. The edit applier then parses the movement
-- type into the TripType enum, so a value that is not one of its names would fail every timeline rebuild.
-- Old versions only ever wrote enum names; anything else came from an import and cannot be applied.
DELETE FROM timeline_trip_movement_overrides
WHERE movement_type NOT IN (
    'WALK', 'BICYCLE', 'RUNNING', 'CAR', 'MOTORCYCLE', 'PUBLIC_TRANSPORT', 'TRAIN', 'FLIGHT', 'BOAT', 'UNKNOWN');

-- A movement-type correction remembers the trip it was made on with that trip's duration. Continuity trips
-- between two back-to-back stays can have zero length, and a correction made on one remembers an empty
-- interval, which V36.81.0 cannot represent. Recover the duration from the trip the correction is attached to,
-- or from the trip at the same time. A correction that still has no positive length could never match a trip
-- again, so drop it instead of letting it block the upgrade.
UPDATE timeline_trip_movement_overrides o
SET source_trip_duration_seconds = recovered.trip_duration
FROM (
    SELECT m.id,
           COALESCE(
               (SELECT t.trip_duration
                  FROM timeline_trips t
                 WHERE t.id = m.trip_id
                   AND t.user_id = m.user_id
                   AND t.trip_duration > 0),
               (SELECT t.trip_duration
                  FROM timeline_trips t
                 WHERE t.user_id = m.user_id
                   AND t.timestamp = m.source_trip_timestamp
                   AND t.trip_duration > 0
                 ORDER BY t.id DESC
                 LIMIT 1)) AS trip_duration
      FROM timeline_trip_movement_overrides m
     WHERE m.source_trip_duration_seconds <= 0
) recovered
WHERE o.id = recovered.id
  AND recovered.trip_duration > 0;

DELETE FROM timeline_trip_movement_overrides
WHERE source_trip_duration_seconds <= 0;

-- V36.81.0 derives an affected interval by adding these durations to a timestamp. A duration this long would
-- overflow that arithmetic ("interval out of range"). No real correction spans a year, so cap it.
UPDATE timeline_trip_movement_overrides
SET source_trip_duration_seconds = 31536000
WHERE source_trip_duration_seconds > 31536000;

UPDATE timeline_trip_stay_split_overrides
SET source_trip_duration_seconds = 31536000
WHERE source_trip_duration_seconds > 31536000;

-- V36.81.0 turns a stay name into a visit-name edit, which the applier refuses to apply when it is blank, and
-- would then fail every timeline rebuild. Old versions and the importer reject blank names, so a blank one is
-- unusable data rather than a correction. The stay keeps its generated name.
DELETE FROM timeline_stay_name_overrides
WHERE location_name !~ '[^[:space:]]';

