<template>
  <div
    class="friend-timeline-card friend-timeline-card--stay"
    :style="{ '--user-color': userColor }"
    @click="handleClick"
  >
    <div class="card-header">
      <div class="user-info">
        <Avatar
          :image="userAvatar || '/avatars/avatar1.png'"
          size="small"
          shape="circle"
        />
        <span class="user-name">{{ userName }}</span>
      </div>
      <span class="relative-time">{{ relativeTime }}</span>
    </div>

    <div class="stay-title">
      <i class="pi pi-map-marker"></i>
      <span class="location-name">{{ item.locationName || 'Unknown Location' }}</span>
    </div>

    <div class="stay-details">
      <div class="detail-item">
        <i class="pi pi-clock"></i>
        <span>{{ formatDuration(item.stayDuration) }}</span>
      </div>
      <div v-if="item.city" class="detail-item detail-item--secondary">
        <i class="pi pi-building"></i>
        <span>{{ item.city }}</span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { formatDuration } from '@/utils/calculationsHelpers'
import { useTimezone } from '@/composables/useTimezone'
import Avatar from 'primevue/avatar'

const props = defineProps({
  item: {
    type: Object,
    required: true
  },
  userName: {
    type: String,
    required: true
  },
  userAvatar: {
    type: String,
    default: null
  },
  userColor: {
    type: String,
    required: true
  }
})

const emit = defineEmits(['click'])

const timezone = useTimezone()

const relativeTime = computed(() => {
  if (!props.item.timestamp) return ''

  const date = timezone.fromUtc(props.item.timestamp)
  const now = timezone.now()
  const diffMinutes = now.diff(date, 'minute')
  const diffHours = now.diff(date, 'hour')
  const diffDays = now.diff(date, 'day')

  if (diffMinutes < 1) return 'Just now'
  if (diffMinutes < 60) return `${diffMinutes}m ago`
  if (diffHours < 24) return `${diffHours}h ago`
  if (diffDays < 7) return `${diffDays}d ago`

  return date.format('MMM D')
})

const handleClick = () => {
  emit('click', props.item)
}
</script>

<style scoped>
.friend-timeline-card {
  cursor: pointer;
  transition: all 0.2s ease;
  border-radius: var(--gp-radius-medium);
  border-left: 4px solid var(--user-color);
  border-top: 1px solid var(--gp-border-light);
  border-right: 1px solid var(--gp-border-light);
  border-bottom: 1px solid var(--gp-border-light);
  background: var(--gp-surface-white);
  padding: var(--gp-spacing-sm) var(--gp-spacing-md);
  margin-bottom: var(--gp-spacing-sm);
}

.friend-timeline-card:hover {
  transform: translateY(-2px);
  box-shadow: var(--gp-shadow-medium);
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: var(--gp-spacing-xs);
}

.user-info {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  flex: 1;
  min-width: 0;
}

.user-name {
  font-size: 0.875rem;
  font-weight: 600;
  color: var(--user-color);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.relative-time {
  font-size: 0.75rem;
  color: var(--gp-text-secondary);
  white-space: nowrap;
}

.stay-title {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  margin-bottom: var(--gp-spacing-xs);
}

.stay-title i {
  color: var(--user-color);
  font-size: 0.95rem;
}

.location-name {
  font-size: 0.9rem;
  font-weight: 600;
  color: var(--gp-text-primary);
  line-height: 1.3;
}

.stay-details {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
}

.detail-item {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  font-size: 0.875rem;
  color: var(--gp-text-primary);
  line-height: 1.3;
}

.detail-item i {
  color: var(--user-color);
  font-size: 0.875rem;
  width: 14px;
  flex-shrink: 0;
}

.detail-item--secondary {
  color: var(--gp-text-secondary);
  font-size: 0.8rem;
}

.detail-item--secondary i {
  opacity: 0.7;
}

/* Dark mode */
.p-dark .friend-timeline-card {
  background: var(--gp-surface-dark);
  border-color: var(--gp-border-medium);
  border-left-color: var(--user-color);
}

.p-dark .location-name,
.p-dark .detail-item {
  color: var(--gp-text-primary);
}

.p-dark .friend-timeline-card:hover {
  box-shadow: var(--gp-shadow-medium);
}

/* Mobile optimizations */
@media (max-width: 768px) {
  .friend-timeline-card {
    padding: var(--gp-spacing-xs) var(--gp-spacing-sm);
    margin-bottom: var(--gp-spacing-xs);
  }

  .user-name {
    font-size: 0.8rem;
  }

  .relative-time {
    font-size: 0.7rem;
  }

  .location-name {
    font-size: 0.875rem;
  }

  .detail-item {
    font-size: 0.8rem;
  }
}
</style>
