import { computed, ref, unref, watch } from 'vue'
import {
  ROUTE_DISPLAY_MODES,
  getNextRouteDisplayMode,
  normalizeRouteDisplayMode
} from '@/constants/routeDisplayModes'
import { buildRawMapMatchingComparisonPathData } from '@/utils/mapMatchingPathData'

export function useMapMatchingComparison({
  rawPathData,
  timelineData,
  highlightedTrip,
  highlightedTripHasMatchedPath,
  matchedTripIds
} = {}) {
  const selectedRouteDisplayMode = ref(ROUTE_DISPLAY_MODES.MATCHED)

  const visibleTrips = computed(() => {
    const items = unref(timelineData)
    return (Array.isArray(items) ? items : []).filter(item => item?.type === 'trip')
  })

  const fullRawComparisonPathData = computed(() => buildRawMapMatchingComparisonPathData({
    rawPathData: unref(rawPathData),
    visibleTrips: visibleTrips.value,
    highlightedTrip: null,
    highlightedTripHasMatchedPath: false,
    matchedTripIds: unref(matchedTripIds)
  }))

  const rawComparisonPathData = computed(() => buildRawMapMatchingComparisonPathData({
    rawPathData: unref(rawPathData),
    visibleTrips: visibleTrips.value,
    highlightedTrip: unref(highlightedTrip),
    highlightedTripHasMatchedPath: unref(highlightedTripHasMatchedPath),
    matchedTripIds: unref(matchedTripIds)
  }))

  const routeDisplayModeControlAvailable = computed(() => fullRawComparisonPathData.value.length > 0)

  const routeDisplayMode = computed(() => {
    if (!routeDisplayModeControlAvailable.value) {
      return ROUTE_DISPLAY_MODES.MATCHED
    }

    return normalizeRouteDisplayMode(selectedRouteDisplayMode.value)
  })

  const routeDisplayModeUsesRawPath = computed(() => (
    routeDisplayMode.value === ROUTE_DISPLAY_MODES.RAW
  ))

  const routeDisplayModeUsesComparison = computed(() => (
    routeDisplayMode.value === ROUTE_DISPLAY_MODES.COMPARISON
  ))

  const cycleRouteDisplayMode = () => {
    if (!routeDisplayModeControlAvailable.value) {
      selectedRouteDisplayMode.value = ROUTE_DISPLAY_MODES.MATCHED
      return
    }

    selectedRouteDisplayMode.value = getNextRouteDisplayMode(routeDisplayMode.value)
  }

  watch(routeDisplayModeControlAvailable, (available) => {
    if (!available) {
      selectedRouteDisplayMode.value = ROUTE_DISPLAY_MODES.MATCHED
    }
  })

  return {
    routeDisplayMode,
    routeDisplayModeControlAvailable,
    routeDisplayModeUsesRawPath,
    routeDisplayModeUsesComparison,
    rawComparisonPathData,
    cycleRouteDisplayMode
  }
}
