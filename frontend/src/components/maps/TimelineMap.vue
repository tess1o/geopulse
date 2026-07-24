<template>
  <div
    class="map-view-container"
    :class="{
      'map-view-container--trip-replay-bar': showTripReplayBar,
      'map-view-container--trip-replay-restore': showTripReplayRestoreButton
    }"
  >
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
          :show-raw-gps-points-control="!props.isPublicView"
          :raw-gps-points-enabled="showRawGpsPoints"
          :raw-gps-points-loading="rawGpsPointsLoading"
          :show-immich="showImmich"
          :show-heatmap="props.showHeatmapControl && !props.isPublicView"
          :heatmap-enabled="heatmapEnabled"
          :heatmap-layer="heatmapLayer"
          :heatmap-available="heatmapAvailable"
          :immich-configured="immichConfigured"
          :immich-loading="immichLoading"
          :show-notes="showNotesLayer"
          :show-notes-button="shouldShowNotesLayer"
          :notes-loading="notesLoading"
          :show-weather="showWeather"
          :show-weather-button="!props.isPublicView && weatherSamples.length > 0"
          :weather-loading="timelineStore.weatherLoading"
          @toggle-favorites="toggleFavorites"
          @toggle-timeline="toggleTimeline"
          @toggle-path="togglePath"
          @toggle-raw-gps-points="handleToggleRawGpsPoints"
          @toggle-immich="toggleImmich"
          @toggle-notes="toggleNotes"
          @toggle-weather="toggleWeather"
          @toggle-heatmap="handleToggleHeatmap"
          @heatmap-layer-change="handleHeatmapLayerChange"
          @zoom-to-data="handleZoomToData"
          class="map-controls"
        />
        <ViewerLocationControl
          v-if="map && isReady && showViewerLocationControl"
          class="timeline-viewer-location-control"
          style="top: 4.75rem;"
          :status="viewerLocationStatus"
          :active="viewerLocationActive"
          :message="viewerLocationMessage"
          @locate="handleViewerLocationRequest"
          @stop="handleViewerLocationStop"
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
          :show-highlighted-trip-popup="showHighlightedTripPopup"
          @path-click="handlePathClick"
          @trip-marker-click="handleTripMarkerClick"
          @highlighted-trip-click="handleHighlightedTripClick"
          @highlighted-trip-replay-data="handleHighlightedTripReplayData"
        />

        <RawGpsPointsLayer
          v-if="map && isReady && !props.isPublicView"
          :map="map"
          :points="rawGpsPoints"
          :visible="showRawGpsPoints"
          :resolve-location="resolveRawGpsPointLocation"
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

        <!-- Notes Layer -->
        <NotesLayer
          v-if="map && isReady && shouldShowNotesLayer"
          ref="notesLayerRef"
          :map="map"
          :visible="showNotesLayer"
          :notes="notesForLayer"
          :load-notes="!props.isPublicView"
          :can-manage-notes="!props.isPublicView"
          @error="handleNotesError"
        />

        <WeatherLayer
          v-if="map && isReady && !props.isPublicView"
          :map="map"
          :samples="weatherSamples"
          :visible="showWeather"
          :highlighted-item="activeTimelineHighlight"
        />

        <!-- Current Location Layer -->
        <CurrentLocationLayer
          v-if="map && isReady && showCurrentLocation && currentLocation"
          :map="map"
          :location="currentLocation"
        />

        <ViewerLocationMarker
          v-if="map && isReady && viewerLocation"
          :map="map"
          :location="viewerLocation"
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
      v-if="showMobileTripSummary"
      class="mobile-trip-summary"
      :class="{
        'mobile-trip-summary--above-replay': showTripReplayBar,
        'mobile-trip-summary--above-restore': showTripReplayRestoreButton && !showTripReplayBar
      }"
      @mousedown.stop
      @touchstart.stop
      @click.stop
    >
      <div class="mobile-trip-summary-icon">
        <i :class="mobileTripSummary.iconClass"></i>
      </div>
      <div class="mobile-trip-summary-content">
        <div class="mobile-trip-summary-title">{{ mobileTripSummary.title }}</div>
        <div class="mobile-trip-summary-meta">
          <span>{{ mobileTripSummary.duration }}</span>
          <span class="mobile-trip-summary-dot"></span>
          <span>{{ mobileTripSummary.distance }}</span>
          <span v-if="mobileTripSummary.averageSpeed" class="mobile-trip-summary-dot"></span>
          <span v-if="mobileTripSummary.averageSpeed">{{ mobileTripSummary.averageSpeed }}</span>
        </div>
      </div>
      <button
        type="button"
        class="mobile-trip-summary-close"
        title="Clear trip selection"
        aria-label="Clear trip selection"
        @click="clearAllMapHighlights"
      >
        <i class="pi pi-times"></i>
      </button>
    </div>

    <TripReplayControls
      :show-bar="showTripReplayBar"
      :show-restore-button="showTripReplayRestoreButton"
      :is-playing="isReplayPlaying"
      :elapsed-label="replayElapsedLabel"
      :duration-label="replayDurationLabel"
      :slider-value="replaySliderValue"
      :speed-presets="replaySpeedPresets"
      :speed-multiplier="replaySpeedMultiplier"
      :follow-camera="replayFollowCamera"
      :enable3d="replayEnable3d"
      :show3d-toggle="isVectorMapMode"
      @toggle-playback="toggleReplayPlayback"
      @stop="stopTripReplay"
      @slider-input="handleReplaySliderInput"
      @set-speed="setReplaySpeed"
      @toggle-follow-camera="toggleReplayFollowCamera"
      @toggle-3d="toggleReplay3d"
      @dismiss="dismissTripReplayControls"
      @restore="restoreTripReplayControls"
    />
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
import { useTripReplayControls } from '@/composables/useTripReplayControls'
import { formatDistance, formatDuration, formatSpeed } from '@/utils/calculationsHelpers'
import { getTripMovementIconClass } from '@/utils/timelineIconUtils'
import { resolveAverageTripSpeedKmh } from '@/maps/shared/tripSpeed'

// Map components
import {FavoritesLayer, HeatmapLayer, MapContainer, MapControls, PathLayer, TimelineLayer, CurrentLocationLayer, ImmichLayer, NotesLayer, TripPlanLayer, RawGpsPointsLayer, WeatherLayer} from '@/components/maps'
import TripReplayControls from '@/components/maps/TripReplayControls.vue'
import ViewerLocationControl from '@/components/maps/ViewerLocationControl.vue'
import ViewerLocationMarker from '@/components/maps/ViewerLocationMarker.vue'
import apiService from '@/utils/apiService'

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
import {useNotesStore} from '@/stores/notes'
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
  viewerLocation: {
    type: Object,
    default: () => null
  },
  viewerLocationStatus: {
    type: String,
    default: 'idle'
  },
  viewerLocationActive: {
    type: Boolean,
    default: false
  },
  viewerLocationMessage: {
    type: String,
    default: ''
  },
  showViewerLocationControl: {
    type: Boolean,
    default: false
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
  showNotes: {
    type: Boolean,
    default: false
  },
  notes: {
    type: Array,
    default: null
  },
  weatherSamples: {
    type: Array,
    default: () => []
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
  showNotesByDefault: {
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
  },
  autoShowTripReplayControls: {
    type: Boolean,
    default: true
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
  'viewer-location-request',
  'viewer-location-stop',
  'plan-to-visit',
  'plan-item-edit',
  'plan-item-delete'
])

// Router
const router = useRouter()
const MOBILE_TRIP_SELECTION_MEDIA = '(max-width: 768px), (pointer: coarse)'

// Composables
const {
  showFavorites,
  showTimeline,
  showPath,
  showRawGpsPoints,
  showImmich,
  showNotes: showNotesLayer,
  showWeather,
  toggleFavorites,
  toggleTimeline,
  togglePath,
  toggleRawGpsPoints,
  toggleImmich,
  toggleNotes,
  toggleWeather
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
const notesStore = useNotesStore()
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
const notesLayerRef = ref(null)
const mapContextMenuRef = ref(null)
const favoriteContextMenuRef = ref(null)
const plannedItemContextMenuRef = ref(null)
const isMobileTripSelectionViewport = ref(false)

const confirm = useConfirm()
const toast = useToast()

// Local state
const map = shallowRef(null)
const favoriteContextMenuActive = ref(false)
const heatmapEnabled = ref(false)
const heatmapLayer = ref('stays')
const heatmapPoints = ref([])
const rawGpsPoints = ref([])
const rawGpsPointsLoading = ref(false)
let heatmapRequestId = 0
let rawGpsPointsRequestId = 0
const heatmapPrefetches = new Map()
const rawGpsPointsCache = new Map()
const rawGpsLocationCache = new Map()
const rawGpsLimitWarningKeys = new Set()
let mapContextMenuShowTimeoutId = null

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
const notesLoading = computed(() => notesStore.notesLoading)

// For public views, respect the showPhotos prop; for private views, always allow
const shouldShowImmich = computed(() => {
  if (props.isPublicView) {
    return props.showPhotos
  }
  return true // For non-public views, always allow (controlled by toggle)
})

const shouldShowNotesLayer = computed(() => {
  if (props.isPublicView) {
    return props.showNotes
  }
  return true
})

const notesForLayer = computed(() => (
  Array.isArray(props.notes) ? props.notes : null
))

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

const mapEngineMode = computed(() => resolveMapEngineModeFromInstance(map.value, MAP_RENDER_MODES.RASTER))
const isVectorMapMode = computed(() => mapEngineMode.value === MAP_RENDER_MODES.VECTOR)
const showHighlightedTripPopup = computed(() => !isMobileTripSelectionViewport.value)
const activeHighlightedTrip = computed(() => {
  if (!activeTimelineHighlight.value || activeTimelineHighlight.value.type !== 'trip') {
    return null
  }

  return activeTimelineHighlight.value
})

const formatTripMovementTitle = (movementType) => {
  const normalized = String(movementType || 'Movement')
    .replace(/[_-]+/g, ' ')
    .trim()
    .toLowerCase()

  const label = normalized
    ? normalized.replace(/\b\w/g, (letter) => letter.toUpperCase())
    : 'Movement'

  return `${label} Trip`
}

const mobileTripSummary = computed(() => {
  const trip = activeHighlightedTrip.value
  if (!trip) return null

  const averageSpeedKmh = resolveAverageTripSpeedKmh(trip)

  return {
    iconClass: getTripMovementIconClass(trip.movementType),
    title: formatTripMovementTitle(trip.movementType),
    duration: formatDuration(Number(trip.tripDuration) || 0),
    distance: formatDistance(Number(trip.distanceMeters) || 0),
    averageSpeed: Number.isFinite(averageSpeedKmh) ? formatSpeed(averageSpeedKmh) : null
  }
})

const showMobileTripSummary = computed(() => (
  isMobileTripSelectionViewport.value
  && Boolean(mobileTripSummary.value)
))
const {
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
  stopTripReplay,
  dismissTripReplayControls,
  restoreTripReplayControls,
  toggleReplayPlayback,
  handleReplaySliderInput,
  setReplaySpeed,
  toggleReplayFollowCamera,
  toggleReplay3d,
  handleHighlightedTripReplayData,
  cleanupTripReplay
} = useTripReplayControls({
  enabled: computed(() => props.enableTripReplay),
  activeTrip: activeHighlightedTrip,
  showPath,
  supports3d: isVectorMapMode,
  autoShowControls: computed(() => props.autoShowTripReplayControls)
})

const hideTimelineMarkersForReplay = computed(() => showTripReplayBar.value && isReplayPlaying.value)
const timelineLayerVisible = computed(() => showTimeline.value && !hideTimelineMarkersForReplay.value)

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

const syncMobileTripSelectionViewport = () => {
  if (typeof window === 'undefined' || typeof window.matchMedia !== 'function') {
    isMobileTripSelectionViewport.value = false
    return
  }

  isMobileTripSelectionViewport.value = window.matchMedia(MOBILE_TRIP_SELECTION_MEDIA).matches
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

const handleNotesError = (event) => {
  let title = 'Notes Error'
  let detail = 'Failed to load notes'

  switch (event.type) {
    case 'fetch':
      title = 'Failed to Load Notes'
      detail = event.message || 'Unable to fetch notes for this date range.'
      break
    case 'refresh':
      title = 'Refresh Failed'
      detail = event.message || 'Unable to refresh notes. Please try again.'
      break
    default:
      detail = event.message || detail
  }

  toast.add({
    severity: 'error',
    summary: title,
    detail,
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

const handleViewerLocationRequest = () => {
  emit('viewer-location-request')
}

const handleViewerLocationStop = () => {
  emit('viewer-location-stop')
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

const handleToggleRawGpsPoints = (enabled) => {
  if (props.isPublicView) return

  toggleRawGpsPoints(enabled)
  if (enabled) {
    toggleTimeline(false)
    togglePath(true)
    loadRawGpsPoints()
  }
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

// ─── Raw GPS point inspector logic ──────────────────────────────────────────

const getRawGpsRange = () => {
  const range = dateRangeStore.getCurrentDateRange
  return Array.isArray(range) && range.length === 2 && range[0] && range[1]
    ? range
    : null
}

const getRawGpsRangeKey = (startTime, endTime) => `raw-gps:${startTime}:${endTime}`

const loadRawGpsPoints = async () => {
  if (!showRawGpsPoints.value || props.isPublicView) {
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
    const response = await apiService.get('/gps/map-points', {
      startTime,
      endTime,
      limit: 10000
    })
    if (requestId !== rawGpsPointsRequestId) return

    const data = response?.data || {}
    const points = Array.isArray(data.points) ? data.points : []
    const meta = {
      totalCount: Number(data.totalCount || 0),
      returnedCount: Number(data.returnedCount || points.length),
      limit: Number(data.limit || 10000),
      limited: Boolean(data.limited)
    }

    rawGpsPointsCache.set(key, { points, meta })
    rawGpsPoints.value = points

    if (meta.limited && !rawGpsLimitWarningKeys.has(key)) {
      rawGpsLimitWarningKeys.add(key)
      toast.add({
        severity: 'warn',
        summary: 'Raw GPS points limited',
        detail: `Showing ${meta.returnedCount} of ${meta.totalCount} points. Narrow the date range for exact inspection.`,
        life: 5000
      })
    }
  } catch (error) {
    if (requestId !== rawGpsPointsRequestId) return
    rawGpsPoints.value = []
    console.error('Failed to load raw GPS points:', error)
    toast.add({
      severity: 'error',
      summary: 'Raw GPS Points',
      detail: 'Failed to load raw GPS points for this date range.',
      life: 5000
    })
  } finally {
    if (requestId === rawGpsPointsRequestId) {
      rawGpsPointsLoading.value = false
    }
  }
}

const getRawGpsLocationCacheKey = (point) => {
  if (point?.id != null) {
    return `id:${point.id}`
  }
  return `coord:${point?.latitude}:${point?.longitude}`
}

const resolveRawGpsPointLocation = async (point) => {
  if (!point?.id) {
    throw new Error('GPS point id is required to resolve location')
  }

  const key = getRawGpsLocationCacheKey(point)
  if (rawGpsLocationCache.has(key)) {
    return rawGpsLocationCache.get(key)
  }

  const promise = apiService
    .get(`/gps/points/${point.id}/location`)
    .then((response) => response?.data)

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
    if (showRawGpsPoints.value) {
      loadRawGpsPoints()
    }
  },
  { immediate: true }
)

watch(showRawGpsPoints, (enabled) => {
  if (enabled) {
    loadRawGpsPoints()
  }
})

// Lifecycle
onMounted(() => {
  syncMobileTripSelectionViewport()
  window.addEventListener('resize', syncMobileTripSelectionViewport)
  window.visualViewport?.addEventListener?.('resize', syncMobileTripSelectionViewport)

  if (props.showFavoritesByDefault) {
    toggleFavorites(true)
  }
  if (props.showImmichByDefault) {
    toggleImmich(true)
  }
  if (props.showNotesByDefault) {
    toggleNotes(true)
  }
})

onUnmounted(() => {
  window.removeEventListener('resize', syncMobileTripSelectionViewport)
  window.visualViewport?.removeEventListener?.('resize', syncMobileTripSelectionViewport)

  if (mapContextMenuShowTimeoutId !== null) {
    clearTimeout(mapContextMenuShowTimeoutId)
    mapContextMenuShowTimeoutId = null
  }
  cleanupTripReplay()
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
  top: calc(var(--gp-spacing-lg, 1rem) + env(safe-area-inset-top));
  right: calc(var(--gp-spacing-lg, 1rem) + env(safe-area-inset-right));
  z-index: 900;
}

.mobile-trip-summary {
  position: absolute;
  left: 50%;
  bottom: calc(var(--timeline-mobile-sheet-height, 44px) + 3.25rem + env(safe-area-inset-bottom));
  transform: translateX(-50%);
  z-index: 940;
  width: min(22rem, calc(100% - 1rem - env(safe-area-inset-left) - env(safe-area-inset-right)));
  display: flex;
  align-items: center;
  gap: 0.65rem;
  padding: 0.55rem 0.6rem;
  border: 1px solid rgba(148, 163, 184, 0.58);
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.98);
  color: #0f172a;
  box-shadow: 0 10px 26px rgba(15, 23, 42, 0.22);
  backdrop-filter: blur(4px);
  pointer-events: auto;
}

.mobile-trip-summary--above-replay {
  bottom: calc(var(--timeline-mobile-sheet-height, 44px) + 9rem + env(safe-area-inset-bottom));
}

.mobile-trip-summary--above-restore {
  bottom: calc(var(--timeline-mobile-sheet-height, 44px) + 5.75rem + env(safe-area-inset-bottom));
}

.mobile-trip-summary-icon {
  flex: 0 0 2rem;
  width: 2rem;
  height: 2rem;
  border-radius: 999px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--gp-primary, #1a56db);
  color: #ffffff;
  font-size: 0.9rem;
}

.mobile-trip-summary-content {
  flex: 1 1 auto;
  min-width: 0;
}

.mobile-trip-summary-title {
  overflow: hidden;
  color: #0f172a;
  font-size: 0.88rem;
  font-weight: 700;
  line-height: 1.2;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.mobile-trip-summary-meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 0.38rem;
  overflow: visible;
  color: #334155;
  font-size: 0.76rem;
  font-weight: 600;
  line-height: 1.2;
  white-space: normal;
}

.mobile-trip-summary-dot {
  flex: 0 0 4px;
  width: 4px;
  height: 4px;
  border-radius: 999px;
  background: rgba(100, 116, 139, 0.7);
}

.mobile-trip-summary-close {
  flex: 0 0 2rem;
  width: 2rem;
  height: 2rem;
  border: 1px solid rgba(148, 163, 184, 0.55);
  border-radius: 999px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(248, 250, 252, 0.98);
  color: #334155;
  cursor: pointer;
  transition: background 0.15s ease, color 0.15s ease, border-color 0.15s ease;
}

.mobile-trip-summary-close:hover,
.mobile-trip-summary-close:focus-visible {
  border-color: var(--gp-primary-light, #60a5fa);
  background: rgba(239, 246, 255, 0.98);
  color: var(--gp-primary, #1a56db);
}

:global(.p-dark) .mobile-trip-summary {
  border-color: rgba(100, 116, 139, 0.65);
  background: rgba(15, 23, 42, 0.94);
  color: rgba(248, 250, 252, 0.96);
  box-shadow: 0 12px 28px rgba(2, 6, 23, 0.48);
}

:global(.p-dark) .mobile-trip-summary-title {
  color: rgba(248, 250, 252, 0.96);
}

:global(.p-dark) .mobile-trip-summary-meta {
  color: rgba(203, 213, 225, 0.88);
}

:global(.p-dark) .mobile-trip-summary-close {
  border-color: rgba(100, 116, 139, 0.65);
  background: rgba(30, 41, 59, 0.95);
  color: rgba(203, 213, 225, 0.92);
}

/* Responsive adjustments */
@media (max-width: 768px), (max-height: 520px) and (pointer: coarse) {
  .map-controls {
    top: calc(var(--gp-spacing-md, 0.75rem) + env(safe-area-inset-top));
    right: calc(var(--gp-spacing-md, 0.75rem) + env(safe-area-inset-right));
  }

  .map-view-container {
    width: 100%;
    height: 100%;
    min-height: 300px;
  }

  .mobile-trip-summary {
    width: calc(100% - 1rem - env(safe-area-inset-left) - env(safe-area-inset-right));
    max-width: 22rem;
  }
}
</style>
