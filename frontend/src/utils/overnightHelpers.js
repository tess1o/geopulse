/**
 * Utility functions for handling overnight stays and timeline processing
 */

/**
 * Check if a stay spans multiple days (overnight stay)
 * @param {Object} stayItem - The stay item with timestamp and stayDuration
 * @returns {boolean} - True if the stay crosses midnight boundary
 */
export function isOvernightStay(stayItem) {
  if (!stayItem.timestamp || !stayItem.stayDuration) {
    return false
  }

  const startDate = new Date(stayItem.timestamp)
  // Calculate end time from start time + duration (stayDuration is in minutes)
  const endDate = new Date(startDate.getTime() + (stayItem.stayDuration * 60 * 1000))

  // Get date parts (ignoring time) to check if they're on different days
  const startDay = startDate.toDateString()
  const endDay = endDate.toDateString()

  return startDay !== endDay
}

/**
 * Check if a stay should be shown as an overnight card for a specific date
 * @param {Object} stayItem - The stay item
 * @param {string} currentDateString - The date we're rendering (date key from grouping)
 * @returns {boolean} - True if this should use OvernightStayCard
 */
export function shouldShowAsOvernightStay(stayItem, currentDateString) {
  if (!isOvernightStay(stayItem)) {
    return false
  }

  const currentDate = new Date(currentDateString)
  const stayStartDate = new Date(stayItem.timestamp)
  
  // Get date parts for comparison (ignoring time)
  const currentDateStr = currentDate.toDateString()
  const stayStartDateStr = stayStartDate.toDateString()

  // Show as overnight if the stay started on a different day than we're currently rendering
  return currentDateStr !== stayStartDateStr
}

/**
 * Get the start of day for a given date (respects local timezone)
 * @param {Date|string} date - The date
 * @returns {Date} - Start of day (00:00:00)
 */
export function getStartOfDay(date) {
  const d = new Date(date)
  d.setHours(0, 0, 0, 0)
  return d
}

/**
 * Get the end of day for a given date (respects local timezone)
 * @param {Date|string} date - The date
 * @returns {Date} - End of day (23:59:59.999)
 */
export function getEndOfDay(date) {
  const d = new Date(date)
  d.setHours(23, 59, 59, 999)
  return d
}

/**
 * Calculate the duration of a stay that occurs on a specific day
 * @param {Object} stayItem - The stay item with timestamp and stayDuration
 * @param {string} currentDateString - The date to calculate for
 * @returns {number} - Duration in minutes for this specific day
 */
export function calculateDayDuration(stayItem, currentDateString) {
  const currentDate = new Date(currentDateString)
  const stayStart = new Date(stayItem.timestamp)
  // Calculate end time from start time + duration (stayDuration is in minutes)
  const stayEnd = new Date(stayStart.getTime() + (stayItem.stayDuration * 60 * 1000))
  
  // Get boundaries of the current day
  const dayStart = getStartOfDay(currentDate)
  const dayEnd = getEndOfDay(currentDate)
  
  // Calculate the actual start and end times for this day
  const thisDayStart = stayStart < dayStart ? dayStart : stayStart
  const thisDayEnd = stayEnd > dayEnd ? dayEnd : stayEnd
  
  // Calculate duration in minutes
  const durationMs = thisDayEnd - thisDayStart
  return Math.max(0, Math.floor(durationMs / (1000 * 60)))
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
  
  // Calculate days difference
  const daysDiff = Math.floor((currentDate - startDate) / (1000 * 60 * 60 * 24))
  
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
 * This handles overnight stays that might appear on multiple days
 * @param {Object} timelineItem - The timeline item (stay, trip, dataGap)
 * @param {string} dateKey - The date key we're checking against
 * @returns {boolean} - True if this item should appear under this date
 */
export function shouldItemAppearOnDate(timelineItem, dateKey) {
  const itemDate = new Date(timelineItem.timestamp)
  const targetDate = new Date(dateKey)
  
  // For non-stays or non-overnight items, simple date matching
  if (timelineItem.type !== 'stay' || !isOvernightStay(timelineItem)) {
    return itemDate.toDateString() === targetDate.toDateString()
  }
  
  // For overnight stays, check if this date falls within the stay period
  const stayStart = new Date(timelineItem.timestamp)
  // Calculate end time from start time + duration (stayDuration is in minutes)
  const stayEnd = new Date(stayStart.getTime() + (timelineItem.stayDuration * 60 * 1000))
  
  const dayStart = getStartOfDay(targetDate)
  const dayEnd = getEndOfDay(targetDate)
  
  // Item appears on this date if the stay overlaps with this day
  return stayStart < dayEnd && stayEnd > dayStart
}