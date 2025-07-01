<template>
  <Card 
    class="timeline-card timeline-card--stay"
    @click="handleClick"
  >
    <template #title>
      <p class="timeline-timestamp">
        🕐 {{ formatDate(stayItem.timestamp) }}
      </p>
    </template>

    <template #subtitle>
      <div class="timeline-subtitle">
        🏠 Stayed at
        <span class="location-name">
          {{ stayItem.locationName }}
        </span>
      </div>
    </template>

    <template #content>
      <div class="stay-content">
        <span>For </span>
        <span class="duration-text">
          {{ formatDuration(stayItem.stayDuration) }}
        </span>
      </div>
    </template>
  </Card>
</template>

<script setup>
import { formatDate } from '@/utils/dateHelpers'
import { formatDuration } from '@/utils/calculationsHelpers'

const props = defineProps({
  stayItem: {
    type: Object,
    required: true
  }
})

const emit = defineEmits(['click'])

const handleClick = () => {
  emit('click', props.stayItem)
}
</script>

<style scoped>
.timeline-card {
  margin-top: var(--gp-spacing-md);
  cursor: pointer;
  transition: all 0.2s ease;
  border-radius: var(--gp-radius-medium);
  border: 1px solid var(--gp-border-light);
  overflow: hidden;
  padding: var(--gp-spacing-sm) var(--gp-spacing-md);
}

/* Mobile optimizations */
@media (max-width: 768px) {
  .timeline-card {
    margin-top: var(--gp-spacing-sm);
    padding: var(--gp-spacing-xs) var(--gp-spacing-sm);
  }
  
  .timeline-timestamp {
    font-size: 0.875rem;
  }
  
  .timeline-subtitle {
    margin: var(--gp-spacing-xs) 0 0 0;
    font-size: 0.875rem;
  }
  
  .stay-content {
    margin-top: var(--gp-spacing-xs);
    font-size: 0.875rem;
  }
}

.timeline-card:hover {
  transform: translateY(-2px);
  box-shadow: var(--gp-shadow-medium);
}

.timeline-card--stay {
  background-color: var(--gp-timeline-blue-light);
  border-left: 4px solid var(--gp-primary);
}

.timeline-timestamp {
  color: var(--gp-primary);
  font-weight: 600;
  font-size: 0.95rem;
  margin: 0;
  line-height: 1.2;
}

.timeline-subtitle {
  margin: var(--gp-spacing-xs) 0 0 0;
  color: var(--gp-text-primary);
  font-size: 0.9rem;
  line-height: 1.3;
}

.location-name {
  color: var(--gp-primary);
  font-weight: 700;
}

.stay-content {
  margin-top: var(--gp-spacing-xs);
  color: var(--gp-text-primary);
  display: flex;
  align-items: center;
  gap: var(--gp-spacing-xs);
  font-size: 0.9rem;
  line-height: 1.3;
}

.duration-text {
  color: var(--gp-primary);
  font-weight: 700;
}

/* Dark mode adjustments */
.p-dark .timeline-card {
  border-color: var(--gp-border-medium);
}

.p-dark .timeline-card--stay {
  background-color: var(--gp-timeline-blue);
  border-left: 4px solid var(--gp-primary);
}

.p-dark .timeline-timestamp {
  color: var(--gp-primary);
}

.p-dark .location-name,
.p-dark .duration-text {
  color: var(--gp-primary);
}

.p-dark .timeline-subtitle,
.p-dark .stay-content {
  color: var(--gp-text-primary);
}

.p-dark .timeline-card:hover {
  box-shadow: var(--gp-shadow-medium);
}
</style>