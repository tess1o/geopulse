ALTER TABLE geofence_events
    ADD COLUMN IF NOT EXISTS subject_display_name VARCHAR(255);

UPDATE geofence_events ge
SET subject_display_name = COALESCE(NULLIF(TRIM(u.full_name), ''), u.email)
FROM users u
WHERE ge.subject_user_id = u.id
  AND ge.subject_display_name IS NULL;

UPDATE geofence_events
SET subject_display_name = 'Unknown subject'
WHERE subject_display_name IS NULL;

ALTER TABLE geofence_events
    ALTER COLUMN subject_display_name SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_geofence_events_owner_occurred_id
    ON geofence_events (owner_user_id, occurred_at DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_geofence_events_owner_subject_display_id
    ON geofence_events (owner_user_id, subject_display_name, id DESC);

CREATE INDEX IF NOT EXISTS idx_geofence_events_owner_event_type_id
    ON geofence_events (owner_user_id, event_type, id DESC);
