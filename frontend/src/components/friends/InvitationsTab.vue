<template>
  <div class="invites-content">
    <!-- Received Invites -->
    <Card v-if="receivedInvites?.length > 0" class="invites-section">
      <template #title>
        <div class="section-header">
          <div class="section-title">
            <i class="pi pi-inbox mr-2"></i>
            Received Invitations
          </div>
          <div class="section-actions">
            <Button
                label="Accept All"
                size="small"
                @click="$emit('accept-all')"
                :loading="bulkActionsLoading.acceptAll"
            />
            <Button
                label="Reject All"
                size="small"
                severity="danger"
                outlined
                @click="$emit('reject-all')"
                :loading="bulkActionsLoading.rejectAll"
            />
          </div>
        </div>
      </template>
      <template #content>
        <div class="invites-list">
          <div v-for="invite in receivedInvites" :key="invite.id" class="invite-item">
            <div class="invite-info">
              <Avatar
                  :image="invite.senderAvatar || '/avatars/avatar1.png'"
                  size="large"
                  class="invite-avatar"
              />
              <div class="invite-details">
                <div class="invite-email">{{ invite.senderName }}</div>
                <div class="invite-date">{{ formatDate(invite.createdAt) }}</div>
              </div>
            </div>

            <div class="invite-actions">
              <Button
                  label="Accept"
                  icon="pi pi-check"
                  size="small"
                  @click="$emit('accept-invite', invite.id)"
                  :loading="inviteActionsLoading[invite.id]?.accept"
              />
              <Button
                  label="Reject"
                  icon="pi pi-times"
                  size="small"
                  severity="danger"
                  outlined
                  @click="$emit('reject-invite', invite.id)"
                  :loading="inviteActionsLoading[invite.id]?.reject"
              />
            </div>
          </div>
        </div>
      </template>
    </Card>

    <!-- Sent Invites -->
    <Card v-if="sentInvites?.length > 0" class="invites-section">
      <template #title>
        <div class="section-header">
          <div class="section-title">
            <i class="pi pi-send mr-2"></i>
            Sent Invitations
          </div>
          <div class="section-actions">
            <Button
                label="Cancel All"
                size="small"
                severity="danger"
                outlined
                @click="$emit('cancel-all')"
                :loading="bulkActionsLoading.cancelAll"
            />
          </div>
        </div>
      </template>
      <template #content>
        <div class="invites-list">
          <div v-for="invite in sentInvites" :key="invite.id" class="invite-item">
            <div class="invite-info">
              <Avatar
                  :image="invite.receiverAvatar || '/avatars/avatar1.png'"
                  size="large"
                  class="invite-avatar"
              />
              <div class="invite-details">
                <div class="invite-email">{{ invite.receiverName }}</div>
                <div class="invite-date">{{ formatDate(invite.createdAt) }}</div>
              </div>
            </div>

            <div class="invite-actions">
              <Badge value="Pending" severity="warning"/>
              <Button
                  label="Cancel"
                  icon="pi pi-times"
                  size="small"
                  severity="danger"
                  outlined
                  @click="$emit('cancel-invite', invite.id)"
                  :loading="inviteActionsLoading[invite.id]?.cancel"
              />
            </div>
          </div>
        </div>
      </template>
    </Card>

    <!-- Empty State for Invites -->
    <div v-if="!receivedInvites?.length && !sentInvites?.length" class="empty-state">
      <div class="empty-icon">
        <i class="pi pi-envelope"></i>
      </div>
      <h3 class="empty-title">No Pending Invitations</h3>
      <p class="empty-description">
        All your invitations have been processed
      </p>
    </div>
  </div>
</template>

<script setup>
import { useTimezone } from '@/composables/useTimezone'

const timezone = useTimezone()

defineProps({
  receivedInvites: {
    type: Array,
    default: () => []
  },
  sentInvites: {
    type: Array,
    default: () => []
  },
  inviteActionsLoading: {
    type: Object,
    default: () => ({})
  },
  bulkActionsLoading: {
    type: Object,
    default: () => ({
      acceptAll: false,
      rejectAll: false,
      cancelAll: false
    })
  }
})

defineEmits([
  'accept-invite',
  'reject-invite',
  'cancel-invite',
  'accept-all',
  'reject-all',
  'cancel-all'
])

const formatDate = (dateString) => {
  return timezone.format(dateString, 'MMM D, YYYY')
}
</script>

<style scoped>
.invites-content {
  display: flex;
  flex-direction: column;
  gap: 2rem;
}

.invites-section {
  background: var(--gp-surface-white);
  border: 1px solid var(--gp-border-light);
  box-shadow: var(--gp-shadow-light);
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 1rem;
}

.section-title {
  display: flex;
  align-items: center;
  font-size: 1.1rem;
  font-weight: 600;
  color: var(--gp-text-primary);
}

.section-actions {
  display: flex;
  gap: 0.5rem;
}

.invites-list {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.invite-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 1rem;
  background: var(--gp-surface-light);
  border-radius: var(--gp-radius-medium);
  border: 1px solid var(--gp-border-light);
}

.invite-info {
  display: flex;
  align-items: center;
  gap: 1rem;
  flex: 1;
  min-width: 0;
}

.invite-avatar {
  flex-shrink: 0;
}

.invite-details {
  flex: 1;
  min-width: 0;
}

.invite-email {
  font-size: 0.9rem;
  color: var(--gp-text-secondary);
  margin-bottom: 0.25rem;
  word-break: break-word;
}

.invite-date {
  font-size: 0.8rem;
  color: var(--gp-text-secondary);
}

.invite-actions {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  flex-shrink: 0;
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

/* Responsive Design */
@media (max-width: 768px) {
  .section-header {
    flex-direction: column;
    align-items: stretch;
  }

  .invite-item {
    flex-direction: column;
    gap: 1rem;
    align-items: stretch;
  }

  .invite-actions {
    justify-content: center;
  }
}
</style>
