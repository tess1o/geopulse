-- A pending timeline rebuild per user: the earliest GPS time whose timeline is out of date. It is written in the
-- same transaction as the change that needs it (saved, late or deleted points, a favorite or settings change that
-- could not start its own job, an import) and cleared by the next timeline generation that covers it. Without a
-- request the real-time job only maintains the ongoing data gap instead of rebuilding the latest stay.
--
-- `revision` changes on every write. A generation clears the request only if the revision is still the one it read
-- when it started, so a point saved while a generation is running keeps its request for the next run.
--
-- Separate from `users` on purpose: a generation holds the user's row lock for its whole duration, and saving a
-- point must never wait for that.
CREATE TABLE IF NOT EXISTS timeline_rebuild_requests
(
    user_id         UUID PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    rebuild_from    TIMESTAMPTZ NOT NULL,
    revision        BIGINT      NOT NULL DEFAULT 1,
    -- Consecutive failed generations for this request; the real-time job stops retrying it after a few, so that
    -- a range that always fails cannot stop the rest of the timeline from updating.
    failed_attempts INTEGER     NOT NULL DEFAULT 0,
    last_failed_at  TIMESTAMPTZ,
    last_error      TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Before this table existed the real-time job rebuilt every user's latest stays on every pass. Give each user with GPS
-- data one request at upgrade time, so the first pass does exactly that once and points saved just before the
-- upgrade are not left out of the timeline.
INSERT INTO timeline_rebuild_requests (user_id, rebuild_from)
SELECT u.id, NOW()
FROM users u
WHERE EXISTS (SELECT 1 FROM gps_points p WHERE p.user_id = u.id)
ON CONFLICT (user_id) DO NOTHING;
