-- Add custom map tile URL support for user profiles
-- Allows users to configure custom map tile providers (e.g., MapTiler, Mapbox, etc.)

ALTER TABLE users ADD COLUMN custom_map_tile_url VARCHAR(1000);

COMMENT ON COLUMN users.custom_map_tile_url IS 'Custom map tile URL template with {z}, {x}, {y} placeholders. Null means use default OSM tiles.';
