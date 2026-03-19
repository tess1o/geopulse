-- Generic user inbox notifications (decoupled from producer tables)
CREATE TABLE IF NOT EXISTS user_notifications (
    id BIGSERIAL PRIMARY KEY,
    owner_user_id UUID NOT NULL,
    source VARCHAR(40) NOT NULL,
    type VARCHAR(80) NOT NULL,
    title TEXT,
    message TEXT NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    seen_at TIMESTAMPTZ,
    delivery_status VARCHAR(20),
    object_ref VARCHAR(255),
    metadata JSONB,
    dedupe_key VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_user_notifications_owner
        FOREIGN KEY (owner_user_id) REFERENCES users (id)
        ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_user_notifications_owner_seen_occurred
    ON user_notifications (owner_user_id, seen_at, occurred_at DESC);

CREATE INDEX IF NOT EXISTS idx_user_notifications_owner_occurred
    ON user_notifications (owner_user_id, occurred_at DESC);

CREATE INDEX IF NOT EXISTS idx_user_notifications_owner_source_occurred
    ON user_notifications (owner_user_id, source, occurred_at DESC);

CREATE UNIQUE INDEX IF NOT EXISTS uq_user_notifications_dedupe_key
    ON user_notifications (dedupe_key)
    WHERE dedupe_key IS NOT NULL;

-- Backfill from existing geofence events (idempotent via dedupe key)
INSERT INTO user_notifications (
    owner_user_id,
    source,
    type,
    title,
    message,
    occurred_at,
    seen_at,
    delivery_status,
    object_ref,
    metadata,
    dedupe_key,
    created_at
)
SELECT
    ge.owner_user_id,
    'GEOFENCE',
    CASE ge.event_type
        WHEN 'ENTER' THEN 'GEOFENCE_ENTER'
        ELSE 'GEOFENCE_LEAVE'
        END,
    ge.title,
    ge.message,
    ge.occurred_at,
    ge.seen_at,
    ge.delivery_status,
    ge.id::TEXT,
    jsonb_build_object(
        'ruleId', ge.rule_id,
        'ruleName', gr.name,
        'subjectUserId', ge.subject_user_id,
        'subjectDisplayName', COALESCE(NULLIF(TRIM(su.full_name), ''), su.email),
        'eventCode', ge.event_type,
        'eventVerb', CASE ge.event_type WHEN 'ENTER' THEN 'entered' ELSE 'left' END,
        'lat', CASE WHEN gp.coordinates IS NULL THEN NULL ELSE ST_Y(gp.coordinates) END,
        'lon', CASE WHEN gp.coordinates IS NULL THEN NULL ELSE ST_X(gp.coordinates) END
    ),
    CONCAT('geofence-event:', ge.id),
    COALESCE(ge.created_at, ge.occurred_at, NOW())
FROM geofence_events ge
         LEFT JOIN geofence_rules gr ON ge.rule_id = gr.id
         LEFT JOIN users su ON ge.subject_user_id = su.id
         LEFT JOIN gps_points gp ON ge.point_id = gp.id
WHERE NOT EXISTS (
    SELECT 1
    FROM user_notifications un
    WHERE un.dedupe_key = CONCAT('geofence-event:', ge.id)
);
