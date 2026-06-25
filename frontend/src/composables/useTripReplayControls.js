import { computed, onUnmounted, ref, unref, watch } from 'vue'
import {
  DEFAULT_TRIP_REPLAY_SPEED,
  TRIP_REPLAY_SPEED_PRESETS,
  advanceTripReplayElapsed,
  buildTripReplayTimeline,
  clampTripReplayElapsed,
  resolveTripReplayCursor
} from '@/maps/shared/tripReplayMath'
import { getHighlightedTripKey } from '@/maps/shared/highlightedTripData'

const formatReplayClock = (durationMs) => {
  const totalSeconds = Math.max(0, Math.floor((durationMs || 0) / 1000))
  const hours = Math.floor(totalSeconds / 3600)
  const minutes = Math.floor((totalSeconds % 3600) / 60)
  const seconds = totalSeconds % 60

  if (hours > 0) {
    return `${String(hours).padStart(2, '0')}:${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`
  }

  return `${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`
}

export const useTripReplayControls = ({
  enabled,
  activeTrip,
  showPath = true,
  supports3d = false,
  autoShowControls = true
} = {}) => {
  const replayPathPayload = ref({ tripKey: '', points: [] })
  const replayElapsedMs = ref(0)
  const replaySpeedMultiplier = ref(DEFAULT_TRIP_REPLAY_SPEED)
  const replayFollowCamera = ref(true)
  const replayEnable3d = ref(false)
  const replayIsPlaying = ref(false)
  const replayControlsDismissed = ref(false)
  let replayAnimationFrameId = null
  let replayLastFrameTimestamp = null

  const replaySpeedPresets = TRIP_REPLAY_SPEED_PRESETS

  const activeHighlightedTrip = computed(() => {
    const trip = unref(activeTrip)
    return trip?.type === 'trip' ? trip : null
  })

  const activeHighlightedTripKey = computed(() => getHighlightedTripKey(activeHighlightedTrip.value))

  const activeReplayPathPoints = computed(() => {
    const payload = replayPathPayload.value
    if (!payload || payload.tripKey !== activeHighlightedTripKey.value) {
      return []
    }

    return Array.isArray(payload.points) ? payload.points : []
  })

  const replayTimeline = computed(() => {
    if (!activeHighlightedTrip.value || activeReplayPathPoints.value.length < 2) {
      return null
    }

    const preferredDurationMs = (
      Number.isFinite(Number(activeHighlightedTrip.value.tripDuration))
        ? Number(activeHighlightedTrip.value.tripDuration) * 1000
        : null
    )
    const timeline = buildTripReplayTimeline(activeReplayPathPoints.value, { preferredDurationMs })
    return Number.isFinite(timeline.durationMs) && timeline.durationMs > 0 ? timeline : null
  })

  const replayDurationMs = computed(() => replayTimeline.value?.durationMs || 0)
  const replayCursor = computed(() => {
    if (!replayTimeline.value) {
      return null
    }

    return resolveTripReplayCursor(replayTimeline.value, replayElapsedMs.value)
  })

  const showTripReplayBarAvailable = computed(() => (
    Boolean(unref(enabled))
    && Boolean(unref(showPath))
    && Boolean(activeHighlightedTrip.value)
    && Boolean(replayTimeline.value)
  ))
  const showTripReplayBar = computed(() => showTripReplayBarAvailable.value && !replayControlsDismissed.value)
  const showTripReplayRestoreButton = computed(() => (
    showTripReplayBarAvailable.value && replayControlsDismissed.value
  ))

  const shouldAutoShowControls = () => unref(autoShowControls) !== false

  const pathReplayState = computed(() => ({
    enabled: showTripReplayBar.value,
    playing: replayIsPlaying.value,
    elapsedMs: replayElapsedMs.value,
    movementType: activeHighlightedTrip.value?.movementType || 'UNKNOWN',
    followCamera: replayFollowCamera.value,
    enable3d: replayEnable3d.value && Boolean(unref(supports3d)),
    suppressTripPopup: replayIsPlaying.value,
    cursor: replayCursor.value
  }))

  const replaySliderValue = computed(() => {
    if (!replayDurationMs.value) {
      return 0
    }

    return Math.round((replayElapsedMs.value / replayDurationMs.value) * 1000)
  })

  const replayElapsedLabel = computed(() => formatReplayClock(replayElapsedMs.value))
  const replayDurationLabel = computed(() => formatReplayClock(replayDurationMs.value))
  const isReplayPlaying = computed(() => replayIsPlaying.value)

  const cancelReplayAnimationFrame = () => {
    if (replayAnimationFrameId !== null && typeof window !== 'undefined') {
      window.cancelAnimationFrame(replayAnimationFrameId)
    }
    replayAnimationFrameId = null
  }

  const replayTick = (timestamp) => {
    if (!replayIsPlaying.value || !showTripReplayBar.value || !replayTimeline.value) {
      replayIsPlaying.value = false
      replayLastFrameTimestamp = null
      cancelReplayAnimationFrame()
      return
    }

    if (replayLastFrameTimestamp === null) {
      replayLastFrameTimestamp = timestamp
    }

    const deltaMs = Math.max(0, timestamp - replayLastFrameTimestamp)
    replayLastFrameTimestamp = timestamp

    const advancedReplay = advanceTripReplayElapsed({
      elapsedMs: replayElapsedMs.value,
      deltaMs,
      speed: replaySpeedMultiplier.value,
      durationMs: replayDurationMs.value
    })

    replayElapsedMs.value = advancedReplay.elapsedMs

    if (advancedReplay.ended) {
      replayIsPlaying.value = false
      replayLastFrameTimestamp = null
      cancelReplayAnimationFrame()
      return
    }

    if (typeof window !== 'undefined') {
      replayAnimationFrameId = window.requestAnimationFrame(replayTick)
    }
  }

  const pauseTripReplay = () => {
    replayIsPlaying.value = false
    replayLastFrameTimestamp = null
    cancelReplayAnimationFrame()
  }

  const stopTripReplay = () => {
    pauseTripReplay()
    replayElapsedMs.value = 0
  }

  const dismissTripReplayControls = () => {
    pauseTripReplay()
    replayControlsDismissed.value = true
  }

  const restoreTripReplayControls = () => {
    replayControlsDismissed.value = false
  }

  const startTripReplay = () => {
    if (!showTripReplayBar.value || !replayTimeline.value) {
      return
    }

    if (replayElapsedMs.value >= replayDurationMs.value) {
      replayElapsedMs.value = 0
    }

    if (replayIsPlaying.value) {
      return
    }

    replayIsPlaying.value = true
    replayLastFrameTimestamp = null

    if (typeof window !== 'undefined') {
      replayAnimationFrameId = window.requestAnimationFrame(replayTick)
    }
  }

  const toggleReplayPlayback = () => {
    if (replayIsPlaying.value) {
      pauseTripReplay()
      return
    }

    startTripReplay()
  }

  const seekTripReplayByRatio = (ratio) => {
    if (!replayTimeline.value) {
      return
    }

    const safeRatio = Math.max(0, Math.min(1, Number(ratio) || 0))
    replayElapsedMs.value = clampTripReplayElapsed(safeRatio * replayDurationMs.value, replayDurationMs.value)
    replayLastFrameTimestamp = null
  }

  const handleReplaySliderInput = (event) => {
    const rawValue = Number(event?.target?.value)
    if (!Number.isFinite(rawValue)) {
      return
    }

    seekTripReplayByRatio(rawValue / 1000)
  }

  const setReplaySpeed = (speed) => {
    const normalizedSpeed = Number(speed)
    if (!replaySpeedPresets.includes(normalizedSpeed)) {
      return
    }

    replaySpeedMultiplier.value = normalizedSpeed
  }

  const toggleReplayFollowCamera = () => {
    replayFollowCamera.value = !replayFollowCamera.value
  }

  const toggleReplay3d = () => {
    if (!unref(supports3d)) {
      replayEnable3d.value = false
      return
    }

    replayEnable3d.value = !replayEnable3d.value
  }

  const resetTripReplay = ({ resetPreferences = true } = {}) => {
    pauseTripReplay()
    replayElapsedMs.value = 0

    if (resetPreferences) {
      replayFollowCamera.value = true
      replayEnable3d.value = false
    }
  }

  const handleHighlightedTripReplayData = (payload) => {
    if (!payload || typeof payload !== 'object') {
      replayPathPayload.value = { tripKey: '', points: [] }
      return
    }

    replayPathPayload.value = {
      tripKey: payload.tripKey || '',
      points: Array.isArray(payload.points) ? payload.points : []
    }
  }

  const cleanupTripReplay = () => {
    pauseTripReplay()
    replayPathPayload.value = { tripKey: '', points: [] }
  }

  watch(activeHighlightedTripKey, (newTripKey, previousTripKey) => {
    if (newTripKey === previousTripKey) {
      return
    }

    replayControlsDismissed.value = !shouldAutoShowControls()
    replayPathPayload.value = { tripKey: '', points: [] }
    resetTripReplay({ resetPreferences: true })
  })

  watch(showTripReplayBarAvailable, (available) => {
    if (!available) {
      replayControlsDismissed.value = false
      resetTripReplay({ resetPreferences: true })
    } else if (!shouldAutoShowControls()) {
      replayControlsDismissed.value = true
    }
  })

  watch(() => shouldAutoShowControls(), (autoShow) => {
    if (!showTripReplayBarAvailable.value) {
      return
    }

    if (!autoShow) {
      replayControlsDismissed.value = true
    }
  })

  watch(replayDurationMs, (durationMs) => {
    replayElapsedMs.value = clampTripReplayElapsed(replayElapsedMs.value, durationMs)

    if (durationMs <= 0) {
      pauseTripReplay()
    }
  })

  watch(() => Boolean(unref(supports3d)), (isSupported) => {
    if (!isSupported) {
      replayEnable3d.value = false
    }
  })

  onUnmounted(cleanupTripReplay)

  return {
    replayPathPayload,
    replayTimeline,
    replayDurationMs,
    replayCursor,
    showTripReplayBarAvailable,
    showTripReplayBar,
    showTripReplayRestoreButton,
    pathReplayState,
    replaySliderValue,
    replayElapsedLabel,
    replayDurationLabel,
    isReplayPlaying,
    replaySpeedPresets,
    replaySpeedMultiplier,
    replayFollowCamera,
    replayEnable3d,
    pauseTripReplay,
    stopTripReplay,
    dismissTripReplayControls,
    restoreTripReplayControls,
    toggleReplayPlayback,
    handleReplaySliderInput,
    setReplaySpeed,
    toggleReplayFollowCamera,
    toggleReplay3d,
    resetTripReplay,
    handleHighlightedTripReplayData,
    cleanupTripReplay
  }
}
