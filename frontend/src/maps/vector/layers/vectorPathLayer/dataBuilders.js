import { createFeatureCollection, toFiniteNumber } from '@/maps/vector/utils/maplibreLayerUtils'
import {
  normalizePathPoints,
  reconstructTripPathPoints,
  resolveTripMarkerPoint,
  areSameCoordinate
} from '@/utils/tripPathReconstruction'
import {
  buildHighlightedTripSegments,
  HIGHLIGHTED_TRIP_SPEED_BAND_COLORS
} from '@/maps/shared/highlightedTripSpeedBands'

const createEmptyHighlightedData = () => ({
  lineCollection: createFeatureCollection([]),
  endpointMarkers: [],
  hoverPathPoints: [],
  fullLineCoordinates: [],
  replayPathPoints: []
})

export const getHighlightedTripKey = (trip) => {
  if (!trip) {
    return ''
  }

  if (trip.id) {
    return String(trip.id)
  }

  return [
    trip.timestamp,
    trip.latitude,
    trip.longitude,
    trip.endLatitude,
    trip.endLongitude,
    trip.tripDuration,
    trip.distanceMeters
  ].join('|')
}

export const normalizePathCoordinates = (pathGroup) => {
  if (!Array.isArray(pathGroup)) {
    return []
  }

  return pathGroup
    .map((point) => {
      const latitude = toFiniteNumber(point?.latitude)
      const longitude = toFiniteNumber(point?.longitude)

      if (latitude === null || longitude === null) {
        return null
      }

      return [longitude, latitude]
    })
    .filter(Boolean)
}

export const buildPathCollection = (pathData) => {
  const features = []

  ;(Array.isArray(pathData) ? pathData : []).forEach((pathGroup, pathIndex) => {
    const coordinates = normalizePathCoordinates(pathGroup)
    if (coordinates.length < 2) {
      return
    }

    features.push({
      type: 'Feature',
      geometry: {
        type: 'LineString',
        coordinates
      },
      properties: {
        pathIndex,
        pathRaw: JSON.stringify(pathGroup || [])
      }
    })
  })

  return createFeatureCollection(features)
}

const resolveTripPoints = ({ highlightedTrip, pathData }) => {
  if (!highlightedTrip) {
    return null
  }

  const normalizedPath = normalizePathPoints(pathData)
  const { points: reconstructedPoints } = reconstructTripPathPoints(highlightedTrip, normalizedPath)

  if (reconstructedPoints?.length >= 2) {
    return reconstructedPoints
  }

  const startLat = toFiniteNumber(highlightedTrip?.latitude)
  const startLon = toFiniteNumber(highlightedTrip?.longitude)
  const endLat = toFiniteNumber(highlightedTrip?.endLatitude)
  const endLon = toFiniteNumber(highlightedTrip?.endLongitude)

  if ([startLat, startLon, endLat, endLon].every((value) => value !== null)) {
    return [
      { latitude: startLat, longitude: startLon },
      { latitude: endLat, longitude: endLon }
    ]
  }

  return null
}

export const buildHighlightedData = ({ highlightedTrip, pathData }) => {
  if (!highlightedTrip || highlightedTrip.type !== 'trip') {
    return createEmptyHighlightedData()
  }

  const tripPoints = resolveTripPoints({ highlightedTrip, pathData })
  if (!tripPoints || tripPoints.length < 2) {
    return createEmptyHighlightedData()
  }

  const renderedTripPoints = tripPoints.map((point) => ({ ...point }))
  const hoverPathPoints = renderedTripPoints.map((point) => ({
    latitude: point.latitude,
    longitude: point.longitude,
    timestamp: point.timestamp || null
  }))

  const lineCoordinates = renderedTripPoints.map((point) => [point.longitude, point.latitude])

  const startPoint = resolveTripMarkerPoint(highlightedTrip, 'start', {
    latitude: renderedTripPoints[0].latitude,
    longitude: renderedTripPoints[0].longitude
  })

  const endPoint = resolveTripMarkerPoint(highlightedTrip, 'end', {
    latitude: renderedTripPoints[renderedTripPoints.length - 1].latitude,
    longitude: renderedTripPoints[renderedTripPoints.length - 1].longitude
  })

  if (startPoint && !areSameCoordinate(renderedTripPoints[0], startPoint)) {
    lineCoordinates[0] = [startPoint.longitude, startPoint.latitude]
    renderedTripPoints[0] = {
      ...renderedTripPoints[0],
      latitude: startPoint.latitude,
      longitude: startPoint.longitude
    }
    hoverPathPoints[0] = {
      ...hoverPathPoints[0],
      latitude: startPoint.latitude,
      longitude: startPoint.longitude
    }
  }

  if (endPoint && !areSameCoordinate(renderedTripPoints[renderedTripPoints.length - 1], endPoint)) {
    lineCoordinates[lineCoordinates.length - 1] = [endPoint.longitude, endPoint.latitude]
    renderedTripPoints[renderedTripPoints.length - 1] = {
      ...renderedTripPoints[renderedTripPoints.length - 1],
      latitude: endPoint.latitude,
      longitude: endPoint.longitude
    }
    hoverPathPoints[hoverPathPoints.length - 1] = {
      ...hoverPathPoints[hoverPathPoints.length - 1],
      latitude: endPoint.latitude,
      longitude: endPoint.longitude
    }
  }

  const sameEndpoint = Boolean(startPoint && endPoint && areSameCoordinate(startPoint, endPoint))
  const endpointMarkers = []

  if (startPoint) {
    endpointMarkers.push({
      markerType: 'start',
      latitude: startPoint.latitude,
      longitude: startPoint.longitude,
      zIndex: sameEndpoint ? 421 : 420,
      styleOverrides: sameEndpoint ? { transform: 'translateX(-14px)' } : {}
    })
  }

  if (endPoint) {
    endpointMarkers.push({
      markerType: 'end',
      latitude: endPoint.latitude,
      longitude: endPoint.longitude,
      zIndex: sameEndpoint ? 420 : 410,
      styleOverrides: sameEndpoint ? { transform: 'translateX(14px)' } : {}
    })
  }

  const highlightedSegments = buildHighlightedTripSegments(renderedTripPoints)
  const lineCollection = createFeatureCollection(highlightedSegments.segments.map((segment) => ({
    type: 'Feature',
    geometry: {
      type: 'LineString',
      coordinates: segment.coordinates
    },
    properties: {
      speedBand: segment.speedBand,
      tripRaw: JSON.stringify(highlightedTrip || {})
    }
  })))

  return {
    lineCollection,
    endpointMarkers,
    hoverPathPoints,
    fullLineCoordinates: lineCoordinates,
    replayPathPoints: renderedTripPoints
  }
}

export const normalizeReplayPathPoints = (tripPathPoints) => (
  (Array.isArray(tripPathPoints) ? tripPathPoints : [])
    .map((point) => {
      const latitude = toFiniteNumber(point?.latitude)
      const longitude = toFiniteNumber(point?.longitude)
      if (latitude === null || longitude === null) {
        return null
      }

      const timestamp = (
        point?.timestamp
        || (Number.isFinite(point?._timestampMs) ? new Date(point._timestampMs).toISOString() : null)
      )

      return {
        latitude,
        longitude,
        altitude: toFiniteNumber(point?.altitude),
        timestamp
      }
    })
    .filter(Boolean)
)

export const resolvePopupAnchorCoordinate = (lineCoordinates) => {
  if (!Array.isArray(lineCoordinates) || lineCoordinates.length === 0) {
    return null
  }

  return lineCoordinates[Math.floor(lineCoordinates.length / 2)] || lineCoordinates[0]
}

export const highlightedLineColorExpression = [
  'match',
  ['get', 'speedBand'],
  'red', HIGHLIGHTED_TRIP_SPEED_BAND_COLORS.red,
  'yellow', HIGHLIGHTED_TRIP_SPEED_BAND_COLORS.yellow,
  'green', HIGHLIGHTED_TRIP_SPEED_BAND_COLORS.green,
  'unknown', HIGHLIGHTED_TRIP_SPEED_BAND_COLORS.unknown,
  HIGHLIGHTED_TRIP_SPEED_BAND_COLORS.unknown
]
