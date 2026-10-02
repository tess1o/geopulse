<template>
  <div class="friends-content">
    <div v-if="!friends?.length" class="gp-empty-state gp-empty-state--panel">
      <div class="gp-empty-state-icon">
        <i class="pi pi-users"></i>
      </div>
      <h3 class="gp-empty-state-title">{{ t('friends.listTab.empty.title') }}</h3>
      <p class="gp-empty-state-message">
        {{ t('friends.listTab.empty.description') }}
      </p>
      <p v-if="readOnly" class="demo-disabled-text">
        {{ t('friends.listTab.empty.demoDisabled') }}
      </p>
      <Button
          :label="t('friends.listTab.empty.inviteFirst')"
          icon="pi pi-user-plus"
          :disabled="readOnly"
          v-tooltip.bottom="readOnly ? t('friends.demo.invitationsTooltip') : t('friends.listTab.empty.inviteFirst')"
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
                <span class="last-seen">{{ t('friends.listTab.lastSeenLabel', { text: getLastSeenText(friend.lastSeen) }) }}</span>
              </div>
              <div v-if="friend.lastLocation" class="friend-location">
                <i class="pi pi-map-marker location-icon"></i>
                <span class="location-text">{{ friend.lastLocation }}</span>
              </div>
            </div>
          </div>

          <!-- What Friend Shares With You (Read-only status) -->
          <div class="friend-sharing-status">
            <div class="section-header">
              <i class="pi pi-eye"></i>
              <span>{{ t('friends.listTab.sharesWithYou') }}</span>
            </div>

            <div class="sharing-status-items">
              <div class="status-item">
                <i :class="['pi', friend.friendSharesLiveLocation ? 'pi-check-circle' : 'pi-times-circle']"
                   :style="{ color: friend.friendSharesLiveLocation ? 'var(--p-green-500)' : 'var(--p-red-500)' }"></i>
                <span class="status-label">{{ t('friends.listTab.liveLocation') }}</span>
                <Badge
                  :value="friend.friendSharesLiveLocation ? t('friends.listTab.shared') : t('friends.listTab.notShared')"
                  :severity="friend.friendSharesLiveLocation ? 'success' : 'secondary'"
                />
              </div>
              <div class="status-item">
                <i :class="['pi', friend.friendSharesTimeline ? 'pi-check-circle' : 'pi-times-circle']"
                   :style="{ color: friend.friendSharesTimeline ? 'var(--p-green-500)' : 'var(--p-red-500)' }"></i>
                <span class="status-label">{{ t('friends.listTab.timelineHistory') }}</span>
                <Badge
                  :value="friend.friendSharesTimeline ? t('friends.listTab.shared') : t('friends.listTab.notShared')"
                  :severity="friend.friendSharesTimeline ? 'success' : 'secondary'"
                />
              </div>
            </div>
          </div>

          <!-- What You Share With Friend (Editable toggles) -->
          <div class="friend-permissions">
            <div class="section-header">
              <i class="pi pi-lock"></i>
              <span>{{ t('friends.listTab.sharesWithFriend') }}</span>
            </div>
            <p v-if="readOnly" class="demo-disabled-text demo-disabled-text--inline">
              {{ t('friends.listTab.demoPermissionsReadOnly') }}
            </p>
            <div class="permission-item">
              <i class="pi pi-map-marker permission-icon"></i>
              <span class="permission-label">{{ t('friends.listTab.liveLocation') }}</span>
              <InputSwitch
                  v-model="friend.shareLiveLocationPermission"
                  :disabled="readOnly"
                  @change="handleLiveLocationPermissionChange(friend)"
                  class="permission-switch"
              />
              <i class="pi pi-info-circle info-icon"
                 v-tooltip="t('friends.listTab.liveLocationInfo')"
              ></i>
            </div>
            <div class="permission-item">
              <i class="pi pi-history permission-icon"></i>
              <span class="permission-label">{{ t('friends.listTab.timelineHistory') }}</span>
              <InputSwitch
                  v-model="friend.shareTimelinePermission"
                  :disabled="readOnly"
                  @change="handleTimelinePermissionChange(friend)"
                  class="permission-switch"
              />
              <i class="pi pi-info-circle info-icon"
                 v-tooltip="t('friends.listTab.timelineHistoryInfo')"
              ></i>
            </div>
          </div>

          <div class="friend-actions">
            <Button
                icon="pi pi-map-marker"
                :label="t('friends.listTab.actions.live')"
                size="small"
                outlined
                @click="$emit('show-on-map', friend)"
                :disabled="!friend.lastLatitude || !friend.lastLongitude"
                v-tooltip.bottom="t('friends.listTab.actions.liveTooltip')"
            />
            <Button
                icon="pi pi-history"
                :label="t('friends.listTab.actions.timeline')"
                size="small"
                outlined
                @click="$emit('show-timeline', friend)"
                v-tooltip.bottom="t('friends.listTab.actions.timelineTooltip')"
            />
            <Button
                icon="pi pi-trash"
                size="small"
                severity="danger"
                outlined
                :disabled="readOnly"
                @click="$emit('delete-friend', friend)"
                v-tooltip.bottom="readOnly ? t('friends.listTab.actions.removeTooltipDemo') : t('friends.listTab.actions.removeTooltip')"
            />
          </div>
        </template>
      </Card>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useTimezone } from '@/composables/useTimezone'
import { useToast } from 'primevue/usetoast'
import { useConfirm } from 'primevue/useconfirm'
import { useFriendsStore } from '@/stores/friends'
import { formatApiErrorDetail } from '@/utils/apiErrorDetail'
import InputSwitch from 'primevue/inputswitch'
import { showDemoModeToast } from '@/utils/demoMode'

const { t } = useI18n()
const timezone = useTimezone()
const toast = useToast()
const confirm = useConfirm()
const friendsStore = useFriendsStore()

const props = defineProps({
  friends: {
    type: Array,
    default: () => []
  },
  readOnly: {
    type: Boolean,
    default: false
  }
})

defineEmits(['invite-friend', 'show-on-map', 'show-timeline', 'delete-friend'])

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
        const response = await friendsStore.getFriendPermissions(friend.friendId)
        friend.shareTimelinePermission = response?.shareTimeline || false
        friend.shareLiveLocationPermission = response?.shareLiveLocation || false
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
  if (props.readOnly) {
    friend.shareTimelinePermission = !newValue
    showDemoModeToast(toast, t('friends.listTab.demoPermissionsReadOnly'))
    return
  }

  // Show confirmation dialog
  confirm.require({
    message: newValue
      ? t('friends.listTab.permissionDialog.timelineAllow', { name: friend.fullName })
      : t('friends.listTab.permissionDialog.timelineRevoke', { name: friend.fullName }),
    header: t('friends.listTab.permissionDialog.header'),
    icon: 'pi pi-exclamation-triangle',
    accept: async () => {
      try {
        const response = await friendsStore.updateFriendPermissions(friend.friendId, newValue)

        // Update state from API response to ensure consistency
        friend.shareTimelinePermission = response?.shareTimeline ?? newValue
        friend.shareLiveLocationPermission = response?.shareLiveLocation ?? friend.shareLiveLocationPermission

        toast.add({
          severity: 'success',
          summary: t('friends.listTab.permissionToast.updatedSummary'),
          detail: newValue
            ? t('friends.listTab.permissionToast.timelineGranted', { name: friend.fullName })
            : t('friends.listTab.permissionToast.timelineRevoked', { name: friend.fullName }),
          life: 3000
        })
      } catch (error) {
        console.error('Failed to update permission:', error)

        // Revert the switch
        friend.shareTimelinePermission = !newValue

        toast.add({
          severity: 'error',
          summary: t('friends.listTab.permissionToast.failedSummary'),
          detail: formatApiErrorDetail(error, t('friends.listTab.permissionToast.timelineFailedDetail')),
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
  if (props.readOnly) {
    friend.shareLiveLocationPermission = !newValue
    showDemoModeToast(toast, t('friends.listTab.demoPermissionsReadOnly'))
    return
  }

  // Show confirmation dialog
  confirm.require({
    message: newValue
      ? t('friends.listTab.permissionDialog.liveAllow', { name: friend.fullName })
      : t('friends.listTab.permissionDialog.liveRevoke', { name: friend.fullName }),
    header: t('friends.listTab.permissionDialog.header'),
    icon: 'pi pi-exclamation-triangle',
    accept: async () => {
      try {
        const response = await friendsStore.updateLiveLocationPermission(friend.friendId, newValue)

        // Update state from API response to ensure consistency
        friend.shareLiveLocationPermission = response?.shareLiveLocation ?? newValue
        friend.shareTimelinePermission = response?.shareTimeline ?? friend.shareTimelinePermission

        toast.add({
          severity: 'success',
          summary: t('friends.listTab.permissionToast.updatedSummary'),
          detail: newValue
            ? t('friends.listTab.permissionToast.liveGranted', { name: friend.fullName })
            : t('friends.listTab.permissionToast.liveRevoked', { name: friend.fullName }),
          life: 3000
        })
      } catch (error) {
        console.error('Failed to update live location permission:', error)

        // Revert the switch
        friend.shareLiveLocationPermission = !newValue

        toast.add({
          severity: 'error',
          summary: t('friends.listTab.permissionToast.failedSummary'),
          detail: formatApiErrorDetail(error, t('friends.listTab.permissionToast.liveFailedDetail')),
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
const getFriendStatusKey = (friend) => {
  if (!friend.lastSeen) return 'noLocation'

  const lastSeen = timezone.fromUtc(friend.lastSeen)
  const now = timezone.now()
  const diffMinutes = now.diff(lastSeen, 'minute')

  if (diffMinutes < 5) return 'online'
  if (diffMinutes < 60) return 'recent'
  return 'offline'
}

const getFriendStatus = (friend) => t(`friends.listTab.status.${getFriendStatusKey(friend)}`)

const getFriendStatusSeverity = (friend) => {
  switch (getFriendStatusKey(friend)) {
    case 'online':
      return 'success'
    case 'recent':
      return 'warning'
    default:
      return 'secondary'
  }
}

const getLastSeenText = (lastSeen) => {
  if (!lastSeen) return t('friends.listTab.lastSeenText.never')

  const date = timezone.fromUtc(lastSeen)
  const now = timezone.now()
  const diffMinutes = now.diff(date, 'minute')

  if (diffMinutes < 1) return t('friends.listTab.lastSeenText.justNow')
  if (diffMinutes < 60) return t('friends.listTab.lastSeenText.minutesAgo', { count: Math.floor(diffMinutes) })
  if (diffMinutes < 1440) return t('friends.listTab.lastSeenText.hoursAgo', { count: Math.floor(diffMinutes / 60) })
  return t('friends.listTab.lastSeenText.daysAgo', { count: Math.floor(diffMinutes / 1440) })
}
</script>

<style scoped>
.friends-content {
  width: 100%;
}

.demo-disabled-text {
  margin: -0.5rem 0 1rem 0;
  font-size: 0.9rem;
}

.demo-disabled-text--inline {
  margin: 0 0 0.5rem 0;
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
  background: var(--gp-surface-card);
  border: 1px solid var(--gp-border);
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

/* Sharing Status Section (What friend shares with you) */
.friend-sharing-status {
  margin-top: 1rem;
  padding: 0.75rem;
  background: var(--gp-surface-muted);
  border: 1px solid var(--gp-border);
  border-radius: var(--gp-radius-small);
}

.section-header {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  font-size: 0.85rem;
  font-weight: 600;
  color: var(--gp-text-primary);
  margin-bottom: 0.75rem;
}

.section-header i {
  font-size: 0.9rem;
  color: var(--gp-primary);
}

.sharing-status-items {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.status-item {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.5rem;
  background: var(--gp-surface-card);
  border: 1px solid var(--gp-border);
  border-radius: var(--gp-radius-small);
}

.status-item i {
  font-size: 1rem;
}

.status-label {
  flex: 1;
  font-size: 0.85rem;
  color: var(--gp-text-primary);
}

/* Permissions Section (What you share with friend) */
.friend-permissions {
  margin-top: 0.75rem;
  padding: 0.75rem;
  background: var(--gp-surface-muted);
  border: 1px solid var(--gp-border);
  border-radius: var(--gp-radius-small);
}

.permission-item {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  padding: 0.5rem;
  background: var(--gp-surface-card);
  border: 1px solid var(--gp-border);
  border-radius: var(--gp-radius-small);
  margin-top: 0.5rem;
}

.permission-item:first-of-type {
  margin-top: 0;
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
  margin-top: 1rem;
  padding-top: 1rem;
  border-top: 1px solid var(--gp-border);
}

/* Responsive Design */
@media (max-width: 768px) {
  .friends-grid {
    grid-template-columns: 1fr;
  }
}
</style>
