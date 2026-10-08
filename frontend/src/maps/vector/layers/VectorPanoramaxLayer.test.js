import { mount } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'

const popups = []
vi.mock('maplibre-gl', () => ({
  Popup: class {
    constructor(options) {
      this.options = options
      popups.push(this)
    }

    setLngLat(value) { this.lngLat = value; return this }
    setDOMContent(value) { this.element = value; return this }
    addTo() { return this }
    on() { return this }
    remove() { this.removed = true }
  }
}))
import VectorPanoramaxLayer from './VectorPanoramaxLayer.vue'

const flush = () => new Promise(resolve => setTimeout(resolve, 0))
const response = (data) => ({ ok: true, json: async () => data })

const v1Style = {
  sources: { panoramax: { type: 'vector', tiles: ['https://example.test/map/{z}/{x}/{y}.mvt'], minzoom: 0, maxzoom: 15 } }
}
const v2Style = {
  metadata: { 'panoramax:tiles_version': 2 },
  sources: { panoramax: { type: 'vector', tiles: ['https://example.test/map/2/{z}/{x}/{y}.mvt'], minzoom: 0, maxzoom: 17 } }
}
const catalogWithV2 = {
  links: [
    { rel: 'xyz', type: 'application/vnd.mapbox-vector-tile', href: '/map/{z}/{x}/{y}.mvt' },
    { rel: 'xyz-style', href: '/map/style.json' },
    { rel: 'xyz-style', href: '/map/2/style.json' },
    { rel: 'item-preview', href: '/pictures/{id}/thumb.jpg' }
  ]
}

const createMap = () => {
  const sources = new Map()
  const layers = new Map()
  const handlers = new Map()
  return {
    style: {},
    sources,
    layers,
    handlers,
    addSource: vi.fn((id, source) => sources.set(id, source)),
    getSource: vi.fn(id => sources.get(id)),
    removeSource: vi.fn(id => sources.delete(id)),
    addLayer: vi.fn(layer => layers.set(layer.id, layer)),
    getLayer: vi.fn(id => layers.get(id)),
    removeLayer: vi.fn(id => layers.delete(id)),
    on: vi.fn((event, layerId, handler) => handlers.set(`${event}:${layerId}`, handler)),
    off: vi.fn((event, layerId) => handlers.delete(`${event}:${layerId}`)),
    getCanvas: () => ({ style: {} }),
    getZoom: () => 12,
    easeTo: vi.fn()
  }
}

const layerBySourceLayer = (map, sourceLayer) => [...map.layers.values()]
  .find(layer => layer['source-layer'] === sourceLayer)

describe('VectorPanoramaxLayer', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
    popups.length = 0
  })

  it('prefers v2 coverage cells and opens the representative photo', async () => {
    const map = createMap()
    vi.stubGlobal('fetch', vi.fn((url) => {
      if (url === 'https://example.test/api') return Promise.resolve(response(catalogWithV2))
      if (url === 'https://example.test/map/style.json') return Promise.resolve(response(v1Style))
      return Promise.resolve(response(v2Style))
    }))

    const wrapper = mount(VectorPanoramaxLayer, {
      props: { map, visible: true, endpoint: 'https://example.test/api' }
    })
    await flush()
    await flush()

    expect(map.addSource).toHaveBeenCalledWith(expect.any(String), expect.objectContaining({
      maxzoom: 17,
      tiles: ['https://example.test/map/2/{z}/{x}/{y}.mvt']
    }))
    expect(layerBySourceLayer(map, 'grid')).toMatchObject({ type: 'fill', maxzoom: 14.9 })
    expect(layerBySourceLayer(map, 'sequences')).toMatchObject({ minzoom: 15 })
    expect(layerBySourceLayer(map, 'pictures')).toMatchObject({ minzoom: 15 })

    const grid = layerBySourceLayer(map, 'grid')
    map.handlers.get(`mousemove:${grid.id}`)({
      features: [{ properties: { best_item: '3ec17c7e-5efa-4a20-b8df-67d56a3718c5' } }],
      lngLat: { lng: 14.4, lat: 50.1 }
    })
    expect(popups.at(-1).element.querySelector('img').src).toContain('/pictures/3ec17c7e-5efa-4a20-b8df-67d56a3718c5/thumb.jpg')

    map.handlers.get(`click:${grid.id}`)({
      features: [{ properties: { best_item: '3ec17c7e-5efa-4a20-b8df-67d56a3718c5' } }]
    })
    expect(wrapper.emitted('select')).toContainEqual([{ pictureId: '3ec17c7e-5efa-4a20-b8df-67d56a3718c5' }])

    const pictures = layerBySourceLayer(map, 'pictures')
    map.handlers.get(`click:${pictures.id}`)({
      features: [{ properties: { id: 'picture-id', sequences: '["1e345a15-43c8-485d-9aff-47c1f5cbf849"]' } }]
    })
    expect(wrapper.emitted('select').at(-1)).toEqual([{
      pictureId: 'picture-id',
      sequenceId: '1e345a15-43c8-485d-9aff-47c1f5cbf849',
      captureTime: null,
      heading: null
    }])

    await wrapper.setProps({ visible: false })
    expect(map.removeLayer).toHaveBeenCalledTimes(3)
    expect(map.removeSource).toHaveBeenCalledTimes(1)
  })

  it('falls back to the legacy tiles when v2 is unavailable', async () => {
    const map = createMap()
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(response({
      links: [{ rel: 'xyz', type: 'application/vnd.mapbox-vector-tile', href: '/tiles/{z}/{x}/{y}.mvt' }]
    })))

    mount(VectorPanoramaxLayer, { props: { map, visible: true, endpoint: 'https://example.test/api' } })
    await flush()

    expect(map.addSource).toHaveBeenCalledWith(expect.any(String), expect.objectContaining({
      maxzoom: 15,
      tiles: ['https://example.test/tiles/{z}/{x}/{y}.mvt']
    }))
    expect(layerBySourceLayer(map, 'grid')).toMatchObject({ type: 'circle', maxzoom: 6 })
    expect(layerBySourceLayer(map, 'sequences')).toMatchObject({ minzoom: 6 })
  })

  it('does not request tiles until the map control enables it', async () => {
    const map = createMap()
    const fetch = vi.fn().mockResolvedValue(response({
      links: [{ rel: 'xyz', type: 'application/vnd.mapbox-vector-tile', href: '/tiles/{z}/{x}/{y}.mvt' }]
    }))
    vi.stubGlobal('fetch', fetch)

    const wrapper = mount(VectorPanoramaxLayer, {
      props: { map, visible: false, endpoint: 'https://example.test/api' }
    })
    await flush()
    expect(fetch).not.toHaveBeenCalled()

    await wrapper.setProps({ visible: true })
    await flush()
    expect(fetch).toHaveBeenCalledWith('https://example.test/api')
  })
})
