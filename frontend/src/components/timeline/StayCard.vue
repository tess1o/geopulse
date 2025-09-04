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
      <div class="stay-content" v-if="!isOvernight">
        <span>For </span>
        <span class="duration-text">
          {{ formatDuration(stayItem.stayDuration) }}
        </span>
      </div>
      <div class="overnight-stay-content" v-else>
        <p class="duration-detail">
          📈 Total duration:
          <span class="duration-value">{{ formatDuration(stayItem.stayDuration) }}</span>
        </p>
        <p class="duration-detail">
          ⏱️ On this day:
          <span class="duration-value">{{ formatOnThisDayDuration(stayItem) }}</span>
        </p>
      </div>
    </template>
  </Card>
</template>

<script setup>
import { computed } from 'vue'
import { formatDate, formatTime } from '@/utils/dateHelpers'
import { formatDuration } from '@/utils/calculationsHelpers'
import { isOvernightStay } from '@/utils/overnightHelpers'

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

const isOvernight = computed(() => isOvernightStay(props.stayItem))

const getEndOfDayTime = (startTime) => {
  const date = new Date(startTime)
  const endOfDay = new Date(date)
  endOfDay.setHours(23, 59, 59, 999)
  return endOfDay
}

const formatOnThisDayDuration = (stayItem) => {
  const stayStart = new Date(stayItem.timestamp)
  const stayEnd = new Date(stayStart.getTime() + (stayItem.stayDuration * 1000)) // stayDuration is now in seconds
  const endOfDay = getEndOfDayTime(stayStart)
  
  // For overnight stays, the "on this day" duration is from start time to end of day
  const thisDayEnd = stayEnd < endOfDay ? stayEnd : endOfDay
  
  const startTimeStr = formatTime(stayStart)
  const endTimeStr = formatTime(thisDayEnd)
  
  const durationMs = thisDayEnd - stayStart
  const durationSeconds = Math.floor(durationMs / 1000)
  
  return `${startTimeStr} - ${endTimeStr} (${formatDuration(durationSeconds)})`
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
  color: var(--gp-primary);
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

.p-dark .overnight-stay-content,
.p-dark .duration-detail {
  color: var(--gp-text-primary);
}

.p-dark .duration-detail .duration-value {
  color: var(--gp-primary);
}
</style>