-- Add connection_type column to gps_source_config table for MQTT support
ALTER TABLE gps_source_config 
ADD COLUMN connection_type VARCHAR(10) NOT NULL DEFAULT 'HTTP';

-- Add constraint to ensure only HTTP or MQTT values
ALTER TABLE gps_source_config 
ADD CONSTRAINT chk_gps_source_connection_type 
CHECK (connection_type IN ('HTTP', 'MQTT'));

-- Create index for efficient queries by username and connection type
CREATE INDEX idx_gps_source_config_username_connection_type 
ON gps_source_config (username, connection_type) 
WHERE active = true;