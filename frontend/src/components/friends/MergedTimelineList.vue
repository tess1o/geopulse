<template>
  <div class="merged-timeline-list">
    <div class="list-header">
      <h3>Timeline Items</h3>
      <Badge :value="timelineItems.length" severity="info" />
    </div>

    <div v-if="loading" class="loading-state">
      <ProgressSpinner size="small" />
      <p>Loading timeline items...</p>
    </div>

    <div v-else-if="timelineItems.length === 0" class="empty-state">
      <i class="pi pi-inbox"></i>
      <p>No timeline data for selected date range</p>
    </div>

    <div v-else class="timeline-items">
      <template v-for="(item, index) in displayedItems" :key="`${item.userId}-${item.timestamp}-${index}`">
        <!-- Stay Card -->
        <FriendStayCard
          v-if="item.type === 'stay'"
          :item="item"
          :user-name="item.userFullName"
          :user-avatar="item.userAvatar"
          :user-color="item.userColor"
          @click="handleItemClick"
        />

        <!-- Trip Card -->
        <FriendTripCard
          v-else-if="item.type === 'trip'"
          :item="item"
          :user-name="item.userFullName"
          :user-avatar="item.userAvatar"
          :user-color="item.userColor"
          @click="handleItemClick"
        />

        <!-- Data Gap Item -->
        <div
          v-else-if="item.type === 'dataGap'"
          class="timeline-item timeline-item--gap"
          :style="{ '--user-color': item.userColor }"
        >
          <div class="gap-header">
            <Avatar
              :image="item.userAvatar || '/avatars/avatar1.png'"
              size="small"
              shape="circle"
            />
            <span class="gap-user-name">{{ item.userFullName }}</span>
            <span class="gap-time">{{ formatTime(item.timestamp) }}</span>
          </div>
          <div class="gap-content">
            <i class="pi pi-exclamation-triangle"></i>
            <span>No data for {{ formatDuration(item.durationSeconds) }}</span>
          </div>
        </div>
      </template>

      <!-- Load More Button -->
      <div v-if="timelineItems.length > displayLimit" class="load-more">
        <Button
            label="Load More"
            icon="pi pi-plus"
            @click="loadMore"
            size="small"
            outlined
        />
        <span class="showing-count">
          Showing {{ displayLimit }} of {{ timelineItems.length }} items
        </span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { useTimezone } from '@/composables/useTimezone'
import { formatDuration } from '@/utils/calculationsHelpers'
import ProgressSpinner from 'primevue/progressspinner'
import Button from 'primevue/button'
import Avatar from 'primevue/avatar'
import Badge from 'primevue/badge'
import FriendStayCard from './FriendStayCard.vue'
import FriendTripCard from './FriendTripCard.vue'

const timezone = useTimezone()

const props = defineProps({
  timelineItems: {
    type: Array,
    default: () => []
  },
  loading: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['item-click'])

const displayLimit = ref(50)

const displayedItems = computed(() => {
  return props.timelineItems.slice(0, displayLimit.value)
})

function loadMore() {
  displayLimit.value = Math.min(displayLimit.value + 50, props.timelineItems.length)
}

function handleItemClick(item) {
  emit('item-click', item)
}

function formatTime(timestamp) {
  const date = timezone.fromUtc(timestamp)
  const now = timezone.now()
  const diffHours = now.diff(date, 'hour')

  if (diffHours < 1) {
    const diffMinutes = now.diff(date, 'minute')
    return diffMinutes < 1 ? 'Just now' : `${diffMinutes}m ago`
  }
  if (diffHours < 24) {
    return `${Math.floor(diffHours)}h ago`
  }

  return date.format('MMM D, h:mm A')
}
</script>

<style scoped>
.merged-timeline-list {
  background: var(--gp-surface-white);
  border: 1px solid var(--gp-border-light);
  border-radius: var(--gp-radius-medium);
  padding: 1rem;
  display: flex;
  flex-direction: column;
  max-height: 600px;
}

.list-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 1rem;
  padding-bottom: 0.75rem;
  border-bottom: 1px solid var(--gp-border-light);
}

.list-header h3 {
  margin: 0;
  font-size: 1rem;
  font-weight: 600;
  color: var(--gp-text-primary);
}

.loading-state,
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 2rem;
  color: var(--gp-text-secondary);
  gap: 1rem;
}

.empty-state i {
  font-size: 2rem;
}

.timeline-items {
  flex: 1;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
}

/* Data Gap Item */
.timeline-item--gap {
  background: var(--gp-surface-white);
  border: 2px dashed var(--user-color);
  border-radius: var(--gp-radius-medium);
  padding: 0.75rem;
  margin-bottom: 0.75rem;
  opacity: 0.7;
}

.gap-header {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  margin-bottom: 0.5rem;
  padding-bottom: 0.5rem;
  border-bottom: 1px solid var(--gp-border-light);
}

.gap-user-name {
  font-size: 0.875rem;
  font-weight: 600;
  color: var(--user-color);
  flex: 1;
}

.gap-time {
  font-size: 0.75rem;
  color: var(--gp-text-secondary);
}

.gap-content {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  font-size: 0.875rem;
  color: var(--gp-text-secondary);
  font-style: italic;
}

.gap-content i {
  color: var(--gp-warning);
  font-size: 1rem;
}

.load-more {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.5rem;
  padding-top: 1rem;
  margin-top: 1rem;
  border-top: 1px solid var(--gp-border-light);
}

.showing-count {
  font-size: 0.8rem;
  color: var(--gp-text-secondary);
}

/* Dark mode */
.p-dark .merged-timeline-list {
  background: var(--gp-surface-dark);
  border-color: var(--gp-border-dark);
}

.p-dark .list-header {
  border-bottom-color: var(--gp-border-dark);
}

.p-dark .timeline-item--gap {
  background: var(--gp-surface-dark);
}

.p-dark .gap-header {
  border-bottom-color: var(--gp-border-dark);
}

/* Mobile optimizations */
@media (max-width: 768px) {
  .merged-timeline-list {
    padding: 0.75rem;
  }

  .gap-user-name {
    font-size: 0.8rem;
  }

  .gap-time {
    font-size: 0.7rem;
  }

  .gap-content {
    font-size: 0.8rem;
  }
}
</style>
