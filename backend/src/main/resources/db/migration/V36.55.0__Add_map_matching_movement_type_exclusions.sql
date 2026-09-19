ALTER TABLE users
    ADD COLUMN IF NOT EXISTS timeline_display_map_matching_excluded_movement_types JSONB NOT NULL DEFAULT '[]'::jsonb;