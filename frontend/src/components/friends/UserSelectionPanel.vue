<template>
  <div class="user-selection-panel">
    <div class="panel-header">
      <h3>{{ t('friends.selection.title') }}</h3>
      <div class="selection-controls">
        <Button :label="t('friends.filters.all')" outlined size="small" @click="$emit('select-all')" />
        <Button :label="t('friends.filters.none')" outlined size="small" @click="$emit('deselect-all')" />
      </div>
    </div>

    <div class="user-list">
      <div
          v-for="user in availableUsers"
          :key="user.userId"
          class="user-item"
          :class="{ 'is-me': user.userId === requestingUserId }"
      >
        <Checkbox
            :model-value="selectedUserIds.has(user.userId)"
            :binary="true"
            @update:model-value="$emit('toggle-user', user.userId)"
        />
        <div class="color-indicator" :style="{ backgroundColor: user.color }"></div>
        <Avatar
            :image="user.avatar || '/avatars/avatar1.png'"
            size="small"
            class="user-avatar"
        />
        <div class="user-info">
          <span class="user-name">
            {{ user.fullName }}
            <span v-if="user.userId === requestingUserId" class="you-label">{{ t('friends.selection.youLabel') }}</span>
          </span>
        </div>
        <Badge :value="user.itemCount" severity="info" class="item-badge" />
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { storeToRefs } from 'pinia'
import { useFriendsTimelineStore } from '@/stores/friendsTimeline'
import Checkbox from 'primevue/checkbox'
import Button from 'primevue/button'
import Avatar from 'primevue/avatar'
import Badge from 'primevue/badge'

const { t } = useI18n()
const friendsTimelineStore = useFriendsTimelineStore()
const { selectedUserIds } = storeToRefs(friendsTimelineStore)

const props = defineProps({
  availableUsers: {
    type: Array,
    default: () => []
  },
  selectedUserIds: {
    type: Set,
    default: () => new Set()
  }
})

defineEmits(['toggle-user', 'select-all', 'deselect-all'])

const requestingUserId = computed(() => friendsTimelineStore.requestingUserId)
</script>

<style scoped>
.user-selection-panel {
  background: var(--gp-surface-card);
  border: 1px solid var(--gp-border);
  border-radius: var(--gp-radius-medium);
  padding: 1rem;
}

.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 1rem;
  padding-bottom: 0.75rem;
  border-bottom: 1px solid var(--gp-border);
}

.panel-header h3 {
  margin: 0;
  font-size: 1rem;
  font-weight: 600;
  color: var(--gp-text-primary);
}

.selection-controls {
  display: flex;
  gap: 0.5rem;
}

/* Hide entire header on mobile to save space */
@media (max-width: 768px) {
  .panel-header {
    display: none;
  }
}

.user-list {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.user-item {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  padding: 0.75rem;
  border-radius: var(--gp-radius-small);
  background: var(--gp-surface-muted);
  transition: background-color 0.2s;
}

.user-item:hover {
  background: var(--gp-surface-hover);
}

.user-item.is-me {
  background: var(--gp-primary-light);
  border: 1px solid var(--gp-primary);
}

.color-indicator {
  width: 16px;
  height: 16px;
  border-radius: 50%;
  flex-shrink: 0;
  border: 2px solid white;
  box-shadow: 0 0 0 1px var(--gp-border-medium);
}

.user-avatar {
  flex-shrink: 0;
}

.user-info {
  flex: 1;
  min-width: 0;
}

.user-name {
  font-size: 0.9rem;
  font-weight: 500;
  color: var(--gp-text-primary);
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.you-label {
  font-size: 0.75rem;
  font-weight: 600;
  color: var(--gp-primary);
}

.item-badge {
  flex-shrink: 0;
}
</style>
