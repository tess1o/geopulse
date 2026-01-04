<template>
  <div
    class="friend-timeline-card"
    :class="cardClass"
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

    <div class="card-subtitle">
      <slot name="subtitle"></slot>
    </div>

    <div class="card-content">
      <slot name="content"></slot>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
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
  },
  variant: {
    type: String,
    default: 'default',
    validator: (value) => ['stay', 'trip', 'gap', 'default'].includes(value)
  }
})

const emit = defineEmits(['click'])

const timezone = useTimezone()

const cardClass = computed(() => ({
  [`friend-timeline-card--${props.variant}`]: props.variant !== 'default'
}))

const handleClick = () => {
  emit('click', props.item)
}
</script>

<style scoped>
/* Base Card Styles */
.friend-timeline-card {
  cursor: pointer;
  transition: all 0.2s ease;
  border-radius: var(--gp-radius-medium);
  border-left: 4px solid var(--user-color);
  border-top: 1px solid var(--gp-border-light);
  border-right: 1px solid var(--gp-border-light);
  border-bottom: 1px solid var(--gp-border-light);
  padding: var(--gp-spacing-sm) var(--gp-spacing-md);
  margin-bottom: var(--gp-spacing-sm);
}

.friend-timeline-card:hover {
  transform: translateY(-2px);
  box-shadow: var(--gp-shadow-medium);
}

/* Card Header - Shared */
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

/* Subtitle Section - Slotted */
.card-subtitle {
  font-size: 0.875rem;
  color: var(--gp-text-primary);
  margin-bottom: var(--gp-spacing-xs);
  line-height: 1.4;
}

/* Content Section - Slotted */
.card-content {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
}

/* Variant Backgrounds */
.friend-timeline-card--stay {
  background: var(--gp-timeline-purple-light);
}

.friend-timeline-card--trip {
  background: var(--gp-timeline-orange-light);
}

.friend-timeline-card--gap {
  background: var(--gp-surface-white);
  border-style: dashed;
  opacity: 0.7;
}

/* Dark mode */
.p-dark .friend-timeline-card--stay {
  background: var(--gp-timeline-purple);
  border-color: var(--gp-border-medium);
  border-left-color: var(--user-color);
}

.p-dark .friend-timeline-card--trip {
  background: var(--gp-timeline-orange);
  border-color: var(--gp-border-medium);
  border-left-color: var(--user-color);
}

.p-dark .friend-timeline-card--gap {
  background: var(--gp-surface-dark);
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

  .card-subtitle {
    font-size: 0.8rem;
  }
}
</style>
