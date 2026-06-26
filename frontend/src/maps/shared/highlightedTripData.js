import {
  areSameCoordinate,
  normalizePathPoints,
  reconstructTripPathPoints,
  resolveTripMarkerPoint
} from '@/utils/tripPathReconstruction'
import { buildHighlightedTripSegments } from '@/maps/shared/highlightedTripSpeedBands'

export const HIGHLIGHTED_TRIP_BACKGROUND_OPACITY = 0.25
export const HIGHLIGHTED_TRIP_LINE_WEIGHT = 6
export const HIGHLIGHTED_TRIP_RASTER_HIT_WEIGHT = 16
export const HIGHLIGHTED_TRIP_VECTOR_HIT_WEIGHT = 18
export const HIGHLIGHTED_TRIP_START_Z_INDEX = 420
export const HIGHLIGHTED_TRIP_END_Z_INDEX = 410
export const HIGHLIGHTED_TRIP_SAME_ENDPOINT_START_Z_INDEX = 421
export const HIGHLIGHTED_TRIP_SAME_ENDPOINT_END_Z_INDEX = 420
export const HIGHLIGHTED_TRIP_SAME_ENDPOINT_START_OFFSET = 'translateX(-14px)'
export const HIGHLIGHTED_TRIP_SAME_ENDPOINT_END_OFFSET = 'translateX(14px)'

export const toFiniteCoordinate = (value) => {
  const parsed = Number(value)
  return Number.isFinite(parsed) ? parsed : null
}

export const isTripItem = (item) => item?.type === 'trip'

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

export const normalizeReplayPathPoints = (tripPathPoints) => (
  (Array.isArray(tripPathPoints) ? tripPathPoints : [])
    .map((point) => {
      const latitude = toFiniteCoordinate(point?.latitude ?? point?.lat)
      const longitude = toFiniteCoordinate(point?.longitude ?? point?.lng ?? point?.lon)
      if (latitude === null || longitude === null) {
        return null
      }

      const timestamp = (
        point?.timestamp
        || point?.time
        || point?.createdAt
        || (Number.isFinite(point?._timestampMs) ? new Date(point._timestampMs).toISOString() : null)
      )

      return {
        latitude,
        longitude,
        altitude: toFiniteCoordinate(point?.altitude),
        timestamp
      }
    })
    .filter(Boolean)
)

export const buildReplayEmission = ({ trip, tripPathPoints, previousEmissionKey = '' }) => {
  if (!isTripItem(trip)) {
    return {
      changed: previousEmissionKey !== '',
      emissionKey: '',
      payload: { tripKey: '', points: [] }
    }
  }

  const tripKey = getHighlightedTripKey(trip)
  const replayPathPoints = normalizeReplayPathPoints(tripPathPoints)
  const firstPoint = replayPathPoints[0]
  const lastPoint = replayPathPoints[replayPathPoints.length - 1]
  const emissionKey = [
    tripKey,
    replayPathPoints.length,
    firstPoint?.timestamp || '',
    lastPoint?.timestamp || ''
  ].join('|')

  return {
    changed: previousEmissionKey !== emissionKey,
    emissionKey,
    payload: {
      tripKey,
      points: replayPathPoints
    }
  }
}

const buildEndpointFallbackPath = (trip) => {
  const startLat = toFiniteCoordinate(trip?.latitude)
  const startLon = toFiniteCoordinate(trip?.longitude)
  const endLat = toFiniteCoordinate(trip?.endLatitude)
  const endLon = toFiniteCoordinate(trip?.endLongitude)

  if ([startLat, startLon, endLat, endLon].some((value) => value === null)) {
    return []
  }

  return [
    {
      latitude: startLat,
      longitude: startLon,
      timestamp: trip?.timestamp || null
    },
    {
      latitude: endLat,
      longitude: endLon,
      timestamp: null
    }
  ]
}

export const resolveHighlightedTripPoints = ({
  highlightedTrip,
  pathData,
  allowPathDataFallback = false
}) => {
  if (!isTripItem(highlightedTrip)) {
    return []
  }

  const normalizedPath = normalizePathPoints(pathData)
  const { points: reconstructedPoints } = reconstructTripPathPoints(highlightedTrip, normalizedPath)
  if (reconstructedPoints?.length >= 2) {
    return reconstructedPoints
  }

  if (allowPathDataFallback && normalizedPath.length >= 2) {
    return normalizedPath
  }

  return buildEndpointFallbackPath(highlightedTrip)
}

export const createEmptyHighlightedTripData = () => ({
  endpointMarkers: [],
  hoverPathPoints: [],
  lineCoordinates: [],
  replayPathPoints: [],
  renderedTripPoints: [],
  highlightedSegments: { segments: [] },
  sameEndpoint: false
})

export const buildHighlightedTripData = ({
  highlightedTrip,
  pathData,
  allowPathDataFallback = false
}) => {
  const tripPoints = resolveHighlightedTripPoints({
    highlightedTrip,
    pathData,
    allowPathDataFallback
  })

  if (!tripPoints || tripPoints.length < 2) {
    return createEmptyHighlightedTripData()
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
    Object.assign(renderedTripPoints[0], {
      latitude: startPoint.latitude,
      longitude: startPoint.longitude
    })
    Object.assign(hoverPathPoints[0], {
      latitude: startPoint.latitude,
      longitude: startPoint.longitude
    })
  }

  if (endPoint && !areSameCoordinate(renderedTripPoints[renderedTripPoints.length - 1], endPoint)) {
    lineCoordinates[lineCoordinates.length - 1] = [endPoint.longitude, endPoint.latitude]
    Object.assign(renderedTripPoints[renderedTripPoints.length - 1], {
      latitude: endPoint.latitude,
      longitude: endPoint.longitude
    })
    Object.assign(hoverPathPoints[hoverPathPoints.length - 1], {
      latitude: endPoint.latitude,
      longitude: endPoint.longitude
    })
  }

  const sameEndpoint = Boolean(startPoint && endPoint && areSameCoordinate(startPoint, endPoint))
  const endpointMarkers = []

  if (startPoint) {
    endpointMarkers.push({
      markerType: 'start',
      latitude: startPoint.latitude,
      longitude: startPoint.longitude,
      zIndex: sameEndpoint ? HIGHLIGHTED_TRIP_SAME_ENDPOINT_START_Z_INDEX : HIGHLIGHTED_TRIP_START_Z_INDEX,
      styleOverrides: sameEndpoint ? { transform: HIGHLIGHTED_TRIP_SAME_ENDPOINT_START_OFFSET } : {}
    })
  }

  if (endPoint) {
    endpointMarkers.push({
      markerType: 'end',
      latitude: endPoint.latitude,
      longitude: endPoint.longitude,
      zIndex: sameEndpoint ? HIGHLIGHTED_TRIP_SAME_ENDPOINT_END_Z_INDEX : HIGHLIGHTED_TRIP_END_Z_INDEX,
      styleOverrides: sameEndpoint ? { transform: HIGHLIGHTED_TRIP_SAME_ENDPOINT_END_OFFSET } : {}
    })
  }

  return {
    endpointMarkers,
    hoverPathPoints,
    lineCoordinates,
    replayPathPoints: renderedTripPoints,
    renderedTripPoints,
    highlightedSegments: buildHighlightedTripSegments(renderedTripPoints),
    sameEndpoint
  }
}
