ALTER TABLE users
    ADD COLUMN IF NOT EXISTS timeline_display_default_path_color VARCHAR(7),
    ADD COLUMN IF NOT EXISTS timeline_display_active_path_color VARCHAR(7);

COMMENT ON COLUMN users.timeline_display_default_path_color IS
    'Display-only: hex color (#rrggbb) for the normal (non-highlighted) timeline path. Null = app default (#007bff).';
COMMENT ON COLUMN users.timeline_display_active_path_color IS
    'Display-only: hex color (#rrggbb) for the highlighted/selected trip path. Null = app default (#ef4444).';
