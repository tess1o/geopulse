-- GeoPulse 2.1.0 changes how the timeline engine builds stays and trips (stops recorded with few points, favorite
-- areas, burst-resistant speeds, water evidence, flight rules) and records why it created each stay, trip and data
-- gap. Timelines generated before the upgrade have neither, so the full timeline of every user with GPS data is
-- regenerated. Users are enqueued and notified on startup.

-- A release campaign still in progress (for example issue #627 on an upgrade straight from 1.x) regenerates a range
-- this campaign covers too. It is cancelled, so no user is regenerated twice.
UPDATE timeline_regeneration_campaign_users cu
SET status     = 'SKIPPED',
    updated_at = NOW()
FROM timeline_regeneration_campaigns c
WHERE c.id = cu.campaign_id
  AND c.source = 'RELEASE'
  AND c.status = 'ACTIVE'
  AND cu.status IN ('PENDING', 'RUNNING', 'FAILED');

UPDATE timeline_regeneration_campaigns c
SET status        = 'CANCELLED',
    skipped_users = (SELECT COUNT(*)
                     FROM timeline_regeneration_campaign_users cu
                     WHERE cu.campaign_id = c.id
                       AND cu.status = 'SKIPPED'),
    updated_at    = NOW()
WHERE c.source = 'RELEASE'
  AND c.status = 'ACTIVE';

INSERT INTO timeline_regeneration_campaigns (campaign_key, affected_from, reason, source)
VALUES ('release-2.1.0-timeline-engine',
        '1970-01-01 00:00:00+00',
        'GeoPulse 2.1.0 detects stays and trips more accurately and records why each stay, trip and data gap looks the way it does. Your timeline is being regenerated in the background to apply this. Your manual trip and stay corrections are kept.',
        'RELEASE')
ON CONFLICT (campaign_key) DO NOTHING;
