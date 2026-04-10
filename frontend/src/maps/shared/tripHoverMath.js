const DEFAULT_EXACT_HOVER_THRESHOLD_PX = 10

const clamp01 = (value) => Math.min(1, Math.max(0, value))

const toFiniteNumber = (value) => {
  const parsed = Number(value)
  return Number.isFinite(parsed) ? parsed : null
}

const toPointTimestampMs = (point, timeApi = {}) => {
  if (typeof timeApi.toTimestampMs === 'function') {
    const result = timeApi.toTimestampMs(point)
    return Number.isFinite(result) ? result : null
  }

  if (Number.isFinite(point?._timestampMs)) {
    return point._timestampMs
  }

  if (!point?.timestamp) {
    return null
  }

  const parsed = Date.parse(point.timestamp)
  return Number.isNaN(parsed) ? null : parsed
}

const toRadians = (value) => value * (Math.PI / 180)

const haversineDistanceMeters = (left, right) => {
  const earthRadius = 6371000
  const lat1 = toRadians(left.latitude)
  const lat2 = toRadians(right.latitude)
  const deltaLat = toRadians(right.latitude - left.latitude)
  const deltaLon = toRadians(right.longitude - left.longitude)

  const a = (
    Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2)
    + Math.cos(lat1) * Math.cos(lat2) * Math.sin(deltaLon / 2) * Math.sin(deltaLon / 2)
  )
  const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
  return earthRadius * c
}

const normalizeGeoPoint = (point) => {
  if (!point || typeof point !== 'object') {
    return null
  }

  const latitude = toFiniteNumber(point.latitude ?? point.lat)
  const longitude = toFiniteNumber(point.longitude ?? point.lng ?? point.lon)
  if (latitude === null || longitude === null) {
    return null
  }

  return {
    latitude,
    longitude,
    timestamp: point.timestamp || null,
    _raw: point
  }
}

const normalizeProjectedPoint = (point) => {
  if (!point || typeof point !== 'object') {
    return null
  }

  const x = toFiniteNumber(point.x)
  const y = toFiniteNumber(point.y)
  if (x === null || y === null) {
    return null
  }

  return { x, y }
}

export const buildTripHoverContext = (pathPoints, distanceApi = {}, timeApi = {}) => {
  const points = (Array.isArray(pathPoints) ? pathPoints : [])
    .map((point) => normalizeGeoPoint(point))
    .filter(Boolean)

  if (points.length < 2) {
    return null
  }

  const distanceFn = typeof distanceApi === 'function'
    ? distanceApi
    : (typeof distanceApi.distance === 'function' ? distanceApi.distance : haversineDistanceMeters)

  const timestampsMs = points.map((point) => toPointTimestampMs(point, timeApi))
  const cumulativeDistancesMeters = []
  let distanceFromStartMeters = 0

  for (let index = 0; index < points.length; index += 1) {
    if (index > 0) {
      distanceFromStartMeters += distanceFn(points[index - 1], points[index])
    }
    cumulativeDistancesMeters.push(distanceFromStartMeters)
  }

  return {
    points,
    timestampsMs,
    cumulativeDistancesMeters,
    projectedPoints: [],
    segments: []
  }
}

export const projectTripHoverContext = (context, projectionApi = {}) => {
  if (!context || typeof projectionApi.project !== 'function') {
    return
  }

  context.projectedPoints = context.points
    .map((point) => normalizeProjectedPoint(projectionApi.project(point)))
    .filter(Boolean)
  context.segments = []

  for (let index = 0; index < context.projectedPoints.length - 1; index += 1) {
    const startProjected = context.projectedPoints[index]
    const endProjected = context.projectedPoints[index + 1]
    const deltaX = endProjected.x - startProjected.x
    const deltaY = endProjected.y - startProjected.y
    const segmentLengthSq = deltaX * deltaX + deltaY * deltaY
    const segmentLengthMeters = context.cumulativeDistancesMeters[index + 1] - context.cumulativeDistancesMeters[index]

    context.segments.push({
      index,
      startProjected,
      deltaX,
      deltaY,
      segmentLengthSq,
      segmentLengthMeters
    })
  }
}

export const resolveTripHoverTiming = (context, cursorPoint, projectionApi = {}) => {
  if (!context || !context.projectedPoints?.length) {
    return null
  }

  const projectedCursor = normalizeProjectedPoint(
    typeof projectionApi.toProjectedPoint === 'function'
      ? projectionApi.toProjectedPoint(cursorPoint)
      : cursorPoint
  )
  if (!projectedCursor) {
    return null
  }

  const exactThresholdPx = Number.isFinite(projectionApi.exactThresholdPx)
    ? projectionApi.exactThresholdPx
    : DEFAULT_EXACT_HOVER_THRESHOLD_PX
  const exactThresholdSq = exactThresholdPx * exactThresholdPx

  let nearestExact = null
  for (let index = 0; index < context.projectedPoints.length; index += 1) {
    const timestampMs = context.timestampsMs[index]
    if (!Number.isFinite(timestampMs)) continue

    const projected = context.projectedPoints[index]
    const distanceSq = (
      (projectedCursor.x - projected.x) * (projectedCursor.x - projected.x) +
      (projectedCursor.y - projected.y) * (projectedCursor.y - projected.y)
    )

    if (!nearestExact || distanceSq < nearestExact.distanceSq) {
      nearestExact = {
        distanceSq,
        timeMs: timestampMs,
        distanceFromStartMeters: context.cumulativeDistancesMeters[index],
        snappedPoint: {
          latitude: context.points[index].latitude,
          longitude: context.points[index].longitude
        }
      }
    }
  }

  if (nearestExact && nearestExact.distanceSq <= exactThresholdSq) {
    return {
      ...nearestExact,
      mode: 'exact'
    }
  }

  let bestSegmentMatch = null

  for (let index = 0; index < context.segments.length; index += 1) {
    const segment = context.segments[index]
    const projectionFactor = segment.segmentLengthSq > 0
      ? clamp01(
        (
          ((projectedCursor.x - segment.startProjected.x) * segment.deltaX) +
          ((projectedCursor.y - segment.startProjected.y) * segment.deltaY)
        ) / segment.segmentLengthSq
      )
      : 0

    const projectedX = segment.startProjected.x + projectionFactor * segment.deltaX
    const projectedY = segment.startProjected.y + projectionFactor * segment.deltaY
    const distanceSq = (
      (projectedCursor.x - projectedX) * (projectedCursor.x - projectedX) +
      (projectedCursor.y - projectedY) * (projectedCursor.y - projectedY)
    )

    const timeA = context.timestampsMs[segment.index]
    const timeB = context.timestampsMs[segment.index + 1]
    let estimatedTimeMs = null

    if (Number.isFinite(timeA) && Number.isFinite(timeB)) {
      estimatedTimeMs = Math.round(timeA + ((timeB - timeA) * projectionFactor))
    } else if (Number.isFinite(timeA)) {
      estimatedTimeMs = timeA
    } else if (Number.isFinite(timeB)) {
      estimatedTimeMs = timeB
    }

    if (!bestSegmentMatch || distanceSq < bestSegmentMatch.distanceSq) {
      let snappedPoint = {
        latitude: context.points[segment.index].latitude,
        longitude: context.points[segment.index].longitude
      }

      if (typeof projectionApi.unproject === 'function') {
        const unprojected = projectionApi.unproject({ x: projectedX, y: projectedY })
        const latitude = toFiniteNumber(unprojected?.latitude ?? unprojected?.lat)
        const longitude = toFiniteNumber(unprojected?.longitude ?? unprojected?.lng ?? unprojected?.lon)
        if (latitude !== null && longitude !== null) {
          snappedPoint = { latitude, longitude }
        }
      }

      bestSegmentMatch = {
        distanceSq,
        timeMs: estimatedTimeMs,
        distanceFromStartMeters: context.cumulativeDistancesMeters[segment.index] + (segment.segmentLengthMeters * projectionFactor),
        snappedPoint
      }
    }
  }

  if (!Number.isFinite(bestSegmentMatch?.timeMs)) {
    return nearestExact
      ? {
        ...nearestExact,
        mode: 'exact'
      }
      : null
  }

  return {
    ...bestSegmentMatch,
    mode: 'estimated'
  }
}
