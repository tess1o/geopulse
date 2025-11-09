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
          :immich-configured="immichConfigured"
          :immich-loading="immichLoading"
          @toggle-favorites="toggleFavorites"
          @toggle-timeline="toggleTimeline"
          @toggle-path="togglePath"
          @toggle-immich="toggleImmich"
          @zoom-to-data="handleZoomToData"
          class="map-controls"
        />
      </template>

      <!-- Map Layers -->
      <template #overlays="{ map, isReady }">
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

        <!-- Immich Photos Layer -->
        <ImmichLayer
          v-if="map && isReady"
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

        <!-- Edit Favorite Dialog -->
        <EditFavoriteDialog
          v-if="editFavoriteDialogVisible"
          :visible="editFavoriteDialogVisible"
          :header="'Edit Favorite'"
          :favoriteLocation="selectedFavoriteLocation"
          @edit-favorite="onEditFavoriteLocationSubmit"
          @close="closeEditFavorite"
          @delete-favorite="deleteFavoriteLocation"
        />

        <!-- Photo Viewer Dialog -->
        <PhotoViewerDialog
          v-model:visible="photoViewerVisible"
          :photos="photoViewerPhotos"
          :initial-photo-index="photoViewerIndex"
          @close="closePhotoViewer"
        />

        <!-- Timeline Regeneration Modal -->
        <TimelineRegenerationModal
          v-model:visible="timelineRegenerationVisible"
          :type="timelineRegenerationType"
          :job-id="currentJobId"
        />
      </template>
    </MapContainer>
  </div>
</template>

<script setup>
import {computed, nextTick, onMounted, readonly, ref, watch} from 'vue'
import {useConfirm} from "primevue/useconfirm"
import {useToast} from "primevue/usetoast"
import ContextMenu from 'primevue/contextmenu'
import ConfirmDialog from 'primevue/confirmdialog'

// Map components
import {FavoritesLayer, MapContainer, MapControls, PathLayer, TimelineLayer, CurrentLocationLayer, ImmichLayer} from '@/components/maps'

// Dialog components
import AddFavoriteDialog from '@/components/dialogs/AddFavoriteDialog.vue'
import EditFavoriteDialog from '@/components/dialogs/EditFavoriteDialog.vue'
import PhotoViewerDialog from '@/components/dialogs/PhotoViewerDialog.vue'
import TimelineRegenerationModal from '@/components/dialogs/TimelineRegenerationModal.vue'

// Composables
import {useMapHighlights, useMapInteractions, useMapLayers, useRectangleDrawing} from '@/composables'

// Store imports
import {useHighlightStore} from '@/stores/highlight'
import {useFavoritesStore} from '@/stores/favorites'
import {useLocationStore} from '@/stores/location'
import {useTimelineStore} from '@/stores/timeline'
import {useImmichStore} from '@/stores/immich'


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
  currentLocation: {
    type: Object,
    default: () => null
  },
  showCurrentLocation: {
    type: Boolean,
    default: false
  }
})

// Emits
const emit = defineEmits([
  'add-point-with-regeneration',
  'add-area-with-regeneration',
  'edit-favorite',
  'delete-favorite-with-regeneration',
  'highlighted-path-click',
  'timeline-marker-click'
])

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

// Store instances
const favoritesStore = useFavoritesStore()
const locationStore = useLocationStore()
const timelineStore = useTimelineStore()
const immichStore = useImmichStore()

const {
  handleTimelineMarkerClick: baseHandleTimelineMarkerClick,
  handlePathClick: baseHandlePathClick,
  handleFriendClick: baseHandleFriendClick,
  handleFavoriteClick: baseHandleFavoriteClick,
  handleMapClick: baseHandleMapClick,
  handleMapContextMenu: baseHandleMapContextMenu
} = useMapInteractions({
  onTimelineMarkerClick: (event) => emit('timeline-marker-click', event),
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
const immichLayerRef = ref(null)
const mapContextMenuRef = ref(null)
const favoriteContextMenuRef = ref(null)

const confirm = useConfirm()
const toast = useToast()

// Local state
const map = ref(null)
const favoriteContextMenuActive = ref(false)

// Dialog state
const dialogState = ref({
  addToFavoritesVisible: false,
  addAreaVisible: false,
  editFavoriteVisible: false,
  selectedFavorite: null,
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

// Photo viewer state
const photoViewerVisible = ref(false)
const photoViewerPhotos = ref([])
const photoViewerIndex = ref(0)

// Timeline regeneration modal state
const timelineRegenerationVisible = ref(false)
const timelineRegenerationType = ref('general')
const modalShowStartTime = ref(null)
const currentJobId = ref(null)

// Computed getters for dialog state (for template compatibility)
const addToFavoritesDialogVisible = computed(() => dialogState.value.addToFavoritesVisible)
const addAreaShowDialog = computed(() => dialogState.value.addAreaVisible)
const editFavoriteDialogVisible = computed(() => dialogState.value.editFavoriteVisible)
const selectedFavoriteLocation = computed(() => dialogState.value.selectedFavorite)

// Map configuration
const mapCenter = ref([51.505, -0.09])
const mapZoom = ref(13)

// Controls configuration
const controlsProps = computed(() => ({
  showZoomControls: true
}))

// Immich computed properties
const immichConfigured = computed(() => immichStore.isConfigured)
const immichLoading = computed(() => immichStore.photosLoading || immichStore.configLoading)

// Context menu items
const mapMenuItems = ref([
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
])

// Favorite context menu items
const favoriteMenuItems = ref([
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

// Map event handlers
const handleMapReady = (mapInstance) => {
  map.value = mapInstance
  
  // Initialize rectangle drawing
  initializeDrawing(mapInstance)
  initAreaDrawControl()
  
  // Fit map to data if available
  if (hasAnyData.value && dataBounds.value) {
    nextTick(() => {
      if (dataBounds.value.length === 1) {
        // Single point - center on it with a reasonable zoom
        mapInstance.setView(dataBounds.value[0], 14)
      } else if (dataBounds.value.length > 1) {
        // Multiple points - fit bounds
        mapInstance.fitBounds(dataBounds.value, { padding: [20, 20] })
      }
    })
  }
}

const handleMapClick = (event) => {
  dialogState.value.addToFavoritesLatLng = event.latlng
  baseHandleMapClick(event)
  
  // Clear all highlights when clicking on empty map
  clearAllMapHighlights()
}

const handleMapContextMenu = (event) => {
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
  
  // Check if this item is already highlighted - if so, clear it
  if (highlightStore.isItemHighlighted(event.timelineItem)) {
    clearAllMapHighlights()
  } else {
    // Use simplified highlighting (store only)
    highlightTimelineItem(event.timelineItem)
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

// const handleFriendClick = (event) => {
//   baseHandleFriendClick(event)
// }


const handleFavoriteClick = (event) => {
  baseHandleFavoriteClick(event)
}

// Immich layer event handlers
const handlePhotoClick = (event) => {
  // Always open PhotoViewerDialog for consistent experience
  photoViewerPhotos.value = event.photos || []
  photoViewerIndex.value = event.initialIndex || 0
  photoViewerVisible.value = true
}

const handlePhotoHover = (event) => {
  // Could show preview tooltip in the future
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
  dialogState.value.selectedFavorite = event.favorite || event
  dialogState.value.editFavoriteVisible = true
}

const handleFavoriteDelete = (event) => {
  const favorite = event.favorite || event
  // Confirm deletion
  confirm.require({
    message: 'Are you sure you want to delete this favorite location? This will also regenerate your timeline data.',
    header: 'Delete Favorite',
    icon: 'pi pi-exclamation-triangle',
    accept: () => {
      // Show timeline regeneration modal with timing
      showTimelineRegenerationModal('favorite-delete')
      
      // Emit the event with modal context
      emit('delete-favorite-with-regeneration', {
        favorite,
        onComplete: () => {
          closeTimelineRegenerationModal()
          toggleFavorites(true)
        },
        onError: () => {
          closeTimelineRegenerationModal()
        },
        onJobCreated: (jobId) => {
          currentJobId.value = jobId
        }
      })
    }
  })
}

const handleFavoriteContextMenu = (event) => {
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

const handleZoomToData = () => {
  if (map.value && dataBounds.value) {
    if (dataBounds.value.length === 1) {
      // Single point - center on it with a reasonable zoom
      map.value.setView(dataBounds.value[0], 14)
    } else if (dataBounds.value.length > 1) {
      // Multiple points - fit bounds
      map.value.fitBounds(dataBounds.value, { padding: [20, 20] })
    }
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
  const newFavorite = {
    name: favoriteData,
    lat: dialogState.value.addToFavoritesLatLng.lat,
    lon: dialogState.value.addToFavoritesLatLng.lng,
    type: 'point'
  }
  
  // Close the add favorite dialog
  closeAddFavoritePoint()
  
  // Show timeline regeneration modal with timing
  showTimelineRegenerationModal('favorite')
  
  // Emit the event with modal context
  emit('add-point-with-regeneration', {
    favorite: newFavorite,
    onComplete: () => {
      closeTimelineRegenerationModal()
      toggleFavorites(true)
    },
    onError: () => {
      closeTimelineRegenerationModal()
    },
    onJobCreated: (jobId) => {
      currentJobId.value = jobId
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
  
  // Show timeline regeneration modal with timing
  showTimelineRegenerationModal('favorite')
  
  // Emit the event with modal context
  emit('add-area-with-regeneration', {
    favorite: newFavorite,
    onComplete: () => {
      closeTimelineRegenerationModal()
      toggleFavorites(true)
    },
    onError: () => {
      closeTimelineRegenerationModal()
    },
    onJobCreated: (jobId) => {
      currentJobId.value = jobId
    }
  })
}

const onEditFavoriteLocationSubmit = (updatedData) => {
  emit('edit-favorite', updatedData)
  closeEditFavorite()
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

const closeEditFavorite = () => {
  dialogState.value.editFavoriteVisible = false
  dialogState.value.selectedFavorite = null
}

const deleteFavoriteLocation = (favorite) => {
  // Close edit dialog first
  closeEditFavorite()
  // Use the same delete handling logic
  handleFavoriteDelete({ favorite })
}

// Photo viewer handlers
const closePhotoViewer = () => {
  photoViewerVisible.value = false
  photoViewerPhotos.value = []
  photoViewerIndex.value = 0
}

// Timeline regeneration modal timing helpers
const showTimelineRegenerationModal = (type) => {
  timelineRegenerationType.value = type
  modalShowStartTime.value = Date.now()
  timelineRegenerationVisible.value = true
}

const closeTimelineRegenerationModal = () => {
  if (!modalShowStartTime.value) {
    timelineRegenerationVisible.value = false
    return
  }
  
  const elapsed = Date.now() - modalShowStartTime.value
  const minimumDisplayTime = 3000 // 3 seconds
  const remainingTime = Math.max(0, minimumDisplayTime - elapsed)
  
  setTimeout(() => {
    timelineRegenerationVisible.value = false
    modalShowStartTime.value = null
  }, remainingTime)
}

// Computed data from stores and props
const processedPathData = computed(() => {
  // Handle both object format {userId, points, pointCount} and direct array format
  const pathData = props.pathData || locationStore.pathData
  
  if (!pathData) return []
  
  // If it's an object with points property, extract the points array
  if (pathData && typeof pathData === 'object' && pathData.points) {
    return Array.isArray(pathData.points) ? [pathData.points] : []
  }
  
  // If it's already an array, return as is
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

const hasAnyData = computed(() => {
  return processedPathData.value.length > 0 ||
         processedTimelineData.value.length > 0 ||
         processedFavoritesData.value.length > 0
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
              map.value.setView(newBounds[0], 14, { animate: false })
            } else if (newBounds.length > 1) {
              // Multiple points - fit bounds
              map.value.fitBounds(newBounds, { 
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

// Lifecycle
onMounted(() => {
  // Any additional initialization
})

// Expose methods for parent component
defineExpose({
  map: readonly(map),
  clearAllHighlights: clearAllMapHighlights,
  zoomToData: handleZoomToData
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
  z-index: 1000;
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