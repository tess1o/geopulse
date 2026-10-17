import { ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useToast } from 'primevue/usetoast'
import { useDateRangeStore } from '@/stores/dateRange'
import { useTechnicalDataStore } from '@/stores/technicalData'

const RAW_GPS_POINTS_LIMIT = 10000

const getRawGpsRangeKey = (startTime, endTime) => `raw-gps:${startTime}:${endTime}`

const getRawGpsLocationCacheKey = (point) => {
  if (point?.id != null) {
    return `id:${point.id}`
  }
  return `coord:${point?.latitude}:${point?.longitude}`
}

/**
 * Raw GPS points layer of the timeline map: loads the selected range's points while the layer is on (cached
 * per range, capped at 10,000 with a one-time warning) and resolves a point's address on demand.
 *
 * @param {Object} options
 * @param {import('vue').Ref<boolean>} options.enabled whether the raw GPS layer is shown
 * @param {Function} options.isPublicView true on shared pages, which never load raw points
 */
export function useRawGpsPointsInspector({ enabled, isPublicView }) {
  const { t } = useI18n()
  const toast = useToast()
  const dateRangeStore = useDateRangeStore()
  const technicalDataStore = useTechnicalDataStore()

  const rawGpsPoints = ref([])
  const rawGpsPointsLoading = ref(false)
  let rawGpsPointsRequestId = 0
  const rawGpsPointsCache = new Map()
  const rawGpsLocationCache = new Map()
  const rawGpsLimitWarningKeys = new Set()

  const getRawGpsRange = () => {
    const range = dateRangeStore.getCurrentDateRange
    return Array.isArray(range) && range.length === 2 && range[0] && range[1]
      ? range
      : null
  }

  const loadRawGpsPoints = async () => {
    if (!enabled.value || isPublicView()) {
      return
    }

    const range = getRawGpsRange()
    if (!range) {
      rawGpsPoints.value = []
      return
    }

    const [startTime, endTime] = range
    const key = getRawGpsRangeKey(startTime, endTime)
    const cached = rawGpsPointsCache.get(key)
    if (cached) {
      rawGpsPoints.value = cached.points
      return
    }

    const requestId = ++rawGpsPointsRequestId
    rawGpsPointsLoading.value = true

    try {
      const data = await technicalDataStore.fetchRawMapPoints({
        from: startTime,
        to: endTime,
        limit: RAW_GPS_POINTS_LIMIT
      })
      if (requestId !== rawGpsPointsRequestId) return

      const points = Array.isArray(data.points) ? data.points : []
      const meta = {
        totalCount: Number(data.totalCount || 0),
        returnedCount: Number(data.returnedCount || points.length),
        limit: Number(data.limit || RAW_GPS_POINTS_LIMIT),
        limited: Boolean(data.limited)
      }

      rawGpsPointsCache.set(key, { points, meta })
      rawGpsPoints.value = points

      if (meta.limited && !rawGpsLimitWarningKeys.has(key)) {
        rawGpsLimitWarningKeys.add(key)
        toast.add({
          severity: 'warn',
          summary: t('maps.timelineMap.rawGpsLimited.summary'),
          detail: t('maps.timelineMap.rawGpsLimited.detail', { returnedCount: meta.returnedCount, totalCount: meta.totalCount }),
          life: 5000
        })
      }
    } catch (error) {
      if (requestId !== rawGpsPointsRequestId) return
      rawGpsPoints.value = []
      console.error('Failed to load raw GPS points:', error)
      toast.add({
        severity: 'error',
        summary: t('maps.timelineMap.rawGpsLoadFailed.summary'),
        detail: t('maps.timelineMap.rawGpsLoadFailed.detail'),
        life: 5000
      })
    } finally {
      if (requestId === rawGpsPointsRequestId) {
        rawGpsPointsLoading.value = false
      }
    }
  }

  // Concurrent requests for the same point share one lookup; failures are not cached.
  const resolveRawGpsPointLocation = async (point) => {
    if (!point?.id) {
      throw new Error('GPS point id is required to resolve location')
    }

    const key = getRawGpsLocationCacheKey(point)
    if (rawGpsLocationCache.has(key)) {
      return rawGpsLocationCache.get(key)
    }

    const promise = technicalDataStore.resolveRawPointLocation(point.id)

    rawGpsLocationCache.set(key, promise)
    try {
      const location = await promise
      rawGpsLocationCache.set(key, location)
      return location
    } catch (error) {
      rawGpsLocationCache.delete(key)
      throw error
    }
  }

  watch(
    () => dateRangeStore.getCurrentDateRange,
    () => {
      if (enabled.value) {
        loadRawGpsPoints()
      }
    },
    { immediate: true }
  )

  watch(enabled, (isEnabled) => {
    if (isEnabled) {
      loadRawGpsPoints()
    }
  })

  return {
    rawGpsPoints,
    rawGpsPointsLoading,
    loadRawGpsPoints,
    resolveRawGpsPointLocation
  }
}
