import { describe, expect, it } from 'vitest'
import {
  areVisibleMapMatchingTripsSettled,
  buildActiveMapMatchingPathData,
  buildRawMapMatchingComparisonPathData,
  chunkMapMatchingIds,
  getMapMatchingPollDelay,
  getPendingMapMatchingTargets,
  mergeMapMatchingTripResolutions,
  timestampMatchedTripSegments
} from './mapMatchingTimeline'

describe('timeline map matching helpers', () => {
  it('batches visible IDs without truncating them', () => {
    const ids = Array.from({ length: 205 }, (_, index) => index + 1)
    const batches = chunkMapMatchingIds(ids)

    expect(batches.map(batch => batch.length)).toEqual([100, 100, 5])
    expect(batches.flat()).toEqual(ids)
  })

  it('merges status transitions without removing completed geometry', () => {
    const existing = [{ tripId: 1, targetId: 10, status: 'QUEUED' }]
    const completed = [{ tripId: 1, targetId: 10, status: 'COMPLETED', segments: [[{ latitude: 1 }]] }]

    expect(mergeMapMatchingTripResolutions(existing, completed)).toEqual(completed)
  })

  it('polls only non-terminal targets and honors server-directed backoff', () => {
    const pending = getPendingMapMatchingTargets([
      { targetId: 1, status: 'QUEUED', pollAfterMs: 9000 },
      { targetId: 2, status: 'COMPLETED' },
      { targetId: 3, status: 'SKIPPED' }
    ])

    expect(pending.map(trip => trip.targetId)).toEqual([1])
    expect(getMapMatchingPollDelay(pending, 0)).toBe(9000)
    expect(getMapMatchingPollDelay([], 10)).toBe(15000)
  })

  it('uses the earliest server delay so a sleeping retry does not hold up active work', () => {
    const pending = [
      { targetId: 1, status: 'QUEUED', pollAfterMs: 60000 },
      { targetId: 2, status: 'PROCESSING', pollAfterMs: 2500 }
    ]

    expect(getMapMatchingPollDelay(pending, 10)).toBe(2500)
  })

  it('waits for every visible trip before allowing the page-wide route swap', () => {
    expect(areVisibleMapMatchingTripsSettled([1, 2], [
      { tripId: 1, status: 'COMPLETED' },
      { tripId: 2, status: 'PROCESSING' }
    ])).toBe(false)

    expect(areVisibleMapMatchingTripsSettled([1, 2], [
      { tripId: 1, status: 'COMPLETED' }
    ])).toBe(false)

    expect(areVisibleMapMatchingTripsSettled([1, 2, 3, 4], [
      { tripId: 1, status: 'COMPLETED' },
      { tripId: 2, status: 'FAILED' },
      { tripId: 3, status: 'SKIPPED' },
      { tripId: 4, status: 'UNAVAILABLE' }
    ])).toBe(true)
  })

  it('assigns one monotonic timeline across disconnected matched fragments', () => {
    const timestamped = timestampMatchedTripSegments([
      [{ latitude: 1 }, { latitude: 2 }],
      [{ latitude: 3 }, { latitude: 4 }]
    ], '2026-08-16T14:16:04Z', 60)

    const timestamps = timestamped.flat().map(point => Date.parse(point.timestamp))
    expect(timestamps).toEqual([...timestamps].sort((left, right) => left - right))
    expect(timestamps[2]).toBeGreaterThan(timestamps[1])
    expect(timestamps.at(-1) - timestamps[0]).toBe(60_000)
  })

  it('keeps the exact raw source until every visible trip settles', () => {
    const raw = { segments: [[{ timestamp: '2026-01-01T10:00:00Z' }]] }
    const result = buildActiveMapMatchingPathData({
      rawPathData: raw,
      visibleTrips: [{ id: 1, timestamp: '2026-01-01T10:00:00Z', tripDuration: 60 }],
      matchedSegmentsByTripId: new Map([[1, [[{ latitude: 1 }, { latitude: 2 }]]]]),
      settled: false
    })

    expect(result).toBe(raw)
  })

  it('replaces completed trip windows while retaining terminal raw fallbacks', () => {
    const raw = {
      userId: 'user',
      segments: [[
        { id: 'before', timestamp: '2026-01-01T09:59:00Z' },
        { id: 'matched-raw-1', timestamp: '2026-01-01T10:00:00Z' },
        { id: 'matched-raw-2', timestamp: '2026-01-01T10:01:00Z' },
        { id: 'fallback-raw', timestamp: '2026-01-01T10:02:00Z' }
      ]]
    }
    const result = buildActiveMapMatchingPathData({
      rawPathData: raw,
      visibleTrips: [
        { id: 1, timestamp: '2026-01-01T10:00:00Z', tripDuration: 60 },
        { id: 2, timestamp: '2026-01-01T10:02:00Z', tripDuration: 60 }
      ],
      matchedSegmentsByTripId: new Map([[
        1,
        [[{ id: 'matched-1', latitude: 1 }, { id: 'matched-2', latitude: 2 }]]
      ]]),
      settled: true
    })

    expect(result.points.map(point => point.id)).toEqual([
      'before',
      'matched-1',
      'matched-2',
      'fallback-raw'
    ])
    expect(result.pointCount).toBe(4)
  })

  it('settles mixed completed and failed trips as matched geometry plus normal raw fallback', () => {
    expect(areVisibleMapMatchingTripsSettled([1, 2], [
      { tripId: 1, status: 'COMPLETED', segments: [[{ id: 'matched-1' }]] },
      {
        tripId: 2,
        status: 'FAILED',
        error: 'Valhalla trace_route failed with HTTP 400: {"error_code":443}'
      }
    ])).toBe(true)

    const raw = {
      segments: [[
        { id: 'raw-completed', timestamp: '2026-01-01T10:00:00Z' },
        { id: 'raw-failed', timestamp: '2026-01-01T10:03:00Z' }
      ]]
    }
    const result = buildActiveMapMatchingPathData({
      rawPathData: raw,
      visibleTrips: [
        { id: 1, timestamp: '2026-01-01T10:00:00Z', tripDuration: 60 },
        { id: 2, timestamp: '2026-01-01T10:03:00Z', tripDuration: 60 }
      ],
      matchedSegmentsByTripId: new Map([[
        1,
        [[{ id: 'matched-completed', latitude: 49.84, longitude: 24.03 }]]
      ]]),
      settled: true
    })

    expect(result.points.map(point => point.id)).toEqual(['matched-completed', 'raw-failed'])
    expect(result.points.find(point => point.id === 'raw-failed')).toEqual(raw.segments[0][1])
  })

  it('keeps raw data when all terminal trips use fallback', () => {
    const raw = { points: [{ timestamp: '2026-01-01T10:00:00Z' }] }
    expect(buildActiveMapMatchingPathData({
      rawPathData: raw,
      visibleTrips: [{ id: 1 }],
      matchedSegmentsByTripId: new Map(),
      settled: true
    })).toBe(raw)
  })

  it('builds raw comparison paths for all matched trips when no trip is highlighted', () => {
    const rawPathData = {
      points: [
        point('trip-1-a', '2026-01-01T10:00:00Z'),
        point('trip-1-b', '2026-01-01T10:01:00Z'),
        point('trip-2-a', '2026-01-01T11:00:00Z'),
        point('trip-2-b', '2026-01-01T11:01:00Z'),
        point('unmatched-a', '2026-01-01T12:00:00Z'),
        point('unmatched-b', '2026-01-01T12:01:00Z')
      ]
    }

    const result = buildRawMapMatchingComparisonPathData({
      rawPathData,
      visibleTrips: [
        trip(1, '2026-01-01T10:00:00Z'),
        trip(2, '2026-01-01T11:00:00Z'),
        trip(3, '2026-01-01T12:00:00Z')
      ],
      highlightedTrip: null,
      highlightedTripHasMatchedPath: false,
      matchedTripIds: [1, 2]
    })

    expect(result.map(segment => segment.map(pathPoint => pathPoint.id))).toEqual([
      ['trip-1-a', 'trip-1-b'],
      ['trip-2-a', 'trip-2-b']
    ])
  })

  it('builds raw comparison path only for the highlighted matched trip', () => {
    const rawPathData = {
      points: [
        point('trip-1-a', '2026-01-01T10:00:00Z'),
        point('trip-1-b', '2026-01-01T10:01:00Z'),
        point('trip-2-a', '2026-01-01T11:00:00Z'),
        point('trip-2-b', '2026-01-01T11:01:00Z')
      ]
    }

    const result = buildRawMapMatchingComparisonPathData({
      rawPathData,
      visibleTrips: [
        trip(1, '2026-01-01T10:00:00Z'),
        trip(2, '2026-01-01T11:00:00Z')
      ],
      highlightedTrip: trip(2, '2026-01-01T11:00:00Z'),
      highlightedTripHasMatchedPath: true,
      matchedTripIds: [1, 2]
    })

    expect(result.map(segment => segment.map(pathPoint => pathPoint.id))).toEqual([
      ['trip-2-a', 'trip-2-b']
    ])
  })
})

const point = (id, timestamp) => ({
  id,
  timestamp,
  latitude: 50.1,
  longitude: 30.1
})

const trip = (id, timestamp) => ({
  id,
  type: 'trip',
  timestamp,
  tripDuration: 120
})
