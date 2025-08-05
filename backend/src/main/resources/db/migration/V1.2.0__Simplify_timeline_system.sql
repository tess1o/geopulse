-- Timeline System Simplification
-- Removes complex version tracking and adds priority-based regeneration queue
-- Part of Timeline System Simplification Plan

-- Add merge impact flag to favorites table
ALTER TABLE favorite_locations 
    ADD COLUMN merge_impact BOOLEAN DEFAULT FALSE;

-- Remove version-related fields from timeline tables
ALTER TABLE timeline_stays 
    DROP COLUMN IF EXISTS timeline_version,
    DROP COLUMN IF EXISTS is_stale;

ALTER TABLE timeline_trips 
    DROP COLUMN IF EXISTS timeline_version,
    DROP COLUMN IF EXISTS is_stale;

-- Create priority regeneration queue table
CREATE TABLE timeline_regeneration_queue (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    priority INTEGER NOT NULL DEFAULT 1, -- 1=high (favorites), 2=low (bulk imports)
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING', -- PENDING, PROCESSING, COMPLETED, FAILED
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    processing_started_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,
    retry_count INTEGER DEFAULT 0,
    error_message TEXT,
    CONSTRAINT fk_regeneration_queue_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- Indexes for efficient queue processing
CREATE INDEX idx_regeneration_queue_priority_created 
    ON timeline_regeneration_queue(priority, created_at) 
    WHERE status = 'PENDING';

CREATE INDEX idx_regeneration_queue_user_dates 
    ON timeline_regeneration_queue(user_id, start_date, end_date);

CREATE INDEX idx_regeneration_queue_status 
    ON timeline_regeneration_queue(status, processing_started_at);

-- Update merge impact for existing favorites
-- Area favorites always have merge impact
UPDATE favorite_locations 
SET merge_impact = TRUE 
WHERE type = 'AREA';

-- Point favorites have merge impact if they are near other favorites (within 200m)
UPDATE favorite_locations 
SET merge_impact = TRUE 
WHERE type = 'POINT' 
  AND EXISTS (
      SELECT 1 FROM favorite_locations f2 
      WHERE f2.user_id = favorite_locations.user_id 
        AND f2.id != favorite_locations.id
        AND ST_DWithin(f2.geometry, favorite_locations.geometry, 200)
  );

-- Add comment for documentation
COMMENT ON TABLE timeline_regeneration_queue IS 'Priority queue for timeline regeneration tasks. High priority for favorite changes, low priority for bulk imports.';
COMMENT ON COLUMN favorite_locations.merge_impact IS 'Indicates if this favorite could cause timeline stays to merge/unmerge when changed.';