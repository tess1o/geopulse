<template>
  <BaseCard title="Stays" class="stays-table-card">
    <!-- Table Header with Filters and Export -->
    <template #header>
      <div class="table-header">
        <div class="table-title-section">
          <h3 class="table-title">Stays</h3>
          <span class="table-count">{{ filteredStaysData.length }} stays</span>
        </div>
        <div class="table-actions">
          <div class="filter-controls">
            <InputText 
              v-model="searchTerm"
              placeholder="Search locations..."
              class="search-input"
            />
            <Select
              v-model="durationFilter"
              :options="durationFilterOptions"
              optionLabel="label"
              optionValue="value"
              placeholder="Duration"
              showClear
              class="duration-filter"
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

    <!-- Stays Data Table -->
    <DataTable
      v-if="!loading && filteredStaysData.length > 0"
      :value="filteredStaysData"
      :loading="loading"
      :paginator="false"
      sortMode="single"
      removableSort
      selectionMode="single"
      v-model:selection="selectedStay"
      @row-select="handleRowSelect"
      class="stays-data-table"
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
        field="stayDuration" 
        header="Duration" 
        :sortable="true"
        :style="{ 'min-width': '100px' }"
      >
        <template #body="slotProps">
          <span class="duration-badge">
            {{ formatDuration(slotProps.data.stayDuration) }}
          </span>
        </template>
      </Column>

      <!-- Location Name Column -->
      <Column
        field="locationName"
        header="Location"
        :sortable="true"
        :style="{ 'min-width': '200px' }"
      >
        <template #body="slotProps">
          <div class="location-info">
            <div class="location-name-wrapper">
              <span class="location-name">
                {{ slotProps.data.locationName || 'Unknown Location' }}
              </span>
              <Button
                v-if="hasPlaceDetails(slotProps.data)"
                icon="pi pi-external-link"
                v-tooltip.top="'View Place Details'"
                text
                rounded
                size="small"
                @click="navigateToPlaceDetails(slotProps.data)"
                class="place-details-link"
              />
            </div>
            <div v-if="slotProps.data.address" class="location-address">
              {{ slotProps.data.address }}
            </div>
          </div>
        </template>
      </Column>

      <!-- Coordinates Column -->
      <Column 
        field="coordinates" 
        header="Coordinates"
        :style="{ 'min-width': '150px' }"
      >
        <template #body="slotProps">
          <div class="coordinates" v-if="slotProps.data.latitude && slotProps.data.longitude">
            {{ slotProps.data.latitude.toFixed(4) }}, {{ slotProps.data.longitude.toFixed(4) }}
          </div>
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
    <div v-if="!loading && filteredStaysData.length === 0" class="no-data-state">
      <i class="pi pi-map-marker no-data-icon"></i>
      <h4 class="no-data-title">No Stays Found</h4>
      <p class="no-data-message">
        No stays found for the selected date range and filters.
      </p>
    </div>

    <!-- Stay Details Dialog -->
    <StayDetailsDialog
      :visible="detailsDialogVisible"
      :stay="selectedStayForDetails"
      @close="closeDetailsDialog"
    />
  </BaseCard>
</template>

<script setup>
import { ref, computed, defineAsyncComponent } from 'vue'
import { useRouter } from 'vue-router'
import DataTable from 'primevue/datatable'
import Column from 'primevue/column'
import InputText from 'primevue/inputtext'
import Select from 'primevue/select'
import Button from 'primevue/button'
import BaseCard from '@/components/ui/base/BaseCard.vue'
import { useTimezone } from '@/composables/useTimezone'
import { useTableFilters } from '@/composables/useTableFilters'
import { formatDurationSmart } from '@/utils/calculationsHelpers'
import { memoizedDateTimeFormat, memoizedDurationFormat, memoizedEndTimeFormat } from '@/utils/formatMemoizer'

// Lazy load the dialog component
const StayDetailsDialog = defineAsyncComponent(() =>
  import('@/components/dialogs/StayDetailsDialog.vue')
)

const timezone = useTimezone()
const router = useRouter()

const props = defineProps({
  stays: {
    type: Array,
    default: () => []
  },
  dateRange: Array,
  loading: Boolean
})

const emit = defineEmits(['export', 'row-select'])

// Use shared table filters composable with stays-specific options
const {
  searchTerm,
  durationFilter,
  durationFilterOptions,
  useStaysFilter
} = useTableFilters({
  durationOptions: [
    { label: 'Less than 1 hour', value: 'short', maxDuration: 3600 },
    { label: '1-4 hours', value: 'medium', minDuration: 3600, maxDuration: 14400 },
    { label: '4-8 hours', value: 'long', minDuration: 14400, maxDuration: 28800 },
    { label: 'More than 8 hours', value: 'overnight', minDuration: 28800 }
  ]
})

// Local state
const selectedStay = ref(null)
const detailsDialogVisible = ref(false)
const selectedStayForDetails = ref(null)

// Use shared filter logic
const filteredStaysData = useStaysFilter(computed(() => props.stays))

// Methods - Using memoized formatters for better performance
const formatDate = (timestamp) => {
  return memoizedDateTimeFormat(timestamp, 'YYYY-MM-DD', (ts, fmt) => timezone.format(ts, fmt))
}

const formatTime = (timestamp) => {
  return memoizedDateTimeFormat(timestamp, 'HH:mm', (ts, fmt) => timezone.format(ts, fmt))
}

const formatDuration = (seconds) => {
  return memoizedDurationFormat(seconds || 0, formatDurationSmart)
}

const formatDateTime = (timestamp) => {
  if (!timestamp) return 'N/A'
  return memoizedDateTimeFormat(timestamp, 'YYYY-MM-DD HH:mm:ss', (ts, fmt) => timezone.format(ts, fmt))
}

const getEndDateTime = (stay) => {
  if (!stay?.timestamp || !stay?.stayDuration) return 'N/A'

  return memoizedEndTimeFormat(
    stay.timestamp,
    stay.stayDuration,
    'YYYY-MM-DD HH:mm:ss',
    (startTime, duration, format) => {
      const start = timezone.fromUtc(startTime)
      const end = start.clone().add(duration, 'seconds')
      return timezone.format(end.toISOString(), format)
    }
  )
}

const getEndDate = (stay) => {
  if (!stay?.timestamp || !stay?.stayDuration) return 'N/A'

  return memoizedEndTimeFormat(
    stay.timestamp,
    stay.stayDuration,
    'YYYY-MM-DD',
    (startTime, duration, format) => {
      const start = timezone.fromUtc(startTime)
      const end = start.clone().add(duration, 'seconds')
      return timezone.format(end.toISOString(), format)
    }
  )
}

const getEndTime = (stay) => {
  if (!stay?.timestamp || !stay?.stayDuration) return 'N/A'

  return memoizedEndTimeFormat(
    stay.timestamp,
    stay.stayDuration,
    'HH:mm',
    (startTime, duration, format) => {
      const start = timezone.fromUtc(startTime)
      const end = start.clone().add(duration, 'seconds')
      return timezone.format(end.toISOString(), format)
    }
  )
}

const handleRowSelect = (event) => {
  emit('row-select', event.data)
  // Also open the details dialog when a row is selected
  showDetails(event.data)
}

const showDetails = (stay) => {
  selectedStayForDetails.value = stay
  detailsDialogVisible.value = true
}

const closeDetailsDialog = () => {
  detailsDialogVisible.value = false
  selectedStayForDetails.value = null
}

const hasPlaceDetails = (stay) => {
  return (stay.favoriteId && stay.favoriteId > 0) || (stay.geocodingId && stay.geocodingId > 0)
}

const navigateToPlaceDetails = (stay) => {
  if (stay.favoriteId && stay.favoriteId > 0) {
    router.push(`/app/place-details/favorite/${stay.favoriteId}`)
  } else if (stay.geocodingId && stay.geocodingId > 0) {
    router.push(`/app/place-details/geocoding/${stay.geocodingId}`)
  }
}

</script>

<style scoped>
.stays-table-card {
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
  width: 200px;
}

.duration-filter {
  width: 150px;
}

.time-range {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.start-time {
  font-weight: 500;
  color: var(--gp-text-primary);
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

.end-time {
  font-size: 0.875rem;
  color: var(--gp-text-secondary);
}

.duration-badge {
  background: var(--gp-primary-50);
  color: var(--gp-primary-700);
  border-radius: 12px;
  font-size: 0.9rem;
  font-weight: 500;
}

.location-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.location-name-wrapper {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.location-name {
  font-weight: 500;
  color: var(--gp-text-primary);
  flex: 1;
}

.place-details-link {
  color: var(--gp-primary) !important;
  min-width: 28px !important;
  width: 28px !important;
  height: 28px !important;
  flex-shrink: 0;
}

.place-details-link:hover {
  background-color: var(--gp-primary-light) !important;
}

.location-address {
  font-size: 0.875rem;
  color: var(--gp-text-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 200px;
}

.coordinates {
  font-family: 'SF Mono', Monaco, 'Cascadia Code', 'Roboto Mono', Consolas, 'Courier New', monospace;
  font-size: 0.875rem;
  color: var(--gp-text-secondary);
}

.place-type-tag {
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
  background: var(--gp-primary-900);
  color: var(--gp-primary-300);
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
  .place-type-filter,
  .duration-filter {
    width: 100%;
    min-width: 0;
  }

  .export-button {
    width: 100%;
  }

  .location-address {
    max-width: 150px;
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
}

/* PrimeVue DataTable Dark Mode Styling */
.p-dark .stays-data-table :deep(.p-datatable) {
  background: var(--gp-surface-dark) !important;
  color: var(--gp-text-primary) !important;
}

.p-dark .stays-data-table :deep(.p-datatable-header) {
  background: var(--gp-surface-darker) !important;
  color: var(--gp-text-primary) !important;
  border-color: var(--gp-border-dark) !important;
}

.p-dark .stays-data-table :deep(.p-datatable-tbody > tr) {
  background: var(--gp-surface-dark) !important;
  color: var(--gp-text-primary) !important;
  border-color: var(--gp-border-dark) !important;
}

.p-dark .stays-data-table :deep(.p-datatable-tbody > tr:hover) {
  background: var(--gp-surface-light) !important;
}

.p-dark .stays-data-table :deep(.p-datatable-tbody > tr > td) {
  color: var(--gp-text-primary) !important;
  border-color: var(--gp-border-dark) !important;
}

.p-dark .stays-data-table :deep(.p-datatable-thead > tr > th) {
  background: var(--gp-surface-darker) !important;
  color: var(--gp-text-primary) !important;
  border-color: var(--gp-border-dark) !important;
}

.p-dark .stays-data-table :deep(.p-datatable-paginator-bottom),
.p-dark .stays-data-table :deep(.p-paginator.p-component) {
  background: var(--gp-surface-darker) !important;
  color: var(--gp-text-primary) !important;
  border: 1px solid var(--gp-border-dark) !important;
  border-top: 1px solid var(--gp-border-dark) !important;
}

.p-dark .stays-data-table :deep(.p-datatable-wrapper) {
  border-radius: var(--gp-radius-medium) !important;
  overflow: hidden !important;
  background: var(--gp-surface-dark) !important;
}

.p-dark .stays-data-table :deep(.p-paginator .p-paginator-page),
.p-dark .stays-data-table :deep(.p-paginator .p-paginator-next),
.p-dark .stays-data-table :deep(.p-paginator .p-paginator-prev),
.p-dark .stays-data-table :deep(.p-paginator .p-paginator-first),
.p-dark .stays-data-table :deep(.p-paginator .p-paginator-last) {
  color: var(--gp-text-primary) !important;
  background: transparent !important;
  border: 1px solid var(--gp-border-dark) !important;
  margin: 0 2px !important;
}

.p-dark .stays-data-table :deep(.p-paginator .p-paginator-page:hover),
.p-dark .stays-data-table :deep(.p-paginator .p-paginator-next:hover),
.p-dark .stays-data-table :deep(.p-paginator .p-paginator-prev:hover),
.p-dark .stays-data-table :deep(.p-paginator .p-paginator-first:hover),
.p-dark .stays-data-table :deep(.p-paginator .p-paginator-last:hover) {
  background: var(--gp-surface-light) !important;
  color: var(--gp-text-primary) !important;
  border-color: var(--gp-border-medium) !important;
}

.p-dark .stays-data-table :deep(.p-paginator .p-paginator-page.p-highlight),
.p-dark .stays-data-table :deep(.p-paginator .p-paginator-page-selected) {
  background: var(--gp-primary) !important;
  color: white !important;
  border-color: var(--gp-primary) !important;
}

.p-dark .stays-data-table :deep(.p-paginator .p-paginator-current) {
  color: var(--gp-text-secondary) !important;
}
</style>