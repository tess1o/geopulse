import { describe, it, expect } from 'vitest'
import { pointsInViewport, selectedReconstructionRange, missingActivityGaps, missingActivityBoundaryPlaces } from './timelineReconstructionContext'

const bounds = (west, east) => ({
  getSouthWest: () => ({ lat: 10, lng: west }),
  getNorthEast: () => ({ lat: 20, lng: east })
})
describe('Timeline reconstruction context', () => {
  it('uses raw points within the current view, including boundaries', () => {
    const inside = { latitude: 10, longitude: 30 }
    expect(pointsInViewport([inside, { latitude: 21, longitude: 30 }, { latitude: 12, longitude: 50 }], bounds(30, 40))).toEqual([inside])
  })
  it('handles views crossing the date line and world copies', () => {
    const points = [-175, 175, 0].map(longitude => ({ latitude: 15, longitude }))
    expect(pointsInViewport(points, bounds(170, -170))).toEqual(points.slice(0, 2))
    expect(pointsInViewport(points, bounds(530, 550))).toEqual(points.slice(0, 2))
  })
  it('orders selected timestamps and preserves their exact instants', () => {
    expect(selectedReconstructionRange({ timestamp: '2026-11-03T16:27:00+02:00' }, { timestamp: '2026-11-03T14:24:00Z' }))
      .toEqual({ startTime: '2026-11-03T14:24:00.000Z', endTime: '2026-11-03T14:27:00.000Z' })
  })
  it('requires two valid, distinct timestamps', () => {
    const point = { timestamp: '2026-11-03T14:24:00Z' }
    expect(selectedReconstructionRange(point, point)).toBeNull()
    expect(selectedReconstructionRange(point, null)).toBeNull()
    expect(selectedReconstructionRange(point, { timestamp: 'bad' })).toBeNull()
  })
})


it('finds only completed gaps on the selected day and clips overnight bounds', () => {
  const gaps = missingActivityGaps([
    { id: 1, type: 'dataGap', startTime: '2026-10-15T23:00:00Z', endTime: '2026-10-16T03:00:00Z' },
    { id: 2, type: 'dataGap', ongoing: true, startTime: '2026-10-16T04:00:00Z', endTime: '2026-11-01T00:00:00Z' }
  ], '2026-10-16T00:00:00Z', '2026-10-17T00:00:00Z')
  expect(gaps).toHaveLength(1)
  expect(gaps[0].startTime).toBe('2026-10-16T00:00:00.000Z')
})
it('finds the recorded boundary points around a gap without inventing coordinates', () => {
  const point = { latitude: 50, longitude: 30, timestamp: '2026-10-16T10:00:00Z' }
  const next = { latitude: 51, longitude: 31, timestamp: '2026-10-16T11:00:00Z' }
  expect(missingActivityBoundaryPlaces({ startTime: point.timestamp, endTime: next.timestamp }, [], [point, next]))
    .toEqual({ before: point, after: next })
})
