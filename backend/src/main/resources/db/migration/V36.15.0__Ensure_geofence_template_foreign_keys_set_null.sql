-- Ensure template deletion never removes geofence rules/events by FK cascade.
ALTER TABLE geofence_rules
    DROP CONSTRAINT IF EXISTS fk_geofence_rules_enter_template;

ALTER TABLE geofence_rules
    ADD CONSTRAINT fk_geofence_rules_enter_template
        FOREIGN KEY (enter_template_id) REFERENCES notification_templates (id)
            ON DELETE SET NULL;

ALTER TABLE geofence_rules
    DROP CONSTRAINT IF EXISTS fk_geofence_rules_leave_template;

ALTER TABLE geofence_rules
    ADD CONSTRAINT fk_geofence_rules_leave_template
        FOREIGN KEY (leave_template_id) REFERENCES notification_templates (id)
            ON DELETE SET NULL;

ALTER TABLE geofence_events
    DROP CONSTRAINT IF EXISTS fk_geofence_events_template;

ALTER TABLE geofence_events
    ADD CONSTRAINT fk_geofence_events_template
        FOREIGN KEY (template_id) REFERENCES notification_templates (id)
            ON DELETE SET NULL;
