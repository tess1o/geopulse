-- Seed default in-app geofence notification templates for all existing users.
-- Do not override existing defaults.

INSERT INTO notification_templates (
    user_id,
    name,
    destination,
    title_template,
    body_template,
    default_for_enter,
    default_for_leave,
    enabled,
    created_at,
    updated_at
)
SELECT
    u.id,
    'In-App Enter (Default)',
    '',
    'Geofence ENTER: {{geofenceName}}',
    '{{subjectName}} entered {{geofenceName}} at {{timestamp}} ({{lat}}, {{lon}})',
    true,
    false,
    true,
    NOW(),
    NOW()
FROM users u
WHERE NOT EXISTS (
    SELECT 1
    FROM notification_templates nt
    WHERE nt.user_id = u.id
      AND nt.default_for_enter = true
);

INSERT INTO notification_templates (
    user_id,
    name,
    destination,
    title_template,
    body_template,
    default_for_enter,
    default_for_leave,
    enabled,
    created_at,
    updated_at
)
SELECT
    u.id,
    'In-App Leave (Default)',
    '',
    'Geofence LEAVE: {{geofenceName}}',
    '{{subjectName}} left {{geofenceName}} at {{timestamp}} ({{lat}}, {{lon}})',
    false,
    true,
    true,
    NOW(),
    NOW()
FROM users u
WHERE NOT EXISTS (
    SELECT 1
    FROM notification_templates nt
    WHERE nt.user_id = u.id
      AND nt.default_for_leave = true
);
