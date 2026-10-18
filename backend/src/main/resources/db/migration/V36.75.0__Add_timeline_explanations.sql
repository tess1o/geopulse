-- Why the timeline engine created each stay, trip and data gap as it did: how it starts and ends and what was decided
-- on the way, as reason codes with the values they were based on. Shown in the timeline cards. Filled in on the next
-- regeneration; NULL until then.
ALTER TABLE timeline_stays ADD COLUMN IF NOT EXISTS explanation JSONB;
ALTER TABLE timeline_trips ADD COLUMN IF NOT EXISTS explanation JSONB;
ALTER TABLE timeline_data_gaps ADD COLUMN IF NOT EXISTS explanation JSONB;

COMMENT ON COLUMN timeline_stays.explanation IS 'How the timeline engine decided on this stay: {start, notes, end} reason codes';
COMMENT ON COLUMN timeline_trips.explanation IS 'How the timeline engine decided on this trip: {start, notes, end} reason codes';
COMMENT ON COLUMN timeline_data_gaps.explanation IS 'How the timeline engine decided on this data gap: {start, notes, end} reason codes';
