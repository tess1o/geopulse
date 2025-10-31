-- Add GPS filtering columns to gps_source_config table
-- These columns allow users to filter inaccurate GPS data based on accuracy and speed thresholds

-- Add filter_inaccurate_data column to enable/disable filtering
ALTER TABLE gps_source_config
ADD COLUMN filter_inaccurate_data BOOLEAN NOT NULL DEFAULT false;

-- Add max_allowed_accuracy column (in meters)
-- GPS points with accuracy worse than this value will be rejected
ALTER TABLE gps_source_config
ADD COLUMN max_allowed_accuracy INTEGER;

-- Add max_allowed_speed column (in km/h)
-- GPS points with speed higher than this value will be rejected
ALTER TABLE gps_source_config
ADD COLUMN max_allowed_speed INTEGER;

-- Update existing records with default values
-- This ensures all existing GPS sources have the filtering feature available
UPDATE gps_source_config
SET filter_inaccurate_data = false,
    max_allowed_accuracy = 100,
    max_allowed_speed = 250
WHERE id IS NOT NULL;
