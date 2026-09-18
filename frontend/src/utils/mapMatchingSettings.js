/**
 * Settings that become part of the map-matching cache key on the backend
 * (`MapMatchingConfiguration.configHashSource()` plus the movement profile).
 *
 * Changing one of them means stored matches are never reused afterwards, so past trips need a re-run to
 * pick the new configuration up. Saving them does not re-match history by itself - the admin is prompted.
 */
export const MAP_MATCHING_CACHE_AFFECTING_KEYS = new Set([
  'map-matching.provider',
  'map-matching.valhalla.base-url',
  'map-matching.max-input-points',
  'map-matching.max-trip-duration-hours',
  'map-matching.quality.min-raw-distance-meters',
  'map-matching.quality.min-distance-coverage-percent',
  'map-matching.quality.max-discontinuity-percent',
  'map-matching.quality.max-short-discontinuity-meters'
])

/**
 * @param {string[]} keys setting keys that were changed or reset
 * @returns {boolean} true when any of them invalidates stored map-matching results
 */
export const affectsMapMatchingCache = (keys) => (
  (Array.isArray(keys) ? keys : []).some(key => MAP_MATCHING_CACHE_AFFECTING_KEYS.has(key))
)
