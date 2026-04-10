import maplibregl from 'maplibre-gl'
import {
  normalizePathPoints,
  reconstructTripPathPoints,
  resolveTripMarkerPoint,
  areSameCoordinate
} from '@/utils/tripPathReconstruction'
import {
  normalizePathSegmentsToLeafletTuples,
  toLeafletLatLngTuple,
  toFiniteCoordinate
} from '@/maps/shared/coordinateUtils'
import {
  isMapLibreMap,
  normalizeLeafletBoundsToMapLibre
} from '@/maps/vector/utils/maplibreLayerUtils'
import { createTripEndpointMarkerElement } from '@/maps/shared/tripEndpointMarkerBuilder'
import { escapeHtml } from '@/maps/shared/popupContentBuilders'

const defaultStayPopupHtml = (userTimeline, stay, color) => `
  <div style="font-family: sans-serif;">
    <div style="font-weight: 600; color: ${escapeHtml(color)}; margin-bottom: 4px;">${escapeHtml(userTimeline?.fullName || 'User')}</div>
    <div style="font-weight: 500; margin-bottom: 2px;">${escapeHtml(stay?.locationName || 'Stay')}</div>
    <div style="font-size: 0.875rem; color: #666;">${escapeHtml(formatDuration(stay?.stayDuration))}</div>
  </div>
`

const defaultTripPopupHtml = (trip) => `
  <div style="font-family: sans-serif;">
    <div style="font-weight: 600; margin-bottom: 4px;">${escapeHtml(trip?.userFullName || 'Trip')}</div>
    <div style="font-weight: 500; margin-bottom: 2px;">${escapeHtml(trip?.movementType || 'Trip')}</div>
    <div style="font-size: 0.875rem; color: #666;">
      ${escapeHtml(formatDuration(trip?.tripDuration))} • ${escapeHtml(formatDistance(trip?.distanceMeters))}
    </div>
  </div>
`

const toStayId = (userId, stayLike) => `${userId}-stay-${stayLike?.timestamp}`
const toTripId = (userId, tripLike) => `${userId}-trip-${tripLike?.timestamp}`

const resolveFallbackTripCoords = (trip) => {
  if (!trip) {
    return null
  }

  const startLat = toFiniteCoordinate(trip.latitude)
  const startLng = toFiniteCoordinate(trip.longitude)
  const endLat = toFiniteCoordinate(trip.endLatitude)
  const endLng = toFiniteCoordinate(trip.endLongitude)

  if ([startLat, startLng, endLat, endLng].some((coord) => coord === null)) {
    return null
  }

  return [[startLat, startLng], [endLat, endLng]]
}

const reconstructTripCoordsFromPath = (trip, userPathPoints) => {
  const { points } = reconstructTripPathPoints(trip, userPathPoints)
  return points
    .map((point) => toLeafletLatLngTuple(point))
    .filter((coord) => Array.isArray(coord) && coord.length === 2)
}

const toLineFeatures = (pathSegments) => pathSegments.map((segmentCoords) => ({
  type: 'Feature',
  geometry: {
    type: 'LineString',
    coordinates: segmentCoords.map(([lat, lng]) => [lng, lat])
  },
  properties: {}
}))

const createStayMarkerElement = (color) => {
  const element = document.createElement('div')
  element.className = 'custom-marker maplibre-friends-timeline-stay'
  element.innerHTML = `<div style="background-color: ${escapeHtml(color)}; width: 24px; height: 24px; border-radius: 50%; border: 2px solid white; box-shadow: 0 2px 4px rgba(0,0,0,0.3);"></div>`
  element.style.cursor = 'pointer'
  return element
}

export const createVectorFriendsTimelineMapAdapter = (callbacks = {}) => {
  const token = `friends-timeline-${Math.random().toString(36).slice(2, 10)}`
  let map = null
  let styleLoadHandler = null
  let lineArtifacts = []
  let highlightedLine = null
  let highlightedPopup = null
  let highlightedStartMarker = null
  let highlightedEndMarker = null
  let highlightedTripId = null
  let lastRenderPayload = null
  let lastFocusedItem = null

  const stayMarkers = new Map()
  const userPathPointsByUser = new Map()

  const buildStayPopupHtml = callbacks.buildStayPopupHtml || defaultStayPopupHtml
  const buildTripPopupHtml = callbacks.buildTripPopupHtml || defaultTripPopupHtml

  const clearLineArtifacts = () => {
    if (!isMapLibreMap(map)) {
      lineArtifacts = []
      return
    }

    lineArtifacts.forEach(({ sourceId, layerId }) => {
      if (map.getLayer(layerId)) {
        map.removeLayer(layerId)
      }
      if (map.getSource(sourceId)) {
        map.removeSource(sourceId)
      }
    })

    lineArtifacts = []
  }

  const clearStayMarkers = () => {
    stayMarkers.forEach((entry) => {
      entry.cleanup?.()
      entry.marker?.remove?.()
    })
    stayMarkers.clear()
  }

  const clearHighlightedTrip = () => {
    if (isMapLibreMap(map) && highlightedLine) {
      if (map.getLayer(highlightedLine.layerId)) {
        map.removeLayer(highlightedLine.layerId)
      }
      if (map.getSource(highlightedLine.sourceId)) {
        map.removeSource(highlightedLine.sourceId)
      }
    }

    highlightedLine = null

    if (highlightedPopup) {
      highlightedPopup.remove()
      highlightedPopup = null
    }

    if (highlightedStartMarker) {
      highlightedStartMarker.remove()
      highlightedStartMarker = null
    }

    if (highlightedEndMarker) {
      highlightedEndMarker.remove()
      highlightedEndMarker = null
    }

    highlightedTripId = null
  }

  const clear = () => {
    clearHighlightedTrip()
    clearLineArtifacts()
    clearStayMarkers()
    userPathPointsByUser.clear()
  }

  const fitToBounds = (points, options = {}) => {
    if (!isMapLibreMap(map) || !Array.isArray(points) || points.length === 0) {
      return
    }

    const normalizedBounds = normalizeLeafletBoundsToMapLibre(points)
    if (!normalizedBounds) {
      return
    }

    map.fitBounds(normalizedBounds, {
      padding: 50,
      duration: 0,
      ...options
    })
  }

  const render = ({ visibleTimelines = [] } = {}) => {
    if (!isMapLibreMap(map)) {
      return
    }

    lastRenderPayload = { visibleTimelines }

    clear()

    const allBounds = []

    visibleTimelines.forEach((userTimeline, index) => {
      const userId = userTimeline?.userId
      if (!userId) {
        return
      }

      const color = userTimeline?.assignedColor || '#3b82f6'
      const pathSegments = normalizePathSegmentsToLeafletTuples(userTimeline?.pathSegments)
      const lineFeatures = toLineFeatures(pathSegments)

      if (lineFeatures.length > 0) {
        const sourceId = `${token}-user-path-${index}-source`
        const layerId = `${token}-user-path-${index}-layer`

        map.addSource(sourceId, {
          type: 'geojson',
          data: {
            type: 'FeatureCollection',
            features: lineFeatures
          }
        })

        map.addLayer({
          id: layerId,
          type: 'line',
          source: sourceId,
          layout: {
            'line-join': 'round',
            'line-cap': 'round'
          },
          paint: {
            'line-color': color,
            'line-width': 4,
            'line-opacity': 0.75
          }
        })

        lineArtifacts.push({ sourceId, layerId })

        pathSegments.forEach((segmentCoords) => {
          segmentCoords.forEach((coord) => allBounds.push(coord))
        })
      }

      const stays = Array.isArray(userTimeline?.timeline?.stays)
        ? userTimeline.timeline.stays
        : []

      stays.forEach((stay) => {
        const stayLatLng = toLeafletLatLngTuple(stay)
        if (!stayLatLng) {
          return
        }

        const [lat, lng] = stayLatLng
        const element = createStayMarkerElement(color)
        const popup = new maplibregl.Popup({
          closeButton: true,
          closeOnClick: true,
          closeOnMove: false,
          offset: 12
        }).setHTML(buildStayPopupHtml(userTimeline, stay, color))

        const marker = new maplibregl.Marker({
          element,
          anchor: 'center'
        })
          .setLngLat([lng, lat])
          .setPopup(popup)
          .addTo(map)

        const handleClick = (event) => {
          event.preventDefault()
          event.stopPropagation()
          map.easeTo({
            center: [lng, lat],
            zoom: 15,
            duration: 500
          })
          popup.addTo(map)
        }

        element.addEventListener('click', handleClick)

        const stayId = toStayId(userId, stay)
        stayMarkers.set(stayId, {
          marker,
          popup,
          cleanup: () => {
            element.removeEventListener('click', handleClick)
          }
        })

        allBounds.push(stayLatLng)
      })

      userPathPointsByUser.set(userId, normalizePathPoints(userTimeline?.pathSegments))
    })

    if (allBounds.length > 0) {
      fitToBounds(allBounds)
    }

    if (lastFocusedItem?.type === 'trip') {
      focusOnItem(lastFocusedItem)
    }
  }

  const focusOnStay = (item) => {
    const stayId = toStayId(item?.userId, item)
    const entry = stayMarkers.get(stayId)
    if (!entry) {
      return
    }

    const lngLat = entry.marker.getLngLat()
    map.easeTo({
      center: [lngLat.lng, lngLat.lat],
      zoom: 15,
      duration: 500
    })

    entry.popup?.addTo(map)
  }

  const highlightTrip = (item) => {
    const tripId = toTripId(item?.userId, item)
    if (highlightedTripId === tripId) {
      clearHighlightedTrip()
      return
    }

    clearHighlightedTrip()

    const userPathPoints = userPathPointsByUser.get(item?.userId) || []
    let tripCoords = reconstructTripCoordsFromPath(item, userPathPoints)
    if (!tripCoords || tripCoords.length < 2) {
      tripCoords = resolveFallbackTripCoords(item)
    }

    if (!tripCoords || tripCoords.length < 2) {
      return
    }

    const startPoint = resolveTripMarkerPoint(item, 'start', {
      latitude: tripCoords[0][0],
      longitude: tripCoords[0][1]
    })
    const endPoint = resolveTripMarkerPoint(item, 'end', {
      latitude: tripCoords[tripCoords.length - 1][0],
      longitude: tripCoords[tripCoords.length - 1][1]
    })

    if (startPoint && endPoint && tripCoords.length >= 2) {
      tripCoords[0] = [startPoint.latitude, startPoint.longitude]
      tripCoords[tripCoords.length - 1] = [endPoint.latitude, endPoint.longitude]
    }

    const sourceId = `${token}-highlighted-trip-source`
    const layerId = `${token}-highlighted-trip-layer`

    map.addSource(sourceId, {
      type: 'geojson',
      data: {
        type: 'FeatureCollection',
        features: [
          {
            type: 'Feature',
            geometry: {
              type: 'LineString',
              coordinates: tripCoords.map(([lat, lng]) => [lng, lat])
            },
            properties: {}
          }
        ]
      }
    })

    map.addLayer({
      id: layerId,
      type: 'line',
      source: sourceId,
      layout: {
        'line-join': 'round',
        'line-cap': 'round'
      },
      paint: {
        'line-color': '#ff6b6b',
        'line-width': 6,
        'line-opacity': 1,
        'line-dasharray': [2, 2]
      }
    })

    highlightedLine = { sourceId, layerId }
    highlightedTripId = tripId

    const firstPoint = tripCoords[0]
    highlightedPopup = new maplibregl.Popup({
      closeButton: true,
      closeOnClick: true,
      closeOnMove: false,
      offset: 12
    })
      .setLngLat([firstPoint[1], firstPoint[0]])
      .setHTML(buildTripPopupHtml(item))
      .addTo(map)

    if (startPoint && endPoint) {
      const sameEndpoint = areSameCoordinate(startPoint, endPoint)

      const startElement = createTripEndpointMarkerElement({
        markerType: 'start',
        instant: true,
        styleOverrides: sameEndpoint ? { transform: 'translateX(-14px)' } : {}
      })
      const endElement = createTripEndpointMarkerElement({
        markerType: 'end',
        instant: true,
        styleOverrides: sameEndpoint ? { transform: 'translateX(14px)' } : {}
      })

      if (sameEndpoint) {
        startElement.style.zIndex = '20'
        endElement.style.zIndex = '10'
      } else {
        startElement.style.zIndex = '10'
        endElement.style.zIndex = '10'
      }

      highlightedStartMarker = new maplibregl.Marker({
        element: startElement,
        anchor: 'center'
      })
        .setLngLat([startPoint.longitude, startPoint.latitude])
        .addTo(map)

      highlightedEndMarker = new maplibregl.Marker({
        element: endElement,
        anchor: 'center'
      })
        .setLngLat([endPoint.longitude, endPoint.latitude])
        .addTo(map)
    }

    const boundsPoints = [...tripCoords]
    if (startPoint) {
      boundsPoints.push([startPoint.latitude, startPoint.longitude])
    }
    if (endPoint) {
      boundsPoints.push([endPoint.latitude, endPoint.longitude])
    }

    fitToBounds(boundsPoints, { duration: 500 })
  }

  const focusOnItem = (item) => {
    lastFocusedItem = item || null

    if (!item) {
      clearHighlightedTrip()
      return
    }

    if (item.type === 'stay') {
      focusOnStay(item)
      return
    }

    if (item.type === 'trip') {
      highlightTrip(item)
    }
  }

  const highlightItem = (item) => {
    if (!item) {
      clearHighlightedTrip()
      return
    }

    if (item.type === 'trip') {
      highlightTrip(item)
    }
  }

  const destroy = () => {
    clear()

    if (isMapLibreMap(map) && styleLoadHandler) {
      map.off('style.load', styleLoadHandler)
    }

    styleLoadHandler = null
    map = null
    lastRenderPayload = null
    lastFocusedItem = null
  }

  return {
    initialize(mapInstance) {
      map = mapInstance

      if (!isMapLibreMap(map)) {
        return
      }

      styleLoadHandler = () => {
        if (lastRenderPayload) {
          render(lastRenderPayload)
        }
      }

      map.on('style.load', styleLoadHandler)
    },
    render,
    focusOnItem,
    highlightItem,
    clear,
    destroy
  }
}

function formatDuration(seconds) {
  if (!seconds) return 'Unknown'
  const hours = Math.floor(seconds / 3600)
  const minutes = Math.floor((seconds % 3600) / 60)
  if (hours > 0) {
    return `${hours}h ${minutes}m`
  }
  return `${minutes}m`
}

function formatDistance(meters) {
  if (!meters) return 'Unknown'
  const km = meters / 1000
  if (km >= 1) {
    return `${km.toFixed(1)} km`
  }
  return `${meters.toFixed(0)} m`
}
