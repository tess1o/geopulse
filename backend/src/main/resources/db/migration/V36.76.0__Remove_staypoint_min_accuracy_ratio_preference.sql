-- The accuracy-ratio stay check never fired: the accuracy pre-filter drops every point it would have counted as
-- inaccurate. The check and its staypointMinAccuracyRatio setting are retired, so remove the stored value.
UPDATE users
SET timeline_preferences = timeline_preferences - 'staypointMinAccuracyRatio'
WHERE timeline_preferences IS NOT NULL
  AND timeline_preferences ? 'staypointMinAccuracyRatio';
