import { formatDuration } from '@/utils/calculationsHelpers'

export const STACK_MOVEMENT_TYPE_MAP = {
  WALK: { label: 'Walk', icon: '🚶' },
  BICYCLE: { label: 'Bicycle', icon: '🚴' },
  RUNNING: { label: 'Running', icon: '🏃' },
  CAR: { label: 'Car', icon: '🚗' },
  TRAIN: { label: 'Train', icon: '🚊' },
  FLIGHT: { label: 'Flight', icon: '✈️' },
  UNKNOWN: { label: 'Unknown', icon: '❓' }
}

export const getMovementTypeDisplay = (movementType) => (
  STACK_MOVEMENT_TYPE_MAP[movementType] || { label: movementType || 'Unknown', icon: '❓' }
)

export const getStackItemTypeClass = (item) => {
  if (item?.type === 'stay') return 'stack-item--stay'
  if (item?.type === 'trip') return 'stack-item--trip'
  if (item?.type === 'dataGap') return 'stack-item--datagap'
  return 'stack-item--default'
}

export const getStackItemTitle = (item) => {
  if (item?.type === 'stay') {
    return `🏠 Stayed at ${item.locationName || item.address || 'Unknown place'}`
  }

  if (item?.type === 'trip') {
    return '🔄 Transition to new place'
  }

  if (item?.type === 'dataGap') {
    return '⚠️ Data Gap'
  }

  return 'Timeline event'
}

export const getStackItemSubtitle = (item) => {
  if (item?.type === 'trip') {
    const movement = getMovementTypeDisplay(item.movementType)
    const isManual = item.movementTypeSource === 'MANUAL' ? ' (Manual)' : ''
    return `🚦 Movement: ${movement.icon} ${movement.label}${isManual}`
  }

  return ''
}

export const getStackItemMeta = (item) => {
  if (item?.type === 'stay' && item.stayDuration) {
    return `For ${formatDuration(item.stayDuration)}`
  }

  if (item?.type === 'trip') {
    const duration = item.tripDuration ? `Duration: ${formatDuration(item.tripDuration)}` : null
    const distanceValue = item.distanceMeters ?? item.totalDistanceMeters
    const distance = distanceValue ? `Distance: ${(distanceValue / 1000).toFixed(1)} km` : null
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
      : 'Unknown time'

    return {
      item,
      index,
      typeClass: getStackItemTypeClass(item),
      dateStr,
      title: getStackItemTitle(item),
      subtitle: getStackItemSubtitle(item),
      meta: getStackItemMeta(item)
    }
  })
}
