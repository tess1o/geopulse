<template>
  <div class="friends-map-container">
    <div class="friends-map-content">
      <!-- Loading overlay -->
      <div v-if="isLoading" class="map-loading-overlay">
        <ProgressSpinner size="small"/>
        <p class="text-sm text-surface-500 mt-2">Loading friend locations...</p>
      </div>

      <!-- Empty state overlay -->
      <div v-else-if="!hasLocations" class="map-empty-overlay">
        <div class="empty-content">
          <i class="pi pi-map-marker text-4xl text-surface-400 mb-3"></i>
          <h3 class="text-lg font-medium text-surface-600 dark:text-surface-400 mb-2">
            No Friend Locations
          </h3>
          <p class="text-sm text-surface-500 text-center max-w-sm">
            {{
              friends?.length ? 'Your friends haven\'t shared their locations yet.' : 'Add friends to see their locations on the map.'
            }}
          </p>
        </div>
      </div>

      <!-- Map Container -->
      <MapContainer
          v-if="hasLocations"
          :key="mapKey"
          ref="mapContainerRef"
          :map-id="uniqueMapId"
          :center="mapCenter"
          :zoom="mapZoom"
          :show-controls="false"
          height="100%"
          width="100%"
          @map-ready="handleMapReady"
      >
        <template #controls="{ map, isReady }">
          <div v-if="isReady" class="leaflet-top leaflet-right">
            <div class="leaflet-control">
              <button @click="refreshLocations" title="Refresh Locations" class="custom-map-control-button" style="margin-bottom: 5px">
                <i class="pi pi-refresh"></i>
              </button>
              <button v-if="initialFriendEmail != null" @click="resetFriendSelections" title="Show all"
                      class="custom-map-control-button">
                <i class="pi pi-filter-slash"></i>
              </button>
              <button
                  @click="toggleFriendLocationTrails"
                  :title="showFriendLocationTrails ? 'Hide location trails' : 'Show location trails'"
                  :aria-pressed="showFriendLocationTrails"
                  class="custom-map-control-button"
                  :class="{ 'custom-map-control-button--active': showFriendLocationTrails }"
              >
                <i :class="showFriendLocationTrails ? 'pi pi-sitemap' : 'pi pi-eye-slash'"></i>
              </button>
            </div>
          </div>
        </template>

        <!-- Friends Layer -->
        <template #overlays="{ map, isReady }">
          <FriendsLayer
              v-if="map && isReady && hasLocations"
              ref="friendsLayerRef"
              :map="map"
              :friends-data="processedFriendsData"
              :friend-trails="friendTrails"
              :show-trails="showFriendLocationTrails"
              :visible="true"
              @friend-click="handleFriendClick"
              @friend-hover="handleFriendHover"
          />
          <CurrentLocationLayer
              v-if="map && isReady && currentUser"
              :map="map"
              :location="currentUser"
              :visible="true"
          />
        </template>
      </MapContainer>
    </div>
  </div>
</template>

<script setup>
import {ref, computed, watch, onMounted, onUnmounted, nextTick} from 'vue'
import {useToast} from 'primevue/usetoast'
import {ProgressSpinner} from 'primevue'

// Map components
import {MapContainer, FriendsLayer, CurrentLocationLayer} from '@/components/maps'
import { MAP_RENDER_MODES, resolveMapEngineModeFromInstance } from '@/maps/contracts/mapContracts'

// Store
import {useFriendsStore} from '@/stores/friends'

const props = defineProps({
  friends: Array,
  currentUser: Object,
  initialFriendEmail: String,
  friendTrails: {
    type: Object,
    default: () => ({})
  },
  showFriendLocationTrails: {
    type: Boolean,
    default: true
  }
})

const emit = defineEmits(['friend-located', 'refresh', 'show-all', 'toggle-trails'])

// Pinia store
const friendsStore = useFriendsStore()

const toast = useToast()

// Computed data
const processedFriendsData = computed(() => {
  if (!props.friends) return []

  return processFriendsForMap(props.friends)
})

const FRIEND_TRAIL_COLOR_PALETTE = [
  '#E53935',
  '#43A047',
  '#1E88E5',
  '#FDD835',
  '#8E24AA',
  '#F57C00',
  '#00ACC1',
  '#3949AB',
  '#6D4C41',
  '#546E7A',
  '#00897B',
  '#6A1B9A'
]

const getFriendLocationKey = (friend) => {
  return friend?.friendId || friend?.userId || friend?.id || friend?.email
}

const getColorByFriend = (friend, index) => {
  const key = String(getFriendLocationKey(friend) || `friend-${index}`)
  const hash = key
      .split('')
      .reduce((acc, char) => (acc * 31 + char.charCodeAt(0)) % FRIEND_TRAIL_COLOR_PALETTE.length, 0)

  return FRIEND_TRAIL_COLOR_PALETTE[hash]
}

const getFriendTrailPoints = (friend) => {
  const key = getFriendLocationKey(friend)
  const trail = props.friendTrails?.[key] || props.friendTrails?.[String(key)] || []
  if (!Array.isArray(trail)) return []

  return trail
      .map(point => {
        const latitude = point?.latitude
        const longitude = point?.longitude

        if (typeof latitude !== 'number' || typeof longitude !== 'number' || Number.isNaN(latitude) || Number.isNaN(longitude)) {
          return null
        }

        return {
          latitude,
          longitude,
          timestamp: point?.timestamp
        }
      })
      .filter(Boolean)
}

const hasValidCoordinates = (latitude, longitude) => {
  return typeof latitude === 'number' &&
      typeof longitude === 'number' &&
      !Number.isNaN(latitude) &&
      !Number.isNaN(longitude)
}

const dataBounds = computed(() => {
  const bounds = []

  if (props.friends) {
    props.friends.forEach(friend => {
      if (hasValidCoordinates(friend?.lastLatitude, friend?.lastLongitude)) {
        bounds.push([friend.lastLatitude, friend.lastLongitude])
      }
    })
  }

  if (hasValidCoordinates(props.currentUser?.latitude, props.currentUser?.longitude)) {
    bounds.push([props.currentUser.latitude, props.currentUser.longitude])
  }

  return bounds.length > 0 ? bounds : null
})

// Template refs
const mapContainerRef = ref(null)
const friendsLayerRef = ref(null)

// Local state
const map = ref(null)
const isLoading = ref(false)
const mapKey = ref(0) // Force re-render key
const uniqueMapId = computed(() => `friends-map-${mapKey.value}-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`)
const selectedFriendKey = ref(null)
const isAutoFollowEnabled = ref(false)
const isProgrammaticCameraMove = ref(false)
let clearProgrammaticCameraMoveTimeoutId = null
let detachMapInteractionListeners = null

// Map configuration
const mapCenter = ref((() => {
  // Start centered on an initially selected friend when possible
  if (props.initialFriendEmail && props.friends?.length) {
    const friendToCenter = props.friends.find(friend =>
        friend?.email === props.initialFriendEmail &&
        hasValidCoordinates(friend?.lastLatitude, friend?.lastLongitude)
    )

    if (friendToCenter) {
      return [friendToCenter.lastLatitude, friendToCenter.lastLongitude]
    }
  }

  // Otherwise center on current user once at initialization
  if (hasValidCoordinates(props.currentUser?.latitude, props.currentUser?.longitude)) {
    return [props.currentUser.latitude, props.currentUser.longitude]
  }

  // If we have friends with locations, center on the first friend
  if (props.friends?.length) {
    const firstFriendWithLocation = props.friends.find(friend =>
        hasValidCoordinates(friend?.lastLatitude, friend?.lastLongitude)
    )

    if (firstFriendWithLocation) {
      return [firstFriendWithLocation.lastLatitude, firstFriendWithLocation.lastLongitude]
    }
  }

  // Default to London if no locations
  return [51.505, -0.09]
})())

const mapZoom = ref(13)

// Computed
const hasLocations = computed(() => {
  const hasFriendLocation = props.friends?.some(friend =>
      hasValidCoordinates(friend?.lastLatitude, friend?.lastLongitude)
  )

  const hasCurrentUserLocation = hasValidCoordinates(props.currentUser?.latitude, props.currentUser?.longitude)

  return hasFriendLocation || hasCurrentUserLocation
})

const toFriendKey = (friend) => {
  const key = getFriendLocationKey(friend)
  return key !== null && key !== undefined ? String(key) : null
}

const findFriendByKey = (targetKey) => {
  if (!targetKey || !props.friends?.length) return null

  return props.friends.find(friend => toFriendKey(friend) === String(targetKey)) || null
}

const clearProgrammaticCameraMoveTimeout = () => {
  if (clearProgrammaticCameraMoveTimeoutId !== null) {
    clearTimeout(clearProgrammaticCameraMoveTimeoutId)
    clearProgrammaticCameraMoveTimeoutId = null
  }
}

const beginProgrammaticCameraMove = (durationMs = 900) => {
  isProgrammaticCameraMove.value = true
  clearProgrammaticCameraMoveTimeout()
  clearProgrammaticCameraMoveTimeoutId = setTimeout(() => {
    isProgrammaticCameraMove.value = false
    clearProgrammaticCameraMoveTimeoutId = null
  }, durationMs)
}

const stopProgrammaticCameraMove = () => {
  clearProgrammaticCameraMoveTimeout()
  isProgrammaticCameraMove.value = false
}

const enableAutoFollow = () => {
  if (!selectedFriendKey.value) {
    return
  }

  isAutoFollowEnabled.value = true
}

const disableAutoFollow = () => {
  isAutoFollowEnabled.value = false
}

const setSelectedFriend = (friend) => {
  selectedFriendKey.value = toFriendKey(friend)
  if (selectedFriendKey.value) {
    enableAutoFollow()
  } else {
    disableAutoFollow()
  }
}

const clearMapInteractionListeners = () => {
  if (typeof detachMapInteractionListeners === 'function') {
    detachMapInteractionListeners()
    detachMapInteractionListeners = null
  }
}

const registerMapInteractionListeners = (mapInstance) => {
  clearMapInteractionListeners()

  if (!mapInstance) {
    return
  }

  const pauseAutoFollowFromManualMovement = () => {
    if (!selectedFriendKey.value || !isAutoFollowEnabled.value || isProgrammaticCameraMove.value) {
      return
    }

    disableAutoFollow()
  }

  const mapMode = resolveMapEngineModeFromInstance(mapInstance)
  const listeners = mapMode === MAP_RENDER_MODES.RASTER
      ? ['dragstart', 'zoomstart', 'movestart']
      : ['dragstart', 'zoomstart', 'movestart', 'rotatestart', 'pitchstart']

  listeners.forEach((eventName) => {
    mapInstance.on?.(eventName, pauseAutoFollowFromManualMovement)
  })

  detachMapInteractionListeners = () => {
    listeners.forEach((eventName) => {
      mapInstance.off?.(eventName, pauseAutoFollowFromManualMovement)
    })
  }
}

const setMapView = (center, zoom, options = {}) => {
  if (typeof mapContainerRef.value?.setView !== 'function') {
    return false
  }

  beginProgrammaticCameraMove(options?.animate ? 1200 : 900)
  mapContainerRef.value.setView(center, zoom, options)
  return true
}

const fitMapBounds = (bounds, options = {}) => {
  if (typeof mapContainerRef.value?.fitBounds !== 'function') {
    return false
  }

  beginProgrammaticCameraMove(1200)
  mapContainerRef.value.fitBounds(bounds, options)
  return true
}

const focusOnFriend = (friend, { openPopup = false, zoom = 17 } = {}) => {
  if (!map.value || !friend) {
    return false
  }

  if (friendsLayerRef.value?.focusOnFriend) {
    beginProgrammaticCameraMove(1200)
    const focusedInLayer = friendsLayerRef.value.focusOnFriend(friend, { openPopup, zoom })
    if (focusedInLayer) {
      return true
    }
  }

  if (hasValidCoordinates(friend?.lastLatitude, friend?.lastLongitude)) {
    return setMapView([friend.lastLatitude, friend.lastLongitude], zoom)
  }

  return false
}

// Methods
const handleMapReady = (mapInstance) => {
  map.value = mapInstance
  registerMapInteractionListeners(mapInstance)

  // Keep focus on selected friend when possible.
  if (selectedFriendKey.value && isAutoFollowEnabled.value) {
    const selectedFriend = findFriendByKey(selectedFriendKey.value)
    if (selectedFriend) {
      nextTick(() => {
        focusOnFriend(selectedFriend, { openPopup: false, zoom: 17 })
      })
      return
    }
  }

  // If an initial friend email is provided, try to zoom to that friend.
  if (props.initialFriendEmail) {
    const focused = tryZoomToInitialFriend({ openPopup: true })
    if (focused) {
      return
    }
  } else if (hasLocations.value && dataBounds.value) {
    // Otherwise, fit map to all friends data if available
    nextTick(() => {
      fitMapBounds(dataBounds.value, {padding: [20, 20]})
    })
  }
}

const handleFriendClick = (event) => {
  const {friend} = event
  setSelectedFriend(friend)
  emit('friend-located', friend)
}

const handleFriendHover = (event) => {
  // Could add hover effects here
}

const loadFriendLocations = async () => {
  if (!props.friends?.length) return

  isLoading.value = true

  try {
    await friendsStore.fetchFriends()
    // Data will be updated via watcher
  } catch (error) {
    console.error('Error loading friend locations:', error)
    toast.add({
      severity: 'error',
      summary: 'Error loading locations',
      detail: 'Could not load friend locations',
      life: 3000
    })
  } finally {
    isLoading.value = false
  }
}

const refreshLocations = () => {
  // Emit refresh event to parent to trigger full data refresh
  emit('refresh')
}

const resetFriendSelections = () => {
  selectedFriendKey.value = null
  disableAutoFollow()
  emit('show-all')
}

const toggleFriendLocationTrails = () => {
  const nextState = !props.showFriendLocationTrails
  emit('toggle-trails', nextState)
}

const zoomToFriend = (friend) => {
  if (!map.value) {
    toast.add({
      severity: 'warn',
      summary: 'Map not ready',
      detail: 'Map is not ready yet',
      life: 3000
    })
    return false
  }

  if (!friend || !hasValidCoordinates(friend.lastLatitude, friend.lastLongitude)) {
    toast.add({
      severity: 'warn',
      summary: 'Location not found',
      detail: `Could not find ${friend.fullName || friend.name}'s current location`,
      life: 3000
    })
    return false
  }

  setSelectedFriend(friend)
  focusOnFriend(friend, { openPopup: true, zoom: 17 })

  emit('friend-located', friend)
  return true
}

// Process friends data for the map
const processFriendsForMap = (friends) => {
  if (!friends || !Array.isArray(friends)) return []

  return friends
      .filter(friend => hasValidCoordinates(friend?.lastLatitude, friend?.lastLongitude))
      .map((friend, index) => ({
        ...friend,
        latitude: friend.lastLatitude,
        longitude: friend.lastLongitude,
        id: getFriendLocationKey(friend),
        friendId: getFriendLocationKey(friend),
        name: friend.fullName || friend.email,
        avatar: friend.avatar,
        lastSeen: friend.lastSeen,
        trailPoints: getFriendTrailPoints(friend),
        trailColor: getColorByFriend(friend, index)
      }))
}

// Update map center when data changes
watch(dataBounds, (newBounds, oldBounds) => {
  if (newBounds && map.value) {
    if (selectedFriendKey.value || props.initialFriendEmail) {
      return
    }

    // Avoid recentering on every background poll; only auto-fit when locations appear.
    if (oldBounds) {
      return
    }

    nextTick(() => {
      try {
        fitMapBounds(newBounds, {padding: [20, 20]})
      } catch (error) {
        console.warn('Error fitting bounds:', error)
      }
    })
  }
})

// Watch for friends prop changes to trigger map refresh if needed
watch(() => props.friends, (newFriends) => {
  // Force re-render if we transition from no friends to having friends
  if (newFriends && newFriends.length > 0 && !map.value) {
    mapKey.value++
  }

  if (!map.value || !newFriends?.length) {
    return
  }

  // Keep following the selected friend marker during live refreshes.
  if (selectedFriendKey.value) {
    const selectedFriend = findFriendByKey(selectedFriendKey.value)
    if (selectedFriend) {
      if (!isAutoFollowEnabled.value) {
        return
      }

      nextTick(() => {
        focusOnFriend(selectedFriend, { openPopup: false, zoom: 17 })
      })
      return
    }

    selectedFriendKey.value = null
    disableAutoFollow()
  }

  if (props.initialFriendEmail) {
    nextTick(() => {
      tryZoomToInitialFriend({ openPopup: false })
    })
  }
}, {deep: true})

const tryZoomToInitialFriend = ({ openPopup = true } = {}) => {
  if (props.initialFriendEmail && props.friends?.length && map.value) {
    const friendToZoom = props.friends.find(f =>
        f.email === props.initialFriendEmail &&
        hasValidCoordinates(f?.lastLatitude, f?.lastLongitude)
    )
    if (friendToZoom) {
      setSelectedFriend(friendToZoom)
      // Ensure map is ready before zooming
      nextTick(() => {
        focusOnFriend(friendToZoom, { openPopup, zoom: 17 })
      })
      return true
    }
  }

  return false
}

// Watch for initialFriendEmail prop changes
watch(() => props.initialFriendEmail, (newEmail) => {
  if (newEmail) {
    nextTick(() => {
      tryZoomToInitialFriend({ openPopup: true })
    })
  } else if (map.value && dataBounds.value) {
    selectedFriendKey.value = null
    disableAutoFollow()
    // If the friend is deselected, fit the map to the data bounds
    nextTick(() => {
      fitMapBounds(dataBounds.value, { padding: [20, 20] })
    })
  }
})

// Lifecycle
onMounted(async () => {
  // Load initial data
  if (props.friends?.length === 0) {
    await loadFriendLocations()
  }

  // Attempt to zoom to initial friend if provided, after data is potentially loaded
  if (props.initialFriendEmail) {
    tryZoomToInitialFriend({ openPopup: true })
  }
})

onUnmounted(() => {
  clearMapInteractionListeners()
  stopProgrammaticCameraMove()
})

// Expose methods to parent
defineExpose({
  zoomToFriend,
  refreshLocations
})
</script>

<script>
export default {
  name: "FriendsMap"
}
</script>

<style scoped>
.friends-map-container {
  height: 100%;
  width: 100%;
  display: flex;
  flex-direction: column;
}

.friends-map-header {
  padding: 1rem;
  border-bottom: 1px solid var(--p-surface-200);
  background: var(--p-surface-50);
  flex-shrink: 0;
}

.friends-map-content {
  flex: 1;
  min-height: 400px; /* Important for flexbox */
  width: 100%;
}

.map-loading-overlay,
.map-empty-overlay {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--p-surface-50);
  z-index: 1000;
}

.empty-content {
  text-align: center;
  padding: 2rem;
}

/* Friends Map Title */
.friends-map-title {
  color: var(--p-surface-900);
}

/* Dark mode support */
.dark .friends-map-container {
  background: var(--p-surface-900);
  border-color: var(--p-surface-700);
}

.dark .friends-map-header {
  background: var(--p-surface-800);
  border-bottom-color: var(--p-surface-700);
}

.dark .friends-map-title {
  color: var(--p-surface-100);
}

.dark .map-loading-overlay,
.dark .map-empty-overlay {
  background: var(--p-surface-800);
}

.custom-map-control-button {
  background-color: var(--gp-primary);
  color: white;
  border: none;
  border-radius: 50%;
  width: 40px;
  height: 40px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: var(--gp-shadow-large);
}

.custom-map-control-button:hover {
  background-color: var(--gp-primary-hover);
}

.custom-map-control-button i {
  font-size: 1.2rem;
  color: white;
}

.custom-map-control-button--active {
  background-color: #0f766e;
  border: 1px solid rgba(255, 255, 255, 0.35);
}

/* Mobile responsiveness */
@media (max-width: 768px) {
  .friends-map-container {
    min-height: 400px;
    border-radius: 1rem;
  }

  .friends-map-content {
    min-height: 300px;
  }

  .friends-map-header {
    padding: 0.75rem;
  }
}
</style>
