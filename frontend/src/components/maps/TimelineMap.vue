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
          @path-click="handlePathClick"
          @trip-marker-click="handleTripMarkerClick"
          @highlighted-trip-click="handleHighlightedTripClick"
        />
        

        <!-- Timeline Layer -->
        <TimelineLayer
          v-if="map && isReady"
          ref="timelineLayerRef"
          :map="map"
          :timeline-data="processedTimelineData"
          :highlighted-item="activeTimelineHighlight"
          :visible="showTimeline"
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

// Map components
import {FavoritesLayer, HeatmapLayer, MapContainer, MapControls, PathLayer, TimelineLayer, CurrentLocationLayer, ImmichLayer, TripPlanLayer} from '@/components/maps'

import PhotoViewerDialog from '@/components/dialogs/PhotoViewerDialog.vue'
import TimelineRegenerationModal from '@/components/dialogs/TimelineRegenerationModal.vue'
import {useMapHighlights, useMapInteractions, useMapLayers, useRectangleDrawing} from '@/composables'

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
} = useRectangleDrawing({
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

  // Don't show map context menu if favorite context menu is active
  if (favoriteContextMenuActive.value) {
    favoriteContextMenuActive.value = false
    return
  }

  // Prevent default browser context menu
  if (event.originalEvent) {
    event.originalEvent.preventDefault()
    event.originalEvent.stopPropagation()
  }

  dialogState.value.addToFavoritesLatLng = event.latlng
  baseHandleMapContextMenu(event)

  // Show PrimeVue context menu
  if (mapContextMenuRef.value && event.originalEvent) {
    mapContextMenuRef.value.show(event.originalEvent)
  }
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

  // Show favorite context menu
  if (favoriteContextMenuRef.value && event.event) {
    // Use setTimeout to ensure the event has fully propagated/stopped
    setTimeout(() => {
      favoriteContextMenuRef.value.show(event.event)
      // Reset the flag after a short delay
      setTimeout(() => {
        favoriteContextMenuActive.value = false
      }, 100)
    }, 10)
  }
}

const handlePlannedItemContextMenu = (event) => {
  // Set flag to prevent map context menu
  favoriteContextMenuActive.value = true

  if (event.event) {
    event.event.preventDefault()
    event.event.stopPropagation()
    event.event.stopImmediatePropagation()
  }

  dialogState.value.selectedPlannedItem = event

  if (plannedItemContextMenuRef.value && event.event) {
    setTimeout(() => {
      plannedItemContextMenuRef.value.show(event.event)
      setTimeout(() => {
        favoriteContextMenuActive.value = false
      }, 100)
    }, 10)
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
}
</style>
