-- Optimize reverse geocoding batch lookup indexes.
-- Goals:
-- 1) Remove duplicate GiST indexes from earlier migrations.
-- 2) Add geography expression indexes that match ST_DWithin(...::geography) lookups.

-- Remove duplicate geometry indexes (kept *_gist variants from later migrations).
DROP INDEX IF EXISTS idx_reverse_geocoding_request_coords;
DROP INDEX IF EXISTS idx_reverse_geocoding_bounding_box;

-- Replace geometry result-coordinate indexes with geography expression indexes.
-- Batch lookup uses ST_DWithin(result_coordinates::geography, ...), so geography
-- expression indexes are the right match for planner index usage.
DROP INDEX IF EXISTS idx_reverse_geocoding_result_coordinates_gist;
DROP INDEX IF EXISTS idx_reverse_geocoding_user_result_coords_notnull;
DROP INDEX IF EXISTS idx_reverse_geocoding_result_coords_null;

CREATE INDEX IF NOT EXISTS idx_reverse_geocoding_user_result_geog_notnull
ON reverse_geocoding_location
USING GIST ((result_coordinates::geography))
WHERE user_id IS NOT NULL AND result_coordinates IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_reverse_geocoding_result_geog_null
ON reverse_geocoding_location
USING GIST ((result_coordinates::geography))
WHERE user_id IS NULL AND result_coordinates IS NOT NULL;

-- Add geography indexes for request coordinates as well, because the batch query
-- also uses ST_DWithin(request_coordinates::geography, ...).
CREATE INDEX IF NOT EXISTS idx_reverse_geocoding_user_request_geog_notnull
ON reverse_geocoding_location
USING GIST ((request_coordinates::geography))
WHERE user_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_reverse_geocoding_request_geog_null
ON reverse_geocoding_location
USING GIST ((request_coordinates::geography))
WHERE user_id IS NULL;

ANALYZE reverse_geocoding_location;
