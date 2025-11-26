-- Add timeline sharing support to shared_link table

-- Add share_type column (LIVE_LOCATION or TIMELINE)
ALTER TABLE shared_link ADD COLUMN share_type VARCHAR(20) DEFAULT 'LIVE_LOCATION' NOT NULL;

-- Add timeline-specific date range fields
ALTER TABLE shared_link ADD COLUMN start_date TIMESTAMP WITHOUT TIME ZONE;
ALTER TABLE shared_link ADD COLUMN end_date TIMESTAMP WITHOUT TIME ZONE;

-- Add flag to show current location during active timeline shares
ALTER TABLE shared_link ADD COLUMN show_current_location BOOLEAN DEFAULT true;

-- Add photo sharing toggle for timeline shares
ALTER TABLE shared_link ADD COLUMN show_photos BOOLEAN DEFAULT false;

-- Create index on share_type for faster filtering
CREATE INDEX idx_shared_link_share_type ON shared_link(share_type);

-- Create index on user_id and share_type for separate limit counting
CREATE INDEX idx_shared_link_user_type ON shared_link(user_id, share_type);

-- Add check constraint to ensure timeline shares have required dates
ALTER TABLE shared_link ADD CONSTRAINT chk_timeline_dates
    CHECK (
        (share_type = 'LIVE_LOCATION' AND start_date IS NULL AND end_date IS NULL) OR
        (share_type = 'TIMELINE' AND start_date IS NOT NULL AND end_date IS NOT NULL AND end_date >= start_date)
    );
