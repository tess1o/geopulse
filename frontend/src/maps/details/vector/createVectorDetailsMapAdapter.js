import maplibregl from 'maplibre-gl'
import { toFiniteCoordinate } from '@/maps/shared/coordinateUtils'
import {
  isMapLibreMap,
  normalizeLeafletBoundsToMapLibre
} from '@/maps/vector/utils/maplibreLayerUtils'
import MapInfoPopup from '@/maps/shared/popups/MapInfoPopup.vue'
import { mountMapPopup } from '@/maps/shared/popups/mountMapPopup'
import {
  getMapPopupVariantClassName,
  MAP_POPUP_COMPACT_MAX_WIDTH
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
  let stayPopupMount = null
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
    stayPopupMount?.unmount?.()
    stayPopupMount = null

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

    if (typeof callbacks.buildStayPopupHtml === 'function') {
      stayPopup = new maplibregl.Popup({
        closeButton: true,
        closeOnClick: true,
        closeOnMove: false,
        offset: 12
      }).setHTML(callbacks.buildStayPopupHtml(stay))
    } else {
      stayPopupMount = mountMapPopup(MapInfoPopup, buildDetailsStayPopupModel(stay))
      stayPopup = new maplibregl.Popup({
        closeButton: true,
        closeOnClick: true,
        closeOnMove: false,
        maxWidth: MAP_POPUP_COMPACT_MAX_WIDTH,
        offset: 12,
        className: getMapPopupVariantClassName('compact', 'gp-details-stay-popup-container')
      }).setDOMContent(stayPopupMount.element)
    }

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
