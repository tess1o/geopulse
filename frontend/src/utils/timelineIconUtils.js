const TRIP_MOVEMENT_ICON_CLASS_MAP = Object.freeze({
  WALK: 'fas fa-walking',
  BICYCLE: 'fas fa-bicycle',
  RUNNING: 'fas fa-running',
  TRAIN: 'fas fa-train',
  FLIGHT: 'fas fa-plane',
  CAR: 'pi pi-car',
  UNKNOWN: 'pi pi-car'
})

/**
 * Resolve icon class for trip movement types.
 * @param {string} movementType
 * @returns {string}
 */
export function getTripMovementIconClass(movementType) {
  const normalizedType = String(movementType || 'UNKNOWN').toUpperCase()
  return TRIP_MOVEMENT_ICON_CLASS_MAP[normalizedType] || TRIP_MOVEMENT_ICON_CLASS_MAP.UNKNOWN
}

/**
 * Resolve icon class for timeline item markers.
 * @param {Object|null} item
 * @param {Object} options
 * @param {boolean} options.isOvernight
 * @returns {string}
 */
export function getTimelineItemIconClass(item, { isOvernight = false } = {}) {
  if (isOvernight) return 'pi pi-moon'

  const itemType = item?.type
  if (itemType === 'stay') return 'pi pi-map-marker'
  if (itemType === 'trip') return getTripMovementIconClass(item?.movementType)
  if (itemType === 'dataGap') return 'pi pi-question'
  return 'pi pi-circle'
}
