import { describe, expect, it } from 'vitest'
import {
  getWeatherCodeInfo,
  getWeatherSamplesForTimelineItem,
  isWeatherSampleInTimelineItem,
  summarizeWeatherSamples
} from './weatherDisplay'

const stay = {
  type: 'stay',
  timestamp: '2026-06-25T12:10:00Z',
  stayDuration: 30 * 60,
  latitude: 49.55,
  longitude: 25.6
}

const sample = (observedAt, overrides = {}) => ({
  observedAt,
  latitude: 49.55,
  longitude: 25.6,
  temperature: 28,
  weatherCode: 0,
  ...overrides
})

describe('weatherDisplay timeline matching', () => {
  it('uses exact samples inside the timeline item window', () => {
    const exact = sample('2026-06-25T12:20:00Z')

    expect(getWeatherSamplesForTimelineItem(stay, [exact])).toEqual([exact])
  })

  it('uses the nearest hourly sample when no exact sample exists', () => {
    const hourlyBeforeStart = sample('2026-06-25T12:00:00Z')

    expect(getWeatherSamplesForTimelineItem(stay, [hourlyBeforeStart])).toEqual([hourlyBeforeStart])
  })

  it('prefers exact samples over nearby fallback samples', () => {
    const hourlyBeforeStart = sample('2026-06-25T12:00:00Z')
    const exact = sample('2026-06-25T12:20:00Z')

    expect(getWeatherSamplesForTimelineItem(stay, [hourlyBeforeStart, exact])).toEqual([exact])
  })

  it('ignores nearby-hour samples outside the one hour tolerance', () => {
    const tooFar = sample('2026-06-25T10:59:00Z')

    expect(getWeatherSamplesForTimelineItem(stay, [tooFar])).toEqual([])
  })

  it('uses the same tolerance for selected item highlighting', () => {
    expect(isWeatherSampleInTimelineItem(sample('2026-06-25T12:00:00Z'), stay)).toBe(true)
    expect(isWeatherSampleInTimelineItem(sample('2026-06-25T10:59:00Z'), stay)).toBe(false)
  })
})

describe('weatherDisplay condition icons', () => {
  it.each([
    [0, 'Clear', 'fas fa-sun'],
    [2, 'Partly cloudy', 'fas fa-cloud-sun'],
    [3, 'Cloudy', 'fas fa-cloud'],
    [45, 'Fog', 'fas fa-smog'],
    [51, 'Drizzle', 'fas fa-cloud-rain'],
    [61, 'Rain', 'fas fa-cloud-showers-heavy'],
    [71, 'Snow', 'fas fa-snowflake'],
    [80, 'Rain showers', 'fas fa-cloud-showers-heavy'],
    [85, 'Snow showers', 'fas fa-snowflake'],
    [95, 'Storm', 'fas fa-cloud-bolt']
  ])('maps weather code %s to %s with an available icon', (code, label, icon) => {
    expect(getWeatherCodeInfo(code)).toMatchObject({ label, icon })
  })

  it('uses the mapped condition icon in summaries', () => {
    const summary = summarizeWeatherSamples([sample('2026-06-25T12:00:00Z', { weatherCode: 2 })])

    expect(summary).toMatchObject({
      condition: 'Partly cloudy',
      icon: 'fas fa-cloud-sun'
    })
  })
})
