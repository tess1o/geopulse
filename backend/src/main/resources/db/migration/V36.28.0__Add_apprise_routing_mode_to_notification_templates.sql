-- Add dual Apprise routing support for geofence notification templates.

ALTER TABLE notification_templates
    ADD COLUMN IF NOT EXISTS external_routing_mode VARCHAR(32) NOT NULL DEFAULT 'URLS';

ALTER TABLE notification_templates
    ADD COLUMN IF NOT EXISTS apprise_config_key VARCHAR(255);

ALTER TABLE notification_templates
    ADD COLUMN IF NOT EXISTS apprise_tag VARCHAR(255);

UPDATE notification_templates
SET external_routing_mode = 'URLS'
WHERE external_routing_mode IS NULL OR btrim(external_routing_mode) = '';
