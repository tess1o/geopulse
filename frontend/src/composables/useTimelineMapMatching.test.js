import { beforeEach, describe, expect, it, vi } from 'vitest'
import { nextTick, ref } from 'vue'
import { useTimelineMapMatching } from './useTimelineMapMatching'

describe('useTimelineMapMatching', () => {
  beforeEach(() => {
    vi.useRealTimers()
  })

  it('keeps the raw path active while a visible trip is pending', async () => {
    const rawPathData = ref({
      points: [
        point('raw-a', '2026-01-01T10:00:00Z'),
        point('raw-b', '2026-01-01T10:01:00Z')
      ]
    })
    const service = {
      resolve: vi.fn().mockResolvedValue({
        enabled: true,
        provider: 'valhalla',
        trips: [{ tripId: 1, targetId: 10, status: 'QUEUED', pollAfterMs: 2500 }]
      }),
      status: vi.fn()
    }

    const mapMatching = useTimelineMapMatching({
      enabled: ref(true),
      visibleTrips: ref([trip(1)]),
      rawPathData,
      store: service
    })

    await mapMatching.resolve()
    await nextTick()

    expect(service.resolve).toHaveBeenCalledWith([1])
    expect(mapMatching.statusText.value).toBe('Refining route...')
    expect(mapMatching.activePathData.value).toBe(rawPathData.value)
    expect(mapMatching.matchedTripIds.value).toEqual([])

    mapMatching.stop()
  })

  it('swaps to matched geometry after every visible trip reaches a terminal state', async () => {
    const rawPathData = ref({
      points: [
        point('raw-matched', '2026-01-01T10:00:00Z'),
        point('raw-failed-a', '2026-01-01T10:03:00Z'),
        point('raw-failed-b', '2026-01-01T10:04:00Z')
      ]
    })
    const service = {
      resolve: vi.fn().mockResolvedValue({
        enabled: true,
        provider: 'valhalla',
        trips: [
            {
              tripId: 1,
              targetId: 10,
              status: 'COMPLETED',
              segments: [[{ id: 'matched', latitude: 50.1, longitude: 30.1 }]]
            },
            {
              tripId: 2,
              targetId: 11,
              status: 'FAILED',
              error: 'Valhalla trace_route failed with HTTP 400'
            }
        ]
      }),
      status: vi.fn()
    }

    const mapMatching = useTimelineMapMatching({
      enabled: ref(true),
      visibleTrips: ref([trip(1), trip(2, '2026-01-01T10:03:00Z')]),
      rawPathData,
      store: service
    })

    await mapMatching.resolve()
    await nextTick()

    expect(mapMatching.statusText.value).toBe('')
    expect(mapMatching.matchedTripIds.value).toEqual([1])
    expect(mapMatching.activePathData.value.points.map(pathPoint => pathPoint.id)).toEqual([
      'matched',
      'raw-failed-a',
      'raw-failed-b'
    ])
  })

  it('exposes what map matching did with every resolved trip', async () => {
    const rawPathData = ref({ points: [point('raw-a', '2026-01-01T10:00:00Z')] })
    const service = {
      resolve: vi.fn().mockResolvedValue({
        enabled: true,
        provider: 'valhalla',
        trips: [
          {
            tripId: 1,
            targetId: 10,
            status: 'COMPLETED',
            completedAt: '2026-09-16T20:51:32Z',
            segments: [[{ id: 'matched', latitude: 50.1, longitude: 30.1 }]]
          },
          {
            tripId: 2,
            targetId: 11,
            status: 'FAILED',
            completedAt: '2026-09-11T13:45:19Z',
            error: { key: 'mapMatching.error.failed', fallback: 'Valhalla trace_route failed with HTTP 400' }
          },
          { tripId: 3, targetId: null, status: 'SKIPPED', error: 'Trip exceeds configured duration limit' },
          { tripId: 4, targetId: 12, status: 'QUEUED' },
          { tripId: 5, targetId: null, status: 'UNAVAILABLE' }
        ]
      }),
      status: vi.fn()
    }

    const mapMatching = useTimelineMapMatching({
      enabled: ref(true),
      visibleTrips: ref([trip(1), trip(2), trip(3), trip(4), trip(5)]),
      rawPathData,
      store: service
    })

    await mapMatching.resolve()
    await nextTick()

    const states = mapMatching.mapMatchingByTripId.value
    // The matched trip carries when it was refined; UNAVAILABLE is noise and stays out.
    expect(Array.from(states.keys())).toEqual([1, 2, 3, 4])
    expect(states.get(1)).toEqual({
      status: 'COMPLETED',
      targetId: 10,
      error: null,
      completedAt: '2026-09-16T20:51:32Z'
    })
    expect(states.get(2)).toEqual({
      status: 'FAILED',
      targetId: 11,
      error: { key: 'mapMatching.error.failed', fallback: 'Valhalla trace_route failed with HTTP 400' },
      completedAt: '2026-09-11T13:45:19Z'
    })
    // Skipped before queueing: no target row exists yet, the reason is enough to explain it.
    expect(states.get(3)).toEqual({
      status: 'SKIPPED',
      targetId: null,
      error: 'Trip exceeds configured duration limit',
      completedAt: null
    })
    expect(states.get(4)).toEqual({ status: 'QUEUED', targetId: 12, error: null, completedAt: null })

    mapMatching.stop()
  })

  it('polls pending targets and merges completed status responses', async () => {
    vi.useFakeTimers()
    const rawPathData = ref({
      points: [
        point('raw-a', '2026-01-01T10:00:00Z'),
        point('raw-b', '2026-01-01T10:01:00Z')
      ]
    })
    const service = {
      resolve: vi.fn().mockResolvedValue({
        enabled: true,
        provider: 'valhalla',
        trips: [{ tripId: 1, targetId: 10, status: 'QUEUED', pollAfterMs: 2500 }]
      }),
      status: vi.fn().mockResolvedValue([{
          tripId: 1,
          targetId: 10,
          status: 'COMPLETED',
          segments: [[{ id: 'matched', latitude: 50.1, longitude: 30.1 }]]
        }])
    }

    const mapMatching = useTimelineMapMatching({
      enabled: ref(true),
      visibleTrips: ref([trip(1)]),
      rawPathData,
      store: service
    })

    await mapMatching.resolve()
    await vi.advanceTimersByTimeAsync(2500)
    await nextTick()

    expect(service.status).toHaveBeenCalledWith([10])
    expect(mapMatching.matchedTripIds.value).toEqual([1])
    expect(mapMatching.activePathData.value.points.map(pathPoint => pathPoint.id)).toEqual(['matched'])

    mapMatching.stop()
  })
})

const point = (id, timestamp) => ({
  id,
  timestamp,
  latitude: 50.1,
  longitude: 30.1
})

const trip = (id, timestamp = '2026-01-01T10:00:00Z') => ({
  id,
  type: 'trip',
  timestamp,
  tripDuration: 120
})
