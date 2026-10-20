-- Add altitude evidence columns to timeline_trips table
-- These fields enable altitude-based flight classification (gliders, balloons, light aircraft)

ALTER TABLE timeline_trips ADD COLUMN altitude_sustained_height_meters DOUBLE PRECISION;
ALTER TABLE timeline_trips ADD COLUMN altitude_vertical_speed_p90 DOUBLE PRECISION;
ALTER TABLE timeline_trips ADD COLUMN altitude_sample_count INTEGER;

COMMENT ON COLUMN timeline_trips.altitude_sustained_height_meters IS 'Height above the higher of the start and end altitude that the trip stays at for at least a quarter of its time (m). Used for altitude-based flight classification.';
COMMENT ON COLUMN timeline_trips.altitude_vertical_speed_p90 IS '90th percentile of vertical speed over windows of about a minute (m/s). Used for altitude-based flight classification.';
COMMENT ON COLUMN timeline_trips.altitude_sample_count IS 'Count of GPS points with a usable altitude. NULL when altitude evidence was never calculated for the trip.';
