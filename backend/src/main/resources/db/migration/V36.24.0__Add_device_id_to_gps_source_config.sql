-- Add optional device_id filter to support per-device routing for shared Traccar tokens
ALTER TABLE gps_source_config
ADD COLUMN device_id VARCHAR(255);
