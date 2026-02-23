/**
 * Timezone utility functions for easy access to user's timezone
 */

// Mapping for timezone names that differ between JavaScript and Java
const TIMEZONE_MAPPING = {
  'Europe/Kiev': 'Europe/Kyiv',  // JavaScript returns old name, Java expects new name
  // Add more mappings if needed in the future
}

/**
 * Normalize timezone name to be compatible with Java backend
 * @param {string} timezone - The timezone to normalize
 * @returns {string} - Normalized timezone name
 */
function normalizeTimezone(timezone) {
  return TIMEZONE_MAPPING[timezone] || timezone
}

/**
 * Get the browser's detected timezone (normalized for Java compatibility)
 * @returns {string} Browser's timezone or 'UTC' as fallback
 */
export function getBrowserTimezone() {
  try {
    const browserTimezone = Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC'
    return normalizeTimezone(browserTimezone)
  } catch (error) {
    console.warn('Failed to detect browser timezone:', error)
    return 'UTC'
  }
}
