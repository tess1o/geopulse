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
    description: 'Primary geocoding service'
  },
  'geocoding.fallback-provider': {
    label: 'Fallback Provider',
    description: 'Fallback geocoding service (optional)'
  },
  'geocoding.delay-ms': {
    label: 'Request Delay',
    description: 'Delay between geocoding requests (milliseconds)'
  },
  'geocoding.nominatim.enabled': {
    label: 'Nominatim',
    description: 'Enable Nominatim geocoding provider'
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
    description: 'Language preference (BCP 47: en-US, de, uk, ja, etc.)'
  },
  'geocoding.googlemaps.api-key': {
    label: 'Google Maps API Key',
    description: 'API key for Google Maps (encrypted, enter to update)'
  },
  'geocoding.mapbox.access-token': {
    label: 'Mapbox Access Token',
    description: 'Access token for Mapbox (encrypted, enter to update)'
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
  }
}

/**
 * Geocoding provider options
 */
export const GEOCODING_PROVIDER_OPTIONS = [
  { label: 'Nominatim', value: 'nominatim' },
  { label: 'Photon', value: 'photon' },
  { label: 'Google Maps', value: 'googlemaps' },
  { label: 'Mapbox', value: 'mapbox' }
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
