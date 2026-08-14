import { computed, unref } from 'vue'
import { useRouter } from 'vue-router'
import { buildTimelineItemGpsRange } from '@/utils/timelineGpsRange'

export const useTimelineGpsDrilldown = (itemRef) => {
  const router = useRouter()

  const gpsPointRange = computed(() => buildTimelineItemGpsRange(unref(itemRef)))

  const navigateToGpsPoints = () => {
    const range = gpsPointRange.value
    if (!range) return

    router.push({
      path: '/app/gps-data',
      query: {
        startTime: range.startTime,
        endTime: range.endTime
      }
    })
  }

  const appendGpsPointsMenuItem = (items) => {
    if (!gpsPointRange.value) return

    items.push({
      label: 'Show GPS points',
      icon: 'pi pi-map-marker',
      command: navigateToGpsPoints
    })
  }

  return {
    gpsPointRange,
    navigateToGpsPoints,
    appendGpsPointsMenuItem
  }
}
