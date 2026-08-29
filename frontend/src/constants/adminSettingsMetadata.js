/**
 * Admin Settings Metadata
 * Centralized configuration for all admin settings labels, descriptions, and options
 */

export const SETTING_METADATA = {
  // Authentication Settings
  'auth.registration.enabled': {
    label: 'Registration Enabled',
    description: 'Allow new users to register'
  },
  'auth.password-registration.enabled': {
    label: 'Password Registration',
    description: 'Allow registration with email/password'
  },
  'auth.oidc.registration.enabled': {
    label: 'OIDC Registration',
    description: 'Allow registration via OIDC providers'
  },
  'auth.oidc.auto-link-accounts': {
    label: 'Auto-Link OIDC Accounts',
    description: 'Automatically link OIDC accounts by email (security risk)'
  },
  'auth.oidc.callback-base-url': {
    label: 'OIDC Callback Base URL',
    description: 'Optional public base URL used to build OIDC callback redirects'
  },
  'auth.oidc.jwks-cache.ttl-hours': {
    label: 'JWKS Cache TTL',
    description: 'Hours to cache OIDC provider signing keys'
  },
  'auth.oidc.cleanup.session-states.enabled': {
    label: 'OIDC Session Cleanup',
    description: 'Clean up expired OIDC login session state records'
  },
  'auth.login.enabled': {
    label: 'Login Enabled',
    description: 'Allow users to log in (master switch)'
  },
  'auth.password-login.enabled': {
    label: 'Password Login',
    description: 'Allow login with email/password'
  },
  'auth.oidc.login.enabled': {
    label: 'OIDC Login',
    description: 'Allow login via OIDC providers'
  },
  'auth.admin-login-bypass.enabled': {
    label: 'Admin Login Bypass',
    description: 'Allow admins to bypass login restrictions (prevents lockout)'
  },
  'auth.guest-root-redirect-to-login.enabled': {
    label: 'Redirect Guests From Root',
    description: 'Redirect signed-out users from "/" to "/login" instead of showing Home'
  },

  // AI Settings
  'ai.logging.enabled': {
    label: 'Enable AI Request/Response Logging',
    description: 'Log detailed AI requests and responses for debugging (changes apply immediately)'
  },
  'ai.chat-memory.max-messages': {
    label: 'Chat Memory Size',
    description: 'Maximum number of messages to keep in conversation history per user'
  },
  'ai.tool-result.max-length': {
    label: 'Tool Result Max Length',
    description: 'Maximum characters in tool results to prevent token limit errors (12000 ≈ 3000 tokens)'
  },

  // Geocoding Settings
  'geocoding.primary-provider': {
    label: 'Primary Provider',
    description: 'Primary geocoding service for new lookups only; existing cached records stay unchanged until reconciled'
  },
  'geocoding.fallback-provider': {
    label: 'Fallback Provider',
    description: 'Fallback for new provider calls only; does not rewrite existing cached records'
  },
  'geocoding.delay-ms': {
    label: 'Request Delay',
    description: 'Delay between geocoding requests (milliseconds)'
  },
  'geocoding.nominatim.enabled': {
    label: 'Nominatim',
    description: 'Enable Nominatim geocoding provider'
  },
  'geocoding.nominatim.public-host-forward-search-enabled': {
    label: 'Nominatim Forward Search',
    description: 'Allow Nominatim search/autocomplete on public nominatim.openstreetmap.org (self-hosted Nominatim is always allowed)',
  },
  'geocoding.photon.enabled': {
    label: 'Photon',
    description: 'Enable Photon geocoding provider'
  },
  'geocoding.googlemaps.enabled': {
    label: 'Google Maps',
    description: 'Enable Google Maps geocoding provider'
  },
  'geocoding.mapbox.enabled': {
    label: 'Mapbox',
    description: 'Enable Mapbox geocoding provider'
  },
  'geocoding.geoapify.enabled': {
    label: 'Geoapify',
    description: 'Enable Geoapify geocoding provider'
  },
  'geocoding.chibigeo.enabled': {
    label: 'ChibiGeo',
    description: 'Enable ChibiGeo geocoding provider'
  },
  'geocoding.nominatim.url': {
    label: 'Nominatim URL',
    description: 'Custom Nominatim server URL (optional)'
  },
  'geocoding.nominatim.language': {
    label: 'Nominatim Language',
    description: 'Language preference (BCP 47: en-US, de, uk, ja, etc.)'
  },
  'geocoding.photon.url': {
    label: 'Photon URL',
    description: 'Custom Photon server URL (optional)'
  },
  'geocoding.photon.language': {
    label: 'Photon Language',
    description: 'Photon language code (allowed: de, pl, el, en, es, fa, fr, it, ja, ko). Leave empty for provider default'
  },
  'geocoding.googlemaps.api-key': {
    label: 'Google Maps API Key',
    description: 'API key for Google Maps (encrypted, enter to update)'
  },
  'geocoding.googlemaps.language': {
    label: 'Google Maps Language',
    description: 'See supported languages: https://developers.google.com/maps/faq#languagesupport'
  },
  'geocoding.mapbox.access-token': {
    label: 'Mapbox Access Token',
    description: 'Access token for Mapbox (encrypted, enter to update)'
  },
  'geocoding.geoapify.api-key': {
    label: 'Geoapify API Key',
    description: 'API key for Geoapify (encrypted, enter to update)'
  },
  'geocoding.geoapify.language': {
    label: 'Geoapify Language',
    description: 'Language preference for Geoapify responses (optional)'
  },
  'geocoding.geoapify.delay-ms': {
    label: 'Geoapify Delay',
    description: 'Delay between Geoapify requests (milliseconds)'
  },
  'geocoding.chibigeo.url': {
    label: 'ChibiGeo URL',
    description: 'Photon-compatible ChibiGeo URL'
  },
  'geocoding.chibigeo.api-key': {
    label: 'ChibiGeo API Key',
    description: 'API key for ChibiGeo (encrypted, enter to update)'
  },
  'geocoding.chibigeo.language': {
    label: 'ChibiGeo Language',
    description: 'Photon-compatible language code (allowed: de, pl, el, en, es, fa, fr, it, ja, ko). Leave empty for provider default'
  },
  'geocoding.chibigeo.delay-ms': {
    label: 'ChibiGeo Delay',
    description: 'Delay between ChibiGeo requests (milliseconds)'
  },
  'geocoding.cache.max-bbox-area-km2': {
    label: 'Cache BBox Limit',
    description: 'Maximum provider bounding box area accepted for geocoding cache matching'
  },
  'geocoding.reconcile.item.max-attempts': {
    label: 'Reconcile Attempts',
    description: 'Maximum attempts when reconciling one cached geocoding record'
  },
  'geocoding.reconcile.circuit-open-wait-ms': {
    label: 'Circuit Open Wait',
    description: 'Milliseconds to wait when provider circuit breaker is open during reconciliation'
  },
  'geocoding.reconcile.inter-item-delay-ms': {
    label: 'Reconcile Pacing',
    description: 'Delay between reconciled geocoding records in milliseconds'
  },

  // Weather Settings
  'weather.enabled': {
    label: 'Enable Weather',
    description: 'Enable weather samples for timeline stays and trips'
  },
  'weather.primary-provider': {
    label: 'Primary Provider',
    description: 'Weather provider used first for new weather samples'
  },
  'weather.secondary-provider': {
    label: 'Secondary Provider',
    description: 'Optional fallback provider used when the primary provider cannot return a sample'
  },
  'weather.open-meteo.enabled': {
    label: 'Enable Open-Meteo',
    description: 'Enable Open-Meteo as a selectable weather provider'
  },
  'weather.open-meteo.forecast-url': {
    label: 'Forecast URL',
    description: 'Open-Meteo forecast API base URL'
  },
  'weather.open-meteo.archive-url': {
    label: 'Archive URL',
    description: 'Open-Meteo historical archive API base URL'
  },
  'weather.open-meteo.api-key': {
    label: 'Open-Meteo API Key',
    description: 'Optional Open-Meteo API key (encrypted)'
  },
  'weather.pirate.enabled': {
    label: 'Enable Pirate Weather',
    description: 'Enable Pirate Weather as a selectable weather provider'
  },
  'weather.pirate.base-url': {
    label: 'Pirate Weather Forecast URL',
    description: 'Pirate Weather forecast API base URL'
  },
  'weather.pirate.time-machine-url': {
    label: 'Pirate Weather Time Machine URL',
    description: 'Pirate Weather historical time machine API base URL'
  },
  'weather.pirate.api-key': {
    label: 'Pirate Weather API Key',
    description: 'Pirate Weather API key (encrypted)'
  },
  'weather.ongoing.enabled': {
    label: 'Ongoing Weather',
    description: 'Create weather targets for active latest stays and trips'
  },
  'weather.ongoing.interval-minutes': {
    label: 'Ongoing Interval',
    description: 'Minimum minutes between ongoing weather samples (minimum 30)'
  },
  'weather.backfill.enabled': {
    label: 'Historical Weather Backfill',
    description: 'Discover historical weather targets automatically'
  },
  'weather.quota.daily-request-limit': {
    label: 'Daily Request Limit',
    description: 'Maximum provider requests per UTC day'
  },
  'weather.quota.ongoing-reserve': {
    label: 'Ongoing Reserve',
    description: 'Requests reserved for ongoing weather samples each day'
  },
  'weather.coordinate-precision': {
    label: 'Coordinate Precision',
    description: 'Decimal precision for weather location buckets'
  },
  'weather.failed-target-retry.enabled': {
    label: 'Retry Failed Targets',
    description: 'Retry stale failed weather targets after cooldown'
  },
  'weather.failed-target-retry.cooldown-hours': {
    label: 'Failed Retry Cooldown',
    description: 'Hours before a failed weather target can be retried'
  },
  'weather.open-meteo.connect-timeout-seconds': {
    label: 'Open-Meteo Connect Timeout',
    description: 'Seconds to wait while opening an Open-Meteo connection'
  },
  'weather.open-meteo.read-timeout-seconds': {
    label: 'Open-Meteo Read Timeout',
    description: 'Seconds to wait for Open-Meteo responses'
  },
  'weather.pirate.connect-timeout-seconds': {
    label: 'Pirate Connect Timeout',
    description: 'Seconds to wait while opening a Pirate Weather connection'
  },
  'weather.pirate.read-timeout-seconds': {
    label: 'Pirate Read Timeout',
    description: 'Seconds to wait for Pirate Weather responses'
  },
  'weather.targets.completed-retention-days': {
    label: 'Completed Target Retention',
    description: 'Days to retain completed weather target records'
  },
  'weather.targets.failed-retention-days': {
    label: 'Failed Target Retention',
    description: 'Days to retain failed weather target records'
  },
  'weather.targets.in-progress-timeout-minutes': {
    label: 'In-Progress Target Timeout',
    description: 'Minutes before in-progress weather targets are considered stale'
  },

  // Map Matching Settings
  'map-matching.enabled': {
    label: 'Enable Map Matching',
    description: 'Global prerequisite for user opt-in map matching'
  },
  'map-matching.automatic.enabled': {
    label: 'Automatic Future Matching',
    description: 'Map-match stable new trips for all users after the quiet period'
  },
  'map-matching.backfill.enabled': {
    label: 'Historical Backfill',
    description: 'Discover historical trips for all users in the background; progress is resumable'
  },
  'map-matching.automatic.quiet-period-minutes': {
    label: 'Quiet Period',
    description: 'Minutes after a timeline change before automatic matching starts'
  },
  'map-matching.provider': {
    label: 'Provider',
    description: 'Map matching engine used for trip route refinement'
  },
  'map-matching.valhalla.base-url': {
    label: 'Valhalla Base URL',
    description: 'Base URL for the self-hosted Valhalla service'
  },
  'map-matching.valhalla.connect-timeout-seconds': {
    label: 'Connect Timeout',
    description: 'Seconds to wait while opening a Valhalla connection'
  },
  'map-matching.valhalla.read-timeout-seconds': {
    label: 'Read Timeout',
    description: 'Seconds to wait for Valhalla map matching responses'
  },
  'map-matching.max-input-points': {
    label: 'Max Input Points',
    description: 'Maximum GPS points sent to Valhalla per trip'
  },
  'map-matching.max-trip-duration-hours': {
    label: 'Max Trip Duration',
    description: 'Trips longer than this many hours are skipped'
  },
  'map-matching.worker.batch-size': {
    label: 'Worker Batch Size',
    description: 'Map matching targets processed per scheduled worker run'
  },
  'map-matching.max-attempts': {
    label: 'Max Attempts',
    description: 'Maximum retry attempts for each map matching target'
  },
  'map-matching.quality.min-raw-distance-meters': {
    label: 'Quality Check Distance',
    description: 'Minimum raw chunk distance before partial-match quality checks apply'
  },
  'map-matching.quality.min-distance-coverage-percent': {
    label: 'Minimum Coverage',
    description: 'Minimum matched distance as a percent of raw chunk distance'
  },
  'map-matching.quality.max-discontinuity-percent': {
    label: 'Maximum Gap Percent',
    description: 'Maximum unmatched gap distance between matched fragments as a percent of raw chunk distance'
  },
  'map-matching.quality.max-short-discontinuity-meters': {
    label: 'Short Gap Allowance',
    description: 'Minimum absolute gap allowance between matched fragments'
  },

  // Import Settings
  'import.bulk-insert-batch-size': {
    label: 'Bulk Insert Batch Size',
    description: 'Number of GPS points to insert in a single database batch'
  },
  'import.merge-batch-size': {
    label: 'Merge Batch Size',
    description: 'Batch size when merging data with duplicate detection'
  },
  'import.large-file-threshold-mb': {
    label: 'Large File Threshold (MB)',
    description: 'Files larger than this are stored as temp files instead of in memory'
  },
  'import.temp-file-retention-hours': {
    label: 'Temp File Retention (Hours)',
    description: 'How long to keep temporary import files before cleanup'
  },
  'import.drop-folder.enabled': {
    label: 'Drop Folder Enabled',
    description: 'Enable drop folder imports'
  },
  'import.drop-folder.path': {
    label: 'Drop Folder Path',
    description: 'Filesystem path for drop folder imports'
  },
  'import.drop-folder.poll-interval-seconds': {
    label: 'Drop Scan Interval (Seconds)',
    description: 'How often to scan the drop folder'
  },
  'import.drop-folder.stable-age-seconds': {
    label: 'Drop File Stable Age (Seconds)',
    description: 'Minimum file age before import begins'
  },
  'import.drop-folder.geopulse-max-size-mb': {
    label: 'Drop GeoPulse Max Size (MB)',
    description: 'Max GeoPulse ZIP size for drop imports'
  },
  'import.drop-folder.runtime-identity': {
    label: 'Drop Folder Process Identity',
    description: 'Effective user/group running the backend process (read-only)',
    readOnly: true
  },
  'import.chunk-size-mb': {
    label: 'Chunk Size (MB)',
    description: 'Size of each upload chunk for large file uploads'
  },
  'import.max-file-size-gb': {
    label: 'Max File Size (GB)',
    description: 'Maximum file size allowed for imports'
  },
  'import.upload-timeout-hours': {
    label: 'Upload Timeout (Hours)',
    description: 'How long an upload session remains valid'
  },
  'import.transaction-timeout-minutes': {
    label: 'Import Transaction Timeout',
    description: 'Maximum transaction duration for one import job'
  },
  'import.upload-cleanup-minutes': {
    label: 'Upload Cleanup Interval',
    description: 'How often expired chunked upload sessions are cleaned up'
  },
  'import.geonames.cities.enabled': {
    label: 'City Import Enabled',
    description: 'Enable GeoNames city dataset import'
  },
  'import.geonames.cities.url': {
    label: 'City Dataset URL',
    description: 'GeoNames city dataset ZIP archive URL'
  },
  'import.geonames.cities.batch-size': {
    label: 'City Batch Size',
    description: 'Rows processed per GeoNames city import batch'
  },
  'import.geonames.cities.min-row-threshold': {
    label: 'City Row Threshold',
    description: 'Minimum staged GeoNames city rows required before replacing data'
  },
  'import.geonames.cities.force-refresh': {
    label: 'Force City Refresh',
    description: 'Reimport city data even when existing data passes the threshold'
  },
  'import.geonames.cities.connect-timeout-seconds': {
    label: 'City Connect Timeout',
    description: 'Seconds to wait while connecting to the GeoNames city download'
  },
  'import.geonames.cities.read-timeout-seconds': {
    label: 'City Read Timeout',
    description: 'Seconds to wait for GeoNames city download reads'
  },
  'import.geonames.countries.enabled': {
    label: 'Country Import Enabled',
    description: 'Enable GeoNames country dataset import'
  },
  'import.geonames.countries.url': {
    label: 'Country Dataset URL',
    description: 'GeoNames country dataset URL'
  },
  'import.geonames.countries.batch-size': {
    label: 'Country Batch Size',
    description: 'Rows processed per GeoNames country import batch'
  },
  'import.geonames.countries.min-row-threshold': {
    label: 'Country Row Threshold',
    description: 'Minimum staged GeoNames country rows required before replacing data'
  },
  'import.geonames.countries.force-refresh': {
    label: 'Force Country Refresh',
    description: 'Reimport country data even when existing data passes the threshold'
  },
  'import.geonames.countries.connect-timeout-seconds': {
    label: 'Country Connect Timeout',
    description: 'Seconds to wait while connecting to the GeoNames country download'
  },
  'import.geonames.countries.read-timeout-seconds': {
    label: 'Country Read Timeout',
    description: 'Seconds to wait for GeoNames country download reads'
  },
  'import.geojson-streaming-batch-size': {
    label: 'GeoJSON Batch Size',
    description: 'Batch size for streaming GeoJSON parser'
  },
  'import.googletimeline-streaming-batch-size': {
    label: 'Google Timeline Batch Size',
    description: 'Batch size for streaming Google Timeline parser'
  },
  'import.gpx-streaming-batch-size': {
    label: 'GPX Batch Size',
    description: 'Batch size for streaming GPX parser'
  },
  'import.csv-streaming-batch-size': {
    label: 'CSV Batch Size',
    description: 'Batch size for streaming CSV parser'
  },
  'import.owntracks-streaming-batch-size': {
    label: 'OwnTracks Batch Size',
    description: 'Batch size for streaming OwnTracks parser'
  },

  // Export Settings
  'export.max-jobs-per-user': {
    label: 'Max Jobs Per User',
    description: 'Maximum number of export jobs a user can have at once'
  },
  'export.job-expiry-hours': {
    label: 'Job Expiry (Hours)',
    description: 'Hours before completed export jobs are automatically deleted'
  },
  'export.concurrent-jobs-limit': {
    label: 'Concurrent Jobs Limit',
    description: 'Maximum number of export jobs processed simultaneously'
  },
  'export.batch-size': {
    label: 'Batch Size',
    description: 'Number of records to process in each batch during export'
  },
  'export.trip-point-limit': {
    label: 'Trip Point Limit',
    description: 'Maximum GPS points per single trip export'
  },
  'export.temp-file-retention-hours': {
    label: 'Temp File Retention (Hours)',
    description: 'How long to keep temporary export files before cleanup'
  },

  // System / Notifications Settings
  'system.user.default-distance-unit': {
    label: 'Default Distance Unit',
    description: 'Distance unit assigned to newly created users'
  },
  'system.user.default-temperature-unit': {
    label: 'Default Temperature Unit',
    description: 'Temperature unit assigned to newly created users'
  },
  'system.timeline.view.item-limit': {
    label: 'Timeline View Item Limit',
    description: 'Maximum number of timeline items returned in a single view request'
  },
  'system.timeline.processing.thread-pool-size': {
    label: 'Timeline Processing Threads',
    description: 'Number of worker threads used for timeline processing'
  },
  'system.version-check.github-api-url': {
    label: 'Release API URL',
    description: 'GitHub-compatible API URL used to check for GeoPulse updates'
  },
  'system.version-check.release-url': {
    label: 'Release Page URL',
    description: 'Fallback release page URL shown with update information'
  },
  'system.version-check.cache-ttl-minutes': {
    label: 'Update Cache TTL',
    description: 'Minutes to cache version update checks'
  },
  'system.version-check.connect-timeout-seconds': {
    label: 'Update Connect Timeout',
    description: 'Seconds to wait while connecting to the update API'
  },
  'system.version-check.read-timeout-seconds': {
    label: 'Update Read Timeout',
    description: 'Seconds to wait for update API responses'
  },
  'system.water-dataset.url': {
    label: 'Water Dataset URL',
    description: 'Water dataset archive URL used for Boat setup'
  },
  'system.water-dataset.sha256': {
    label: 'Water Dataset Checksum',
    description: 'Optional expected SHA-256 checksum for the water dataset archive'
  },
  'system.water-dataset.auto-import': {
    label: 'Water Auto-Import',
    description: 'Automatically import the water dataset when Boat setup requires it'
  },
  'system.water-dataset.connect-timeout-seconds': {
    label: 'Water Connect Timeout',
    description: 'Seconds to wait while connecting to the water dataset download'
  },
  'system.water-dataset.download-timeout-hours': {
    label: 'Water Download Timeout',
    description: 'Maximum duration for a water dataset download'
  },
  'system.water-dataset.download-stall-timeout-seconds': {
    label: 'Water Stall Timeout',
    description: 'Seconds without downloaded bytes before the water download fails'
  },
  'system.water-dataset.setup-start-timeout-minutes': {
    label: 'Boat Setup Start Timeout',
    description: 'Minutes before a queued Boat setup job is treated as failed to start'
  },
  'system.notifications.apprise.enabled': {
    label: 'Enable Apprise Notifications',
    description: 'Enable delivery of geofence alerts to Apprise destinations'
  },
  'system.notifications.apprise.api-url': {
    label: 'Apprise API URL',
    description: 'Base URL of the Apprise API service (for example http://apprise-api:8000)'
  },
  'system.notifications.apprise.auth-token': {
    label: 'Apprise API Token',
    description: 'Optional API token/key used for authenticating with Apprise (encrypted)'
  },
  'system.notifications.apprise.timeout-ms': {
    label: 'Apprise Timeout (ms)',
    description: 'HTTP request timeout when sending notifications to Apprise'
  },
  'system.notifications.apprise.verify-tls': {
    label: 'Verify TLS Certificates',
    description: 'Whether HTTPS certificate validation is enabled for Apprise requests'
  },
  'system.notifications.geofence-events.cleanup.enabled': {
    label: 'Enable Geofence Event Cleanup',
    description: 'Enable automatic cleanup of old geofence notification events'
  },
  'system.notifications.geofence-events.retention-days': {
    label: 'Retention (Days)',
    description: 'Delete geofence notification events older than this number of days'
  }
}

/**
 * Geocoding provider options
 */
export const GEOCODING_PROVIDER_OPTIONS = [
  { label: 'Nominatim', value: 'nominatim' },
  { label: 'Photon', value: 'photon' },
  { label: 'Google Maps', value: 'googlemaps' },
  { label: 'Mapbox', value: 'mapbox' },
  { label: 'Geoapify', value: 'geoapify' },
  { label: 'ChibiGeo', value: 'chibigeo' }
]

export const DISTANCE_UNIT_OPTIONS = [
  { label: 'Kilometers (km, m)', value: 'KILOMETERS' },
  { label: 'Miles (mi, ft)', value: 'MILES' }
]

export const TEMPERATURE_UNIT_OPTIONS = [
  { label: 'Celsius (°C)', value: 'CELSIUS' },
  { label: 'Fahrenheit (°F)', value: 'FAHRENHEIT' }
]

/**
 * Get setting metadata by key
 * @param {string} key - Setting key
 * @returns {object} Setting metadata with label and description
 */
export function getSettingMetadata(key) {
  return SETTING_METADATA[key] || {
    label: key,
    description: ''
  }
}
