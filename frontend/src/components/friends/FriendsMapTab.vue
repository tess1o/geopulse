<template>
  <div>
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
        Your friends haven't shared their location data yet. Location sharing happens automatically when they
        use location tracking apps.
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

    <div v-else class="map-wrapper">
      <FriendsMap
          ref="friendsMapRef"
          :friends="friendsWithLocation"
          :current-user="currentUser"
          :initial-friend-email="initialFriendEmailToZoom"
          :key="`friends-map-${friendsWithLocation.length}`"
          @friend-located="$emit('friend-located', $event)"
          @refresh="$emit('refresh')"
          @show-all="$emit('show-all')"
          class="friends-map"
      />
    </div>
  </div>
</template>

<script setup>
import { computed, ref, watch, nextTick } from 'vue'
import FriendsMap from '@/components/maps/FriendsMap.vue'

const props = defineProps({
  friends: {
    type: Array,
    default: () => []
  },
  currentUser: {
    type: Object,
    default: null
  },
  initialFriendEmailToZoom: {
    type: String,
    default: null
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

const emit = defineEmits(['invite-friend', 'refresh', 'friend-located', 'show-all'])

const friendsMapRef = ref(null)

const friendsWithLocation = computed(() => {
  return props.friends?.filter(friend =>
      friend.lastLatitude &&
      friend.lastLongitude &&
      typeof friend.lastLatitude === 'number' &&
      typeof friend.lastLongitude === 'number' &&
      !isNaN(friend.lastLatitude) &&
      !isNaN(friend.lastLongitude)
  ) || []
})

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

.map-wrapper {
  height: 75vh;
}

.friends-map {
  width: 100%;
  height: 100%;
  max-width: 100%;
  box-sizing: border-box;
}

/* Responsive Design */
@media (max-width: 768px) {
  .map-wrapper {
    height: 70vh;
  }
}
</style>
