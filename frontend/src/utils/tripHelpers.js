import { useTimezone } from '@/composables/useTimezone'

const timezone = useTimezone()
const FALLBACK_TRIP_COLOR = '#0ea5e9'

const toEpochMs = (value) => {
  if (!value && value !== 0) return null

  if (value instanceof Date) {
    const time = value.getTime()
    return Number.isFinite(time) ? time : null
  }

  if (typeof value === 'number') {
    return Number.isFinite(value) ? value : null
  }

  const parsed = new Date(value).getTime()
  return Number.isFinite(parsed) ? parsed : null
}

const getTripRange = (trip) => {
  if (!trip) return null

  const startValue = trip.startTime ?? trip.start_time ?? trip.startDate ?? trip.start_date
  const endValue = trip.endTime ?? trip.end_time ?? trip.endDate ?? trip.end_date
  const startMs = toEpochMs(startValue)
  if (startMs === null) return null

  const endMs = endValue ? toEpochMs(endValue) : Number.POSITIVE_INFINITY
  if (endMs === null) return null

  return { startMs, endMs }
}

const selectBestTrip = (trips = []) => {
  if (!trips.length) return null

  return [...trips].sort((a, b) => {
    const aStart = toEpochMs(a?.startTime ?? a?.start_time ?? a?.startDate ?? a?.start_date) ?? 0
    const bStart = toEpochMs(b?.startTime ?? b?.start_time ?? b?.startDate ?? b?.start_date) ?? 0
    if (aStart !== bStart) return bStart - aStart
    return Number(b?.id || 0) - Number(a?.id || 0)
  })[0]
}

/**
 * Trip helper utilities for finding origin and destination stays
 * Shared between the table filters composable and data exporter
 */

/**
 * Find the origin stay for a trip (latest stay before trip start)
 * @param {Array} stays - Array of stay objects
 * @param {string} tripTimestamp - Trip start timestamp
 * @returns {Object|null} - Origin stay object or null if not found
 */
export const findOriginStay = (stays, tripTimestamp) => {
  if (!stays || stays.length === 0) return null
  
  const tripTime = timezone.fromUtc(tripTimestamp)
  
  return stays
    .filter(stay => {
      if (!stay.timestamp) return false
      const stayTime = timezone.fromUtc(stay.timestamp)
      const stayEndTime = stayTime.clone().add(stay.stayDuration || 0, 'seconds')
      // Stay should end before or very close to trip start (within 10 minutes tolerance)
      return stayEndTime.isBefore(tripTime.clone().add(10, 'minutes'))
    })
    .sort((a, b) => {
      const aEndTime = timezone.fromUtc(a.timestamp).add(a.stayDuration || 0, 'seconds')
      const bEndTime = timezone.fromUtc(b.timestamp).add(b.stayDuration || 0, 'seconds')
      return bEndTime.isAfter(aEndTime) ? 1 : -1
    })[0] || null
}

/**
 * Find the destination stay for a trip (earliest stay after trip end)
 * @param {Array} stays - Array of stay objects
 * @param {string} tripEndTimestamp - Trip end timestamp
 * @returns {Object|null} - Destination stay object or null if not found
 */
export const findDestinationStay = (stays, tripEndTimestamp) => {
  if (!stays || stays.length === 0) return null
  
  const tripEndTime = timezone.fromUtc(tripEndTimestamp)
  
  return stays
    .filter(stay => {
      if (!stay.timestamp) return false
      const stayTime = timezone.fromUtc(stay.timestamp)
      // Stay should start after or very close to trip end (within 10 minutes tolerance)
      return stayTime.isAfter(tripEndTime.clone().subtract(10, 'minutes'))
    })
    .sort((a, b) => {
      const aTime = timezone.fromUtc(a.timestamp)
      const bTime = timezone.fromUtc(b.timestamp)
      return aTime.isBefore(bTime) ? -1 : 1
    })[0] || null
}

export const normalizeTripColor = (color) => {
  if (!color || typeof color !== 'string') return FALLBACK_TRIP_COLOR
  if (color.startsWith('#') || color.startsWith('rgb') || color.startsWith('hsl') || color.startsWith('var(')) {
    return color
  }
  return `#${color}`
}

export const findMatchingTripForInterval = (startTime, endTime, trips = []) => {
  const visitStartMs = toEpochMs(startTime)
  if (visitStartMs === null) return null

  const visitEndMs = toEpochMs(endTime) ?? visitStartMs

  const matches = (Array.isArray(trips) ? trips : []).filter((trip) => {
    const range = getTripRange(trip)
    if (!range) return false
    return visitStartMs <= range.endMs && visitEndMs >= range.startMs
  })

  return selectBestTrip(matches)
}

export const findMatchingTripForTimestamp = (timestamp, trips = []) => {
  return findMatchingTripForInterval(timestamp, timestamp, trips)
}

export const findMatchingTripForVisit = (visit, trips = []) => {
  if (!visit?.timestamp) return null
  const startMs = toEpochMs(visit.timestamp)
  if (startMs === null) return null

  const durationSeconds = Number(visit.stayDuration || 0)
  const endMs = durationSeconds > 0 ? startMs + durationSeconds * 1000 : startMs
  return findMatchingTripForInterval(startMs, endMs, trips)
}

export const findMatchingTripForShareLink = (shareLink, trips = []) => {
  if (!shareLink) return null

  const directTripId = Number(shareLink.tripId ?? shareLink.trip_id)
  if (Number.isFinite(directTripId)) {
    const directMatch = (Array.isArray(trips) ? trips : []).find((trip) => Number(trip?.id) === directTripId)
    if (directMatch) return directMatch
  }

  const startValue = shareLink.startDate ?? shareLink.start_date
  const endValue = shareLink.endDate ?? shareLink.end_date
  if (!startValue) return null
  return findMatchingTripForInterval(startValue, endValue || startValue, trips)
}
