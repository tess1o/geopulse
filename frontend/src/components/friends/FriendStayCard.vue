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
      <span class="relative-time">{{ timezone.format(props.item.timestamp) }}</span>
    </div>

    <div class="stay-subtitle">
      🏠 Stayed at
      <span class="location-name">{{ item.locationName || 'Unknown Location' }}</span>
    </div>

    <div class="stay-content">
      <p class="stay-detail">
        For <span class="font-bold">{{ formatDuration(item.stayDuration) }}</span>
      </p>
    </div>
  </div>
</template>

<script setup>
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
  background: var(--gp-timeline-purple-light);
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

.stay-subtitle {
  font-size: 0.875rem;
  color: var(--gp-text-primary);
  margin-bottom: var(--gp-spacing-xs);
  line-height: 1.4;
}

.location-name {
  font-weight: 600;
  color: var(--gp-text-primary);
}

.stay-content {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
}

.stay-detail {
  font-size: 0.875rem;
  color: var(--gp-text-primary);
  margin: 0;
  line-height: 1.4;
}

.stay-detail--secondary {
  color: var(--gp-text-secondary);
  font-size: 0.8rem;
}

.font-bold {
  font-weight: 600;
}

/* Dark mode */
.p-dark .friend-timeline-card {
  background: var(--gp-timeline-purple);
  border-color: var(--gp-border-medium);
  border-left-color: var(--user-color);
}

.p-dark .location-name,
.p-dark .stay-detail {
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

  .stay-subtitle {
    font-size: 0.8rem;
  }

  .stay-detail {
    font-size: 0.8rem;
  }
}
</style>
