import { computed, ref, unref } from 'vue'
import { buildRawMapMatchingComparisonPathData } from '@/utils/mapMatchingPathData'

export function useMapMatchingComparison({
  rawPathData,
  timelineData,
  highlightedTrip,
  highlightedTripHasMatchedPath,
  matchedTripIds
} = {}) {
  const showPathComparison = ref(false)

  const visibleTrips = computed(() => {
    const items = unref(timelineData)
    return (Array.isArray(items) ? items : []).filter(item => item?.type === 'trip')
  })

  const rawComparisonPathData = computed(() => buildRawMapMatchingComparisonPathData({
    rawPathData: unref(rawPathData),
    visibleTrips: visibleTrips.value,
    highlightedTrip: unref(highlightedTrip),
    highlightedTripHasMatchedPath: unref(highlightedTripHasMatchedPath),
    matchedTripIds: unref(matchedTripIds)
  }))

  const pathComparisonAvailable = computed(() => rawComparisonPathData.value.length > 0)

  return {
    showPathComparison,
    rawComparisonPathData,
    pathComparisonAvailable
  }
}
