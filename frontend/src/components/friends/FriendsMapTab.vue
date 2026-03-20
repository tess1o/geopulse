<template>
  <div class="friends-map-tab">
    <!-- Loading State -->
    <div v-if="loading" class="loading-state">
      <div class="loading-content">
        <i class="pi pi-spin pi-spinner loading-spinner"></i>
        <p class="loading-text">Loading friends map...</p>
      </div>
    </div>

    <div v-else-if="!friends?.length" class="empty-state">
      <div class="empty-icon">
        <i class="pi pi-map"></i>
      </div>
      <h3 class="empty-title">No Friends to Show</h3>
      <p class="empty-description">
        Add friends to see their locations on the map
      </p>
      <Button
          label="Invite Friends"
          icon="pi pi-user-plus"
          @click="$emit('invite-friend')"
      />
    </div>

    <div v-else-if="!friendsWithLocation.length" class="empty-state">
      <div class="empty-icon">
        <i class="pi pi-map-marker"></i>
      </div>
      <h3 class="empty-title">No Location Data Available</h3>
      <p class="empty-description">
        Your friends haven't shared their location yet. This could be because they've disabled location sharing
        or haven't used location tracking apps.
      </p>
      <div class="empty-actions">
        <Button
            label="Refresh"
            icon="pi pi-refresh"
            outlined
            @click="$emit('refresh')"
            :loading="refreshing"
        />
        <Button
            label="Invite More Friends"
            icon="pi pi-user-plus"
            @click="$emit('invite-friend')"
        />
      </div>
    </div>

    <div v-else class="map-layout">
      <LiveFriendsFilter
          v-model="selectedFriendKeysState"
          :friends="friendsWithLocation"
          class="live-friends-filter"
      />

      <div v-if="showSelectionEmptyState" class="empty-state selection-empty-state">
        <div class="empty-icon">
          <i class="pi pi-filter"></i>
        </div>
        <h3 class="empty-title">No Friends Selected</h3>
        <p class="empty-description">
          Choose at least one friend in the filter to show locations on the map.
        </p>
        <div class="empty-actions">
          <Button
              label="Show All Friends"
              icon="pi pi-users"
              @click="resetSelectionToAll"
          />
        </div>
      </div>

      <div v-else class="map-wrapper">
        <FriendsMap
            ref="friendsMapRef"
            :friends="filteredFriendsWithLocation"
            :friend-trails="friendTrails"
            :show-friend-location-trails="showFriendLocationTrails"
            :current-user="currentUser"
            :initial-friend-email="initialFriendEmailToZoom"
            :key="`friends-map-${filteredFriendsWithLocation.length}`"
            @friend-located="$emit('friend-located', $event)"
            @refresh="$emit('refresh')"
            @show-all="$emit('show-all')"
            @toggle-trails="$emit('toggle-trails', $event)"
            class="friends-map"
        />
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, ref, watch, nextTick } from 'vue'
import FriendsMap from '@/components/maps/FriendsMap.vue'
import LiveFriendsFilter from '@/components/friends/LiveFriendsFilter.vue'

const props = defineProps({
  friends: {
    type: Array,
    default: () => []
  },
  selectedFriendKeys: {
    type: Array,
    default: () => []
  },
  useDefaultSelection: {
    type: Boolean,
    default: true
  },
  currentUser: {
    type: Object,
    default: null
  },
  initialFriendEmailToZoom: {
    type: String,
    default: null
  },
  friendTrails: {
    type: Object,
    default: () => ({})
  },
  showFriendLocationTrails: {
    type: Boolean,
    default: true
  },
  refreshing: {
    type: Boolean,
    default: false
  },
  loading: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['invite-friend', 'refresh', 'friend-located', 'show-all', 'toggle-trails', 'selection-change'])

const friendsMapRef = ref(null)

const getFriendFilterKey = (friend) => {
  const key = friend?.friendId || friend?.userId || friend?.id || friend?.email
  return key !== null && key !== undefined ? String(key) : null
}

const hasValidCoordinate = (value) => {
  return typeof value === 'number' && !Number.isNaN(value)
}

const friendsWithLocation = computed(() => {
  return props.friends?.filter(friend =>
      hasValidCoordinate(friend.lastLatitude) &&
      hasValidCoordinate(friend.lastLongitude)
  ) || []
})

const availableFriendKeys = computed(() => {
  const seenKeys = new Set()
  const keys = []

  friendsWithLocation.value.forEach(friend => {
    const key = getFriendFilterKey(friend)
    if (!key || seenKeys.has(key)) {
      return
    }

    seenKeys.add(key)
    keys.push(key)
  })

  return keys
})

const normalizeSelectedKeys = (selectedKeys, availableKeys) => {
  if (!Array.isArray(selectedKeys)) {
    return []
  }

  const normalizedSelection = new Set(
      selectedKeys
          .map(key => (key !== null && key !== undefined ? String(key) : null))
          .filter(Boolean)
  )

  return availableKeys.filter(key => normalizedSelection.has(key))
}

const areKeyArraysEqual = (left, right) => {
  if (left.length !== right.length) {
    return false
  }

  return left.every((value, index) => value === right[index])
}

const selectedFriendKeysState = ref([])

watch([() => props.selectedFriendKeys, () => props.useDefaultSelection, availableFriendKeys], ([incomingSelection, useDefaultSelection, availableKeys]) => {
  const nextSelection = useDefaultSelection
      ? [...availableKeys]
      : normalizeSelectedKeys(incomingSelection, availableKeys)

  if (!areKeyArraysEqual(selectedFriendKeysState.value, nextSelection)) {
    selectedFriendKeysState.value = nextSelection
  }
}, { immediate: true })

watch(selectedFriendKeysState, (newSelection) => {
  emit('selection-change', {
    selectedKeys: [...newSelection],
    availableKeys: [...availableFriendKeys.value]
  })
})

const filteredFriendsWithLocation = computed(() => {
  if (!friendsWithLocation.value.length || selectedFriendKeysState.value.length === 0) {
    return []
  }

  const selectedKeySet = new Set(selectedFriendKeysState.value)
  return friendsWithLocation.value.filter(friend => {
    const key = getFriendFilterKey(friend)
    return key && selectedKeySet.has(key)
  })
})

const showSelectionEmptyState = computed(() => {
  return friendsWithLocation.value.length > 0 && filteredFriendsWithLocation.value.length === 0
})

const resetSelectionToAll = () => {
  selectedFriendKeysState.value = [...availableFriendKeys.value]
}

const zoomToFriend = (friend) => {
  if (friendsMapRef.value) {
    friendsMapRef.value.zoomToFriend(friend)
  }
}

// Watch for refresh completion and re-zoom if initialFriendEmailToZoom is set
watch(() => props.refreshing, (newRefreshing, oldRefreshing) => {
  // Detect when refreshing transitions from true to false (refresh completed)
  if (oldRefreshing && !newRefreshing && props.initialFriendEmailToZoom) {
    // Give map time to update with new data
    nextTick(() => {
      setTimeout(() => {
        const friend = props.friends?.find(f => f.email === props.initialFriendEmailToZoom)
        if (friend && friendsMapRef.value) {
          zoomToFriend(friend)
        }
      }, 300)
    })
  }
})

defineExpose({
  zoomToFriend
})
</script>

<style scoped>
.friends-map-tab {
  width: 100%;
}

/* Loading State */
.loading-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
  padding: 3rem 1rem;
  background: var(--gp-surface-light);
  border-radius: var(--gp-radius-large);
  margin: 2rem 0;
  min-height: 400px;
}

.loading-content {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 1rem;
}

.loading-spinner {
  font-size: 3rem;
  color: var(--gp-primary);
}

.loading-text {
  font-size: 1rem;
  color: var(--gp-text-secondary);
  margin: 0;
}

/* Empty State */
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
  padding: 3rem 1rem;
  background: var(--gp-surface-light);
  border-radius: var(--gp-radius-large);
  margin: 2rem 0;
}

.empty-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 4rem;
  height: 4rem;
  background: var(--gp-primary-light);
  color: var(--gp-primary);
  border-radius: 50%;
  font-size: 2rem;
  margin-bottom: 1rem;
}

.empty-title {
  font-size: 1.25rem;
  font-weight: 600;
  color: var(--gp-text-primary);
  margin: 0 0 0.5rem 0;
}

.empty-description {
  font-size: 1rem;
  color: var(--gp-text-secondary);
  margin: 0 0 1.5rem 0;
  max-width: 400px;
  line-height: 1.5;
}

.empty-actions {
  display: flex;
  gap: 1rem;
  justify-content: center;
  flex-wrap: wrap;
}

.map-layout {
  height: 75vh;
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.live-friends-filter {
  flex-shrink: 0;
}

.selection-empty-state {
  margin: 0;
  flex: 1;
}

.map-wrapper {
  flex: 1;
  min-height: 320px;
}

.friends-map {
  width: 100%;
  height: 100%;
  max-width: 100%;
  box-sizing: border-box;
}

/* Responsive Design */
@media (max-width: 768px) {
  .map-layout {
    height: 70vh;
    gap: 0.5rem;
  }
}
</style>
