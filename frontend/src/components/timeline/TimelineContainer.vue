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

    <!-- Warning for large datasets -->
    <div v-if="!timelineNoData && !timelineDataLoading && timelineData && timelineData.length > displayLimit && displayLimit < timelineData.length" class="timeline-warning">
      <i class="pi pi-info-circle"></i>
      <span>Showing {{ displayLimit }} of {{ timelineData.length }} items.</span>
      <Button
        label="Load More"
        icon="pi pi-plus"
        @click="loadMore"
        size="small"
        class="load-more-button"
      />
      <router-link to="/app/timeline-reports" class="reports-link">Or view all in Timeline Reports</router-link>
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
            <!-- Stay Cards -->
            <OvernightStayCard
              v-if="slotProps.item.type === 'stay' && isOvernightItem(slotProps.item)"
              :stay-item="slotProps.item"
              :current-date="dateGroup.date"
              @click="handleTimelineItemClick"
              @export-gpx="handleExportStayAsGpx"
            />

            <StayCard
              v-else-if="slotProps.item.type === 'stay'"
              :stay-item="slotProps.item"
              @click="handleTimelineItemClick"
              @export-gpx="handleExportStayAsGpx"
            />

            <!-- Trip Cards -->
            <OvernightTripCard
              v-if="slotProps.item.type === 'trip' && isOvernightItem(slotProps.item)"
              :trip-item="slotProps.item"
              :current-date="dateGroup.date"
              @click="handleTimelineItemClick"
              @export-gpx="handleExportTripAsGpx"
              @show-classification="handleShowClassification"
            />

            <TripCard
              v-else-if="slotProps.item.type === 'trip'"
              :trip-item="slotProps.item"
              @click="handleTimelineItemClick"
              @export-gpx="handleExportTripAsGpx"
              @show-classification="handleShowClassification"
            />

            <!-- Data Gap Cards -->
            <OvernightDataGapCard
              v-if="slotProps.item.type === 'dataGap' && isOvernightItem(slotProps.item)"
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

    <!-- Trip Classification Dialog -->
    <TripClassificationDialog
      :visible="classificationDialogVisible"
      :trip="selectedTripForClassification"
      @close="handleCloseClassificationDialog"
    />
  </div>
</template>

<script setup>
import { computed, ref, defineAsyncComponent } from 'vue'
import Timeline from 'primevue/timeline'
import ProgressSpinner from 'primevue/progressspinner'
import Button from 'primevue/button'
import { useToast } from 'primevue/usetoast'
import { useExportImportStore } from '@/stores/exportImport'
import StayCard from './StayCard.vue'
import TripCard from './TripCard.vue'
import DataGapCard from './DataGapCard.vue'
import OvernightStayCard from './OvernightStayCard.vue'
import OvernightTripCard from './OvernightTripCard.vue'
import OvernightDataGapCard from './OvernightDataGapCard.vue'
import { useTimezone } from '@/composables/useTimezone'

// Lazy load the classification dialog
const TripClassificationDialog = defineAsyncComponent(() =>
  import('@/components/dialogs/TripClassificationDialog.vue')
)

const toast = useToast()
const exportImportStore = useExportImportStore()

// Display limit for progressive loading
const displayLimit = ref(50)

// Classification dialog state
const classificationDialogVisible = ref(false)
const selectedTripForClassification = ref(null)

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

// Check if an item spans multiple days (overnight)
const isOvernightItem = (item) => {
  return timezone.getTotalDaysSpanned(item) > 1;
};

// Computed properties
const getMarkerIcon = computed(() => (type) => {
  if (type === 'stay') return 'pi pi-map-marker'
  if (type === 'trip') return 'pi pi-car'
  if (type === 'dataGap') return 'pi pi-question'
  return 'pi pi-circle'
})

// Marker icons based on item type
const getMarkerIconForItem = computed(() => (item, dateKey) => {
  // Show moon icon for overnight items
  if (isOvernightItem(item)) {
    return 'pi pi-moon';
  }
  // Special walking icon for trips
  if (item.type === 'trip' && item.movementType === 'WALK') {
    return 'fas fa-walking';
  }
  return getMarkerIcon.value(item.type);
});

const getMarkerClass = computed(() => (type) => {
  if (type === 'stay') return 'marker-stay'
  if (type === 'trip') return 'marker-trip'
  if (type === 'dataGap') return 'marker-data-gap'
  return 'marker-default'
})

// Marker classes based on item type  
const getMarkerClassForItem = computed(() => (item, dateKey) => {
  const baseClass = getMarkerClass.value(item.type);
  return isOvernightItem(item) ? `${baseClass} marker-overnight` : baseClass;
})

// Group timeline data by date with proper overnight stay handling
const groupedTimelineData = computed(() => {
  if (!props.timelineData || props.timelineData.length === 0) {
    return []
  }

  // For large datasets, progressively load items based on displayLimit
  const itemsToProcess = props.timelineData.length > displayLimit.value
    ? props.timelineData.slice(0, displayLimit.value)
    : props.timelineData

  if (props.timelineData.length > displayLimit.value) {
    console.log(`Timeline: Showing ${displayLimit.value} of ${props.timelineData.length} items`)
  }

  const allDates = new Set();
  if (props.dateRange && props.dateRange.length === 2) {
    // Generate date range using timezone composable
    const dateArray = timezone.getDateRangeArray(props.dateRange[0], props.dateRange[1])
    dateArray.forEach(date => allDates.add(date))
  } else {
    itemsToProcess.forEach(item => {
      const itemStartTime = item.timestamp || item.startTime
      const itemDate = timezone.fromUtc(itemStartTime)
      allDates.add(itemDate.format('YYYY-MM-DD'));
      
      // Add all days this item spans to the date range
      const totalDays = timezone.getTotalDaysSpanned(item);
      if (totalDays > 1) {
        for (let i = 1; i < totalDays; i++) {
          const spanDate = itemDate.add(i, 'day');
          allDates.add(spanDate.format('YYYY-MM-DD'));
        }
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

  itemsToProcess.forEach(item => {
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
const loadMore = () => {
  // Load 100 more items each time
  displayLimit.value = Math.min(displayLimit.value + 100, props.timelineData.length)
  console.log(`Timeline: Loading more items. Now showing ${displayLimit.value} of ${props.timelineData.length}`)
}

const handleTimelineItemClick = (item) => {
  emit('timeline-item-click', item)
}

const handleExportTripAsGpx = async (tripItem) => {
  try {
    await exportImportStore.exportTripAsGpx(tripItem.id)
    toast.add({
      severity: 'success',
      summary: 'Export Started',
      detail: 'Trip is being exported as GPX',
      life: 3000
    })
  } catch (error) {
    console.error('Failed to export trip as GPX:', error)
    toast.add({
      severity: 'error',
      summary: 'Export Failed',
      detail: error.message || 'Failed to export trip',
      life: 5000
    })
  }
}

const handleExportStayAsGpx = async (stayItem) => {
  try {
    await exportImportStore.exportStayAsGpx(stayItem.id)
    toast.add({
      severity: 'success',
      summary: 'Export Started',
      detail: 'Stay is being exported as GPX',
      life: 3000
    })
  } catch (error) {
    console.error('Failed to export stay as GPX:', error)
    toast.add({
      severity: 'error',
      summary: 'Export Failed',
      detail: error.message || 'Failed to export stay',
      life: 5000
    })
  }
}

const handleShowClassification = (tripItem) => {
  selectedTripForClassification.value = tripItem
  classificationDialogVisible.value = true
}

const handleCloseClassificationDialog = () => {
  classificationDialogVisible.value = false
  selectedTripForClassification.value = null
}
</script>

<style scoped>
.timeline-container {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.timeline-content {
  flex: 1;
  overflow-y: auto;
  overflow-x: hidden;
  padding: 0 var(--gp-spacing-md);
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

.timeline-warning {
  padding: var(--gp-spacing-md);
  margin: 0 var(--gp-spacing-lg) var(--gp-spacing-lg);
  color: var(--gp-warning-dark);
  background: var(--gp-warning-light);
  border: 1px solid var(--gp-warning);
  border-radius: var(--gp-radius-medium);
  font-size: 0.875rem;
  font-weight: 500;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: center;
  gap: var(--gp-spacing-sm);
}

.timeline-warning i {
  font-size: 1rem;
}

.load-more-button {
  font-weight: 600;
}

.reports-link {
  color: var(--gp-primary);
  font-weight: 500;
  text-decoration: none;
  font-size: 0.8125rem;
}

.reports-link:hover {
  text-decoration: underline;
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