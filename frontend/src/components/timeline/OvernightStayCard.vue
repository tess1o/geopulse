<template>
  <Card 
    class="timeline-card timeline-card--overnight-stay"
    @click="handleClick"
  >
    <template #title>
      <p class="timeline-timestamp">
        🕐 {{ formatContinuationText(stayItem.timestamp, currentDate) }}
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
      <div class="overnight-stay-content">
        <p class="duration-detail">
          ⏱️ Total duration:
          <span class="duration-value">{{ formatDuration(stayItem.stayDuration) }}</span>
        </p>
        <p class="duration-detail">
          ⏱️ On this day:
          <span class="duration-value">{{ formatOnThisDayDuration(stayItem, currentDate) }}</span>
        </p>
      </div>
    </template>
  </Card>
</template>

<script setup>
import { computed } from 'vue';
import { formatDuration } from '@/utils/calculationsHelpers';
import { useTimezone } from '@/composables/useTimezone';

const props = defineProps({
  stayItem: {
    type: Object,
    required: true
  },
  currentDate: {
    type: String,
    required: true
  }
});

const emit = defineEmits(['click']);

const timezone = useTimezone();

const formatContinuationText = (startTime, currentDateString) => {
  return timezone.formatContinuationText(startTime, currentDateString);
};

const formatOnThisDayDuration = (stayItem, currentDateString) => {
  return timezone.formatOnThisDayDuration(stayItem, currentDateString, 'stay');
};

const handleClick = () => {
  emit('click', props.stayItem);
};
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
  
  .overnight-stay-content {
    margin-top: var(--gp-spacing-xs);
  }
  
  .duration-detail {
    margin: 2px 0;
    font-size: 0.8rem;
  }
}

.timeline-card:hover {
  transform: translateY(-2px);
  box-shadow: var(--gp-shadow-medium);
}

.timeline-card--overnight-stay {
  background-color: var(--gp-timeline-purple-light);
  border-left: 4px solid var(--gp-primary-dark);
}

.timeline-timestamp {
  color: var(--gp-primary-dark);
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

.overnight-stay-content {
  margin-top: var(--gp-spacing-xs);
  color: var(--gp-text-primary);
}

.duration-detail {
  margin: var(--gp-spacing-xs) 0;
  color: var(--gp-text-primary);
  font-size: 0.875rem;
  line-height: 1.3;
}

.duration-detail .duration-value {
  font-weight: 700;
  color: var(--gp-primary-dark);
}

/* Dark mode adjustments */
.p-dark .timeline-card {
  border-color: var(--gp-border-medium);
}

.p-dark .timeline-card--overnight-stay {
  background-color: var(--gp-timeline-purple);
  border-left: 4px solid var(--gp-primary);
}

.p-dark .timeline-timestamp,
.p-dark .duration-detail .duration-value {
  color: var(--gp-primary);
}

.p-dark .location-name {
  color: var(--gp-primary);
}

.p-dark .timeline-subtitle,
.p-dark .overnight-stay-content,
.p-dark .duration-detail {
  color: var(--gp-text-primary);
}

.p-dark .timeline-card:hover {
  box-shadow: var(--gp-shadow-medium);
}
</style>