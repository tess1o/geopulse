-- Geofencing and notification foundation

CREATE TABLE IF NOT EXISTS notification_templates (
    id BIGSERIAL PRIMARY KEY,
    user_id UUID NOT NULL,
    name VARCHAR(120) NOT NULL,
    destination TEXT NOT NULL,
    title_template TEXT,
    body_template TEXT,
    default_for_enter BOOLEAN NOT NULL DEFAULT false,
    default_for_leave BOOLEAN NOT NULL DEFAULT false,
    enabled BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_notification_templates_user
        FOREIGN KEY (user_id) REFERENCES users (id)
            ON DELETE CASCADE
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_notification_templates_default_enter
    ON notification_templates (user_id)
    WHERE default_for_enter = true;

CREATE UNIQUE INDEX IF NOT EXISTS uq_notification_templates_default_leave
    ON notification_templates (user_id)
    WHERE default_for_leave = true;

CREATE INDEX IF NOT EXISTS idx_notification_templates_user
    ON notification_templates (user_id);

CREATE TABLE IF NOT EXISTS geofence_rules (
    id BIGSERIAL PRIMARY KEY,
    owner_user_id UUID NOT NULL,
    subject_user_id UUID NOT NULL,
    name VARCHAR(120) NOT NULL,
    north_east_lat DOUBLE PRECISION NOT NULL,
    north_east_lon DOUBLE PRECISION NOT NULL,
    south_west_lat DOUBLE PRECISION NOT NULL,
    south_west_lon DOUBLE PRECISION NOT NULL,
    monitor_enter BOOLEAN NOT NULL DEFAULT true,
    monitor_leave BOOLEAN NOT NULL DEFAULT true,
    cooldown_seconds INTEGER NOT NULL DEFAULT 120,
    enter_template_id BIGINT,
    leave_template_id BIGINT,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_geofence_rules_owner
        FOREIGN KEY (owner_user_id) REFERENCES users (id)
            ON DELETE CASCADE,
    CONSTRAINT fk_geofence_rules_subject
        FOREIGN KEY (subject_user_id) REFERENCES users (id)
            ON DELETE CASCADE,
    CONSTRAINT fk_geofence_rules_enter_template
        FOREIGN KEY (enter_template_id) REFERENCES notification_templates (id)
            ON DELETE SET NULL,
    CONSTRAINT fk_geofence_rules_leave_template
        FOREIGN KEY (leave_template_id) REFERENCES notification_templates (id)
            ON DELETE SET NULL,
    CONSTRAINT chk_geofence_rule_event_selection
        CHECK (monitor_enter = true OR monitor_leave = true),
    CONSTRAINT chk_geofence_rule_cooldown_non_negative
        CHECK (cooldown_seconds >= 0),
    CONSTRAINT chk_geofence_rule_latitudes
        CHECK (north_east_lat >= -90 AND north_east_lat <= 90 AND south_west_lat >= -90 AND south_west_lat <= 90),
    CONSTRAINT chk_geofence_rule_longitudes
        CHECK (north_east_lon >= -180 AND north_east_lon <= 180 AND south_west_lon >= -180 AND south_west_lon <= 180)
);

CREATE INDEX IF NOT EXISTS idx_geofence_rules_owner
    ON geofence_rules (owner_user_id);

CREATE INDEX IF NOT EXISTS idx_geofence_rules_subject_status
    ON geofence_rules (subject_user_id, status);

CREATE TABLE IF NOT EXISTS geofence_rule_state (
    rule_id BIGINT PRIMARY KEY,
    current_inside BOOLEAN NOT NULL,
    last_point_id BIGINT,
    last_transition_at TIMESTAMPTZ,
    last_notified_at TIMESTAMPTZ,
    last_notified_inside BOOLEAN,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_geofence_rule_state_rule
        FOREIGN KEY (rule_id) REFERENCES geofence_rules (id)
            ON DELETE CASCADE,
    CONSTRAINT fk_geofence_rule_state_point
        FOREIGN KEY (last_point_id) REFERENCES gps_points (id)
            ON DELETE SET NULL
);

CREATE TABLE IF NOT EXISTS geofence_events (
    id BIGSERIAL PRIMARY KEY,
    owner_user_id UUID NOT NULL,
    subject_user_id UUID NOT NULL,
    rule_id BIGINT NOT NULL,
    template_id BIGINT,
    point_id BIGINT,
    event_type VARCHAR(16) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    title TEXT,
    message TEXT NOT NULL,
    delivery_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    delivery_attempts INTEGER NOT NULL DEFAULT 0,
    last_delivery_error TEXT,
    delivered_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_geofence_events_owner
        FOREIGN KEY (owner_user_id) REFERENCES users (id)
            ON DELETE CASCADE,
    CONSTRAINT fk_geofence_events_subject
        FOREIGN KEY (subject_user_id) REFERENCES users (id)
            ON DELETE CASCADE,
    CONSTRAINT fk_geofence_events_rule
        FOREIGN KEY (rule_id) REFERENCES geofence_rules (id)
            ON DELETE CASCADE,
    CONSTRAINT fk_geofence_events_template
        FOREIGN KEY (template_id) REFERENCES notification_templates (id)
            ON DELETE SET NULL,
    CONSTRAINT fk_geofence_events_point
        FOREIGN KEY (point_id) REFERENCES gps_points (id)
            ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_geofence_events_owner_occurred
    ON geofence_events (owner_user_id, occurred_at DESC);

CREATE INDEX IF NOT EXISTS idx_geofence_events_delivery_status
    ON geofence_events (delivery_status, created_at);

CREATE INDEX IF NOT EXISTS idx_geofence_events_rule
    ON geofence_events (rule_id);
