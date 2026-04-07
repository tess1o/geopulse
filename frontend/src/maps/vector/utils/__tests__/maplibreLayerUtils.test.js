import { describe, expect, it } from 'vitest'
import {
  normalizeLeafletBoundsToMapLibre,
  toLatLngTuple,
  toLngLatTuple
} from '@/maps/vector/utils/maplibreLayerUtils'

describe('maplibreLayerUtils', () => {
  it('converts leaflet-style lat/lng tuple to maplibre lng/lat tuple', () => {
    expect(toLngLatTuple([50.45, 30.52])).toEqual([30.52, 50.45])
    expect(toLatLngTuple([30.52, 50.45])).toEqual([50.45, 30.52])
  })

  it('converts leaflet bounds into maplibre bounds', () => {
    const leafletBounds = [
      [49.0, 20.0],
      [51.0, 30.0]
    ]

    expect(normalizeLeafletBoundsToMapLibre(leafletBounds)).toEqual([
      [20.0, 49.0],
      [30.0, 51.0]
    ])
  })

  it('converts array of leaflet points into maplibre bounding box', () => {
    const points = [
      [49.1, 25.2],
      [49.4, 25.9],
      [49.0, 25.5]
    ]

    expect(normalizeLeafletBoundsToMapLibre(points)).toEqual([
      [25.2, 49.0],
      [25.9, 49.4]
    ])
  })

  it('supports [south, west, north, east] bounds array', () => {
    expect(normalizeLeafletBoundsToMapLibre([49.0, 20.0, 51.0, 30.0])).toEqual([
      [20.0, 49.0],
      [30.0, 51.0]
    ])
  })

  it('supports object bounds shape', () => {
    const bounds = {
      south: 10,
      west: 20,
      north: 15,
      east: 30
    }

    expect(normalizeLeafletBoundsToMapLibre(bounds)).toEqual([
      [20, 10],
      [30, 15]
    ])
  })
})
