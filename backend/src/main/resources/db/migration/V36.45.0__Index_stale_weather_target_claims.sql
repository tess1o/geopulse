CREATE INDEX idx_weather_targets_in_progress_locked_at
    ON weather_sample_targets (locked_at)
    WHERE status = 'IN_PROGRESS' AND locked_at IS NOT NULL;
