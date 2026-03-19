-- Update geofence notification macro contract and refresh default template text.

UPDATE notification_templates
SET title_template = regexp_replace(title_template, '\{\{\s*eventType\s*\}\}', '{{eventCode}}', 'g'),
    body_template = regexp_replace(body_template, '\{\{\s*eventType\s*\}\}', '{{eventCode}}', 'g'),
    updated_at = NOW()
WHERE (title_template IS NOT NULL AND title_template ~ '\{\{\s*eventType\s*\}\}')
   OR (body_template IS NOT NULL AND body_template ~ '\{\{\s*eventType\s*\}\}');

UPDATE notification_templates
SET title_template = 'Geofence Alert: {{geofenceName}}',
    body_template = '{{subjectName}} {{eventVerb}} {{geofenceName}} at {{timestamp}} ({{lat}}, {{lon}})',
    updated_at = NOW()
WHERE name IN ('In-App Enter (Default)', 'In-App Leave (Default)')
  AND destination = ''
  AND (
      title_template IN ('Geofence ENTER: {{geofenceName}}', 'Geofence LEAVE: {{geofenceName}}')
      OR body_template IN (
          '{{subjectName}} entered {{geofenceName}} at {{timestamp}} ({{lat}}, {{lon}})',
          '{{subjectName}} left {{geofenceName}} at {{timestamp}} ({{lat}}, {{lon}})'
      )
  );
