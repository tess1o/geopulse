-- Add processing_status column to timeline_user_state table for synchronization between
-- real-time and batch processing to prevent timeline duplication and data corruption
ALTER TABLE timeline_user_state 
ADD COLUMN processing_status VARCHAR(255) NOT NULL DEFAULT 'IDLE';

-- Add constraint to ensure valid processing status values
ALTER TABLE timeline_user_state 
ADD CONSTRAINT timeline_user_state_processing_status_check 
CHECK (processing_status IN ('IDLE', 'REAL_TIME_PROCESSING', 'BATCH_PROCESSING'));

-- Expand processor_mode column to accommodate longer enum values
ALTER TABLE timeline_user_state 
ALTER COLUMN processor_mode TYPE VARCHAR(255);

-- Expand connection_type column to accommodate longer enum values  
ALTER TABLE gps_source_config 
ALTER COLUMN connection_type TYPE VARCHAR(255);

-- Add index for efficient processing status queries
CREATE INDEX idx_timeline_user_state_processing_status ON timeline_user_state(processing_status);
CREATE INDEX idx_timeline_user_state_updated_at ON timeline_user_state(updated_at);

-- Add comments for documentation
COMMENT ON COLUMN timeline_user_state.processing_status IS 'Current processing lock status for coordinating real-time vs batch processing';