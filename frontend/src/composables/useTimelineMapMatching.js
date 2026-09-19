import { computed, getCurrentInstance, onBeforeUnmount, ref, unref, watch } from 'vue'
import { useMapMatchingStore } from '@/stores/mapMatching'
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
  excludedMovementTypes,
  visibleTrips,
  rawPathData,
  store = null
} = {}) {
  const mapMatchingStore = store || useMapMatchingStore()
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

  const normalizeMovementType = (value) => {
    const normalized = String(value || '').trim().toUpperCase()
    return {
      WALKING: 'WALK',
      RUN: 'RUNNING',
      CYCLING: 'BICYCLE',
      BIKE: 'BICYCLE',
      DRIVING: 'CAR'
    }[normalized] || normalized
  }
  const excludedMovementTypeSet = computed(() => new Set(
    (Array.isArray(unref(excludedMovementTypes)) ? unref(excludedMovementTypes) : [])
      .map(normalizeMovementType)
  ))
  const eligibleVisibleTrips = computed(() => readVisibleTrips().filter(trip => (
    !excludedMovementTypeSet.value.has(normalizeMovementType(trip?.movementType))
  )))
  const eligibleTripIds = computed(() => new Set(
    eligibleVisibleTrips.value.map(trip => Number(trip.id))
  ))

  const matchedSegmentsByTripId = computed(() => {
    const result = new Map()
    const trips = Array.isArray(resolution.value?.trips) ? resolution.value.trips : []
    trips.forEach((trip) => {
      if (eligibleTripIds.value.has(Number(trip?.tripId))
          && trip?.status === 'COMPLETED'
          && Array.isArray(trip.segments)
          && trip.segments.length > 0) {
        result.set(Number(trip.tripId), trip.segments)
      }
    })
    return result
  })

  // What map matching did with each visible trip, keyed by trip id. Unlike the matched segments this is
  // not gated behind pageSettled: the state is stable once a trip leaves the queue, and the trip card
  // only reads it when the user opens the context menu. UNAVAILABLE is left out - it only shows up while
  // the feature is being switched off, and there is nothing useful to tell the user about it.
  const mapMatchingByTripId = computed(() => {
    const result = new Map()
    const trips = Array.isArray(resolution.value?.trips) ? resolution.value.trips : []
    trips.forEach((trip) => {
      if (!eligibleTripIds.value.has(Number(trip?.tripId))
          || !trip?.status
          || trip.status === 'UNAVAILABLE') return
      result.set(Number(trip.tripId), {
        status: trip.status,
        targetId: trip.targetId ?? null,
        error: trip.error ?? null,
        completedAt: trip.completedAt ?? null
      })
    })
    return result
  })

  const pageSettled = computed(() => {
    if (!isEnabled() || resolving.value || !resolution.value) {
      return false
    }

    return areVisibleMapMatchingTripsSettled(
      eligibleVisibleTrips.value.map(trip => trip.id),
      resolution.value.trips
    )
  })

  const displayedMatchedSegmentsByTripId = computed(() => (
    pageSettled.value ? matchedSegmentsByTripId.value : new Map()
  ))

  const matchedTripIds = computed(() => Array.from(displayedMatchedSegmentsByTripId.value.keys()))

  const pendingCount = computed(() => {
    const trips = Array.isArray(resolution.value?.trips) ? resolution.value.trips : []
    return trips.filter(trip => (
      eligibleTripIds.value.has(Number(trip?.tripId))
      && (trip?.status === 'QUEUED' || trip?.status === 'PROCESSING')
    )).length
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
      const responses = await Promise.all(batches.map(targetIds => mapMatchingStore.status(targetIds)))
      if (token !== requestToken) return
      responses.forEach((trips) => mergeTrips(trips || []))
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
      const responses = await Promise.all(chunkMapMatchingIds(tripIds).map(batch => mapMatchingStore.resolve(batch)))
      if (token !== requestToken) {
        return
      }
      const trips = []
      let enabledResult = false
      let provider = null
      responses.forEach((result = {}) => {
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

  watch(
    () => readVisibleTrips()
      .map(trip => `${trip?.id}:${normalizeMovementType(trip?.movementType)}`)
      .join('|'),
    () => {
      if (!isEnabled() || (!resolution.value && !resolving.value)) return
      reset()
      void resolve()
    }
  )

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
    mapMatchingByTripId,
    resolve,
    reset,
    stop
  }
}
