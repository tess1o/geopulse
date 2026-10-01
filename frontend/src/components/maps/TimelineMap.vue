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
        <div v-if="map && isReady" class="timeline-map-control-stack">
          <MapControls
            :map="map"
            :show-favorites="showFavorites"
            :show-timeline="showTimeline"
            :show-path="showPath"
            :show-route-display-mode-control="routeDisplayModeControlAvailable"
            :route-display-mode="routeDisplayMode"
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
            :show-panoramax-control="panoramaxControlAvailable"
            :panoramax-enabled="showPanoramax"
            :panoramax-supported="isVectorMapMode"
            :show3d-buildings-control="show3dBuildingsControl"
            :buildings3d-enabled="buildings3dEnabled"
            :zoom-control-title="zoomControlTitle"
            :zoom-control-icon="zoomControlIcon"
            @toggle-favorites="toggleFavorites"
            @toggle-timeline="toggleTimeline"
            @toggle-path="togglePath"
            @cycle-route-display-mode="cycleRouteDisplayMode"
            @toggle-raw-gps-points="handleToggleRawGpsPoints"
            @toggle-immich="toggleImmich"
            @toggle-notes="toggleNotes"
            @toggle-weather="toggleWeather"
            @toggle-panoramax="togglePanoramax"
            @toggle-3d-buildings="handleToggle3dBuildings"
            @toggle-heatmap="handleToggleHeatmap"
            @heatmap-layer-change="handleHeatmapLayerChange"
            @zoom-to-data="handleZoomToData"
          />
          <ViewerLocationControl
            v-if="showViewerLocationControl"
            class="timeline-viewer-location-control"
            :status="viewerLocationStatus"
            :active="viewerLocationActive"
            :message="viewerLocationMessage"
            @locate="handleViewerLocationRequest"
            @stop="handleViewerLocationStop"
          />
        </div>
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

        <!-- Single interactive path: matched route unless raw route mode is selected. -->
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

        <!-- Diagnostic overlay: raw GPS above the matched route. -->
        <PathLayer
          v-if="map && isReady && routeDisplayModeControlAvailable"
          :map="map"
          :path-data="rawComparisonPathData"
          :highlighted-trip="null"
          :visible="showPath && routeDisplayModeUsesComparison && rawComparisonPathData.length > 0"
          :path-options="rawComparisonPathOptions"
          :outline="false"
          :inspection-enabled="false"
          :focus-highlighted-trip="false"
          :show-highlighted-trip-popup="false"
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
          :item-weather="stayWeatherByTimelineIndex"
          @marker-click="handleTimelineMarkerClick"
          @marker-contextmenu="handleTimelineMarkerContextMenu"
          @groups-change="requestCrossTypeCompute"
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
          :photos="photosForLayer"
          :auth-token="props.photoAuthToken"
          @photo-click="handlePhotoClick"
          @photo-hover="handlePhotoHover"
          @error="handleImmichError"
          @groups-change="requestCrossTypeCompute"
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
          @groups-change="requestCrossTypeCompute"
        />

        <!-- Combo markers for Stays/Trips, Notes and Photos that overlap each other (vector maps only). -->
        <VectorCrossTypeCollisionLayer
          v-if="map && isReady && isVectorMapMode"
          ref="crossTypeLayerRef"
          :map="map"
          :get-sources="getCrossTypeSources"
          :item-weather="stayWeatherByTimelineIndex"
          @select-timeline="handleTimelineMarkerClick"
          @select-notes="handleCrossTypeNotesSelect"
          @select-photos="openPhotoViewerFromPayload"
        />

        <WeatherLayer
          v-if="map && isReady && !props.isPublicView"
          ref="weatherLayerRef"
          :map="map"
          :samples="weatherLayerSamples"
          :visible="showWeather"
          :highlighted-item="activeTimelineHighlight"
          :managed="isVectorMapMode"
          @groups-change="requestCrossTypeCompute"
        />

        <VectorPanoramaxLayer
          v-if="map && isReady && panoramaxControlAvailable && isVectorMapMode"
          :map="map"
          :visible="showPanoramax"
          :endpoint="props.panoramaxEndpoint"
          @select="openPanoramaxViewer"
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
          ref="stayContextMenuRef"
          :model="stayMenuItems"
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
          :header="t('maps.timelineMap.addFavoriteDialog.pointHeader')"
          @add-to-favorites="onFavoritePointSubmit"
          @close="closeAddFavoritePoint"
        />

        <AddFavoriteDialog
          v-if="addAreaShowDialog"
          :visible="addAreaShowDialog"
          :header="t('maps.timelineMap.addFavoriteDialog.areaHeader')"
          @add-to-favorites="onFavoriteAreaSubmit"
          @close="closeAddFavoriteArea"
        />

        <!-- Photo Viewer Dialog -->
        <PhotoViewerDialog
          v-model:visible="photoViewerVisible"
          :photos="photoViewerPhotos"
          :initial-photo-index="photoViewerIndex"
          :auth-token="props.photoAuthToken"
          @show-on-map="handlePhotoShowOnMap"
          @close="closePhotoViewer"
        />

        <LocationLookupDialog
          :visible="dialogState.locationLookupVisible"
          :point="dialogState.locationLookupPoint"
          :result="dialogState.locationLookupResult"
          :loading="dialogState.locationLookupLoading"
          :error="dialogState.locationLookupError"
          @close="closeLocationLookup"
          @retry="retryLocationLookup"
        />

        <!-- Timeline Regeneration Modal -->
        <TimelineRegenerationModal
          v-model:visible="timelineRegenerationVisible"
          :type="timelineRegenerationType"
          :job-id="currentJobId"
          :job-progress="jobProgress"
        />
        <PanoramaxViewerDialog
          v-if="panoramaxControlAvailable"
          v-model:visible="panoramaxViewerVisible"
          :endpoint="props.panoramaxEndpoint"
          :sequence-id="panoramaxSelection.sequenceId"
          :picture-id="panoramaxSelection.pictureId"
          :details="panoramaxSelection.details"
        />

      </template>
    </MapContainer>

    <MapColorLegend
      v-if="showHeatmapLegend"
      class="heatmap-legend"
      variant="heatmap"
      :gradient="heatmapGradient"
    />

    <div v-if="mapMatchingStatusText" class="map-matching-status" aria-live="polite">
      <i class="pi pi-sync map-matching-status-icon" aria-hidden="true" />
      <span>{{ mapMatchingStatusText }}</span>
    </div>

    <!-- Docked summary of the highlighted trip (all viewports): kept off the route itself. -->
    <!-- While the replay bar is open, the summary is its header row instead (see TripReplayControls). -->
    <div
      v-if="showTripSummary && !showTripReplayBar"
      class="trip-summary"
      @mousedown.stop
      @touchstart.stop
      @click.stop
    >
      <div class="trip-summary-icon">
        <i :class="tripSummary.iconClass"></i>
      </div>
      <div class="trip-summary-content">
        <div class="trip-summary-title">{{ tripSummary.title }}</div>
        <div class="trip-summary-meta">
          <template v-for="(item, index) in tripSummary.metaItems" :key="index">
            <span v-if="index > 0" class="trip-summary-dot"></span>
            <span>{{ item }}</span>
          </template>
        </div>
        <MapColorLegend
          v-if="showSpeedLegend"
          class="trip-summary-legend"
          variant="speed"
          :speed-colors="mapAppearance.speedBandColors"
          :outline="mapAppearance.outlineEnabled"
        />
        <div v-if="!isMobileTripSelectionViewport" class="trip-summary-hint">
          {{ t('maps.timelineMap.tripSummaryHoverHint') }}
        </div>
      </div>
      <!-- Replaces the separate floating "Replay" button once the replay bar is dismissed. -->
      <button
        v-if="showTripReplayRestoreButton"
        type="button"
        class="trip-summary-replay"
        :title="t('maps.tripReplay.showControls')"
        @click="restoreTripReplayControls"
      >
        <i class="pi pi-play-circle"></i>
        {{ t('maps.tripReplay.replay') }}
      </button>
      <button
        type="button"
        class="trip-summary-close"
        :title="t('maps.timelineMap.clearTripSelection')"
        :aria-label="t('maps.timelineMap.clearTripSelection')"
        @click="clearAllMapHighlights"
      >
        <i class="pi pi-times"></i>
      </button>
    </div>

    <TripReplayControls
      :show-bar="showTripReplayBar"
      :show-restore-button="showTripReplayRestoreButton && !showTripSummary"
      :summary="tripSummary"
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
import {useI18n} from 'vue-i18n'
import {useConfirm} from "primevue/useconfirm"
import {useToast} from "primevue/usetoast"
import ContextMenu from 'primevue/contextmenu'
import ConfirmDialog from 'primevue/confirmdialog'
import {useTimelineRegeneration} from '@/composables/useTimelineRegeneration'
import { usePhotoMapMarkersRuntime } from '@/maps/runtime/usePhotoMapMarkersRuntime'
import '@/styles/photo-map-markers.css'
import { MAP_RENDER_MODES, resolveMapEngineModeFromInstance } from '@/maps/contracts/mapContracts'
import { setMapTilerBuildings3dEnabled, supportsMapTilerBuildings3d } from '@/maps/vector/utils/maptilerBuildings3d'
import { useTripReplayControls } from '@/composables/useTripReplayControls'
import { useMapMatchingComparison } from '@/composables/useMapMatchingComparison'
import { formatDistance, formatDuration, formatSpeed } from '@/utils/calculationsHelpers'
import { useTimezone } from '@/composables/useTimezone'
import { getTripMovementIconClass } from '@/utils/timelineIconUtils'
import { getStayPlaceDetailsRoute } from '@/maps/shared/timelinePlaceRoute'
import { resolveAverageTripSpeedKmh } from '@/maps/shared/tripSpeed'
import { partitionWeatherSamplesByStay } from '@/maps/shared/stayWeather'
import { haversineDistanceMetersFromCoordinates } from '@/utils/geoDistance'
import { showDemoModeToast } from '@/utils/demoMode'

// Map components
import {FavoritesLayer, HeatmapLayer, MapContainer, MapControls, PathLayer, TimelineLayer, CurrentLocationLayer, ImmichLayer, NotesLayer, TripPlanLayer, RawGpsPointsLayer, WeatherLayer} from '@/components/maps'
import VectorCrossTypeCollisionLayer from '@/maps/vector/layers/VectorCrossTypeCollisionLayer.vue'
import VectorPanoramaxLayer from '@/maps/vector/layers/VectorPanoramaxLayer.vue'
import PanoramaxViewerDialog from '@/components/maps/dialogs/PanoramaxViewerDialog.vue'
import TripReplayControls from '@/components/maps/TripReplayControls.vue'
import MapColorLegend from '@/components/maps/MapColorLegend.vue'
import { useMapAppearance } from '@/composables/useMapAppearance'
import ViewerLocationControl from '@/components/maps/ViewerLocationControl.vue'
import ViewerLocationMarker from '@/components/maps/ViewerLocationMarker.vue'
import { formatApiErrorDetail } from '@/utils/apiErrorDetail'

import PhotoViewerDialog from '@/components/dialogs/PhotoViewerDialog.vue'
import LocationLookupDialog from '@/components/dialogs/LocationLookupDialog.vue'
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
import {useTechnicalDataStore} from '@/stores/technicalData'

// Props
const props = defineProps({
  pathData: {
    type: Object,
    default: () => null
  },
  preserveViewportOnDataRefresh: {
    type: Boolean,
    default: false
  },
  rawPathData: {
    type: Object,
    default: () => null
  },
  matchedTripIds: {
    type: Array,
    default: () => []
  },
  mapMatchingStatusText: {
    type: String,
    default: ''
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
  readOnly: {
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
  photos: {
    type: Array,
    default: null
  },
  photoAuthToken: {
    type: String,
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
  },
  enable3dBuildingsByDefault: {
    type: Boolean,
    default: false
  },
  panoramaxAvailable: {
    type: Boolean,
    default: false
  },
  panoramaxEndpoint: {
    type: String,
    default: ''
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
const { t, te } = useI18n()
const MOBILE_TRIP_SELECTION_MEDIA = '(max-width: 768px), (pointer: coarse)'
const TIMELINE_SINGLE_LOCATION_ZOOM = 14
const TIMELINE_FIT_BOUNDS_MAX_ZOOM = 16
const TIMELINE_COLLAPSED_BOUNDS_THRESHOLD_METERS = 250
const TIMELINE_FIT_BOUNDS_PADDING = [20, 20]

// Composables
const {
  showFavorites,
  showTimeline,
  showPath,
  showRawGpsPoints,
  showImmich,
  showNotes: showNotesLayer,
  showWeather,
  showPanoramax,
  toggleFavorites,
  toggleTimeline,
  togglePath,
  toggleRawGpsPoints,
  toggleImmich,
  toggleNotes,
  toggleWeather,
  togglePanoramax
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
const technicalDataStore = useTechnicalDataStore()

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
const crossTypeLayerRef = ref(null)
const weatherLayerRef = ref(null)
const mapContextMenuRef = ref(null)
const favoriteContextMenuRef = ref(null)
const stayContextMenuRef = ref(null)
const plannedItemContextMenuRef = ref(null)
const isMobileTripSelectionViewport = ref(false)

const confirm = useConfirm()
const toast = useToast()
const timezone = useTimezone()

// Local state
const map = shallowRef(null)
const panoramaxViewerVisible = ref(false)
const panoramaxSelection = ref({ sequenceId: null, pictureId: null, details: '' })
const featureContextMenuActive = ref(false)
const heatmapEnabled = ref(false)
const heatmapLayer = ref('stays')
const heatmapPoints = ref([])
const rawGpsPoints = ref([])
const rawGpsPointsLoading = ref(false)
const buildings3dEnabled = ref(false)
const mapTilerBuildings3dSupported = ref(false)
let mapTilerBuildings3dListenerMap = null
let mapTilerBuildings3dStyleListener = null
let heatmapRequestId = 0
let rawGpsPointsRequestId = 0
const heatmapPrefetches = new Map()
const rawGpsPointsCache = new Map()
const rawGpsLocationCache = new Map()
const rawGpsLimitWarningKeys = new Set()
let mapContextMenuShowTimeoutId = null

// Path colors, width and outline come from the viewer's appearance preferences (see PathLayer).
const mapAppearance = useMapAppearance()

const rawComparisonPathOptions = {
  color: '#a855f7',
  weight: 2,
  opacity: 0.95,
  dashArray: '5 7',
  smoothFactor: 1
}

// Dialog state
const dialogState = ref({
  addToFavoritesVisible: false,
  addAreaVisible: false,
  selectedFavorite: null,
  selectedStay: null,
  selectedPlannedItem: null,
  addToFavoritesLatLng: null,
  locationLookupVisible: false,
  locationLookupPoint: null,
  locationLookupResult: null,
  locationLookupLoading: false,
  locationLookupError: ''
})
let locationLookupRequestId = 0

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

// Map configuration - start with null to avoid showing default location before data loads.
//
// `mapCenter` freezes to the first data point it sees (set by a watcher
// further down, once `dataBounds` is declared) and stops tracking
// `dataBounds` after that. It exists only to give the map a reasonable
// initial position before any real "fit to data" has run - ongoing camera
// updates as more data arrives are owned exclusively by the debounced
// `applyTimelineDataViewport` watcher below (which does a proper
// multi-point fitBounds, not just a single-point recenter).
//
// If this stayed reactive to `dataBounds`, it would independently re-fire
// VectorMapHost's own `:center` prop watcher (which calls `jumpTo`) every
// time `dataBounds` recomputes - and since timeline data and path data
// resolve as two separate async fetches, that meant the camera would jump
// to an intermediate center, then jump again once the final data arrived,
// on top of (and independent from) the fitBounds watcher's own transition.
const frozenMapCenter = ref(null)
const mapCenter = computed(() => frozenMapCenter.value || props.defaultCenterWhenEmpty)
const mapZoom = ref(13)

const toFiniteMapCoordinate = (value) => {
  const parsed = Number(value)
  return Number.isFinite(parsed) ? parsed : null
}

const isValidMapLatitude = (value) => Number.isFinite(value) && value >= -90 && value <= 90
const isValidMapLongitude = (value) => Number.isFinite(value) && value >= -180 && value <= 180

const normalizeTimelineBoundsPoint = (point) => {
  if (Array.isArray(point) && point.length >= 2) {
    const latitude = toFiniteMapCoordinate(point[0])
    const longitude = toFiniteMapCoordinate(point[1])
    if (isValidMapLatitude(latitude) && isValidMapLongitude(longitude)) {
      return [latitude, longitude]
    }
  }

  if (point && typeof point === 'object') {
    const latitude = toFiniteMapCoordinate(point.latitude ?? point.lat)
    const longitude = toFiniteMapCoordinate(point.longitude ?? point.lng ?? point.lon)
    if (isValidMapLatitude(latitude) && isValidMapLongitude(longitude)) {
      return [latitude, longitude]
    }
  }

  return null
}

const addTimelineBoundsPoint = (bounds, point) => {
  const normalizedPoint = normalizeTimelineBoundsPoint(point)
  if (normalizedPoint) {
    bounds.push(normalizedPoint)
  }
}

const getTimelineBoundsViewport = (bounds) => {
  const points = Array.isArray(bounds)
    ? bounds.map(normalizeTimelineBoundsPoint).filter(Boolean)
    : []

  if (points.length === 0) {
    return null
  }

  let south = Infinity
  let west = Infinity
  let north = -Infinity
  let east = -Infinity

  points.forEach(([latitude, longitude]) => {
    south = Math.min(south, latitude)
    west = Math.min(west, longitude)
    north = Math.max(north, latitude)
    east = Math.max(east, longitude)
  })

  if (![south, west, north, east].every(Number.isFinite)) {
    return null
  }

  const center = [
    (south + north) / 2,
    (west + east) / 2
  ]
  const diagonalMeters = haversineDistanceMetersFromCoordinates(south, west, north, east)
  const isCollapsedBounds = points.length === 1
    || (Number.isFinite(diagonalMeters) && diagonalMeters <= TIMELINE_COLLAPSED_BOUNDS_THRESHOLD_METERS)

  return {
    points,
    center,
    isCollapsedBounds
  }
}

const applyTimelineDataViewport = (bounds, options = {}) => {
  const viewport = getTimelineBoundsViewport(bounds)
  if (!viewport) {
    return
  }

  if (viewport.isCollapsedBounds) {
    mapContainerRef.value?.setView?.(viewport.center, TIMELINE_SINGLE_LOCATION_ZOOM, options)
    return
  }

  mapContainerRef.value?.fitBounds?.(viewport.points, {
    padding: TIMELINE_FIT_BOUNDS_PADDING,
    maxZoom: TIMELINE_FIT_BOUNDS_MAX_ZOOM,
    ...options
  })
}

const hasValidCurrentLocation = computed(() => {
  const latitude = Number(props.currentLocation?.latitude)
  const longitude = Number(props.currentLocation?.longitude)

  return Number.isFinite(latitude) && Number.isFinite(longitude)
})

const canZoomToCurrentLocation = computed(() => (
  props.showCurrentLocation && hasValidCurrentLocation.value
))

const zoomControlTitle = computed(() => (
  canZoomToCurrentLocation.value ? t('maps.timelineMap.zoomControl.currentLocation') : t('maps.timelineMap.zoomControl.data')
))

const zoomControlIcon = computed(() => (
  canZoomToCurrentLocation.value ? 'pi pi-compass' : 'pi pi-arrows-alt'
))

// Controls configuration
const controlsProps = computed(() => ({
  showZoomControls: true,
  zoomControlTitle: zoomControlTitle.value,
  zoomControlIcon: zoomControlIcon.value
}))

// Immich computed properties
// For public views, "configured" means the share allows photos (there's no authenticated
// immichStore session to check), so the map-controls toggle button stays reachable.
const immichConfigured = computed(() => {
  if (props.isPublicView) {
    return shouldShowImmich.value
  }
  return immichStore.isConfigured
})
const immichLoading = computed(() => immichStore.photosLoading || immichStore.configLoading)
const notesLoading = computed(() => notesStore.notesLoading)

// For public views, respect the showPhotos prop; for private views, always allow
const shouldShowImmich = computed(() => {
  if (props.isPublicView) {
    return props.showPhotos
  }
  return true // For non-public views, always allow (controlled by toggle)
})

const photosForLayer = computed(() => (
  Array.isArray(props.photos) ? props.photos : null
))

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

const heatmapGradient = computed(() => mapAppearance.value.heatmapGradient)
const showHeatmapLegend = computed(() => heatmapEnabled.value && heatmapAvailable.value)

const mapEngineMode = computed(() => resolveMapEngineModeFromInstance(map.value, MAP_RENDER_MODES.RASTER))
const isVectorMapMode = computed(() => mapEngineMode.value === MAP_RENDER_MODES.VECTOR)
const show3dBuildingsControl = computed(() => isVectorMapMode.value && mapTilerBuildings3dSupported.value)
const panoramaxControlAvailable = computed(() => !props.isPublicView && props.panoramaxAvailable && Boolean(props.panoramaxEndpoint))

const openPanoramaxViewer = (selection) => {
  panoramaxSelection.value = {
    sequenceId: selection?.sequenceId || null,
    pictureId: selection?.pictureId || null,
    details: [
      selection?.captureTime ? `Captured ${new Date(selection.captureTime).toLocaleString()}` : '',
      Number.isFinite(Number(selection?.heading)) ? `Heading ${Math.round(Number(selection.heading))}°` : ''
    ].filter(Boolean).join(' · ')
  }
  panoramaxViewerVisible.value = true
}
const showHighlightedTripPopup = computed(() => !isMobileTripSelectionViewport.value)
const activeHighlightedTrip = computed(() => {
  if (!activeTimelineHighlight.value || activeTimelineHighlight.value.type !== 'trip') {
    return null
  }

  return activeTimelineHighlight.value
})

const highlightedTripHasMatchedPath = computed(() => (
  activeTimelineHighlight.value?.type === 'trip'
  && props.matchedTripIds.map(Number).includes(Number(activeTimelineHighlight.value.id))
))

const formatTripMovementTitle = (movementType) => {
  const label = movementType && te(`movementTypes.${movementType}`)
    ? t(`movementTypes.${movementType}`)
    : t('maps.popups.timeline.unknownMovement')

  return t('maps.popups.timeline.movementTrip', { movementType: label })
}

// "19:35 → 20:10", or with the end date when the trip ends on another day.
const formatTripTimeRange = (trip) => {
  const startMs = Date.parse(trip?.timestamp)
  if (!Number.isFinite(startMs)) return null

  const start = new Date(startMs).toISOString()
  const end = new Date(startMs + Math.max(Number(trip.tripDuration) || 0, 0) * 1000).toISOString()
  const endText = timezone.isSameDay(start, end)
    ? timezone.formatTime(end)
    : `${timezone.formatDateDisplay(end)} ${timezone.formatTime(end)}`

  return `${timezone.formatTime(start)} → ${endText}`
}

const tripSummary = computed(() => {
  const trip = activeHighlightedTrip.value
  if (!trip) return null

  const averageSpeedKmh = resolveAverageTripSpeedKmh(trip)

  return {
    iconClass: getTripMovementIconClass(trip.movementType),
    title: formatTripMovementTitle(trip.movementType),
    metaItems: [
      formatTripTimeRange(trip),
      formatDuration(Number(trip.tripDuration) || 0),
      formatDistance(Number(trip.distanceMeters) || 0),
      Number.isFinite(averageSpeedKmh) ? formatSpeed(averageSpeedKmh) : null
    ].filter(Boolean)
  }
})

const showTripSummary = computed(() => Boolean(tripSummary.value))
// Car trips are drawn in speed bands; the legend explains them unless the user turned bands off.
const showSpeedLegend = computed(() => (
  mapAppearance.value.speedBandsEnabled
  && String(activeHighlightedTrip.value?.movementType || '').trim().toUpperCase() === 'CAR'
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

const getCrossTypeSources = () => ({
  timeline: timelineLayerRef.value,
  notes: notesLayerRef.value,
  photos: immichLayerRef.value,
  weather: weatherLayerRef.value,
  // The highlighted trip's start/end markers: nothing may hide under them.
  getFocusObstacles: () => pathLayerRef.value?.getHighlightedEndpointObstacles?.() ?? []
})
const requestCrossTypeCompute = () => crossTypeLayerRef.value?.requestCompute?.()
const handleCrossTypeNotesSelect = (notes) => notesLayerRef.value?.openNotes?.(notes)

const hideTimelineMarkersForReplay = computed(() => showTripReplayBar.value && isReplayPlaying.value)
const timelineLayerVisible = computed(() => showTimeline.value && !hideTimelineMarkersForReplay.value)

watch([timelineLayerVisible, showImmich, showNotesLayer, activeTimelineHighlight], () => requestCrossTypeCompute())

const showReadOnlyToast = () => {
  showDemoModeToast(toast)
}

// Context menu items
const mapMenuItems = computed(() => {
  const items = [
    {
      label: t('maps.timelineMap.contextMenu.wasIHere'),
      icon: 'pi pi-clock',
      command: () => openLocationLookup(dialogState.value.addToFavoritesLatLng)
    }
  ]

  if (props.showPlanToVisitAction) {
    items.push({
      label: t('maps.timelineMap.contextMenu.planToVisitHere'),
      icon: 'pi pi-map-marker',
      disabled: props.readOnly,
      command: () => {
        if (props.readOnly) {
          showReadOnlyToast()
          return
        }
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
        label: t('maps.timelineMap.contextMenu.addToFavorites'),
        icon: 'pi pi-star',
        disabled: props.readOnly,
        command: () => {
          if (props.readOnly) {
            showReadOnlyToast()
            return
          }
          dialogState.value.addToFavoritesVisible = true
        }
      },
      {
        label: t('maps.timelineMap.contextMenu.addAreaToFavorites'),
        icon: 'pi pi-star',
        disabled: props.readOnly,
        command: () => {
          if (props.readOnly) {
            showReadOnlyToast()
            return
          }
          startDrawing()
        }
      }
    )
  }

  return items
})

// Favorite context menu items
const favoriteMenuItems = computed(() => [
  {
    label: t('maps.timelineMap.contextMenu.viewAllVisits'),
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
    label: t('maps.timelineMap.contextMenu.edit'),
    icon: 'pi pi-pencil',
    disabled: props.readOnly,
    command: () => {
      if (props.readOnly) {
        showReadOnlyToast()
        return
      }
      if (dialogState.value.selectedFavorite) {
        handleFavoriteEdit(dialogState.value.selectedFavorite)
      }
    }
  },
  {
    label: t('maps.timelineMap.contextMenu.delete'),
    icon: 'pi pi-trash',
    disabled: props.readOnly,
    command: () => {
      if (props.readOnly) {
        showReadOnlyToast()
        return
      }
      if (dialogState.value.selectedFavorite) {
        handleFavoriteDelete(dialogState.value.selectedFavorite)
      }
    }
  }
])

const stayMenuItems = computed(() => [
  {
    label: t('maps.timelineMap.contextMenu.viewAllVisits'),
    icon: 'pi pi-chart-line',
    command: () => {
      if (dialogState.value.selectedStay) {
        navigateToStayDetails(dialogState.value.selectedStay)
      }
    }
  }
])

const plannedItemMenuItems = computed(() => [
  {
    label: t('maps.timelineMap.contextMenu.editPlannedItem'),
    icon: 'pi pi-pencil',
    command: () => {
      if (dialogState.value.selectedPlannedItem) {
        emit('plan-item-edit', dialogState.value.selectedPlannedItem)
      }
    }
  },
  {
    label: t('maps.timelineMap.contextMenu.deletePlannedItem'),
    icon: 'pi pi-trash',
    command: () => {
      if (dialogState.value.selectedPlannedItem) {
        emit('plan-item-delete', dialogState.value.selectedPlannedItem)
      }
    }
  }
])

// Map event handlers
const detachMapTilerBuildings3dListener = () => {
  if (mapTilerBuildings3dListenerMap && mapTilerBuildings3dStyleListener) {
    mapTilerBuildings3dListenerMap.off?.('style.load', mapTilerBuildings3dStyleListener)
  }
  mapTilerBuildings3dListenerMap = null
  mapTilerBuildings3dStyleListener = null
}

const syncMapTilerBuildings3dSupport = (mapInstance) => {
  const supported = supportsMapTilerBuildings3d(mapInstance)
  mapTilerBuildings3dSupported.value = supported
  if (!supported) {
    const wasEnabled = buildings3dEnabled.value
    buildings3dEnabled.value = false
    if (wasEnabled) {
      mapInstance?.easeTo?.({ pitch: 0, duration: 300, essential: true })
    }
    return
  }
  if (buildings3dEnabled.value) {
    if (!setMapTilerBuildings3dEnabled(mapInstance, true)) {
      mapTilerBuildings3dSupported.value = false
      buildings3dEnabled.value = false
      return
    }
    mapInstance?.easeTo?.({ pitch: 45, duration: 300, essential: true })
  }
}

const handleToggle3dBuildings = (enabled) => {
  if (!map.value || !mapTilerBuildings3dSupported.value) return

  if (!setMapTilerBuildings3dEnabled(map.value, enabled)) {
    mapTilerBuildings3dSupported.value = false
    buildings3dEnabled.value = false
    return
  }

  buildings3dEnabled.value = enabled
  map.value.easeTo?.({ pitch: enabled ? 45 : 0, duration: 300, essential: true })
}

const handleMapReady = (mapInstance) => {
  detachMapTilerBuildings3dListener()
  buildings3dEnabled.value = props.enable3dBuildingsByDefault
  map.value = mapInstance ? markRaw(mapInstance) : null
  syncMapTilerBuildings3dSupport(mapInstance)
  if (mapInstance?.on) {
    mapTilerBuildings3dListenerMap = mapInstance
    mapTilerBuildings3dStyleListener = () => {
      if (map.value === mapInstance) {
        syncMapTilerBuildings3dSupport(mapInstance)
      }
    }
    mapInstance.on('style.load', mapTilerBuildings3dStyleListener)
  }
  
  // Initialize rectangle drawing
  initializeDrawing(mapInstance)
  initAreaDrawControl()
  
  // Fit map to data if available
  if (hasAnyData.value && dataBounds.value) {
    lastBoundsString = JSON.stringify(dataBounds.value)
    nextTick(() => {
      applyTimelineDataViewport(dataBounds.value)
    })
  }
}

watch(() => props.enable3dBuildingsByDefault, (enabled) => {
  if (map.value && mapTilerBuildings3dSupported.value) {
    handleToggle3dBuildings(enabled)
  }
})

const syncMobileTripSelectionViewport = () => {
  if (typeof window === 'undefined' || typeof window.matchMedia !== 'function') {
    isMobileTripSelectionViewport.value = false
    return
  }

  isMobileTripSelectionViewport.value = window.matchMedia(MOBILE_TRIP_SELECTION_MEDIA).matches
}

const openLocationLookup = async (point) => {
  const latitude = Number(point?.lat)
  const longitude = Number(point?.lng)
  if (!Number.isFinite(latitude) || !Number.isFinite(longitude)) {
    return
  }

  const requestId = ++locationLookupRequestId
  dialogState.value.locationLookupPoint = { lat: latitude, lng: longitude }
  dialogState.value.locationLookupResult = null
  dialogState.value.locationLookupError = ''
  dialogState.value.locationLookupLoading = true
  dialogState.value.locationLookupVisible = true

  try {
    const response = await timelineStore.lookupLocation(latitude, longitude)
    if (requestId !== locationLookupRequestId) return
    dialogState.value.locationLookupResult = response || null
  } catch (error) {
    if (requestId !== locationLookupRequestId) return
    dialogState.value.locationLookupError = formatApiErrorDetail(
      error,
      'Could not check visits at this location.'
    )
  } finally {
    if (requestId === locationLookupRequestId) {
      dialogState.value.locationLookupLoading = false
    }
  }
}

const retryLocationLookup = () => {
  openLocationLookup(dialogState.value.locationLookupPoint)
}

const closeLocationLookup = () => {
  locationLookupRequestId += 1
  dialogState.value.locationLookupVisible = false
  dialogState.value.locationLookupLoading = false
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
  // contextmenu handlers (favorites/planned items/stays) to set suppression flags.
  mapContextMenuShowTimeoutId = setTimeout(() => {
    mapContextMenuShowTimeoutId = null

    if (featureContextMenuActive.value) {
      featureContextMenuActive.value = false
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

const handleTimelineMarkerContextMenu = (event) => {
  if (props.isPublicView) {
    return
  }

  const stay = event?.timelineItem
  if (!getStayPlaceDetailsRoute(stay)) {
    return
  }

  if (mapContextMenuShowTimeoutId !== null) {
    clearTimeout(mapContextMenuShowTimeoutId)
    mapContextMenuShowTimeoutId = null
  }

  featureContextMenuActive.value = true

  if (event.event) {
    event.event.preventDefault?.()
    event.event.stopPropagation?.()
    event.event.stopImmediatePropagation?.()
  }

  dialogState.value.selectedStay = event

  mapContextMenuRef.value?.hide?.()
  favoriteContextMenuRef.value?.hide?.()
  plannedItemContextMenuRef.value?.hide?.()

  if (stayContextMenuRef.value && event.event) {
    stayContextMenuRef.value.show(event.event)
    setTimeout(() => {
      featureContextMenuActive.value = false
    }, 120)
  }
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

  let title = t('maps.timelineMap.immichError.genericTitle')
  let detail = t('maps.timelineMap.immichError.genericDetail')

  // Customize error messages based on error type
  switch (event.type) {
    case 'fetch':
      title = t('maps.timelineMap.immichError.fetchTitle')
      detail = event.message || t('maps.timelineMap.immichError.fetchDetailFallback')
      break
    case 'refresh':
      title = t('maps.timelineMap.immichError.refreshTitle')
      detail = event.message || t('maps.timelineMap.immichError.refreshDetailFallback')
      break
    case 'config':
      title = t('maps.timelineMap.immichError.configTitle')
      detail = event.message || t('maps.timelineMap.immichError.configDetailFallback')
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
  let title = t('maps.timelineMap.notesError.genericTitle')
  let detail = t('maps.timelineMap.notesError.genericDetail')

  switch (event.type) {
    case 'fetch':
      title = t('maps.timelineMap.notesError.fetchTitle')
      detail = event.message || t('maps.timelineMap.notesError.fetchDetailFallback')
      break
    case 'refresh':
      title = t('maps.timelineMap.notesError.refreshTitle')
      detail = event.message || t('maps.timelineMap.notesError.refreshDetailFallback')
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
  if (props.readOnly) {
    showReadOnlyToast()
    return
  }
  const favorite = event.favorite || event
  emit('edit-favorite', favorite)
}

const handleFavoriteDelete = (event) => {
  if (props.readOnly) {
    showReadOnlyToast()
    return
  }
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
  featureContextMenuActive.value = true

  // Prevent default browser context menu and map context menu
  if (event.event) {
    event.event.preventDefault()
    event.event.stopPropagation()
    event.event.stopImmediatePropagation()
  }

  // Store the selected favorite for context menu actions
  dialogState.value.selectedFavorite = event

  mapContextMenuRef.value?.hide?.()
  stayContextMenuRef.value?.hide?.()
  plannedItemContextMenuRef.value?.hide?.()

  // Show favorite context menu
  if (favoriteContextMenuRef.value && event.event) {
    favoriteContextMenuRef.value.show(event.event)
    setTimeout(() => {
      featureContextMenuActive.value = false
    }, 120)
  }
}

const handlePlannedItemContextMenu = (event) => {
  if (mapContextMenuShowTimeoutId !== null) {
    clearTimeout(mapContextMenuShowTimeoutId)
    mapContextMenuShowTimeoutId = null
  }

  // Set flag to prevent map context menu
  featureContextMenuActive.value = true

  if (event.event) {
    event.event.preventDefault()
    event.event.stopPropagation()
    event.event.stopImmediatePropagation()
  }

  dialogState.value.selectedPlannedItem = event

  mapContextMenuRef.value?.hide?.()
  favoriteContextMenuRef.value?.hide?.()
  stayContextMenuRef.value?.hide?.()

  if (plannedItemContextMenuRef.value && event.event) {
    plannedItemContextMenuRef.value.show(event.event)
    setTimeout(() => {
      featureContextMenuActive.value = false
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

const navigateToStayDetails = (event) => {
  const route = getStayPlaceDetailsRoute(event?.timelineItem || event)
  if (route) {
    router.push(route)
  }
}

const handleZoomToData = () => {
  if (!map.value) {
    return
  }

  if (canZoomToCurrentLocation.value) {
    const latitude = Number(props.currentLocation.latitude)
    const longitude = Number(props.currentLocation.longitude)
    mapContainerRef.value?.setView?.([latitude, longitude], 15, { animate: true })
    return
  }

  if (dataBounds.value) {
    applyTimelineDataViewport(dataBounds.value)
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
  if (props.readOnly) {
    closeAddFavoritePoint()
    showReadOnlyToast()
    return
  }

  const pointLatLng = dialogState.value.addToFavoritesLatLng

  if (!pointLatLng) {
    toast.add({
      severity: 'error',
      summary: t('common.error'),
      detail: t('maps.timelineMap.addFavoriteFailed'),
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
  if (props.readOnly) {
    closeAddFavoriteArea()
    showReadOnlyToast()
    return
  }

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
    const data = await technicalDataStore.fetchRawMapPoints({
      startTime,
      endTime,
      limit: 10000
    })
    if (requestId !== rawGpsPointsRequestId) return

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



// Computed data from stores and props
const pathDataToSegments = (pathData) => {
  // A deliberate null means this layer has no remaining raw fallback segments.
  // Falling back to the store would redraw raw geometry after the atomic swap.
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
}

const processedTimelineData = computed(() => {
  return props.timelineData || timelineStore.timelineData || []
})

// On vector maps a stay's weather is a badge on the stay marker (its samples sit
// exactly on the stay), so only the remaining trip samples get their own markers.
const stayWeatherPartition = computed(() => {
  if (!isVectorMapMode.value || !showWeather.value || props.isPublicView) {
    return null
  }
  return partitionWeatherSamplesByStay(processedTimelineData.value, props.weatherSamples)
})
const stayWeatherByTimelineIndex = computed(() => stayWeatherPartition.value?.weatherByTimelineIndex ?? null)
const weatherLayerSamples = computed(() => stayWeatherPartition.value?.remainingSamples ?? props.weatherSamples)

const {
  routeDisplayMode,
  routeDisplayModeControlAvailable,
  routeDisplayModeUsesRawPath,
  routeDisplayModeUsesComparison,
  rawComparisonPathData,
  cycleRouteDisplayMode
} = useMapMatchingComparison({
  rawPathData: computed(() => props.rawPathData),
  timelineData: processedTimelineData,
  highlightedTrip: activeHighlightedTrip,
  highlightedTripHasMatchedPath,
  matchedTripIds: computed(() => props.matchedTripIds)
})

const processedPathData = computed(() => pathDataToSegments(
  routeDisplayModeUsesRawPath.value ? props.rawPathData : props.pathData
))

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
          addTimelineBoundsPoint(bounds, point)
        })
      }
    })
  }
  
  // Add timeline data bounds
  if (processedTimelineData.value.length > 0) {
    processedTimelineData.value.forEach(item => {
      addTimelineBoundsPoint(bounds, item)
    })
  }
  
  // Add favorites data bounds
  if (processedFavoritesData.value.length > 0) {
    processedFavoritesData.value.forEach(favorite => {
      if (favorite.type === 'point') {
        addTimelineBoundsPoint(bounds, favorite)
      } else if (favorite.type === 'area' && favorite.coordinates) {
        favorite.coordinates.forEach(coord => {
          addTimelineBoundsPoint(bounds, coord)
        })
      }
    })
  }

  // Add planned items bounds
  if (processedPlannedItemsData.value.length > 0) {
    processedPlannedItemsData.value.forEach(item => {
      addTimelineBoundsPoint(bounds, item)
    })
  }
  
  return bounds.length > 0 ? bounds : null
})

// Sets the one-time initial map center (see `frozenMapCenter` declaration above).
watch(dataBounds, (newBounds) => {
  if (!frozenMapCenter.value && newBounds && newBounds.length > 0) {
    frozenMapCenter.value = newBounds[0]
  }
}, { immediate: true })

// Watch for data bounds changes and update map view
let lastBoundsString = ''
let pendingFitTimeoutId = null
watch(dataBounds, (newBounds) => {
  if (map.value && newBounds && hasAnyData.value) {
    const boundsString = JSON.stringify(newBounds)
    if (boundsString !== lastBoundsString) {
      const shouldFitBounds = !lastBoundsString || !props.preserveViewportOnDataRefresh
      lastBoundsString = boundsString
      if (!shouldFitBounds) return
      nextTick(() => {
        // Debounced: cancel any fit still pending from an earlier bounds
        // change so staggered data arrival (e.g. timeline data and path
        // data resolving as two separate async fetches) coalesces into a
        // single fit using the latest bounds, instead of the camera
        // visibly jumping to an intermediate view and then again to the
        // final one.
        if (pendingFitTimeoutId !== null) {
          clearTimeout(pendingFitTimeoutId)
        }

        pendingFitTimeoutId = setTimeout(() => {
          pendingFitTimeoutId = null
          if (map.value) {
            applyTimelineDataViewport(newBounds, {
              animate: false // Disable animation to prevent tile issues
            })
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
  detachMapTilerBuildings3dListener()
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

const setView = (center, zoom = map.value?.getZoom?.(), options = {}) => {
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
  background-color: var(--gp-surface-muted, #f8fafc);
  flex: 1;
  display: flex;
  flex-direction: column;
}

.timeline-map-control-stack {
  position: absolute;
  top: calc(var(--gp-spacing-lg, 1rem) + env(safe-area-inset-top));
  right: calc(var(--gp-spacing-lg, 1rem) + env(safe-area-inset-right));
  z-index: 900;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: var(--gp-spacing-sm, 0.5rem);
  pointer-events: none;
}

.timeline-map-control-stack > * {
  pointer-events: auto;
}

.timeline-viewer-location-control {
  position: relative !important;
  top: auto !important;
  right: auto !important;
  z-index: auto;
}

.map-matching-status {
  position: absolute;
  top: calc(var(--gp-spacing-lg, 1rem) + env(safe-area-inset-top));
  left: calc(var(--gp-spacing-lg, 1rem) + env(safe-area-inset-left));
  z-index: 905;
  display: inline-flex;
  align-items: center;
  gap: 0.45rem;
  max-width: min(18rem, calc(100% - 7rem));
  padding: 0.5rem 0.65rem;
  border: 1px solid var(--gp-border-medium);
  border-radius: 999px;
  background: color-mix(in srgb, var(--gp-surface-card) 94%, transparent);
  color: var(--gp-text-primary);
  box-shadow: 0 8px 24px rgba(15, 23, 42, 0.16);
  font-size: 0.82rem;
  font-weight: 700;
  pointer-events: none;
}

/* Both overlays below were written against the light theme only, so on a dark map they
   showed up as white pills with dark text. */

.map-matching-status-icon {
  animation: mapMatchingPulse 1.4s ease-in-out infinite;
  color: #2563eb;
}

@keyframes mapMatchingPulse {
  0%, 100% { opacity: 0.45; transform: rotate(0deg); }
  50% { opacity: 1; transform: rotate(12deg); }
}

/* Bottom-left, clear of the attribution (bottom-right) and the docked trip summary (bottom-centre). */
.heatmap-legend {
  position: absolute;
  left: calc(0.75rem + env(safe-area-inset-left));
  bottom: calc(2.35rem + env(safe-area-inset-bottom));
  z-index: 905;
  padding: 0.35rem 0.6rem;
  border: 1px solid var(--gp-border-medium);
  border-radius: 999px;
  background: color-mix(in srgb, var(--gp-surface-card) 94%, transparent);
  color: var(--gp-text-primary);
  box-shadow: 0 8px 24px rgba(15, 23, 42, 0.16);
  pointer-events: none;
}

.trip-summary-legend {
  margin-top: 0.3rem;
}

/* Desktop: docked bottom-centre, where the replay bar also lives. */
.trip-summary {
  position: absolute;
  left: 50%;
  bottom: calc(2.35rem + env(safe-area-inset-bottom));
  transform: translateX(-50%);
  z-index: 940;
  width: min(30rem, calc(100% - 2rem));
  display: flex;
  align-items: center;
  gap: 0.65rem;
  padding: 0.55rem 0.6rem;
  /* Same card as the replay bar it stands in for (TripReplayControls .trip-replay-bar). */
  border: 1px solid var(--gp-border-medium);
  border-radius: 0.75rem;
  background: color-mix(in srgb, var(--gp-surface-card) 95%, transparent);
  color: var(--gp-text-primary);
  box-shadow: var(--gp-shadow-large);
  backdrop-filter: blur(4px);
  pointer-events: auto;
}

.trip-summary-icon {
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

.trip-summary-content {
  flex: 1 1 auto;
  min-width: 0;
}

.trip-summary-title {
  overflow: hidden;
  color: var(--gp-text-primary);
  font-size: 0.88rem;
  font-weight: 700;
  line-height: 1.2;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.trip-summary-meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 0.38rem;
  overflow: visible;
  color: var(--gp-text-secondary);
  font-size: 0.76rem;
  font-weight: 600;
  line-height: 1.2;
  white-space: normal;
}

.trip-summary-hint {
  margin-top: 0.15rem;
  overflow: hidden;
  color: var(--gp-text-muted);
  font-size: 0.72rem;
  font-weight: 500;
  line-height: 1.2;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.trip-summary-dot {
  flex: 0 0 4px;
  width: 4px;
  height: 4px;
  border-radius: 999px;
  background: var(--gp-text-muted);
}

.trip-summary-close {
  flex: 0 0 2rem;
  width: 2rem;
  height: 2rem;
  border: 1px solid var(--gp-border-medium);
  border-radius: 999px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--gp-surface-muted);
  color: var(--gp-text-secondary);
  cursor: pointer;
  transition: background 0.15s ease, color 0.15s ease, border-color 0.15s ease;
}

.trip-summary-replay {
  flex: 0 0 auto;
  height: 2rem;
  padding: 0 0.7rem;
  border: 1px solid var(--gp-border-medium);
  border-radius: 999px;
  display: flex;
  align-items: center;
  gap: 0.35rem;
  background: var(--gp-surface-muted);
  color: var(--gp-text-primary);
  font-size: 0.76rem;
  font-weight: 700;
  cursor: pointer;
  transition: background 0.15s ease, color 0.15s ease, border-color 0.15s ease;
}

.trip-summary-replay:hover,
.trip-summary-replay:focus-visible,
.trip-summary-close:hover,
.trip-summary-close:focus-visible {
  border-color: var(--gp-primary-light, #60a5fa);
  background: var(--gp-primary-soft);
  color: var(--gp-primary-text);
}

/* Responsive adjustments */
@media (max-width: 768px), (max-height: 520px) and (pointer: coarse) {
  .timeline-map-control-stack {
    top: calc(var(--gp-spacing-md, 0.75rem) + env(safe-area-inset-top));
    right: calc(var(--gp-spacing-md, 0.75rem) + env(safe-area-inset-right));
    align-items: flex-end;
  }

  .map-view-container {
    width: 100%;
    height: 100%;
    min-height: 300px;
  }

  .map-view-container :global(.leaflet-bottom),
  .map-view-container :global(.maplibregl-ctrl-bottom-left),
  .map-view-container :global(.maplibregl-ctrl-bottom-right) {
    bottom: calc(var(--timeline-mobile-sheet-height, 44px) + env(safe-area-inset-bottom)) !important;
    z-index: 880 !important;
    pointer-events: none !important;
  }

  .map-view-container :global(.leaflet-bottom.leaflet-left),
  .map-view-container :global(.maplibregl-ctrl-bottom-left) {
    left: calc(0.5rem + env(safe-area-inset-left)) !important;
  }

  .map-view-container :global(.leaflet-bottom.leaflet-right),
  .map-view-container :global(.maplibregl-ctrl-bottom-right) {
    right: calc(0.5rem + env(safe-area-inset-right)) !important;
  }

  .map-view-container :global(.leaflet-control-attribution),
  .map-view-container :global(.maplibregl-ctrl-attrib) {
    max-width: calc(100vw - 1rem - env(safe-area-inset-left) - env(safe-area-inset-right));
    box-sizing: border-box;
    pointer-events: auto !important;
  }

  .map-view-container :global(.maplibregl-ctrl-attrib) {
    margin-bottom: 0 !important;
    font-size: 9px !important;
    white-space: nowrap;
  }

  .trip-summary {
    bottom: calc(var(--timeline-mobile-sheet-height, 44px) + 3.25rem + env(safe-area-inset-bottom));
    width: calc(100% - 1rem - env(safe-area-inset-left) - env(safe-area-inset-right));
    max-width: 22rem;
  }
}
</style>
