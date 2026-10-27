import { computed, onBeforeUnmount, ref, unref, watch } from 'vue'
import { selectTripPhotos } from '@/utils/photoMemoryCuration'
import { TRIP_FOLLOW_MS, buildTripPhases } from '@/maps/shared/cinematicCamera'

export const CINEMATIC_SCENE_DURATIONS = Object.freeze({
  intro: 3000,
  overview: 3000,
  place: 4000,
  closing: 4000
})
// The replay API returns one trip per active month, so the film covers every month that has one.
export const MAX_CINEMATIC_TRIPS = 12
// A full reel of four photos gets a little more travel time, so each still shows for over a second.
export const FULL_REEL_EXTRA_FOLLOW_MS = 1000

const validPlace = (place) => {
  const [latitude, longitude] = Array.isArray(place?.coordinates) ? place.coordinates : []
  return Number.isFinite(latitude) && latitude >= -90 && latitude <= 90
    && Number.isFinite(longitude) && longitude >= -180 && longitude <= 180
}

const byStartTime = (first, second) => String(first.startTime).localeCompare(String(second.startTime))

/** The film's trips in date order, each with its own photos; a photo is used by one trip only. */
const selectFilmTrips = (trips, photos) => {
  const usedPhotoIds = new Set()
  return [...trips].sort(byStartTime).slice(0, MAX_CINEMATIC_TRIPS).map((trip) => {
    const tripPhotos = selectTripPhotos(trip, photos, usedPhotoIds)
    tripPhotos.forEach((photo) => usedPhotoIds.add(photo.id))
    return { trip, photos: tripPhotos }
  })
}

export const buildCinematicStory = ({ trips = [], topPlaces = [], photos = [] }) => {
  const tripScenes = selectFilmTrips(trips, photos).map(({ trip, photos: tripPhotos }) => {
    const phases = buildTripPhases({
      followMs: TRIP_FOLLOW_MS + (tripPhotos.length >= 4 ? FULL_REEL_EXTRA_FOLLOW_MS : 0)
    })
    return { type: 'trip', durationMs: phases.durationMs, phases, trip, photos: tripPhotos }
  })
  const placeScenes = tripScenes.length === 0
    ? topPlaces.filter(validPlace).slice(0, 5).map((place) => ({
        type: 'place',
        durationMs: CINEMATIC_SCENE_DURATIONS.place,
        place
      }))
    : []
  const scenes = [
    { type: 'intro', durationMs: CINEMATIC_SCENE_DURATIONS.intro },
    { type: 'overview', durationMs: CINEMATIC_SCENE_DURATIONS.overview },
    ...tripScenes,
    ...placeScenes,
    { type: 'closing', durationMs: CINEMATIC_SCENE_DURATIONS.closing }
  ]

  let startMs = 0
  return scenes.map((scene, index) => {
    const timedScene = { ...scene, index, startMs, endMs: startMs + scene.durationMs }
    startMs = timedScene.endMs
    return timedScene
  })
}

export const useCinematicRewind = ({ story, ready = true } = {}) => {
  const elapsedMs = ref(0)
  const isPlaying = ref(false)
  const buffering = ref(false)
  let animationFrameId = null
  let lastFrameTimestamp = null

  const scenes = computed(() => unref(story) || [])
  const totalDurationMs = computed(() => scenes.value.at(-1)?.endMs || 0)
  const currentSceneIndex = computed(() => {
    if (!scenes.value.length) return -1
    const index = scenes.value.findIndex((scene) => elapsedMs.value < scene.endMs)
    return index === -1 ? scenes.value.length - 1 : index
  })
  const currentScene = computed(() => scenes.value[currentSceneIndex.value] || null)
  const sceneProgress = computed(() => {
    const scene = currentScene.value
    if (!scene?.durationMs) return 0
    return Math.max(0, Math.min(1, (elapsedMs.value - scene.startMs) / scene.durationMs))
  })
  const progressRatio = computed(() => totalDurationMs.value > 0 ? elapsedMs.value / totalDurationMs.value : 0)

  const cancelFrame = () => {
    if (animationFrameId !== null && typeof window !== 'undefined') {
      window.cancelAnimationFrame(animationFrameId)
    }
    animationFrameId = null
  }

  const tick = (timestamp) => {
    if (!isPlaying.value) return
    if (lastFrameTimestamp === null || buffering.value || !unref(ready)) {
      lastFrameTimestamp = timestamp
    } else {
      elapsedMs.value = Math.min(totalDurationMs.value, elapsedMs.value + Math.max(0, timestamp - lastFrameTimestamp))
      lastFrameTimestamp = timestamp
    }

    if (elapsedMs.value >= totalDurationMs.value) {
      isPlaying.value = false
      lastFrameTimestamp = null
      cancelFrame()
      return
    }
    animationFrameId = window.requestAnimationFrame(tick)
  }

  const play = () => {
    if (!unref(ready) || totalDurationMs.value <= 0 || isPlaying.value) return
    if (elapsedMs.value >= totalDurationMs.value) elapsedMs.value = 0
    isPlaying.value = true
    lastFrameTimestamp = null
    animationFrameId = window.requestAnimationFrame(tick)
  }

  const pause = () => {
    isPlaying.value = false
    lastFrameTimestamp = null
    cancelFrame()
  }

  const seekToMs = (value) => {
    elapsedMs.value = Math.max(0, Math.min(totalDurationMs.value, Number(value) || 0))
    lastFrameTimestamp = null
  }
  const seekToRatio = (ratio) => seekToMs((Number(ratio) || 0) * totalDurationMs.value)
  const restart = () => {
    seekToMs(0)
    play()
  }
  const nextScene = () => {
    const next = scenes.value[Math.min(currentSceneIndex.value + 1, scenes.value.length - 1)]
    if (next) seekToMs(next.startMs)
  }
  const previousScene = () => {
    const current = currentScene.value
    const targetIndex = current && elapsedMs.value - current.startMs > 800
      ? currentSceneIndex.value
      : Math.max(0, currentSceneIndex.value - 1)
    const target = scenes.value[targetIndex]
    if (target) seekToMs(target.startMs)
  }
  const setBuffering = (value) => {
    buffering.value = Boolean(value)
    lastFrameTimestamp = null
  }

  watch(scenes, () => {
    pause()
    elapsedMs.value = 0
  })
  onBeforeUnmount(pause)

  return {
    elapsedMs,
    isPlaying,
    buffering,
    totalDurationMs,
    currentSceneIndex,
    currentScene,
    sceneProgress,
    progressRatio,
    play,
    pause,
    restart,
    nextScene,
    previousScene,
    seekToMs,
    seekToRatio,
    setBuffering
  }
}
