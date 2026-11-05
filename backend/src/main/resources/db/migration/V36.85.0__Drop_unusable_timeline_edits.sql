-- Corrections migrated by V36.81.0 keep the movement type and visit name the legacy tables held. The edit
-- applier parses the movement type into the TripType enum and rejects a blank visit name, so an edit carrying
-- either would fail every timeline rebuild. Such values only came from imports, which did not validate them.
-- Removing the edit lets the automatic result stand instead of breaking the timeline. This also reaches users
-- who upgraded to 2.2.0 before V36.80.1 could repair the legacy tables, and edits imported without validation.
DELETE FROM timeline_edits
WHERE kind = 'SET_MOVEMENT_TYPE'
  AND COALESCE(payload ->> 'movementType', '') NOT IN (
      'WALK', 'BICYCLE', 'RUNNING', 'CAR', 'MOTORCYCLE', 'PUBLIC_TRANSPORT', 'TRAIN', 'FLIGHT', 'BOAT', 'UNKNOWN');

DELETE FROM timeline_edits
WHERE kind = 'SET_VISIT_NAME'
  AND (COALESCE(payload ->> 'visitName', '') !~ '[^[:space:]]'
       OR length(payload ->> 'visitName') > 500);
