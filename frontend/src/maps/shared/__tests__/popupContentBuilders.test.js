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
  },
  formatDistance: (meters) => `${(Number(meters || 0) / 1000).toFixed(2)} km`
}))
import {
  buildStayPopupHtml,
  buildTimelineItemPopupHtml,
  buildTripHoverTooltipHtml,
  buildTripPopupHtml
} from '@/maps/shared/popupContentBuilders'

const deps = {
  formatDateTimeDisplay: (value) => `FMT:${value}`
}

describe('popupContentBuilders', () => {
  it('builds escaped stay popup HTML with telemetry and duration', () => {
    const html = buildStayPopupHtml({
      type: 'stay',
      locationName: '<Home & Work>',
      timestamp: '2026-02-03T10:00:00Z',
      stayDuration: 3600,
      telemetryCurrentPopup: [
        { key: 'speed', label: 'Speed', value: 12, unit: 'km/h' }
      ]
    }, deps)

    expect(html).toContain('&lt;Home &amp; Work&gt;')
    expect(html).toContain('FMT:2026-02-03T10:00:00Z')
    expect(html).toContain('Stay duration: 1 hour')
    expect(html).toContain('Telemetry')
    expect(html).toContain('Speed')
    expect(html).toContain('12 km/h')
  })

  it('builds trip popup HTML with formatted timing and metrics', () => {
    const html = buildTripPopupHtml({
      type: 'trip',
      movementType: 'CAR',
      timestamp: '2026-02-03T10:00:00Z',
      tripDuration: 600,
      distanceMeters: 4200
    }, deps)

    expect(html).toContain('CAR Trip')
    expect(html).toContain('Start: FMT:2026-02-03T10:00:00.000Z')
    expect(html).toContain('End: FMT:2026-02-03T10:10:00.000Z')
    expect(html).toContain('Duration: 10 minutes')
    expect(html).toContain('Distance: 4.20 km')
  })

  it('builds trip hover tooltip HTML with exact and estimated labels', () => {
    const exactHtml = buildTripHoverTooltipHtml(
      { timestamp: '2026-02-03T10:00:00Z' },
      { mode: 'exact', timeMs: Date.parse('2026-02-03T10:02:00Z') },
      deps
    )

    const estimatedHtml = buildTripHoverTooltipHtml(
      { timestamp: '2026-02-03T10:00:00Z' },
      { mode: 'estimated', timeMs: Date.parse('2026-02-03T10:03:00Z') },
      deps
    )

    expect(exactHtml).toContain('Exact GPS point')
    expect(exactHtml).toContain('From trip start: 2 minutes')
    expect(estimatedHtml).toContain('Estimated between points')
    expect(estimatedHtml).toContain('From trip start: 3 minutes')
  })

  it('builds timeline popup variants by item type', () => {
    const tripHtml = buildTimelineItemPopupHtml({
      type: 'trip',
      movementType: 'TRAIN',
      timestamp: '2026-02-03T10:00:00Z',
      tripDuration: 900,
      totalDistanceMeters: 3200
    }, deps)

    const gapHtml = buildTimelineItemPopupHtml({
      type: 'dataGap',
      timestamp: '2026-02-03T10:00:00Z'
    }, deps)

    expect(tripHtml).toContain('Trip (TRAIN)')
    expect(tripHtml).toContain('Duration: 15 minutes')
    expect(tripHtml).toContain('Distance: 3.2 km')
    expect(gapHtml).toContain('Data Gap')
  })
})
