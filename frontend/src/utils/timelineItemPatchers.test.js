import { describe, expect, it } from 'vitest'
import {
  applyStayFavoriteUpdateToTimelineItems,
  applyStayGeocodingUpdateToTimelineItems
} from './timelineItemPatchers'

describe('timelineItemPatchers', () => {
  it('updates all matching favorite stays and preserves unrelated items', () => {
    const unrelatedTrip = { type: 'trip', id: 3, movementType: 'WALK' }
    const items = [
      { type: 'stay', id: 1, favoriteId: 10, locationName: 'Old', city: 'A', country: 'B' },
      { type: 'stay', id: 2, favoriteId: 11, locationName: 'Other', city: 'C', country: 'D' },
      unrelatedTrip,
      { type: 'stay', id: 4, favoriteId: '10', locationName: 'Old again', city: 'E', country: 'F' }
    ]

    const result = applyStayFavoriteUpdateToTimelineItems(items, {
      id: 10,
      name: 'New name',
      city: 'Kyiv',
      country: 'Ukraine'
    })

    expect(result[0]).toMatchObject({ locationName: 'New name', city: 'Kyiv', country: 'Ukraine' })
    expect(result[1]).toBe(items[1])
    expect(result[2]).toBe(unrelatedTrip)
    expect(result[3]).toMatchObject({ locationName: 'New name', city: 'Kyiv', country: 'Ukraine' })
  })

  it('updates matching geocoding stays and preserves unrelated items', () => {
    const unrelatedStay = { type: 'stay', id: 2, geocodingId: 44, locationName: 'Other' }
    const items = [
      { type: 'stay', id: 1, geocodingId: 33, locationName: 'Old', city: 'A', country: 'B' },
      unrelatedStay,
      { type: 'dataGap', id: 3, geocodingId: 33, locationName: 'Gap' }
    ]

    const result = applyStayGeocodingUpdateToTimelineItems(items, 33, {
      id: 55,
      displayName: 'Resolved place',
      city: 'Lviv',
      country: 'Ukraine'
    })

    expect(result[0]).toMatchObject({
      geocodingId: 55,
      locationName: 'Resolved place',
      city: 'Lviv',
      country: 'Ukraine'
    })
    expect(result[1]).toBe(unrelatedStay)
    expect(result[2]).toBe(items[2])
  })
})
