import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it } from 'vitest'
import { useAuthStore } from '@/stores/auth'
import { DEFAULT_VECTOR_STYLE_URL, buildRasterMapLibreStyle, resolveProfileMapLibreStyle } from './mapSourceResolver'

describe('profile MapLibre style', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  const signIn = (profile) => {
    useAuthStore().user = { id: 'user', ...profile }
  }

  it('uses the vector style of the timeline settings', () => {
    signIn({ mapRenderMode: 'VECTOR', customMapStyleUrl: 'https://tiles.example.com/style.json' })
    expect(resolveProfileMapLibreStyle()).toBe('https://tiles.example.com/style.json')

    signIn({ mapRenderMode: 'VECTOR' })
    expect(resolveProfileMapLibreStyle()).toBe(DEFAULT_VECTOR_STYLE_URL)
  })

  it('draws the raster tiles of the timeline settings with MapLibre, one URL per subdomain', () => {
    signIn({ mapRenderMode: 'RASTER', customMapTileUrl: 'https://{s}.tiles.example.com/{z}/{x}/{y}.png' })

    const style = resolveProfileMapLibreStyle()
    const source = style.sources['profile-raster']
    expect(source.type).toBe('raster')
    expect(source.tileSize).toBe(256)
    expect(source.tiles.map((url) => url.split('?')[0])).toEqual([
      'https://a.tiles.example.com/{z}/{x}/{y}.png',
      'https://b.tiles.example.com/{z}/{x}/{y}.png',
      'https://c.tiles.example.com/{z}/{x}/{y}.png'
    ])
    expect(style.layers).toEqual([{ id: 'profile-raster', type: 'raster', source: 'profile-raster' }])
  })

  it('makes the bundled OSM proxy URL absolute without encoding its placeholders', () => {
    const style = buildRasterMapLibreStyle({ tileUrl: '/osm/tiles/{s}/{z}/{x}/{y}.png', subdomains: ['a'] })

    expect(style.sources['profile-raster'].tiles).toEqual([`${window.location.origin}/osm/tiles/a/{z}/{x}/{y}.png`])
  })
})
