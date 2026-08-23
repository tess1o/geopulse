ALTER TABLE timeline_trip_path_matches
    ADD COLUMN IF NOT EXISTS source VARCHAR(40) NOT NULL DEFAULT 'ON_DEMAND',
    ADD COLUMN IF NOT EXISTS priority INTEGER NOT NULL DEFAULT 100;

CREATE INDEX IF NOT EXISTS idx_timeline_trip_path_matches_claim
    ON timeline_trip_path_matches (status, priority DESC, next_attempt_at, created_at);

CREATE TABLE IF NOT EXISTS map_matching_reconciliations (
    id BIGSERIAL PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    source VARCHAR(40) NOT NULL,
    range_start TIMESTAMPTZ NOT NULL,
    range_end TIMESTAMPTZ NOT NULL,
    cursor_at TIMESTAMPTZ NOT NULL,
    cursor_trip_id BIGINT NOT NULL DEFAULT 0,
    eligible_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    locked_at TIMESTAMPTZ,
    total_trips BIGINT NOT NULL DEFAULT 0,
    scanned_trips BIGINT NOT NULL DEFAULT 0,
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_map_matching_reconciliations_user_source UNIQUE (user_id, source)
);

CREATE INDEX IF NOT EXISTS idx_map_matching_reconciliations_claim
    ON map_matching_reconciliations (source, eligible_at, cursor_at)
    WHERE completed_at IS NULL;
