import { formatDuration, formatDistance } from '@/utils/calculationsHelpers'

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
  `${timezone.formatDateDisplay(value)} ${timezone.format(value, 'HH:mm:ss')}`
)

const resolveFormatDateTimeDisplay = (deps = {}) => {
  if (typeof deps.formatDateTimeDisplay === 'function') {
    return deps.formatDateTimeDisplay
  }

  return (value) => defaultFormatDateTimeDisplay(value, deps.timezone)
}

const formatTelemetryValue = (item) => {
  if (!item) return '-'
  const value = item.value ?? '-'
  if (!item.unit) return value
  if (item.unit === '%') return `${value}${item.unit}`
  return `${value} ${item.unit}`
}

export const buildTelemetryPopupHtml = (telemetryItems) => {
  if (!Array.isArray(telemetryItems) || telemetryItems.length === 0) {
    return ''
  }

  const rows = telemetryItems
    .map((telemetryItem) => `
      <div class="popup-telemetry-row">
        <span class="popup-telemetry-label">${escapeHtml(telemetryItem.label || telemetryItem.key || 'Value')}:</span>
        <span class="popup-telemetry-value">${escapeHtml(formatTelemetryValue(telemetryItem))}</span>
      </div>
    `)
    .join('')

  return `
    <div class="popup-telemetry">
      <div class="popup-telemetry-title">Telemetry</div>
      ${rows}
    </div>
  `
}

export const buildStayPopupHtml = (stay, deps = {}) => {
  const timestamp = stay?.timestamp || stay?.startTime
  const formatDateTimeDisplay = resolveFormatDateTimeDisplay(deps)
  const dateStr = timestamp ? formatDateTimeDisplay(timestamp) : 'Unknown time'
  const durationText = stay?.stayDuration ? formatDuration(stay.stayDuration) : null
  const locationName = stay?.locationName || stay?.address || 'Unknown Location'
  const telemetryHtml = buildTelemetryPopupHtml(stay?.telemetryCurrentPopup)

  return `
    <div class="timeline-popup">
      <div class="popup-location">${escapeHtml(locationName)}</div>
      <div class="popup-time">${escapeHtml(dateStr)}</div>
      ${durationText ? `<div class="popup-duration">Stay duration: ${escapeHtml(durationText)}</div>` : ''}
      ${telemetryHtml}
    </div>
  `.trim()
}

export const buildTripPopupHtml = (trip, deps = {}) => {
  const formatDateTimeDisplay = resolveFormatDateTimeDisplay(deps)
  const startMs = Date.parse(trip?.timestamp)
  const durationSeconds = Number.isFinite(Number(trip?.tripDuration)) ? Number(trip.tripDuration) : 0
  const endMs = Number.isFinite(startMs) ? startMs + Math.max(0, durationSeconds) * 1000 : null
  const movementType = trip?.movementType || 'Movement'
  const startText = Number.isFinite(startMs)
    ? formatDateTimeDisplay(new Date(startMs).toISOString())
    : 'Unknown'
  const endText = Number.isFinite(endMs)
    ? formatDateTimeDisplay(new Date(endMs).toISOString())
    : 'Unknown'

  return `
    <div class="trip-popup">
      <div class="trip-title">
        ${escapeHtml(movementType)} Trip
      </div>
      <div class="trip-detail">
        Start: ${escapeHtml(startText)}
      </div>
      <div class="trip-detail">
        End: ${escapeHtml(endText)}
      </div>
      <div class="trip-detail">
        Duration: ${escapeHtml(formatDuration(trip?.tripDuration))}
      </div>
      <div class="trip-detail">
        Distance: ${escapeHtml(formatDistance(trip?.distanceMeters || 0))}
      </div>
      <div class="trip-detail trip-detail-hint">
        Hover the highlighted route to see when you were there.
      </div>
    </div>
  `.trim()
}

export const buildDataGapPopupHtml = (item, deps = {}) => {
  const timestamp = item?.timestamp || item?.startTime
  const formatDateTimeDisplay = resolveFormatDateTimeDisplay(deps)
  const dateStr = timestamp ? formatDateTimeDisplay(timestamp) : 'Unknown time'

  return `
    <div class="timeline-popup">
      <div class="popup-location">Data Gap</div>
      <div class="popup-time">${escapeHtml(dateStr)}</div>
    </div>
  `.trim()
}

export const buildTimelineItemPopupHtml = (item, deps = {}) => {
  if (!item) {
    return ''
  }

  if (item.type === 'stay') {
    return buildStayPopupHtml(item, deps)
  }

  if (item.type === 'trip') {
    const timestamp = item?.timestamp || item?.startTime
    const formatDateTimeDisplay = resolveFormatDateTimeDisplay(deps)
    const dateStr = timestamp ? formatDateTimeDisplay(timestamp) : 'Unknown time'
    const durationText = item.tripDuration ? formatDuration(item.tripDuration) : null
    const distanceText = item.totalDistanceMeters ? `${(item.totalDistanceMeters / 1000).toFixed(1)} km` : null
    const movementType = item.movementType || 'Unknown'

    return `
      <div class="timeline-popup">
        <div class="popup-location">Trip (${escapeHtml(movementType)})</div>
        <div class="popup-time">${escapeHtml(dateStr)}</div>
        ${durationText ? `<div class="popup-duration">Duration: ${escapeHtml(durationText)}</div>` : ''}
        ${distanceText ? `<div class="popup-duration">Distance: ${escapeHtml(distanceText)}</div>` : ''}
      </div>
    `.trim()
  }

  if (item.type === 'dataGap') {
    return buildDataGapPopupHtml(item, deps)
  }

  const timestamp = item?.timestamp || item?.startTime
  const formatDateTimeDisplay = resolveFormatDateTimeDisplay(deps)
  const dateStr = timestamp ? formatDateTimeDisplay(timestamp) : 'Unknown time'
  return `
    <div class="timeline-popup">
      <div class="popup-time">${escapeHtml(dateStr)}</div>
    </div>
  `.trim()
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

  return `
    <div class="trip-hover-tooltip">
      <div class="trip-hover-time">
        ${formatDateTimeDisplay(new Date(hoverTiming.timeMs).toISOString())}
      </div>
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
