import { formatDuration } from '@/utils/durationFormatter'
import { formatDistanceForUnit } from '@/utils/measurementFormatters'
import { t, te } from '@/locales'

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
    return [duration, distance].filter(Boolean).join(' | ')
  }

  return ''
}

export const buildTimelineStackItems = (items, deps = {}) => {
  const formatDateDisplay = deps.formatDateDisplay || (() => '')
  const formatTime = deps.formatTime || (() => '')

  return (Array.isArray(items) ? items : []).map((item, index) => {
    const timestamp = item?.timestamp || item?.startTime
    const dateStr = timestamp
      ? `${formatDateDisplay(timestamp)} ${formatTime(timestamp)}`
      : t('maps.popups.common.unknownTime')

    return {
      item,
      index,
      typeClass: getStackItemTypeClass(item),
      dateStr,
      title: getStackItemTitle(item),
      subtitle: getStackItemSubtitle(item),
      meta: getStackItemMeta(item, deps)
    }
  })
}
