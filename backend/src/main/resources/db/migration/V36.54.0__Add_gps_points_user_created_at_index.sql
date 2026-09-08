CREATE INDEX IF NOT EXISTS idx_gps_points_user_created_at
    ON gps_points (user_id, created_at DESC);
