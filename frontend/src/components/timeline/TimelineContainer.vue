<template>
  <div class="timeline-container">
    <div class="timeline-header">
      Movement Timeline
    </div>

    <div v-if="timelineDataLoading" class="loading-messages">
      <ProgressSpinner />
    </div>

    <div v-show="timelineNoData" class="loading-messages">
      No timeline for the given date range.
    </div>

    <Timeline
      :value="timelineData"
      v-show="!timelineNoData && !timelineDataLoading"
      align="left"
      class="custom-timeline"
    >
      <template #marker="slotProps">
        <span class="timeline-marker" :class="getMarkerClass(slotProps.item.type)">
          <i :class="getMarkerIcon(slotProps.item.type)" />
        </span>
      </template>

      <template #content="slotProps">
        <StayCard
          v-if="slotProps.item.type === 'stay'"
          :stay-item="slotProps.item"
          @click="handleTimelineItemClick"
        />
        
        <TripCard
          v-else-if="slotProps.item.type === 'trip'"
          :trip-item="slotProps.item"
          @click="handleTimelineItemClick"
        />
        
        <DataGapCard
          v-else-if="slotProps.item.type === 'dataGap'"
          :data-gap-item="slotProps.item"
          @click="handleTimelineItemClick"
        />
      </template>
    </Timeline>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import StayCard from './StayCard.vue'
import TripCard from './TripCard.vue'
import DataGapCard from './DataGapCard.vue'

// Props
const props = defineProps({
  timelineNoData: {
    type: Boolean,
    default: false
  },
  timelineData: {
    type: Array,
    default: () => []
  },
  timelineDataLoading: {
    type: Boolean,
    default: false
  }
})

// Emits
const emit = defineEmits(['timeline-item-click'])

// Computed properties
const getMarkerIcon = computed(() => (type) => {
  if (type === 'stay') return 'pi pi-map-marker'
  if (type === 'trip') return 'pi pi-car'
  if (type === 'dataGap') return 'pi pi-question'
  return 'pi pi-circle'
})

const getMarkerClass = computed(() => (type) => {
  if (type === 'stay') return 'marker-stay'
  if (type === 'trip') return 'marker-trip'
  if (type === 'dataGap') return 'marker-data-gap'
  return 'marker-default'
})

// Methods
const handleTimelineItemClick = (item) => {
  emit('timeline-item-click', item)
}
</script>

<style scoped>
.timeline-container {
  width: 100%;
}

.timeline-header {
  width: 100%;
  justify-content: center;
  text-align: center;
  color: var(--gp-primary);
  font-size: 1.1rem;
  font-weight: 600;
  margin-bottom: var(--gp-spacing-lg);
  padding-bottom: var(--gp-spacing-xs);
  border-bottom: 2px solid var(--gp-primary-light);
}

/* Mobile optimizations */
@media (max-width: 768px) {
  .timeline-header {
    font-size: 1rem;
    margin-bottom: var(--gp-spacing-md);
    padding-bottom: var(--gp-spacing-xs);
  }
}

.loading-messages {
  text-align: center;
  padding: var(--gp-spacing-lg);
  margin: var(--gp-spacing-lg);
  color: var(--gp-text-secondary);
  background: var(--gp-surface-light);
  border: 1px solid var(--gp-border-light);
  border-radius: var(--gp-radius-medium);
  font-size: 0.875rem;
  font-weight: 500;
}

.timeline-marker {
  @apply flex w-8 h-8 items-center justify-center;
  color: white;
  border-radius: 50%;
  box-shadow: var(--gp-shadow-light);
}

.timeline-marker.marker-stay {
  background: var(--gp-primary);
}

.timeline-marker.marker-trip {
  background: var(--gp-success);
}

.timeline-marker.marker-data-gap {
  background: var(--gp-warning);
}

/* Dark mode adjustments */
.p-dark .timeline-header {
  color: var(--gp-primary);
  border-bottom-color: var(--gp-border-medium);
}

.p-dark .loading-messages {
  color: var(--gp-text-primary);
  background: var(--gp-surface-dark);
  border-color: var(--gp-border-dark);
}
</style>