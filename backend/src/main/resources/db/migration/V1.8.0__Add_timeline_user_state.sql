-- Add timeline_user_state table for future real-time streaming timeline processing
-- This table will store the processing state of each user to enable incremental
-- timeline processing as GPS points arrive in real-time mode

CREATE TABLE timeline_user_state (
    user_id UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    processor_mode VARCHAR(20) NOT NULL DEFAULT 'UNKNOWN',
    last_processed_timestamp TIMESTAMP WITH TIME ZONE,
    active_points JSONB,
    last_gps_point JSONB,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Add constraint to ensure valid processor modes
ALTER TABLE timeline_user_state 
ADD CONSTRAINT timeline_user_state_processor_mode_check 
CHECK (processor_mode IN ('UNKNOWN', 'POTENTIAL_STAY', 'CONFIRMED_STAY', 'IN_TRIP'));

-- Add index for efficient lookups
CREATE INDEX idx_timeline_user_state_user_id ON timeline_user_state(user_id);
CREATE INDEX idx_timeline_user_state_last_processed ON timeline_user_state(last_processed_timestamp);

-- Add comments for documentation
COMMENT ON TABLE timeline_user_state IS 'Stores processing state for streaming timeline algorithm to enable real-time GPS point processing';
COMMENT ON COLUMN timeline_user_state.processor_mode IS 'Current state in the timeline processing state machine';
COMMENT ON COLUMN timeline_user_state.last_processed_timestamp IS 'Timestamp of the last GPS point processed for this user';
COMMENT ON COLUMN timeline_user_state.active_points IS 'JSON array of GPS points currently being accumulated for potential stay/trip';
COMMENT ON COLUMN timeline_user_state.last_gps_point IS 'Last processed GPS point JSON for data gap detection';