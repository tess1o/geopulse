import { ref, watch } from 'vue'
import { useDigestStore } from '@/stores/digest'
import { useDateRangeStore } from '@/stores/dateRange'

const getHeatmapRangeKey = (startTime, endTime, layer) => {
  return `range:${startTime}:${endTime}:${layer}`
}

/**
 * Heatmap overlay of the timeline map for the selected date range: which layer (stays or trips) is shown and its
 * points. Fetch only the selected layer when enabled; the digest store caches visited ranges.
 *
 * @param {Object} options
 * @param {import('vue').Ref<boolean>} options.available false where the heatmap can't be shown (e.g. public views)
 */
export function useTimelineMapHeatmap({ available }) {
  const digestStore = useDigestStore()
  const dateRangeStore = useDateRangeStore()

  const heatmapEnabled = ref(false)
  const heatmapLayer = ref('stays')
  const heatmapPoints = ref([])
  let heatmapRequestId = 0
  const heatmapRequests = new Map()

  const loadHeatmap = async () => {
    const requestId = ++heatmapRequestId
    if (!heatmapEnabled.value || !available.value) {
      heatmapPoints.value = []
      return
    }

    const range = dateRangeStore.getCurrentDateRange
    if (!range || range.length !== 2) return
    const [startTime, endTime] = range
    const key = getHeatmapRangeKey(startTime, endTime, heatmapLayer.value)

    const cached = digestStore.heatmapData?.[key]
    if (cached) {
      heatmapPoints.value = Array.isArray(cached) ? cached : []
      return
    }

    if (!heatmapRequests.has(key)) {
      const promise = digestStore.fetchHeatmapRangeData(
        startTime,
        endTime,
        heatmapLayer.value,
        { silent: true }
      )
        .finally(() => heatmapRequests.delete(key))
      heatmapRequests.set(key, promise)
    }
    const data = await heatmapRequests.get(key)
    if (requestId !== heatmapRequestId) return

    heatmapPoints.value = Array.isArray(data) ? data : []
  }

  const toggleHeatmap = (enabled) => {
    if (!available.value && enabled) {
      return
    }
    heatmapEnabled.value = enabled
  }

  const setHeatmapLayer = (layer) => {
    heatmapLayer.value = layer
  }

  watch(
    [heatmapEnabled, heatmapLayer, () => dateRangeStore.getCurrentDateRange, available],
    loadHeatmap,
    { immediate: true }
  )

  return {
    heatmapEnabled,
    heatmapLayer,
    heatmapPoints,
    toggleHeatmap,
    setHeatmapLayer
  }
}
