import { resolveAverageTripSpeedKmh } from '@/maps/shared/tripSpeed'
import { formatDuration } from '@/utils/durationFormatter'
import { formatDistanceForUnit, formatSpeedForUnit } from '@/utils/measurementFormatters'
import { t, te } from '@/locales'

const translateMovementType = (type, fallback) => {
  if (!type) return fallback
  return te(`movementTypes.${type}`) ? t(`movementTypes.${type}`) : type
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
      title: t('maps.popups.common.telemetry'),
      rows: telemetryItems.map((item) => ({
        label: item?.label || item?.key || t('maps.popups.common.value'),
        value: formatTelemetryValue(item)
      }))
    }
  ]
}

const getTimelineTimestamp = (item) => item?.timestamp || item?.startTime

export const buildStayPopupModel = (stay, deps = {}) => {
  const timestamp = getTimelineTimestamp(stay)
  const formatDateTimeDisplay = resolveFormatDateTimeDisplay(deps)
  const dateText = timestamp
    ? formatDateTimeDisplay(timestamp, stay?.locationTimezone)
    : t('maps.popups.common.unknownTime')
  const durationText = stay?.stayDuration ? formatDuration(stay.stayDuration) : ''
  const locationName = stay?.locationName || stay?.address || t('maps.popups.common.unknownLocation')

  return {
    title: locationName,
    subtitle: dateText,
    iconClass: 'pi pi-map-marker',
    rows: durationText
      ? [
          {
            label: t('maps.popups.common.duration'),
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
  const dateText = timestamp
    ? formatDateTimeDisplay(timestamp, item?.startLocationTimezone)
    : t('maps.popups.common.unknownTime')
  const durationText = item?.tripDuration ? formatDuration(item.tripDuration) : ''
  const distanceText = item?.totalDistanceMeters
    ? formatDistanceForUnit(item.totalDistanceMeters, { unit: deps.unit })
    : ''
  const movementType = translateMovementType(item?.movementType, t('maps.popups.common.unknown'))

  return {
    title: t('maps.popups.timeline.trip', { movementType }),
    subtitle: dateText,
    iconClass: 'pi pi-arrow-right',
    rows: [
      durationText
        ? {
            label: t('maps.popups.common.duration'),
            value: durationText
          }
        : null,
      distanceText
        ? {
            label: t('maps.popups.common.distance'),
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
    title: t('maps.popups.timeline.dataGap'),
    subtitle: timestamp
      ? formatDateTimeDisplay(timestamp, item?.startLocationTimezone)
      : t('maps.popups.common.unknownTime'),
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
    title: t('maps.popups.timeline.timelineItem'),
    subtitle: timestamp ? formatDateTimeDisplay(timestamp) : t('maps.popups.common.unknownTime'),
    variant: 'compact'
  }
}

export const buildHighlightedTripPopupModel = (trip, deps = {}) => {
  const formatDateTimeDisplay = resolveFormatDateTimeDisplay(deps)
  const startMs = Date.parse(trip?.timestamp)
  const durationSeconds = Number.isFinite(Number(trip?.tripDuration)) ? Number(trip.tripDuration) : 0
  const endMs = Number.isFinite(startMs) ? startMs + Math.max(0, durationSeconds) * 1000 : null
  const movementType = translateMovementType(trip?.movementType, t('maps.popups.timeline.unknownMovement'))
  const startText = Number.isFinite(startMs)
    ? formatDateTimeDisplay(new Date(startMs).toISOString(), trip?.startLocationTimezone)
    : t('maps.popups.common.unknown')
  const endText = Number.isFinite(endMs)
    ? formatDateTimeDisplay(new Date(endMs).toISOString(), trip?.endLocationTimezone)
    : t('maps.popups.common.unknown')
  const averageSpeedKmh = resolveAverageTripSpeedKmh(trip)
  const averageSpeedText = formatSpeedForUnit(averageSpeedKmh, { unit: deps.unit, fallback: '' })

  return {
    title: t('maps.popups.timeline.movementTrip', { movementType }),
    description: t('maps.popups.timeline.hoverHint'),
    iconClass: 'pi pi-compass',
    rows: [
      {
        label: t('maps.popups.timeline.start'),
        value: startText
      },
      {
        label: t('maps.popups.timeline.end'),
        value: endText
      },
      {
        label: t('maps.popups.common.duration'),
        value: formatDuration(trip?.tripDuration)
      },
      {
        label: t('maps.popups.common.distance'),
        value: formatDistanceForUnit(trip?.distanceMeters, { unit: deps.unit })
      },
      averageSpeedText
        ? {
            label: t('maps.popups.timeline.averageSpeed'),
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
    ? formatDateTimeDisplay(
      new Date(pointTime).toISOString(),
      isStart ? trip?.startLocationTimezone : trip?.endLocationTimezone
    )
    : t('maps.popups.common.unknown')

  return {
    title: isStart ? t('maps.popups.timeline.tripStart') : t('maps.popups.timeline.tripEnd'),
    iconClass: isStart ? 'pi pi-play' : 'pi pi-flag',
    rows: [
      {
        label: t('maps.popups.timeline.time'),
        value: timeText
      },
      {
        label: t('maps.popups.common.duration'),
        value: formatDuration(durationSeconds)
      },
      {
        label: t('maps.popups.common.distance'),
        value: formatDistanceForUnit(trip?.distanceMeters, { unit: deps.unit })
      },
      {
        label: t('maps.popups.timeline.mode'),
        value: translateMovementType(trip?.movementType, t('maps.popups.common.unknown'))
      }
    ],
    variant: 'compact'
  }
}

export const buildPanoramaxCoveragePopupModel = (coverage = {}) => {
  const total = Number(coverage.nb_pictures) || 0
  const panoramic = Number(coverage.nb_360_pictures) || 0
  const flat = Number(coverage.nb_flat_pictures) || 0

  return {
    title: t('maps.popups.timeline.panoramaxCoverage'),
    description: t('maps.popups.timeline.zoomingIn'),
    iconClass: 'pi pi-images',
    rows: [
      { label: t('maps.popups.timeline.photos'), value: String(total) },
      panoramic ? { label: t('maps.popups.timeline.panoramicPhotos'), value: String(panoramic) } : null,
      flat ? { label: t('maps.popups.timeline.flatPhotos'), value: String(flat) } : null
    ].filter(Boolean),
    variant: 'compact'
  }
}

export const buildFriendTimelineStayPopupModel = (userTimeline, stay) => ({
  title: userTimeline?.fullName || t('maps.popups.timeline.user'),
  subtitle: stay?.locationName || t('maps.popups.timeline.stay'),
  iconClass: 'pi pi-user',
  rows: [
    {
      label: t('maps.popups.common.duration'),
      value: formatDuration(stay?.stayDuration)
    }
  ],
  variant: 'compact'
})

export const buildFriendTimelineTripPopupModel = (trip, deps = {}) => ({
  title: trip?.userFullName || t('maps.popups.timeline.tripFallback'),
  subtitle: translateMovementType(trip?.movementType, t('maps.popups.timeline.tripFallback')),
  iconClass: 'pi pi-arrow-right',
  rows: [
    {
      label: t('maps.popups.common.duration'),
      value: formatDuration(trip?.tripDuration)
    },
    {
      label: t('maps.popups.common.distance'),
      value: formatDistanceForUnit(trip?.distanceMeters, { unit: deps.unit })
    }
  ],
  variant: 'compact'
})
