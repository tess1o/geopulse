import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import apiService from '../utils/apiService'
import { useTimezone } from '@/composables/useTimezone'
import { useTimelineStore } from './timeline'

vi.mock('../utils/apiService', () => ({
  default: {
    get: vi.fn()
  }
}))

const zone = (timezone) => ({ timezone, status: 'RESOLVED', nearestCity: 'City', countryCode: 'XX', distanceKm: 1 })
const paris = zone('Europe/Paris')
const la = zone('America/Los_Angeles')

describe('timeline store in location time mode', () => {
  const tz = useTimezone()

  beforeEach(() => {
    setActivePinia(createPinia())
    tz.setTimezone('Europe/Paris')
    tz.setTimeDisplayMode('location')
    apiService.get.mockReset()
  })

  afterEach(() => {
    tz.setTimezone('UTC')
    tz.setTimeDisplayMode('profile')
  })

  it('loads the local day in each place and remembers the window it covers', async () => {
    apiService.get.mockResolvedValue({
      stays: [
        { id: 'before', timestamp: '2026-10-17T02:00:00Z', stayDuration: 8 * 3600, locationTimezone: paris },
        { id: 'paris', timestamp: '2026-10-17T20:00:00Z', stayDuration: 15 * 3600, locationTimezone: paris },
        { id: 'california', timestamp: '2026-10-18T22:30:00Z', stayDuration: 17.5 * 3600, locationTimezone: la },
        { id: 'after', timestamp: '2026-10-19T16:30:00Z', stayDuration: 3600, locationTimezone: la }
      ],
      trips: [
        { id: 'flight', timestamp: '2026-10-18T11:00:00Z', tripDuration: 11.5 * 3600, startLocationTimezone: paris, endLocationTimezone: la }
      ],
      dataGaps: []
    })

    const store = useTimelineStore()
    await store.fetchMovementTimeline('2026-10-17T22:00:00.000Z', '2026-10-18T21:59:59.999Z')

    expect(apiService.get).toHaveBeenCalledWith('/timeline', {
      from: '2026-10-16T20:00:00.000Z',
      to: '2026-10-19T23:59:59.999Z',
      includeLocationTimezones: true
    })
    expect(store.timelineData.map(item => item.id)).toEqual(['paris', 'flight', 'california'])
    expect(store.localDayWindow).toEqual({
      start: '2026-10-17T22:00:00.000Z',
      end: '2026-10-19T06:59:59.999Z'
    })
  })

  it('requests the exact range and keeps every item in profile mode', async () => {
    tz.setTimeDisplayMode('profile')
    apiService.get.mockResolvedValue({
      stays: [{ id: 'california', timestamp: '2026-10-18T22:30:00Z', stayDuration: 3600, locationTimezone: la }],
      trips: [],
      dataGaps: []
    })

    const store = useTimelineStore()
    await store.fetchMovementTimeline('2026-10-17T22:00:00.000Z', '2026-10-18T21:59:59.999Z')

    expect(apiService.get).toHaveBeenCalledWith('/timeline', {
      from: '2026-10-17T22:00:00.000Z',
      to: '2026-10-18T21:59:59.999Z'
    })
    expect(store.timelineData.map(item => item.id)).toEqual(['california'])
    expect(store.localDayWindow).toBeNull()
  })
})
