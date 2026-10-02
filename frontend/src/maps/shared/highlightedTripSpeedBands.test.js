import { describe, expect, it } from 'vitest'
import {
  HIGHLIGHTED_TRIP_SPEED_BANDS,
  buildHighlightedTripSegments,
  classifyHighlightedTripSpeedBand
} from './highlightedTripSpeedBands'

describe('highlighted trip speed bands', () => {
  it('classifies speeds into slow, medium and fast bands', () => {
    expect(classifyHighlightedTripSpeedBand(5)).toBe(HIGHLIGHTED_TRIP_SPEED_BANDS.SLOW)
    expect(classifyHighlightedTripSpeedBand(10)).toBe(HIGHLIGHTED_TRIP_SPEED_BANDS.MEDIUM)
    expect(classifyHighlightedTripSpeedBand(25)).toBe(HIGHLIGHTED_TRIP_SPEED_BANDS.MEDIUM)
    expect(classifyHighlightedTripSpeedBand(25.1)).toBe(HIGHLIGHTED_TRIP_SPEED_BANDS.FAST)
    expect(classifyHighlightedTripSpeedBand(null)).toBe(HIGHLIGHTED_TRIP_SPEED_BANDS.UNKNOWN)
  })

  it('leaves coloring to the renderer: segments carry a band, not a color', () => {
    const { segments } = buildHighlightedTripSegments([
      { latitude: 50, longitude: 30, timestamp: '2026-08-16T14:00:00Z' },
      { latitude: 50.01, longitude: 30, timestamp: '2026-08-16T14:01:00Z' }
    ])

    expect(segments).toHaveLength(1)
    expect(segments[0].speedBand).toBe(HIGHLIGHTED_TRIP_SPEED_BANDS.FAST)
    expect(segments[0]).not.toHaveProperty('color')
  })
})
