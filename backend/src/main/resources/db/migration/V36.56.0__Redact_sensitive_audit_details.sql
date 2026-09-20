UPDATE audit_log
SET details = details - 'oldValue' - 'newValue'
WHERE target_type = 'SETTING'
  AND target_id IN (
      'geocoding.googlemaps.api-key',
      'geocoding.mapbox.access-token',
      'geocoding.geoapify.api-key',
      'geocoding.chibigeo.api-key',
      'weather.open-meteo.api-key',
      'weather.pirate.api-key',
      'backup.password',
      'system.notifications.apprise.auth-token',
      'backup.health.apprise.destination'
  )
  AND details IS NOT NULL;

UPDATE audit_log
SET details = details - 'token'
WHERE target_type = 'INVITATION'
  AND details ? 'token';
