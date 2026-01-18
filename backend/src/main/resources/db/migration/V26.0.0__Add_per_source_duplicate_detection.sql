-- Add per-source duplicate detection settings
ALTER TABLE gps_source_config
ADD COLUMN enable_duplicate_detection BOOLEAN NOT NULL DEFAULT false;

ALTER TABLE gps_source_config
ADD COLUMN duplicate_detection_threshold_minutes INTEGER;

-- Enable duplicate detection for existing OwnTracks sources (preserve current behavior)
-- Leave threshold NULL - application will fall back to global config value
-- This respects users who customized GEOPULSE_GPS_DUPLICATE_DETECTION_LOCATION_TIME_THRESHOLD_MINUTES
UPDATE gps_source_config
SET enable_duplicate_detection = true
WHERE source_type = 'OWNTRACKS';

-- Non-OwnTracks sources stay disabled (no change in behavior)
