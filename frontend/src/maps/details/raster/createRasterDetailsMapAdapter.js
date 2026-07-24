import L from 'leaflet'
import { toFiniteCoordinate } from '@/maps/shared/coordinateUtils'
import MapInfoPopup from '@/maps/shared/popups/MapInfoPopup.vue'
import { mountMapPopup } from '@/maps/shared/popups/mountMapPopup'
import {
  getMapPopupVariantClassName,
  MAP_POPUP_COMPACT_MAX_WIDTH_PX
} from '@/maps/shared/popups/mapPopupOptions'
import { formatDuration } from '@/utils/durationFormatter'

const toTripPathCoordinates = (tripPoints) => {
  if (!Array.isArray(tripPoints)) {
    return []
  }

  return tripPoints
    .map((point) => {
      const latitude = toFiniteCoordinate(point?.latitude)
      const longitude = toFiniteCoordinate(point?.longitude)
      if (latitude === null || longitude === null) {
        return null
      }
      return [latitude, longitude]
    })
    .filter(Boolean)
}

const createTripEndpointIcon = (type) => L.divIcon({
  className: `${type}-marker`,
  html: `<div class="marker-pin ${type}-pin"><i class="pi ${type === 'start' ? 'pi-play' : 'pi-stop'}"></i></div>`,
  iconSize: [30, 30],
  iconAnchor: [15, 15]
})

const buildDetailsStayPopupModel = (stay) => ({
  title: stay?.locationName || 'Unknown location',
  subtitle: stay?.address || '',
  iconClass: 'pi pi-map-marker',
  rows: [
    {
      label: 'Duration',
      value: formatDuration(stay?.stayDuration)
    }
  ],
  variant: 'compact'
})

export const createRasterDetailsMapAdapter = (callbacks = {}) => {
  let map = null
  let layerGroup = null
  let stayPopupMount = null

  const ensureLayerGroup = () => {
    if (!map) {
      return null
    }

    if (!layerGroup) {
      layerGroup = L.layerGroup().addTo(map)
    }

    return layerGroup
  }

  const clear = () => {
    stayPopupMount?.unmount?.()
    stayPopupMount = null

    if (!map || !layerGroup) {
      return
    }

    layerGroup.clearLayers()
  }

  const renderStay = (stay) => {
    if (!map) {
      return false
    }

    const latitude = toFiniteCoordinate(stay?.latitude)
    const longitude = toFiniteCoordinate(stay?.longitude)
    if (latitude === null || longitude === null) {
      clear()
      return false
    }

    const activeLayerGroup = ensureLayerGroup()
    if (!activeLayerGroup) {
      return false
    }

    clear()

    const marker = L.marker([latitude, longitude]).addTo(activeLayerGroup)
    if (typeof callbacks.buildStayPopupHtml === 'function') {
      marker.bindPopup(callbacks.buildStayPopupHtml(stay)).openPopup()
    } else {
      stayPopupMount = mountMapPopup(MapInfoPopup, buildDetailsStayPopupModel(stay))
      marker
        .bindPopup(stayPopupMount.element, {
          maxWidth: MAP_POPUP_COMPACT_MAX_WIDTH_PX,
          className: getMapPopupVariantClassName('compact', 'gp-details-stay-popup-container')
        })
        .openPopup()
    }

    map.setView([latitude, longitude], 16)

    return Boolean(marker)
  }

  const renderTrip = ({ trip, tripGpsPoints = [], pathColor = '#6b7280' } = {}) => {
    if (!map) {
      return false
    }

    const activeLayerGroup = ensureLayerGroup()
    if (!activeLayerGroup) {
      return false
    }

    clear()

    const pathCoordinates = toTripPathCoordinates(tripGpsPoints)
    if (pathCoordinates.length === 0) {
      return false
    }

    const pathLayer = L.polyline(pathCoordinates, {
      color: pathColor,
      weight: 4,
      opacity: 0.8
    }).addTo(activeLayerGroup)

    const startLat = toFiniteCoordinate(trip?.latitude)
    const startLng = toFiniteCoordinate(trip?.longitude)
    if (startLat !== null && startLng !== null) {
      L.marker([startLat, startLng], {
        icon: createTripEndpointIcon('start')
      }).addTo(activeLayerGroup)
    }

    const endLat = toFiniteCoordinate(trip?.endLatitude)
    const endLng = toFiniteCoordinate(trip?.endLongitude)
    if (endLat !== null && endLng !== null) {
      L.marker([endLat, endLng], {
        icon: createTripEndpointIcon('end')
      }).addTo(activeLayerGroup)
    }

    map.fitBounds(pathLayer.getBounds(), { padding: [20, 20] })
    return true
  }

  const destroy = () => {
    clear()

    if (map && layerGroup) {
      map.removeLayer(layerGroup)
    }

    layerGroup = null
    map = null
  }

  return {
    initialize(mapInstance) {
      map = mapInstance
    },
    renderStay,
    renderTrip,
    clear,
    destroy
  }
}
