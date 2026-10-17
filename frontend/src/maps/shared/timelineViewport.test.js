import { describe, expect, it } from 'vitest'
import { collectTimelineDataBounds, getTimelineBoundsViewport, normalizeTimelineBoundsPoint } from './timelineViewport'

describe('timeline viewport', () => {
  it('normalizes pairs and objects, rejecting out-of-range coordinates', () => {
    expect(normalizeTimelineBoundsPoint(['50.1', 30])).toEqual([50.1, 30])
    expect(normalizeTimelineBoundsPoint({ lat: 1, lng: 2 })).toEqual([1, 2])
    expect(normalizeTimelineBoundsPoint({ latitude: 1, lon: 2 })).toEqual([1, 2])
    expect(normalizeTimelineBoundsPoint([91, 0])).toBeNull()
    expect(normalizeTimelineBoundsPoint({ latitude: 'x', longitude: 0 })).toBeNull()
  })

  it('collects every kind of map data and skips invalid points', () => {
    const bounds = collectTimelineDataBounds({
      pathData: [[[1, 1], [2, 2]], 'not-a-path'],
      timelineData: [{ latitude: 3, longitude: 3 }, { latitude: null, longitude: 3 }],
      favoritesData: [{ type: 'point', lat: 4, lon: 4 }, { type: 'area', coordinates: [[5, 5], [6, 6]] }],
      plannedItemsData: [{ latitude: 7, longitude: 7 }]
    })

    expect(bounds).toEqual([[1, 1], [2, 2], [3, 3], [4, 4], [5, 5], [6, 6], [7, 7]])
    expect(collectTimelineDataBounds({})).toBeNull()
  })

  it('collapses a single location or points a few meters apart', () => {
    expect(getTimelineBoundsViewport([[50, 30]])).toMatchObject({ center: [50, 30], isCollapsedBounds: true })
    expect(getTimelineBoundsViewport([[50, 30], [50.0001, 30.0001]]).isCollapsedBounds).toBe(true)

    const spread = getTimelineBoundsViewport([[50, 30], [51, 31]])
    expect(spread.isCollapsedBounds).toBe(false)
    expect(spread.center).toEqual([50.5, 30.5])
    expect(getTimelineBoundsViewport([])).toBeNull()
  })
})
