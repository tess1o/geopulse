import { describe, expect, it } from 'vitest'
import {
  getTimelineGeographicBounds,
  getWeatherQueryRange,
  padWeatherBounds
} from './timelineWeatherQuery'

describe('timelineWeatherQuery', () => {
  it('builds a padded weather range from timeline item durations', () => {
    const range = getWeatherQueryRange('2026-01-01T00:00:00.000Z', '2026-01-02T00:00:00.000Z', [
      {
        type: 'stay',
        timestamp: '2026-01-01T12:00:00.000Z',
        stayDuration: 7200
      }
    ])

    expect(range).toEqual({
      startTime: '2026-01-01T11:00:00.000Z',
      endTime: '2026-01-01T15:00:00.000Z'
    })
  })

  it('falls back to the selected date range when no item range is available', () => {
    const range = getWeatherQueryRange('2026-01-01T00:00:00.000Z', '2026-01-01T23:59:59.000Z', [])

    expect(range).toEqual({
      startTime: '2025-12-31T23:00:00.000Z',
      endTime: '2026-01-02T00:59:59.000Z'
    })
  })

  it('pads geographic bounds and keeps zero coordinates valid', () => {
    const bounds = getTimelineGeographicBounds([
      { latitude: 0, longitude: 0 },
      { latitude: 10, longitude: -20 }
    ])

    expect(padWeatherBounds(bounds)).toEqual({
      minLat: -0.02,
      minLon: -20.02,
      maxLat: 10.02,
      maxLon: 0.02
    })
  })
})
