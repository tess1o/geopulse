-- Add GPS-based speed statistics columns to timeline_trips table
-- These fields enable more accurate travel classification using real GPS data

ALTER TABLE timeline_trips ADD COLUMN avg_gps_speed DOUBLE PRECISION;
ALTER TABLE timeline_trips ADD COLUMN max_gps_speed DOUBLE PRECISION;
ALTER TABLE timeline_trips ADD COLUMN speed_variance DOUBLE PRECISION;
ALTER TABLE timeline_trips ADD COLUMN low_accuracy_points_count INTEGER;

-- Add comments for documentation
COMMENT ON COLUMN timeline_trips.avg_gps_speed IS 'Average GPS speed from actual GPS readings (m/s). Used for more accurate travel classification.';
COMMENT ON COLUMN timeline_trips.max_gps_speed IS 'Maximum GPS speed from actual GPS readings (m/s). Used for more accurate travel classification.';
COMMENT ON COLUMN timeline_trips.speed_variance IS 'Speed variance indicating consistency of movement. Lower values = steady movement (walking), higher = variable (driving).';
COMMENT ON COLUMN timeline_trips.low_accuracy_points_count IS 'Count of GPS points with low accuracy (> threshold). Used to assess data quality for classification reliability.';