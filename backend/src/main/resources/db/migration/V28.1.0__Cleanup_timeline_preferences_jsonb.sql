UPDATE users
SET timeline_preferences = timeline_preferences
  - 'pathSimplificationEnabled'
  - 'pathSimplificationTolerance'
  - 'pathMaxPoints'
  - 'pathAdaptiveSimplification'
WHERE timeline_preferences IS NOT NULL
  AND (
    timeline_preferences ? 'pathSimplificationEnabled'
    OR timeline_preferences ? 'pathSimplificationTolerance'
    OR timeline_preferences ? 'pathMaxPoints'
    OR timeline_preferences ? 'pathAdaptiveSimplification'
  );

-- Add comment indicating cleanup completion
COMMENT ON TABLE users IS 'Users table - Path simplification settings migrated to dedicated columns in V28.0.0, JSONB cleanup completed in V28.1.0';
