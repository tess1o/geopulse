/**
 * Setting Helpers
 * Utility functions for transforming and parsing admin settings
 */

/**
 * Transform setting value based on valueType
 * @param {object} setting - Setting object from API
 * @returns {any} Transformed value
 */
export function transformSettingValue(setting) {
  if (setting.valueType === 'BOOLEAN') {
    return setting.value === 'true'
  }

  if (setting.valueType === 'INTEGER') {
    return parseInt(setting.value)
  }

  // For empty strings or "" values, return empty string
  if (setting.value === '""' || setting.value === '') {
    return ''
  }

  return setting.value
}

/**
 * Parse setting value for API submission
 * @param {object} setting - Setting object with currentValue
 * @returns {string} Stringified value for API
 */
export function parseSettingValue(setting) {
  if (setting.valueType === 'BOOLEAN') {
    return setting.currentValue.toString()
  }

  if (setting.valueType === 'INTEGER') {
    return setting.currentValue.toString()
  }

  return setting.currentValue
}

/**
 * Get placeholder text for input fields
 * @param {object} setting - Setting object
 * @returns {string} Placeholder text
 */
export function getPlaceholder(setting) {
  if (setting.key.includes('.language')) {
    return 'e.g., en-US, de, uk, ja (optional)'
  }

  if (setting.key.includes('.url')) {
    return 'Optional custom URL'
  }

  if (setting.valueType === 'ENCRYPTED') {
    return 'Enter new value to update'
  }

  return 'Optional'
}

/**
 * Check if encrypted field value should skip update
 * @param {object} setting - Setting object
 * @returns {boolean} True if should skip update
 */
export function shouldSkipEncryptedUpdate(setting) {
  return setting.valueType === 'ENCRYPTED' &&
         (setting.currentValue === '********' || setting.currentValue === '')
}
