-- Issue #627: since v1.29.0 a continuity trip could be synthesized across a data gap, producing an
-- extra trip that overlapped the real one and drew a straight line over the gap. Any period generated
-- or regenerated since then (including imported history) may be affected, so the full timeline of
-- every user with GPS data is regenerated. Users are enqueued and notified on startup.
INSERT INTO timeline_regeneration_campaigns (campaign_key, affected_from, reason, source)
VALUES ('issue-627-data-gap-continuity-trips',
        '1970-01-01 00:00:00+00',
        'A timeline bug could create an extra trip across a gap in your GPS data, overlapping the real trip. Your timeline is being regenerated in the background to fix it. Your manual trip and stay corrections are kept.',
        'RELEASE')
ON CONFLICT (campaign_key) DO NOTHING;
