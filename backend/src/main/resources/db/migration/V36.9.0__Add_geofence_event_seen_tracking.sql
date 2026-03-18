-- Add in-app read tracking for geofence events.

ALTER TABLE geofence_events
    ADD COLUMN IF NOT EXISTS seen_at TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_geofence_events_owner_seen_occurred
    ON geofence_events (owner_user_id, seen_at, occurred_at DESC);
