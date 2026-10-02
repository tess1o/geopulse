import { describe, expect, it } from 'vitest'
import { placeWeatherMarkers } from './weatherMarkerPlacement'

// 1 degree = 1000px on each axis, keeps the math obvious.
const makeMap = () => ({
  project: ([lng, lat]) => ({ x: lng * 1000, y: lat * 1000 })
})

const sampleAt = (longitude, latitude = 0) => ({ longitude, latitude, weatherCode: 0 })

describe('placeWeatherMarkers', () => {
  it('lets nearby samples share one marker', () => {
    const placed = placeWeatherMarkers({
      samples: [sampleAt(0), sampleAt(0.01), sampleAt(1)],
      mapInstance: makeMap()
    })

    expect(placed.map((group) => group.indices)).toEqual([[0, 1], [2]])
  })

  it('hides a weather marker that would overlap another marker', () => {
    const placed = placeWeatherMarkers({
      samples: [sampleAt(0), sampleAt(1)],
      occupied: { circles: [{ x: 10, y: 0, radius: 16 }], rects: [] },
      mapInstance: makeMap()
    })

    expect(placed.map((group) => group.indices)).toEqual([[1]])
  })

  it('hides a weather marker under a combo chip', () => {
    const placed = placeWeatherMarkers({
      samples: [sampleAt(0.05, 0.01)],
      occupied: { circles: [], rects: [{ left: 0, right: 120, top: -14, bottom: 14 }] },
      mapInstance: makeMap()
    })

    expect(placed).toEqual([])
  })

  it('keeps highlighted samples separate and lets them win', () => {
    const placed = placeWeatherMarkers({
      samples: [sampleAt(0), sampleAt(0.02)],
      highlightedIndices: new Set([1]),
      mapInstance: makeMap()
    })

    expect(placed).toHaveLength(1)
    expect(placed[0]).toMatchObject({ indices: [1], highlighted: true })
  })

  it('skips samples without coordinates', () => {
    const placed = placeWeatherMarkers({
      samples: [{ latitude: null, longitude: null }, sampleAt(0)],
      mapInstance: makeMap()
    })

    expect(placed.map((group) => group.indices)).toEqual([[1]])
  })
})
