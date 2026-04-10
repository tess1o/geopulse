import { beforeEach, describe, expect, it, vi } from 'vitest'

const authState = {
  customMapTileUrl: '',
  customMapStyleUrl: '',
  mapRenderMode: 'VECTOR'
}

vi.mock('@/stores/auth', () => ({
  useAuthStore: () => authState
}))

import {
  DEFAULT_RASTER_TILE_URL,
  DEFAULT_VECTOR_STYLE_URL,
  resolveEffectiveMapMode,
  resolveRasterTileSource,
  resolveVectorStyleSource
} from '@/maps/runtime/mapSourceResolver'

describe('mapSourceResolver', () => {
  beforeEach(() => {
    authState.customMapTileUrl = ''
    authState.customMapStyleUrl = ''
    authState.mapRenderMode = 'VECTOR'
  })

  it('uses auth defaults when no overrides are present', () => {
    const rasterSource = resolveRasterTileSource({})
    const vectorSource = resolveVectorStyleSource({})

    expect(rasterSource.tileUrl).toContain(DEFAULT_RASTER_TILE_URL)
    expect(vectorSource.styleUrl).toBe(DEFAULT_VECTOR_STYLE_URL)
    expect(resolveEffectiveMapMode({})).toBe('VECTOR')
  })

  it('keeps raster and vector custom sources independently', () => {
    authState.customMapTileUrl = 'https://tiles.example.com/{z}/{x}/{y}.png'
    authState.customMapStyleUrl = 'https://styles.example.com/styles/base.json'

    const rasterSource = resolveRasterTileSource({})
    const vectorSource = resolveVectorStyleSource({})

    expect(rasterSource.tileUrl).toContain('https://tiles.example.com/{z}/{x}/{y}.png')
    expect(vectorSource.styleUrl).toBe('https://styles.example.com/styles/base.json')
  })

  it('honors explicit render mode override', () => {
    authState.mapRenderMode = 'RASTER'

    expect(resolveEffectiveMapMode({ overrideRenderMode: 'VECTOR' })).toBe('VECTOR')
    expect(resolveEffectiveMapMode({ overrideRenderMode: 'RASTER' })).toBe('RASTER')
  })

  it('defaults shared legacy raster-only links to raster mode', () => {
    const mode = resolveEffectiveMapMode({
      isSharedView: true,
      overrideTileUrl: 'https://legacy.example.com/{z}/{x}/{y}.png',
      overrideStyleUrl: null
    })

    expect(mode).toBe('RASTER')
  })

  it('uses vector mode for shared links with explicit vector style', () => {
    const mode = resolveEffectiveMapMode({
      isSharedView: true,
      overrideStyleUrl: 'https://tiles.openfreemap.org/styles/liberty',
      overrideTileUrl: null
    })

    expect(mode).toBe('VECTOR')
  })
})
