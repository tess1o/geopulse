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

    <div v-show="!timelineNoData && !timelineDataLoading" class="timeline-content">
      <div v-for="dateGroup in groupedTimelineData" :key="dateGroup.date" class="date-group">
        <!-- Date Header Separator -->
        <div class="date-separator">
          <div class="date-separator-line"></div>
          <div class="date-separator-text">{{ dateGroup.dateLabel }}</div>
          <div class="date-separator-line"></div>
        </div>
        
        <!-- Timeline for this date -->
        <Timeline
          :value="dateGroup.items"
          align="left"
          class="custom-timeline date-timeline"
        >
          <template #marker="slotProps">
            <span class="timeline-marker" :class="getMarkerClassForItem(slotProps.item, dateGroup.date)">
              <i :class="getMarkerIconForItem(slotProps.item, dateGroup.date)" />
            </span>
          </template>

          <template #content="slotProps">
            <OvernightStayCard
              v-if="slotProps.item.type === 'stay' && shouldShowAsOvernightStay(slotProps.item, dateGroup.date)"
              :stay-item="slotProps.item"
              :current-date="dateGroup.date"
              @click="handleTimelineItemClick"
            />
            
            <StayCard
              v-else-if="slotProps.item.type === 'stay'"
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
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import StayCard from './StayCard.vue'
import TripCard from './TripCard.vue'
import DataGapCard from './DataGapCard.vue'
import OvernightStayCard from './OvernightStayCard.vue'
import { shouldItemAppearOnDate, shouldShowAsOvernightStay } from '@/utils/overnightHelpers'

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
  },
  dateRange: {
    type: Array,
    default: () => []
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

// Updated to handle overnight stays with different markers
const getMarkerIconForItem = computed(() => (item, dateKey) => {
  if (item.type === 'stay' && shouldShowAsOvernightStay(item, dateKey)) {
    return 'pi pi-moon' // Moon icon for overnight stays
  }
  return getMarkerIcon.value(item.type)
})

const getMarkerClass = computed(() => (type) => {
  if (type === 'stay') return 'marker-stay'
  if (type === 'trip') return 'marker-trip'
  if (type === 'dataGap') return 'marker-data-gap'
  return 'marker-default'
})

// Updated to handle overnight stays with different classes
const getMarkerClassForItem = computed(() => (item, dateKey) => {
  if (item.type === 'stay' && shouldShowAsOvernightStay(item, dateKey)) {
    return 'marker-overnight-stay' // Special class for overnight stays
  }
  return getMarkerClass.value(item.type)
})

// Group timeline data by date with proper overnight stay handling
const groupedTimelineData = computed(() => {
  if (!props.timelineData || props.timelineData.length === 0) {
    return []
  }

  // First, determine all the dates we need to show based on the dateRange
  const allDates = new Set()
  
  // Use requested date range if available, otherwise fallback to timeline items
  if (props.dateRange && props.dateRange.length === 2) {
    const [startDate, endDate] = props.dateRange
    const currentDate = new Date(startDate)
    const end = new Date(endDate)
    
    while (currentDate <= end) {
      allDates.add(currentDate.toDateString())
      currentDate.setDate(currentDate.getDate() + 1)
    }
  } else {
    // Fallback: add dates from timeline items only if no range specified
    props.timelineData.forEach(item => {
      const itemDate = new Date(item.timestamp)
      allDates.add(itemDate.toDateString())
      
      // For overnight stays, also add the end date
      if (item.type === 'stay' && item.stayDuration) {
        const endDate = new Date(itemDate.getTime() + (item.stayDuration * 60 * 1000))
        if (itemDate.toDateString() !== endDate.toDateString()) {
          allDates.add(endDate.toDateString())
        }
      }
    })
  }

  // Create a map to group items by date
  const dateGroups = new Map()
  
  // Initialize all date groups
  allDates.forEach(dateKey => {
    const date = new Date(dateKey)
    dateGroups.set(dateKey, {
      date: dateKey,
      dateLabel: date.toLocaleDateString('en-US', {
        weekday: 'long',
        year: 'numeric',
        month: 'long',
        day: 'numeric'
      }),
      items: []
    })
  })
  
  // Process each timeline item and assign to appropriate dates
  props.timelineData.forEach(item => {
    // Check each date to see if this item should appear on it
    allDates.forEach(dateKey => {
      if (shouldItemAppearOnDate(item, dateKey)) {
        dateGroups.get(dateKey).items.push(item)
      }
    })
  })
  
  // Convert map to array, filter out empty groups, and sort by date
  return Array.from(dateGroups.values())
    .filter(group => group.items.length > 0)
    .sort((a, b) => new Date(a.date) - new Date(b.date))
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

.timeline-marker.marker-overnight-stay {
  background: var(--gp-primary-dark);
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

/* Date separator styles */
.date-group {
  margin-bottom: var(--gp-spacing-xl);
}

.date-separator {
  display: flex;
  align-items: center;
  margin: var(--gp-spacing-xl) 0 var(--gp-spacing-lg) 0;
  gap: var(--gp-spacing-md);
}

.date-separator-line {
  flex: 1;
  height: 1px;
  background: var(--gp-border-medium);
}

.date-separator-text {
  color: var(--gp-text-secondary);
  font-weight: 600;
  font-size: 0.9rem;
  padding: 0 var(--gp-spacing-sm);
  background: var(--gp-surface-white);
  white-space: nowrap;
}

.date-timeline {
  margin-top: 0;
}

/* Mobile optimizations for date separators */
@media (max-width: 768px) {
  .date-separator {
    margin: var(--gp-spacing-lg) 0 var(--gp-spacing-md) 0;
  }
  
  .date-separator-text {
    font-size: 0.8rem;
    padding: 0 var(--gp-spacing-xs);
  }
  
  .date-group {
    margin-bottom: var(--gp-spacing-lg);
  }
}

/* Dark mode adjustments for date separators */
.p-dark .date-separator-line {
  background: var(--gp-border-dark);
}

.p-dark .date-separator-text {
  color: var(--gp-text-secondary);
  background: var(--gp-surface-white);
}
</style>