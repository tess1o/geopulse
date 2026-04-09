import { isMapLibreMap } from '@/maps/vector/utils/maplibreLayerUtils'
import { getAreaPolygonLngLat } from '@/maps/favoritesManagement/shared/favoritesManagementGeometry'

const createAreaFeatureCollection = (coordinates) => ({
  type: 'FeatureCollection',
  features: [
    {
      type: 'Feature',
      geometry: {
        type: 'Polygon',
        coordinates: [coordinates]
      },
      properties: {}
    }
  ]
})

export const createVectorFavoriteAreaOverlayAdapter = () => {
  const overlayIds = {
    sourceId: `edit-favorite-area-source-${Math.random().toString(36).slice(2, 10)}`,
    fillLayerId: `edit-favorite-area-fill-${Math.random().toString(36).slice(2, 10)}`,
    lineLayerId: `edit-favorite-area-line-${Math.random().toString(36).slice(2, 10)}`
  }

  let map = null
  let styleLoadHandler = null
  let lastArea = null

  const removeOverlay = () => {
    if (!isMapLibreMap(map)) {
      return
    }

    if (map.getLayer(overlayIds.lineLayerId)) {
      map.removeLayer(overlayIds.lineLayerId)
    }
    if (map.getLayer(overlayIds.fillLayerId)) {
      map.removeLayer(overlayIds.fillLayerId)
    }
    if (map.getSource(overlayIds.sourceId)) {
      map.removeSource(overlayIds.sourceId)
    }
  }

  const draw = (areaLike) => {
    if (!isMapLibreMap(map)) {
      return
    }

    const coordinates = getAreaPolygonLngLat(areaLike)
    if (!coordinates) {
      removeOverlay()
      return
    }

    lastArea = areaLike
    const data = createAreaFeatureCollection(coordinates)
    const source = map.getSource(overlayIds.sourceId)

    if (source && typeof source.setData === 'function') {
      source.setData(data)
    } else {
      map.addSource(overlayIds.sourceId, {
        type: 'geojson',
        data
      })
    }

    if (!map.getLayer(overlayIds.fillLayerId)) {
      map.addLayer({
        id: overlayIds.fillLayerId,
        type: 'fill',
        source: overlayIds.sourceId,
        paint: {
          'fill-color': '#ef4444',
          'fill-opacity': 0.2
        }
      })
    }

    if (!map.getLayer(overlayIds.lineLayerId)) {
      map.addLayer({
        id: overlayIds.lineLayerId,
        type: 'line',
        source: overlayIds.sourceId,
        paint: {
          'line-color': '#ef4444',
          'line-width': 2,
          'line-opacity': 0.95
        }
      })
    }
  }

  const clear = () => {
    lastArea = null
    removeOverlay()
  }

  const initialize = (mapInstance) => {
    map = mapInstance

    styleLoadHandler = () => {
      if (lastArea) {
        draw(lastArea)
      }
    }

    map.on('style.load', styleLoadHandler)
  }

  const destroy = () => {
    clear()

    if (isMapLibreMap(map) && styleLoadHandler) {
      map.off('style.load', styleLoadHandler)
    }

    styleLoadHandler = null
    map = null
  }

  return {
    initialize,
    draw,
    clear,
    destroy
  }
}

