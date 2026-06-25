ALTER TABLE users
    ADD COLUMN IF NOT EXISTS timeline_display_auto_show_trip_replay_controls BOOLEAN NOT NULL DEFAULT true;

COMMENT ON COLUMN users.timeline_display_auto_show_trip_replay_controls IS
    'Display-only: Automatically show trip replay controls when a trip is selected';
