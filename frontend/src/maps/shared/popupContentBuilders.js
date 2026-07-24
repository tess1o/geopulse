import { formatDuration, formatSpeed } from '@/utils/calculationsHelpers'
import { resolveHoverSpeedKmh } from '@/maps/shared/tripSpeed'

export const escapeHtml = (value) => {
  if (value === null || value === undefined) {
    return ''
  }

  return String(value)
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#39;')
}

const defaultFormatDateTimeDisplay = (value, timezone) => (
  `${timezone.formatDateDisplay(value)} ${timezone.formatTime(value, { withSeconds: true })}`
)

const resolveFormatDateTimeDisplay = (deps = {}) => {
  if (typeof deps.formatDateTimeDisplay === 'function') {
    return deps.formatDateTimeDisplay
  }

  return (value) => defaultFormatDateTimeDisplay(value, deps.timezone)
}

export const buildTripHoverTooltipHtml = (trip, hoverTiming, deps = {}) => {
  if (!hoverTiming || !Number.isFinite(hoverTiming.timeMs)) {
    return ''
  }

  const formatDateTimeDisplay = resolveFormatDateTimeDisplay(deps)
  const startMs = Date.parse(trip?.timestamp)
  const offsetSeconds = Number.isFinite(startMs)
    ? Math.max(0, Math.round((hoverTiming.timeMs - startMs) / 1000))
    : null
  const confidenceLabel = hoverTiming.mode === 'exact' ? 'Exact GPS point' : 'Estimated between points'
  const confidenceClass = hoverTiming.mode === 'exact' ? 'exact' : 'estimated'
  const speedKmh = resolveHoverSpeedKmh(hoverTiming)
  const speedText = Number.isFinite(speedKmh) ? formatSpeed(speedKmh) : null

  return `
    <div class="trip-hover-tooltip">
      <div class="trip-hover-time">
        ${formatDateTimeDisplay(new Date(hoverTiming.timeMs).toISOString())}
      </div>
      ${speedText ? `
      <div class="trip-hover-speed">
        Speed: ${escapeHtml(speedText)}
      </div>
      ` : ''}
      <div class="trip-hover-confidence ${confidenceClass}">
        ${confidenceLabel}
      </div>
      ${Number.isFinite(offsetSeconds) ? `
      <div class="trip-hover-offset">
        From trip start: ${formatDuration(offsetSeconds)}
      </div>
      ` : ''}
    </div>
  `
}
