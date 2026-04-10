import { describe, expect, it } from 'vitest'
import {
  MAP_RENDER_MODES,
  markMapEngineMode,
  normalizeMapRenderMode,
  resolveMapEngineModeFromInstance
} from '@/maps/contracts/mapContracts'

describe('mapContracts', () => {
  it('normalizes unknown mode to VECTOR', () => {
    expect(normalizeMapRenderMode('UNKNOWN')).toBe(MAP_RENDER_MODES.VECTOR)
    expect(normalizeMapRenderMode('RASTER')).toBe(MAP_RENDER_MODES.RASTER)
  })

  it('prefers explicit engine marker when present', () => {
    const map = {}

    markMapEngineMode(map, MAP_RENDER_MODES.RASTER)
    expect(resolveMapEngineModeFromInstance(map)).toBe(MAP_RENDER_MODES.RASTER)

    markMapEngineMode(map, MAP_RENDER_MODES.VECTOR)
    expect(resolveMapEngineModeFromInstance(map)).toBe(MAP_RENDER_MODES.VECTOR)
  })

  it('falls back to heuristic detection when no marker exists', () => {
    const rasterLikeMap = {
      addLayer: () => {},
      setView: () => {}
    }

    const vectorLikeMap = {
      addSource: () => {},
      setStyle: () => {}
    }

    expect(resolveMapEngineModeFromInstance(rasterLikeMap)).toBe(MAP_RENDER_MODES.RASTER)
    expect(resolveMapEngineModeFromInstance(vectorLikeMap)).toBe(MAP_RENDER_MODES.VECTOR)
  })
})
