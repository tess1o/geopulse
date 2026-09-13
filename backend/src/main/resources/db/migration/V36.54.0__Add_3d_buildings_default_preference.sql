ALTER TABLE users
    ADD COLUMN IF NOT EXISTS timeline_display_enable_3d_buildings_by_default BOOLEAN NOT NULL DEFAULT false;

COMMENT ON COLUMN users.timeline_display_enable_3d_buildings_by_default IS
    'Display-only: Enable 3D buildings by default for compatible MapTiler vector maps';
