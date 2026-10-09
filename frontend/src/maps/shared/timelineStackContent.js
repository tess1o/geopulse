import { formatDuration } from '@/utils/durationFormatter'
import { formatDistanceForUnit } from '@/utils/measurementFormatters'
import { t, te } from '@/locales'
import { escapeHtml } from '@/maps/shared/popupContentBuilders'
import { formatWeatherDisplayTitle } from '@/maps/shared/stayWeather'

const STACK_MOVEMENT_TYPE_ICONS = {
  WALK: '🚶',
  BICYCLE: '🚴',
  RUNNING: '🏃',
  CAR: '🚗',
  MOTORCYCLE: '🏍️',
  PUBLIC_TRANSPORT: '🚌',
  TRAIN: '🚊',
  FLIGHT: '✈️',
  BOAT: '⛵',
  UNKNOWN: '❓'
}

export const getMovementTypeDisplay = (movementType) => ({
  label: te(`movementTypes.${movementType}`) ? t(`movementTypes.${movementType}`) : (movementType || t('maps.popups.common.unknown')),
  icon: STACK_MOVEMENT_TYPE_ICONS[movementType] || '❓'
})

export const getStackItemTypeClass = (item) => {
  if (item?.type === 'stay') return 'stack-item--stay'
  if (item?.type === 'trip') return 'stack-item--trip'
  if (item?.type === 'dataGap') return 'stack-item--datagap'
  return 'stack-item--default'
}

export const getStackItemTitle = (item) => {
  if (item?.type === 'stay') {
    return `🏠 ${t('timeline.stay.stayedAt')} ${item.locationName || item.address || t('maps.popups.common.unknownLocation')}`
  }

  if (item?.type === 'trip') {
    return `🔄 ${t('timeline.trip.transitionToNewPlace')}`
  }

  if (item?.type === 'dataGap') {
    return `⚠️ ${t('maps.popups.timeline.dataGap')}`
  }

  return t('maps.popups.timeline.timelineItem')
}

export const getStackItemSubtitle = (item) => {
  if (item?.type === 'trip') {
    const movement = getMovementTypeDisplay(item.movementType)
    const isManual = item.movementTypeSource === 'MANUAL' ? ` ${t('timeline.stay.manualIndicator')}` : ''
    return `🚦 ${t('timeline.trip.movementLabel')} ${movement.icon} ${movement.label}${isManual}`
  }

  return ''
}

export const getStackItemMeta = (item, deps = {}) => {
  if (item?.type === 'stay' && item.stayDuration) {
    return `${t('timeline.stay.forDuration')} ${formatDuration(item.stayDuration)}`
  }

  if (item?.type === 'trip') {
    const duration = item.tripDuration ? `${t('timeline.trip.durationLabel')} ${formatDuration(item.tripDuration)}` : null
    const distanceValue = item.distanceMeters ?? item.totalDistanceMeters
    const distance = distanceValue
      ? `${t('timeline.trip.distanceLabel')} ${formatDistanceForUnit(distanceValue, { unit: deps.unit })}`
      : null
    return [duration, distance].filter(Boolean).join(' · ')
  }

  return ''
}

export const buildTimelineStackItems = (items, deps = {}) => {
  const formatDateDisplay = deps.formatDateDisplay || (() => '')
  const formatTime = deps.formatTime || (() => '')
  // Optional (timestamp, item) formatter, so a row can be shown in the item's own (location) timezone.
  const formatItemDateTime = deps.formatItemDateTime
    || ((timestamp) => `${formatDateDisplay(timestamp)} ${formatTime(timestamp)}`)

  return (Array.isArray(items) ? items : []).map((item, index) => {
    const timestamp = item?.timestamp || item?.startTime
    const dateStr = timestamp
      ? formatItemDateTime(timestamp, item)
      : t('maps.popups.common.unknownTime')

    return {
      item,
      index,
      typeClass: getStackItemTypeClass(item),
      dateStr,
      title: getStackItemTitle(item),
      subtitle: getStackItemSubtitle(item),
      meta: getStackItemMeta(item, deps),
      // Optional display from buildStayWeatherDisplay(); stacks show weather per row, not as a badge.
      weather: deps.getItemWeather?.(item) || null
    }
  })
}

/** Inner HTML of one stack popup row button (shared by the stack and cross-type popups). */
export const buildStackRowHtml = (row) => {
  const weather = row.weather
    ? `<span class="stack-item-weather stack-item-weather--${escapeHtml(row.weather.severity || 'cloud')}" title="${escapeHtml(formatWeatherDisplayTitle(row.weather))}">`
      + `<i class="${escapeHtml(row.weather.icon)}" aria-hidden="true"></i>${escapeHtml(row.weather.temperatureText)}</span>`
    : ''

  return `
    <div class="stack-item-time"><span>${escapeHtml(row.dateStr)}</span>${weather}</div>
    <div class="stack-item-title">${escapeHtml(row.title)}</div>
    ${row.subtitle ? `<div class="stack-item-subtitle">${escapeHtml(row.subtitle)}</div>` : ''}
    ${row.meta ? `<div class="stack-item-meta">${escapeHtml(row.meta)}</div>` : ''}
  `.trim()
}

const normalizeNoteText = (value) => String(value || '')
  .replace(/[#*_`>\[\]()]/g, ' ')
  .replace(/\s+/g, ' ')
  .trim()

const formatDateTime = (timestamp, deps) => (
  `${(deps.formatDateDisplay || (() => ''))(timestamp)} ${(deps.formatTime || (() => ''))(timestamp)}`
)

// "12/03/2026 10:14 - 10:40" for a same-day range, full date on both ends otherwise.
const formatPhotoTimeRange = (photos, deps) => {
  const timestamps = (photos || [])
    .map((photo) => photo?.takenAt || photo?.fileCreatedAt)
    .filter(Boolean)
    .sort((a, b) => new Date(a) - new Date(b))

  if (timestamps.length === 0) {
    return t('maps.popups.common.unknownTime')
  }

  const first = timestamps[0]
  const last = timestamps[timestamps.length - 1]
  const firstLabel = formatDateTime(first, deps)
  if (first === last) {
    return firstLabel
  }

  const formatDate = deps.formatDateDisplay || (() => '')
  const lastLabel = formatDate(first) === formatDate(last)
    ? (deps.formatTime || (() => ''))(last)
    : formatDateTime(last, deps)
  return firstLabel === formatDateTime(last, deps) ? firstLabel : `${firstLabel} – ${lastLabel}`
}

/**
 * Mixed-kind rows for a cross-type collision popup. `members` are
 * `{ type: 'timeline' | 'notes' | 'photos', group }` entries. Timeline rows
 * are the unchanged stay/trip rows; notes and photos get their own row kinds.
 */
export const buildCrossTypeStackItems = (members, deps = {}) => {
  const rows = []

  ;(Array.isArray(members) ? members : []).forEach(({ type, group }) => {
    if (type === 'timeline') {
      buildTimelineStackItems(group.items, deps).forEach((row) => {
        rows.push({ ...row, kind: 'timeline', typeClass: row.typeClass })
      })
      return
    }

    if (type === 'notes') {
      ;(group.notes || []).forEach((note) => {
        const timestamp = note?.eventTime || note?.createdAt
        rows.push({
          kind: 'note',
          item: note,
          notes: [note],
          typeClass: 'stack-item--note',
          dateStr: timestamp ? formatDateTime(timestamp, deps) : t('maps.popups.common.unknownTime'),
          title: `📝 ${normalizeNoteText(note?.title) || t('maps.popups.timeline.crossType.note')}`,
          subtitle: normalizeNoteText(note?.snippet || note?.contentMarkdown).slice(0, 80),
          meta: ''
        })
      })
      return
    }

    if (type === 'photos') {
      const count = Math.max(Number(group.count) || group.photos?.length || 1, 1)
      rows.push({
        kind: 'photo',
        item: group,
        group,
        typeClass: 'stack-item--photo',
        dateStr: formatPhotoTimeRange(group.photos, deps),
        title: `📷 ${count > 1 ? t('maps.popups.timeline.crossType.photoCount', { count }) : t('maps.popups.timeline.crossType.photo')}`,
        subtitle: '',
        meta: ''
      })
    }
  })

  return rows
}
