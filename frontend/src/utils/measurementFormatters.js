import { intlLocale, t } from '@/locales'

export const DISTANCE_UNITS = {
  KILOMETERS: 'KILOMETERS',
  MILES: 'MILES'
}

const normalizeDistanceUnit = (unit) => (
  unit === DISTANCE_UNITS.MILES ? DISTANCE_UNITS.MILES : DISTANCE_UNITS.KILOMETERS
)

// Locale-grouped numbers ("442,830" / "442 830"), so long distances stay readable.
const numberFormats = new Map()
const formatNumber = (value, minimumFractionDigits, maximumFractionDigits) => {
  const key = `${intlLocale()}|${minimumFractionDigits}|${maximumFractionDigits}`
  let format = numberFormats.get(key)
  if (!format) {
    format = new Intl.NumberFormat(intlLocale(), { minimumFractionDigits, maximumFractionDigits })
    numberFormats.set(key, format)
  }
  return format.format(value)
}

export function formatDistanceForUnit(meters, options = {}) {
  const {
    unit = DISTANCE_UNITS.KILOMETERS,
    rounded = false,
    fallback = t('common.unknown')
  } = options
  const numericValue = Number(meters)

  if (!Number.isFinite(numericValue) || numericValue < 0) {
    return fallback
  }

  if (normalizeDistanceUnit(unit) === DISTANCE_UNITS.MILES) {
    const feet = numericValue * 3.28084
    if (feet < 5280) {
      return `${formatNumber(feet, 0, 0)} ${t('common.units.ft')}`
    }

    const miles = feet / 5280
    return `${rounded ? formatNumber(miles, 0, 0) : formatNumber(miles, 2, 2)} ${t('common.units.mi')}`
  }

  if (numericValue < 1000) {
    return `${formatNumber(numericValue, 0, rounded ? 0 : 2)} ${t('common.units.m')}`
  }

  return `${formatNumber(numericValue / 1000, 0, rounded ? 0 : 2)} ${t('common.units.km')}`
}

export function formatSpeedForUnit(speedKmH, options = {}) {
  const {
    unit = DISTANCE_UNITS.KILOMETERS,
    fallback = t('common.notAvailable')
  } = options
  const numericValue = Number(speedKmH)

  if (!Number.isFinite(numericValue)) {
    return fallback
  }

  if (normalizeDistanceUnit(unit) === DISTANCE_UNITS.MILES) {
    return `${(numericValue * 0.621371).toFixed(2)} ${t('common.units.mph')}`
  }

  return `${numericValue.toFixed(2)} ${t('common.units.kmh')}`
}
