import { ref, watch } from 'vue'
import { useDigestStore } from '@/stores/digest'
import { useDateRangeStore } from '@/stores/dateRange'

const HEATMAP_LAYERS = ['stays', 'trips']

const getHeatmapRangeKey = (startTime, endTime, layer) => {
  return `range:${startTime}:${endTime}:${layer}`
}

/**
 * Heatmap overlay of the timeline map for the selected date range: which layer (stays or trips) is shown and its
 * points. Both layers are prefetched whenever the range changes, so switching layers is instant.
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
  const heatmapPrefetches = new Map()

  const loadHeatmap = async () => {
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

    const requestId = ++heatmapRequestId
    const inFlight = heatmapPrefetches.get(key)
    const data = inFlight
      ? await inFlight
      : await digestStore.fetchHeatmapRangeData(
        startTime,
        endTime,
        heatmapLayer.value,
        { silent: true }
      )
    if (requestId !== heatmapRequestId) return

    heatmapPoints.value = Array.isArray(data) ? data : []
  }

  const prefetchHeatmapRange = () => {
    if (!available.value) return
    const range = dateRangeStore.getCurrentDateRange
    if (!range || range.length !== 2) return
    const [startTime, endTime] = range

    HEATMAP_LAYERS.forEach((layer) => {
      const key = getHeatmapRangeKey(startTime, endTime, layer)
      if (digestStore.heatmapData?.[key] || heatmapPrefetches.has(key)) return

      const promise = digestStore.fetchHeatmapRangeData(
        startTime,
        endTime,
        layer,
        { silent: true }
      )
        .finally(() => heatmapPrefetches.delete(key))
      heatmapPrefetches.set(key, promise)
    })
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

  watch(heatmapEnabled, (enabled) => {
    if (enabled) {
      loadHeatmap()
    }
  })

  watch(heatmapLayer, () => {
    if (heatmapEnabled.value) {
      loadHeatmap()
    }
  })

  watch(
    () => dateRangeStore.getCurrentDateRange,
    () => {
      prefetchHeatmapRange()
      if (heatmapEnabled.value) {
        loadHeatmap()
      }
    },
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
