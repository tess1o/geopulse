-- ============================================================================
-- Migration V17.9.0: Cleanup duplicate reverse_geocoding originals
--
-- Purpose: Remove duplicate originals that prevent V18.0.0 unique constraint
-- Strategy:
--   1. Update FK references to point to the entry we'll keep
--   2. Delete duplicates
--   3. Keep the oldest (lowest id) among duplicates
--
-- Note: This migration is idempotent - safe to run even if no duplicates exist
-- Requires: spring.flyway.out-of-order=true
-- ============================================================================

-- Step 1: Identify duplicates and which to keep
-- ==============================================
-- For each set of duplicate originals at the same coordinates, keep the oldest (MIN id)

-- Step 2: Update FK references from duplicates to the keeper
-- ==========================================================
-- This handles the case where BOTH duplicates are referenced in timeline_stays
WITH duplicate_coords AS (
    -- Find coordinates that have more than one original
    SELECT ST_AsBinary(request_coordinates) as coords_binary
    FROM reverse_geocoding_location
    WHERE user_id IS NULL
    GROUP BY ST_AsBinary(request_coordinates)
    HAVING COUNT(*) > 1
),
keeper_mapping AS (
    -- For each duplicate coordinate, identify the keeper (oldest id)
    SELECT
        rgl.id as original_id,
        ST_AsBinary(rgl.request_coordinates) as coords_binary,
        MIN(rgl.id) OVER (PARTITION BY ST_AsBinary(rgl.request_coordinates)) as keeper_id
    FROM reverse_geocoding_location rgl
    INNER JOIN duplicate_coords dc ON ST_AsBinary(rgl.request_coordinates) = dc.coords_binary
    WHERE rgl.user_id IS NULL
)
UPDATE timeline_stays ts
SET geocoding_id = km.keeper_id
FROM keeper_mapping km
WHERE ts.geocoding_id = km.original_id
  AND km.original_id != km.keeper_id;

-- Step 3: Delete duplicate originals (now safe - no FK violations)
-- ================================================================
WITH duplicate_coords AS (
    SELECT ST_AsBinary(request_coordinates) as coords_binary
    FROM reverse_geocoding_location
    WHERE user_id IS NULL
    GROUP BY ST_AsBinary(request_coordinates)
    HAVING COUNT(*) > 1
),
originals_to_keep AS (
    -- Keep the oldest (MIN id) for each duplicate set
    SELECT MIN(rgl.id) as id, ST_AsBinary(rgl.request_coordinates) as coords_binary
    FROM reverse_geocoding_location rgl
    INNER JOIN duplicate_coords dc ON ST_AsBinary(rgl.request_coordinates) = dc.coords_binary
    WHERE rgl.user_id IS NULL
    GROUP BY ST_AsBinary(rgl.request_coordinates)
)
DELETE FROM reverse_geocoding_location rgl
WHERE rgl.user_id IS NULL
  AND ST_AsBinary(rgl.request_coordinates) IN (SELECT coords_binary FROM duplicate_coords)
  AND rgl.id NOT IN (SELECT id FROM originals_to_keep);

-- Step 4: Verify no orphaned references exist (safety check)
-- ==========================================================
DO $$
DECLARE
    orphan_count INTEGER;
BEGIN
    SELECT COUNT(*) INTO orphan_count
    FROM timeline_stays ts
    WHERE ts.geocoding_id IS NOT NULL
      AND NOT EXISTS (
          SELECT 1 FROM reverse_geocoding_location rgl WHERE rgl.id = ts.geocoding_id
      );

    IF orphan_count > 0 THEN
        RAISE EXCEPTION 'Found % timeline_stays with orphaned geocoding_id references!', orphan_count;
    END IF;
END $$;

-- ============================================================================
-- Usage Instructions
-- ============================================================================
-- This migration requires: spring.flyway.out-of-order=true
--
-- For users who failed on V18.0.0:
--   1. Delete the failed V18.0.0 entry from flyway_schema_history:
--      DELETE FROM flyway_schema_history WHERE version = '18.0.0' AND success = false;
--   2. Run migrations again
--   3. V17.9.0 will run (cleanup), then V18.0.0 will succeed
--
-- For users who already succeeded on V18.0.0:
--   - This migration will run and do nothing (idempotent)
-- ============================================================================
