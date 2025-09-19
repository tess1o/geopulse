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
 * Get the current user's timezone from localStorage
 * @returns {string} User's timezone or 'UTC' as default
 */
export function getUserTimezone() {
  try {
    const userInfo = JSON.parse(localStorage.getItem('userInfo') || '{}')
    return userInfo.timezone || 'UTC'
  } catch (error) {
    console.warn('Failed to get user timezone from localStorage:', error)
    return 'UTC'
  }
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

/**
 * Update user's timezone in localStorage (normalized for Java compatibility)
 * @param {string} timezone - The timezone to set
 */
export function setUserTimezone(timezone) {
  try {
    const userInfo = JSON.parse(localStorage.getItem('userInfo') || '{}')
    userInfo.timezone = normalizeTimezone(timezone) || 'UTC'
    localStorage.setItem('userInfo', JSON.stringify(userInfo))
  } catch (error) {
    console.warn('Failed to update user timezone in localStorage:', error)
  }
}

/**
 * Check if user's stored timezone differs from browser timezone
 * @returns {boolean} True if timezones differ
 */
export function hasTimezoneChanged() {
  const userTimezone = getUserTimezone()
  const browserTimezone = getBrowserTimezone()
  return userTimezone !== browserTimezone
}