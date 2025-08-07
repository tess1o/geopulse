-- V1.4.0__Add_spatial_performance_indexes.sql
-- Performance indexes for spatial batch queries optimization

-- Note: Using regular CREATE INDEX (not CONCURRENTLY) for small development datasets
-- In production with large tables, consider using CONCURRENTLY to avoid table locking

-- Separate indexes for favorite locations (GIST doesn't support UUID directly)
CREATE INDEX IF NOT EXISTS idx_favorite_locations_user_id 
    ON favorite_locations (user_id);

CREATE INDEX IF NOT EXISTS idx_favorite_locations_geometry_gist 
    ON favorite_locations USING GIST (geometry);

CREATE INDEX IF NOT EXISTS idx_favorite_locations_user_type 
    ON favorite_locations (user_id, type);

-- Spatial indexes for reverse geocoding cache
CREATE INDEX IF NOT EXISTS idx_reverse_geocoding_request_coordinates_gist 
    ON reverse_geocoding_location USING GIST (request_coordinates);

CREATE INDEX IF NOT EXISTS idx_reverse_geocoding_result_coordinates_gist 
    ON reverse_geocoding_location USING GIST (result_coordinates);

CREATE INDEX IF NOT EXISTS idx_reverse_geocoding_bounding_box_gist 
    ON reverse_geocoding_location USING GIST (bounding_box);

-- Index for timestamp ordering (used in DISTINCT ON queries)
CREATE INDEX IF NOT EXISTS idx_reverse_geocoding_last_accessed 
    ON reverse_geocoding_location (last_accessed_at DESC);