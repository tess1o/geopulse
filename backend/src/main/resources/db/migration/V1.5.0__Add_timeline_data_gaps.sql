-- Migration: Add timeline data gaps table
-- Version: V1.5.0
-- Description: Creates table for storing GPS data gaps in timelines

-- Create timeline_data_gaps table
CREATE TABLE timeline_data_gaps (
    id BIGSERIAL PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    start_time TIMESTAMP WITH TIME ZONE NOT NULL,
    end_time TIMESTAMP WITH TIME ZONE NOT NULL,
    duration_seconds INTEGER NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- Add indexes for performance
CREATE INDEX idx_timeline_data_gaps_user_time ON timeline_data_gaps(user_id, start_time);
CREATE INDEX idx_timeline_data_gaps_start_time ON timeline_data_gaps(start_time);
CREATE INDEX idx_timeline_data_gaps_end_time ON timeline_data_gaps(end_time);

-- Add constraint to ensure end_time is after start_time
ALTER TABLE timeline_data_gaps ADD CONSTRAINT chk_timeline_data_gaps_time_order 
    CHECK (end_time > start_time);

-- Add constraint to ensure duration_seconds is positive
ALTER TABLE timeline_data_gaps ADD CONSTRAINT chk_timeline_data_gaps_duration_positive 
    CHECK (duration_seconds > 0);