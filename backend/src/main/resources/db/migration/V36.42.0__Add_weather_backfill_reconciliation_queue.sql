CREATE TABLE weather_backfill_reconciliations (
    user_id UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    range_start TIMESTAMPTZ NOT NULL,
    range_end TIMESTAMPTZ NOT NULL,
    cursor_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_weather_backfill_reconciliation_range
        CHECK (range_end > range_start),
    CONSTRAINT chk_weather_backfill_reconciliation_cursor
        CHECK (cursor_at >= range_start AND cursor_at <= range_end)
);

CREATE INDEX idx_weather_backfill_reconciliations_updated_at
    ON weather_backfill_reconciliations (updated_at);

-- Existing installations need one reconciliation pass. The worker processes these
-- ranges in bounded chunks and leaves the queue untouched while backfill is disabled.
INSERT INTO weather_backfill_reconciliations (
    user_id,
    range_start,
    range_end,
    cursor_at,
    created_at,
    updated_at
)
SELECT timeline.user_id,
       MIN(timeline.started_at),
       NOW(),
       MIN(timeline.started_at),
       NOW(),
       NOW()
FROM (
    SELECT user_id, timestamp AS started_at
    FROM timeline_stays
    UNION ALL
    SELECT user_id, timestamp AS started_at
    FROM timeline_trips
) timeline
JOIN users u ON u.id = timeline.user_id AND u.is_active = TRUE
GROUP BY timeline.user_id
HAVING NOW() > MIN(timeline.started_at)
ON CONFLICT (user_id) DO NOTHING;
