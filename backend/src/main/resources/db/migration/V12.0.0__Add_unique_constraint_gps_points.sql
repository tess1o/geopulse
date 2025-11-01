-- Add unique constraint to prevent duplicate GPS points
-- This handles duplicates from GPX export/import cycles and other sources
-- Duplicates are defined as same user, timestamp, and coordinates

-- STEP 1: Clean up existing duplicates before creating unique index
-- Keep the row with the smallest ID (oldest record) for each duplicate group
DELETE FROM gps_points
WHERE id IN (
    SELECT id
    FROM (
        SELECT
            id,
            ROW_NUMBER() OVER (
                PARTITION BY user_id, timestamp, coordinates
                ORDER BY id ASC  -- Keep the oldest record (smallest ID)
            ) as row_num
        FROM gps_points
    ) duplicate_groups
    WHERE row_num > 1  -- Delete all but the first record in each group
);

-- STEP 2: Create unique index to prevent future duplicates
-- Note: PostGIS geometry columns work with UNIQUE INDEX and ON CONFLICT
CREATE UNIQUE INDEX idx_gps_points_no_duplicates
ON gps_points(user_id, timestamp, coordinates);