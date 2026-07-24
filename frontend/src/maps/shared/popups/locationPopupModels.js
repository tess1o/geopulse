const formatTelemetryValue = (item) => {
  if (!item) return '-'
  const value = item.value ?? '-'
  if (!item.unit) return value
  if (item.unit === '%') return `${value}${item.unit}`
  return `${value} ${item.unit}`
}

const formatDateTime = (timezone, value) => {
  if (!value) return 'Unknown'
  try {
    return `${timezone.formatDateDisplay(value)} ${timezone.formatTime(value, { withSeconds: true })}`
  } catch {
    return 'Unknown'
  }
}

export const buildViewerLocationPopupModel = (location, { timezone } = {}) => {
  const isFallback = location?.source === 'fallback'
  const title = location?.label || (isFallback ? 'Your last known GeoPulse location' : 'Your location')
  const timestamp = location?.timestamp
    ? `${isFallback ? 'Last recorded' : 'Updated'} ${timezone.timeAgo(location.timestamp)}`
    : ''
  const rows = []

  if (location?.accuracy) {
    rows.push({
      label: 'Accuracy',
      value: `About ${Math.round(location.accuracy)} m`
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
        label: item.label || item.key || 'Value',
        value: formatTelemetryValue(item)
      }))
    : []

  return {
    title: shareData?.sharedBy || 'Shared location',
    description: shareData?.description || '',
    rows: shareData?.sharedAt
      ? [
          {
            label: 'Last seen',
            value: timezone.timeAgo(shareData.sharedAt)
          }
        ]
      : [],
    sections: telemetryRows.length
      ? [
          {
            title: 'Telemetry',
            rows: telemetryRows
          }
        ]
      : [],
    variant: 'compact'
  }
}

export const buildLocationAnalyticsPlacePopupModel = (place, { timezone, onOpenPlaceDetails } = {}) => {
  const cityCountry = [place?.city, place?.country].filter(Boolean).join(', ')

  return {
    title: place?.locationName || 'Unknown location',
    subtitle: cityCountry,
    rows: [
      {
        label: 'Visits',
        value: String(place?.visitCount ?? 0)
      },
      {
        label: 'Last visit',
        value: formatDateTime(timezone, place?.lastVisit)
      }
    ],
    actions: [
      {
        key: 'open-place-details',
        label: 'Open place details',
        iconClass: 'pi pi-external-link',
        onClick: onOpenPlaceDetails
      }
    ],
    variant: 'compact'
  }
}
