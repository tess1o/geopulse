-- Migration: Standardize duration and distance units
-- Version: V2.2.0
-- Description: Standardize all durations to seconds and distances to meters throughout the system

-- Add new distance_meters column to timeline_trips
ALTER TABLE timeline_trips ADD COLUMN distance_meters BIGINT;

-- Copy and convert distance data from km to meters
UPDATE timeline_trips SET distance_meters = ROUND(distance_km * 1000) WHERE distance_km IS NOT NULL;

-- Set distance_meters as NOT NULL now that data is migrated
ALTER TABLE timeline_trips ALTER COLUMN distance_meters SET NOT NULL;

-- Convert stay durations from minutes to seconds (since database currently stores minutes based on converter logic)
-- Note: The StreamingTimelineConverter does stayDuration * 60 when saving to DB, indicating DB expects seconds
-- But current data appears to be in minutes, so we multiply by 60 to get seconds
UPDATE timeline_stays SET stay_duration = stay_duration * 60;

-- Convert trip durations from minutes to seconds (same reasoning as stays)
-- The StreamingTimelineConverter does tripDuration * 60 when saving to DB, indicating DB expects seconds  
-- But current data appears to be in minutes, so we multiply by 60 to get seconds
UPDATE timeline_trips SET trip_duration = trip_duration * 60;

-- Drop the old distance_km column
ALTER TABLE timeline_trips DROP COLUMN distance_km;

-- Add comments to clarify the new units
COMMENT ON COLUMN timeline_trips.distance_meters IS 'Distance traveled in meters';
COMMENT ON COLUMN timeline_trips.trip_duration IS 'Duration of trip in seconds';
COMMENT ON COLUMN timeline_stays.stay_duration IS 'Duration of stay in seconds';
COMMENT ON COLUMN timeline_data_gaps.duration_seconds IS 'Duration of the gap in seconds';