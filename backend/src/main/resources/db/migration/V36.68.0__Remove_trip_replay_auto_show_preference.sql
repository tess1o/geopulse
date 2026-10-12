-- The trip summary card now carries the Replay button, so the "auto-show replay controls" preference is gone.
UPDATE users
SET timeline_display_preferences = timeline_display_preferences - 'autoShowTripReplayControls'
WHERE timeline_display_preferences ? 'autoShowTripReplayControls';
