-- Expand existing manual, date-only timeline labels to include the full selected end day.
-- Only labels whose start and end are both local midnight are treated as date-only labels.

DROP TABLE IF EXISTS tmp_period_tag_end_time_backfill;

CREATE TEMP TABLE tmp_period_tag_end_time_backfill ON COMMIT DROP AS
SELECT
    pt.id AS period_tag_id,
    pt.start_time,
    pt.end_time AS old_end_time,
    (
        ((pt.end_time AT TIME ZONE u.timezone)::date + INTERVAL '1 day' - INTERVAL '1 millisecond')
        AT TIME ZONE u.timezone
    ) AS new_end_time
FROM period_tags pt
JOIN users u ON u.id = pt.user_id
WHERE COALESCE(pt.source, 'manual') = 'manual'
  AND COALESCE(pt.is_active, false) = false
  AND pt.end_time IS NOT NULL
  AND (pt.start_time AT TIME ZONE u.timezone)::time = TIME '00:00:00'
  AND (pt.end_time AT TIME ZONE u.timezone)::time = TIME '00:00:00'
  AND pt.end_time > pt.start_time;

DELETE FROM tmp_period_tag_end_time_backfill
WHERE new_end_time <= start_time
   OR new_end_time = old_end_time;

UPDATE trips t
SET end_time = c.new_end_time,
    updated_at = NOW()
FROM tmp_period_tag_end_time_backfill c
WHERE t.period_tag_id = c.period_tag_id
  AND t.start_time = c.start_time
  AND t.end_time = c.old_end_time;

UPDATE period_tags pt
SET end_time = c.new_end_time,
    updated_at = NOW()
FROM tmp_period_tag_end_time_backfill c
WHERE pt.id = c.period_tag_id;
