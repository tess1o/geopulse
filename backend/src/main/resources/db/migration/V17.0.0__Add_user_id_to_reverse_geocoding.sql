-- ============================================================================
-- Migration V17.0.0: Add user_id to reverse_geocoding_location
--
-- Purpose: Enable per-user reverse geocoding customization via copy-on-write
-- Strategy: Hybrid - NULL = original (shared), UUID = user-specific copy
-- Data Migration: None required (existing data already correct as originals)
-- ============================================================================

-- Step 1: Add user_id column (nullable)
ALTER TABLE reverse_geocoding_location
ADD COLUMN user_id UUID NULL;

-- Step 2: Add foreign key constraint with CASCADE delete
-- If user is deleted, their custom geocoding entities are also deleted
-- Original entities (user_id=NULL) are never deleted via this constraint
ALTER TABLE reverse_geocoding_location
ADD CONSTRAINT FK_REVERSE_GEOCODING_USER
FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;

-- Step 3: Add column comment
COMMENT ON COLUMN reverse_geocoding_location.user_id IS
'User ID for user-specific geocoding modifications. NULL = original/shared data from provider (unmodified by any user). UUID = user-specific copy (modified by that user).';

-- Step 4: Create indexes
-- ============================================================================

-- Simple user_id index (for filtering and joins)
CREATE INDEX idx_reverse_geocoding_user_id
ON reverse_geocoding_location (user_id);

-- Partial spatial indexes for USER-SPECIFIC entities (user_id IS NOT NULL)
-- These allow efficient queries for user's own modified locations
CREATE INDEX idx_reverse_geocoding_user_request_coords_notnull
ON reverse_geocoding_location USING GIST (request_coordinates)
WHERE user_id IS NOT NULL;

CREATE INDEX idx_reverse_geocoding_user_result_coords_notnull
ON reverse_geocoding_location USING GIST (result_coordinates)
WHERE user_id IS NOT NULL;

CREATE INDEX idx_reverse_geocoding_user_bbox_notnull
ON reverse_geocoding_location USING GIST (bounding_box)
WHERE user_id IS NOT NULL AND bounding_box IS NOT NULL;

-- Partial spatial indexes for ORIGINAL entities (user_id IS NULL)
-- These allow efficient queries for shared geocoding cache (most queries)
CREATE INDEX idx_reverse_geocoding_request_coords_null
ON reverse_geocoding_location USING GIST (request_coordinates)
WHERE user_id IS NULL;

CREATE INDEX idx_reverse_geocoding_result_coords_null
ON reverse_geocoding_location USING GIST (result_coordinates)
WHERE user_id IS NULL;

CREATE INDEX idx_reverse_geocoding_bbox_null
ON reverse_geocoding_location USING GIST (bounding_box)
WHERE user_id IS NULL AND bounding_box IS NOT NULL;

-- Composite index for filtering by city/country per user
CREATE INDEX idx_reverse_geocoding_user_city_country
ON reverse_geocoding_location (user_id, country, city)
WHERE city IS NOT NULL AND country IS NOT NULL;

-- Step 5: Update statistics
ANALYZE reverse_geocoding_location;

-- ============================================================================
-- Migration Notes
-- ============================================================================
-- 1. All existing data will have user_id = NULL (correct state: originals)
-- 2. No data migration needed - no UPDATE statements required
-- 3. Zero downtime - column addition is non-blocking in PostgreSQL
-- 4. When users modify locations, app creates user-specific copies
-- 5. Copy-on-write strategy: modify original → duplicate + set user_id
-- 6. Partial indexes optimize query performance for both user-specific and originals
-- 7. Foreign key CASCADE ensures cleanup when users are deleted
-- ============================================================================
