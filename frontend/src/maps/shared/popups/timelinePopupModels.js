import { resolveAverageTripSpeedKmh } from '@/maps/shared/tripSpeed'
import { formatDuration } from '@/utils/durationFormatter'
import { formatDistanceForUnit, formatSpeedForUnit } from '@/utils/measurementFormatters'

const defaultFormatDateTimeDisplay = (value, timezone) => (
  `${timezone.formatDateDisplay(value)} ${timezone.formatTime(value, { withSeconds: true })}`
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

const buildTelemetrySection = (telemetryItems) => {
  if (!Array.isArray(telemetryItems) || telemetryItems.length === 0) {
    return []
  }

  return [
    {
      title: 'Telemetry',
      rows: telemetryItems.map((item) => ({
        label: item?.label || item?.key || 'Value',
        value: formatTelemetryValue(item)
      }))
    }
  ]
}

const getTimelineTimestamp = (item) => item?.timestamp || item?.startTime

export const buildStayPopupModel = (stay, deps = {}) => {
  const timestamp = getTimelineTimestamp(stay)
  const formatDateTimeDisplay = resolveFormatDateTimeDisplay(deps)
  const dateText = timestamp ? formatDateTimeDisplay(timestamp) : 'Unknown time'
  const durationText = stay?.stayDuration ? formatDuration(stay.stayDuration) : ''
  const locationName = stay?.locationName || stay?.address || 'Unknown location'

  return {
    title: locationName,
    subtitle: dateText,
    iconClass: 'pi pi-map-marker',
    rows: durationText
      ? [
          {
            label: 'Duration',
            value: durationText
          }
        ]
      : [],
    sections: buildTelemetrySection(stay?.telemetryCurrentPopup),
    variant: 'compact'
  }
}

export const buildTimelineTripPopupModel = (item, deps = {}) => {
  const timestamp = getTimelineTimestamp(item)
  const formatDateTimeDisplay = resolveFormatDateTimeDisplay(deps)
  const dateText = timestamp ? formatDateTimeDisplay(timestamp) : 'Unknown time'
  const durationText = item?.tripDuration ? formatDuration(item.tripDuration) : ''
  const distanceText = item?.totalDistanceMeters ? formatDistanceForUnit(item.totalDistanceMeters) : ''
  const movementType = item?.movementType || 'Unknown'

  return {
    title: `Trip (${movementType})`,
    subtitle: dateText,
    iconClass: 'pi pi-arrow-right',
    rows: [
      durationText
        ? {
            label: 'Duration',
            value: durationText
          }
        : null,
      distanceText
        ? {
            label: 'Distance',
            value: distanceText
          }
        : null
    ].filter(Boolean),
    variant: 'compact'
  }
}

export const buildDataGapPopupModel = (item, deps = {}) => {
  const timestamp = getTimelineTimestamp(item)
  const formatDateTimeDisplay = resolveFormatDateTimeDisplay(deps)

  return {
    title: 'Data Gap',
    subtitle: timestamp ? formatDateTimeDisplay(timestamp) : 'Unknown time',
    iconClass: 'pi pi-exclamation-triangle',
    variant: 'compact'
  }
}

export const buildTimelineItemPopupModel = (item, deps = {}) => {
  if (!item) {
    return {
      title: '',
      variant: 'compact'
    }
  }

  if (item.type === 'stay') {
    return buildStayPopupModel(item, deps)
  }

  if (item.type === 'trip') {
    return buildTimelineTripPopupModel(item, deps)
  }

  if (item.type === 'dataGap') {
    return buildDataGapPopupModel(item, deps)
  }

  const timestamp = getTimelineTimestamp(item)
  const formatDateTimeDisplay = resolveFormatDateTimeDisplay(deps)

  return {
    title: 'Timeline item',
    subtitle: timestamp ? formatDateTimeDisplay(timestamp) : 'Unknown time',
    variant: 'compact'
  }
}

export const buildHighlightedTripPopupModel = (trip, deps = {}) => {
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
  const averageSpeedKmh = resolveAverageTripSpeedKmh(trip)
  const averageSpeedText = formatSpeedForUnit(averageSpeedKmh, { fallback: '' })

  return {
    title: `${movementType} Trip`,
    description: 'Hover the highlighted route to see when you were there and how fast you were moving.',
    iconClass: 'pi pi-compass',
    rows: [
      {
        label: 'Start',
        value: startText
      },
      {
        label: 'End',
        value: endText
      },
      {
        label: 'Duration',
        value: formatDuration(trip?.tripDuration)
      },
      {
        label: 'Distance',
        value: formatDistanceForUnit(trip?.distanceMeters)
      },
      averageSpeedText
        ? {
            label: 'Average speed',
            value: averageSpeedText
          }
        : null
    ].filter(Boolean),
    variant: 'compact'
  }
}

export const buildTripEndpointPopupModel = (trip, markerType, deps = {}) => {
  const formatDateTimeDisplay = resolveFormatDateTimeDisplay(deps)
  const startMs = Date.parse(trip?.timestamp)
  const durationSeconds = Number.isFinite(Number(trip?.tripDuration)) ? Number(trip.tripDuration) : 0
  const endMs = Number.isFinite(startMs) ? startMs + Math.max(0, durationSeconds) * 1000 : null
  const isStart = markerType === 'start'
  const pointTime = isStart ? startMs : endMs
  const timeText = Number.isFinite(pointTime)
    ? formatDateTimeDisplay(new Date(pointTime).toISOString())
    : 'Unknown'

  return {
    title: isStart ? 'Trip Start' : 'Trip End',
    iconClass: isStart ? 'pi pi-play' : 'pi pi-flag',
    rows: [
      {
        label: 'Time',
        value: timeText
      },
      {
        label: 'Duration',
        value: formatDuration(durationSeconds)
      },
      {
        label: 'Distance',
        value: formatDistanceForUnit(trip?.distanceMeters)
      },
      {
        label: 'Mode',
        value: trip?.movementType || 'Unknown'
      }
    ],
    variant: 'compact'
  }
}

export const buildFriendTimelineStayPopupModel = (userTimeline, stay) => ({
  title: userTimeline?.fullName || 'User',
  subtitle: stay?.locationName || 'Stay',
  iconClass: 'pi pi-user',
  rows: [
    {
      label: 'Duration',
      value: formatDuration(stay?.stayDuration)
    }
  ],
  variant: 'compact'
})

export const buildFriendTimelineTripPopupModel = (trip) => ({
  title: trip?.userFullName || 'Trip',
  subtitle: trip?.movementType || 'Trip',
  iconClass: 'pi pi-arrow-right',
  rows: [
    {
      label: 'Duration',
      value: formatDuration(trip?.tripDuration)
    },
    {
      label: 'Distance',
      value: formatDistanceForUnit(trip?.distanceMeters)
    }
  ],
  variant: 'compact'
})
