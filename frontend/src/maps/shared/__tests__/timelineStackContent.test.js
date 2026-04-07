import { describe, expect, it, vi } from 'vitest'

vi.mock('@/utils/calculationsHelpers', () => ({
  formatDuration: (seconds) => {
    const value = Number(seconds) || 0
    if (value % 3600 === 0 && value >= 3600) {
      const hours = value / 3600
      return `${hours} ${hours === 1 ? 'hour' : 'hours'}`
    }
    const minutes = Math.round(value / 60)
    return `${minutes} ${minutes === 1 ? 'minute' : 'minutes'}`
  }
}))
import { buildTimelineStackItems } from '@/maps/shared/timelineStackContent'

describe('timelineStackContent', () => {
  it('normalizes stack rows for stay, trip, and data gap', () => {
    const items = [
      {
        type: 'stay',
        locationName: 'Home',
        timestamp: '2026-02-03T10:00:00Z',
        stayDuration: 3600
      },
      {
        type: 'trip',
        movementType: 'CAR',
        movementTypeSource: 'MANUAL',
        timestamp: '2026-02-03T11:00:00Z',
        tripDuration: 600,
        distanceMeters: 4250
      },
      {
        type: 'dataGap',
        startTime: '2026-02-03T12:00:00Z'
      }
    ]

    const rows = buildTimelineStackItems(items, {
      formatDateDisplay: () => '02/03/2026',
      formatTime: () => '10:00:00'
    })

    expect(rows).toHaveLength(3)

    expect(rows[0]).toMatchObject({
      typeClass: 'stack-item--stay',
      title: '🏠 Stayed at Home',
      meta: 'For 1 hour'
    })

    expect(rows[1]).toMatchObject({
      typeClass: 'stack-item--trip',
      title: '🔄 Transition to new place',
      subtitle: '🚦 Movement: 🚗 Car (Manual)',
      meta: 'Duration: 10 minutes | Distance: 4.3 km'
    })

    expect(rows[2]).toMatchObject({
      typeClass: 'stack-item--datagap',
      title: '⚠️ Data Gap',
      subtitle: '',
      meta: ''
    })
  })
})
