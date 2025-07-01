<template>
  <div class="friends-map-container">
    <div class="friends-map-header">
      <div class="flex items-center justify-between">
        <div class="flex items-center gap-2">
          <i class="pi pi-map text-blue-500"></i>
          <span class="font-bold text-lg friends-map-title">Friends Map</span>
          <Badge v-if="hasLocations" :value="friends.length" severity="info"/>
        </div>

        <div class="flex items-center gap-2">
          <Button
              icon="pi pi-refresh"
              size="small"
              outlined
              severity="secondary"
              :loading="isLoading"
              @click="refreshLocations"
          />
        </div>
      </div>
    </div>

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
        <!-- Friends Layer -->
        <template #overlays="{ map, isReady }">
          <FriendsLayer
            v-if="map && isReady && hasLocations"
            ref="friendsLayerRef"
            :map="map"
            :friends-data="processedFriendsData"
            :visible="true"
            @friend-click="handleFriendClick"
            @friend-hover="handleFriendHover"
          />
        </template>
      </MapContainer>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted, nextTick } from 'vue'
import { storeToRefs } from 'pinia'
import { useToast } from 'primevue/usetoast'
import { Badge, Button, ProgressSpinner } from 'primevue'

// Map components
import { MapContainer, FriendsLayer } from '@/components/maps'


// Store
import { useFriendsStore } from '@/stores/friends'

const props = defineProps({
  friends: Array
})

const emit = defineEmits(['friend-located'])

// Pinia store
const friendsStore = useFriendsStore()
const { getFriendsWithLocations } = storeToRefs(friendsStore)

const toast = useToast()

// Computed data
const processedFriendsData = computed(() => {
  if (!props.friends) return []
  
  // Debug logging
  console.log('FriendsMap: Raw friends data:', props.friends)
  const processed = processFriendsForMap(props.friends)
  console.log('FriendsMap: Processed friends data:', processed)
  
  return processed
})

const dataBounds = computed(() => {
  if (!hasLocations.value) return null
  
  const bounds = []
  props.friends.forEach(friend => {
    if (friend.lastLatitude && friend.lastLongitude) {
      bounds.push([friend.lastLatitude, friend.lastLongitude])
    }
  })
  
  return bounds.length > 1 ? bounds : null
})

// Template refs
const mapContainerRef = ref(null)
const friendsLayerRef = ref(null)

// Local state
const map = ref(null)
const isLoading = ref(false)
const mapKey = ref(0) // Force re-render key
const uniqueMapId = computed(() => `friends-map-${mapKey.value}-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`)

// Map configuration
const mapCenter = computed(() => {
  // If we have friends with locations, center on the first friend
  if (hasLocations.value && props.friends && props.friends.length > 0) {
    const firstFriendWithLocation = props.friends.find(friend => 
      friend &&
      typeof friend.lastLatitude === 'number' &&
      typeof friend.lastLongitude === 'number' &&
      !isNaN(friend.lastLatitude) &&
      !isNaN(friend.lastLongitude)
    )
    
    if (firstFriendWithLocation) {
      return [firstFriendWithLocation.lastLatitude, firstFriendWithLocation.lastLongitude]
    }
  }
  
  // Default to London if no friends with locations
  return [51.505, -0.09]
})

const mapZoom = ref(15)

// Computed
const hasLocations = computed(() => {
  if (!props.friends || !Array.isArray(props.friends) || props.friends.length === 0) {
    console.log('FriendsMap: No friends data or empty array')
    return false
  }

  const result = props.friends.some(friend => {
    const hasValidLocation = (
        friend &&
        typeof friend.lastLatitude === 'number' &&
        typeof friend.lastLongitude === 'number' &&
        !isNaN(friend.lastLatitude) &&
        !isNaN(friend.lastLongitude)
    )
    
    if (!hasValidLocation) {
      console.log('FriendsMap: Friend without valid location:', {
        friend: friend?.fullName || friend?.email,
        lastLatitude: friend?.lastLatitude,
        lastLongitude: friend?.lastLongitude,
        latType: typeof friend?.lastLatitude,
        lngType: typeof friend?.lastLongitude
      })
    }
    
    return hasValidLocation
  })
  
  console.log('FriendsMap: hasLocations result:', result)
  return result
})

// Methods
const handleMapReady = (mapInstance) => {
  console.log('FriendsMap: Map ready', mapInstance)
  map.value = mapInstance
  
  // Fit map to friends data if available
  if (hasLocations.value && dataBounds.value) {
    nextTick(() => {
      mapInstance.fitBounds(dataBounds.value, { padding: [20, 20] })
    })
  }
}

const handleFriendClick = (event) => {
  const { friend } = event

  // Show success message
  toast.add({
    severity: 'success',
    summary: 'Friend selected',
    detail: `Showing ${friend.fullName || friend.name}'s location`,
    life: 2000
  })

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

const refreshLocations = async () => {
  // Force re-render of map to ensure clean state
  mapKey.value++
  await loadFriendLocations()
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

  if (!friend || !friend.lastLatitude || !friend.lastLongitude) {
    toast.add({
      severity: 'warn',
      summary: 'Location not found',
      detail: `Could not find ${friend.fullName || friend.name}'s current location`,
      life: 3000
    })
    return false
  }

  // Zoom to the friend's location
  map.value.setView([friend.lastLatitude, friend.lastLongitude], 16, {
    animate: true,
    duration: 1
  })

  // Focus on the friend in the layer
  if (friendsLayerRef.value) {
    friendsLayerRef.value.focusOnFriend(friend)
  }

  // Show success message
  toast.add({
    severity: 'success',
    summary: 'Friend located',
    detail: `Showing ${friend.fullName || friend.name}'s location`,
    life: 2000
  })

  emit('friend-located', friend)
  return true
}

// Process friends data for the map
const processFriendsForMap = (friends) => {
  if (!friends || !Array.isArray(friends)) return []
  
  return friends
    .filter(friend => friend.lastLatitude && friend.lastLongitude)
    .map(friend => ({
      ...friend,
      latitude: friend.lastLatitude,
      longitude: friend.lastLongitude,
      id: friend.id || friend.email,
      name: friend.fullName || friend.email,
      avatar: friend.avatar,
      lastSeen: friend.lastSeen
    }))
}

// No watchers needed - using computed properties

// Update map center when data changes
watch(dataBounds, (newBounds) => {
  if (newBounds && map.value) {
    nextTick(() => {
      try {
        map.value.fitBounds(newBounds, { padding: [20, 20] })
      } catch (error) {
        console.warn('Error fitting bounds:', error)
      }
    })
  }
})

// Watch for friends prop changes to trigger map refresh if needed
watch(() => props.friends, (newFriends) => {
  console.log('FriendsMap: Friends prop changed:', newFriends)
  // Force re-render if we transition from no friends to having friends
  if (newFriends && newFriends.length > 0 && !map.value) {
    mapKey.value++
  }
}, { deep: true })

// Lifecycle
onMounted(async () => {
  // Load initial data
  if (props.friends?.length) {
    await loadFriendLocations()
  }
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
  background: var(--p-surface-0);
  border: 1px solid var(--p-surface-200);
  border-radius: 1.5rem;
  overflow: hidden;
  height: 100%;
  min-height: 500px;
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