import { describe, expect, it } from 'vitest'
import {
  buildStayWeatherDisplay,
  buildWeatherPopupSection,
  createStayWeatherBadgeElement,
  getTimelineItemWeatherDisplay,
  partitionWeatherSamplesByStay
} from './stayWeather'

const stay = {
  type: 'stay',
  timestamp: '2026-03-12T08:00:00Z',
  stayDuration: 6 * 3600,
  latitude: 50.45,
  longitude: 30.52
}
const trip = {
  type: 'trip',
  timestamp: '2026-03-12T14:00:00Z',
  tripDuration: 3600,
  latitude: 50.45,
  longitude: 30.52
}

const sample = (observedAt, overrides = {}) => ({
  observedAt,
  latitude: 50.45,
  longitude: 30.52,
  weatherCode: 61,
  temperature: 10,
  precipitation: 1.2,
  windSpeed: 12,
  ...overrides
})

describe('partitionWeatherSamplesByStay', () => {
  it('claims a stay\'s samples and leaves the rest for the weather layer', () => {
    const stayMorning = sample('2026-03-12T09:00:00Z')
    const stayNoon = sample('2026-03-12T12:00:00Z', { temperature: 14 })
    const tripSample = sample('2026-03-12T19:00:00Z', { latitude: 50.6, longitude: 30.9 })

    const { weatherByTimelineIndex, remainingSamples } = partitionWeatherSamplesByStay(
      [stay, trip],
      [stayMorning, stayNoon, tripSample]
    )

    expect(weatherByTimelineIndex.get(0)).toEqual([stayMorning, stayNoon])
    expect(weatherByTimelineIndex.has(1)).toBe(false)
    expect(remainingSamples).toEqual([tripSample])
  })

  it('returns nothing to claim when there are no samples', () => {
    const { weatherByTimelineIndex, remainingSamples } = partitionWeatherSamplesByStay([stay], [])

    expect(weatherByTimelineIndex.size).toBe(0)
    expect(remainingSamples).toEqual([])
  })
})

describe('stay weather display', () => {
  const samples = [
    sample('2026-03-12T09:00:00Z', { temperature: 9 }),
    sample('2026-03-12T12:00:00Z', { temperature: 14 })
  ]

  it('summarizes condition, temperature and range like the sidebar', () => {
    const display = buildStayWeatherDisplay(samples, { temperatureUnit: 'CELSIUS', distanceUnit: 'KILOMETERS' })

    expect(display.severity).toBe('rain')
    expect(display.temperatureText).toBe('12°C')
    expect(display.rangeText).toBe('9°C-14°C')
  })

  it('looks weather up by the layer item\'s timeline index', () => {
    const byIndex = new Map([[3, samples]])

    expect(getTimelineItemWeatherDisplay(byIndex, { __timelineIndex: 3 }, {})).not.toBeNull()
    expect(getTimelineItemWeatherDisplay(byIndex, { __timelineIndex: 4 }, {})).toBeNull()
    expect(getTimelineItemWeatherDisplay(null, { __timelineIndex: 3 }, {})).toBeNull()
  })

  it('builds a popup section and an accessible badge', () => {
    const display = buildStayWeatherDisplay(samples, {})
    const section = buildWeatherPopupSection(display)
    const badge = createStayWeatherBadgeElement(display)

    expect(section.rows.length).toBeGreaterThanOrEqual(3)
    expect(badge.className).toContain('gp-stay-weather-badge--rain')
    expect(badge.getAttribute('aria-label')).toContain('12°C')
  })
})
