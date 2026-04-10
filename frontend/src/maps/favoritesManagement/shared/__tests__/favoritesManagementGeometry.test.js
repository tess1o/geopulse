import { describe, expect, it } from 'vitest'
import {
  buildBoundsPoints,
  getAreaCenterLatLng,
  getAreaPolygonLngLat,
  toLeafletBounds
} from '@/maps/favoritesManagement/shared/favoritesManagementGeometry'

describe('favoritesManagementGeometry', () => {
  it('builds valid bounds points and skips invalid coordinates', () => {
    const points = buildBoundsPoints({
      favorites: [
        { type: 'POINT', latitude: 49.5, longitude: 25.6 },
        { type: 'POINT', latitude: 'x', longitude: 30.1 },
        { type: 'AREA', southWestLat: 49.0, southWestLon: 25.0, northEastLat: 50.0, northEastLon: 26.0 }
      ],
      pendingPoints: [{ lat: 48.9, lon: 24.9 }],
      pendingAreas: [{ southWestLat: 48.0, southWestLon: 24.0, northEastLat: 48.5, northEastLon: 24.5 }]
    })

    expect(points).toEqual([
      [49.5, 25.6],
      [49.0, 25.0],
      [50.0, 26.0],
      [48.9, 24.9],
      [48.0, 24.0],
      [48.5, 24.5]
    ])
  })

  it('returns null bounds for invalid area', () => {
    expect(toLeafletBounds({
      southWestLat: 49.0,
      southWestLon: null,
      northEastLat: 50.0,
      northEastLon: 26.0
    })).toBeNull()
  })

  it('builds center and polygon in correct coordinate order', () => {
    const area = {
      southWestLat: 49,
      southWestLon: 25,
      northEastLat: 50,
      northEastLon: 26
    }

    expect(getAreaCenterLatLng(area)).toEqual({ lat: 49.5, lng: 25.5 })
    expect(getAreaPolygonLngLat(area)).toEqual([
      [25, 49],
      [26, 49],
      [26, 50],
      [25, 50],
      [25, 49]
    ])
  })
})

