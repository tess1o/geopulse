import { formatDuration, formatSpeed } from '@/utils/calculationsHelpers'
import { resolveHoverSpeedKmh } from '@/maps/shared/tripSpeed'
import { t } from '@/locales'

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

const defaultFormatDateTimeDisplay = (value, timezone, locationTimezone) => {
  if (locationTimezone && typeof timezone.formatDateTimeDisplayAt === 'function') {
    return timezone.formatDateTimeDisplayAt(value, locationTimezone, { withSeconds: true })
  }
  return `${timezone.formatDateDisplay(value)} ${timezone.formatTime(value, { withSeconds: true })}`
}

// The formatter takes (value, locationTimezone); the zone only changes the output in the "location" display mode.
const resolveFormatDateTimeDisplay = (deps = {}) => {
  if (typeof deps.formatDateTimeDisplay === 'function') {
    return deps.formatDateTimeDisplay
  }

  return (value, locationTimezone) => defaultFormatDateTimeDisplay(value, deps.timezone, locationTimezone)
}

// A hover point mid-trip has no zone of its own: use the origin's zone for the first half, the destination's after.
const resolveHoverLocationTimezone = (trip, timeMs) => {
  const startMs = Date.parse(trip?.timestamp)
  const durationMs = (Number(trip?.tripDuration) || 0) * 1000
  if (!Number.isFinite(startMs) || timeMs < startMs + durationMs / 2) {
    return trip?.startLocationTimezone || trip?.endLocationTimezone || null
  }
  return trip?.endLocationTimezone || trip?.startLocationTimezone || null
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
  const confidenceLabel = hoverTiming.mode === 'exact'
    ? t('maps.popups.timeline.hoverTooltip.exactGpsPoint')
    : t('maps.popups.timeline.hoverTooltip.estimatedBetweenPoints')
  const confidenceClass = hoverTiming.mode === 'exact' ? 'exact' : 'estimated'
  const speedKmh = resolveHoverSpeedKmh(hoverTiming)
  const speedText = Number.isFinite(speedKmh) ? formatSpeed(speedKmh) : null

  return `
    <div class="trip-hover-tooltip">
      <div class="trip-hover-time">
        ${formatDateTimeDisplay(
          new Date(hoverTiming.timeMs).toISOString(),
          resolveHoverLocationTimezone(trip, hoverTiming.timeMs)
        )}
      </div>
      ${speedText ? `
      <div class="trip-hover-speed">
        ${escapeHtml(t('maps.popups.timeline.hoverTooltip.speedPrefix', { speed: speedText }))}
      </div>
      ` : ''}
      <div class="trip-hover-confidence ${confidenceClass}">
        ${confidenceLabel}
      </div>
      ${Number.isFinite(offsetSeconds) ? `
      <div class="trip-hover-offset">
        ${escapeHtml(t('maps.popups.timeline.hoverTooltip.fromTripStart', { duration: formatDuration(offsetSeconds) }))}
      </div>
      ` : ''}
    </div>
  `
}
