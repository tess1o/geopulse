CREATE INDEX IF NOT EXISTS idx_geofence_events_owner_subject_occurred
    ON geofence_events (owner_user_id, subject_user_id, occurred_at DESC);

CREATE INDEX IF NOT EXISTS idx_geofence_events_owner_event_type_occurred
    ON geofence_events (owner_user_id, event_type, occurred_at DESC);
