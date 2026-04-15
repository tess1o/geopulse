import { haversineDistanceMeters } from '@/utils/geoDistance'

const DEFAULT_ROLLING_WINDOW_HALF_SPAN_MS = 60 * 1000
const RED_SPEED_THRESHOLD_KMH = 10
const YELLOW_SPEED_THRESHOLD_KMH = 25

export const HIGHLIGHTED_TRIP_SPEED_BANDS = {
  RED: 'red',
  YELLOW: 'yellow',
  GREEN: 'green',
  UNKNOWN: 'unknown'
}

export const HIGHLIGHTED_TRIP_SPEED_BAND_COLORS = {
  [HIGHLIGHTED_TRIP_SPEED_BANDS.RED]: '#ef4444',
  [HIGHLIGHTED_TRIP_SPEED_BANDS.YELLOW]: '#f59e0b',
  [HIGHLIGHTED_TRIP_SPEED_BANDS.GREEN]: '#22c55e',
  [HIGHLIGHTED_TRIP_SPEED_BANDS.UNKNOWN]: '#f59e0b'
}

const toFiniteNumber = (value) => {
  if (value === null || value === undefined || value === '') {
    return null
  }

  const parsed = Number(value)
  return Number.isFinite(parsed) ? parsed : null
}

const toTimestampMs = (point) => {
  if (Number.isFinite(point?._timestampMs)) {
    return point._timestampMs
  }

  if (!point?.timestamp) {
    return null
  }

  const parsed = Date.parse(point.timestamp)
  return Number.isNaN(parsed) ? null : parsed
}

const normalizeTripPoint = (point) => {
  if (!point || typeof point !== 'object') {
    return null
  }

  const latitude = toFiniteNumber(point.latitude)
  const longitude = toFiniteNumber(point.longitude)
  if (latitude === null || longitude === null) {
    return null
  }

  return {
    ...point,
    latitude,
    longitude
  }
}

const resolvePairwiseSegmentSpeedKmh = (startPoint, endPoint) => {
  const startTimestampMs = toTimestampMs(startPoint)
  const endTimestampMs = toTimestampMs(endPoint)
  if (!Number.isFinite(startTimestampMs) || !Number.isFinite(endTimestampMs)) {
    return null
  }

  const deltaMs = endTimestampMs - startTimestampMs
  if (deltaMs <= 0) {
    return null
  }

  const distanceMeters = haversineDistanceMeters(startPoint, endPoint)
  if (!Number.isFinite(distanceMeters) || distanceMeters < 0) {
    return null
  }

  return (distanceMeters / (deltaMs / 1000)) * 3.6
}

const resolveRollingWindowSegmentSpeedKmh = (
  points,
  segmentStartIndex,
  segmentEndIndex,
  windowHalfSpanMs = DEFAULT_ROLLING_WINDOW_HALF_SPAN_MS
) => {
  const segmentStartTs = toTimestampMs(points[segmentStartIndex])
  const segmentEndTs = toTimestampMs(points[segmentEndIndex])
  if (!Number.isFinite(segmentStartTs) || !Number.isFinite(segmentEndTs)) {
    return null
  }

  const midpointTs = Math.round((segmentStartTs + segmentEndTs) / 2)
  const windowStart = midpointTs - windowHalfSpanMs
  const windowEnd = midpointTs + windowHalfSpanMs

  const candidateIndices = []
  for (let index = 0; index < points.length; index += 1) {
    const timestampMs = toTimestampMs(points[index])
    if (!Number.isFinite(timestampMs)) {
      continue
    }
    if (timestampMs >= windowStart && timestampMs <= windowEnd) {
      candidateIndices.push(index)
    }
  }

  if (candidateIndices.length < 2) {
    return null
  }

  let distanceMeters = 0
  let deltaSeconds = 0

  for (let i = 0; i < candidateIndices.length - 1; i += 1) {
    const leftIndex = candidateIndices[i]
    const rightIndex = candidateIndices[i + 1]
    const leftPoint = points[leftIndex]
    const rightPoint = points[rightIndex]
    const leftTs = toTimestampMs(leftPoint)
    const rightTs = toTimestampMs(rightPoint)
    if (!Number.isFinite(leftTs) || !Number.isFinite(rightTs)) {
      continue
    }

    const pairDeltaSeconds = (rightTs - leftTs) / 1000
    if (pairDeltaSeconds <= 0) {
      continue
    }

    const pairDistanceMeters = haversineDistanceMeters(leftPoint, rightPoint)
    if (!Number.isFinite(pairDistanceMeters) || pairDistanceMeters < 0) {
      continue
    }

    deltaSeconds += pairDeltaSeconds
    distanceMeters += pairDistanceMeters
  }

  if (deltaSeconds <= 0) {
    return null
  }

  return (distanceMeters / deltaSeconds) * 3.6
}

export const classifyHighlightedTripSpeedBand = (speedKmh) => {
  const resolvedSpeed = toFiniteNumber(speedKmh)
  if (resolvedSpeed === null) {
    return HIGHLIGHTED_TRIP_SPEED_BANDS.UNKNOWN
  }

  if (resolvedSpeed < RED_SPEED_THRESHOLD_KMH) {
    return HIGHLIGHTED_TRIP_SPEED_BANDS.RED
  }
  if (resolvedSpeed <= YELLOW_SPEED_THRESHOLD_KMH) {
    return HIGHLIGHTED_TRIP_SPEED_BANDS.YELLOW
  }

  return HIGHLIGHTED_TRIP_SPEED_BANDS.GREEN
}

export const buildHighlightedTripSegments = (
  tripPathPoints,
  { windowHalfSpanMs = DEFAULT_ROLLING_WINDOW_HALF_SPAN_MS } = {}
) => {
  const points = (Array.isArray(tripPathPoints) ? tripPathPoints : [])
    .map((point) => normalizeTripPoint(point))
    .filter(Boolean)

  if (points.length < 2) {
    return {
      segments: []
    }
  }

  const resolvedWindowHalfSpanMs = (
    Number.isFinite(windowHalfSpanMs) && windowHalfSpanMs >= 0
      ? windowHalfSpanMs
      : DEFAULT_ROLLING_WINDOW_HALF_SPAN_MS
  )

  const segments = []
  for (let index = 0; index < points.length - 1; index += 1) {
    const startPoint = points[index]
    const endPoint = points[index + 1]

    const rollingWindowSpeedKmh = resolveRollingWindowSegmentSpeedKmh(
      points,
      index,
      index + 1,
      resolvedWindowHalfSpanMs
    )
    const pairwiseFallbackSpeedKmh = resolvePairwiseSegmentSpeedKmh(startPoint, endPoint)
    const speedKmh = rollingWindowSpeedKmh ?? pairwiseFallbackSpeedKmh

    const speedBand = classifyHighlightedTripSpeedBand(speedKmh)

    segments.push({
      index,
      startPoint,
      endPoint,
      coordinates: [
        [startPoint.longitude, startPoint.latitude],
        [endPoint.longitude, endPoint.latitude]
      ],
      latLngs: [
        [startPoint.latitude, startPoint.longitude],
        [endPoint.latitude, endPoint.longitude]
      ],
      speedKmh,
      speedBand,
      color: HIGHLIGHTED_TRIP_SPEED_BAND_COLORS[speedBand] || HIGHLIGHTED_TRIP_SPEED_BAND_COLORS.unknown
    })
  }

  return {
    segments
  }
}
