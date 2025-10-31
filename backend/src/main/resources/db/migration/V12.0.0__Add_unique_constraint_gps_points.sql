-- Add unique constraint to prevent duplicate GPS points
-- This handles duplicates from GPX export/import cycles and other sources
-- Duplicates are defined as same user, timestamp, and coordinates

-- STEP 1: Clean up existing duplicates before creating unique index
-- Keep the row with the smallest ID (oldest record) for each duplicate group
-- Delete all other duplicates

DO $$
DECLARE
    deleted_count INTEGER;
BEGIN
    -- Delete duplicates, keeping only the row with the smallest ID per (user_id, timestamp, coordinates) group
    WITH duplicate_groups AS (
        SELECT
            id,
            user_id,
            timestamp,
            coordinates,
            ROW_NUMBER() OVER (
                PARTITION BY user_id, timestamp, coordinates
                ORDER BY id ASC  -- Keep the oldest record (smallest ID)
            ) as row_num
        FROM gps_points
    )
    DELETE FROM gps_points
    WHERE id IN (
        SELECT id
        FROM duplicate_groups
        WHERE row_num > 1  -- Delete all but the first record in each group
    );

    GET DIAGNOSTICS deleted_count = ROW_COUNT;

    -- Log the cleanup results
    RAISE NOTICE 'Deleted % duplicate GPS points before creating unique index', deleted_count;
END $$;

-- STEP 2: Create unique index to prevent future duplicates
CREATE UNIQUE INDEX idx_gps_points_no_duplicates
ON gps_points(user_id, timestamp, coordinates);

-- Add comment for documentation
COMMENT ON INDEX idx_gps_points_no_duplicates IS
'Prevents duplicate GPS points with identical user_id, timestamp, and coordinates.
Used for automatic deduplication during import (ON CONFLICT DO NOTHING).';
