-- Supports full timeline regeneration keyset scans ordered by timestamp and id.
CREATE INDEX IF NOT EXISTS idx_gps_points_timeline_stream_keyset
    ON gps_points (user_id, timestamp, id);
