-- Add vector map style URL + render mode to users and shared links.
-- Keep existing custom_map_tile_url for backward-compatible raster configuration.

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS custom_map_style_url VARCHAR(1000),
    ADD COLUMN IF NOT EXISTS map_render_mode VARCHAR(32) NOT NULL DEFAULT 'VECTOR';

COMMENT ON COLUMN users.custom_map_style_url IS
    'Custom vector map style URL. Null means use application default vector style.';
COMMENT ON COLUMN users.map_render_mode IS
    'Preferred map rendering mode. Allowed values: RASTER, VECTOR. Default: VECTOR.';

ALTER TABLE shared_link
    ADD COLUMN IF NOT EXISTS custom_map_style_url VARCHAR(1000),
    ADD COLUMN IF NOT EXISTS map_render_mode VARCHAR(32) NOT NULL DEFAULT 'VECTOR';

-- Backward compatibility for legacy users/links that only configured raster tiles.
-- If a custom raster tile URL already exists, keep behavior by defaulting render mode to RASTER.
UPDATE users
SET map_render_mode = 'RASTER'
WHERE custom_map_tile_url IS NOT NULL
  AND BTRIM(custom_map_tile_url) <> '';

UPDATE shared_link
SET map_render_mode = 'RASTER'
WHERE custom_map_tile_url IS NOT NULL
  AND BTRIM(custom_map_tile_url) <> '';

COMMENT ON COLUMN shared_link.custom_map_style_url IS
    'Optional custom vector map style URL to use in shared views.';
COMMENT ON COLUMN shared_link.map_render_mode IS
    'Map rendering mode for this shared link. Allowed values: RASTER, VECTOR. Default: VECTOR.';
