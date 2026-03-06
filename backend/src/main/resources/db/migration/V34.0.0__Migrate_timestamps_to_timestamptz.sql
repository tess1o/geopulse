-- Migrate Instant-backed columns from TIMESTAMP WITHOUT TIME ZONE to TIMESTAMPTZ.
-- Existing values are interpreted as UTC wall-clock timestamps to preserve instants.
-- The migration is idempotent per column: already-migrated columns are skipped.
--
-- NOTE: DATE(timestamptz) is not immutable, so old DATE(timestamp) expression
-- indexes on timeline tables must be recreated in an immutable UTC form.

DROP INDEX IF EXISTS idx_timeline_stays_user_date;
DROP INDEX IF EXISTS idx_timeline_trips_user_date;

CREATE OR REPLACE FUNCTION migrate_timestamp_to_timestamptz(p_table TEXT, p_column TEXT)
RETURNS void
LANGUAGE plpgsql
AS $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = p_table
          AND column_name = p_column
          AND udt_name = 'timestamp'
    ) THEN
        EXECUTE format(
                'ALTER TABLE %I ALTER COLUMN %I TYPE TIMESTAMPTZ USING %I AT TIME ZONE ''UTC''',
                p_table,
                p_column,
                p_column
                );
    END IF;
END;
$$;

SELECT migrate_timestamp_to_timestamptz('users', 'created_at');
SELECT migrate_timestamp_to_timestamptz('users', 'updated_at');
SELECT migrate_timestamp_to_timestamptz('friend_invitations', 'sent_at');
SELECT migrate_timestamp_to_timestamptz('gps_points', 'timestamp');
SELECT migrate_timestamp_to_timestamptz('gps_points', 'created_at');
SELECT migrate_timestamp_to_timestamptz('reverse_geocoding_location', 'created_at');
SELECT migrate_timestamp_to_timestamptz('reverse_geocoding_location', 'last_accessed_at');
SELECT migrate_timestamp_to_timestamptz('shared_link', 'expires_at');
SELECT migrate_timestamp_to_timestamptz('shared_link', 'created_at');
SELECT migrate_timestamp_to_timestamptz('shared_link', 'start_date');
SELECT migrate_timestamp_to_timestamptz('shared_link', 'end_date');
SELECT migrate_timestamp_to_timestamptz('user_friends', 'created_at');
SELECT migrate_timestamp_to_timestamptz('timeline_stays', 'timestamp');
SELECT migrate_timestamp_to_timestamptz('timeline_stays', 'created_at');
SELECT migrate_timestamp_to_timestamptz('timeline_stays', 'last_updated');
SELECT migrate_timestamp_to_timestamptz('timeline_trips', 'timestamp');
SELECT migrate_timestamp_to_timestamptz('timeline_trips', 'last_updated');
SELECT migrate_timestamp_to_timestamptz('timeline_trips', 'created_at');
SELECT migrate_timestamp_to_timestamptz('user_oidc_connections', 'linked_at');
SELECT migrate_timestamp_to_timestamptz('user_oidc_connections', 'last_login_at');
SELECT migrate_timestamp_to_timestamptz('oidc_session_states', 'created_at');
SELECT migrate_timestamp_to_timestamptz('oidc_session_states', 'expires_at');
SELECT migrate_timestamp_to_timestamptz('user_badges', 'earned_date');
SELECT migrate_timestamp_to_timestamptz('user_badges', 'last_calculated');
SELECT migrate_timestamp_to_timestamptz('user_badges', 'created_at');
SELECT migrate_timestamp_to_timestamptz('user_badges', 'updated_at');
SELECT migrate_timestamp_to_timestamptz('system_settings', 'updated_at');
SELECT migrate_timestamp_to_timestamptz('audit_log', 'timestamp');
SELECT migrate_timestamp_to_timestamptz('user_invitations', 'created_at');
SELECT migrate_timestamp_to_timestamptz('user_invitations', 'expires_at');
SELECT migrate_timestamp_to_timestamptz('user_invitations', 'used_at');
SELECT migrate_timestamp_to_timestamptz('user_invitations', 'revoked_at');
SELECT migrate_timestamp_to_timestamptz('oidc_providers', 'metadata_cached_at');
SELECT migrate_timestamp_to_timestamptz('oidc_providers', 'created_at');
SELECT migrate_timestamp_to_timestamptz('oidc_providers', 'updated_at');
SELECT migrate_timestamp_to_timestamptz('user_friend_permissions', 'created_at');
SELECT migrate_timestamp_to_timestamptz('user_friend_permissions', 'updated_at');
SELECT migrate_timestamp_to_timestamptz('coverage_cells', 'first_seen');
SELECT migrate_timestamp_to_timestamptz('coverage_cells', 'last_seen');
SELECT migrate_timestamp_to_timestamptz('coverage_state', 'last_processed');
SELECT migrate_timestamp_to_timestamptz('coverage_state', 'updated_at');
SELECT migrate_timestamp_to_timestamptz('coverage_state', 'processing_started_at');

CREATE INDEX IF NOT EXISTS idx_timeline_stays_user_date
    ON timeline_stays (user_id, DATE(timestamp AT TIME ZONE 'UTC'));

CREATE INDEX IF NOT EXISTS idx_timeline_trips_user_date
    ON timeline_trips (user_id, DATE(timestamp AT TIME ZONE 'UTC'));

DROP FUNCTION migrate_timestamp_to_timestamptz(TEXT, TEXT);
