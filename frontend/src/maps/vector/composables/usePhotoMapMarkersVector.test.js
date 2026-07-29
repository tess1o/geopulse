import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { usePhotoMapMarkersVector } from './usePhotoMapMarkersVector'

vi.mock('maplibre-gl', () => ({
  default: {
    Marker: vi.fn(() => ({
      setLngLat: vi.fn().mockReturnThis(),
      addTo: vi.fn().mockReturnThis(),
      remove: vi.fn()
    }))
  }
}))

vi.mock('@/utils/immichPhotoThumbnails', () => ({
  buildPhotoThumbnailImageId: (prefix, photo) => `${prefix}-thumb-${photo.id}`,
  createCircularPhotoThumbnailImageData: vi.fn(async () => ({
    data: new Uint8ClampedArray(4),
    width: 1,
    height: 1
  }))
}))

const createMap = () => {
  const layers = new Map()
  const sources = new Map()
  const images = new Set()

  return {
    style: {},
    addSource: vi.fn((sourceId, source) => {
      sources.set(sourceId, {
        ...source,
        setData: vi.fn()
      })
    }),
    getSource: vi.fn((sourceId) => sources.get(sourceId)),
    removeSource: vi.fn((sourceId) => {
      sources.delete(sourceId)
    }),
    addLayer: vi.fn((layer) => {
      layers.set(layer.id, layer)
    }),
    getLayer: vi.fn((layerId) => layers.get(layerId)),
    removeLayer: vi.fn((layerId) => {
      layers.delete(layerId)
    }),
    setLayoutProperty: vi.fn(),
    hasImage: vi.fn((imageId) => images.has(imageId)),
    addImage: vi.fn((imageId) => {
      images.add(imageId)
    }),
    removeImage: vi.fn((imageId) => {
      images.delete(imageId)
    }),
    on: vi.fn(),
    off: vi.fn(),
    getCanvas: vi.fn(() => ({ style: {} }))
  }
}

describe('usePhotoMapMarkersVector', () => {
  beforeEach(() => {
    vi.spyOn(HTMLCanvasElement.prototype, 'getContext').mockReturnValue({
      fillStyle: '',
      fillRect: vi.fn(),
      beginPath: vi.fn(),
      arc: vi.fn(),
      fill: vi.fn(),
      getImageData: vi.fn(() => ({
        data: new Uint8ClampedArray(4),
        width: 1,
        height: 1
      }))
    })
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('does not throw when MapLibre image registry disappears during cleanup', () => {
    const map = createMap()
    const { clearPhotoMarkers, renderPhotoMarkers } = usePhotoMapMarkersVector()

    renderPhotoMarkers(map, [])
    map.hasImage.mockImplementation(() => {
      throw new TypeError("Cannot read properties of undefined (reading 'getImage')")
    })

    expect(() => clearPhotoMarkers()).not.toThrow()
  })

  it('does not add async thumbnail images after cleanup', async () => {
    const map = createMap()
    const { clearPhotoMarkers, renderPhotoMarkers } = usePhotoMapMarkersVector()

    renderPhotoMarkers(map, [{
      id: 'photo-1',
      thumbnailUrl: '/thumbnail/photo-1',
      latitude: 10,
      longitude: 20
    }])

    clearPhotoMarkers()
    await Promise.resolve()
    await Promise.resolve()

    expect(map.addImage).not.toHaveBeenCalledWith(
      expect.stringContaining('-thumb-photo-1'),
      expect.anything(),
      expect.anything()
    )
  })
})
