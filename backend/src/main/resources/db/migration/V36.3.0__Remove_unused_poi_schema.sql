-- Remove unused POI schema after planner simplification.
-- Keeps only coordinate/favorite/geocoding based plan suggestion flow.

-- Drop trip plan item provider linkage columns and index (no longer used).
DROP INDEX IF EXISTS idx_trip_plan_items_source_ref;

ALTER TABLE trip_plan_items
    DROP COLUMN IF EXISTS poi_source,
    DROP COLUMN IF EXISTS external_ref;

-- Drop POI cache tables (search/image provider stack removed).
DROP TABLE IF EXISTS poi_image_cache;
DROP TABLE IF EXISTS poi_cache;
