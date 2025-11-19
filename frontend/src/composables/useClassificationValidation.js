import { computed } from 'vue'

/**
 * Composable for validating trip classification speed thresholds
 *
 * Checks for:
 * - Overlapping speed ranges between transport types
 * - Min/max inversions
 * - Unrealistic threshold values
 * - Priority order conflicts
 *
 * @param {Object} prefs - Timeline preferences object with all classification parameters
 * @returns {Object} Validation state and helper functions
 */
export function useClassificationValidation(prefs) {

  /**
   * Check for bicycle-car speed overlap (CRITICAL)
   * Bicycle must be checked before car, but overlapping ranges cause issues
   */
  const bicycleCarOverlapWarning = computed(() => {
    if (!prefs.value?.bicycleEnabled) return null

    const bicycleMaxAvg = prefs.value?.bicycleMaxAvgSpeed ?? 25.0
    const carMinAvg = prefs.value?.carMinAvgSpeed ?? 10.0

    if (bicycleMaxAvg >= carMinAvg) {
      return {
        type: 'bicycle',
        severity: 'error',
        message: `Bicycle max avg speed (${bicycleMaxAvg} km/h) overlaps with car min avg speed (${carMinAvg} km/h). Trips in this range may be misclassified.`
      }
    }

    return null
  })

  /**
   * Check for insufficient gap between bicycle and car
   */
  const bicycleCarGapWarning = computed(() => {
    if (!prefs.value?.bicycleEnabled) return null

    const bicycleMaxAvg = prefs.value?.bicycleMaxAvgSpeed ?? 25.0
    const carMinAvg = prefs.value?.carMinAvgSpeed ?? 10.0
    const gap = carMinAvg - bicycleMaxAvg

    if (gap > 0 && gap < 2.0) {
      return {
        type: 'bicycle',
        severity: 'warn',
        message: `Only ${gap.toFixed(1)} km/h gap between bicycle and car thresholds. Consider widening to at least 2 km/h.`
      }
    }

    return null
  })

  /**
   * Check for large gap between walking and bicycle
   */
  const walkBicycleGapWarning = computed(() => {
    if (!prefs.value?.bicycleEnabled) return null

    const walkMaxMax = prefs.value?.walkingMaxMaxSpeed ?? 8.0
    const bicycleMinAvg = prefs.value?.bicycleMinAvgSpeed ?? 8.0
    const gap = bicycleMinAvg - walkMaxMax

    if (gap > 3.0) {
      return {
        type: 'bicycle',
        severity: 'warn',
        message: `Large gap (${gap.toFixed(1)} km/h) between walking max (${walkMaxMax} km/h) and bicycle min (${bicycleMinAvg} km/h). Trips in this range will be classified as UNKNOWN.`
      }
    }

    return null
  })

  /**
   * Check for bicycle min/max inversion
   */
  const bicycleMinMaxWarning = computed(() => {
    if (!prefs.value?.bicycleEnabled) return null

    const minAvg = prefs.value?.bicycleMinAvgSpeed
    const maxAvg = prefs.value?.bicycleMaxAvgSpeed

    if (minAvg !== undefined && maxAvg !== undefined && minAvg >= maxAvg) {
      return {
        type: 'bicycle',
        severity: 'error',
        message: `Bicycle min avg speed (${minAvg} km/h) must be less than max avg speed (${maxAvg} km/h).`
      }
    }

    return null
  })

  /**
   * Check for bicycle max avg vs max peak mismatch
   */
  const bicyclePeakWarning = computed(() => {
    if (!prefs.value?.bicycleEnabled) return null

    const maxAvg = prefs.value?.bicycleMaxAvgSpeed
    const maxMax = prefs.value?.bicycleMaxMaxSpeed

    if (maxAvg !== undefined && maxMax !== undefined && maxAvg > maxMax) {
      return {
        type: 'bicycle',
        severity: 'error',
        message: `Bicycle max avg speed (${maxAvg} km/h) cannot exceed max peak speed (${maxMax} km/h).`
      }
    }

    return null
  })

  /**
   * Check for train speed variance threshold being too high
   */
  const trainVarianceWarning = computed(() => {
    if (!prefs.value?.trainEnabled) return null

    const variance = prefs.value?.trainMaxSpeedVariance ?? 15.0

    if (variance > 20.0) {
      return {
        type: 'train',
        severity: 'warn',
        message: `High variance threshold (${variance}) may allow cars to be classified as trains. Recommended: ≤ 20.0`
      }
    }

    return null
  })

  /**
   * Check for train min/max speed inversions
   */
  const trainMinMaxWarning = computed(() => {
    if (!prefs.value?.trainEnabled) return null

    const warnings = []

    const minAvg = prefs.value?.trainMinAvgSpeed
    const maxAvg = prefs.value?.trainMaxAvgSpeed

    if (minAvg !== undefined && maxAvg !== undefined && minAvg >= maxAvg) {
      warnings.push({
        type: 'train',
        severity: 'error',
        message: `Train min avg speed (${minAvg} km/h) must be less than max avg speed (${maxAvg} km/h).`
      })
    }

    const minMax = prefs.value?.trainMinMaxSpeed
    const maxMax = prefs.value?.trainMaxMaxSpeed

    if (minMax !== undefined && maxMax !== undefined && minMax >= maxMax) {
      warnings.push({
        type: 'train',
        severity: 'error',
        message: `Train min peak speed (${minMax} km/h) must be less than max peak speed (${maxMax} km/h).`
      })
    }

    return warnings.length > 0 ? warnings[0] : null
  })

  /**
   * Check for train peak speed too low (station-only detection)
   */
  const trainMinPeakWarning = computed(() => {
    if (!prefs.value?.trainEnabled) return null

    const minMax = prefs.value?.trainMinMaxSpeed ?? 80.0

    if (minMax < 60.0) {
      return {
        type: 'train',
        severity: 'warn',
        message: `Train min peak speed (${minMax} km/h) is very low. May detect station waiting time as train trips.`
      }
    }

    return null
  })

  /**
   * Check for flight min/max inversion
   */
  const flightMinMaxWarning = computed(() => {
    if (!prefs.value?.flightEnabled) return null

    const minAvg = prefs.value?.flightMinAvgSpeed
    const minMax = prefs.value?.flightMinMaxSpeed

    // Note: For flight we use OR logic, so this is just a sanity check
    if (minAvg !== undefined && minMax !== undefined && minAvg > minMax) {
      return {
        type: 'flight',
        severity: 'warn',
        message: `Flight min avg speed (${minAvg} km/h) is higher than min peak speed (${minMax} km/h). This may be intentional but is unusual.`
      }
    }

    return null
  })

  /**
   * Check for car min/max inversion
   */
  const carMinMaxWarning = computed(() => {
    const minAvg = prefs.value?.carMinAvgSpeed
    const minMax = prefs.value?.carMinMaxSpeed

    if (minAvg !== undefined && minMax !== undefined && minAvg > minMax) {
      return {
        type: 'car',
        severity: 'warn',
        message: `Car min avg speed (${minAvg} km/h) is higher than min peak speed (${minMax} km/h). Trips may be harder to classify.`
      }
    }

    return null
  })

  /**
   * Check for walking min/max issues
   */
  const walkingMaxWarning = computed(() => {
    const maxAvg = prefs.value?.walkingMaxAvgSpeed
    const maxMax = prefs.value?.walkingMaxMaxSpeed

    if (maxAvg !== undefined && maxMax !== undefined && maxAvg > maxMax) {
      return {
        type: 'walk',
        severity: 'error',
        message: `Walking max avg speed (${maxAvg} km/h) cannot exceed max peak speed (${maxMax} km/h).`
      }
    }

    return null
  })

  /**
   * Aggregate all validation warnings
   */
  const validationWarnings = computed(() => {
    const warnings = [
      bicycleCarOverlapWarning.value,
      bicycleCarGapWarning.value,
      walkBicycleGapWarning.value,
      bicycleMinMaxWarning.value,
      bicyclePeakWarning.value,
      trainVarianceWarning.value,
      trainMinMaxWarning.value,
      trainMinPeakWarning.value,
      flightMinMaxWarning.value,
      carMinMaxWarning.value,
      walkingMaxWarning.value
    ].filter(w => w !== null)

    return warnings
  })

  /**
   * Check if there are any warnings
   */
  const hasWarnings = computed(() => validationWarnings.value.length > 0)

  /**
   * Check if there are any error-level warnings
   */
  const hasErrors = computed(() =>
    validationWarnings.value.some(w => w.severity === 'error')
  )

  /**
   * Get warnings for a specific transport type
   */
  const getWarningsForType = (type) => {
    return computed(() =>
      validationWarnings.value.filter(w => w.type === type)
    )
  }

  /**
   * Get warning messages for a specific transport type
   */
  const getWarningMessagesForType = (type) => {
    return computed(() =>
      getWarningsForType(type).value.map(w => w.message)
    )
  }

  return {
    validationWarnings,
    hasWarnings,
    hasErrors,
    getWarningsForType,
    getWarningMessagesForType
  }
}
