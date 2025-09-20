/**
 * Utility functions for handling overnight stays and timeline processing
 */
import { getUserTimezone } from '@/utils/timezoneUtils'
import { formatTime } from '@/utils/dateHelpers'

/**
 * Format date to YYYY-MM-DD string in specific timezone
 * @param {Date} date - The date to format
 * @param {string} timezone - The timezone to use
 * @returns {string} - Date in YYYY-MM-DD format
 */
export function formatDateInTimezone(date, timezone) {
  const formatter = new Intl.DateTimeFormat('en-CA', {
    timeZone: timezone,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit'
  })
  return formatter.format(date)
}

/**
 * Check if an item spans multiple days (overnight item)
 * @param {string|Date} timestamp - The start timestamp
 * @param {number} duration - Duration in seconds
 * @returns {boolean} - True if the item crosses midnight boundary
 */
export function isOvernightItem(timestamp, duration) {
  if (!timestamp || !duration) {
    return false
  }

  const startDate = new Date(timestamp)
  // Calculate end time from start time + duration (duration is in seconds)
  const endDate = new Date(startDate.getTime() + (duration * 1000))

  // Get date parts in user's timezone to check if they're on different days
  const userTimezone = getUserTimezone()
  const startDateInTz = new Date(startDate.toLocaleDateString('en-US', { timeZone: userTimezone }))
  const endDateInTz = new Date(endDate.toLocaleDateString('en-US', { timeZone: userTimezone }))

  return startDateInTz.toDateString() !== endDateInTz.toDateString()
}

/**
 * Check if a stay spans multiple days (overnight stay)
 * @param {Object} stayItem - The stay item with timestamp and stayDuration
 * @returns {boolean} - True if the stay crosses midnight boundary
 */
export function isOvernightStay(stayItem) {
  return isOvernightItem(stayItem.timestamp, stayItem.stayDuration)
}

/**
 * Check if a trip spans multiple days (overnight trip)
 * @param {Object} tripItem - The trip item with timestamp and tripDuration
 * @returns {boolean} - True if the trip crosses midnight boundary
 */
export function isOvernightTrip(tripItem) {
  // tripDuration might be in minutes for trips, convert to seconds
  const durationSeconds = tripItem.tripDuration * 60 // Convert minutes to seconds
  return isOvernightItem(tripItem.timestamp, durationSeconds)
}

/**
 * Check if a data gap spans multiple days (overnight data gap)
 * @param {Object} dataGapItem - The data gap item with startTime and endTime
 * @returns {boolean} - True if the data gap crosses midnight boundary
 */
export function isOvernightDataGap(dataGapItem) {
  if (!dataGapItem.startTime || !dataGapItem.endTime) {
    return false
  }

  const startDate = new Date(dataGapItem.startTime)
  const endDate = new Date(dataGapItem.endTime)

  // Get date parts in user's timezone to check if they're on different days
  const userTimezone = getUserTimezone()
  const startDateInTz = new Date(startDate.toLocaleDateString('en-US', { timeZone: userTimezone }))
  const endDateInTz = new Date(endDate.toLocaleDateString('en-US', { timeZone: userTimezone }))

  return startDateInTz.toDateString() !== endDateInTz.toDateString()
}

/**
 * Check if an overnight item should be shown as an overnight card for a specific date
 * @param {string|Date} itemTimestamp - The item's start timestamp
 * @param {number} itemDuration - Duration in seconds
 * @param {string} currentDateString - The date we're rendering (date key from grouping)
 * @returns {boolean} - True if this should use an overnight card
 */
export function shouldShowAsOvernightItem(itemTimestamp, itemDuration, currentDateString) {
  if (!isOvernightItem(itemTimestamp, itemDuration)) {
    return false
  }

  const currentDate = new Date(currentDateString)
  const itemStartDate = new Date(itemTimestamp)
  
  // Get date parts for comparison in user's timezone
  const userTimezone = getUserTimezone()
  const currentDateStr = formatDateInTimezone(currentDate, userTimezone)
  const itemStartDateStr = formatDateInTimezone(itemStartDate, userTimezone)

  // Show as overnight if the item started on a different day than we're currently rendering
  return currentDateStr !== itemStartDateStr
}

/**
 * Check if a stay should be shown as an overnight card for a specific date
 * @param {Object} stayItem - The stay item
 * @param {string} currentDateString - The date we're rendering (date key from grouping)
 * @returns {boolean} - True if this should use OvernightStayCard
 */
export function shouldShowAsOvernightStay(stayItem, currentDateString) {
  return shouldShowAsOvernightItem(stayItem.timestamp, stayItem.stayDuration, currentDateString)
}

/**
 * Check if a trip should be shown as an overnight card for a specific date
 * @param {Object} tripItem - The trip item
 * @param {string} currentDateString - The date we're rendering (date key from grouping)
 * @returns {boolean} - True if this should use OvernightTripCard
 */
export function shouldShowAsOvernightTrip(tripItem, currentDateString) {
  // tripDuration might be in minutes for trips, convert to seconds
  const durationSeconds = tripItem.tripDuration * 60 // Convert minutes to seconds
  return shouldShowAsOvernightItem(tripItem.timestamp, durationSeconds, currentDateString)
}

/**
 * Check if a data gap should be shown as an overnight card for a specific date
 * @param {Object} dataGapItem - The data gap item
 * @param {string} currentDateString - The date we're rendering (date key from grouping)
 * @returns {boolean} - True if this should use OvernightDataGapCard
 */
export function shouldShowAsOvernightDataGap(dataGapItem, currentDateString) {
  if (!isOvernightDataGap(dataGapItem)) {
    return false
  }

  const currentDate = new Date(currentDateString)
  const gapStartDate = new Date(dataGapItem.startTime)
  
  // Get date parts for comparison in user's timezone
  const userTimezone = getUserTimezone()
  const currentDateInTz = new Date(currentDate.toLocaleDateString('en-US', { timeZone: userTimezone }))
  const gapStartDateInTz = new Date(gapStartDate.toLocaleDateString('en-US', { timeZone: userTimezone }))

  // Show as overnight if the data gap started on a different day than we're currently rendering
  return currentDateInTz.toDateString() !== gapStartDateInTz.toDateString()
}

/**
 * Get the start of day for a given date in user's timezone
 * @param {Date|string} date - The date
 * @returns {Date} - Start of day (00:00:00) in user's timezone
 */
export function getStartOfDay(date) {
  const userTimezone = getUserTimezone()
  return getStartOfDayInTimezone(date, userTimezone)
}

/**
 * Get the end of day for a given date in user's timezone
 * @param {Date|string} date - The date
 * @returns {Date} - End of day (23:59:59.999) in user's timezone
 */
export function getEndOfDay(date) {
  const userTimezone = getUserTimezone()
  return getEndOfDayInTimezone(date, userTimezone)
}

/**
 * Get the start of day for a given date in specific timezone
 * @param {Date|string} date - The date
 * @param {string} timezone - The timezone to use
 * @returns {Date} - Start of day (00:00:00) in specified timezone as UTC Date
 */
export function getStartOfDayInTimezone(date, timezone) {
  const d = new Date(date)
  
  if (timezone === 'UTC') {
    const utcDate = new Date(d)
    utcDate.setUTCHours(0, 0, 0, 0)
    return utcDate
  }
  
  // Get the date string in the target timezone (YYYY-MM-DD format)
  const dateStr = formatDateInTimezone(d, timezone)
  
  // Use a direct approach: iterate through possible UTC times to find 
  // which one corresponds to midnight in the target timezone
  const baseTime = new Date(`${dateStr}T00:00:00Z`) // Start from midnight UTC
  
  // Search in a reasonable range around the base time
  for (let offsetHours = -24; offsetHours <= 24; offsetHours++) {
    const candidateTime = new Date(baseTime.getTime() + offsetHours * 3600 * 1000)
    const candidateFormatted = candidateTime.toLocaleString('sv-SE', { timeZone: timezone })
    const [candidateDatePart, candidateTimePart] = candidateFormatted.split(' ')
    
    if (candidateDatePart === dateStr && candidateTimePart === '00:00:00') {
      console.log(`🕛 Found start of day for ${dateStr} in ${timezone}: ${candidateTime.toISOString()}`)
      return candidateTime
    }
  }
  
  // Fallback: this shouldn't happen, but return something reasonable
  console.error(`❌ Could not find start of day for ${dateStr} in ${timezone}`)
  return new Date(`${dateStr}T00:00:00Z`)
}

/**
 * Get the end of day for a given date in specific timezone
 * @param {Date|string} date - The date  
 * @param {string} timezone - The timezone to use
 * @returns {Date} - End of day (23:59:59.999) in specified timezone as UTC Date
 */
export function getEndOfDayInTimezone(date, timezone) {
  const d = new Date(date)
  
  if (timezone === 'UTC') {
    const utcDate = new Date(d)
    utcDate.setUTCHours(23, 59, 59, 999)
    return utcDate
  }
  
  // Get the start of the next day and subtract 1 millisecond
  const nextDay = new Date(d)
  nextDay.setDate(nextDay.getDate() + 1)
  const startOfNextDay = getStartOfDayInTimezone(nextDay, timezone)
  return new Date(startOfNextDay.getTime() - 1)
}

/**
 * Calculate the duration of a stay that occurs on a specific day
 * @param {Object} stayItem - The stay item with timestamp and stayDuration
 * @param {string} currentDateString - The date to calculate for
 * @returns {number} - Duration in seconds for this specific day
 */
export function calculateDayDuration(stayItem, currentDateString) {
  const currentDate = new Date(currentDateString)
  const userTimezone = getUserTimezone()
  const stayStart = new Date(stayItem.timestamp)
  // Calculate end time from start time + duration (stayDuration is in seconds)
  const stayEnd = new Date(stayStart.getTime() + (stayItem.stayDuration * 1000))
  
  // Get boundaries of the current day in user's timezone
  const dayStart = getStartOfDayInTimezone(currentDate, userTimezone)
  const dayEnd = getEndOfDayInTimezone(currentDate, userTimezone)
  
  // Calculate the actual start and end times for this day
  const thisDayStart = stayStart < dayStart ? dayStart : stayStart
  const thisDayEnd = stayEnd > dayEnd ? dayEnd : stayEnd
  
  // Calculate duration in seconds
  const durationMs = thisDayEnd - thisDayStart
  return Math.max(0, Math.floor(durationMs / 1000))
}

/**
 * Format "on this day" duration for overnight items (consolidated function)
 * @param {Object} item - The timeline item (stay, trip, or data gap)
 * @param {string} currentDateString - The current date being rendered
 * @param {string} itemType - Type of item: 'stay', 'trip', or 'dataGap'
 * @returns {string} - Formatted duration string like "17:00 - 11:11 (18 hours 11 minutes)"
 */
export function formatOnThisDayDuration(item, currentDateString, itemType) {
  const currentDate = new Date(currentDateString)
  const userTimezone = getUserTimezone()
  
  // Get start and end times based on item type
  let itemStart, itemEnd
  
  if (itemType === 'stay') {
    itemStart = new Date(item.timestamp)
    itemEnd = item.endTime 
      ? new Date(item.endTime) 
      : new Date(itemStart.getTime() + (item.stayDuration * 1000))
  } else if (itemType === 'trip') {
    itemStart = new Date(item.timestamp)
    // tripDuration is in minutes, convert to milliseconds
    const durationMs = item.tripDuration * 60 * 1000
    itemEnd = new Date(itemStart.getTime() + durationMs)
  } else if (itemType === 'dataGap') {
    itemStart = new Date(item.startTime)
    itemEnd = new Date(item.endTime)
  } else {
    throw new Error(`Unknown item type: ${itemType}`)
  }
  
  // Get boundaries of the current day in user's timezone
  const dayStart = getStartOfDayInTimezone(currentDate, userTimezone)
  const dayEnd = getEndOfDayInTimezone(currentDate, userTimezone)
  
  // Determine the actual start and end times for this day
  const thisDayStart = itemStart < dayStart ? dayStart : itemStart
  const thisDayEnd = itemEnd > dayEnd ? dayEnd : itemEnd
  
  // Format the time range
  const startTimeStr = formatTime(thisDayStart)
  const endTimeStr = formatTime(thisDayEnd)
  
  // Calculate duration for this day only
  const durationMs = thisDayEnd - thisDayStart
  const durationSeconds = Math.floor(durationMs / 1000)
  
  // Format duration appropriately based on length
  let durationStr
  if (durationSeconds < 60) {
    durationStr = `${durationSeconds} second${durationSeconds !== 1 ? 's' : ''}`
  } else if (durationSeconds < 3600) {
    const minutes = Math.floor(durationSeconds / 60)
    durationStr = `${minutes} minute${minutes !== 1 ? 's' : ''}`
  } else {
    const hours = Math.floor(durationSeconds / 3600)
    const minutes = Math.floor((durationSeconds % 3600) / 60)
    if (minutes > 0) {
      durationStr = `${hours} hour${hours !== 1 ? 's' : ''} ${minutes} minute${minutes !== 1 ? 's' : ''}`
    } else {
      durationStr = `${hours} hour${hours !== 1 ? 's' : ''}`
    }
  }
  
  return `${startTimeStr} - ${endTimeStr} (${durationStr})`
}

/**
 * Format smart date text for overnight continuation
 * @param {string|Date} startTime - When the stay originally started
 * @param {string} currentDateString - The current date being rendered
 * @returns {string} - Formatted text like "yesterday", "Monday", or "Jul 01"
 */
export function formatOvernightContinuation(startTime, currentDateString) {
  const startDate = new Date(startTime)
  const currentDate = new Date(currentDateString)
  
  // Calculate days difference using timezone-aware date boundaries
  const userTimezone = getUserTimezone()
  const startDateInTz = new Date(startDate.toLocaleDateString('en-US', { timeZone: userTimezone }))
  const currentDateInTz = new Date(currentDate.toLocaleDateString('en-US', { timeZone: userTimezone }))
  
  const daysDiff = Math.floor((currentDateInTz - startDateInTz) / (1000 * 60 * 60 * 24))
  
  if (daysDiff === 1) {
    return 'yesterday'
  } else if (daysDiff <= 7) {
    return startDate.toLocaleDateString('en-US', { weekday: 'long' })
  } else {
    return startDate.toLocaleDateString('en-US', { 
      month: 'short', 
      day: 'numeric' 
    })
  }
}

/**
 * Check if an item should be grouped under a specific date
 * This handles overnight stays and multi-day data gaps that might appear on multiple days
 * @param {Object} timelineItem - The timeline item (stay, trip, dataGap)
 * @param {string} dateKey - The date key we're checking against
 * @returns {boolean} - True if this item should appear under this date
 */
export function shouldItemAppearOnDate(timelineItem, dateKey) {
  const targetDate = new Date(dateKey)
  const userTimezone = getUserTimezone()
  const dayStart = getStartOfDayInTimezone(targetDate, userTimezone)
  const dayEnd = getEndOfDayInTimezone(targetDate, userTimezone)
  
  // Handle data gaps (which have startTime/endTime instead of timestamp)
  if (timelineItem.type === 'dataGap') {
    const gapStart = new Date(timelineItem.startTime)
    const gapEnd = new Date(timelineItem.endTime)
    
    // Data gap appears on this date if it overlaps with this day
    return gapStart < dayEnd && gapEnd > dayStart
  }
  
  // Handle trips (both single-day and overnight)
  if (timelineItem.type === 'trip') {
    // For non-overnight trips, timezone-aware date matching
    if (!isOvernightTrip(timelineItem)) {
      const itemDate = new Date(timelineItem.timestamp)
      const userTimezone = getUserTimezone()
      // Create timezone-aware date strings for comparison
      const itemDateInTz = new Date(itemDate.toLocaleDateString('en-US', { timeZone: userTimezone }))
      const targetDateInTz = new Date(targetDate.toLocaleDateString('en-US', { timeZone: userTimezone }))
      return itemDateInTz.toDateString() === targetDateInTz.toDateString()
    }
    
    // For overnight trips, check if this date falls within the trip period
    const tripStart = new Date(timelineItem.timestamp)
    // Calculate end time from start time + duration (tripDuration is in minutes for trips)
    const tripEnd = new Date(tripStart.getTime() + (timelineItem.tripDuration * 60 * 1000))
    
    // Item appears on this date if the trip overlaps with this day
    return tripStart < dayEnd && tripEnd > dayStart
  }
  
  // Handle stays (both single-day and overnight)
  if (timelineItem.type === 'stay') {
    // For non-overnight stays, timezone-aware date matching
    if (!isOvernightStay(timelineItem)) {
      const itemDate = new Date(timelineItem.timestamp)
      const userTimezone = getUserTimezone()
      // Create timezone-aware date strings for comparison
      const itemDateInTz = new Date(itemDate.toLocaleDateString('en-US', { timeZone: userTimezone }))
      const targetDateInTz = new Date(targetDate.toLocaleDateString('en-US', { timeZone: userTimezone }))
      return itemDateInTz.toDateString() === targetDateInTz.toDateString()
    }
    
    // For overnight stays, check if this date falls within the stay period
    const stayStart = new Date(timelineItem.timestamp)
    // Calculate end time from start time + duration (stayDuration is in seconds)
    const stayEnd = new Date(stayStart.getTime() + (timelineItem.stayDuration * 1000))
    
    // Item appears on this date if the stay overlaps with this day
    return stayStart < dayEnd && stayEnd > dayStart
  }
  
  // Fallback for unknown types
  return false
}