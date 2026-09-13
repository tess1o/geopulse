import { describe, expect, it, vi } from 'vitest'
import {
  MAPTILER_BUILDINGS_LAYER_ID,
  setMapTilerBuildings3dEnabled,
  supportsMapTilerBuildings3d
} from './maptilerBuildings3d'

const createMap = ({ mapTiler = true, hasLayer = false, hasBuildingLayer = true } = {}) => {
  let buildingLayerActive = hasLayer
  return {
    addSource: vi.fn(),
    addLayer: vi.fn(() => { buildingLayerActive = true }),
    removeLayer: vi.fn(() => { buildingLayerActive = false }),
    getLayer: vi.fn((id) => (buildingLayerActive && id === MAPTILER_BUILDINGS_LAYER_ID ? { id } : null)),
    getStyle: vi.fn(() => ({
      sources: {
        basemap: {
          type: 'vector',
          url: mapTiler
            ? 'https://api.maptiler.com/tiles/v3/tiles.json?key=test'
            : 'https://tiles.openfreemap.org/planet'
        }
      },
      layers: [
        { id: 'land', type: 'fill' },
        ...(hasBuildingLayer ? [{ id: 'building', type: 'fill', source: 'basemap', 'source-layer': 'building', filter: ['!=', ['get', 'hide_3d'], true] }] : []),
        { id: 'place-label', type: 'symbol', layout: { 'text-field': ['get', 'name'] } }
      ]
    }))
  }
}

describe('MapTiler building extrusions', () => {
  it('adds the MapTiler building layer below labels only for the supported schema', () => {
    const map = createMap()

    expect(supportsMapTilerBuildings3d(map)).toBe(true)
    expect(setMapTilerBuildings3dEnabled(map, true)).toBe(true)
    expect(map.addLayer).toHaveBeenCalledWith(expect.objectContaining({
      id: MAPTILER_BUILDINGS_LAYER_ID,
      source: 'basemap',
      'source-layer': 'building',
      type: 'fill-extrusion'
    }), 'place-label')
    expect(setMapTilerBuildings3dEnabled(map, true)).toBe(true)
    expect(map.addLayer).toHaveBeenCalledTimes(1)
  })

  it('supports a MapTiler hybrid style without a pre-styled building layer', () => {
    const map = createMap({ hasBuildingLayer: false })

    expect(supportsMapTilerBuildings3d(map)).toBe(true)
    expect(setMapTilerBuildings3dEnabled(map, true)).toBe(true)
    expect(map.addLayer).toHaveBeenCalledWith(expect.objectContaining({
      source: 'basemap',
      'source-layer': 'building'
    }), 'place-label')
  })

  it('fails closed for other styles and removes an existing layer when disabled', () => {
    const unsupportedMap = createMap({ mapTiler: false })
    const enabledMap = createMap({ hasLayer: true })

    expect(setMapTilerBuildings3dEnabled(unsupportedMap, true)).toBe(false)
    expect(unsupportedMap.addLayer).not.toHaveBeenCalled()
    expect(setMapTilerBuildings3dEnabled(enabledMap, false)).toBe(true)
    expect(enabledMap.removeLayer).toHaveBeenCalledWith(MAPTILER_BUILDINGS_LAYER_ID)
  })
})
