import { describe, expect, it } from 'vitest'
import {
  buildAntimeridianReplayBounds,
  buildTripReplayTimeline,
  interpolateReplayLongitude,
  resolveTripReplayCursor,
  unwrapReplayLongitudes
} from './tripReplayMath'

describe('trip replay interpolation', () => {
  it('crosses the antimeridian by the short route', () => {
    const timeline = buildTripReplayTimeline([
      { latitude: 10, longitude: 179, timestamp: '2026-01-01T00:00:00Z' },
      { latitude: 10, longitude: -179, timestamp: '2026-01-01T00:01:00Z' }
    ])

    const cursor = resolveTripReplayCursor(timeline, timeline.durationMs / 2)

    expect(Math.abs(cursor.longitude)).toBe(180)
    expect(buildAntimeridianReplayBounds([[179, 10], [-179, 12]])).toEqual([[179, 10], [181, 12]])
  })

  it('interpolates 179° → -179° through ±180°, not through 0°', () => {
    expect(interpolateReplayLongitude(179, -179, 0.25)).toBeCloseTo(179.5)
    expect(interpolateReplayLongitude(179, -179, 0.75)).toBeCloseTo(-179.5)
    expect(interpolateReplayLongitude(-179, 179, 0.25)).toBeCloseTo(-179.5)
    expect(interpolateReplayLongitude(10, 20, 0.5)).toBe(15)
  })

  it('unwraps route lines so they do not wrap around the globe', () => {
    expect(unwrapReplayLongitudes([[178, 1], [-179, 2], [-177, 3], [179, 4]]))
      .toEqual([[178, 1], [181, 2], [183, 3], [179, 4]])
    expect(unwrapReplayLongitudes([[30, 50], ['x', 1], [24, 49]])).toEqual([[30, 50], [24, 49]])
  })
})
