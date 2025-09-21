<template>
  <Card 
    class="timeline-card timeline-card--overnight-data-gap"
    @click="handleClick"
  >
    <template #title>
      <p class="timeline-timestamp">
        🕐 {{ formatContinuationText(dataGapItem.startTime, currentDate) }}
      </p>
    </template>

    <template #subtitle>
      <div class="timeline-subtitle">
        <span class="gap-label">❓ Data Gap - Unknown Activity</span>
      </div>
    </template>

    <template #content>
      <div class="overnight-data-gap-content">
        <p class="duration-detail">
          ⏱️ Total duration:
          <span class="duration-value">{{ formatSmartDuration(dataGapItem.durationSeconds) }}</span>
        </p>
        <p class="duration-detail">
          ⏱️ On this day:
          <span class="duration-value">{{ formatOnThisDayDuration(dataGapItem, currentDate) }}</span>
        </p>
        <p class="end-time-detail">
          🔄 Final End Time: <span class="time-value">{{ formatEndTimeWithDate(dataGapItem.endTime, currentDate) }}</span>
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

const formatContinuationText = (startTime, currentDateString) => {
  return timezone.formatContinuationText(startTime, currentDateString);
};

const formatOnThisDayDuration = (dataGapItem, currentDateString) => {
  return timezone.formatOnThisDayDuration(dataGapItem, currentDateString, 'dataGap');
};

const formatEndTimeWithDate = (endTime, currentDateString) => {
  const endDate = timezone.fromUtc(endTime);
  const currentDate = timezone.fromUtc(currentDateString);

  if (!timezone.isSameDay(endTime, currentDateString)) {
    const format = endDate.year() === currentDate.year() ? 'MMM D' : 'MMM D, YYYY';
    return `${endDate.format(format)}, ${timezone.formatTime(endTime)}`;
  } else {
    return timezone.formatTime(endTime);
  }
};

const formatSmartDuration = (seconds) => {
  const totalSeconds = seconds || props.dataGapItem.durationSeconds || 0;
  const totalMinutes = Math.floor(totalSeconds / 60);
  const hoursFloat = totalMinutes / 60;
  const nearFullDay = Math.abs(hoursFloat - 24) <= (5 / 60);
  const isExactFullDay = totalMinutes % (24 * 60) === 0;

  if (nearFullDay || isExactFullDay) {
    const days = Math.floor(totalMinutes / (24 * 60));
    if (days === 1) return "24 hours";
    if (days > 1) return `${days} days`;
  }
  return formatDuration(totalSeconds);
};

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