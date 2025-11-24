<template>
  <BaseCard title="Trips" class="trips-table-card">
    <!-- Table Header with Filters and Export -->
    <template #header>
      <div class="table-header">
        <div class="table-title-section">
          <h3 class="table-title">Trips</h3>
          <span class="table-count">{{ filteredTripsData.length }} trips</span>
        </div>
        <div class="table-actions">
          <div class="filter-controls">
            <InputText 
              v-model="searchTerm"
              placeholder="Search origins/destinations..."
              class="search-input"
            />
            <Select
              v-model="selectedTransportMode"
              :options="transportModeOptions"
              optionLabel="label"
              optionValue="value"
              placeholder="Trip Type"
              showClear
              class="transport-filter"
            />
            <Select
              v-model="distanceFilter"
              :options="distanceFilterOptions"
              optionLabel="label"
              optionValue="value"
              placeholder="Distance"
              showClear
              class="distance-filter"
            />
          </div>
          <Button
            label="Export CSV"
            icon="pi pi-download"
            @click="$emit('export')"
            outlined
            class="export-button"
          />
        </div>
      </div>
    </template>

    <!-- Trips Data Table -->
    <DataTable
      :value="filteredTripsData"
      :loading="loading"
      :paginator="false"
      sortMode="single"
      removableSort
      selectionMode="single"
      v-model:selection="selectedTrip"
      @row-select="handleRowSelect"
      class="trips-data-table"
      responsiveLayout="scroll"
      :scrollable="true"
      scrollHeight="600px"
      :virtualScrollerOptions="{
        itemSize: 73
      }"
      :pt="{
        root: 'bg-surface-0 dark:bg-surface-950',
        header: 'bg-surface-50 dark:bg-surface-900 border-surface-200 dark:border-surface-700',
        tbody: 'bg-surface-0 dark:bg-surface-950',
        row: 'bg-surface-0 dark:bg-surface-950 hover:bg-surface-50 dark:hover:bg-surface-800',
        cell: 'text-surface-900 dark:text-surface-100 border-surface-200 dark:border-surface-700',
        paginator: 'bg-surface-50 dark:bg-surface-900 border-surface-200 dark:border-surface-700'
      }"
    >
      <!-- Start Time Column -->
      <Column 
        field="timestamp" 
        header="Start Time" 
        :sortable="true"
        :style="{ 'min-width': '150px' }"
      >
        <template #body="slotProps">
          <div class="datetime-display">
            <div class="date-part">{{ formatDate(slotProps.data.timestamp) }}</div>
            <div class="time-part">{{ formatTime(slotProps.data.timestamp) }}</div>
          </div>
        </template>
      </Column>

      <!-- End Time Column -->
      <Column 
        field="endTime" 
        header="End Time"
        :sortable="true" 
        :style="{ 'min-width': '150px' }"
      >
        <template #body="slotProps">
          <div class="datetime-display">
            <div class="date-part">{{ getEndDate(slotProps.data) }}</div>
            <div class="time-part">{{ getEndTime(slotProps.data) }}</div>
          </div>
        </template>
      </Column>

      <!-- Duration Column -->
      <Column 
        field="duration" 
        header="Duration" 
        :sortable="true"
        :style="{ 'min-width': '100px' }"
      >
        <template #body="slotProps">
          <span class="duration-badge">
            {{ formatDuration(slotProps.data.tripDuration) }}
          </span>
        </template>
      </Column>

      <!-- Origin Column -->
      <Column 
        field="origin" 
        header="Origin" 
        :sortable="true"
        :style="{ 'min-width': '180px' }"
      >
        <template #body="slotProps">
          <div class="location-info">
            <div class="location-name">
              {{ slotProps.data.origin?.locationName || 'Unknown Origin' }}
            </div>
          </div>
        </template>
      </Column>

      <!-- Destination Column -->
      <Column 
        field="destination" 
        header="Destination" 
        :sortable="true"
        :style="{ 'min-width': '180px' }"
      >
        <template #body="slotProps">
          <div class="location-info">
            <div class="location-name">
              {{ slotProps.data.destination?.locationName || 'Unknown Destination' }}
            </div>
            <div v-if="slotProps.data.destination?.address" class="location-address">
              {{ slotProps.data.destination.address }}
            </div>
          </div>
        </template>
      </Column>

      <!-- Distance Column -->
      <Column 
        field="distance" 
        header="Distance" 
        :sortable="true"
        :style="{ 'min-width': '100px' }"
      >
        <template #body="slotProps">
          <span class="distance-badge">
            {{ formatDistance(slotProps.data.distanceMeters) }}
          </span>
        </template>
      </Column>

      <!-- Transport Mode Column -->
      <Column 
        field="movementType" 
        header="Transport"
        :sortable="true"
        :style="{ 'min-width': '120px' }"
      >
        <template #body="slotProps">
          <Tag 
            v-if="slotProps.data.movementType"
            :value="slotProps.data.movementType"
            :severity="getTransportSeverity(slotProps.data.movementType)"
            :icon="getTransportIcon(slotProps.data.movementType)"
            class="transport-tag"
          />
        </template>
      </Column>

      <!-- Actions Column -->
      <Column 
        header="Actions" 
        :exportable="false"
        :style="{ 'min-width': '120px' }"
      >
        <template #body="slotProps">
          <div class="row-actions">
            <Button
              icon="pi pi-info-circle"
              v-tooltip.top="'View details'"
              outlined
              rounded
              size="small"
              @click="showDetails(slotProps.data)"
              class="action-button"
            />
          </div>
        </template>
      </Column>
    </DataTable>

    <!-- No Data State -->
    <div v-if="!loading && filteredTripsData.length === 0" class="no-data-state">
      <i class="pi pi-car no-data-icon"></i>
      <h4 class="no-data-title">No Trips Found</h4>
      <p class="no-data-message">
        No trips found for the selected date range and filters.
      </p>
    </div>

    <!-- Trip Details Dialog -->
    <TripDetailsDialog
      :visible="detailsDialogVisible"
      :trip="selectedTripForDetails"
      @close="closeDetailsDialog"
    />
  </BaseCard>
</template>

<script setup>
import { ref, computed, watch, defineAsyncComponent } from 'vue'
import DataTable from 'primevue/datatable'
import Column from 'primevue/column'
import InputText from 'primevue/inputtext'
import Select from 'primevue/select'
import Button from 'primevue/button'
import Tag from 'primevue/tag'
import BaseCard from '@/components/ui/base/BaseCard.vue'
import { useTimezone } from '@/composables/useTimezone'
import { useTableFilters } from '@/composables/useTableFilters'
import { formatDurationSmart, formatDistance } from '@/utils/calculationsHelpers'
import { useAuthStore } from '@/stores/auth'
import { storeToRefs } from 'pinia'
import { memoizedDateTimeFormat, memoizedDurationFormat, memoizedEndTimeFormat, memoizedDistanceFormat } from '@/utils/formatMemoizer'

// Lazy load the dialog component
const TripDetailsDialog = defineAsyncComponent(() =>
  import('@/components/dialogs/TripDetailsDialog.vue')
)

const timezone = useTimezone()

const props = defineProps({
  trips: {
    type: Array,
    default: () => []
  },
  stays: {
    type: Array,
    default: () => []
  },
  dateRange: Array,
  loading: Boolean
})

const emit = defineEmits(['export', 'show-on-map', 'row-select'])

const authStore = useAuthStore()
const { measureUnit } = storeToRefs(authStore)

// Use shared table filters composable with trips-specific options
const {
  searchTerm,
  selectedTransportMode,
  distanceFilter,
  transportModeOptions,
  distanceFilterOptions,
  useTripsFilter
} = useTableFilters()

watch(measureUnit, (unit) => {
  if (unit === 'IMPERIAL') {
    distanceFilterOptions.value = [
      { label: 'Less than 1 mile', value: 'short', maxDistance: 1609.34 },
      { label: '1-10 miles', value: 'medium', minDistance: 1609.34, maxDistance: 16093.4 },
      { label: '10-50 miles', value: 'long', minDistance: 16093.4, maxDistance: 80467.2 },
      { label: 'More than 50 miles', value: 'very-long', minDistance: 80467.2 }
    ]
  } else {
    distanceFilterOptions.value = [
      { label: 'Less than 1 km', value: 'short', maxDistance: 1000 },
      { label: '1-10 km', value: 'medium', minDistance: 1000, maxDistance: 10000 },
      { label: '10-50 km', value: 'long', minDistance: 10000, maxDistance: 50000 },
      { label: 'More than 50 km', value: 'very-long', minDistance: 50000 }
    ]
  }
}, { immediate: true })

// Local state
const selectedTrip = ref(null)
const detailsDialogVisible = ref(false)
const selectedTripForDetails = ref(null)

// Use shared filter logic
const filteredTripsData = useTripsFilter(
  computed(() => props.trips),
  computed(() => props.stays)
)


// Methods - Using memoized formatters for better performance
const formatDate = (timestamp) => {
  return memoizedDateTimeFormat(timestamp, 'YYYY-MM-DD', (ts, fmt) => timezone.format(ts, fmt))
}

const formatTime = (timestamp) => {
  return memoizedDateTimeFormat(timestamp, 'HH:mm', (ts, fmt) => timezone.format(ts, fmt))
}

const getEndDate = (trip) => {
  if (!trip?.timestamp || !trip?.tripDuration) return 'N/A'

  return memoizedEndTimeFormat(
    trip.timestamp,
    trip.tripDuration,
    'YYYY-MM-DD',
    (startTime, duration, format) => {
      const start = timezone.fromUtc(startTime)
      const end = start.clone().add(duration, 'seconds')
      return timezone.format(end.toISOString(), format)
    }
  )
}

const getEndTime = (trip) => {
  if (!trip?.timestamp || !trip?.tripDuration) return 'N/A'

  return memoizedEndTimeFormat(
    trip.timestamp,
    trip.tripDuration,
    'HH:mm',
    (startTime, duration, format) => {
      const start = timezone.fromUtc(startTime)
      const end = start.clone().add(duration, 'seconds')
      return timezone.format(end.toISOString(), format)
    }
  )
}

const formatDuration = (seconds) => {
  return memoizedDurationFormat(seconds || 0, formatDurationSmart)
}

const getTransportSeverity = (transportMode) => {
  const severityMap = {
    'CAR': 'info',
    'WALK': 'success',
    'BICYCLE': 'info',
    'RUNNING': 'success',
    'TRAIN': 'info',
    'FLIGHT': 'danger',
    'UNKNOWN': 'secondary'
  }
  return severityMap[transportMode?.toUpperCase()] || 'secondary'
}

const getTransportIcon = (transportMode) => {
  const iconMap = {
    'CAR': 'pi pi-car',
    'WALK': 'fas fa-walking',
    'BICYCLE': 'fas fa-bicycle',
    'RUNNING': 'fas fa-running',
    'TRAIN': 'fas fa-train',
    'FLIGHT': 'fas fa-plane',
    'UNKNOWN': 'pi pi-question-circle'
  }
  return iconMap[transportMode?.toUpperCase()] || 'pi pi-question-circle'
}

const handleRowSelect = (event) => {
  emit('row-select', event.data)
  // Also open the details dialog when a row is selected
  showDetails(event.data)
}

const showDetails = (trip) => {
  selectedTripForDetails.value = trip
  detailsDialogVisible.value = true
}

const closeDetailsDialog = () => {
  detailsDialogVisible.value = false
  selectedTripForDetails.value = null
}

// Search is handled reactively in the computed filteredTripsData
</script>

<style scoped>
.trips-table-card {
  margin-bottom: var(--gp-spacing-lg);
}

.table-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: var(--gp-spacing-md);
  margin-bottom: var(--gp-spacing-lg);
}

.table-title-section {
  display: flex;
  align-items: center;
  gap: var(--gp-spacing-sm);
}

.table-title {
  margin: 0;
  font-size: 1.1rem;
  font-weight: 600;
  color: var(--gp-text-primary);
}

.table-count {
  font-size: 0.875rem;
  color: var(--gp-text-secondary);
  font-weight: 500;
}

.table-actions {
  display: flex;
  align-items: center;
  gap: var(--gp-spacing-md);
}

.filter-controls {
  display: flex;
  align-items: center;
  gap: var(--gp-spacing-sm);
}

.search-input {
  width: 220px;
}

.transport-filter,
.distance-filter {
  width: 150px;
}

.datetime-display {
  display: flex;
  flex-direction: column;
  gap: 2px;
  align-items: flex-start;
}

.date-part {
  font-size: 0.85rem;
  color: var(--gp-text-secondary);
  font-weight: 500;
  font-family: monospace;
}

.time-part {
  font-size: 0.9rem;
  color: var(--gp-text-primary);
  font-weight: 600;
  font-family: monospace;
}

.duration-badge {
  background: var(--gp-success-50);
  color: var(--gp-success-700);
  border-radius: 12px;
  font-size: 0.9rem;
  font-weight: 500;
}

.distance-badge {
  background: var(--gp-info-50);
  color: var(--gp-info-700);
  padding: 2px 8px;
  border-radius: 12px;
  font-size: 0.75rem;
  font-weight: 500;
}

.location-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.location-name {
  font-weight: 500;
  color: var(--gp-text-primary);
}

.location-address {
  font-size: 0.875rem;
  color: var(--gp-text-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 170px;
}

.transport-tag {
  font-size: 0.75rem;
}

.row-actions {
  display: flex;
  gap: var(--gp-spacing-xs);
}

.action-button {
  width: 32px;
  height: 32px;
}

.no-data-state {
  text-align: center;
  padding: var(--gp-spacing-xxl);
  color: var(--gp-text-secondary);
}

.no-data-icon {
  font-size: 3rem;
  margin-bottom: var(--gp-spacing-md);
  opacity: 0.5;
}

.no-data-title {
  margin: 0 0 var(--gp-spacing-sm) 0;
  font-size: 1.1rem;
  font-weight: 600;
  color: var(--gp-text-secondary);
}

.no-data-message {
  margin: 0;
  font-size: 0.875rem;
  color: var(--gp-text-muted);
}

/* Dark Mode */
.p-dark .duration-badge {
  background: var(--gp-success-900);
  color: var(--gp-success-300);
}

.p-dark .distance-badge {
  background: var(--gp-info-900);
  color: var(--gp-info-300);
}

.p-dark .no-data-title {
  color: var(--gp-text-primary);
}

/* Mobile Responsive */
@media (max-width: 768px) {
  .table-header {
    flex-direction: column;
    align-items: stretch;
    gap: var(--gp-spacing-md);
  }

  .table-actions {
    flex-direction: column;
    align-items: stretch;
  }

  .filter-controls {
    flex-wrap: wrap;
    gap: var(--gp-spacing-sm);
  }

  .search-input,
  .transport-filter,
  .distance-filter {
    width: 100%;
    min-width: 0;
  }

  .export-button {
    width: 100%;
  }

  .location-address {
    max-width: 120px;
  }
}

@media (max-width: 480px) {
  .table-title-section {
    flex-direction: column;
    align-items: flex-start;
    gap: var(--gp-spacing-xs);
  }

  .filter-controls {
    flex-direction: column;
  }

  .search-input {
    width: 100%;
  }
}

/* PrimeVue DataTable Dark Mode Styling */
.p-dark .trips-data-table :deep(.p-datatable) {
  background: var(--gp-surface-dark) !important;
  color: var(--gp-text-primary) !important;
}

.p-dark .trips-data-table :deep(.p-datatable-header) {
  background: var(--gp-surface-darker) !important;
  color: var(--gp-text-primary) !important;
  border-color: var(--gp-border-dark) !important;
}

.p-dark .trips-data-table :deep(.p-datatable-tbody > tr) {
  background: var(--gp-surface-dark) !important;
  color: var(--gp-text-primary) !important;
  border-color: var(--gp-border-dark) !important;
}

.p-dark .trips-data-table :deep(.p-datatable-tbody > tr:hover) {
  background: var(--gp-surface-light) !important;
}

.p-dark .trips-data-table :deep(.p-datatable-tbody > tr > td) {
  color: var(--gp-text-primary) !important;
  border-color: var(--gp-border-dark) !important;
}

.p-dark .trips-data-table :deep(.p-datatable-thead > tr > th) {
  background: var(--gp-surface-darker) !important;
  color: var(--gp-text-primary) !important;
  border-color: var(--gp-border-dark) !important;
}

.p-dark .trips-data-table :deep(.p-datatable-paginator-bottom),
.p-dark .trips-data-table :deep(.p-paginator.p-component) {
  background: var(--gp-surface-darker) !important;
  color: var(--gp-text-primary) !important;
  border: 1px solid var(--gp-border-dark) !important;
  border-top: 1px solid var(--gp-border-dark) !important;
}

.p-dark .trips-data-table :deep(.p-datatable-wrapper) {
  border-radius: var(--gp-radius-medium) !important;
  overflow: hidden !important;
  background: var(--gp-surface-dark) !important;
}

.p-dark .trips-data-table :deep(.p-paginator .p-paginator-page),
.p-dark .trips-data-table :deep(.p-paginator .p-paginator-next),
.p-dark .trips-data-table :deep(.p-paginator .p-paginator-prev),
.p-dark .trips-data-table :deep(.p-paginator .p-paginator-first),
.p-dark .trips-data-table :deep(.p-paginator .p-paginator-last) {
  color: var(--gp-text-primary) !important;
  background: transparent !important;
  border: 1px solid var(--gp-border-dark) !important;
  margin: 0 2px !important;
}

.p-dark .trips-data-table :deep(.p-paginator .p-paginator-page:hover),
.p-dark .trips-data-table :deep(.p-paginator .p-paginator-next:hover),
.p-dark .trips-data-table :deep(.p-paginator .p-paginator-prev:hover),
.p-dark .trips-data-table :deep(.p-paginator .p-paginator-first:hover),
.p-dark .trips-data-table :deep(.p-paginator .p-paginator-last:hover) {
  background: var(--gp-surface-light) !important;
  color: var(--gp-text-primary) !important;
  border-color: var(--gp-border-medium) !important;
}

.p-dark .trips-data-table :deep(.p-paginator .p-paginator-page.p-highlight),
.p-dark .trips-data-table :deep(.p-paginator .p-paginator-page-selected) {
  background: var(--gp-primary) !important;
  color: white !important;
  border-color: var(--gp-primary) !important;
}

.p-dark .trips-data-table :deep(.p-paginator .p-paginator-current) {
  color: var(--gp-text-secondary) !important;
}
</style>