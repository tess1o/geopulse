-- ============================================================================
-- Migration V18.0.0: Add unique constraint for reverse_geocoding originals
--
-- Purpose: Prevent duplicate original geocoding entries at same coordinates
-- Bug Fix: Addresses race condition in cacheGeocodingResult() with REQUIRES_NEW
-- Strategy: Partial unique index on coordinates WHERE user_id IS NULL
-- ============================================================================

-- Problem Being Solved:
-- =====================
-- When cacheGeocodingResult() runs in REQUIRES_NEW transaction, it cannot see
-- uncommitted data from parent transaction. This can cause duplicate originals:
--
-- 1. Transaction A creates original at coords (40.7589, -73.9851) - uncommitted
-- 2. Transaction A creates user copy at same coords - uncommitted
-- 3. cacheGeocodingResult() starts NEW transaction B (REQUIRES_NEW)
-- 4. findOriginalByExactCoordinates() in transaction B sees nothing (no commit)
-- 5. Returns null → creates DUPLICATE original
-- 6. Transaction A commits → now 2 originals exist! BUG!
--
-- Solution:
-- =========
-- Use PostgreSQL's partial unique index to enforce constraint at database level.
-- If duplicate insert is attempted, we get unique violation error that we can
-- handle gracefully (query again to get the existing entry).

-- Step 1: Create partial unique index for originals
-- ==================================================
-- This enforces that for any given coordinates, there can only be ONE original.
-- Note: We cannot use a simple unique constraint because PostGIS geometry types
-- don't support standard equality. Instead, we create a functional index using
-- ST_AsBinary() to convert geometry to comparable bytea format.

-- IMPORTANT: This only applies to originals (user_id IS NULL).
-- Users can have multiple copies at the same coordinates (that's by design).

CREATE UNIQUE INDEX idx_reverse_geocoding_unique_original_coords
ON reverse_geocoding_location (ST_AsBinary(request_coordinates))
WHERE user_id IS NULL;

-- Step 2: Add explanatory comment
COMMENT ON INDEX idx_reverse_geocoding_unique_original_coords IS
'Ensures only one original (user_id=NULL) exists per coordinate. Prevents duplicate originals when cacheGeocodingResult() runs in REQUIRES_NEW transaction and cannot see uncommitted parent transaction data. Uses ST_AsBinary() for geometry comparison.';

-- Step 3: Update statistics
ANALYZE reverse_geocoding_location;

-- ============================================================================
-- Migration Notes
-- ============================================================================
-- 1. This is a PARTIAL unique index (WHERE user_id IS NULL)
-- 2. Only affects originals - user-specific copies are unrestricted
-- 3. Uses ST_AsBinary() to convert PostGIS Point to comparable bytea
-- 4. Existing duplicate originals (if any) will cause migration to fail
--    - This is intentional - duplicates should be cleaned up first
-- 5. Application code must handle unique violation gracefully:
--    - Catch constraint violation exception
--    - Query again to get existing original
--    - Update existing entry instead of creating duplicate
-- ============================================================================
