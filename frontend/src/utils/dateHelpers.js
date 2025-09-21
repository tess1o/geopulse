import { useTimezone } from '@/composables/useTimezone'

// Create singleton instance for consistent timezone handling
const timezone = useTimezone()

// Re-export all composable functions with their exact names
export const {
  // Core
  setTimezone,
  getTimezone,
  now,
  fromUtc,
  toUtc,
  create,
  createFromCalendar,
  
  // Boundaries (UTC for API)
  startOfDayUtc,
  endOfDayUtc,
  startOfWeekUtc,
  endOfWeekUtc,
  startOfMonthUtc,
  endOfMonthUtc,
  
  // Date Ranges
  createDateRangeUtc,
  createDateRangeFromPicker,
  getDateRangeArray,
  getTodayRangeUtc,
  
  // Math
  add,
  subtract,
  diff,
  diffInDays,
  isSameDay,
  isBefore,
  isAfter,
  isBetweenDates,
  
  // Formatting
  format,
  formatTime,
  formatDate,
  formatDateUS,
  formatDateLong,
  formatDateShort,
  formatDateWithYear,
  formatDuration,
  
  // Timeline Specific
  isOvernight,
  isOvernightWithDuration,
  shouldItemAppearOnDate,
  shouldShowAsOvernight,
  formatOnThisDayDuration,
  formatContinuationText,
  
  // Grouping
  groupByDay,
  
  // Validation
  isValidDate,
  isValidDateRange
} = timezone

// Legacy function aliases for backward compatibility
export const formatDateInTimezone = (date, userTimezone, fmt) => format(date, fmt)
export const createDateInTimezone = (date) => fromUtc(date)
export const isOvernightInTimezone = (startTime, duration) => isOvernightWithDuration(startTime, duration)
export const isSameDayInTimezone = (date1, date2) => isSameDay(date1, date2)
export const daysDifferenceInTimezone = (date1, date2) => diffInDays(date1, date2)
export const formatTimeInTimezone = (date) => formatTime(date)
export const convertPickerDateToUserTimezone = (pickerDate) => createFromCalendar(pickerDate)
export const createDateRangeInTimezone = (startDate, endDate) => createDateRangeUtc(startDate, endDate)
export const isValidDataRange = isValidDateRange

// Legacy functions for backward compatibility
export function getTodayRange() {
  return getTodayRangeUtc()
}

export function setToEndOfDay(date) {
  return endOfDayUtc(date)
}

export function formatDateMMDDYYYY(date) {
  return formatDateUS(date)
}

export function timeAgo(date) {
  const dateObj = fromUtc(date)
  const nowObj = now()
  
  if (nowObj.diff(dateObj, 'minute') < 1) return 'Just now'
  if (nowObj.diff(dateObj, 'minute') < 60) return `${nowObj.diff(dateObj, 'minute')} min ago`
  if (nowObj.diff(dateObj, 'hour') < 24) return `${nowObj.diff(dateObj, 'hour')} hours ago`
  if (nowObj.diff(dateObj, 'day') < 30) return `${nowObj.diff(dateObj, 'day')} days ago`
  
  return dateObj.format('YYYY-MM-DD')
}

export function formatFullDate(date) {
  return format(date, 'MMMM D, YYYY')
}

export function getLastWeekRange() {
  const nowObj = now()
  const start = nowObj.subtract(7, 'day').startOf('day').utc().toISOString()
  const end = nowObj.endOf('day').utc().toISOString()
  return { start, end }
}

export function getLastMonthRange() {
  const nowObj = now()
  const start = nowObj.subtract(30, 'day').startOf('day').utc().toISOString()
  const end = nowObj.endOf('day').utc().toISOString()
  return { start, end }
}

// Re-export dayjs through the composable for any remaining direct usage
export default timezone