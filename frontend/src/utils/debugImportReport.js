// Helpers for the report a completed debug import returns: how the regenerated timeline compares with the one the
// user saw, and what may explain a difference.

export const comparisonState = (comparison) => {
  if (!comparison?.available) return 'unavailable'
  return comparison.matches ? 'match' : 'differs'
}

const dateFormatter = (timeZone, options) => {
  try {
    return new Intl.DateTimeFormat(undefined, { ...options, timeZone: timeZone || undefined })
  } catch {
    // An unknown zone name: fall back to the browser's zone rather than failing the whole report
    return new Intl.DateTimeFormat(undefined, options)
  }
}

// Times are shown in the exporting user's timezone, where they saw the timeline
export const formatReportTime = (value, timeZone) => {
  if (!value) return ''
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return ''
  return dateFormatter(timeZone, {
    year: 'numeric', month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit', second: '2-digit',
    hourCycle: 'h23'
  }).format(date)
}

export const formatReportDuration = (seconds) => {
  if (seconds === null || seconds === undefined) return ''
  const total = Math.max(0, Math.round(seconds))
  const hours = Math.floor(total / 3600)
  const minutes = Math.floor((total % 3600) / 60)
  const rest = total % 60
  if (hours > 0) return `${hours}h ${String(minutes).padStart(2, '0')}m`
  if (minutes > 0) return `${minutes}m ${String(rest).padStart(2, '0')}s`
  return `${rest}s`
}

// The calendar day of an instant in a timezone, as the timeline page's URL expects it (YYYY-MM-DD)
export const dayInZone = (value, timeZone) => {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return null
  let parts
  try {
    parts = new Intl.DateTimeFormat('en-CA', {
      timeZone: timeZone || undefined, year: 'numeric', month: '2-digit', day: '2-digit'
    }).formatToParts(date)
  } catch {
    parts = new Intl.DateTimeFormat('en-CA', { year: 'numeric', month: '2-digit', day: '2-digit' })
      .formatToParts(date)
  }
  const part = type => parts.find(item => item.type === type)?.value
  return `${part('year')}-${part('month')}-${part('day')}`
}

// Where "open in timeline" goes: the day the user said the problem happened, else the range they exported
export const timelineQuery = (report) => {
  if (!report) return null
  const zone = report.timezone
  if (report.issue?.time) {
    const day = dayInZone(report.issue.time, zone)
    return day ? { start: day, end: day } : null
  }
  const range = report.requestedRange
  if (!range?.start || !range?.end) return null
  const start = dayInZone(range.start, zone)
  const end = dayInZone(range.end, zone)
  return start && end ? { start, end } : null
}

// Groups the warnings: the ones that suggest the difference is not a timeline bug come first
const WARNING_ORDER = [
  'TIMELINE_DIFFERS', 'TIMELINE_STALE', 'REBUILD_FAILING', 'REBUILD_PENDING', 'APP_VERSION_DIFFERS',
  'GPS_FILTERS_ACTIVE', 'WATER_DATASET_MISSING', 'WATER_FLAGS_NOT_EXPORTED', 'OLD_EXPORT_FORMAT',
  'TIMELINE_CONFIG_NOT_APPLIED', 'TIMELINE_CONFIG_NOT_IN_EXPORT', 'EXISTING_DATA_KEPT', 'TIMELINE_NOT_IN_EXPORT'
]

export const sortedWarnings = (warnings = []) => [...warnings].sort((a, b) => {
  const left = WARNING_ORDER.indexOf(a.code)
  const right = WARNING_ORDER.indexOf(b.code)
  return (left === -1 ? WARNING_ORDER.length : left) - (right === -1 ? WARNING_ORDER.length : right)
})

// Which fields of a DIFFERENT row differ, so the page can highlight just those
export const differingFields = (row) => {
  if (row?.status !== 'DIFFERENT' || !row.expected || !row.actual) return []
  return ['end', 'durationSeconds', 'movementType'].filter(field => row.expected[field] !== row.actual[field])
}
