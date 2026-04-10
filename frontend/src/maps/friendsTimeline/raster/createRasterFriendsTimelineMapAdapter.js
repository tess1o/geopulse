import L from 'leaflet'
import { createHighlightedPathStartMarker, createHighlightedPathEndMarker } from '@/utils/mapHelpers'
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

const createStayMarkerIcon = (color) => L.divIcon({
  className: 'custom-marker',
  html: `<div style="background-color: ${escapeHtml(color)}; width: 24px; height: 24px; border-radius: 50%; border: 2px solid white; box-shadow: 0 2px 4px rgba(0,0,0,0.3);"></div>`,
  iconSize: [24, 24],
  iconAnchor: [12, 12]
})

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

const toStayId = (userId, stayLike) => `${userId}-stay-${stayLike?.timestamp}`
const toTripId = (userId, tripLike) => `${userId}-trip-${tripLike?.timestamp}`

export const createRasterFriendsTimelineMapAdapter = (callbacks = {}) => {
  let map = null
  const markerGroups = new Map()
  const markerRefs = new Map()
  const userPathPointsByUser = new Map()

  let highlightedTripPath = null
  let highlightedTripStartMarker = null
  let highlightedTripEndMarker = null
  let highlightedTripId = null

  const buildStayPopupHtml = callbacks.buildStayPopupHtml || defaultStayPopupHtml
  const buildTripPopupHtml = callbacks.buildTripPopupHtml || defaultTripPopupHtml

  const clearHighlightedTrip = () => {
    if (!map) {
      highlightedTripPath = null
      highlightedTripStartMarker = null
      highlightedTripEndMarker = null
      highlightedTripId = null
      return
    }

    if (highlightedTripPath) {
      map.removeLayer(highlightedTripPath)
    }
    if (highlightedTripStartMarker) {
      map.removeLayer(highlightedTripStartMarker)
    }
    if (highlightedTripEndMarker) {
      map.removeLayer(highlightedTripEndMarker)
    }

    highlightedTripPath = null
    highlightedTripStartMarker = null
    highlightedTripEndMarker = null
    highlightedTripId = null
  }

  const clear = () => {
    clearHighlightedTrip()

    markerGroups.forEach((group) => {
      map?.removeLayer?.(group)
    })
    markerGroups.clear()
    markerRefs.clear()
    userPathPointsByUser.clear()
  }

  const render = ({ visibleTimelines = [] } = {}) => {
    if (!map) {
      return
    }

    clear()

    const allBounds = []

    visibleTimelines.forEach((userTimeline) => {
      const userId = userTimeline?.userId
      if (!userId) {
        return
      }

      const color = userTimeline?.assignedColor || '#3b82f6'
      const layerGroup = L.layerGroup()
      const pathSegments = normalizePathSegmentsToLeafletTuples(userTimeline?.pathSegments)

      pathSegments.forEach((segmentCoords) => {
        const pathLine = L.polyline(segmentCoords, {
          color,
          weight: 4,
          opacity: 0.75,
          smoothFactor: 1
        })
        layerGroup.addLayer(pathLine)
        segmentCoords.forEach((coord) => allBounds.push(coord))
      })

      const stays = Array.isArray(userTimeline?.timeline?.stays)
        ? userTimeline.timeline.stays
        : []

      stays.forEach((stay) => {
        const stayLatLng = toLeafletLatLngTuple(stay)
        if (!stayLatLng) {
          return
        }

        const marker = L.marker(stayLatLng, {
          icon: createStayMarkerIcon(color)
        })

        marker.bindPopup(buildStayPopupHtml(userTimeline, stay, color))

        const stayId = toStayId(userId, stay)
        markerRefs.set(stayId, marker)
        layerGroup.addLayer(marker)
        allBounds.push(stayLatLng)
      })

      userPathPointsByUser.set(userId, normalizePathPoints(userTimeline?.pathSegments))
      layerGroup.addTo(map)
      markerGroups.set(userId, layerGroup)
    })

    if (allBounds.length > 0) {
      map.fitBounds(allBounds, { padding: [50, 50] })
    }
  }

  const focusOnStay = (item) => {
    const stayId = toStayId(item?.userId, item)
    const marker = markerRefs.get(stayId)
    if (!marker) {
      return
    }

    const selectedLatLng = toLeafletLatLngTuple(item)
    if (selectedLatLng) {
      map?.setView?.(selectedLatLng, 15, { animate: true })
    }

    marker.openPopup()
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

    const tripPath = L.polyline(tripCoords, {
      color: '#ff6b6b',
      weight: 6,
      opacity: 1,
      dashArray: '10, 5'
    })

    tripPath.bindPopup(buildTripPopupHtml(item))
    tripPath.addTo(map)
    tripPath.bringToFront()

    highlightedTripPath = tripPath
    highlightedTripId = tripId

    if (startPoint && endPoint) {
      const sameEndpoint = areSameCoordinate(startPoint, endPoint)

      const startMarker = createHighlightedPathStartMarker(
        startPoint.latitude,
        startPoint.longitude,
        true,
        sameEndpoint ? { transform: 'translateX(-14px)' } : {}
      )
      const endMarker = createHighlightedPathEndMarker(
        endPoint.latitude,
        endPoint.longitude,
        true,
        sameEndpoint ? { transform: 'translateX(14px)' } : {}
      )

      startMarker.bindPopup('<div style="font-family: sans-serif; font-weight: 600;">Trip Start</div>')
      endMarker.bindPopup('<div style="font-family: sans-serif; font-weight: 600;">Trip End</div>')

      if (sameEndpoint) {
        startMarker.setZIndexOffset(20)
        endMarker.setZIndexOffset(10)
      } else {
        startMarker.setZIndexOffset(10)
        endMarker.setZIndexOffset(10)
      }

      startMarker.addTo(map)
      endMarker.addTo(map)
      highlightedTripStartMarker = startMarker
      highlightedTripEndMarker = endMarker
    }

    const highlightBounds = L.latLngBounds(tripCoords)
    if (startPoint) {
      highlightBounds.extend([startPoint.latitude, startPoint.longitude])
    }
    if (endPoint) {
      highlightBounds.extend([endPoint.latitude, endPoint.longitude])
    }

    map.fitBounds(highlightBounds, { padding: [50, 50], animate: true })
    tripPath.openPopup()
  }

  const focusOnItem = (item) => {
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
    map = null
  }

  return {
    initialize(mapInstance) {
      map = mapInstance
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
