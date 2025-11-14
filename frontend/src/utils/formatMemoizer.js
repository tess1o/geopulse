/**
 * Memoized formatting utilities for Timeline components
 * Caches formatted date/time strings to avoid redundant computations
 */

class FormatMemoizer {
  constructor(maxCacheSize = 1000) {
    this.cache = new Map()
    this.maxCacheSize = maxCacheSize
  }

  /**
   * Get a cached value or compute and cache it
   * @param {string} key - Cache key
   * @param {Function} computeFn - Function to compute the value if not cached
   * @returns {any} Cached or computed value
   */
  get(key, computeFn) {
    if (this.cache.has(key)) {
      return this.cache.get(key)
    }

    const value = computeFn()

    // Implement simple LRU by deleting oldest entries when cache is full
    if (this.cache.size >= this.maxCacheSize) {
      const firstKey = this.cache.keys().next().value
      this.cache.delete(firstKey)
    }

    this.cache.set(key, value)
    return value
  }

  /**
   * Clear the entire cache
   */
  clear() {
    this.cache.clear()
  }

  /**
   * Clear cache entries matching a pattern
   * @param {RegExp} pattern - Pattern to match keys
   */
  clearPattern(pattern) {
    for (const key of this.cache.keys()) {
      if (pattern.test(key)) {
        this.cache.delete(key)
      }
    }
  }

  /**
   * Get cache size
   * @returns {number} Number of cached entries
   */
  size() {
    return this.cache.size
  }
}

// Create singleton instances for different formatting types
const dateTimeFormatCache = new FormatMemoizer(500)
const durationFormatCache = new FormatMemoizer(300)
const distanceFormatCache = new FormatMemoizer(200)

/**
 * Memoized date/time formatter
 * @param {string} timestamp - ISO timestamp
 * @param {string} format - Date format string
 * @param {Function} formatFn - Formatting function (e.g., timezone.format)
 * @returns {string} Formatted date/time string
 */
export function memoizedDateTimeFormat(timestamp, format, formatFn) {
  if (!timestamp) return 'N/A'

  const key = `${timestamp}:${format}`
  return dateTimeFormatCache.get(key, () => formatFn(timestamp, format))
}

/**
 * Memoized duration formatter
 * @param {number} seconds - Duration in seconds
 * @param {Function} formatFn - Formatting function
 * @returns {string} Formatted duration string
 */
export function memoizedDurationFormat(seconds, formatFn) {
  if (seconds == null) return 'N/A'

  const key = `${seconds}`
  return durationFormatCache.get(key, () => formatFn(seconds))
}

/**
 * Memoized distance formatter
 * @param {number} meters - Distance in meters
 * @param {Function} formatFn - Formatting function
 * @returns {string} Formatted distance string
 */
export function memoizedDistanceFormat(meters, formatFn) {
  if (meters == null) return 'N/A'

  const key = `${meters}`
  return distanceFormatCache.get(key, () => formatFn(meters))
}

/**
 * Memoized end time calculator and formatter
 * @param {string} startTime - Start timestamp
 * @param {number} durationSeconds - Duration in seconds
 * @param {string} format - Date format string
 * @param {Function} calculateFn - Function to calculate and format end time
 * @returns {string} Formatted end time string
 */
export function memoizedEndTimeFormat(startTime, durationSeconds, format, calculateFn) {
  if (!startTime || durationSeconds == null) return 'N/A'

  const key = `${startTime}:${durationSeconds}:${format}`
  return dateTimeFormatCache.get(key, () => calculateFn(startTime, durationSeconds, format))
}

/**
 * Clear all format caches (useful when timezone or locale changes)
 */
export function clearAllFormatCaches() {
  dateTimeFormatCache.clear()
  durationFormatCache.clear()
  distanceFormatCache.clear()
}

/**
 * Get cache statistics (useful for debugging)
 */
export function getFormatCacheStats() {
  return {
    dateTime: dateTimeFormatCache.size(),
    duration: durationFormatCache.size(),
    distance: distanceFormatCache.size(),
    total: dateTimeFormatCache.size() + durationFormatCache.size() + distanceFormatCache.size()
  }
}
