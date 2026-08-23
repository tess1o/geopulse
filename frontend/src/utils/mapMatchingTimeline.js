import { normalizePathPoints, reconstructTripPathPoints } from './tripPathReconstruction'

export const MAP_MATCHING_BATCH_SIZE = 100
export const TERMINAL_MAP_MATCHING_STATUSES = new Set([
  'COMPLETED',
  'FAILED',
  'SKIPPED',
  'UNAVAILABLE'
])

export const chunkMapMatchingIds = (values, size = MAP_MATCHING_BATCH_SIZE) => {
  const chunks = []
  for (let index = 0; index < values.length; index += size) {
    chunks.push(values.slice(index, index + size))
  }
  return chunks
}

export const mergeMapMatchingTripResolutions = (existingTrips, incomingTrips) => {
  const byTripId = new Map((existingTrips || []).map(trip => [Number(trip.tripId), trip]))
  ;(incomingTrips || []).forEach((trip) => {
    byTripId.set(Number(trip.tripId), { ...byTripId.get(Number(trip.tripId)), ...trip })
  })
  return Array.from(byTripId.values())
}

export const areVisibleMapMatchingTripsSettled = (visibleTripIds, resolvedTrips) => {
  if (!Array.isArray(visibleTripIds) || visibleTripIds.length === 0) {
    return false
  }

  const statusesByTripId = new Map(
    (resolvedTrips || []).map(trip => [Number(trip.tripId), trip.status])
  )
  return visibleTripIds.every(tripId => (
    TERMINAL_MAP_MATCHING_STATUSES.has(statusesByTripId.get(Number(tripId)))
  ))
}

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

export const buildActiveMapMatchingPathData = ({
  rawPathData,
  visibleTrips,
  matchedSegmentsByTripId,
  settled
}) => {
  if (!rawPathData || !settled || !(matchedSegmentsByTripId instanceof Map) || matchedSegmentsByTripId.size === 0) {
    return rawPathData
  }

  const matchedWindows = []
  const matchedSegments = []
  ;(visibleTrips || []).forEach(trip => {
    const tripSegments = matchedSegmentsByTripId.get(Number(trip?.id))
    if (!tripSegments) return

    const startMs = new Date(trip.timestamp).getTime()
    const endMs = startMs + Math.max(0, Number(trip.tripDuration) || 0) * 1000
    if (Number.isFinite(startMs) && Number.isFinite(endMs)) {
      matchedWindows.push({ startMs, endMs })
    }
    matchedSegments.push(...timestampMatchedTripSegments(
      tripSegments,
      trip.timestamp,
      trip.tripDuration
    ))
  })

  if (matchedSegments.length === 0) return rawPathData

  const rawFallbackSegments = []
  pathSegments(rawPathData).forEach(segment => {
    let current = []
    ;(segment || []).forEach(point => {
      const pointMs = new Date(point?.timestamp).getTime()
      const replacedByMatch = Number.isFinite(pointMs) && matchedWindows.some(window => (
        pointMs >= window.startMs && pointMs <= window.endMs
      ))
      if (replacedByMatch) {
        if (current.length > 0) rawFallbackSegments.push(current)
        current = []
      } else {
        current.push(point)
      }
    })
    if (current.length > 0) rawFallbackSegments.push(current)
  })

  const segments = sortSegmentsByTimestamp([
    ...rawFallbackSegments,
    ...matchedSegments
  ].filter(segment => Array.isArray(segment) && segment.length > 0))

  return {
    ...rawPathData,
    segments,
    points: segments.flat(),
    pointCount: segments.reduce((total, segment) => total + segment.length, 0)
  }
}

export const getPendingMapMatchingTargets = (trips) => (
  (trips || []).filter(trip => (
    trip?.targetId && (trip.status === 'QUEUED' || trip.status === 'PROCESSING')
  ))
)

export const getMapMatchingPollDelay = (pendingTrips, attempt) => {
  const serverDelays = (pendingTrips || [])
    .map(trip => Number(trip.pollAfterMs))
    .filter(delay => Number.isFinite(delay) && delay > 0)
  const clientDelay = Math.min(15000, 2500 * Math.pow(1.5, Math.max(0, attempt || 0)))
  return serverDelays.length > 0 ? Math.min(...serverDelays) : clientDelay
}
