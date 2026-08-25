import { beforeEach, describe, expect, it, vi } from 'vitest'
import { nextTick, ref } from 'vue'
import { useTimelineMapMatching } from './useTimelineMapMatching'

vi.mock('@/services/mapMatchingService', () => ({
  default: {}
}))

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
        data: {
          enabled: true,
          provider: 'valhalla',
          trips: [{ tripId: 1, targetId: 10, status: 'QUEUED', pollAfterMs: 2500 }]
        }
      }),
      status: vi.fn()
    }

    const mapMatching = useTimelineMapMatching({
      enabled: ref(true),
      visibleTrips: ref([trip(1)]),
      rawPathData,
      service
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
        data: {
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
        }
      }),
      status: vi.fn()
    }

    const mapMatching = useTimelineMapMatching({
      enabled: ref(true),
      visibleTrips: ref([trip(1), trip(2, '2026-01-01T10:03:00Z')]),
      rawPathData,
      service
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
        data: {
          enabled: true,
          provider: 'valhalla',
          trips: [{ tripId: 1, targetId: 10, status: 'QUEUED', pollAfterMs: 2500 }]
        }
      }),
      status: vi.fn().mockResolvedValue({
        data: [{
          tripId: 1,
          targetId: 10,
          status: 'COMPLETED',
          segments: [[{ id: 'matched', latitude: 50.1, longitude: 30.1 }]]
        }]
      })
    }

    const mapMatching = useTimelineMapMatching({
      enabled: ref(true),
      visibleTrips: ref([trip(1)]),
      rawPathData,
      service
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
