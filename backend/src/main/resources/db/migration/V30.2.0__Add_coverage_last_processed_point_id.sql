ALTER TABLE coverage_state
    ADD COLUMN IF NOT EXISTS last_processed_point_id BIGINT;

UPDATE coverage_state cs
SET last_processed_point_id = (
    SELECT MAX(gp.id)
    FROM gps_points gp
    WHERE gp.user_id = cs.user_id
      AND gp.timestamp = cs.last_processed
)
WHERE cs.last_processed IS NOT NULL
  AND cs.last_processed_point_id IS NULL;
