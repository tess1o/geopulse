<template>
  <Card 
    class="timeline-card timeline-card--overnight-trip"
    @click="handleClick"
  >
    <template #title>
      <p class="timeline-timestamp">
        🕐 {{ formatContinuationText(tripItem.timestamp, currentDate) }}
      </p>
    </template>

    <template #subtitle>
      <div class="timeline-subtitle">
        🚗 Trip - {{ tripItem.movementType }}
      </div>
    </template>

    <template #content>
      <div class="overnight-trip-content">
        <p class="duration-detail">
          ⏱️ Total duration:
          <span class="duration-value">{{ formatDuration(tripItem.tripDuration) }}</span>
        </p>
        <p class="duration-detail">
          ⏱️ On this day:
          <span class="duration-value">{{ formatOnThisDayDuration(tripItem, currentDate) }}</span>
        </p>
        <p v-if="tripItem.distanceMeters" class="distance-detail">
          📏 Distance: <span class="distance-value">{{ formatDistance(tripItem.distanceMeters) }}</span>
        </p>
      </div>
    </template>
  </Card>
</template>

<script setup>
import { computed } from 'vue';
import { formatDuration, formatDistance } from '@/utils/calculationsHelpers';
import { useTimezone } from '@/composables/useTimezone';

const props = defineProps({
  tripItem: {
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

const formatOnThisDayDuration = (tripItem, currentDateString) => {
  return timezone.formatOnThisDayDuration(tripItem, currentDateString, 'trip');
};

const handleClick = () => {
  emit('click', props.tripItem);
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
  
  .overnight-trip-content {
    margin-top: var(--gp-spacing-xs);
  }
  
  .duration-detail,
  .distance-detail {
    margin: 2px 0;
    font-size: 0.8rem;
  }
}

.timeline-card:hover {
  transform: translateY(-2px);
  box-shadow: var(--gp-shadow-medium);
}

.timeline-card--overnight-trip {
  background-color: var(--gp-timeline-green-light);
  border-left: 4px solid var(--gp-success);
}

.timeline-timestamp {
  color: var(--gp-success-dark);
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

.overnight-trip-content {
  margin-top: var(--gp-spacing-xs);
  color: var(--gp-text-primary);
}

.duration-detail,
.distance-detail {
  margin: var(--gp-spacing-xs) 0;
  color: var(--gp-text-primary);
  font-size: 0.875rem;
  line-height: 1.3;
}

.duration-detail .duration-value,
.distance-detail .distance-value {
  font-weight: 700;
  color: var(--gp-success-dark);
}

/* Dark mode adjustments */
.p-dark .timeline-card {
  border-color: var(--gp-border-medium);
}

.p-dark .timeline-card--overnight-trip {
  background-color: var(--gp-timeline-green);
  border-left: 4px solid var(--gp-success);
}

.p-dark .timeline-timestamp,
.p-dark .duration-detail .duration-value,
.p-dark .distance-detail .distance-value {
  color: var(--gp-success);
}

.p-dark .timeline-subtitle,
.p-dark .overnight-trip-content,
.p-dark .duration-detail,
.p-dark .distance-detail {
  color: var(--gp-text-primary);
}

.p-dark .timeline-card:hover {
  box-shadow: var(--gp-shadow-medium);
}
</style>