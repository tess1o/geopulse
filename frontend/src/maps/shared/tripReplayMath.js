import { haversineDistanceMeters } from '@/utils/geoDistance'

const DEFAULT_FALLBACK_DURATION_MS = 60_000
const MIN_DURATION_MS = 1

export const TRIP_REPLAY_SPEED_PRESETS = Object.freeze([4, 8, 16, 32])
export const DEFAULT_TRIP_REPLAY_SPEED = 16

const toFiniteNumber = (value) => {
  const parsed = Number(value)
  return Number.isFinite(parsed) ? parsed : null
}

const toTimestampMs = (value) => {
  if (!value) {
    return null
  }

  const parsed = Date.parse(value)
  return Number.isNaN(parsed) ? null : parsed
}

const clamp01 = (value) => Math.min(1, Math.max(0, value))

const resolveBearingDegrees = (startPoint, endPoint) => {
  const startLatitude = toFiniteNumber(startPoint?.latitude)
  const startLongitude = toFiniteNumber(startPoint?.longitude)
  const endLatitude = toFiniteNumber(endPoint?.latitude)
  const endLongitude = toFiniteNumber(endPoint?.longitude)

  if (
    startLatitude === null
    || startLongitude === null
    || endLatitude === null
    || endLongitude === null
  ) {
    return null
  }

  const startLatitudeRadians = (startLatitude * Math.PI) / 180
  const endLatitudeRadians = (endLatitude * Math.PI) / 180
  const deltaLongitudeRadians = ((endLongitude - startLongitude) * Math.PI) / 180

  const y = Math.sin(deltaLongitudeRadians) * Math.cos(endLatitudeRadians)
  const x = (
    Math.cos(startLatitudeRadians) * Math.sin(endLatitudeRadians)
    - Math.sin(startLatitudeRadians) * Math.cos(endLatitudeRadians) * Math.cos(deltaLongitudeRadians)
  )

  const bearingRadians = Math.atan2(y, x)
  const bearingDegrees = (bearingRadians * 180) / Math.PI

  return (bearingDegrees + 360) % 360
}

export const normalizeTripReplayPoints = (points) => {
  const rawPoints = Array.isArray(points) ? points : []

  return rawPoints
    .map((point, index) => {
      const latitude = toFiniteNumber(point?.latitude ?? point?.lat)
      const longitude = toFiniteNumber(point?.longitude ?? point?.lng ?? point?.lon)

      if (latitude === null || longitude === null) {
        return null
      }

      return {
        index,
        latitude,
        longitude,
        altitude: toFiniteNumber(point?.altitude),
        timestamp: point?.timestamp || null,
        timestampMs: toTimestampMs(point?.timestamp)
      }
    })
    .filter(Boolean)
}

const buildCumulativeDistances = (points) => {
  const cumulativeDistancesMeters = [0]
  let runningDistanceMeters = 0

  for (let index = 1; index < points.length; index += 1) {
    const segmentDistance = haversineDistanceMeters(points[index - 1], points[index])
    if (Number.isFinite(segmentDistance) && segmentDistance > 0) {
      runningDistanceMeters += segmentDistance
    }

    cumulativeDistancesMeters.push(runningDistanceMeters)
  }

  return cumulativeDistancesMeters
}

const resolveFallbackDurationMs = ({ preferredDurationMs, totalDistanceMeters, pointsCount }) => {
  if (Number.isFinite(preferredDurationMs) && preferredDurationMs > 0) {
    return Math.max(MIN_DURATION_MS, Math.round(preferredDurationMs))
  }

  if (Number.isFinite(totalDistanceMeters) && totalDistanceMeters > 0) {
    // Rough fallback assumption: ~72km/h average for synthetic trip replay timing.
    const estimated = (totalDistanceMeters / 20) * 1000
    return Math.max(MIN_DURATION_MS, Math.round(estimated))
  }

  if (pointsCount > 1) {
    return DEFAULT_FALLBACK_DURATION_MS
  }

  return MIN_DURATION_MS
}

export const buildTripReplayTimeline = (points, options = {}) => {
  const normalizedPoints = normalizeTripReplayPoints(points)
  if (normalizedPoints.length < 2) {
    return {
      mode: 'none',
      points: normalizedPoints,
      axisMs: [],
      cumulativeDistancesMeters: [],
      durationMs: 0,
      totalDistanceMeters: 0,
      firstTimestampMs: null
    }
  }

  const cumulativeDistancesMeters = buildCumulativeDistances(normalizedPoints)
  const totalDistanceMeters = cumulativeDistancesMeters[cumulativeDistancesMeters.length - 1] || 0

  const timestampsMs = normalizedPoints.map((point) => point.timestampMs)
  const allTimestampsDefined = timestampsMs.every((value) => Number.isFinite(value))
  const timestampsMonotonic = timestampsMs.every((value, index) => {
    if (!Number.isFinite(value)) {
      return false
    }

    if (index === 0) {
      return true
    }

    return value >= timestampsMs[index - 1]
  })

  const firstTimestampMs = allTimestampsDefined ? timestampsMs[0] : null
  const lastTimestampMs = allTimestampsDefined ? timestampsMs[timestampsMs.length - 1] : null
  const timestampSpanMs = (
    Number.isFinite(firstTimestampMs)
    && Number.isFinite(lastTimestampMs)
    && lastTimestampMs > firstTimestampMs
      ? lastTimestampMs - firstTimestampMs
      : 0
  )

  if (allTimestampsDefined && timestampsMonotonic && timestampSpanMs > 0) {
    const axisMs = timestampsMs.map((timestampMs) => timestampMs - firstTimestampMs)
    return {
      mode: 'time',
      points: normalizedPoints,
      axisMs,
      cumulativeDistancesMeters,
      durationMs: timestampSpanMs,
      totalDistanceMeters,
      firstTimestampMs
    }
  }

  const fallbackDurationMs = resolveFallbackDurationMs({
    preferredDurationMs: options.preferredDurationMs,
    totalDistanceMeters,
    pointsCount: normalizedPoints.length
  })

  const axisMs = normalizedPoints.map((point, index) => {
    if (index === 0) {
      return 0
    }

    if (index === normalizedPoints.length - 1) {
      return fallbackDurationMs
    }

    if (totalDistanceMeters > 0) {
      const distanceRatio = cumulativeDistancesMeters[index] / totalDistanceMeters
      return Math.round(distanceRatio * fallbackDurationMs)
    }

    const indexRatio = index / (normalizedPoints.length - 1)
    return Math.round(indexRatio * fallbackDurationMs)
  })

  return {
    mode: 'proportional',
    points: normalizedPoints,
    axisMs,
    cumulativeDistancesMeters,
    durationMs: fallbackDurationMs,
    totalDistanceMeters,
    firstTimestampMs: null
  }
}

export const clampTripReplayElapsed = (elapsedMs, durationMs) => {
  if (!Number.isFinite(durationMs) || durationMs <= 0) {
    return 0
  }

  if (!Number.isFinite(elapsedMs)) {
    return 0
  }

  return Math.max(0, Math.min(durationMs, elapsedMs))
}

export const advanceTripReplayElapsed = ({ elapsedMs, deltaMs, speed, durationMs }) => {
  const safeDurationMs = Number.isFinite(durationMs) && durationMs > 0 ? durationMs : 0
  if (safeDurationMs === 0) {
    return {
      elapsedMs: 0,
      ended: true
    }
  }

  const safeElapsedMs = clampTripReplayElapsed(elapsedMs, safeDurationMs)
  const safeDeltaMs = Number.isFinite(deltaMs) && deltaMs > 0 ? deltaMs : 0
  const safeSpeed = Number.isFinite(speed) && speed > 0 ? speed : 1

  const advancedElapsedMs = safeElapsedMs + (safeDeltaMs * safeSpeed)
  const clampedElapsedMs = clampTripReplayElapsed(advancedElapsedMs, safeDurationMs)
  const ended = clampedElapsedMs >= safeDurationMs

  return {
    elapsedMs: clampedElapsedMs,
    ended
  }
}

const resolveSegmentIndexAtElapsed = (axisMs, elapsedMs) => {
  let leftIndex = 0
  let rightIndex = axisMs.length - 1

  while (leftIndex <= rightIndex) {
    const middleIndex = Math.floor((leftIndex + rightIndex) / 2)
    const middleValue = axisMs[middleIndex]

    if (middleValue <= elapsedMs) {
      leftIndex = middleIndex + 1
    } else {
      rightIndex = middleIndex - 1
    }
  }

  return Math.max(0, Math.min(axisMs.length - 2, rightIndex))
}

export const resolveTripReplayCursor = (timeline, elapsedMs) => {
  if (!timeline || !Array.isArray(timeline.points) || timeline.points.length < 2) {
    return null
  }

  const durationMs = Number.isFinite(timeline.durationMs) ? timeline.durationMs : 0
  if (durationMs <= 0) {
    return null
  }

  const axisMs = Array.isArray(timeline.axisMs) ? timeline.axisMs : []
  if (axisMs.length !== timeline.points.length) {
    return null
  }

  const clampedElapsedMs = clampTripReplayElapsed(elapsedMs, durationMs)
  const firstPoint = timeline.points[0]
  const lastPoint = timeline.points[timeline.points.length - 1]
  const progress = clamp01(clampedElapsedMs / durationMs)

  if (clampedElapsedMs <= 0) {
    const bearing = resolveBearingDegrees(firstPoint, timeline.points[1])
    return {
      ...firstPoint,
      bearing,
      progress: 0,
      elapsedMs: 0,
      distanceFromStartMeters: 0,
      segmentIndex: 0,
      timeMs: timeline.mode === 'time' ? timeline.firstTimestampMs : null
    }
  }

  if (clampedElapsedMs >= durationMs) {
    const bearing = resolveBearingDegrees(timeline.points[timeline.points.length - 2], lastPoint)
    return {
      ...lastPoint,
      bearing,
      progress: 1,
      elapsedMs: durationMs,
      distanceFromStartMeters: timeline.totalDistanceMeters || 0,
      segmentIndex: timeline.points.length - 2,
      timeMs: timeline.mode === 'time' && Number.isFinite(timeline.firstTimestampMs)
        ? timeline.firstTimestampMs + durationMs
        : null
    }
  }

  const segmentIndex = resolveSegmentIndexAtElapsed(axisMs, clampedElapsedMs)
  const segmentStartMs = axisMs[segmentIndex]
  const segmentEndMs = axisMs[segmentIndex + 1]
  const segmentDurationMs = segmentEndMs - segmentStartMs
  const segmentProgress = (
    segmentDurationMs > 0
      ? clamp01((clampedElapsedMs - segmentStartMs) / segmentDurationMs)
      : 0
  )

  const startPoint = timeline.points[segmentIndex]
  const endPoint = timeline.points[segmentIndex + 1]

  const latitude = startPoint.latitude + ((endPoint.latitude - startPoint.latitude) * segmentProgress)
  const longitude = startPoint.longitude + ((endPoint.longitude - startPoint.longitude) * segmentProgress)

  const altitude = (
    Number.isFinite(startPoint.altitude) && Number.isFinite(endPoint.altitude)
      ? startPoint.altitude + ((endPoint.altitude - startPoint.altitude) * segmentProgress)
      : (Number.isFinite(startPoint.altitude) ? startPoint.altitude : endPoint.altitude)
  )

  const cumulativeDistances = Array.isArray(timeline.cumulativeDistancesMeters)
    ? timeline.cumulativeDistancesMeters
    : []
  const segmentStartDistance = cumulativeDistances[segmentIndex] || 0
  const segmentEndDistance = cumulativeDistances[segmentIndex + 1] || segmentStartDistance
  const distanceFromStartMeters = segmentStartDistance + ((segmentEndDistance - segmentStartDistance) * segmentProgress)

  const bearing = resolveBearingDegrees(startPoint, endPoint)
  const timeMs = timeline.mode === 'time' && Number.isFinite(timeline.firstTimestampMs)
    ? timeline.firstTimestampMs + clampedElapsedMs
    : null

  return {
    latitude,
    longitude,
    altitude: Number.isFinite(altitude) ? altitude : null,
    bearing,
    progress,
    elapsedMs: clampedElapsedMs,
    distanceFromStartMeters,
    segmentIndex,
    timeMs
  }
}

