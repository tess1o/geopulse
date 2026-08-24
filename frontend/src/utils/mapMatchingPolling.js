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
