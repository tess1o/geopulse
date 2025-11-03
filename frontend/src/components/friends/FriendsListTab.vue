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
import { useTimezone } from '@/composables/useTimezone'

const timezone = useTimezone()

defineProps({
  friends: {
    type: Array,
    default: () => []
  }
})

defineEmits(['invite-friend', 'show-on-map', 'delete-friend'])

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
