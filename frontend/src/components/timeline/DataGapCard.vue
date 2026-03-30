<template>
  <Card 
    class="timeline-card timeline-card--data-gap"
    @click="handleClick"
  >
    <template #title>
      <p class="timeline-timestamp">
        🕐 {{ formattedStartTime }}
      </p>
    </template>

    <template #subtitle>
      <div class="timeline-subtitle">
        ❓ Data Gap - Unknown Activity
      </div>
    </template>

    <template #content>
      <div class="data-gap-content">
        <p class="gap-detail">
          📅 End Time:
          <span class="detail-value">{{ formattedEndTime }}</span>
        </p>
        <p class="gap-detail">
          ⏱️ Duration:
          <span class="detail-value">{{ formatDuration(dataGapItem.durationSeconds) }}</span>
        </p>

        <button
          v-if="dataGapItem.id && !dataGapItem.ongoing"
          class="convert-gap-btn"
          title="Convert this data gap to a stay"
          @click.stop="handleConvertToStay"
        >
          Convert to stay
        </button>

        <!-- Help section for gap configuration guidance -->
        <DataGapHelpSection :duration-seconds="dataGapItem.durationSeconds" />
      </div>
    </template>
  </Card>
</template>

<script setup>
import { computed } from 'vue'
import { formatDuration } from '@/utils/calculationsHelpers'
import { useTimezone } from '@/composables/useTimezone'
import DataGapHelpSection from './DataGapHelpSection.vue'

const props = defineProps({
  dataGapItem: {
    type: Object,
    required: true
  }
})

const emit = defineEmits(['click', 'convert-to-stay'])

const timezone = useTimezone()

const handleClick = () => {
  emit('click', props.dataGapItem)
}

const handleConvertToStay = () => {
  emit('convert-to-stay', props.dataGapItem)
}

const formattedStartTime = computed(() => {
  if (!props.dataGapItem.startTime) return '';
  return `${timezone.formatDateDisplay(props.dataGapItem.startTime)} ${timezone.formatTime(props.dataGapItem.startTime)}`
})

const formattedEndTime = computed(() => {
  if (!props.dataGapItem.endTime) return '';
  return `${timezone.formatDateDisplay(props.dataGapItem.endTime)} ${timezone.formatTime(props.dataGapItem.endTime)}`
})
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
  
  .data-gap-content {
    margin-top: var(--gp-spacing-xs);
  }
  
  .gap-detail {
    margin: 2px 0;
    font-size: 0.8rem;
  }
}

.timeline-card:hover {
  transform: translateY(-2px);
  box-shadow: var(--gp-shadow-medium);
}

.timeline-card--data-gap {
  background-color: var(--gp-timeline-orange-light);
  border-left: 4px solid var(--gp-warning);
}

.timeline-timestamp {
  color: var(--gp-warning);
  font-weight: 600;
  font-size: 0.95rem;
  margin: 0;
  line-height: 1.2;
}

.timeline-subtitle {
  margin: var(--gp-spacing-xs) 0 0 0;
  color: var(--gp-warning);
  font-size: 0.9rem;
  font-weight: 700;
  line-height: 1.3;
}

.data-gap-content {
  margin-top: var(--gp-spacing-xs);
  color: var(--gp-text-primary);
}

.gap-detail {
  margin: var(--gp-spacing-xs) 0;
  color: var(--gp-text-primary);
  font-size: 0.875rem;
  line-height: 1.3;
}

.gap-detail .detail-value {
  font-weight: 700;
  color: var(--gp-warning);
}

.convert-gap-btn {
  margin-top: var(--gp-spacing-xs);
  border: none;
  background: transparent;
  color: var(--gp-warning);
  font-weight: 700;
  font-size: 0.8rem;
  cursor: pointer;
  text-decoration: underline;
  padding: 0;
}

/* Dark mode adjustments */
.p-dark .timeline-card {
  border-color: var(--gp-border-medium);
}

.p-dark .timeline-card--data-gap {
  background-color: var(--gp-timeline-orange);
  border-left: 4px solid var(--gp-warning);
}

.p-dark .timeline-timestamp,
.p-dark .timeline-subtitle {
  color: var(--gp-warning);
}

.p-dark .gap-detail .detail-value {
  color: var(--gp-warning);
}

.p-dark .timeline-subtitle,
.p-dark .data-gap-content,
.p-dark .gap-detail {
  color: var(--gp-text-primary);
}

.p-dark .data-gap-content {
  color: var(--gp-text-primary);
}

.p-dark .timeline-card:hover {
  box-shadow: var(--gp-shadow-medium);
}
</style>
