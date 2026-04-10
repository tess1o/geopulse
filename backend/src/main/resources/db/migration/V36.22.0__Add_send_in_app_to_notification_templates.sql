-- Add template-level in-app delivery toggle for geofence notifications.

ALTER TABLE notification_templates
    ADD COLUMN IF NOT EXISTS send_in_app BOOLEAN NOT NULL DEFAULT true;

UPDATE notification_templates
SET send_in_app = true
WHERE send_in_app IS NULL;
