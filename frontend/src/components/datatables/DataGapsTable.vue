<template>
  <BaseCard title="Data Gaps" class="data-gaps-table-card">
    <!-- Table Header with Filters and Export -->
    <template #header>
      <div class="table-header">
        <div class="table-title-section">
          <h3 class="table-title">Data Gaps</h3>
          <span class="table-count">{{ filteredDataGapsData.length }} gaps</span>
        </div>
        <div class="table-actions">
          <div class="filter-controls">
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

    <!-- Data Gaps Data Table -->
    <DataTable
        :value="filteredDataGapsData"
        :loading="loading"
        :paginator="true"
        :rows="50"
        :rowsPerPageOptions="[25, 50, 100, 200]"
        sortMode="multiple"
        removableSort
        selectionMode="single"
        @row-select="handleRowSelect"
        class="data-gaps-data-table"
        responsiveLayout="scroll"
        :scrollable="true"
        scrollHeight="600px"
        :pt="{
        root: 'bg-surface-0 dark:bg-surface-950',
        header: 'bg-surface-50 dark:bg-surface-900 border-surface-200 dark:border-surface-700',
        tbody: 'bg-surface-0 dark:bg-surface-950',
        row: 'bg-surface-0 dark:bg-surface-950 hover:bg-surface-50 dark:hover:bg-surface-800',
        cell: 'text-surface-900 dark:text-surface-100 border-surface-200 dark:border-surface-700',
        paginator: 'bg-surface-50 dark:bg-surface-900 border-surface-200 dark:border-surface-700'
      }"
    >
      <Column
          field="startTime"
          header="Start Time"
          :sortable="true"
          :style="{ 'min-width': '100px' }"
      >
        <template #body="slotProps">
          <div class="end-time-info">
            <div class="end-date" v-if="!isSameDay(slotProps.data.startTime, slotProps.data.endTime)">
              {{ formatDate(slotProps.data.startTime) }}
            </div>
            <div class="end-time">{{ formatTime(slotProps.data.startTime) }}</div>
          </div>
        </template>
      </Column>

      <Column
          field="endTime"
          header="End Time"
          :sortable="true"
          :style="{ 'min-width': '100px' }"
      >
        <template #body="slotProps">
          <div class="end-time-info">
            <div class="end-date" v-if="!isSameDay(slotProps.data.startTime, slotProps.data.endTime)">
              {{ formatDate(slotProps.data.endTime) }}
            </div>
            <div class="end-time">{{ formatTime(slotProps.data.endTime) }}</div>
          </div>
        </template>
      </Column>

      <!-- Duration Column -->
      <Column
          field="duration"
          header="Duration"
          :sortable="true"
          :style="{ 'min-width': '120px' }"
      >
        <template #body="slotProps">
          <span class="duration-badge">
            {{ formatGapDuration(slotProps.data) }}
          </span>
        </template>
      </Column>
    </DataTable>

    <!-- No Data State -->
    <div v-if="!loading && filteredDataGapsData.length === 0" class="no-data-state">
      <i class="pi pi-check-circle no-data-icon"></i>
      <h4 class="no-data-title">No Data Gaps Found</h4>
      <p class="no-data-message">
        Great! No data gaps found for the selected date range and filters.
        Your GPS tracking appears to be working well.
      </p>
    </div>
  </BaseCard>
</template>

<script setup>
import {ref, computed, watch} from 'vue'
import DataTable from 'primevue/datatable'
import Column from 'primevue/column'
import InputText from 'primevue/inputtext'
import Select from 'primevue/select'
import Button from 'primevue/button'
import BaseCard from '@/components/ui/base/BaseCard.vue'
import {useTimezone} from '@/composables/useTimezone'
import { useTableFilters } from '@/composables/useTableFilters'
import {formatDurationSmart} from "@/utils/calculationsHelpers";

const timezone = useTimezone()

const props = defineProps({
  dataGaps: {
    type: Array,
    default: () => []
  },
  dateRange: Array,
  loading: Boolean
})

const emit = defineEmits(['export', 'analyze', 'row-select'])

// Use shared table filters composable with data gaps-specific options
const {
  durationFilter,
  durationFilterOptions,
  useDataGapsFilter
} = useTableFilters({
  durationOptions: [
    {label: 'Less than 1 hour', value: 'short', maxDuration: 3600},
    {label: '1 hour - 2 hours', value: 'medium', minDuration: 3600, maxDuration: 7200},
    {label: '2-8 hours', value: 'long', minDuration: 7200, maxDuration: 28800},
    {label: 'More than 8 hours', value: 'very-long', minDuration: 28800}
  ]
})

// Use shared filter logic
const filteredDataGapsData = useDataGapsFilter(computed(() => props.dataGaps))

// Methods
const formatDate = (timestamp) => {
  return timezone.format(timestamp, 'YYYY-MM-DD')
}

const formatTime = (timestamp) => {
  return timezone.format(timestamp, 'HH:mm')
}

const isSameDay = (startTime, endTime) => {
  const start = timezone.fromUtc(startTime)
  const end = timezone.fromUtc(endTime)
  return start.format('YYYY-MM-DD') === end.format('YYYY-MM-DD')
}

const calculateGapDurationSeconds = (gap) => {
  const start = timezone.fromUtc(gap.startTime)
  const end = timezone.fromUtc(gap.endTime)
  return end.diff(start, 'seconds')
}

const formatGapDuration = (gap) => {
  const seconds = calculateGapDurationSeconds(gap)
  return formatDurationSmart(seconds);
}

const handleRowSelect = (event) => {
  emit('row-select', event.data)
}

</script>

<style scoped>
.data-gaps-table-card {
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


.gap-type-filter,
.duration-filter {
  width: 150px;
}

.end-time-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.end-date {
  font-size: 0.75rem;
  color: var(--gp-text-muted);
  font-weight: 500;
}

.end-time {
  font-weight: 500;
  color: var(--gp-text-primary);
}

.duration-badge {
  padding: 4px 8px;
  border-radius: 12px;
  font-size: 1rem;
  font-weight: 500;
}

.duration-short {
  background: var(--gp-success-50);
  color: var(--gp-success-700);
}

.duration-medium {
  background: var(--gp-warning-50);
  color: var(--gp-warning-700);
}

.duration-long {
  background: var(--gp-danger-50);
  color: var(--gp-danger-700);
}

.duration-very-long {
  background: var(--gp-danger-100);
  color: var(--gp-danger-800);
  border: 1px solid var(--gp-danger-200);
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

.gap-type-tag {
  font-size: 0.75rem;
}

.unknown-gap-type {
  font-size: 0.875rem;
  color: var(--gp-text-muted);
  font-style: italic;
}

.potential-cause {
  font-size: 0.875rem;
  color: var(--gp-text-secondary);
  font-style: italic;
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
  color: var(--gp-success);
  opacity: 0.7;
}

.no-data-title {
  margin: 0 0 var(--gp-spacing-sm) 0;
  font-size: 1.1rem;
  font-weight: 600;
  color: var(--gp-success);
}

.no-data-message {
  margin: 0;
  font-size: 0.875rem;
  color: var(--gp-text-muted);
  line-height: 1.5;
}

/* Dark Mode */
.p-dark .duration-short {
  background: var(--gp-success-900);
  color: var(--gp-success-300);
}

.p-dark .duration-medium {
  background: var(--gp-warning-900);
  color: var(--gp-warning-300);
}

.p-dark .duration-long {
  background: var(--gp-danger-900);
  color: var(--gp-danger-300);
}

.p-dark .duration-very-long {
  background: var(--gp-danger-800);
  color: var(--gp-danger-200);
  border-color: var(--gp-danger-700);
}

.p-dark .no-data-title {
  color: var(--gp-success);
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

  .gap-type-filter,
  .duration-filter {
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
}

/* PrimeVue DataTable Dark Mode Styling */
.p-dark .data-gaps-data-table :deep(.p-datatable) {
  background: var(--gp-surface-dark) !important;
  color: var(--gp-text-primary) !important;
}

.p-dark .data-gaps-data-table :deep(.p-datatable-header) {
  background: var(--gp-surface-darker) !important;
  color: var(--gp-text-primary) !important;
  border-color: var(--gp-border-dark) !important;
}

.p-dark .data-gaps-data-table :deep(.p-datatable-tbody > tr) {
  background: var(--gp-surface-dark) !important;
  color: var(--gp-text-primary) !important;
  border-color: var(--gp-border-dark) !important;
}

.p-dark .data-gaps-data-table :deep(.p-datatable-tbody > tr:hover) {
  background: var(--gp-surface-light) !important;
}

.p-dark .data-gaps-data-table :deep(.p-datatable-tbody > tr > td) {
  color: var(--gp-text-primary) !important;
  border-color: var(--gp-border-dark) !important;
}

.p-dark .data-gaps-data-table :deep(.p-datatable-thead > tr > th) {
  background: var(--gp-surface-darker) !important;
  color: var(--gp-text-primary) !important;
  border-color: var(--gp-border-dark) !important;
}

.p-dark .data-gaps-data-table :deep(.p-datatable-paginator-bottom),
.p-dark .data-gaps-data-table :deep(.p-paginator.p-component) {
  background: var(--gp-surface-darker) !important;
  color: var(--gp-text-primary) !important;
  border: 1px solid var(--gp-border-dark) !important;
  border-top: 1px solid var(--gp-border-dark) !important;
}

.p-dark .data-gaps-data-table :deep(.p-datatable-wrapper) {
  border-radius: var(--gp-radius-medium) !important;
  overflow: hidden !important;
  background: var(--gp-surface-dark) !important;
}

.p-dark .data-gaps-data-table :deep(.p-paginator .p-paginator-page),
.p-dark .data-gaps-data-table :deep(.p-paginator .p-paginator-next),
.p-dark .data-gaps-data-table :deep(.p-paginator .p-paginator-prev),
.p-dark .data-gaps-data-table :deep(.p-paginator .p-paginator-first),
.p-dark .data-gaps-data-table :deep(.p-paginator .p-paginator-last) {
  color: var(--gp-text-primary) !important;
  background: transparent !important;
  border: 1px solid var(--gp-border-dark) !important;
  margin: 0 2px !important;
}

.p-dark .data-gaps-data-table :deep(.p-paginator .p-paginator-page:hover),
.p-dark .data-gaps-data-table :deep(.p-paginator .p-paginator-next:hover),
.p-dark .data-gaps-data-table :deep(.p-paginator .p-paginator-prev:hover),
.p-dark .data-gaps-data-table :deep(.p-paginator .p-paginator-first:hover),
.p-dark .data-gaps-data-table :deep(.p-paginator .p-paginator-last:hover) {
  background: var(--gp-surface-light) !important;
  color: var(--gp-text-primary) !important;
  border-color: var(--gp-border-medium) !important;
}

.p-dark .data-gaps-data-table :deep(.p-paginator .p-paginator-page.p-highlight),
.p-dark .data-gaps-data-table :deep(.p-paginator .p-paginator-page-selected) {
  background: var(--gp-primary) !important;
  color: white !important;
  border-color: var(--gp-primary) !important;
}

.p-dark .data-gaps-data-table :deep(.p-paginator .p-paginator-current) {
  color: var(--gp-text-secondary) !important;
}
</style>