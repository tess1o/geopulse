import { describe, expect, it } from 'vitest'
import { affectsMapMatchingCache, MAP_MATCHING_CACHE_AFFECTING_KEYS } from './mapMatchingSettings'

describe('affectsMapMatchingCache', () => {
  it('recognises the settings that are part of the cache key', () => {
    expect(affectsMapMatchingCache(['map-matching.valhalla.base-url'])).toBe(true)
    expect(affectsMapMatchingCache(['map-matching.provider'])).toBe(true)
    expect(affectsMapMatchingCache(['map-matching.max-input-points'])).toBe(true)
    expect(affectsMapMatchingCache(['map-matching.max-trip-duration-hours'])).toBe(true)
    expect(affectsMapMatchingCache(['map-matching.quality.min-distance-coverage-percent'])).toBe(true)
  })

  it('detects a cache-affecting key among unrelated ones', () => {
    expect(affectsMapMatchingCache([
      'map-matching.worker.batch-size',
      'map-matching.max-attempts',
      'map-matching.max-input-points'
    ])).toBe(true)
  })

  it('ignores settings that do not invalidate stored matches', () => {
    expect(affectsMapMatchingCache(['map-matching.worker.batch-size'])).toBe(false)
    expect(affectsMapMatchingCache(['map-matching.automatic.quiet-period-minutes'])).toBe(false)
    expect(affectsMapMatchingCache(['map-matching.valhalla.read-timeout-seconds'])).toBe(false)
    expect(affectsMapMatchingCache(['map-matching.enabled'])).toBe(false)
  })

  it('handles empty input', () => {
    expect(affectsMapMatchingCache([])).toBe(false)
    expect(affectsMapMatchingCache(undefined)).toBe(false)
  })

  it('exposes the full key set for the admin UI', () => {
    expect(MAP_MATCHING_CACHE_AFFECTING_KEYS.size).toBe(8)
  })
})
