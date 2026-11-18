-- A trip's route can be corrected without splitting the trip into several events.
ALTER TABLE timeline_edits DROP CONSTRAINT IF EXISTS chk_timeline_edits_kind;
ALTER TABLE timeline_edits ADD CONSTRAINT chk_timeline_edits_kind CHECK (kind IN (
    'SET_MOVEMENT_TYPE', 'SET_PLACE', 'SET_VISIT_NAME', 'FORCE_STAY', 'FORCE_TRIP',
    'SET_TIMES', 'SET_ROUTE', 'SPLIT_STAY', 'MERGE', 'REMOVE_EVENT'));
