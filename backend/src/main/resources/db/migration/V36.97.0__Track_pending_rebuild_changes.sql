-- Keep each pending change's range until a successful generation covers its revision. The per-user row remains
-- the summary read by jobs; retaining the individual ranges lets completion advance it past work already done,
-- even when GPS points or corrections arrive during a long rebuild.
CREATE TABLE timeline_rebuild_changes
(
    user_id     UUID NOT NULL REFERENCES timeline_rebuild_requests (user_id) ON DELETE CASCADE,
    revision    BIGINT NOT NULL,
    open_from   TIMESTAMPTZ,
    window_from TIMESTAMPTZ,
    window_to   TIMESTAMPTZ,
    PRIMARY KEY (user_id, revision)
);

INSERT INTO timeline_rebuild_changes (user_id, revision, open_from, window_from, window_to)
SELECT user_id, revision, open_from,
       CASE WHEN window_to IS NOT NULL THEN rebuild_from END, window_to
FROM timeline_rebuild_requests;
