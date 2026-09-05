import { haversineDistanceMeters } from '@/utils/geoDistance'
import { normalizeLatLngPoint } from '@/maps/shared/coordinateUtils'

const DEFAULT_RADIUS_METERS = 150
const DEFAULT_SINGLE_POINT_PADDING_SECONDS = 300
const MIN_STAY_SECONDS = 60
const ENDPOINT_CLUSTER_ERROR = 'Select an intermediate stop, not the trip start or end.'

export const resolveSplitSelectionRadiusMeters = (preferences = null) => {
  const configured = Number(preferences?.staypointRadiusMeters)
  return Math.max(DEFAULT_RADIUS_METERS, Number.isFinite(configured) ? configured : 0)
}

export const normalizeTripPathPoints = (points = []) => points
  .map((point, index) => {
    const coordinate = normalizeLatLngPoint(point)
    return coordinate
      ? {
          key: `${point?.timestamp || index}-${index}`,
          timestamp: point?.timestamp || null,
          latitude: coordinate.lat,
          longitude: coordinate.lng
        }
      : null
  })
  .filter((point) => point && point.timestamp)

export const normalizeTripPathSegments = (segments = [], points = []) => {
  const sourceSegments = Array.isArray(segments) && segments.length > 0 ? segments : [points]
  return sourceSegments
    .map((segment) => normalizeTripPathPoints(segment))
    .filter((segment) => segment.length >= 2)
}

export const findNearestPathPoint = (points, coordinate) => {
  const normalized = normalizeLatLngPoint(coordinate)
  if (!normalized || !Array.isArray(points) || points.length === 0) return null

  return points.reduce((nearest, point, index) => {
    const distanceMeters = haversineDistanceMeters(
      { latitude: normalized.lat, longitude: normalized.lng },
      point
    )

    if (!Number.isFinite(distanceMeters)) return nearest
    if (!nearest || distanceMeters < nearest.distanceMeters) {
      return { index, point, distanceMeters }
    }
    return nearest
  }, null)
}

export const resolveStayWindowFromSelection = (points, coordinate, options = {}) => {
  const radiusMeters = Number.isFinite(options.radiusMeters)
    ? options.radiusMeters
    : DEFAULT_RADIUS_METERS
  const nearest = findNearestPathPoint(points, coordinate)

  if (!nearest) {
    return { error: 'No trip path points are available.' }
  }

  if (nearest.distanceMeters > radiusMeters) {
    return {
      error: `Select a stay location closer to the trip path (${Math.round(radiusMeters)}m max).`,
      nearestDistanceMeters: nearest.distanceMeters,
      radiusMeters
    }
  }

  const center = nearest.point
  let startIndex = nearest.index
  let endIndex = nearest.index

  while (startIndex > 0 && haversineDistanceMeters(points[startIndex - 1], center) <= radiusMeters) {
    startIndex -= 1
  }

  while (endIndex < points.length - 1 && haversineDistanceMeters(points[endIndex + 1], center) <= radiusMeters) {
    endIndex += 1
  }

  if (startIndex === 0 || endIndex === points.length - 1) {
    return {
      error: ENDPOINT_CLUSTER_ERROR,
      nearestDistanceMeters: nearest.distanceMeters,
      radiusMeters
    }
  }

  const { startTime, endTime } = resolveBoundedInterval(
    points[startIndex]?.timestamp,
    points[endIndex]?.timestamp,
    center.timestamp,
    options
  )

  return {
    point: center,
    snappedLatitude: center.latitude,
    snappedLongitude: center.longitude,
    anchorTimestamp: center.timestamp,
    startTime,
    endTime,
    radiusMeters,
    nearestDistanceMeters: nearest.distanceMeters,
    startIndex,
    endIndex
  }
}

export const validateStayWindowSelection = (points, selectedPoint, range, options = {}) => {
  const radiusMeters = Number.isFinite(options.radiusMeters)
    ? options.radiusMeters
    : DEFAULT_RADIUS_METERS
  const anchorTime = toTime(selectedPoint?.timestamp || options.anchorTimestamp)
  const start = toTime(range?.startTime || range?.start)
  const end = toTime(range?.endTime || range?.end)
  const tripStart = toTime(options.tripStart)
  const tripEnd = toTime(options.tripEnd)

  if (!selectedPoint) return { error: 'Select the stay location on the trip map.' }
  if (!anchorTime || !start || !end) return { error: 'Select valid start and end times.' }
  if (anchorTime < start || anchorTime > end) {
    return { error: 'Selected place is not near GPS points in this time range.' }
  }
  if ((tripStart && anchorTime <= tripStart) || (tripEnd && anchorTime >= tripEnd)) {
    return { error: ENDPOINT_CLUSTER_ERROR }
  }

  const cluster = findContiguousCluster(points, selectedPoint, radiusMeters)
  if (!cluster) return { error: 'No trip path points are available.' }
  if (cluster.startIndex === 0 || cluster.endIndex === points.length - 1) {
    return { error: ENDPOINT_CLUSTER_ERROR }
  }

  const hasPointInRange = points.some((point) => {
    const pointTime = toTime(point.timestamp)
    if (!pointTime) return false
    return pointTime >= start
      && pointTime <= end
      && haversineDistanceMeters(point, selectedPoint) <= radiusMeters
  })

  return hasPointInRange
    ? { valid: true }
    : { error: 'Selected place is not near GPS points in this time range.' }
}

const findContiguousCluster = (points, center, radiusMeters) => {
  const anchorIndex = findAnchorPathIndex(points, center, radiusMeters)
  if (anchorIndex < 0) return null

  const anchorPoint = points[anchorIndex]
  let startIndex = anchorIndex
  let endIndex = anchorIndex

  while (startIndex > 0 && haversineDistanceMeters(points[startIndex - 1], anchorPoint) <= radiusMeters) {
    startIndex -= 1
  }

  while (endIndex < points.length - 1 && haversineDistanceMeters(points[endIndex + 1], anchorPoint) <= radiusMeters) {
    endIndex += 1
  }

  return { startIndex, endIndex }
}

const findAnchorPathIndex = (points, selectedPoint, radiusMeters) => {
  const anchorTime = toTime(selectedPoint?.timestamp)
  if (!Array.isArray(points) || points.length === 0 || !anchorTime) return -1

  let nearestIndex = -1
  let nearestDelta = Number.POSITIVE_INFINITY
  points.forEach((point, index) => {
    const pointTime = toTime(point.timestamp)
    if (!pointTime) return
    const delta = Math.abs(pointTime - anchorTime)
    if (delta < nearestDelta) {
      nearestDelta = delta
      nearestIndex = index
    }
  })

  if (nearestIndex < 0 || haversineDistanceMeters(points[nearestIndex], selectedPoint) > radiusMeters) {
    return -1
  }
  return nearestIndex
}

const resolveBoundedInterval = (rawStart, rawEnd, fallbackCenter, options) => {
  const minStaySeconds = Number.isFinite(options.minStaySeconds) ? options.minStaySeconds : MIN_STAY_SECONDS
  const paddingSeconds = Number.isFinite(options.singlePointPaddingSeconds)
    ? options.singlePointPaddingSeconds
    : DEFAULT_SINGLE_POINT_PADDING_SECONDS
  const tripStart = toTime(options.tripStart)
  const tripEnd = toTime(options.tripEnd)
  let start = toTime(rawStart)
  let end = toTime(rawEnd)

  if (!start || !end || end - start < minStaySeconds * 1000) {
    const center = toTime(fallbackCenter) || start || end || tripStart || tripEnd || Date.now()
    start = center - paddingSeconds * 1000
    end = center + paddingSeconds * 1000
  }

  if (tripStart) start = Math.max(start, tripStart + 1000)
  if (tripEnd) end = Math.min(end, tripEnd - 1000)

  if (end - start < minStaySeconds * 1000) {
    const center = toTime(fallbackCenter) || start
    start = center - (minStaySeconds * 1000) / 2
    end = center + (minStaySeconds * 1000) / 2
  }

  if (tripStart) start = Math.max(start, tripStart + 1000)
  if (tripEnd) end = Math.min(end, tripEnd - 1000)

  return {
    startTime: new Date(start).toISOString(),
    endTime: new Date(end).toISOString()
  }
}

const toTime = (value) => {
  const parsed = value ? new Date(value).getTime() : Number.NaN
  return Number.isFinite(parsed) ? parsed : null
}
