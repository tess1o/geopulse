-- Add timeline display preference columns to users table
-- These settings affect ONLY how GPS paths are rendered in the UI, not timeline generation

ALTER TABLE users
ADD COLUMN timeline_display_path_simplification_enabled BOOLEAN DEFAULT TRUE,
ADD COLUMN timeline_display_path_simplification_tolerance DOUBLE PRECISION DEFAULT 15.0,
ADD COLUMN timeline_display_path_max_points INTEGER DEFAULT 0,
ADD COLUMN timeline_display_path_adaptive_simplification BOOLEAN DEFAULT TRUE;

-- Add column comments for documentation
COMMENT ON COLUMN users.timeline_display_path_simplification_enabled IS 'Display-only: Enable GPS path simplification when rendering paths in UI';
COMMENT ON COLUMN users.timeline_display_path_simplification_tolerance IS 'Display-only: Douglas-Peucker tolerance for path simplification (1.0-100.0 meters)';
COMMENT ON COLUMN users.timeline_display_path_max_points IS 'Display-only: Maximum number of points to display in path (0 = unlimited)';
COMMENT ON COLUMN users.timeline_display_path_adaptive_simplification IS 'Display-only: Enable adaptive simplification based on zoom level';

-- Migrate existing data from timeline_preferences JSONB to new columns
UPDATE users
SET
    timeline_display_path_simplification_enabled = COALESCE((timeline_preferences->>'pathSimplificationEnabled')::boolean, TRUE),
    timeline_display_path_simplification_tolerance = COALESCE((timeline_preferences->>'pathSimplificationTolerance')::double precision, 15.0),
    timeline_display_path_max_points = COALESCE((timeline_preferences->>'pathMaxPoints')::integer, 0),
    timeline_display_path_adaptive_simplification = COALESCE((timeline_preferences->>'pathAdaptiveSimplification')::boolean, TRUE)
WHERE timeline_preferences IS NOT NULL;

-- Note: JSONB data is kept temporarily for rollback safety
-- It will be cleaned up in V28.1.0 after confirming successful migration
