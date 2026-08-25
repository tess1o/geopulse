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
        point('before', '2026-01-01T09:59:00Z'),
        point('matched-raw-1', '2026-01-01T10:00:00Z'),
        point('matched-raw-2', '2026-01-01T10:01:00Z'),
        point('fallback-raw-1', '2026-01-01T10:02:00Z'),
        point('fallback-raw-2', '2026-01-01T10:02:30Z')
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
      'matched-1',
      'matched-2',
      'fallback-raw-1',
      'fallback-raw-2'
    ])
    expect(result.pointCount).toBe(4)
  })

  it('keeps raw stay points as a connector between two matched trips', () => {
    const raw = {
      segments: [[
        { id: 'trip-1-raw-a', timestamp: '2026-07-04T10:09:57Z', latitude: 49.547779, longitude: 25.595296 },
        { id: 'trip-1-raw-b', timestamp: '2026-07-04T10:27:57Z', latitude: 49.552710, longitude: 25.591405 },
        { id: 'favorite-stay-raw', timestamp: '2026-07-04T10:38:27Z', latitude: 49.552573, longitude: 25.589626 },
        { id: 'trip-2-raw-a', timestamp: '2026-07-04T10:54:57Z', latitude: 49.551963, longitude: 25.589147 },
        { id: 'trip-2-raw-b', timestamp: '2026-07-04T11:20:27Z', latitude: 49.547009, longitude: 25.583864 }
      ]]
    }
    const result = buildActiveMapMatchingPathData({
      rawPathData: raw,
      visibleTrips: [
        { id: 7856901, timestamp: '2026-07-04T10:09:57Z', tripDuration: 1080 },
        { id: 7856902, timestamp: '2026-07-04T10:54:57Z', tripDuration: 1530 }
      ],
      matchedSegmentsByTripId: new Map([
        [7856901, [[{ id: 'matched-trip-1-a', latitude: 49.547790, longitude: 25.595317 }, { id: 'matched-trip-1-b', latitude: 49.552702, longitude: 25.591405 }]]],
        [7856902, [[{ id: 'matched-trip-2-a', latitude: 49.551968, longitude: 25.589143 }, { id: 'matched-trip-2-b', latitude: 49.547001, longitude: 25.583854 }]]]
      ]),
      settled: true
    })

    expect(result.points.map(point => point.id)).toEqual([
      'matched-trip-1-a',
      'matched-trip-1-b',
      'matched-trip-1-b',
      'favorite-stay-raw',
      'matched-trip-2-a',
      'matched-trip-2-a',
      'matched-trip-2-b'
    ])
    expect(result.points.some(point => point.id === 'favorite-stay-raw')).toBe(true)
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
        point('raw-completed', '2026-01-01T10:00:00Z'),
        point('raw-failed-1', '2026-01-01T10:03:00Z'),
        point('raw-failed-2', '2026-01-01T10:03:30Z')
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

    expect(result.points.map(point => point.id)).toEqual(['matched-completed', 'raw-failed-1', 'raw-failed-2'])
    expect(result.points.find(point => point.id === 'raw-failed-1')).toMatchObject(raw.segments[0][1])
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
