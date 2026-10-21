import { describe, expect, it } from 'vitest'
import { createTimelineStackMarkerElement } from './timelineMarkerBuilder'

describe('createTimelineStackMarkerElement', () => {
  // MapLibre's Marker rewrites style.opacity on its root element from its own `opacity` option on every update,
  // so a dimmed zoom cluster has to hand its opacity to the Marker rather than only set it on the element.
  it('returns the dimmed opacity for the Marker option', () => {
    expect(createTimelineStackMarkerElement({ count: 2, dimmed: true }).markerOpacity).toBe('0.28')
    expect(createTimelineStackMarkerElement({ count: 2 }).markerOpacity).toBe('1')
  })

  it('keeps the grey filter on a dimmed cluster', () => {
    expect(createTimelineStackMarkerElement({ count: 2, dimmed: true }).element.style.filter).toContain('grayscale')
  })
})
