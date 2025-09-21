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

            <OvernightTripCard
              v-else-if="slotProps.item.type === 'trip' && shouldShowAsOvernightTrip(slotProps.item, dateGroup.date)"
              :trip-item="slotProps.item"
              :current-date="dateGroup.date"
              @click="handleTimelineItemClick"
            />
            
            <TripCard
              v-else-if="slotProps.item.type === 'trip'"
              :trip-item="slotProps.item"
              @click="handleTimelineItemClick"
            />

            <OvernightDataGapCard
              v-else-if="slotProps.item.type === 'dataGap' && shouldShowAsOvernightDataGap(slotProps.item, dateGroup.date)"
              :data-gap-item="slotProps.item"
              :current-date="dateGroup.date"
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
import OvernightTripCard from './OvernightTripCard.vue'
import OvernightDataGapCard from './OvernightDataGapCard.vue'
import { useTimezone } from '@/composables/useTimezone'

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

// Composables
const timezone = useTimezone()

const isOvernightItem = (item) => {
  const duration = item.stayDuration || (item.tripDuration ? item.tripDuration * 60 : 0);
  if (!duration) return false;
  return timezone.isOvernightWithDuration(item.timestamp || item.startTime, duration);
};

const shouldShowAsOvernight = (item, dateKey) => {
  return timezone.shouldShowAsOvernight(item, dateKey);
};

const shouldShowAsOvernightStay = (item, dateKey) => item.type === 'stay' && shouldShowAsOvernight(item, dateKey);
const shouldShowAsOvernightTrip = (item, dateKey) => item.type === 'trip' && shouldShowAsOvernight(item, dateKey);
const shouldShowAsOvernightDataGap = (item, dateKey) => item.type === 'dataGap' && shouldShowAsOvernight(item, dateKey);

// Computed properties
const getMarkerIcon = computed(() => (type) => {
  if (type === 'stay') return 'pi pi-map-marker'
  if (type === 'trip') return 'pi pi-car'
  if (type === 'dataGap') return 'pi pi-question'
  return 'pi pi-circle'
})

// Updated to handle overnight items with different markers
const getMarkerIconForItem = computed(() => (item, dateKey) => {
  if (shouldShowAsOvernight(item, dateKey)) {
    return 'pi pi-moon';
  }
  if (item.type === 'trip' && item.movementType === 'WALK') {
    return 'fas fa-walking' // Walking person icon for walking trips
  }
  return getMarkerIcon.value(item.type)
})

const getMarkerClass = computed(() => (type) => {
  if (type === 'stay') return 'marker-stay'
  if (type === 'trip') return 'marker-trip'
  if (type === 'dataGap') return 'marker-data-gap'
  return 'marker-default'
})

// Updated to handle overnight items with different classes
const getMarkerClassForItem = computed(() => (item, dateKey) => {
  if (shouldShowAsOvernightStay(item, dateKey)) {
    return 'marker-overnight-stay' // Special class for overnight stays
  }
  if (shouldShowAsOvernightTrip(item, dateKey)) {
    return 'marker-overnight-trip' // Special class for overnight trips
  }
  if (shouldShowAsOvernightDataGap(item, dateKey)) {
    return 'marker-overnight-data-gap' // Special class for overnight data gaps
  }
  return getMarkerClass.value(item.type)
})

// Group timeline data by date with proper overnight stay handling
const groupedTimelineData = computed(() => {
  if (!props.timelineData || props.timelineData.length === 0) {
    return []
  }

  const allDates = new Set();
  if (props.dateRange && props.dateRange.length === 2) {
    // Generate date range using timezone composable
    const dateArray = timezone.getDateRangeArray(props.dateRange[0], props.dateRange[1])
    dateArray.forEach(date => allDates.add(date))
  } else {
    props.timelineData.forEach(item => {
      const itemStartTime = item.timestamp || item.startTime
      const itemDate = timezone.fromUtc(itemStartTime)
      allDates.add(itemDate.format('YYYY-MM-DD'));
      
      if (isOvernightItem(item)) {
        const duration = item.stayDuration || (item.tripDuration ? item.tripDuration * 60 : 0);
        const endDate = itemDate.add(duration, 'second');
        allDates.add(endDate.format('YYYY-MM-DD'));
      }
    });
  }

  const dateGroups = new Map();
  allDates.forEach(dateKey => {
    dateGroups.set(dateKey, {
      date: dateKey,
      dateLabel: timezone.formatDateLong(dateKey),
      items: []
    });
  });

  props.timelineData.forEach(item => {
    allDates.forEach(dateKey => {
      if (timezone.shouldItemAppearOnDate(item, dateKey)) {
        dateGroups.get(dateKey)?.items.push(item);
      }
    });
  });

  return Array.from(dateGroups.values())
    .filter(group => group.items.length > 0)
    .sort((a, b) => timezone.diff(a.date, b.date, 'day'));
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

.timeline-marker.marker-overnight-trip {
  background: var(--gp-success-dark);
}

.timeline-marker.marker-overnight-data-gap {
  background: var(--gp-warning-dark);
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