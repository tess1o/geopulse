import L from 'leaflet'
import { toFiniteCoordinate } from '@/maps/shared/coordinateUtils'
import { escapeHtml } from '@/maps/shared/popupContentBuilders'

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

const buildStayPopupHtml = (stay, fallbackFormatter) => {
  if (typeof fallbackFormatter === 'function') {
    return fallbackFormatter(stay)
  }

  const durationText = formatDuration(stay?.stayDuration)

  return `
    <div class="marker-popup">
      <strong>${escapeHtml(stay?.locationName || 'Unknown Location')}</strong><br/>
      ${stay?.address ? `<span>${escapeHtml(stay.address)}</span><br/>` : ''}
      <small>Duration: ${escapeHtml(durationText)}</small>
    </div>
  `
}

export const createRasterDetailsMapAdapter = (callbacks = {}) => {
  let map = null
  let layerGroup = null

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

    const marker = L.marker([latitude, longitude])
      .addTo(activeLayerGroup)
      .bindPopup(buildStayPopupHtml(stay, callbacks.buildStayPopupHtml))
      .openPopup()

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

function formatDuration(seconds) {
  if (!seconds) {
    return 'Unknown'
  }

  const totalSeconds = Number(seconds)
  if (!Number.isFinite(totalSeconds) || totalSeconds <= 0) {
    return 'Unknown'
  }

  const hours = Math.floor(totalSeconds / 3600)
  const minutes = Math.floor((totalSeconds % 3600) / 60)

  if (hours > 0) {
    return `${hours}h ${minutes}m`
  }

  return `${minutes}m`
}
