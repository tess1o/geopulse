import { describe, expect, it } from 'vitest'
import { createNativeClusterMirror } from './nativeClusterMirror'

// Longitude delta that is `px` screen pixels wide at `zoom` (512px tiles).
const lngForPixels = (px, zoom) => (px / (512 * (2 ** zoom))) * 360

const groupsApart = (px, atZoom) => [
  { longitude: 30.52, latitude: 50.45 },
  { longitude: 30.52 + lngForPixels(px, atZoom), latitude: 50.45 }
]

describe('createNativeClusterMirror', () => {
  const options = { clusterRadius: 48, clusterMaxZoom: 16 }

  it('clusters by the integer tile zoom, like MapLibre, not the fractional camera zoom', () => {
    // 60px apart on screen at 15.8 - but only ~34px at tile zoom 15, where MapLibre clusters.
    const entities = createNativeClusterMirror(options).getRenderedEntities(groupsApart(60, 15.8), 15.8)

    expect(entities).toHaveLength(1)
    expect(entities[0].isCluster).toBe(true)
    expect(entities[0].indices.sort()).toEqual([0, 1])
  })

  it('still clusters at clusterMaxZoom itself', () => {
    const entities = createNativeClusterMirror(options).getRenderedEntities(groupsApart(30, 16), 16.4)

    expect(entities).toHaveLength(1)
  })

  it('renders everything standalone above clusterMaxZoom', () => {
    const entities = createNativeClusterMirror(options).getRenderedEntities(groupsApart(30, 17), 17.2)

    expect(entities).toHaveLength(2)
    expect(entities.every((entity) => !entity.isCluster)).toBe(true)
  })

  it('returns nothing for empty input', () => {
    expect(createNativeClusterMirror(options).getRenderedEntities([], 12)).toEqual([])
  })
})
