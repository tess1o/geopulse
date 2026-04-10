import { describe, expect, it } from 'vitest'
import {
  buildTripHoverContext,
  projectTripHoverContext,
  resolveTripHoverTiming
} from '@/maps/shared/tripHoverMath'

const project = (point) => ({ x: point.longitude * 100, y: point.latitude * 100 })
const unproject = ({ x, y }) => ({ latitude: y / 100, longitude: x / 100 })

describe('tripHoverMath', () => {
  it('resolves exact point timing when cursor is close to a timestamped point', () => {
    const pathPoints = [
      { latitude: 0, longitude: 0, timestamp: '2026-01-01T00:00:00Z' },
      { latitude: 0, longitude: 1, timestamp: '2026-01-01T00:10:00Z' },
      { latitude: 0, longitude: 2, timestamp: '2026-01-01T00:20:00Z' }
    ]

    const context = buildTripHoverContext(pathPoints, {
      distance: (left, right) => Math.abs(right.longitude - left.longitude) * 100
    })

    projectTripHoverContext(context, { project })

    const resolved = resolveTripHoverTiming(context, { x: 100, y: 0 }, {
      toProjectedPoint: (cursorPoint) => cursorPoint,
      unproject
    })

    expect(resolved?.mode).toBe('exact')
    expect(resolved?.timeMs).toBe(Date.parse('2026-01-01T00:10:00Z'))
    expect(resolved?.distanceFromStartMeters).toBe(100)
    expect(resolved?.snappedPoint).toEqual({ latitude: 0, longitude: 1 })
  })

  it('interpolates hover timing between points when cursor is on a segment', () => {
    const pathPoints = [
      { latitude: 0, longitude: 0, timestamp: '2026-01-01T00:00:00Z' },
      { latitude: 0, longitude: 2, timestamp: '2026-01-01T00:20:00Z' }
    ]

    const context = buildTripHoverContext(pathPoints, {
      distance: (left, right) => Math.abs(right.longitude - left.longitude) * 100
    })

    projectTripHoverContext(context, { project })

    const resolved = resolveTripHoverTiming(context, { x: 50, y: 0 }, {
      toProjectedPoint: (cursorPoint) => cursorPoint,
      unproject
    })

    expect(resolved?.mode).toBe('estimated')
    expect(resolved?.timeMs).toBe(Date.parse('2026-01-01T00:05:00Z'))
    expect(resolved?.distanceFromStartMeters).toBe(50)
    expect(resolved?.snappedPoint).toEqual({ latitude: 0, longitude: 0.5 })
  })

  it('falls back to available endpoint timestamp when one segment endpoint is missing time', () => {
    const pathPoints = [
      { latitude: 0, longitude: 0, timestamp: '2026-01-01T00:00:00Z' },
      { latitude: 0, longitude: 1 }
    ]

    const context = buildTripHoverContext(pathPoints, {
      distance: (left, right) => Math.abs(right.longitude - left.longitude) * 100
    })

    projectTripHoverContext(context, { project })

    const resolved = resolveTripHoverTiming(context, { x: 70, y: 0 }, {
      toProjectedPoint: (cursorPoint) => cursorPoint,
      unproject
    })

    expect(resolved?.mode).toBe('estimated')
    expect(resolved?.timeMs).toBe(Date.parse('2026-01-01T00:00:00Z'))
  })

  it('returns null when no timestamps are available', () => {
    const pathPoints = [
      { latitude: 0, longitude: 0 },
      { latitude: 0, longitude: 1 }
    ]

    const context = buildTripHoverContext(pathPoints, {
      distance: (left, right) => Math.abs(right.longitude - left.longitude) * 100
    })

    projectTripHoverContext(context, { project })

    const resolved = resolveTripHoverTiming(context, { x: 40, y: 0 }, {
      toProjectedPoint: (cursorPoint) => cursorPoint,
      unproject
    })

    expect(resolved).toBeNull()
  })
})
