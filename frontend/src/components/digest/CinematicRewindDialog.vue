<template>
  <Dialog
    :visible="visible"
    modal
    append-to="body"
    :show-header="false"
    :closable="false"
    :dismissable-mask="false"
    :draggable="false"
    :close-on-escape="false"
    class="cinematic-rewind-dialog"
    @update:visible="handleDialogVisibility"
  >
    <div
      ref="playerRef"
      class="cinematic-player"
      tabindex="-1"
      :style="{ '--cinematic-accent': appearance.activePathColor }"
      @keydown="handleKeydown"
    >
      <button class="cinematic-close" type="button" :aria-label="t('analytics.cinematicRewind.close')" @click="close">
        <i class="pi pi-times"></i>
      </button>

      <div v-if="loading || sessionPhotos === null" class="cinematic-state">
        <ProgressSpinner />
        <p>{{ t('analytics.cinematicRewind.preparing') }}</p>
      </div>
      <div v-else-if="loadError" class="cinematic-state cinematic-state--error">
        <i class="pi pi-exclamation-triangle"></i>
        <h2>{{ t('analytics.cinematicRewind.loadFailed') }}</h2>
        <p>{{ loadError }}</p>
        <Button :label="t('analytics.cinematicRewind.tryAgain')" icon="pi pi-refresh" @click="loadReplay" />
      </div>
      <div v-else-if="!hasStoryContent" class="cinematic-state">
        <i class="pi pi-compass"></i>
        <h2>{{ t('analytics.cinematicRewind.notEnoughActivity') }}</h2>
      </div>
      <template v-else>
        <BaseMap
          map-id="cinematic-rewind-map"
          :center="initialMapCenter"
          :zoom="2"
          height="100%"
          width="100%"
          :map-render-mode="MAP_RENDER_MODES.VECTOR"
          :style-override="mapStyle"
          :enable-fullscreen="false"
          :show-scale="false"
          :options="{ interactive: false, attributionControl: true }"
          @map-ready="handleMapReady"
          @map-warning="handleMapWarning"
        />

        <div v-if="unsupported" class="cinematic-state cinematic-state--overlay">
          <i class="pi pi-desktop"></i>
          <h2>{{ t('analytics.cinematicRewind.unsupported') }}</h2>
        </div>

        <div v-else class="cinematic-vignette" aria-hidden="true"></div>
        <div v-if="projectionFallback && !unsupported" class="cinematic-notice">
          {{ t('analytics.cinematicRewind.mercatorFallback') }}
        </div>
        <div v-if="(buffering || waitingForPhotos) && !unsupported" class="cinematic-buffering">
          <i class="pi pi-spin pi-spinner"></i>
          {{ buffering ? t('analytics.cinematicRewind.loadingMap') : t('analytics.cinematicRewind.loadingPhotos') }}
        </div>

        <Transition name="cinematic-card" mode="out-in">
        <section v-if="currentScene?.type === 'intro'" :key="currentSceneIndex" class="cinematic-card cinematic-card--hero">
          <p>{{ isMonthly ? t('analytics.cinematicRewind.yourMonth') : t('analytics.cinematicRewind.yourYear') }}</p>
          <h1>{{ periodTitle }}</h1>
          <span>{{ t('analytics.cinematicRewind.introSummary', { distance: formatDistanceRounded(metrics.totalDistance || 0), trips: metrics.tripCount || 0 }) }}</span>
        </section>

        <section v-else-if="currentScene?.type === 'overview'" :key="currentSceneIndex" class="cinematic-card cinematic-card--caption">
          <p>{{ isMonthly ? t('analytics.cinematicRewind.overviewMonth') : t('analytics.cinematicRewind.overview') }}</p>
          <h2>{{ t('analytics.cinematicRewind.placesAndRoutes', { places: metrics.citiesVisited || 0, trips: metrics.tripCount || 0 }) }}</h2>
        </section>

        <section
          v-else-if="currentScene?.type === 'trip' && showChapterCard"
          :key="`${currentSceneIndex}-chapter`"
          class="cinematic-card cinematic-card--chapter"
        >
          <h1>{{ chapterLabel(currentScene.trip) }}</h1>
          <p v-if="tripTitle(currentScene.trip)">{{ tripTitle(currentScene.trip) }}</p>
        </section>

        <section v-else-if="currentScene?.type === 'trip'" :key="currentSceneIndex" class="cinematic-card cinematic-card--caption">
          <h2>{{ chapterLabel(currentScene.trip) }}</h2>
          <p v-if="tripTitle(currentScene.trip)" class="cinematic-route">{{ tripTitle(currentScene.trip) }}</p>
          <span>{{ tripDetails(currentScene.trip) }}</span>
        </section>

        <section v-else-if="currentScene?.type === 'place'" :key="currentSceneIndex" class="cinematic-card cinematic-card--caption">
          <p>{{ t('analytics.cinematicRewind.placeHighlight') }}</p>
          <h2>{{ currentScene.place.name }}</h2>
          <span>{{ t('analytics.cinematicRewind.visits', { count: currentScene.place.visits || 0 }) }}</span>
        </section>

        <section v-else-if="currentScene?.type === 'closing'" :key="currentSceneIndex" class="cinematic-card cinematic-card--hero">
          <p>{{ isMonthly ? t('analytics.cinematicRewind.monthInMotion') : t('analytics.cinematicRewind.yearInMotion') }}</p>
          <h1>{{ formatDistanceRounded(metrics.totalDistance || 0) }}</h1>
          <span>{{ t('analytics.cinematicRewind.closingSummary', { cities: metrics.citiesVisited || 0, trips: metrics.tripCount || 0, days: metrics.activeDays || 0 }) }}</span>
        </section>
        </Transition>

        <figure v-if="photoReel" class="cinematic-reel">
          <Transition name="cinematic-reel-photo" mode="out-in">
            <div :key="photoReel.current.photo.id" class="cinematic-reel-current">
              <button
                type="button"
                class="cinematic-photo-frame"
                :aria-label="t('analytics.cinematicRewind.openPhoto')"
                @click="openPhoto(photoReel.index)"
              >
                <img :src="photoReel.current.url" :alt="photoAlt">
              </button>
              <p class="cinematic-photo-caption">{{ formatPhotoDate(photoReel.current.photo.takenAt) }}</p>
            </div>
          </Transition>
          <ol v-if="photoReel.total > 1" class="cinematic-reel-strip" :aria-label="t('analytics.cinematicRewind.photoCount', { current: photoReel.index + 1, total: photoReel.total })">
            <li
              v-for="(entry, index) in photoReel.shown"
              :key="entry.photo.id"
              :class="{ current: index === photoReel.index }"
            >
              <button type="button" :aria-label="t('analytics.cinematicRewind.openPhoto')" @click="openPhoto(index)">
                <img :src="entry.url" alt="">
              </button>
            </li>
          </ol>
        </figure>

        <nav
          v-if="!unsupported"
          class="cinematic-months"
          :style="{ '--chapter-count': chapterStrip.length }"
          :aria-label="isMonthly ? t('analytics.cinematicRewind.chapters') : t('analytics.cinematicRewind.months')"
        >
          <button
            v-for="entry in chapterStrip"
            :key="entry.chapter"
            type="button"
            :class="{ 'has-trip': entry.scene, active: entry.chapter === activeChapter }"
            :disabled="!entry.scene"
            :aria-label="t('analytics.cinematicRewind.jumpToMonth', { month: entry.name })"
            :aria-current="entry.chapter === activeChapter ? 'step' : undefined"
            @click="seekToMs(entry.scene.startMs)"
          >
            {{ entry.short }}
          </button>
        </nav>

        <div v-if="!unsupported" class="cinematic-controls" role="group" :aria-label="t('analytics.cinematicRewind.controls')">
          <button type="button" :aria-label="t('analytics.cinematicRewind.restart')" @click="restart"><i class="pi pi-replay"></i></button>
          <button type="button" :aria-label="t('analytics.cinematicRewind.previous')" @click="previousScene"><i class="pi pi-step-backward"></i></button>
          <button type="button" :aria-label="isPlaying ? t('analytics.cinematicRewind.pause') : t('analytics.cinematicRewind.play')" @click="togglePlayback">
            <i :class="isPlaying ? 'pi pi-pause' : 'pi pi-play'"></i>
          </button>
          <button type="button" :aria-label="t('analytics.cinematicRewind.next')" @click="nextScene"><i class="pi pi-step-forward"></i></button>
          <span>{{ formatClock(elapsedMs) }}</span>
          <input
            type="range"
            min="0"
            max="1000"
            step="1"
            :value="Math.round(progressRatio * 1000)"
            :aria-label="t('analytics.cinematicRewind.seek')"
            @input="seekFromInput"
          >
          <span>{{ formatClock(totalDurationMs) }}</span>
        </div>
      </template>
    </div>
  </Dialog>

  <PhotoViewerDialog
    v-model:visible="viewerVisible"
    :photos="viewerPhotos"
    :initial-photo-index="viewerIndex"
    :allow-show-on-map="false"
    :preloaded-blob-url-resolver="(photoId) => photoUrls.get(photoId)"
  />
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, ref, shallowRef, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import Button from 'primevue/button'
import Dialog from 'primevue/dialog'
import ProgressSpinner from 'primevue/progressspinner'
import BaseMap from '@/components/maps/BaseMap.vue'
import PhotoViewerDialog from '@/components/dialogs/PhotoViewerDialog.vue'
import { useDigestStore } from '@/stores/digest'
import { useTimezone } from '@/composables/useTimezone'
import { PLACE_PHOTO_WINDOW, buildCinematicStory, isValidRewindPlace, useCinematicRewind } from '@/composables/useCinematicRewind'
import { buildTripReplayTimeline, resolveTripReplayCursor, unwrapReplayLongitudes } from '@/maps/shared/tripReplayMath'
import {
  buildCinematicCameraPlan,
  resolveCinematicCamera,
  resolveCinematicHome,
  tripRouteProgress
} from '@/maps/shared/cinematicCamera'
import { MAP_RENDER_MODES, resolveMapEngineModeFromInstance } from '@/maps/contracts/mapContracts'
import { PATH_OUTLINE_EXTRA_WIDTH, getOutlineColor } from '@/maps/shared/mapAppearance'
import { resolveProfileMapLibreStyle } from '@/maps/runtime/mapSourceResolver'
import { useMapAppearance } from '@/composables/useMapAppearance'
import {
  createFeatureCollection,
  ensureGeoJsonSource,
  ensureLayer,
  getMapLibreSource,
  nextLayerToken,
  removeLayers,
  removeSources
} from '@/maps/vector/utils/maplibreLayerUtils'
import { formatDistanceRounded } from '@/utils/calculationsHelpers'
import { formatDuration } from '@/utils/durationFormatter'
import { imageService } from '@/utils/imageService'
import { getMovementTypeEmoji } from '@/constants/movementTypes'
import { formatApiErrorDetail } from '@/utils/apiErrorDetail'

const props = defineProps({
  visible: { type: Boolean, default: false },
  year: { type: Number, required: true },
  // The month of a monthly Rewind; null plays the whole year.
  month: { type: Number, default: null },
  digest: { type: Object, default: () => ({}) },
  photosPayload: { type: Object, default: () => ({ photos: [], settled: true }) }
})
const emit = defineEmits(['update:visible'])

const { t } = useI18n()
const timezone = useTimezone()
const digestStore = useDigestStore()
const playerRef = ref(null)
const replayData = ref(null)
const loading = ref(false)
const loadError = ref('')
const map = ref(null)
const mapReady = ref(false)
const initialMapIdle = ref(false)
const unsupported = ref(false)
const projectionFallback = ref(false)
const photoUrls = ref(new Map())
// The photos the film was opened with; later gallery updates must not rebuild (and restart) the story.
const sessionPhotos = ref(null)
// Per scene index: 'loading' or 'done'. Photos load a few scenes ahead of the film rather than all up front.
const scenePhotoStatus = ref(new Map())
const viewerVisible = ref(false)
const viewerPhotos = ref([])
const viewerIndex = ref(0)
const reducedMotion = ref(false)
let loadToken = 0
let photoLoadToken = 0
let bufferTimer = null
let pendingIdleHandler = null
let styleLoadHandler = null
let resizeHandler = null
let tilesMissingSince = null
let autoStarted = false
const cameraPlan = ref([])
// BaseMap takes [lat, lng] and re-centres on every new array, so this is set once per loaded replay.
const initialMapCenter = ref([0, 0])

// How long tiles may stay missing during playback before the film holds for them.
const TILE_STALL_MS = 900
const TILE_WAIT_MAX_MS = 2500
const INITIAL_IDLE_MAX_MS = 4000
// Immich can stall on an asset; a photo it has not served by then is left out rather than holding the film.
const PHOTO_PRELOAD_MAX_MS = 8000
// The scene playing and the next scenes with photos that load alongside it.
const PHOTO_LOOKAHEAD_SCENES = 2
// The user's own map look: the tiles of their timeline map and the path colours, width and outline of
// Profile → Appearance (the trip being replayed uses the highlighted-trip colour).
const appearance = useMapAppearance()
const mapStyle = shallowRef(null)


const token = nextLayerToken('gp-cinematic')
const ids = {
  allSource: `${token}-all-source`,
  allLayer: `${token}-all-layer`,
  ghostSource: `${token}-ghost-source`,
  ghostLayer: `${token}-ghost-layer`,
  progressSource: `${token}-progress-source`,
  allOutlineLayer: `${token}-all-outline-layer`,
  progressGlowLayer: `${token}-progress-glow-layer`,
  progressLayer: `${token}-progress-layer`,
  markerSource: `${token}-marker-source`,
  markerHaloLayer: `${token}-marker-halo-layer`,
  markerLayer: `${token}-marker-layer`,
  placesSource: `${token}-places-source`,
  placesLayer: `${token}-places-layer`
}
const layerIds = [ids.allOutlineLayer, ids.allLayer, ids.ghostLayer, ids.placesLayer, ids.progressGlowLayer, ids.progressLayer, ids.markerHaloLayer, ids.markerLayer]
const sourceIds = [ids.allSource, ids.ghostSource, ids.progressSource, ids.markerSource, ids.placesSource]

const metrics = computed(() => props.digest?.metrics || {})
const topPlaces = computed(() => Array.isArray(props.digest?.topPlaces) ? props.digest.topPlaces : [])
const story = computed(() => buildCinematicStory({
  trips: replayData.value?.trips || [],
  topPlaces: topPlaces.value,
  photos: sessionPhotos.value || []
}))
const hasStoryContent = computed(() => (replayData.value?.trips?.length || 0) > 0 || topPlaces.value.some(isValidRewindPlace))
const controllerReady = computed(() => props.visible && mapReady.value && initialMapIdle.value && sessionPhotos.value !== null && !unsupported.value)
const scenePhotos = (scene) => (scene?.photos || []).filter((photo) => photo?.id && (photo.previewUrl || photo.thumbnailUrl))
// Read lazily by the film clock, after useCinematicRewind below has defined the current scene.
const currentScenePhotosReady = computed(() => scenePhotos(currentScene.value).length === 0
  || scenePhotoStatus.value.get(currentSceneIndex.value) === 'done')
const {
  elapsedMs,
  isPlaying,
  buffering,
  totalDurationMs,
  currentScene,
  currentSceneIndex,
  sceneProgress,
  progressRatio,
  play,
  pause,
  restart,
  previousScene,
  nextScene,
  seekToMs,
  seekToRatio,
  setBuffering
} = useCinematicRewind({ story, ready: controllerReady, hold: computed(() => !currentScenePhotosReady.value) })
const waitingForPhotos = computed(() => isPlaying.value && !currentScenePhotosReady.value)

const isMonthly = computed(() => Number.isInteger(props.month))
const midMonth = (month) => `${props.year}-${String(month).padStart(2, '0')}-15T12:00:00Z`
const periodTitle = computed(() => isMonthly.value ? timezone.format(midMonth(props.month), 'MMMM YYYY') : String(props.year))

const activeTimeline = computed(() => currentScene.value?.type === 'trip'
  ? buildTripReplayTimeline(currentScene.value.trip.path, {
      preferredDurationMs: Number(currentScene.value.trip.tripDuration || 0) * 1000
    })
  : null)
const activeCursor = computed(() => activeTimeline.value
  ? resolveTripReplayCursor(
      activeTimeline.value,
      activeTimeline.value.durationMs * tripRouteProgress(sceneProgress.value, currentScene.value.phases)
    )
  : null)
// The trip's photos play one after another, large, while the camera travels to the destination; a strip below
// collects each one as it appears and stays for the rest on the finished route.
// A place's photos play the same way once the camera has arrived.
const photoWindow = (scene) => {
  if (scene?.type === 'trip') return { start: scene.phases.flightEnd, end: scene.phases.followEnd }
  if (scene?.type === 'place') return PLACE_PHOTO_WINDOW
  return null
}
const loadedScenePhotos = (scene) => (scene?.photos || [])
  .map((photo) => ({ photo, url: photoUrls.value.get(photo.id) }))
  .filter((entry) => entry.url)
const photoReel = computed(() => {
  const scene = currentScene.value
  const window = photoWindow(scene)
  if (!window || sceneProgress.value < window.start) return null
  const loaded = loadedScenePhotos(scene)
  if (loaded.length === 0) return null
  const travel = (sceneProgress.value - window.start) / (window.end - window.start)
  const index = Math.min(loaded.length - 1, Math.floor(travel * loaded.length))
  return { current: loaded[index], index, shown: loaded.slice(0, index + 1), total: loaded.length }
})
const photoAlt = computed(() => currentScene.value?.type === 'place'
  ? t('analytics.cinematicRewind.photoAltPlace', { place: currentScene.value.place.name })
  : t('analytics.cinematicRewind.photoAlt', { month: chapterLabel(currentScene.value?.trip) }))
// The month fills the screen while the camera flies to the trip, then becomes the caption headline.
const showChapterCard = computed(() => currentScene.value?.type === 'trip' && sceneProgress.value < currentScene.value.phases.flightEnd)
// A yearly Rewind has a chapter per month; a monthly one per seven days (1–7, 8–14, …), as the replay API picks them.
const DAYS_PER_MONTHLY_CHAPTER = 7
const chapterOf = (value) => {
  const [, month, day] = timezone.format(value, 'YYYY-MM-DD').split('-').map(Number)
  return isMonthly.value ? Math.floor((day - 1) / DAYS_PER_MONTHLY_CHAPTER) : month
}
const chapterScene = (chapter) => story.value.find((scene) => scene.type === 'trip' && chapterOf(scene.trip.startTime) === chapter) || null
const chapterStrip = computed(() => {
  if (!isMonthly.value) {
    return Array.from({ length: 12 }, (_, index) => ({
      chapter: index + 1,
      short: timezone.format(midMonth(index + 1), 'MMM'),
      name: timezone.format(midMonth(index + 1), 'MMMM'),
      scene: chapterScene(index + 1)
    }))
  }
  const daysInMonth = new Date(Date.UTC(props.year, props.month, 0)).getUTCDate()
  const monthName = timezone.format(midMonth(props.month), 'MMMM')
  return Array.from({ length: Math.ceil(daysInMonth / DAYS_PER_MONTHLY_CHAPTER) }, (_, chapter) => {
    const days = `${chapter * DAYS_PER_MONTHLY_CHAPTER + 1}–${Math.min(daysInMonth, (chapter + 1) * DAYS_PER_MONTHLY_CHAPTER)}`
    return { chapter, short: days, name: `${days} ${monthName}`, scene: chapterScene(chapter) }
  })
})
const activeChapter = computed(() => currentScene.value?.type === 'trip' ? chapterOf(currentScene.value.trip.startTime) : null)

const formatClock = (value) => {
  const seconds = Math.max(0, Math.floor((Number(value) || 0) / 1000))
  return `${String(Math.floor(seconds / 60)).padStart(2, '0')}:${String(seconds % 60).padStart(2, '0')}`
}
const chapterLabel = (trip) => trip ? timezone.format(trip.startTime, isMonthly.value ? 'D MMMM' : 'MMMM') : ''
// A named trip shows its name; a single trip where it went, as far as the timeline knows.
const tripTitle = (trip) => {
  if (trip?.name) return trip.name
  const from = trip?.originName
  const to = trip?.destinationName
  if (from && to) return from === to ? from : t('analytics.cinematicRewind.route', { from, to })
  if (to) return t('analytics.cinematicRewind.routeTo', { place: to })
  if (from) return t('analytics.cinematicRewind.routeFrom', { place: from })
  return ''
}
const tripDetails = (trip) => {
  const distance = formatDistanceRounded(trip?.distanceMeters || 0)
  const duration = formatDuration(trip?.tripDuration || 0)
  return trip?.name
    ? `${t('analytics.cinematicRewind.legs', { count: trip.legCount || 0 })} · ${distance} · ${duration}`
    : `${movementLabel(trip?.movementType)} · ${distance} · ${duration}`
}
const formatPhotoDate = (value) => value ? timezone.formatDateDisplay(value) : ''
const movementLabel = (movementType) => {
  const type = String(movementType || 'UNKNOWN').toUpperCase()
  const key = `movementTypes.${type}`
  return `${getMovementTypeEmoji(type)} ${t(key)}`
}

const loadReplay = async () => {
  const requestToken = ++loadToken
  loading.value = true
  loadError.value = ''
  replayData.value = null
  try {
    const data = isMonthly.value
      ? await digestStore.fetchMonthlyReplay(props.year, props.month)
      : await digestStore.fetchYearlyReplay(props.year)
    if (requestToken === loadToken) {
      replayData.value = data
      // Open over home so the first frame is never somewhere the user has not been.
      const [longitude, latitude] = resolveCinematicHome(story.value)
      initialMapCenter.value = [latitude, longitude]
    }
  } catch (error) {
    if (requestToken === loadToken) loadError.value = formatApiErrorDetail(error, t('analytics.cinematicRewind.loadFailed'))
  } finally {
    if (requestToken === loadToken) loading.value = false
  }
}

const revokePhotoUrls = () => {
  photoUrls.value.forEach((url) => imageService.revokeBlobUrl(url))
  photoUrls.value = new Map()
}

const verifyPhotoDecodes = async (url) => {
  const image = new Image()
  image.src = url
  if (typeof image.decode === 'function') {
    await image.decode()
    return
  }
  await new Promise((resolve, reject) => {
    image.onload = resolve
    image.onerror = reject
  })
}

/** A decodable blob URL for the photo, or null when Immich cannot serve it. */
const loadPhotoUrl = async (photo) => {
  try {
    // The reel shows photos up to ~28rem wide, so the larger preview beats the ~480 px thumbnail.
    const url = await imageService.loadAuthenticatedImage(photo.previewUrl || photo.thumbnailUrl)
    try {
      await verifyPhotoDecodes(url)
      return url
    } catch {
      imageService.revokeBlobUrl(url)
      return null
    }
  } catch {
    return null
  }
}

// A photo Immich has not served within the limit resolves to null, and its late blob is freed.
const loadPhotoWithin = (photo) => new Promise((resolve) => {
  let timedOut = false
  const timer = setTimeout(() => {
    timedOut = true
    resolve([photo.id, null])
  }, PHOTO_PRELOAD_MAX_MS)
  loadPhotoUrl(photo).then((url) => {
    if (timedOut) {
      if (url) imageService.revokeBlobUrl(url)
      return
    }
    clearTimeout(timer)
    resolve([photo.id, url])
  })
})

const setScenePhotoStatus = (index, status) => {
  scenePhotoStatus.value = new Map(scenePhotoStatus.value).set(index, status)
}

const loadScenePhotos = async (index) => {
  const scene = story.value[index]
  if (!scene || scenePhotoStatus.value.has(index)) return
  const requestToken = photoLoadToken
  setScenePhotoStatus(index, 'loading')
  const loaded = await Promise.all(scenePhotos(scene)
    .filter((photo) => !photoUrls.value.has(photo.id))
    .map(loadPhotoWithin))
  if (requestToken !== photoLoadToken) {
    loaded.forEach(([, url]) => { if (url) imageService.revokeBlobUrl(url) })
    return
  }
  const urls = new Map(photoUrls.value)
  loaded.forEach(([id, url]) => { if (url) urls.set(id, url) })
  photoUrls.value = urls
  setScenePhotoStatus(index, 'done')
}

// Loads the scene at `index` and the next scenes that have photos, so they are ready before the film gets there.
const loadPhotosFrom = (index) => {
  if (sessionPhotos.value === null || index < 0) return
  let withPhotos = 0
  for (let next = index; next < story.value.length && withPhotos <= PHOTO_LOOKAHEAD_SCENES; next += 1) {
    if (scenePhotos(story.value[next]).length === 0) continue
    withPhotos += 1
    loadScenePhotos(next)
  }
}

const restartPhotoLoading = () => {
  photoLoadToken += 1
  revokePhotoUrls()
  scenePhotoStatus.value = new Map()
  loadPhotosFrom(Math.max(0, currentSceneIndex.value))
}

const openPhoto = (index) => {
  pause()
  viewerPhotos.value = loadedScenePhotos(currentScene.value).map((entry) => entry.photo)
  viewerIndex.value = index
  viewerVisible.value = true
}

const pathCoordinates = (trip) => unwrapReplayLongitudes((Array.isArray(trip?.path) ? trip.path : [])
  .map((point) => [point.longitude, point.latitude]))

const staticRouteData = () => createFeatureCollection((replayData.value?.trips || [])
  .map((trip) => pathCoordinates(trip))
  .filter((coordinates) => coordinates.length >= 2)
  .map((coordinates) => ({ type: 'Feature', geometry: { type: 'LineString', coordinates }, properties: {} })))
const placeData = () => createFeatureCollection(topPlaces.value.filter(isValidRewindPlace).map((place) => ({
  type: 'Feature',
  geometry: { type: 'Point', coordinates: [place.coordinates[1], place.coordinates[0]] },
  properties: { name: place.name || '' }
})))

const setupLayers = () => {
  if (!map.value || unsupported.value) return
  ensureGeoJsonSource(map.value, ids.allSource, staticRouteData())
  ensureGeoJsonSource(map.value, ids.ghostSource, createFeatureCollection([]))
  ensureGeoJsonSource(map.value, ids.progressSource, createFeatureCollection([]))
  ensureGeoJsonSource(map.value, ids.markerSource, createFeatureCollection([]))
  ensureGeoJsonSource(map.value, ids.placesSource, placeData())
  const roundLine = { 'line-cap': 'round', 'line-join': 'round' }
  const { defaultPathColor, activePathColor, pathWidth, highlightedPathWidth, outlineEnabled } = appearance.value
  // With the outline setting on, routes get the same contrasting casing as on the timeline; without it the
  // replayed trip still gets a soft glow of its own colour so it stands out over any tiles.
  const outlineWidth = (width) => width + PATH_OUTLINE_EXTRA_WIDTH
  const casing = (color, width) => outlineEnabled
    ? { 'line-color': getOutlineColor(color), 'line-width': outlineWidth(width), 'line-opacity': 1 }
    : null
  const allCasing = casing(defaultPathColor, pathWidth)
  if (allCasing) {
    ensureLayer(map.value, { id: ids.allOutlineLayer, type: 'line', source: ids.allSource, layout: roundLine, paint: allCasing })
  }
  ensureLayer(map.value, { id: ids.allLayer, type: 'line', source: ids.allSource, layout: roundLine, paint: { 'line-color': defaultPathColor, 'line-width': pathWidth, 'line-opacity': 0.9 } })
  ensureLayer(map.value, { id: ids.ghostLayer, type: 'line', source: ids.ghostSource, layout: roundLine, paint: { 'line-color': activePathColor, 'line-width': Math.max(2, pathWidth - 1), 'line-opacity': 0.6, 'line-dasharray': [1, 2] } })
  ensureLayer(map.value, { id: ids.placesLayer, type: 'circle', source: ids.placesSource, paint: { 'circle-radius': 5, 'circle-color': '#f59e0b', 'circle-stroke-color': '#fff', 'circle-stroke-width': 2 } })
  ensureLayer(map.value, {
    id: ids.progressGlowLayer,
    type: 'line',
    source: ids.progressSource,
    layout: roundLine,
    paint: casing(activePathColor, highlightedPathWidth)
      || { 'line-color': activePathColor, 'line-width': highlightedPathWidth + 10, 'line-blur': 8, 'line-opacity': 0.35 }
  })
  ensureLayer(map.value, { id: ids.progressLayer, type: 'line', source: ids.progressSource, layout: roundLine, paint: { 'line-color': activePathColor, 'line-width': highlightedPathWidth } })
  ensureLayer(map.value, { id: ids.markerHaloLayer, type: 'circle', source: ids.markerSource, paint: { 'circle-radius': 18, 'circle-color': activePathColor, 'circle-opacity': 0.3, 'circle-blur': 0.8 } })
  ensureLayer(map.value, { id: ids.markerLayer, type: 'circle', source: ids.markerSource, paint: { 'circle-radius': 6.5, 'circle-color': '#fff', 'circle-stroke-color': activePathColor, 'circle-stroke-width': 3 } })
}

// Space-blue sky with an atmosphere glow around the globe that fades out as the camera comes down.
const applySky = () => {
  try {
    map.value?.setSky?.({
      'sky-color': '#0b1d4d',
      'horizon-color': '#93c5fd',
      'sky-horizon-blend': 0.6,
      'atmosphere-blend': ['interpolate', ['linear'], ['zoom'], 0, 1, 5, 1, 8, 0]
    })
  } catch {
    // Styles that reject a sky keep their own look.
  }
}

const buildCameraPlan = () => {
  if (!map.value || unsupported.value) return
  const container = map.value.getContainer?.()
  cameraPlan.value = buildCinematicCameraPlan({
    scenes: story.value,
    viewport: { width: container?.clientWidth || window.innerWidth, height: container?.clientHeight || window.innerHeight }
  })
}

const updateRouteLayers = (scene, cursor) => {
  const timeline = activeTimeline.value
  let progressCoordinates = []
  if (timeline && cursor) {
    progressCoordinates = timeline.points.slice(0, cursor.segmentIndex + 1)
      .map((point) => [point.longitude, point.latitude])
    progressCoordinates.push([cursor.longitude, cursor.latitude])
    progressCoordinates = unwrapReplayLongitudes(progressCoordinates)
  }
  const ghostCoordinates = scene?.type === 'trip' ? pathCoordinates(scene.trip) : []
  getMapLibreSource(map.value, ids.ghostSource)?.setData(createFeatureCollection(
    ghostCoordinates.length >= 2
      ? [{ type: 'Feature', geometry: { type: 'LineString', coordinates: ghostCoordinates }, properties: {} }]
      : []
  ))
  getMapLibreSource(map.value, ids.progressSource)?.setData(createFeatureCollection(
    progressCoordinates.length >= 2
      ? [{ type: 'Feature', geometry: { type: 'LineString', coordinates: progressCoordinates }, properties: {} }]
      : []
  ))
  getMapLibreSource(map.value, ids.markerSource)?.setData(createFeatureCollection(
    cursor
      ? [{ type: 'Feature', geometry: { type: 'Point', coordinates: progressCoordinates.at(-1) || [cursor.longitude, cursor.latitude] }, properties: {} }]
      : []
  ))
}

const clearPendingIdle = () => {
  if (bufferTimer) window.clearTimeout(bufferTimer)
  bufferTimer = null
  if (map.value && pendingIdleHandler) map.value.off?.('idle', pendingIdleHandler)
  pendingIdleHandler = null
}

// Runs `done` on the map's next idle, or after `maxMs` so a slow tile server never stalls the film for good.
const waitForIdle = (maxMs, done) => {
  clearPendingIdle()
  const finish = () => {
    clearPendingIdle()
    done()
  }
  pendingIdleHandler = finish
  map.value.once?.('idle', finish)
  bufferTimer = window.setTimeout(finish, maxMs)
}

const holdForMissingTiles = () => {
  if (!isPlaying.value || buffering.value || !initialMapIdle.value) return
  if (map.value.areTilesLoaded?.() !== false) {
    tilesMissingSince = null
    return
  }
  const now = Date.now()
  tilesMissingSince ??= now
  if (now - tilesMissingSince < TILE_STALL_MS) return
  setBuffering(true)
  waitForIdle(TILE_WAIT_MAX_MS, () => {
    tilesMissingSince = null
    setBuffering(false)
  })
}

// One frame of the film: the camera and route layers are both derived from the film clock.
const renderFrame = () => {
  if (!map.value || !mapReady.value || unsupported.value) return
  const scene = currentScene.value
  const cursor = activeCursor.value
  updateRouteLayers(scene, cursor)
  const camera = resolveCinematicCamera(cameraPlan.value[currentSceneIndex.value], sceneProgress.value, {
    cursor,
    reducedMotion: reducedMotion.value
  })
  if (camera) map.value.jumpTo(camera)
  holdForMissingTiles()
}

// A style reload resets the projection, so this runs again on every style.load.
const applyGlobeProjection = () => {
  if (!map.value) return
  try {
    map.value.setProjection({ type: 'globe' })
    projectionFallback.value = map.value.getProjection?.()?.type !== 'globe'
  } catch {
    projectionFallback.value = true
  }
}

const handleMapReady = (mapInstance) => {
  map.value = mapInstance
  unsupported.value = resolveMapEngineModeFromInstance(mapInstance) !== MAP_RENDER_MODES.VECTOR
  if (unsupported.value) return
  applyGlobeProjection()
  applySky()
  setupLayers()
  styleLoadHandler = () => {
    applyGlobeProjection()
    applySky()
    setupLayers()
    renderFrame()
  }
  resizeHandler = () => {
    buildCameraPlan()
    renderFrame()
  }
  mapInstance.on?.('style.load', styleLoadHandler)
  mapInstance.on?.('resize', resizeHandler)
  mapReady.value = true
  buildCameraPlan()
  renderFrame()
  waitForIdle(INITIAL_IDLE_MAX_MS, () => { initialMapIdle.value = true })
}

const handleMapWarning = (warning) => {
  if (warning?.code === 'engine_fatal' || warning?.code === 'vector_engine_init_failed') unsupported.value = true
}

const cleanupMap = () => {
  clearPendingIdle()
  if (map.value) {
    if (styleLoadHandler) map.value.off?.('style.load', styleLoadHandler)
    if (resizeHandler) map.value.off?.('resize', resizeHandler)
    removeLayers(map.value, layerIds)
    removeSources(map.value, sourceIds)
  }
  styleLoadHandler = null
  resizeHandler = null
  tilesMissingSince = null
  cameraPlan.value = []
  map.value = null
  mapReady.value = false
  initialMapIdle.value = false
}

const resetPlayer = () => {
  pause()
  setBuffering(false)
  seekToMs(0)
  loadToken += 1
  photoLoadToken += 1
  cleanupMap()
  revokePhotoUrls()
  scenePhotoStatus.value = new Map()
  viewerVisible.value = false
  replayData.value = null
  sessionPhotos.value = null
  unsupported.value = false
  projectionFallback.value = false
  loadError.value = ''
  autoStarted = false
}

const close = () => {
  resetPlayer()
  emit('update:visible', false)
}
const handleDialogVisibility = (value) => { if (!value) close() }
const togglePlayback = () => { isPlaying.value ? pause() : play() }
const seekFromInput = (event) => seekToRatio(Number(event.target.value) / 1000)
const handleKeydown = (event) => {
  // A focused button keeps its native Space activation and the scrubber keeps its native arrow keys.
  const target = event.target instanceof Element ? event.target : null
  const onControl = Boolean(target?.closest('button, input'))
  const onScrubber = Boolean(target?.closest('input'))
  if (event.key === 'Escape') close()
  else if (event.key === ' ' && !onControl) { event.preventDefault(); togglePlayback() }
  else if (event.key === 'ArrowRight' && !onScrubber) nextScene()
  else if (event.key === 'ArrowLeft' && !onScrubber) previousScene()
}
const handleVisibilityChange = () => { if (document.hidden) pause() }

const captureSessionPhotos = () => {
  if (props.visible && sessionPhotos.value === null && props.photosPayload?.settled) {
    sessionPhotos.value = Array.isArray(props.photosPayload.photos) ? props.photosPayload.photos : []
  }
}

watch(() => props.visible, async (visible) => {
  if (!visible) {
    // Hidden by the page (e.g. a period change) rather than by close(): stop the clock and free the map too.
    resetPlayer()
    return
  }
  captureSessionPhotos()
  mapStyle.value = resolveProfileMapLibreStyle()
  reducedMotion.value = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches === true
  await loadReplay()
  await nextTick()
  playerRef.value?.focus()
}, { immediate: true })
watch(() => props.photosPayload, captureSessionPhotos)
watch(currentSceneIndex, loadPhotosFrom)
watch(story, () => {
  restartPhotoLoading()
  buildCameraPlan()
})
watch([mapReady, replayData, topPlaces], setupLayers, { deep: true })
watch([elapsedMs, cameraPlan], renderFrame)
watch(controllerReady, (ready) => {
  if (ready && !reducedMotion.value && !autoStarted) {
    autoStarted = true
    play()
  }
})

if (typeof document !== 'undefined') document.addEventListener('visibilitychange', handleVisibilityChange)
onBeforeUnmount(() => {
  document.removeEventListener('visibilitychange', handleVisibilityChange)
  resetPlayer()
})
</script>

<style scoped>
:global(.cinematic-rewind-dialog) { width:100vw!important; max-width:100vw!important; height:100dvh!important; max-height:100dvh!important; margin:0!important; border:0!important; border-radius:0!important }
:global(.cinematic-rewind-dialog .p-dialog-content) { height:100%; padding:0!important; overflow:hidden!important; background:#020617 }
.cinematic-player { position:relative; width:100%; height:100%; overflow:hidden; background:#020617; color:#fff; outline:none }
.cinematic-player :deep(.base-map) { width:100%!important; height:100%!important }
.cinematic-player :deep(.maplibregl-ctrl-top-left) { display:none }
.cinematic-vignette { position:absolute; inset:0; pointer-events:none; background:linear-gradient(180deg,rgba(2,6,23,.42),transparent 35%,rgba(2,6,23,.62)); box-shadow:inset 0 0 10rem rgba(2,6,23,.35) }
.cinematic-close { position:absolute; z-index:20; top:max(1rem,env(safe-area-inset-top)); right:max(1rem,env(safe-area-inset-right)); width:2.75rem; height:2.75rem; border:1px solid rgba(255,255,255,.3); border-radius:50%; background:rgba(2,6,23,.72); color:#fff; cursor:pointer }
.cinematic-close:focus-visible,.cinematic-controls button:focus-visible,.cinematic-controls input:focus-visible { outline:3px solid #93c5fd; outline-offset:2px }
.cinematic-state { position:absolute; inset:0; z-index:10; display:grid; place-content:center; justify-items:center; gap:1rem; padding:2rem; text-align:center; background:#020617 }
.cinematic-state--overlay { background:rgba(2,6,23,.92) }.cinematic-state--error>i,.cinematic-state>i { font-size:3rem }.cinematic-state--error>i { color:#f87171 }
.cinematic-card { position:absolute; z-index:5; left:clamp(1rem,5vw,5rem); max-width:min(42rem,calc(100% - 2rem)); text-shadow:0 3px 20px rgba(0,0,0,.85); pointer-events:none }
.cinematic-card p { margin:0 0 .45rem; color:#bfdbfe; font-size:.78rem; font-weight:800; letter-spacing:.16em; text-transform:uppercase }
.cinematic-card p small { margin-left:.6rem; padding:.1rem .45rem; border:1px solid rgba(191,219,254,.45); border-radius:999px; font-size:.68rem; letter-spacing:.08em; color:#e0f2fe }
.cinematic-card h1,.cinematic-card h2 { margin:0; line-height:.95 }.cinematic-card h1 { font-size:clamp(4rem,14vw,10rem) }.cinematic-card h2 { font-size:clamp(2rem,6vw,5rem) }
.cinematic-card span { display:block; margin-top:.8rem; font-size:clamp(1rem,2vw,1.35rem); color:rgba(255,255,255,.9) }
.cinematic-card--hero { top:50%; transform:translateY(-50%) }.cinematic-card--caption { bottom:10rem }
.cinematic-card--chapter { top:42%; left:50%; transform:translate(-50%,-50%); text-align:center }
.cinematic-card--chapter h1 { font-size:clamp(3.5rem,12vw,8.5rem); text-transform:capitalize }
.cinematic-card--caption h2 { text-transform:capitalize }
.cinematic-card--chapter p { margin:1rem 0 0; color:#e0f2fe; font-size:clamp(.9rem,2vw,1.2rem); letter-spacing:.1em }
.cinematic-card p.cinematic-route { margin:.65rem 0 0; color:#fff; font-size:clamp(1.05rem,2.4vw,1.6rem); font-weight:700; letter-spacing:0; text-transform:none }
.cinematic-reel { position:absolute; z-index:6; right:clamp(1rem,6vw,6rem); top:50%; width:min(32vw,28rem); margin:0; transform:translateY(-50%) }
.cinematic-reel-current { padding:.55rem; border-radius:1rem; background:#fff; box-shadow:0 1.5rem 4rem rgba(0,0,0,.48); transform:rotate(1.5deg) }
.cinematic-photo-caption { margin:0; padding:.55rem .3rem .15rem; color:#334155; font-weight:700 }
.cinematic-reel-strip { display:flex; justify-content:flex-end; gap:.45rem; margin:.9rem 0 0; padding:0; list-style:none }
.cinematic-reel-strip li { width:3.6rem; aspect-ratio:1; padding:.18rem; border-radius:.5rem; background:rgba(255,255,255,.75); box-shadow:0 .5rem 1.2rem rgba(0,0,0,.4); opacity:.75; animation:cinematic-strip-in .4s cubic-bezier(.34,1.56,.64,1) both; transition:opacity .2s ease, background .2s ease }
.cinematic-reel-strip li.current { opacity:1; background:#fff; outline:2px solid var(--cinematic-accent,#38bdf8); outline-offset:1px }
.cinematic-reel-strip button { display:block; width:100%; height:100%; padding:0; border:0; background:none; cursor:zoom-in }
.cinematic-reel-strip button:focus-visible,.cinematic-photo-frame:focus-visible { outline:3px solid #93c5fd; outline-offset:2px }
.cinematic-reel-strip img { display:block; width:100%; height:100%; object-fit:cover; border-radius:.35rem }
.cinematic-photo-frame { display:block; width:100%; padding:0; border:0; overflow:hidden; border-radius:.65rem; background:none; cursor:zoom-in }
.cinematic-photo-frame img { display:block; width:100%; max-height:54vh; object-fit:cover; animation:cinematic-ken-burns 2.4s ease-out both }
.cinematic-controls { position:absolute; z-index:15; left:50%; bottom:max(1rem,env(safe-area-inset-bottom)); transform:translateX(-50%); display:flex; align-items:center; gap:.55rem; width:min(760px,calc(100% - 2rem)); padding:.65rem .8rem; border:1px solid rgba(255,255,255,.2); border-radius:999px; background:rgba(2,6,23,.8); backdrop-filter:blur(14px) }
.cinematic-controls button { flex:0 0 auto; width:2.2rem; height:2.2rem; border:0; border-radius:50%; background:transparent; color:#fff; cursor:pointer }.cinematic-controls button:hover { background:rgba(255,255,255,.14) }
.cinematic-controls input { flex:1; min-width:4rem }.cinematic-controls span { min-width:2.7rem; font-variant-numeric:tabular-nums; font-size:.8rem; text-align:center }
.cinematic-months { position:absolute; z-index:15; left:50%; bottom:calc(max(1rem,env(safe-area-inset-bottom)) + 4.1rem); transform:translateX(-50%); display:grid; grid-template-columns:repeat(var(--chapter-count,12),minmax(0,1fr)); gap:.25rem; width:min(760px,calc(100% - 2rem)) }
.cinematic-months button { position:relative; padding:.35rem 0 .55rem; border:0; border-radius:.5rem; background:transparent; color:rgba(255,255,255,.38); font-size:.72rem; font-weight:700; letter-spacing:.04em; text-transform:uppercase; text-shadow:0 1px 6px rgba(0,0,0,.8); cursor:pointer; transition:color .2s ease, background .2s ease }
.cinematic-months button:disabled { cursor:default }
.cinematic-months button.has-trip { color:rgba(255,255,255,.78) }
.cinematic-months button.has-trip::after { content:''; position:absolute; left:50%; bottom:.15rem; width:.3rem; height:.3rem; border-radius:50%; background:var(--cinematic-accent,#38bdf8); transform:translateX(-50%) }
.cinematic-months button.has-trip:hover { background:rgba(255,255,255,.12) }
.cinematic-months button.active { color:#0b1d4d; background:#e0f2fe; text-shadow:none }
.cinematic-months button.active::after { background:#0b1d4d }
.cinematic-months button:focus-visible { outline:3px solid #93c5fd; outline-offset:2px }
.cinematic-buffering,.cinematic-notice { position:absolute; z-index:12; left:50%; transform:translateX(-50%); padding:.45rem .8rem; border-radius:999px; background:rgba(2,6,23,.78); font-size:.8rem }
.cinematic-buffering { top:1rem }.cinematic-notice { top:3.9rem; color:#fde68a }
@media (max-width:720px) { .cinematic-reel { right:1rem; top:26%; width:min(46vw,15rem) }.cinematic-reel-strip li { width:2.6rem }.cinematic-card--caption { bottom:8.5rem }.cinematic-months { gap:.1rem; bottom:calc(max(1rem,env(safe-area-inset-bottom)) + 3.6rem) }.cinematic-months button { font-size:.6rem; letter-spacing:0 }.cinematic-controls { gap:.2rem; padding:.5rem }.cinematic-controls button { width:2rem;height:2rem } }
.cinematic-card-enter-active { transition:opacity .55s ease, transform .7s cubic-bezier(.16,1,.3,1) }
.cinematic-card-leave-active { transition:opacity .25s ease }
.cinematic-card-enter-from { opacity:0; transform:translateY(1.2rem) }
.cinematic-card--hero.cinematic-card-enter-from { transform:translateY(-50%) scale(.94) }
.cinematic-card--chapter.cinematic-card-enter-from { transform:translate(-50%,-50%) scale(.9) }
.cinematic-card-leave-to { opacity:0 }
.cinematic-reel-photo-enter-active { transition:opacity .35s ease, transform .5s cubic-bezier(.16,1,.3,1) }
.cinematic-reel-photo-leave-active { transition:opacity .2s ease, transform .2s ease }
.cinematic-reel-photo-enter-from { opacity:0; transform:translateX(1.5rem) rotate(4deg) }
.cinematic-reel-photo-leave-to { opacity:0; transform:translateX(-1rem) rotate(-2deg) }
@keyframes cinematic-ken-burns { from { transform:scale(1.08) } to { transform:scale(1) } }
@keyframes cinematic-strip-in { from { opacity:0; scale:.4 } to { scale:1 } }
@media (prefers-reduced-motion:reduce) {
  .cinematic-photo-frame img,.cinematic-reel-strip li { animation:none }
  .cinematic-card-enter-active,.cinematic-card-leave-active,.cinematic-reel-photo-enter-active,.cinematic-reel-photo-leave-active { transition:none }
}
</style>
