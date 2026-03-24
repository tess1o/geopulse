-- Migrate geofence rules from single-subject to multi-subject model.

CREATE TABLE IF NOT EXISTS geofence_rule_subjects (
    rule_id BIGINT NOT NULL,
    subject_user_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (rule_id, subject_user_id),
    CONSTRAINT fk_geofence_rule_subjects_rule
        FOREIGN KEY (rule_id) REFERENCES geofence_rules (id)
            ON DELETE CASCADE,
    CONSTRAINT fk_geofence_rule_subjects_subject
        FOREIGN KEY (subject_user_id) REFERENCES users (id)
            ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_geofence_rule_subjects_rule
    ON geofence_rule_subjects (rule_id);

CREATE INDEX IF NOT EXISTS idx_geofence_rule_subjects_subject
    ON geofence_rule_subjects (subject_user_id);

INSERT INTO geofence_rule_subjects (rule_id, subject_user_id, created_at)
SELECT id, subject_user_id, NOW()
FROM geofence_rules
WHERE subject_user_id IS NOT NULL
ON CONFLICT DO NOTHING;

CREATE TABLE IF NOT EXISTS geofence_rule_state_v36_13 (
    rule_id BIGINT NOT NULL,
    subject_user_id UUID NOT NULL,
    current_inside BOOLEAN NOT NULL,
    last_point_id BIGINT,
    last_transition_at TIMESTAMPTZ,
    last_notified_at TIMESTAMPTZ,
    last_notified_inside BOOLEAN,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (rule_id, subject_user_id),
    CONSTRAINT fk_geofence_rule_state_v36_13_rule
        FOREIGN KEY (rule_id) REFERENCES geofence_rules (id)
            ON DELETE CASCADE,
    CONSTRAINT fk_geofence_rule_state_v36_13_subject
        FOREIGN KEY (subject_user_id) REFERENCES users (id)
            ON DELETE CASCADE,
    CONSTRAINT fk_geofence_rule_state_v36_13_point
        FOREIGN KEY (last_point_id) REFERENCES gps_points (id)
            ON DELETE SET NULL
);

INSERT INTO geofence_rule_state_v36_13 (
    rule_id,
    subject_user_id,
    current_inside,
    last_point_id,
    last_transition_at,
    last_notified_at,
    last_notified_inside,
    updated_at
)
SELECT rs.rule_id,
       gr.subject_user_id,
       rs.current_inside,
       rs.last_point_id,
       rs.last_transition_at,
       rs.last_notified_at,
       rs.last_notified_inside,
       rs.updated_at
FROM geofence_rule_state rs
         JOIN geofence_rules gr ON gr.id = rs.rule_id
WHERE gr.subject_user_id IS NOT NULL
ON CONFLICT DO NOTHING;

DROP TABLE IF EXISTS geofence_rule_state;
ALTER TABLE geofence_rule_state_v36_13 RENAME TO geofence_rule_state;

CREATE INDEX IF NOT EXISTS idx_geofence_rule_state_subject_rule
    ON geofence_rule_state (subject_user_id, rule_id);

DROP INDEX IF EXISTS idx_geofence_rules_subject_status;
ALTER TABLE geofence_rules DROP CONSTRAINT IF EXISTS fk_geofence_rules_subject;
ALTER TABLE geofence_rules DROP COLUMN IF EXISTS subject_user_id;
