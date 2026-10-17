/**
 * Movement types: the backend TripType enum values and how the UI shows each one. Labels come from the shared
 * `movementTypes.*` locale catalog; colours live in utils/movementTypeColors.js because they follow the user's
 * color scheme.
 */
export const MOVEMENT_TYPES = Object.freeze([
  'WALK',
  'RUNNING',
  'BICYCLE',
  'CAR',
  'MOTORCYCLE',
  'PUBLIC_TRANSPORT',
  'TRAIN',
  'FLIGHT',
  'BOAT',
  'UNKNOWN'
])

// emoji: timeline cards, popups and legends. icon: icon-font class for tags and tables.
// severity: PrimeVue Tag severity in trip tables and dialogs.
const MOVEMENT_TYPE_DISPLAY = Object.freeze({
  WALK: Object.freeze({ emoji: '🚶', icon: 'fas fa-walking', severity: 'success' }),
  RUNNING: Object.freeze({ emoji: '🏃', icon: 'fas fa-running', severity: 'success' }),
  BICYCLE: Object.freeze({ emoji: '🚴', icon: 'fas fa-bicycle', severity: 'info' }),
  CAR: Object.freeze({ emoji: '🚗', icon: 'pi pi-car', severity: 'info' }),
  MOTORCYCLE: Object.freeze({ emoji: '🏍️', icon: 'fas fa-motorcycle', severity: 'info' }),
  PUBLIC_TRANSPORT: Object.freeze({ emoji: '🚌', icon: 'pi pi-directions', severity: 'info' }),
  TRAIN: Object.freeze({ emoji: '🚊', icon: 'fas fa-train', severity: 'info' }),
  FLIGHT: Object.freeze({ emoji: '✈️', icon: 'fas fa-plane', severity: 'danger' }),
  BOAT: Object.freeze({ emoji: '⛵', icon: 'pi pi-compass', severity: 'info' }),
  UNKNOWN: Object.freeze({ emoji: '❓', icon: 'pi pi-question-circle', severity: 'secondary' })
})

const displayFor = (movementType) => MOVEMENT_TYPE_DISPLAY[String(movementType || '').toUpperCase()] || null

export const getMovementTypeEmoji = (movementType) => (displayFor(movementType) || MOVEMENT_TYPE_DISPLAY.UNKNOWN).emoji

export const getMovementTypeIcon = (movementType) => (displayFor(movementType) || MOVEMENT_TYPE_DISPLAY.UNKNOWN).icon

export const getMovementTypeSeverity = (movementType) => (displayFor(movementType) || MOVEMENT_TYPE_DISPLAY.UNKNOWN).severity
