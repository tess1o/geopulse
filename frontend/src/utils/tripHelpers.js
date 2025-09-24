import { useTimezone } from '@/composables/useTimezone'

const timezone = useTimezone()

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