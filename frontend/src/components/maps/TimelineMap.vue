<template>
  <div class="map-view-container">
    <!-- Confirmation Dialog -->
    <ConfirmDialog />
    <MapContainer
      ref="mapContainerRef"
      map-id="map-dashboard"
      :center="mapCenter"
      :zoom="mapZoom"
      :show-controls="true"
      :controls-props="controlsProps"
      :custom-tile-url="customTileUrl"
      :custom-style-url="customStyleUrl"
      :map-render-mode="mapRenderMode"
      :is-shared-view="isSharedView"
      @map-ready="handleMapReady"
      @map-click="handleMapClick"
      @map-contextmenu="handleMapContextMenu"
    >
      <!-- Map Controls -->
      <template #controls="{ map, isReady }">
        <MapControls
          v-if="map && isReady"
          :map="map"
          :show-favorites="showFavorites"
          :show-timeline="showTimeline"
          :show-path="showPath"
          :show-immich="showImmich"
          :show-heatmap="props.showHeatmapControl && !props.isPublicView"
          :heatmap-enabled="heatmapEnabled"
          :heatmap-layer="heatmapLayer"
          :heatmap-available="heatmapAvailable"
          :immich-configured="immichConfigured"
          :immich-loading="immichLoading"
          @toggle-favorites="toggleFavorites"
          @toggle-timeline="toggleTimeline"
          @toggle-path="togglePath"
          @toggle-immich="toggleImmich"
          @toggle-heatmap="handleToggleHeatmap"
          @heatmap-layer-change="handleHeatmapLayerChange"
          @zoom-to-data="handleZoomToData"
          class="map-controls"
        />
      </template>

      <!-- Map Layers -->
      <template #overlays="{ map, isReady }">
        <!-- Heatmap Layer -->
        <HeatmapLayer
          v-if="map && isReady"
          :map="map"
          :points="heatmapPoints"
          :profile="heatmapLayer"
          :value-key="'durationSeconds'"
          :min-weight="heatmapScale.minWeight"
          :gamma="heatmapScale.gamma"
          :radius="heatmapStyle.radius"
          :blur="heatmapStyle.blur"
          :min-opacity="heatmapStyle.minOpacity"
          :max="heatmapStyle.max"
          :gradient="heatmapGradient"
          :enabled="heatmapEnabled"
        />

        <!-- Path Layer -->
        <PathLayer
          v-if="map && isReady"
          ref="pathLayerRef"
          :map="map"
          :path-data="processedPathData"
          :highlighted-trip="activeTimelineHighlight"
          :visible="showPath"
          :replay-state="pathReplayState"
          @path-click="handlePathClick"
          @trip-marker-click="handleTripMarkerClick"
          @highlighted-trip-click="handleHighlightedTripClick"
          @highlighted-trip-replay-data="handleHighlightedTripReplayData"
        />
        

        <!-- Timeline Layer -->
        <TimelineLayer
          v-if="map && isReady"
          ref="timelineLayerRef"
          :map="map"
          :timeline-data="processedTimelineData"
          :highlighted-item="activeTimelineHighlight"
          :visible="timelineLayerVisible"
          @marker-click="handleTimelineMarkerClick"
        />

        <!-- Favorites Layer -->
        <FavoritesLayer
          v-if="map && isReady"
          ref="favoritesLayerRef"
          :map="map"
          :favorites-data="processedFavoritesData"
          :visible="showFavorites"
          @favorite-click="handleFavoriteClick"
          @favorite-edit="handleFavoriteEdit"
          @favorite-delete="handleFavoriteDelete"
          @favorite-contextmenu="handleFavoriteContextMenu"
        />

        <TripPlanLayer
          v-if="map && isReady"
          ref="tripPlanLayerRef"
          :map="map"
          :planned-items-data="processedPlannedItemsData"
          :visible="true"
          @plan-item-contextmenu="handlePlannedItemContextMenu"
        />

        <!-- Immich Photos Layer -->
        <ImmichLayer
          v-if="map && isReady && shouldShowImmich"
          ref="immichLayerRef"
          :map="map"
          :visible="showImmich"
          @photo-click="handlePhotoClick"
          @photo-hover="handlePhotoHover"
          @error="handleImmichError"
        />

        <!-- Current Location Layer -->
        <CurrentLocationLayer
          v-if="map && isReady && showCurrentLocation && currentLocation"
          :map="map"
          :location="currentLocation"
        />
      </template>

      <!-- Dialogs -->
      <template #dialogs>
        <!-- Context Menus -->
        <ContextMenu
          ref="mapContextMenuRef"
          :model="mapMenuItems"
          :popup="true"
        />
        
        <ContextMenu
          ref="favoriteContextMenuRef"
          :model="favoriteMenuItems"
          :popup="true"
        />

        <ContextMenu
          ref="plannedItemContextMenuRef"
          :model="plannedItemMenuItems"
          :popup="true"
        />

        <!-- Add Favorite Dialogs -->
        <AddFavoriteDialog
          v-if="addToFavoritesDialogVisible"
          :visible="addToFavoritesDialogVisible"
          :header="'Add To Favorites'"
          @add-to-favorites="onFavoritePointSubmit"
          @close="closeAddFavoritePoint"
        />

        <AddFavoriteDialog
          v-if="addAreaShowDialog"
          :visible="addAreaShowDialog"
          :header="'Add Area To Favorites'"
          @add-to-favorites="onFavoriteAreaSubmit"
          @close="closeAddFavoriteArea"
        />

        <!-- Photo Viewer Dialog -->
        <PhotoViewerDialog
          v-model:visible="photoViewerVisible"
          :photos="photoViewerPhotos"
          :initial-photo-index="photoViewerIndex"
          @show-on-map="handlePhotoShowOnMap"
          @close="closePhotoViewer"
        />

        <!-- Timeline Regeneration Modal -->
        <TimelineRegenerationModal
          v-model:visible="timelineRegenerationVisible"
          :type="timelineRegenerationType"
          :job-id="currentJobId"
          :job-progress="jobProgress"
        />

      </template>
    </MapContainer>

    <div
      v-if="showTripReplayBar"
      class="trip-replay-bar"
      @mousedown.stop
      @touchstart.stop
      @click.stop
    >
      <div class="trip-replay-bar-main">
        <button
          type="button"
          class="trip-replay-btn"
          :title="isReplayPlaying ? 'Pause replay' : 'Play replay'"
          @click="toggleReplayPlayback"
        >
          <i :class="isReplayPlaying ? 'pi pi-pause' : 'pi pi-play'"></i>
        </button>

        <button
          type="button"
          class="trip-replay-btn"
          title="Stop replay"
          @click="stopTripReplay"
        >
          <i class="pi pi-stop"></i>
        </button>

        <span class="trip-replay-time">{{ replayElapsedLabel }}</span>

        <input
          class="trip-replay-slider"
          type="range"
          min="0"
          max="1000"
          step="1"
          :value="replaySliderValue"
          @input="handleReplaySliderInput"
        />

        <span class="trip-replay-time">{{ replayDurationLabel }}</span>
      </div>

      <div class="trip-replay-bar-options">
        <div class="trip-replay-speeds">
          <button
            v-for="speed in replaySpeedPresets"
            :key="speed"
            type="button"
            class="trip-replay-speed-btn"
            :class="{ active: replaySpeedMultiplier === speed }"
            :title="`Set speed ${speed}x`"
            @click="setReplaySpeed(speed)"
          >
            {{ speed }}x
          </button>
        </div>

        <button
          type="button"
          class="trip-replay-toggle-btn"
          :class="{ active: replayFollowCamera }"
          title="Follow camera"
          @click="toggleReplayFollowCamera"
        >
          <i class="pi pi-compass"></i>
          Follow
        </button>

        <button
          type="button"
          class="trip-replay-toggle-btn"
          :class="{ active: replayEnable3d }"
          title="Enable 3D camera"
          @click="toggleReplay3d"
        >
          <i class="pi pi-box"></i>
          3D
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
import {computed, markRaw, nextTick, onMounted, onUnmounted, readonly, ref, shallowRef, watch} from 'vue'
import {useRouter} from 'vue-router'
import {useConfirm} from "primevue/useconfirm"
import {useToast} from "primevue/usetoast"
import ContextMenu from 'primevue/contextmenu'
import ConfirmDialog from 'primevue/confirmdialog'
import {useTimelineRegeneration} from '@/composables/useTimelineRegeneration'
import { usePhotoMapMarkersRuntime } from '@/maps/runtime/usePhotoMapMarkersRuntime'
import '@/styles/photo-map-markers.css'
import { MAP_RENDER_MODES, resolveMapEngineModeFromInstance } from '@/maps/contracts/mapContracts'
import {
  DEFAULT_TRIP_REPLAY_SPEED,
  TRIP_REPLAY_SPEED_PRESETS,
  advanceTripReplayElapsed,
  buildTripReplayTimeline,
  clampTripReplayElapsed,
  resolveTripReplayCursor
} from '@/maps/shared/tripReplayMath'

// Map components
import {FavoritesLayer, HeatmapLayer, MapContainer, MapControls, PathLayer, TimelineLayer, CurrentLocationLayer, ImmichLayer, TripPlanLayer} from '@/components/maps'

import PhotoViewerDialog from '@/components/dialogs/PhotoViewerDialog.vue'
import TimelineRegenerationModal from '@/components/dialogs/TimelineRegenerationModal.vue'
import {useMapHighlights, useMapInteractions, useMapLayers} from '@/composables'
import { useRectangleDrawingRuntime } from '@/composables/useRectangleDrawingRuntime'

// Store imports
import {useHighlightStore} from '@/stores/highlight'
import {useFavoritesStore} from '@/stores/favorites'
import {useLocationStore} from '@/stores/location'
import {useTimelineStore} from '@/stores/timeline'
import {useImmichStore} from '@/stores/immich'
import {useDigestStore} from '@/stores/digest'
import {useDateRangeStore} from '@/stores/dateRange'

// Props
const props = defineProps({
  pathData: {
    type: Object,
    default: () => null
  },
  timelineData: {
    type: Array,
    default: () => []
  },
  favoritePlaces: {
    type: Object,
    default: () => null
  },
  plannedItemsData: {
    type: Array,
    default: () => []
  },
  currentLocation: {
    type: Object,
    default: () => null
  },
  showCurrentLocation: {
    type: Boolean,
    default: false
  },
  isPublicView: {
    type: Boolean,
    default: false
  },
  showPhotos: {
    type: Boolean,
    default: true
  },
  customTileUrl: {
    type: String,
    default: null
  },
  customStyleUrl: {
    type: String,
    default: null
  },
  mapRenderMode: {
    type: String,
    default: null
  },
  isSharedView: {
    type: Boolean,
    default: false
  },
  showPlanToVisitAction: {
    type: Boolean,
    default: false
  },
  showFavoritesContextActions: {
    type: Boolean,
    default: true
  },
  showHeatmapControl: {
    type: Boolean,
    default: true
  },
  showFavoritesByDefault: {
    type: Boolean,
    default: false
  },
  showImmichByDefault: {
    type: Boolean,
    default: false
  },
  defaultCenterWhenEmpty: {
    type: Array,
    default: () => [51.505, -0.09]
  },
  enableFavoriteContextMenu: {
    type: Boolean,
    default: true
  },
  enableTripReplay: {
    type: Boolean,
    default: false
  }
})

// Emits
const emit = defineEmits([
  'add-point-with-regeneration',
  'add-area-with-regeneration',
  'edit-favorite',
  'delete-favorite',
  'highlighted-path-click',
  'timeline-marker-click',
  'map-click',
  'plan-to-visit',
  'plan-item-edit',
  'plan-item-delete'
])

// Router
const router = useRouter()

// Composables
const {
  showFavorites,
  showTimeline,
  showPath,
  showImmich,
  toggleFavorites,
  toggleTimeline,
  togglePath,
  toggleImmich
} = useMapLayers()

const {
  timelineRegenerationVisible,
  timelineRegenerationType,
  currentJobId,
  jobProgress,
  withTimelineRegeneration
} = useTimelineRegeneration()


// Store instances
const favoritesStore = useFavoritesStore()
const locationStore = useLocationStore()
const timelineStore = useTimelineStore()
const immichStore = useImmichStore()
const digestStore = useDigestStore()
const dateRangeStore = useDateRangeStore()

const {
  handleTimelineMarkerClick: baseHandleTimelineMarkerClick,
  handlePathClick: baseHandlePathClick,
  handleFavoriteClick: baseHandleFavoriteClick,
  handleMapClick: baseHandleMapClick,
  handleMapContextMenu: baseHandleMapContextMenu
} = useMapInteractions({
  onTimelineMarkerClick: (event) => emit('timeline-marker-click', event?.timelineItem || event),
  onPathClick: (event) => emit('highlighted-path-click', event),
  onFriendClick: (event) => {},
  onFavoriteClick: (event) => {},
  onMapClick: (event) => {},
  onMapContextMenu: (event) => {}
})

const {
  activeTimelineHighlight,
  highlightTimelineItem,
  clearAllMapHighlights
} = useMapHighlights()

const highlightStore = useHighlightStore()

// Template refs
const mapContainerRef = ref(null)
const pathLayerRef = ref(null)
const timelineLayerRef = ref(null)
const favoritesLayerRef = ref(null)
const tripPlanLayerRef = ref(null)
const immichLayerRef = ref(null)
const mapContextMenuRef = ref(null)
const favoriteContextMenuRef = ref(null)
const plannedItemContextMenuRef = ref(null)

const confirm = useConfirm()
const toast = useToast()

// Local state
const map = shallowRef(null)
const favoriteContextMenuActive = ref(false)
const heatmapEnabled = ref(false)
const heatmapLayer = ref('stays')
const heatmapPoints = ref([])
let heatmapRequestId = 0
const heatmapPrefetches = new Map()
let mapContextMenuShowTimeoutId = null
const replayPathPayload = ref({ tripKey: '', points: [] })
const replayElapsedMs = ref(0)
const replaySpeedMultiplier = ref(DEFAULT_TRIP_REPLAY_SPEED)
const replayFollowCamera = ref(true)
const replayEnable3d = ref(false)
const replayIsPlaying = ref(false)
let replayAnimationFrameId = null
let replayLastFrameTimestamp = null

// Dialog state
const dialogState = ref({
  addToFavoritesVisible: false,
  addAreaVisible: false,
  selectedFavorite: null,
  selectedPlannedItem: null,
  addToFavoritesLatLng: null
})

// Rectangle drawing composable
const {
  drawingState,
  initialize: initializeDrawing,
  startDrawing,
  stopDrawing,
  cleanupTempLayer,
  isDrawing
} = useRectangleDrawingRuntime({
  onRectangleCreated: (rectangle) => {
    drawingState.value.tempAreaLayer = rectangle.layer
    dialogState.value.addAreaVisible = true
  }
})

const openPhotoViewerFromPayload = (payload) => {
  const photos = Array.isArray(payload?.photos) ? payload.photos : []
  if (photos.length === 0) {
    return
  }

  const safeIndex = Math.min(Math.max(0, payload?.initialIndex || 0), photos.length - 1)
  photoViewerPhotos.value = photos
  photoViewerIndex.value = safeIndex
  photoViewerVisible.value = true
}

const {
  clearFocusMarker: clearFocusedPhotoMarker,
  focusOnPhoto: focusOnMapPhoto
} = usePhotoMapMarkersRuntime({
  emit: (eventName, payload) => {
    if (eventName === 'photo-click') {
      openPhotoViewerFromPayload(payload)
    }
  }
})

// Photo viewer state
const photoViewerVisible = ref(false)
const photoViewerPhotos = ref([])
const photoViewerIndex = ref(0)

// Computed getters for dialog state (for template compatibility)
const addToFavoritesDialogVisible = computed(() => dialogState.value.addToFavoritesVisible)
const addAreaShowDialog = computed(() => dialogState.value.addAreaVisible)

// Map configuration - start with null to avoid showing default location before data loads
const mapCenter = computed(() => {
  // Return first available data point, or null if no data yet
  if (dataBounds.value && dataBounds.value.length > 0) {
    return dataBounds.value[0]
  }
  return props.defaultCenterWhenEmpty
})
const mapZoom = ref(13)

// Controls configuration
const controlsProps = computed(() => ({
  showZoomControls: true
}))

// Immich computed properties
const immichConfigured = computed(() => immichStore.isConfigured)
const immichLoading = computed(() => immichStore.photosLoading || immichStore.configLoading)

// For public views, respect the showPhotos prop; for private views, always allow
const shouldShowImmich = computed(() => {
  if (props.isPublicView) {
    return props.showPhotos
  }
  return true // For non-public views, always allow (controlled by toggle)
})

const heatmapAvailable = computed(() => {
  return !props.isPublicView && dateRangeStore.hasDateRange && dateRangeStore.isValidRange
})

const heatmapScale = computed(() => {
  return heatmapLayer.value === 'trips'
    ? { minWeight: 0.02, gamma: 1.0 }
    : { minWeight: 0.05, gamma: 0.6 }
})

const heatmapStyle = computed(() => {
  return heatmapLayer.value === 'trips'
    ? { radius: 14, blur: 10, minOpacity: 0.2, max: 1.0 }
    : { radius: 32, blur: 24, minOpacity: 0.3, max: 1.0 }
})

const heatmapGradient = {
  0.0: '#2563eb',
  0.35: '#22c55e',
  0.6: '#eab308',
  0.8: '#f97316',
  1.0: '#dc2626',
}

const getHighlightedTripKey = (trip) => {
  if (!trip) {
    return ''
  }

  if (trip.id) {
    return String(trip.id)
  }

  return [
    trip.timestamp,
    trip.latitude,
    trip.longitude,
    trip.endLatitude,
    trip.endLongitude,
    trip.tripDuration,
    trip.distanceMeters
  ].join('|')
}

const mapEngineMode = computed(() => resolveMapEngineModeFromInstance(map.value, MAP_RENDER_MODES.RASTER))
const isVectorMapMode = computed(() => mapEngineMode.value === MAP_RENDER_MODES.VECTOR)
const replaySpeedPresets = TRIP_REPLAY_SPEED_PRESETS
const activeHighlightedTrip = computed(() => {
  if (!activeTimelineHighlight.value || activeTimelineHighlight.value.type !== 'trip') {
    return null
  }

  return activeTimelineHighlight.value
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

const showTripReplayBar = computed(() => (
  Boolean(props.enableTripReplay)
  && isVectorMapMode.value
  && showPath.value
  && Boolean(activeHighlightedTrip.value)
  && Boolean(replayTimeline.value)
))
const hideTimelineMarkersForReplay = computed(() => showTripReplayBar.value && replayIsPlaying.value)
const timelineLayerVisible = computed(() => showTimeline.value && !hideTimelineMarkersForReplay.value)

const pathReplayState = computed(() => ({
  enabled: showTripReplayBar.value,
  playing: replayIsPlaying.value,
  elapsedMs: replayElapsedMs.value,
  movementType: activeHighlightedTrip.value?.movementType || 'UNKNOWN',
  followCamera: replayFollowCamera.value,
  enable3d: replayEnable3d.value,
  suppressTripPopup: replayIsPlaying.value,
  cursor: replayCursor.value
}))

const replaySliderValue = computed(() => {
  if (!replayDurationMs.value) {
    return 0
  }

  return Math.round((replayElapsedMs.value / replayDurationMs.value) * 1000)
})

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

// Context menu items
const mapMenuItems = computed(() => {
  const items = []

  if (props.showPlanToVisitAction) {
    items.push({
      label: 'Plan to visit here',
      icon: 'pi pi-map-marker',
      command: () => {
        if (!dialogState.value.addToFavoritesLatLng) {
          return
        }
        emit('plan-to-visit', { latlng: dialogState.value.addToFavoritesLatLng })
      }
    })
  }

  if (props.showFavoritesContextActions) {
    items.push(
      {
        label: 'Add to Favorites',
        icon: 'pi pi-star',
        command: () => {
          dialogState.value.addToFavoritesVisible = true
        }
      },
      {
        label: 'Add an area to Favorites',
        icon: 'pi pi-star',
        command: () => {
          startDrawing()
        }
      }
    )
  }

  return items
})

// Favorite context menu items
const favoriteMenuItems = ref([
  {
    label: 'View all visits',
    icon: 'pi pi-chart-line',
    command: () => {
      if (dialogState.value.selectedFavorite) {
        navigateToFavoriteDetails(dialogState.value.selectedFavorite)
      }
    }
  },
  {
    separator: true
  },
  {
    label: 'Edit',
    icon: 'pi pi-pencil',
    command: () => {
      if (dialogState.value.selectedFavorite) {
        handleFavoriteEdit(dialogState.value.selectedFavorite)
      }
    }
  },
  {
    label: 'Delete',
    icon: 'pi pi-trash',
    command: () => {
      if (dialogState.value.selectedFavorite) {
        handleFavoriteDelete(dialogState.value.selectedFavorite)
      }
    }
  }
])

const plannedItemMenuItems = ref([
  {
    label: 'Edit planned item',
    icon: 'pi pi-pencil',
    command: () => {
      if (dialogState.value.selectedPlannedItem) {
        emit('plan-item-edit', dialogState.value.selectedPlannedItem)
      }
    }
  },
  {
    label: 'Delete planned item',
    icon: 'pi pi-trash',
    command: () => {
      if (dialogState.value.selectedPlannedItem) {
        emit('plan-item-delete', dialogState.value.selectedPlannedItem)
      }
    }
  }
])

// Map event handlers
const handleMapReady = (mapInstance) => {
  map.value = mapInstance ? markRaw(mapInstance) : null
  
  // Initialize rectangle drawing
  initializeDrawing(mapInstance)
  initAreaDrawControl()
  
  // Fit map to data if available
  if (hasAnyData.value && dataBounds.value) {
    nextTick(() => {
      if (dataBounds.value.length === 1) {
        // Single point - center on it with a reasonable zoom
        mapContainerRef.value?.setView?.(dataBounds.value[0], 14)
      } else if (dataBounds.value.length > 1) {
        // Multiple points - fit bounds
        mapContainerRef.value?.fitBounds?.(dataBounds.value, { padding: [20, 20] })
      }
    })
  }
}

const handleMapClick = (event) => {
  dialogState.value.addToFavoritesLatLng = event.latlng
  baseHandleMapClick(event)
  emit('map-click', event)
  
  // Clear all highlights when clicking on empty map
  clearAllMapHighlights()
  clearFocusedPhotoMarker()
}

const handleMapContextMenu = (event) => {
  // Don't show context menu in public view
  if (props.isPublicView) {
    return
  }

  if (!mapMenuItems.value || mapMenuItems.value.length === 0) {
    return
  }

  // If drawing is in progress, do nothing to avoid conflicts with touch events.
  if (isDrawing()) {
    return
  }

  // Prevent default browser context menu
  if (event.originalEvent) {
    event.originalEvent.preventDefault()
    event.originalEvent.stopPropagation()
  }

  if (mapContextMenuShowTimeoutId !== null) {
    clearTimeout(mapContextMenuShowTimeoutId)
    mapContextMenuShowTimeoutId = null
  }

  // Defer map-context menu opening by one tick to allow feature-level
  // contextmenu handlers (favorites/planned items) to set suppression flags.
  mapContextMenuShowTimeoutId = setTimeout(() => {
    mapContextMenuShowTimeoutId = null

    if (favoriteContextMenuActive.value) {
      favoriteContextMenuActive.value = false
      return
    }

    dialogState.value.addToFavoritesLatLng = event.latlng
    baseHandleMapContextMenu(event)

    if (mapContextMenuRef.value && event.originalEvent) {
      mapContextMenuRef.value.show(event.originalEvent)
    }
  }, 0)
}

// Layer event handlers
const handleTimelineMarkerClick = (event) => {
  baseHandleTimelineMarkerClick(event)
}


const handlePathClick = (event) => {
  baseHandlePathClick(event)
}


const handleTripMarkerClick = (event) => {
  // Check if this trip is already highlighted - if so, clear it
  if (highlightStore.isItemHighlighted(event.tripData)) {
    clearAllMapHighlights()
  } else {
    // Use simplified highlighting (store only)
    highlightTimelineItem(event.tripData)
  }
}

const handleHighlightedTripClick = () => {
  clearAllMapHighlights()
}

const handleFavoriteClick = (event) => {
  baseHandleFavoriteClick(event)
}

// Immich layer event handlers
const handlePhotoClick = (event) => {
  openPhotoViewerFromPayload(event)
}

const handlePhotoHover = (event) => {
  // Could show preview tooltip in the future
}

const handlePhotoShowOnMap = (photo) => {
  if (!photo || typeof photo.latitude !== 'number' || typeof photo.longitude !== 'number' || !map.value) {
    return
  }

  const targetZoom = Math.max(map.value.getZoom?.() || 0, 16)
  focusOnMapPhoto(map.value, photo, targetZoom)
}

const focusOnPhoto = (photo) => {
  handlePhotoShowOnMap(photo)
}

const handleImmichError = (event) => {
  
  let title = 'Immich Photos Error'
  let detail = 'Failed to load photos from Immich'
  
  // Customize error messages based on error type
  switch (event.type) {
    case 'fetch':
      title = 'Failed to Load Photos'
      detail = event.message || 'Unable to fetch photos from your Immich server. Please check your configuration.'
      break
    case 'refresh':
      title = 'Refresh Failed'
      detail = event.message || 'Unable to refresh photos from Immich. Please try again.'
      break
    case 'config':
      title = 'Configuration Error'
      detail = event.message || 'Immich configuration is invalid. Please check your server settings.'
      break
    default:
      detail = event.message || detail
  }
  
  toast.add({
    severity: 'error',
    summary: title,
    detail: detail,
    life: 5000
  })
}


const handleFavoriteEdit = (event) => {
  const favorite = event.favorite || event
  emit('edit-favorite', favorite)
}

const handleFavoriteDelete = (event) => {
  const favorite = event.favorite || event
  emit('delete-favorite', favorite)
}

const handleFavoriteContextMenu = (event) => {
  if (!props.enableFavoriteContextMenu) {
    return
  }

  if (mapContextMenuShowTimeoutId !== null) {
    clearTimeout(mapContextMenuShowTimeoutId)
    mapContextMenuShowTimeoutId = null
  }

  // Set flag to prevent map context menu
  favoriteContextMenuActive.value = true

  // Prevent default browser context menu and map context menu
  if (event.event) {
    event.event.preventDefault()
    event.event.stopPropagation()
    event.event.stopImmediatePropagation()
  }

  // Store the selected favorite for context menu actions
  dialogState.value.selectedFavorite = event

  mapContextMenuRef.value?.hide?.()

  // Show favorite context menu
  if (favoriteContextMenuRef.value && event.event) {
    favoriteContextMenuRef.value.show(event.event)
    setTimeout(() => {
      favoriteContextMenuActive.value = false
    }, 120)
  }
}

const handlePlannedItemContextMenu = (event) => {
  if (mapContextMenuShowTimeoutId !== null) {
    clearTimeout(mapContextMenuShowTimeoutId)
    mapContextMenuShowTimeoutId = null
  }

  // Set flag to prevent map context menu
  favoriteContextMenuActive.value = true

  if (event.event) {
    event.event.preventDefault()
    event.event.stopPropagation()
    event.event.stopImmediatePropagation()
  }

  dialogState.value.selectedPlannedItem = event

  mapContextMenuRef.value?.hide?.()

  if (plannedItemContextMenuRef.value && event.event) {
    plannedItemContextMenuRef.value.show(event.event)
    setTimeout(() => {
      favoriteContextMenuActive.value = false
    }, 120)
  }
}

const navigateToFavoriteDetails = (favorite) => {
  if (favorite && favorite.favorite && favorite.favorite.id) {
    router.push({
      name: 'Place Details',
      params: {
        type: 'favorite',
        id: favorite.favorite.id
      }
    })
  }
}

const handleZoomToData = () => {
  if (map.value && dataBounds.value) {
    if (dataBounds.value.length === 1) {
      // Single point - center on it with a reasonable zoom
      mapContainerRef.value?.setView?.(dataBounds.value[0], 14)
    } else if (dataBounds.value.length > 1) {
      // Multiple points - fit bounds
      mapContainerRef.value?.fitBounds?.(dataBounds.value, { padding: [20, 20] })
    }
  }
}

const handleToggleHeatmap = (enabled) => {
  if (!heatmapAvailable.value && enabled) {
    return
  }
  heatmapEnabled.value = enabled
}

const handleHeatmapLayerChange = (layer) => {
  heatmapLayer.value = layer
}

// Area drawing control (for escape key handling)

const initAreaDrawControl = () => {
  if (!map.value) return
  
  // Handle escape key to cancel drawing
  const handleEscape = (e) => {
    if (e.key === 'Escape') {
      closeAddFavoriteArea()
      if (isDrawing()) {
        stopDrawing()
      }
    }
  }
  window.addEventListener('keydown', handleEscape)
  
  // Cleanup function
  return () => {
    window.removeEventListener('keydown', handleEscape)
  }
}

// Dialog handlers
const onFavoritePointSubmit = (favoriteData) => {
  const pointLatLng = dialogState.value.addToFavoritesLatLng

  if (!pointLatLng) {
    toast.add({
      severity: 'error',
      summary: 'Error',
      detail: 'Could not add favorite. Location data was missing. Please try again.',
      life: 4000
    })
    closeAddFavoritePoint()
    return
  }

  const newFavorite = {
    name: favoriteData,
    lat: pointLatLng.lat,
    lon: pointLatLng.lng,
    type: 'point'
  }

  // Close the add favorite dialog
  closeAddFavoritePoint()

  // Use the composable to handle the timeline regeneration
  const action = () => favoritesStore.addPointToFavorites(
    newFavorite.name,
    newFavorite.lat,
    newFavorite.lon
  )

  withTimelineRegeneration(action, {
    modalType: 'favorite',
    successMessage: 'Favorite point added. Timeline is regenerating.',
    errorMessage: 'Failed to add favorite point.',
    onSuccess: () => {
      toggleFavorites(true)
    }
  })
}

const onFavoriteAreaSubmit = (favoriteData) => {
  if (!drawingState.value.tempAreaLayer) return

  // Extract bounds from drawn rectangle
  const bounds = drawingState.value.tempAreaLayer.getBounds()
  const newFavorite = {
    name: favoriteData,
    type: 'area',
    northEastLat: bounds.getNorthEast().lat,
    northEastLon: bounds.getNorthEast().lng,
    southWestLat: bounds.getSouthWest().lat,
    southWestLon: bounds.getSouthWest().lng
  }

  // Close the add favorite dialog
  closeAddFavoriteArea()

  // Use the composable to handle the timeline regeneration
  const action = () => favoritesStore.addAreaToFavorites(
    newFavorite.name,
    newFavorite.northEastLat,
    newFavorite.northEastLon,
    newFavorite.southWestLat,
    newFavorite.southWestLon
  )

  withTimelineRegeneration(action, {
    modalType: 'favorite',
    successMessage: 'Favorite area added. Timeline is regenerating.',
    errorMessage: 'Failed to add favorite area.',
    onSuccess: () => {
      toggleFavorites(true)
    }
  })
}



const closeAddFavoritePoint = () => {
  dialogState.value.addToFavoritesVisible = false
  dialogState.value.addToFavoritesLatLng = null
}

const closeAddFavoriteArea = () => {
  dialogState.value.addAreaVisible = false
  
  // Clean up drawing state
  if (isDrawing()) {
    stopDrawing()
  }
  
  // Clean up temp layer
  cleanupTempLayer()
}



// Photo viewer handlers
const closePhotoViewer = () => {
  photoViewerVisible.value = false
  photoViewerPhotos.value = []
  photoViewerIndex.value = 0
}



// ─── Heatmap logic ───────────────────────────────────────────────────────────

const getHeatmapRangeKey = (startTime, endTime, layer) => {
  return `range:${startTime}:${endTime}:${layer}`
}

const loadHeatmap = async () => {
  if (!heatmapEnabled.value || !heatmapAvailable.value) {
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
  if (!heatmapAvailable.value) return
  const range = dateRangeStore.getCurrentDateRange
  if (!range || range.length !== 2) return
  const [startTime, endTime] = range

  const layers = ['stays', 'trips']
  layers.forEach((layer) => {
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



// Computed data from stores and props
const processedPathData = computed(() => {
  // Handle both object format {userId, segments, points, pointCount} and direct array format
  const pathData = props.pathData || locationStore.pathData

  if (!pathData) return []

  // Prefer backend-computed segments (each segment is a contiguous recorded track,
  // so PathLayer draws separate polylines with no phantom lines between them)
  if (pathData.segments && Array.isArray(pathData.segments) && pathData.segments.length > 0) {
    return pathData.segments
  }

  // Fallback: flat points array wrapped as a single segment
  if (pathData && typeof pathData === 'object' && pathData.points) {
    return Array.isArray(pathData.points) ? [pathData.points] : []
  }

  // Already an array (e.g. passed directly as prop)
  if (Array.isArray(pathData)) {
    return pathData
  }

  return []
})

const processedTimelineData = computed(() => {
  return props.timelineData || timelineStore.timelineData || []
})


const processedFavoritesData = computed(() => {
  const storeFavorites = favoritesStore.favoritePlaces
  const propsFavorites = props.favoritePlaces
  
  if (propsFavorites) {
    // Convert from store structure {areas: [], points: []} to flat array
    if (propsFavorites.areas || propsFavorites.points) {
      return [...(propsFavorites.areas || []), ...(propsFavorites.points || [])]
    }
    return Array.isArray(propsFavorites) ? propsFavorites : []
  }
  
  if (storeFavorites) {
    // Convert from store structure {areas: [], points: []} to flat array
    if (storeFavorites.areas || storeFavorites.points) {
      return [...(storeFavorites.areas || []), ...(storeFavorites.points || [])]
    }
    return Array.isArray(storeFavorites) ? storeFavorites : []
  }
  
  return []
})

const processedPlannedItemsData = computed(() => {
  return Array.isArray(props.plannedItemsData) ? props.plannedItemsData : []
})

const hasAnyData = computed(() => {
  return processedPathData.value.length > 0 ||
         processedTimelineData.value.length > 0 ||
         processedFavoritesData.value.length > 0 ||
         processedPlannedItemsData.value.length > 0
})

const dataBounds = computed(() => {
  const bounds = []
  
  // Add path data bounds
  if (processedPathData.value.length > 0) {
    processedPathData.value.forEach(pathGroup => {
      if (Array.isArray(pathGroup)) {
        pathGroup.forEach(point => {
          if (point.latitude && point.longitude) {
            bounds.push([point.latitude, point.longitude])
          }
        })
      }
    })
  }
  
  // Add timeline data bounds
  if (processedTimelineData.value.length > 0) {
    processedTimelineData.value.forEach(item => {
      if (item.latitude && item.longitude) {
        bounds.push([item.latitude, item.longitude])
      }
    })
  }
  
  // Add favorites data bounds
  if (processedFavoritesData.value.length > 0) {
    processedFavoritesData.value.forEach(favorite => {
      if (favorite.type === 'point' && favorite.latitude && favorite.longitude) {
        bounds.push([favorite.latitude, favorite.longitude])
      } else if (favorite.type === 'area' && favorite.coordinates) {
        favorite.coordinates.forEach(coord => {
          if (coord.length === 2) {
            bounds.push(coord)
          }
        })
      }
    })
  }

  // Add planned items bounds
  if (processedPlannedItemsData.value.length > 0) {
    processedPlannedItemsData.value.forEach(item => {
      if (item.latitude && item.longitude) {
        bounds.push([item.latitude, item.longitude])
      }
    })
  }
  
  return bounds.length > 0 ? bounds : null
})

// Watch for data bounds changes and update map view
let lastBoundsString = ''
watch(dataBounds, (newBounds) => {
  if (map.value && newBounds && hasAnyData.value) {
    const boundsString = JSON.stringify(newBounds)
    if (boundsString !== lastBoundsString) {
      lastBoundsString = boundsString
      nextTick(() => {
        // Delay fitBounds to let initial tiles load
        setTimeout(() => {
          if (map.value) {
            if (newBounds.length === 1) {
              // Single point - center on it with a reasonable zoom
              mapContainerRef.value?.setView?.(newBounds[0], 14, { animate: false })
            } else if (newBounds.length > 1) {
              // Multiple points - fit bounds
              mapContainerRef.value?.fitBounds?.(newBounds, { 
                padding: [20, 20],
                maxZoom: 16,
                animate: false // Disable animation to prevent tile issues
              })
            }
          }
        }, 200)
      })
    }
  }
}, { immediate: true })

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

watch(activeHighlightedTripKey, (newTripKey, previousTripKey) => {
  if (newTripKey === previousTripKey) {
    return
  }

  replayPathPayload.value = { tripKey: '', points: [] }
  resetTripReplay({ resetPreferences: true })
})

watch(showTripReplayBar, (visible) => {
  if (!visible) {
    resetTripReplay({ resetPreferences: true })
  }
})

watch(replayDurationMs, (durationMs) => {
  replayElapsedMs.value = clampTripReplayElapsed(replayElapsedMs.value, durationMs)

  if (durationMs <= 0) {
    pauseTripReplay()
  }
})

// Lifecycle
onMounted(() => {
  if (props.showFavoritesByDefault) {
    toggleFavorites(true)
  }
  if (props.showImmichByDefault) {
    toggleImmich(true)
  }
})

onUnmounted(() => {
  if (mapContextMenuShowTimeoutId !== null) {
    clearTimeout(mapContextMenuShowTimeoutId)
    mapContextMenuShowTimeoutId = null
  }
  pauseTripReplay()
  replayPathPayload.value = { tripKey: '', points: [] }
  clearFocusedPhotoMarker()
})

const invalidateSize = () => {
  mapContainerRef.value?.invalidateSize?.()
}

const setView = (center, zoom, options = {}) => {
  mapContainerRef.value?.setView?.(center, zoom, options)
}

const fitBounds = (bounds, options = {}) => {
  mapContainerRef.value?.fitBounds?.(bounds, options)
}

// Expose methods for parent component
defineExpose({
  map: readonly(map),
  clearAllHighlights: clearAllMapHighlights,
  zoomToData: handleZoomToData,
  focusOnPhoto,
  invalidateSize,
  setView,
  fitBounds
})
</script>

<style scoped>
.map-view-container {
  width: 100%;
  height: 100%;
  min-height: 400px;
  position: relative;
  background-color: var(--gp-surface-light, #f8fafc);
  flex: 1;
  display: flex;
  flex-direction: column;
}

.map-controls {
  position: absolute;
  top: var(--gp-spacing-lg, 1rem);
  right: var(--gp-spacing-lg, 1rem);
  z-index: 900;
}

.trip-replay-bar {
  position: absolute;
  left: 50%;
  bottom: 0.75rem;
  transform: translateX(-50%);
  width: min(900px, calc(100% - 1.25rem));
  z-index: 920;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 0.5rem;
  padding: 0.6rem 0.7rem;
  border-radius: 0.75rem;
  border: 1px solid rgba(148, 163, 184, 0.55);
  background: rgba(255, 255, 255, 0.95);
  box-shadow: 0 10px 26px rgba(15, 23, 42, 0.22);
  backdrop-filter: blur(2px);
}

.trip-replay-bar-main {
  flex: 1 1 440px;
  display: flex;
  align-items: center;
  gap: 0.5rem;
  min-width: 260px;
}

.trip-replay-bar-options {
  flex: 0 1 auto;
  display: flex;
  align-items: center;
  gap: 0.45rem;
}

.trip-replay-speeds {
  display: flex;
  align-items: center;
  gap: 0.3rem;
}

.trip-replay-btn,
.trip-replay-speed-btn,
.trip-replay-toggle-btn {
  border: 1px solid rgba(148, 163, 184, 0.55);
  background: rgba(248, 250, 252, 0.96);
  color: #0f172a;
  cursor: pointer;
  border-radius: 0.5rem;
  transition: background-color 0.18s ease, color 0.18s ease, border-color 0.18s ease;
}

.trip-replay-btn {
  width: 2rem;
  height: 2rem;
  display: flex;
  align-items: center;
  justify-content: center;
}

.trip-replay-btn:hover,
.trip-replay-speed-btn:hover,
.trip-replay-toggle-btn:hover {
  background: rgba(226, 232, 240, 0.96);
}

.trip-replay-speed-btn {
  min-width: 2.5rem;
  height: 2rem;
  padding: 0 0.45rem;
  font-size: 0.8rem;
  font-weight: 600;
}

.trip-replay-toggle-btn {
  height: 2rem;
  padding: 0 0.55rem;
  font-size: 0.78rem;
  font-weight: 600;
  display: flex;
  align-items: center;
  gap: 0.25rem;
}

.trip-replay-slider {
  flex: 1;
  min-width: 140px;
  accent-color: #2563eb;
}

.trip-replay-time {
  min-width: 2.9rem;
  font-size: 0.8rem;
  font-weight: 700;
  color: #334155;
  font-variant-numeric: tabular-nums;
}

.p-dark .trip-replay-bar {
  border-color: rgba(100, 116, 139, 0.6);
  background: linear-gradient(135deg, rgba(15, 23, 42, 0.94), rgba(30, 41, 59, 0.92));
  box-shadow: 0 12px 28px rgba(2, 6, 23, 0.58);
}

.p-dark .trip-replay-btn,
.p-dark .trip-replay-speed-btn,
.p-dark .trip-replay-toggle-btn {
  border-color: rgba(100, 116, 139, 0.62);
  background: rgba(30, 41, 59, 0.94);
  color: rgba(226, 232, 240, 0.97);
}

.p-dark .trip-replay-btn:hover,
.p-dark .trip-replay-speed-btn:hover,
.p-dark .trip-replay-toggle-btn:hover {
  background: rgba(51, 65, 85, 0.95);
}

.trip-replay-speed-btn.active,
.trip-replay-toggle-btn.active {
  border-color: rgba(30, 64, 175, 0.88);
  background: rgba(37, 99, 235, 0.95);
  color: #ffffff;
}

.p-dark .trip-replay-speed-btn.active,
.p-dark .trip-replay-toggle-btn.active {
  border-color: rgba(56, 189, 248, 0.98) !important;
  background: linear-gradient(135deg, rgba(37, 99, 235, 0.99), rgba(14, 165, 233, 0.97)) !important;
  color: #ffffff !important;
  box-shadow: 0 0 0 1px rgba(125, 211, 252, 0.45), 0 6px 16px rgba(14, 116, 144, 0.45);
}

.p-dark .trip-replay-speed-btn.active:hover,
.p-dark .trip-replay-toggle-btn.active:hover,
.p-dark .trip-replay-speed-btn.active:focus-visible,
.p-dark .trip-replay-toggle-btn.active:focus-visible {
  border-color: rgba(125, 211, 252, 1) !important;
  background: linear-gradient(135deg, rgba(59, 130, 246, 1), rgba(6, 182, 212, 0.98)) !important;
  color: #ffffff !important;
  box-shadow: 0 0 0 2px rgba(125, 211, 252, 0.5), 0 8px 18px rgba(14, 116, 144, 0.5);
}

.p-dark .trip-replay-time {
  color: rgba(226, 232, 240, 0.93);
}

/* Responsive adjustments */
@media (max-width: 768px) {
  .map-controls {
    top: var(--gp-spacing-md, 0.75rem);
    right: var(--gp-spacing-md, 0.75rem);
  }

  .map-view-container {
    width: 100%;
    height: 100%;
    min-height: 300px;
  }

  .trip-replay-bar {
    bottom: 0.5rem;
    width: calc(100% - 0.75rem);
    padding: 0.5rem 0.55rem;
    gap: 0.4rem;
  }

  .trip-replay-bar-main {
    flex: 1 1 100%;
    min-width: 0;
    gap: 0.35rem;
  }

  .trip-replay-bar-options {
    width: 100%;
    justify-content: space-between;
  }

  .trip-replay-speed-btn {
    min-width: 2.2rem;
    font-size: 0.74rem;
  }

  .trip-replay-toggle-btn {
    font-size: 0.72rem;
    padding: 0 0.45rem;
  }
}
</style>
