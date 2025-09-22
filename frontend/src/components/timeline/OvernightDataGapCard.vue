<template>
  <Card 
    class="timeline-card timeline-card--overnight-data-gap"
    @click="handleClick"
  >
    <template #title>
      <p class="timeline-timestamp">
        🕐 {{ getTimestampText() }}
      </p>
    </template>

    <template #subtitle>
      <div class="timeline-subtitle">
        <span class="gap-label">📵 Data Gap - Unknown Activity</span>
      </div>
    </template>

    <template #content>
      <div class="overnight-data-gap-content">
        <p class="duration-detail">
          📈 Total duration: <span class="duration-value">{{ getGapDuration() }}</span>
        </p>
        <p class="duration-detail">
          ⏱️ On this day:
          <span class="duration-value"> {{ getOnThisDayText() }}</span>
        </p>
      </div>
    </template>
  </Card>
</template>

<script setup>
import { useTimezone } from '@/composables/useTimezone';
import { formatDurationSmart } from '@/utils/calculationsHelpers';

const props = defineProps({
  dataGapItem: {
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

// Methods
const getTimestampText = () => {
  return timezone.getOvernightTimestampText(props.dataGapItem, props.currentDate)
}

const getGapDuration = () => {
  const startTime = timezone.fromUtc(props.dataGapItem.startTime)
  const endTime = timezone.fromUtc(props.dataGapItem.endTime)
  const durationSeconds = endTime.diff(startTime, 'second')
  return formatDurationSmart(durationSeconds)
}

const getOnThisDayText = () => {
  return timezone.getOvernightOnThisDayText(props.dataGapItem, props.currentDate)
}

const handleClick = () => {
  emit('click', props.dataGapItem);
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
  
  .overnight-data-gap-content {
    margin-top: var(--gp-spacing-xs);
  }
  
  .duration-detail,
  .end-time-detail {
    margin: 2px 0;
    font-size: 0.8rem;
  }
}

.timeline-card:hover {
  transform: translateY(-2px);
  box-shadow: var(--gp-shadow-medium);
}

.timeline-card--overnight-data-gap {
  background-color: var(--gp-timeline-orange-light);
  border-left: 4px solid var(--gp-warning-dark);
}

.timeline-timestamp {
  color: var(--gp-warning-dark);
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
  font-weight: 600;
}

.gap-label {
  color: var(--gp-warning);
  font-weight: 700;
}

.overnight-data-gap-content {
  margin-top: var(--gp-spacing-xs);
  color: var(--gp-text-primary);
}

.duration-detail,
.end-time-detail {
  margin: var(--gp-spacing-xs) 0;
  color: var(--gp-text-primary);
  font-size: 0.875rem;
  line-height: 1.3;
}

.duration-detail .duration-value,
.end-time-detail .time-value {
  font-weight: 700;
  color: var(--gp-warning-dark);
}

/* Dark mode adjustments */
.p-dark .timeline-card {
  border-color: var(--gp-border-medium);
}

.p-dark .timeline-card--overnight-data-gap {
  background-color: var(--gp-timeline-orange);
  border-left: 4px solid var(--gp-warning-dark);
}

.p-dark .timeline-timestamp,
.p-dark .duration-detail .duration-value,
.p-dark .end-time-detail .time-value {
  color: var(--gp-warning);
}

.p-dark .gap-label {
  color: var(--gp-warning);
}

.p-dark .timeline-subtitle,
.p-dark .overnight-data-gap-content,
.p-dark .duration-detail,
.p-dark .end-time-detail {
  color: var(--gp-text-primary);
}

.p-dark .timeline-card:hover {
  box-shadow: var(--gp-shadow-medium);
}
</style>