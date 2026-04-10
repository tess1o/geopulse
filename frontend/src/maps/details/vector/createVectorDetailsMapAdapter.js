import maplibregl from 'maplibre-gl'
import { toFiniteCoordinate } from '@/maps/shared/coordinateUtils'
import { escapeHtml } from '@/maps/shared/popupContentBuilders'
import {
  isMapLibreMap,
  normalizeLeafletBoundsToMapLibre
} from '@/maps/vector/utils/maplibreLayerUtils'

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

const createTripEndpointElement = (type) => {
  const root = document.createElement('div')
  root.className = `${type}-marker`
  root.innerHTML = `<div class="marker-pin ${type}-pin"><i class="pi ${type === 'start' ? 'pi-play' : 'pi-stop'}"></i></div>`
  return root
}

export const createVectorDetailsMapAdapter = (callbacks = {}) => {
  const token = `details-map-${Math.random().toString(36).slice(2, 10)}`
  const ids = {
    tripSourceId: `${token}-trip-source`,
    tripLayerId: `${token}-trip-layer`
  }

  let map = null
  let stayMarker = null
  let stayPopup = null
  let tripStartMarker = null
  let tripEndMarker = null

  const clearTripLine = () => {
    if (!isMapLibreMap(map)) {
      return
    }

    if (map.getLayer(ids.tripLayerId)) {
      map.removeLayer(ids.tripLayerId)
    }
    if (map.getSource(ids.tripSourceId)) {
      map.removeSource(ids.tripSourceId)
    }
  }

  const clear = () => {
    clearTripLine()

    if (stayMarker) {
      stayMarker.remove()
      stayMarker = null
    }

    if (stayPopup) {
      stayPopup.remove()
      stayPopup = null
    }

    if (tripStartMarker) {
      tripStartMarker.remove()
      tripStartMarker = null
    }

    if (tripEndMarker) {
      tripEndMarker.remove()
      tripEndMarker = null
    }
  }

  const renderStay = (stay) => {
    if (!isMapLibreMap(map)) {
      return false
    }

    const latitude = toFiniteCoordinate(stay?.latitude)
    const longitude = toFiniteCoordinate(stay?.longitude)
    if (latitude === null || longitude === null) {
      clear()
      return false
    }

    clear()

    stayPopup = new maplibregl.Popup({
      closeButton: true,
      closeOnClick: true,
      closeOnMove: false,
      offset: 12
    }).setHTML(buildStayPopupHtml(stay, callbacks.buildStayPopupHtml))

    stayMarker = new maplibregl.Marker()
      .setLngLat([longitude, latitude])
      .setPopup(stayPopup)
      .addTo(map)

    map.setView([latitude, longitude], 16)
    stayPopup.addTo(map)

    return true
  }

  const renderTrip = ({ trip, tripGpsPoints = [], pathColor = '#6b7280' } = {}) => {
    if (!isMapLibreMap(map)) {
      return false
    }

    clear()

    const pathCoordinates = toTripPathCoordinates(tripGpsPoints)
    if (pathCoordinates.length === 0) {
      return false
    }

    map.addSource(ids.tripSourceId, {
      type: 'geojson',
      data: {
        type: 'FeatureCollection',
        features: [
          {
            type: 'Feature',
            geometry: {
              type: 'LineString',
              coordinates: pathCoordinates.map(([lat, lng]) => [lng, lat])
            },
            properties: {}
          }
        ]
      }
    })

    map.addLayer({
      id: ids.tripLayerId,
      type: 'line',
      source: ids.tripSourceId,
      layout: {
        'line-join': 'round',
        'line-cap': 'round'
      },
      paint: {
        'line-color': pathColor,
        'line-width': 4,
        'line-opacity': 0.8
      }
    })

    const startLat = toFiniteCoordinate(trip?.latitude)
    const startLng = toFiniteCoordinate(trip?.longitude)
    if (startLat !== null && startLng !== null) {
      tripStartMarker = new maplibregl.Marker({
        element: createTripEndpointElement('start'),
        anchor: 'center'
      })
        .setLngLat([startLng, startLat])
        .addTo(map)
    }

    const endLat = toFiniteCoordinate(trip?.endLatitude)
    const endLng = toFiniteCoordinate(trip?.endLongitude)
    if (endLat !== null && endLng !== null) {
      tripEndMarker = new maplibregl.Marker({
        element: createTripEndpointElement('end'),
        anchor: 'center'
      })
        .setLngLat([endLng, endLat])
        .addTo(map)
    }

    const normalizedBounds = normalizeLeafletBoundsToMapLibre(pathCoordinates)
    if (normalizedBounds) {
      map.fitBounds(normalizedBounds, {
        padding: 20,
        duration: 0
      })
    }

    return true
  }

  const destroy = () => {
    clear()
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
