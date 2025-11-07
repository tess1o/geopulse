<template>
  <BaseCard title="Visit History">
    <!-- Table Header -->
    <template #header>
      <div class="table-header">
        <div class="table-title-section">
          <h3 class="table-title">All Visits</h3>
          <span class="table-count">{{ pagination.totalCount }} visits</span>
        </div>
        <Button
          label="Export CSV"
          icon="pi pi-download"
          @click="$emit('export')"
          outlined
          class="export-button"
        />
      </div>
    </template>

    <!-- Visits Data Table -->
    <DataTable
      v-if="!loading && visits.length > 0"
      :value="visits"
      :loading="loading"
      :lazy="true"
      :paginator="true"
      :rows="pagination.pageSize"
      :totalRecords="pagination.totalCount"
      :rowsPerPageOptions="[25, 50, 100, 200]"
      @page="handlePageChange"
      @sort="handleSort"
      v-model:first="firstRow"
      sortMode="single"
      :sortField="sortField"
      :sortOrder="sortOrder"
      class="visits-data-table"
      responsiveLayout="scroll"
      :scrollable="true"
      scrollHeight="600px"
    >
      <!-- Start Time Column -->
      <Column
        field="timestamp"
        header="Visit Date"
        :sortable="true"
        :style="{ 'min-width': '180px' }"
      >
        <template #body="slotProps">
          <div class="datetime-display">
            <div class="date-part">{{ formatDate(slotProps.data.timestamp) }}</div>
            <div class="time-part">{{ formatTime(slotProps.data.timestamp) }}</div>
          </div>
        </template>
      </Column>

      <!-- Duration Column -->
      <Column
        field="stayDuration"
        header="Duration"
        :sortable="true"
        :style="{ 'min-width': '120px' }"
      >
        <template #body="slotProps">
          <span class="duration-badge">
            {{ formatDuration(slotProps.data.stayDuration) }}
          </span>
        </template>
      </Column>

      <!-- End Time Column -->
      <Column
        header="End Time"
        :style="{ 'min-width': '150px' }"
      >
        <template #body="slotProps">
          <div class="datetime-display">
            <div class="date-part">{{ getEndDate(slotProps.data) }}</div>
            <div class="time-part">{{ getEndTime(slotProps.data) }}</div>
          </div>
        </template>
      </Column>

      <!-- Day of Week Column -->
      <Column
        header="Day of Week"
        :style="{ 'min-width': '120px' }"
      >
        <template #body="slotProps">
          <div class="day-of-week">
            {{ getDayOfWeek(slotProps.data.timestamp) }}
          </div>
        </template>
      </Column>
    </DataTable>

    <!-- No Data State -->
    <div v-if="!loading && visits.length === 0" class="no-data-state">
      <i class="pi pi-map-marker no-data-icon"></i>
      <h4 class="no-data-title">No Visits Found</h4>
      <p class="no-data-message">
        No visits recorded for this location.
      </p>
    </div>

    <!-- Loading State -->
    <div v-if="loading" class="loading-state">
      <ProgressSpinner />
    </div>
  </BaseCard>
</template>

<script setup>
import { ref, computed } from 'vue'
import DataTable from 'primevue/datatable'
import Column from 'primevue/column'
import Button from 'primevue/button'
import ProgressSpinner from 'primevue/progressspinner'
import BaseCard from '@/components/ui/base/BaseCard.vue'
import { formatDurationSmart } from '@/utils/calculationsHelpers'
import { useTimezone } from '@/composables/useTimezone'

const timezone = useTimezone()

const props = defineProps({
  visits: {
    type: Array,
    default: () => []
  },
  pagination: {
    type: Object,
    required: true
  },
  loading: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['page-change', 'sort-change', 'export'])

const firstRow = ref(0)
const sortField = ref('timestamp')
const sortOrder = ref(-1) // -1 for desc, 1 for asc

const formatDate = (timestamp) => {
  return timezone.format(timestamp, 'YYYY-MM-DD')
}

const formatTime = (timestamp) => {
  return timezone.format(timestamp, 'HH:mm')
}

const formatDuration = (seconds) => {
  return formatDurationSmart(seconds || 0)
}

const getEndDate = (visit) => {
  if (!visit.timestamp || !visit.stayDuration) return 'N/A'

  const startTime = timezone.fromUtc(visit.timestamp)
  const endTime = startTime.clone().add(visit.stayDuration, 'seconds')

  return timezone.format(endTime.toISOString(), 'YYYY-MM-DD')
}

const getEndTime = (visit) => {
  if (!visit.timestamp || !visit.stayDuration) return 'N/A'

  const startTime = timezone.fromUtc(visit.timestamp)
  const endTime = startTime.clone().add(visit.stayDuration, 'seconds')

  return timezone.format(endTime.toISOString(), 'HH:mm')
}

const getDayOfWeek = (timestamp) => {
  if (!timestamp) return 'N/A'
  return timezone.format(timestamp, 'dddd') // Full day name (Monday, Tuesday, etc.)
}

const handlePageChange = (event) => {
  firstRow.value = event.first
  const page = event.page
  const pageSize = event.rows

  emit('page-change', { page, pageSize })
}

const handleSort = (event) => {
  sortField.value = event.sortField
  sortOrder.value = event.sortOrder

  const sortDirection = event.sortOrder === 1 ? 'asc' : 'desc'
  emit('sort-change', { sortBy: event.sortField, sortDirection })
}
</script>

<style scoped>
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
  background: var(--gp-primary-50);
  color: var(--gp-primary-700);
  padding: 4px 8px;
  border-radius: 12px;
  font-size: 0.9rem;
  font-weight: 500;
}

.day-of-week {
  font-weight: 500;
  color: var(--gp-text-secondary);
  font-size: 0.9rem;
}

.no-data-state,
.loading-state {
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
  }

  .export-button {
    width: 100%;
  }
}
</style>
