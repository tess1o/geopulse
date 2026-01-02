-- Add custom_map_tile_url column to shared_link table
-- This allows share link creators to optionally share their custom map tiles with viewers
ALTER TABLE shared_link
ADD COLUMN custom_map_tile_url VARCHAR(1000);

COMMENT ON COLUMN shared_link.custom_map_tile_url IS
  'Optional custom map tile URL to use in shared views. If null, shared views use default OSM tiles. User must explicitly opt-in to share their tile URL.';
