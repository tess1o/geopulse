import { computed, getCurrentInstance, onBeforeUnmount, ref, unref } from 'vue'
import mapMatchingService from '@/services/mapMatchingService'
import {
  areVisibleMapMatchingTripsSettled,
  buildActiveMapMatchingPathData,
  chunkMapMatchingIds,
  getMapMatchingPollDelay,
  getPendingMapMatchingTargets,
  mergeMapMatchingTripResolutions
} from '@/utils/mapMatchingTimeline'

export function useTimelineMapMatching({
  enabled,
  visibleTrips,
  rawPathData,
  service = mapMatchingService
} = {}) {
  const resolving = ref(false)
  const resolution = ref(null)
  let pollTimer = null
  let requestToken = 0
  let pollAttempt = 0

  const isEnabled = () => unref(enabled) === true
  const readVisibleTrips = () => {
    const trips = unref(visibleTrips)
    return Array.isArray(trips) ? trips : []
  }

  const matchedSegmentsByTripId = computed(() => {
    const result = new Map()
    const trips = Array.isArray(resolution.value?.trips) ? resolution.value.trips : []
    trips.forEach((trip) => {
      if (trip?.status === 'COMPLETED' && Array.isArray(trip.segments) && trip.segments.length > 0) {
        result.set(Number(trip.tripId), trip.segments)
      }
    })
    return result
  })

  const pageSettled = computed(() => {
    if (!isEnabled() || resolving.value || !resolution.value) {
      return false
    }

    return areVisibleMapMatchingTripsSettled(
      readVisibleTrips().map(trip => trip.id),
      resolution.value.trips
    )
  })

  const displayedMatchedSegmentsByTripId = computed(() => (
    pageSettled.value ? matchedSegmentsByTripId.value : new Map()
  ))

  const matchedTripIds = computed(() => Array.from(displayedMatchedSegmentsByTripId.value.keys()))

  const pendingCount = computed(() => {
    const trips = Array.isArray(resolution.value?.trips) ? resolution.value.trips : []
    return trips.filter(trip => trip?.status === 'QUEUED' || trip?.status === 'PROCESSING').length
  })

  const statusText = computed(() => {
    if (!isEnabled() || pendingCount.value === 0) {
      return ''
    }
    return pendingCount.value === 1
      ? 'Refining route...'
      : `Refining ${pendingCount.value} routes...`
  })

  const activePathData = computed(() => buildActiveMapMatchingPathData({
    rawPathData: unref(rawPathData),
    visibleTrips: readVisibleTrips(),
    matchedSegmentsByTripId: displayedMatchedSegmentsByTripId.value,
    settled: pageSettled.value
  }))

  const clearPoll = () => {
    if (pollTimer) {
      clearTimeout(pollTimer)
      pollTimer = null
    }
  }

  const reset = () => {
    clearPoll()
    requestToken += 1
    resolution.value = null
    resolving.value = false
    pollAttempt = 0
  }

  const stop = () => {
    clearPoll()
    requestToken += 1
  }

  const mergeTrips = (incomingTrips) => {
    const existing = Array.isArray(resolution.value?.trips)
      ? resolution.value.trips
      : []
    resolution.value = {
      ...(resolution.value || {}),
      enabled: true,
      trips: mergeMapMatchingTripResolutions(existing, incomingTrips)
    }
  }

  const schedulePoll = (token) => {
    const pending = getPendingMapMatchingTargets(resolution.value?.trips)
    if (pending.length === 0 || token !== requestToken) return

    const delay = getMapMatchingPollDelay(pending, pollAttempt)
    pollAttempt += 1
    pollTimer = setTimeout(() => pollStatus(token), delay)
  }

  const pollStatus = async (token) => {
    clearPoll()
    if (token !== requestToken || !isEnabled()) return
    const pendingTargets = getPendingMapMatchingTargets(resolution.value?.trips)
    if (pendingTargets.length === 0) return

    try {
      const batches = chunkMapMatchingIds(pendingTargets.map(trip => trip.targetId))
      const responses = await Promise.all(batches.map(targetIds => service.status(targetIds)))
      if (token !== requestToken) return
      responses.forEach((response) => mergeTrips(response?.data || response || []))
    } catch (error) {
      if (token === requestToken) {
        console.warn('Failed to poll map matching status:', error)
      }
    }
    schedulePoll(token)
  }

  const resolve = async () => {
    clearPoll()
    const token = requestToken
    if (!isEnabled()) {
      resolution.value = null
      resolving.value = false
      return
    }

    const tripIds = readVisibleTrips().map(trip => trip.id).filter(Boolean)
    if (tripIds.length === 0) {
      resolution.value = null
      resolving.value = false
      return
    }

    resolving.value = true
    pollAttempt = 0

    try {
      const responses = await Promise.all(chunkMapMatchingIds(tripIds).map(batch => service.resolve(batch)))
      if (token !== requestToken) {
        return
      }
      const trips = []
      let enabledResult = false
      let provider = null
      responses.forEach((response) => {
        const result = response?.data || response || {}
        enabledResult = enabledResult || result.enabled === true
        provider = provider || result.provider
        trips.push(...(Array.isArray(result.trips) ? result.trips : []))
      })
      resolution.value = { enabled: enabledResult, provider, trips }
    } catch (error) {
      if (token !== requestToken) {
        return
      }
      console.warn('Failed to resolve map matching:', error)
      resolution.value = null
    } finally {
      if (token === requestToken) {
        resolving.value = false
      }
    }

    schedulePoll(token)
  }

  if (getCurrentInstance()) {
    onBeforeUnmount(stop)
  }

  return {
    resolving,
    resolution,
    pageSettled,
    pendingCount,
    statusText,
    activePathData,
    matchedTripIds,
    resolve,
    reset,
    stop
  }
}
