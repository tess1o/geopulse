<template>
  <div class="friends-content">
    <div v-if="!friends?.length" class="empty-state">
      <div class="empty-icon">
        <i class="pi pi-users"></i>
      </div>
      <h3 class="empty-title">No Friends Yet</h3>
      <p class="empty-description">
        Start building your network by inviting friends to connect and share locations
      </p>
      <Button
          label="Invite Your First Friend"
          icon="pi pi-user-plus"
          @click="$emit('invite-friend')"
      />
    </div>

    <div v-else class="friends-grid">
      <Card v-for="friend in friends" :key="friend.id" class="friend-card">
        <template #content>
          <div class="friend-info">
            <Avatar
                :image="friend.avatar || '/avatars/avatar1.png'"
                size="large"
                class="friend-avatar"
            />
            <div class="friend-details">
              <div class="friend-name">{{ friend.fullName }}</div>
              <div class="friend-email">{{ friend.email }}</div>
              <div class="friend-status">
                <Badge
                    :value="getFriendStatus(friend)"
                    :severity="getFriendStatusSeverity(friend)"
                    class="status-badge"
                />
                <span class="last-seen">Last seen: {{ getLastSeenText(friend.lastSeen) }}</span>
              </div>
              <div v-if="friend.lastLocation" class="friend-location">
                <i class="pi pi-map-marker location-icon"></i>
                <span class="location-text">{{ friend.lastLocation }}</span>
              </div>
            </div>
          </div>

          <!-- Permissions Section -->
          <div class="friend-permissions">
            <div class="permission-item">
              <i class="pi pi-map-marker permission-icon"></i>
              <span class="permission-label">Share Live Location</span>
              <InputSwitch
                  v-model="friend.shareLiveLocationPermission"
                  @change="handleLiveLocationPermissionChange(friend)"
                  class="permission-switch"
              />
              <i class="pi pi-info-circle info-icon"
                 v-tooltip="'Allows this friend to see your current location in real-time'"
              ></i>
            </div>
            <div class="permission-item">
              <i class="pi pi-history permission-icon"></i>
              <span class="permission-label">Share Timeline History</span>
              <InputSwitch
                  v-model="friend.shareTimelinePermission"
                  @change="handleTimelinePermissionChange(friend)"
                  class="permission-switch"
              />
              <i class="pi pi-info-circle info-icon"
                 v-tooltip="'Allows this friend to view your complete location history'"
              ></i>
            </div>
          </div>

          <div class="friend-actions">
            <Button
                icon="pi pi-map-marker"
                size="small"
                outlined
                @click="$emit('show-on-map', friend)"
                :disabled="!friend.lastLatitude || !friend.lastLongitude"
            />
            <Button
                icon="pi pi-trash"
                size="small"
                severity="danger"
                outlined
                @click="$emit('delete-friend', friend)"
            />
          </div>
        </template>
      </Card>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, watch } from 'vue'
import { useTimezone } from '@/composables/useTimezone'
import { useToast } from 'primevue/usetoast'
import { useConfirm } from 'primevue/useconfirm'
import apiService from '@/utils/apiService'
import InputSwitch from 'primevue/inputswitch'

const timezone = useTimezone()
const toast = useToast()
const confirm = useConfirm()

const props = defineProps({
  friends: {
    type: Array,
    default: () => []
  }
})

defineEmits(['invite-friend', 'show-on-map', 'delete-friend'])

// Load permissions for all friends
onMounted(async () => {
  await loadAllPermissions()
})

// Watch for friends changes and reload permissions
watch(() => props.friends, async (newFriends) => {
  if (newFriends && newFriends.length > 0) {
    await loadAllPermissions()
  }
}, { deep: true })

async function loadAllPermissions() {
  if (!props.friends || props.friends.length === 0) return

  try {
    // Load permissions for each friend
    const permissionPromises = props.friends.map(async (friend) => {
      try {
        const response = await apiService.getFriendPermissions(friend.friendId)
        friend.shareTimelinePermission = response.data?.shareTimeline || false
        friend.shareLiveLocationPermission = response.data?.shareLiveLocation || false
      } catch (error) {
        console.error(`Failed to load permissions for friend ${friend.friendId}:`, error)
        friend.shareTimelinePermission = false
        friend.shareLiveLocationPermission = false
      }
    })

    await Promise.all(permissionPromises)
  } catch (error) {
    console.error('Failed to load friend permissions:', error)
  }
}

async function handleTimelinePermissionChange(friend) {
  const newValue = friend.shareTimelinePermission

  // Show confirmation dialog
  confirm.require({
    message: newValue
      ? `This will allow ${friend.fullName} to view your complete location history (all past stays and trips). Continue?`
      : `This will revoke ${friend.fullName}'s access to your location history. Continue?`,
    header: 'Confirm Permission Change',
    icon: 'pi pi-exclamation-triangle',
    accept: async () => {
      try {
        await apiService.updateFriendPermissions(friend.friendId, newValue)

        toast.add({
          severity: 'success',
          summary: 'Permission Updated',
          detail: newValue
            ? `${friend.fullName} can now view your timeline history`
            : `${friend.fullName} can no longer view your timeline history`,
          life: 3000
        })
      } catch (error) {
        console.error('Failed to update permission:', error)

        // Revert the switch
        friend.shareTimelinePermission = !newValue

        toast.add({
          severity: 'error',
          summary: 'Failed to Update Permission',
          detail: error.message || 'Could not update friend permissions',
          life: 5000
        })
      }
    },
    reject: () => {
      // Revert the switch
      friend.shareTimelinePermission = !newValue
    }
  })
}

async function handleLiveLocationPermissionChange(friend) {
  const newValue = friend.shareLiveLocationPermission

  // Show confirmation dialog
  confirm.require({
    message: newValue
      ? `This will allow ${friend.fullName} to see your current location in real-time. Continue?`
      : `This will revoke ${friend.fullName}'s access to your live location. Continue?`,
    header: 'Confirm Permission Change',
    icon: 'pi pi-exclamation-triangle',
    accept: async () => {
      try {
        await apiService.updateLiveLocationPermission(friend.friendId, newValue)

        toast.add({
          severity: 'success',
          summary: 'Permission Updated',
          detail: newValue
            ? `${friend.fullName} can now view your live location`
            : `${friend.fullName} can no longer view your live location`,
          life: 3000
        })
      } catch (error) {
        console.error('Failed to update live location permission:', error)

        // Revert the switch
        friend.shareLiveLocationPermission = !newValue

        toast.add({
          severity: 'error',
          summary: 'Failed to Update Permission',
          detail: error.message || 'Could not update live location permission',
          life: 5000
        })
      }
    },
    reject: () => {
      // Revert the switch
      friend.shareLiveLocationPermission = !newValue
    }
  })
}

// Utility functions
const getFriendStatus = (friend) => {
  if (!friend.lastSeen) return 'No Location'

  const lastSeen = timezone.fromUtc(friend.lastSeen)
  const now = timezone.now()
  const diffMinutes = now.diff(lastSeen, 'minute')

  if (diffMinutes < 5) return 'Online'
  if (diffMinutes < 60) return 'Recent'
  return 'Offline'
}

const getFriendStatusSeverity = (friend) => {
  const status = getFriendStatus(friend)
  switch (status) {
    case 'Online':
      return 'success'
    case 'Recent':
      return 'warning'
    default:
      return 'secondary'
  }
}

const getLastSeenText = (lastSeen) => {
  if (!lastSeen) return 'Never'

  const date = timezone.fromUtc(lastSeen)
  const now = timezone.now()
  const diffMinutes = now.diff(date, 'minute')

  if (diffMinutes < 1) return 'Just now'
  if (diffMinutes < 60) return `${Math.floor(diffMinutes)}m ago`
  if (diffMinutes < 1440) return `${Math.floor(diffMinutes / 60)}h ago`
  return `${Math.floor(diffMinutes / 1440)}d ago`
}
</script>

<style scoped>
.friends-content {
  width: 100%;
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

/* Friends Grid */
.friends-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 1rem;
  box-sizing: border-box;
  width: 100%;
}

.friend-card {
  background: var(--gp-surface-white);
  border: 1px solid var(--gp-border-light);
  box-shadow: var(--gp-shadow-light);
  transition: all 0.2s ease;
}

.friend-card:hover {
  box-shadow: var(--gp-shadow-medium);
  border-color: var(--gp-primary-light);
}

.friend-info {
  display: flex;
  align-items: flex-start;
  gap: 1rem;
  margin-bottom: 1rem;
}

.friend-avatar {
  flex-shrink: 0;
}

.friend-details {
  flex: 1;
  min-width: 0;
}

.friend-name {
  font-size: 1.1rem;
  font-weight: 600;
  color: var(--gp-text-primary);
  margin-bottom: 0.25rem;
}

.friend-email {
  font-size: 0.9rem;
  color: var(--gp-text-secondary);
  margin-bottom: 0.5rem;
  word-break: break-word;
}

.friend-status {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.last-seen {
  font-size: 0.8rem;
  color: var(--gp-text-secondary);
}

.friend-location {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  margin-top: 0.5rem;
}

.location-icon {
  font-size: 0.75rem;
  color: var(--gp-primary);
}

.location-text {
  font-size: 0.8rem;
  color: var(--gp-text-secondary);
  line-height: 1.2;
}

/* Permissions Section */
.friend-permissions {
  margin-top: 1rem;
  padding-top: 1rem;
  border-top: 1px solid var(--gp-border-light);
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.permission-item {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  padding: 0.5rem;
  background: var(--gp-surface-light);
  border-radius: var(--gp-radius-small);
}

.permission-icon {
  font-size: 1rem;
  color: var(--gp-primary);
}

.permission-label {
  flex: 1;
  font-size: 0.9rem;
  font-weight: 500;
  color: var(--gp-text-primary);
}

.permission-switch {
  flex-shrink: 0;
}

.info-icon {
  font-size: 0.875rem;
  color: var(--gp-text-secondary);
  cursor: help;
}

.friend-actions {
  display: flex;
  gap: 0.5rem;
  justify-content: flex-end;
}

/* Responsive Design */
@media (max-width: 768px) {
  .friends-grid {
    grid-template-columns: 1fr;
  }
}
</style>
