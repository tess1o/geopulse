export const DISTANCE_UNITS = {
  KILOMETERS: 'KILOMETERS',
  MILES: 'MILES'
}

const normalizeDistanceUnit = (unit) => (
  unit === DISTANCE_UNITS.MILES ? DISTANCE_UNITS.MILES : DISTANCE_UNITS.KILOMETERS
)

export function formatDistanceForUnit(meters, options = {}) {
  const {
    unit = DISTANCE_UNITS.KILOMETERS,
    rounded = false,
    fallback = 'Unknown'
  } = options
  const numericValue = Number(meters)

  if (!Number.isFinite(numericValue) || numericValue < 0) {
    return fallback
  }

  if (normalizeDistanceUnit(unit) === DISTANCE_UNITS.MILES) {
    const feet = numericValue * 3.28084
    if (feet < 5280) {
      return `${Math.round(feet)} ft`
    }

    const miles = feet / 5280
    return `${rounded ? Math.round(miles) : miles.toFixed(2)} mi`
  }

  if (numericValue < 1000) {
    const displayMeters = rounded
      ? Math.round(numericValue)
      : Math.round(numericValue * 100) / 100
    return `${displayMeters} m`
  }

  const kilometers = numericValue / 1000
  const displayKilometers = rounded
    ? Math.round(kilometers)
    : Math.round(kilometers * 100) / 100
  return `${displayKilometers} km`
}

export function formatSpeedForUnit(speedKmH, options = {}) {
  const {
    unit = DISTANCE_UNITS.KILOMETERS,
    fallback = 'N/A'
  } = options
  const numericValue = Number(speedKmH)

  if (!Number.isFinite(numericValue)) {
    return fallback
  }

  if (normalizeDistanceUnit(unit) === DISTANCE_UNITS.MILES) {
    return `${(numericValue * 0.621371).toFixed(2)} mph`
  }

  return `${numericValue.toFixed(2)} km/h`
}
