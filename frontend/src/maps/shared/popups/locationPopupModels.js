import { t } from '@/locales'

const formatTelemetryValue = (item) => {
  if (!item) return '-'
  const value = item.value ?? '-'
  if (!item.unit) return value
  if (item.unit === '%') return `${value}${item.unit}`
  return `${value} ${item.unit}`
}

const formatDateTime = (timezone, value) => {
  if (!value) return t('maps.popups.common.unknown')
  try {
    return `${timezone.formatDateDisplay(value)} ${timezone.formatTime(value, { withSeconds: true })}`
  } catch {
    return t('maps.popups.common.unknown')
  }
}

export const buildViewerLocationPopupModel = (location, { timezone } = {}) => {
  const isFallback = location?.source === 'fallback'
  const title = location?.label || (isFallback ? t('maps.popups.location.lastKnown') : t('maps.popups.location.yourLocation'))
  const timestamp = location?.timestamp
    ? (isFallback
      ? t('maps.popups.location.lastRecorded', { time: timezone.timeAgo(location.timestamp) })
      : t('maps.popups.location.updated', { time: timezone.timeAgo(location.timestamp) }))
    : ''
  const rows = []

  if (location?.accuracy) {
    rows.push({
      label: t('maps.popups.common.accuracy'),
      value: t('maps.popups.location.aboutMeters', { value: Math.round(location.accuracy) })
    })
  }

  return {
    title,
    subtitle: timestamp,
    rows,
    variant: 'compact'
  }
}

export const buildSharedLocationPopupModel = (shareData, { timezone } = {}) => {
  const telemetryRows = Array.isArray(shareData?.telemetry)
    ? shareData.telemetry.map((item) => ({
        label: item.label || item.key || t('maps.popups.common.value'),
        value: formatTelemetryValue(item)
      }))
    : []

  return {
    title: shareData?.sharedBy || t('maps.popups.location.sharedLocation'),
    description: shareData?.description || '',
    rows: shareData?.sharedAt
      ? [
          {
            label: t('maps.popups.friend.lastSeen'),
            value: timezone.timeAgo(shareData.sharedAt)
          }
        ]
      : [],
    sections: telemetryRows.length
      ? [
          {
            title: t('maps.popups.common.telemetry'),
            rows: telemetryRows
          }
        ]
      : [],
    variant: 'compact'
  }
}

export const buildLocationAnalyticsPlacePopupModel = (place, { timezone, onOpenPlaceDetails } = {}) => {
  const isCity = place?.type === 'city'
  const cityCountry = [place?.city, place?.country].filter(Boolean).join(', ')

  return {
    title: place?.locationName || t('maps.popups.common.unknownLocation'),
    subtitle: cityCountry,
    rows: isCity
      ? [
          { label: t('maps.popups.location.visits'), value: String(place?.visitCount ?? 0) },
          { label: t('maps.popups.location.places'), value: String(place?.uniquePlaces ?? 0) }
        ]
      : [
          { label: t('maps.popups.location.visits'), value: String(place?.visitCount ?? 0) },
          { label: t('maps.popups.location.lastVisit'), value: formatDateTime(timezone, place?.lastVisit) }
        ],
    actions: [
      {
        key: 'open-place-details',
        label: isCity ? t('maps.popups.location.openCityDetails') : t('maps.popups.location.openPlaceDetails'),
        iconClass: 'pi pi-external-link',
        onClick: onOpenPlaceDetails
      }
    ],
    variant: 'compact'
  }
}
