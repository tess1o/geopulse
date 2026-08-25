import { normalizePathPoints, reconstructTripPathPoints } from './tripPathReconstruction'

export const timestampMatchedTripSegments = (segments, startTimestamp, durationSeconds) => {
  const normalizedSegments = (segments || [])
    .filter(segment => Array.isArray(segment) && segment.length > 0)
  const totalVertices = normalizedSegments.reduce((total, segment) => total + segment.length, 0)
  if (totalVertices === 0) return []

  const parsedStartMs = new Date(startTimestamp).getTime()
  const startMs = Number.isFinite(parsedStartMs) ? parsedStartMs : 0
  const durationMs = Math.max(0, Number(durationSeconds) || 0) * 1000
  const lastVertexIndex = Math.max(1, totalVertices - 1)
  let vertexIndex = 0

  return normalizedSegments.map(segment => segment.map(point => {
    const timestamp = new Date(
      startMs + (durationMs * vertexIndex / lastVertexIndex)
    ).toISOString()
    vertexIndex += 1
    return { ...point, timestamp }
  }))
}

const pathSegments = (pathData) => {
  if (Array.isArray(pathData?.segments) && pathData.segments.length > 0) {
    return pathData.segments
  }
  return Array.isArray(pathData?.points) ? [pathData.points] : []
}

export const buildRawMapMatchingComparisonPathData = ({
  rawPathData,
  visibleTrips,
  highlightedTrip,
  highlightedTripHasMatchedPath,
  matchedTripIds
}) => {
  const rawSegments = pathSegments(rawPathData)
    .filter(segment => Array.isArray(segment) && segment.length >= 2)
  if (rawSegments.length === 0) return []

  const normalizedRawPoints = normalizePathPoints(rawSegments)
  if (normalizedRawPoints.length < 2) return []

  if (highlightedTrip) {
    if (!highlightedTripHasMatchedPath) return []
    const { points } = reconstructTripPathPoints(highlightedTrip, normalizedRawPoints)
    return points.length >= 2 ? [points] : []
  }

  const matchedTripIdSet = new Set((matchedTripIds || []).map(Number))
  if (matchedTripIdSet.size === 0) return []

  return (visibleTrips || [])
    .filter(trip => matchedTripIdSet.has(Number(trip?.id)))
    .map(trip => reconstructTripPathPoints(trip, normalizedRawPoints).points)
    .filter(points => points.length >= 2)
}

const sortSegmentsByTimestamp = (segments) => [...segments].sort((left, right) => {
  const leftTime = new Date(left?.[0]?.timestamp).getTime()
  const rightTime = new Date(right?.[0]?.timestamp).getTime()
  return (Number.isFinite(leftTime) ? leftTime : 0) - (Number.isFinite(rightTime) ? rightTime : 0)
})

const tripWindow = (trip) => {
  const startMs = new Date(trip?.timestamp).getTime()
  if (!Number.isFinite(startMs)) return null
  return {
    trip,
    id: Number(trip?.id),
    startMs,
    endMs: startMs + Math.max(0, Number(trip?.tripDuration) || 0) * 1000
  }
}

const hasCoordinates = (point) => (
  point
  && Number.isFinite(Number(point.latitude))
  && Number.isFinite(Number(point.longitude))
)

const segmentEndpoints = (segments) => {
  const validSegments = (segments || [])
    .filter(segment => Array.isArray(segment) && segment.length > 0)
  if (validSegments.length === 0) return null
  const first = validSegments[0][0]
  const lastSegment = validSegments[validSegments.length - 1]
  const last = lastSegment[lastSegment.length - 1]
  return hasCoordinates(first) && hasCoordinates(last) ? { first, last } : null
}

export const buildActiveMapMatchingPathData = ({
  rawPathData,
  visibleTrips,
  matchedSegmentsByTripId,
  settled
}) => {
  if (!rawPathData || !settled || !(matchedSegmentsByTripId instanceof Map) || matchedSegmentsByTripId.size === 0) {
    return rawPathData
  }

  const visibleTripWindows = (visibleTrips || [])
    .map(tripWindow)
    .filter(Boolean)
    .sort((left, right) => left.startMs - right.startMs)
  const matchedSegments = []
  const renderedTripEndpoints = new Map()
  visibleTripWindows.forEach(({ trip, id }) => {
    const tripSegments = matchedSegmentsByTripId.get(id)
    if (!tripSegments) return

    const timestampedSegments = timestampMatchedTripSegments(
      tripSegments,
      trip.timestamp,
      trip.tripDuration
    )
    const endpoints = segmentEndpoints(timestampedSegments)
    if (endpoints) {
      renderedTripEndpoints.set(id, endpoints)
    }
    matchedSegments.push(...timestampedSegments)
  })

  if (matchedSegments.length === 0) return rawPathData

  const matchedTripIdSet = new Set(Array.from(matchedSegmentsByTripId.keys()).map(Number))
  const normalizedRawPoints = normalizePathPoints(pathSegments(rawPathData))
  const rawFallbackSegments = []
  visibleTripWindows
    .filter(({ id }) => !matchedTripIdSet.has(id))
    .forEach(({ trip, id }) => {
      const { points } = reconstructTripPathPoints(trip, normalizedRawPoints)
      if (points.length >= 2) {
        rawFallbackSegments.push(points)
        const endpoints = segmentEndpoints([points])
        if (endpoints) {
          renderedTripEndpoints.set(id, endpoints)
        }
      }
    })

  const connectorSegments = []
  for (let index = 0; index < visibleTripWindows.length - 1; index += 1) {
    const current = visibleTripWindows[index]
    const next = visibleTripWindows[index + 1]
    if (current.endMs > next.startMs) continue

    const currentEndpoints = renderedTripEndpoints.get(current.id)
    const nextEndpoints = renderedTripEndpoints.get(next.id)
    if (!currentEndpoints || !nextEndpoints) continue

    const connectorPoints = normalizedRawPoints.filter(point => (
      point._timestampMs > current.endMs && point._timestampMs < next.startMs
    ))
    if (connectorPoints.length === 0) continue

    connectorSegments.push([
      currentEndpoints.last,
      ...connectorPoints,
      nextEndpoints.first
    ])
  }

  const segments = sortSegmentsByTimestamp([
    ...rawFallbackSegments,
    ...connectorSegments,
    ...matchedSegments
  ].filter(segment => Array.isArray(segment) && segment.length > 0))

  return {
    ...rawPathData,
    segments,
    points: segments.flat(),
    pointCount: segments.reduce((total, segment) => total + segment.length, 0)
  }
}
