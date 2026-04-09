import L from 'leaflet'
import { toLeafletBounds } from '@/maps/favoritesManagement/shared/favoritesManagementGeometry'

export const createRasterFavoriteAreaOverlayAdapter = () => {
  let map = null
  let rectangleLayer = null

  const clear = () => {
    if (map && rectangleLayer) {
      map.removeLayer(rectangleLayer)
    }
    rectangleLayer = null
  }

  const draw = (areaLike) => {
    if (!map) {
      return
    }

    const bounds = toLeafletBounds(areaLike)
    if (!bounds) {
      clear()
      return
    }

    clear()
    rectangleLayer = L.rectangle(bounds, {
      color: '#ef4444',
      fillColor: '#ef4444',
      fillOpacity: 0.2,
      weight: 2
    }).addTo(map)
  }

  const initialize = (mapInstance) => {
    map = mapInstance
  }

  const destroy = () => {
    clear()
    map = null
  }

  return {
    initialize,
    draw,
    clear,
    destroy
  }
}

