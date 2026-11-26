-- Background, batched user deletion.
--
-- An admin request records the deletion here and locks the user out; a worker then deletes the user's data in short
-- batches and finally the user row. The row has no foreign key to users, so it outlives the user and reports the
-- outcome.

CREATE TABLE user_deletions (
    user_id            UUID PRIMARY KEY,
    user_email         TEXT        NOT NULL,
    requested_by       UUID,
    requested_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    status             VARCHAR(16) NOT NULL,
    phase              VARCHAR(40),
    deleted_gps_points BIGINT      NOT NULL DEFAULT 0,
    attempts           INTEGER     NOT NULL DEFAULT 0,
    next_attempt_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_error         TEXT,
    completed_at       TIMESTAMPTZ,
    revision           BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT chk_user_deletions_status CHECK (status IN ('QUEUED', 'RUNNING', 'FAILED', 'COMPLETED'))
);

CREATE INDEX idx_user_deletions_due ON user_deletions (next_attempt_at) WHERE status IN ('QUEUED', 'RUNNING');

COMMENT ON TABLE user_deletions IS 'Admin-requested user deletions, worked off in batches by the user deletion worker';
COMMENT ON COLUMN user_deletions.revision IS 'Bumped by every change; a failure report for an older revision is ignored';
COMMENT ON COLUMN user_deletions.phase IS 'The table group the worker is deleting; NULL until the user is locked out';

-- DELETING keeps every timeline job (they all need IDLE) away from a user whose data is being deleted. Startup
-- recovery only resets PROCESSING and REGENERATING, so it survives a restart.
ALTER TABLE users DROP CONSTRAINT chk_timeline_status;
ALTER TABLE users ADD CONSTRAINT chk_timeline_status
    CHECK (timeline_status IN ('IDLE', 'PROCESSING', 'REGENERATING', 'DELETING'));

-- References to users that blocked deleting a user. Constraint names differ between installations, so they are
-- looked up and dropped, then added again with the right ON DELETE action.
DO $$
DECLARE
    r RECORD;
BEGIN
    FOR r IN
        SELECT c.conname, c.conrelid::regclass::text AS table_name
        FROM pg_constraint c
        JOIN pg_attribute a ON a.attrelid = c.conrelid AND a.attnum = c.conkey[1]
        WHERE c.contype = 'f'
          AND c.confrelid = 'users'::regclass
          AND array_length(c.conkey, 1) = 1
          AND (c.conrelid::regclass::text, a.attname::text) IN (
              ('audit_log', 'admin_user_id'),
              ('system_settings', 'updated_by'),
              ('oidc_providers', 'created_by'),
              ('oidc_providers', 'updated_by'),
              ('oidc_session_states', 'linking_user_id'),
              ('coverage_cells', 'user_id'),
              ('coverage_state', 'user_id'))
    LOOP
        EXECUTE format('ALTER TABLE %s DROP CONSTRAINT %I', r.table_name, r.conname);
    END LOOP;
END $$;

-- Audit entries of a deleted admin are kept, unlinked, with the admin's email.
ALTER TABLE audit_log ALTER COLUMN admin_user_id DROP NOT NULL;
ALTER TABLE audit_log ADD COLUMN admin_email VARCHAR(255);
UPDATE audit_log a SET admin_email = u.email FROM users u WHERE u.id = a.admin_user_id;
ALTER TABLE audit_log ADD CONSTRAINT fk_audit_log_admin_user
    FOREIGN KEY (admin_user_id) REFERENCES users (id) ON DELETE SET NULL;
COMMENT ON COLUMN audit_log.admin_email IS 'Email of the admin when the action was recorded; kept after the admin is deleted';

ALTER TABLE system_settings ADD CONSTRAINT fk_system_settings_updated_by
    FOREIGN KEY (updated_by) REFERENCES users (id) ON DELETE SET NULL;
ALTER TABLE oidc_providers ADD CONSTRAINT fk_oidc_providers_created_by
    FOREIGN KEY (created_by) REFERENCES users (id) ON DELETE SET NULL;
ALTER TABLE oidc_providers ADD CONSTRAINT fk_oidc_providers_updated_by
    FOREIGN KEY (updated_by) REFERENCES users (id) ON DELETE SET NULL;
ALTER TABLE oidc_session_states ADD CONSTRAINT fk_oidc_session_states_linking_user
    FOREIGN KEY (linking_user_id) REFERENCES users (id) ON DELETE CASCADE;
ALTER TABLE coverage_cells ADD CONSTRAINT fk_coverage_cells_user
    FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE;
ALTER TABLE coverage_state ADD CONSTRAINT fk_coverage_state_user
    FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE;

-- Deleting a GPS point or a stay clears these references; without an index each deleted row scans the table.
CREATE INDEX IF NOT EXISTS idx_geofence_events_point ON geofence_events (point_id) WHERE point_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_geofence_rule_state_last_point ON geofence_rule_state (last_point_id)
    WHERE last_point_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_trip_place_visit_match_stay ON trip_place_visit_match (stay_id)
    WHERE stay_id IS NOT NULL;
-- The deletion worker removes the events where the deleted user is the subject.
CREATE INDEX IF NOT EXISTS idx_geofence_events_subject ON geofence_events (subject_user_id);
