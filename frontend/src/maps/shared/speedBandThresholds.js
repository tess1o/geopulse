import { DISTANCE_UNITS } from '@/utils/measurementFormatters'
import { HIGHLIGHTED_TRIP_SPEED_THRESHOLDS_KMH } from '@/maps/shared/highlightedTripSpeedBands'

const KMH_TO_MPH = 0.621371

/**
 * The speed band limits in the user's distance unit, for legends and settings copy.
 * `unitLabels` carries the translated unit names: { kmh, mph }.
 */
export function getSpeedBandThresholds(distanceUnit, unitLabels) {
  const useMiles = distanceUnit === DISTANCE_UNITS.MILES
  const unit = useMiles ? unitLabels.mph : unitLabels.kmh
  const convert = (speedKmh) => (useMiles ? Math.round(speedKmh * KMH_TO_MPH) : speedKmh)

  return {
    unit,
    slowBelow: convert(HIGHLIGHTED_TRIP_SPEED_THRESHOLDS_KMH.slowBelow),
    mediumUpTo: convert(HIGHLIGHTED_TRIP_SPEED_THRESHOLDS_KMH.mediumUpTo)
  }
}
