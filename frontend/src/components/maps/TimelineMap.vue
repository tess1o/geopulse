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
          :show-friends="showFriends"
          :show-favorites="showFavorites"
          :show-timeline="showTimeline"
          :show-path="showPath"
          @toggle-friends="toggleFriends"
          @toggle-favorites="toggleFavorites"
          @toggle-timeline="toggleTimeline"
          @toggle-path="togglePath"
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

<!--        &lt;!&ndash; Friends Layer &ndash;&gt;-->
<!--        <FriendsLayer-->
<!--          v-if="map && isReady"-->
<!--          ref="friendsLayerRef"-->
<!--          :map="map"-->
<!--          :friends-data="processedFriendsData"-->
<!--          :visible="showFriends"-->
<!--          @friend-click="handleFriendClick"-->
<!--        />-->

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
      </template>
    </MapContainer>
  </div>
</template>

<script setup>
import {computed, nextTick, onMounted, readonly, ref, watch} from 'vue'
import {useConfirm} from "primevue/useconfirm"
import ContextMenu from 'primevue/contextmenu'
import ConfirmDialog from 'primevue/confirmdialog'
import L from 'leaflet'
import 'leaflet-draw'
import 'leaflet-draw/dist/leaflet.draw.css'

// Map components
import {FavoritesLayer, FriendsLayer, MapContainer, MapControls, PathLayer, TimelineLayer} from '@/components/maps'
import CurrentLocationLayer from '@/components/maps/CurrentLocationLayer.vue'

// Dialog components
import AddFavoriteDialog from '@/components/dialogs/AddFavoriteDialog.vue'
import EditFavoriteDialog from '@/components/dialogs/EditFavoriteDialog.vue'

// Composables
import {useMapHighlights, useMapInteractions, useMapLayers} from '@/composables'

// Store imports
import {useHighlightStore} from '@/stores/highlight'
import {useFavoritesStore} from '@/stores/favorites'
import {useLocationStore} from '@/stores/location'
import {useTimelineStore} from '@/stores/timeline'
import {useFriendsStore} from '@/stores/friends'


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
  friends: {
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
  }
})

// Emits
const emit = defineEmits([
  'add-point',
  'add-area',
  'edit-favorite',
  'delete-favorite',
  'highlighted-path-click',
  'timeline-marker-click'
])

// Composables
const {
  showFriends,
  showFavorites,
  showTimeline,
  showPath,
  toggleFriends,
  toggleFavorites,
  toggleTimeline,
  togglePath
} = useMapLayers()

// Store instances
const favoritesStore = useFavoritesStore()
const locationStore = useLocationStore()
const timelineStore = useTimelineStore()
//const friendsStore = useFriendsStore()

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
  onFriendClick: (event) => console.log('Friend clicked:', event),
  onFavoriteClick: (event) => console.log('Favorite clicked:', event),
  onMapClick: (event) => console.log('Map clicked:', event),
  onMapContextMenu: (event) => console.log('Map context menu:', event)
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
//const friendsLayerRef = ref(null)
const favoritesLayerRef = ref(null)
const mapContextMenuRef = ref(null)
const favoriteContextMenuRef = ref(null)

const confirm = useConfirm()

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

// Drawing state
const drawingState = ref({
  areaDrawHandler: null,
  tempAreaLayer: null
})

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
  
  // Initialize drawing controls
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


const handleFavoriteEdit = (event) => {
  dialogState.value.selectedFavorite = event.favorite || event
  dialogState.value.editFavoriteVisible = true
}

const handleFavoriteDelete = (event) => {
  const favorite = event.favorite || event
  // Confirm deletion
  confirm.require({
    message: 'Are you sure you want to delete this favorite location?',
    header: 'Delete Favorite',
    icon: 'pi pi-exclamation-triangle',
    accept: () => {
      emit('delete-favorite', favorite)
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

// Drawing Methods
const startDrawing = () => {
  if (!map.value) return

  drawingState.value.areaDrawHandler = new L.Draw.Rectangle(map.value, {
    showArea: true,
    shapeOptions: {
      color: '#e91e63', // MARKER_COLORS.FAVORITE
      weight: 2
    }
  })
  drawingState.value.areaDrawHandler.enable()
}

const initAreaDrawControl = () => {
  if (!map.value) return
  
  // Handle escape key to cancel drawing
  const handleEscape = (e) => {
    if (e.key === 'Escape') {
      closeAddFavoriteArea()
      if (drawingState.value.areaDrawHandler) {
        drawingState.value.areaDrawHandler.disable()
      }
    }
  }
  window.addEventListener('keydown', handleEscape)

  // Handle draw:created event
  map.value.on('draw:created', (e) => {
    drawingState.value.tempAreaLayer = e.layer
    dialogState.value.addAreaVisible = true
  })
  
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
  emit('add-point', newFavorite)
  closeAddFavoritePoint()
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
  
  emit('add-area', newFavorite)
  closeAddFavoriteArea()
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
  if (drawingState.value.areaDrawHandler) {
    drawingState.value.areaDrawHandler.disable()
    drawingState.value.areaDrawHandler = null
  }
  
  if (drawingState.value.tempAreaLayer && map.value) {
    map.value.removeLayer(drawingState.value.tempAreaLayer)
    drawingState.value.tempAreaLayer = null
  }
}

const closeEditFavorite = () => {
  dialogState.value.editFavoriteVisible = false
  dialogState.value.selectedFavorite = null
}

const deleteFavoriteLocation = (favorite) => {
  handleFavoriteDelete(favorite)
  closeEditFavorite()
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

// const processedFriendsData = computed(() => {
//   return props.friends || friendsStore.friends || []
// })

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
         //processedFriendsData.value.length > 0 ||
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
  
  // Add friends data bounds
  // if (processedFriendsData.value.length > 0) {
  //   processedFriendsData.value.forEach(friend => {
  //     if (friend.latitude && friend.longitude) {
  //       bounds.push([friend.latitude, friend.longitude])
  //     }
  //   })
  // }
  
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