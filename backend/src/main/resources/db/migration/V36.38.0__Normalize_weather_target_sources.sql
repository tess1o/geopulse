UPDATE weather_sample_targets
SET source = 'HISTORICAL_BACKFILL'
WHERE source = 'VISIBLE_RANGE';

UPDATE weather_samples
SET source = 'HISTORICAL_BACKFILL'
WHERE source = 'VISIBLE_RANGE';
