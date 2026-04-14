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
      style="max-width: 100%; box-sizing: border-box;"
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

      <!-- City Column (for Country pages) -->
      <Column
        v-if="showCity"
        field="city"
        header="City"
        :sortable="true"
        :style="{ 'min-width': '120px' }"
        :class="{ 'hide-on-mobile-city': showCity && showLocationName }"
        headerClass="city-column-header"
        bodyClass="city-column-body"
      >
        <template #body="slotProps">
          <span
            v-if="enableCityNavigation && slotProps.data.city"
            class="city-link"
            @click="handleCityClick(slotProps.data.city)"
          >
            {{ slotProps.data.city }}
          </span>
          <span v-else>{{ slotProps.data.city || 'N/A' }}</span>
        </template>
      </Column>

      <!-- Place Name Column (for City/Country pages) -->
      <Column
        v-if="showLocationName"
        field="locationName"
        header="Place Name"
        :sortable="true"
        :style="{ 'min-width': '150px' }"
        headerClass="place-name-column-header"
        bodyClass="place-name-column-body"
      >
        <template #body="slotProps">
          <span class="place-name">{{ slotProps.data.locationName || 'Unknown' }}</span>
        </template>
      </Column>

      <!-- Duration Column -->
      <Column
        v-if="hasAnyVisitTripTag"
        header="Trip"
        :style="{ 'min-width': '170px' }"
        class="trip-column"
        headerClass="trip-column"
        bodyClass="trip-column"
      >
        <template #body="slotProps">
          <span
            v-if="getVisitTripTag(slotProps.data)"
            class="trip-tag-chip"
            :style="{ '--trip-tag-color': getVisitTripColor(slotProps.data) }"
            :title="`Visit is linked to trip planner: ${getVisitTripLabel(slotProps.data)}`"
            role="button"
            tabindex="0"
            :aria-label="`Open trip planner ${getVisitTripLabel(slotProps.data)}`"
            @click.stop="handleTripTagClick(getVisitTripTag(slotProps.data))"
            @keydown.enter="handleTripTagClick(getVisitTripTag(slotProps.data))"
            @keydown.space.prevent="handleTripTagClick(getVisitTripTag(slotProps.data))"
          >
            <span class="trip-tag-dot"></span>
            {{ getVisitTripLabel(slotProps.data) }}
          </span>
          <span v-else class="trip-tag-empty">-</span>
        </template>
      </Column>

      <!-- Duration Column -->
      <Column
        field="stayDuration"
        header="Duration"
        :sortable="true"
        :style="{ 'min-width': '120px' }"
        :class="{ 'duration-column-country': showCity && showLocationName }"
        headerClass="duration-column-header"
        :bodyClass="showCity && showLocationName ? 'duration-column-country' : ''"
      >
        <template #body="slotProps">
          <span class="duration-badge">
            {{ formatDuration(slotProps.data.stayDuration) }}
          </span>
        </template>
      </Column>

      <!-- End Time Column -->
      <Column
        v-if="showEndTime"
        header="End Time"
        :style="{ 'min-width': '150px' }"
        class="end-time-column"
        headerClass="end-time-column"
        bodyClass="end-time-column"
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
        class="day-of-week-column"
        headerClass="day-of-week-column"
        bodyClass="day-of-week-column"
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
import { ref, computed, watch } from 'vue'
import { useRouter } from 'vue-router'
import DataTable from 'primevue/datatable'
import Column from 'primevue/column'
import Button from 'primevue/button'
import ProgressSpinner from 'primevue/progressspinner'
import BaseCard from '@/components/ui/base/BaseCard.vue'
import { formatDurationSmart } from '@/utils/calculationsHelpers'
import { useTimezone } from '@/composables/useTimezone'
import { useTripsStore } from '@/stores/trips'
import { findMatchingTripForVisit, normalizeTripColor } from '@/utils/tripHelpers'

const timezone = useTimezone()
const router = useRouter()
const tripsStore = useTripsStore()

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
  },
  showLocationName: {
    type: Boolean,
    default: false
  },
  showCity: {
    type: Boolean,
    default: false
  },
  enableCityNavigation: {
    type: Boolean,
    default: false
  },
  showEndTime: {
    type: Boolean,
    default: true
  }
})

const emit = defineEmits(['page-change', 'sort-change', 'export'])

const firstRow = ref(0)
const sortField = ref('timestamp')
const sortOrder = ref(-1) // -1 for desc, 1 for asc

const visitTripTagsByKey = computed(() => {
  const result = new Map()
  const trips = Array.isArray(tripsStore.trips) ? tripsStore.trips : []
  for (const visit of props.visits || []) {
    result.set(getVisitKey(visit), findMatchingTripForVisit(visit, trips))
  }
  return result
})

const hasAnyVisitTripTag = computed(() => {
  for (const tag of visitTripTagsByKey.value.values()) {
    if (tag) return true
  }
  return false
})

const formatDate = (timestamp) => {
  return timezone.formatDateDisplay(timestamp)
}

const formatTime = (timestamp) => {
  return timezone.formatTime(timestamp)
}

const formatDuration = (seconds) => {
  return formatDurationSmart(seconds || 0)
}

const getVisitKey = (visit) => {
  if (!visit) return 'unknown'
  if (visit.id !== undefined && visit.id !== null) return `id:${visit.id}`
  return `ts:${visit.timestamp || 'none'}:${visit.locationName || ''}:${visit.city || ''}`
}

const getVisitTripTag = (visit) => {
  return visitTripTagsByKey.value.get(getVisitKey(visit)) || null
}

const getVisitTripColor = (visit) => {
  return normalizeTripColor(getVisitTripTag(visit)?.color)
}

const getVisitTripLabel = (visit) => {
  const trip = getVisitTripTag(visit)
  if (!trip) return ''
  return trip.name || `Trip #${trip.id}`
}

const getEndDate = (visit) => {
  if (!visit.timestamp || !visit.stayDuration) return 'N/A'

  const startTime = timezone.fromUtc(visit.timestamp)
  const endTime = startTime.clone().add(visit.stayDuration, 'seconds')

  return timezone.formatDateDisplay(endTime.toISOString())
}

const getEndTime = (visit) => {
  if (!visit.timestamp || !visit.stayDuration) return 'N/A'

  const startTime = timezone.fromUtc(visit.timestamp)
  const endTime = startTime.clone().add(visit.stayDuration, 'seconds')

  return timezone.formatTime(endTime.toISOString())
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

const handleCityClick = (cityName) => {
  if (props.enableCityNavigation && cityName) {
    router.push(`/app/location-analytics/city/${encodeURIComponent(cityName)}`)
  }
}

const handleTripTagClick = (trip) => {
  if (!trip?.id) return
  router.push(`/app/trips/${trip.id}`)
}

const ensureTripsLoaded = async () => {
  if (Array.isArray(tripsStore.trips) && tripsStore.trips.length > 0) return
  if (tripsStore.loading?.trips) return
  try {
    await tripsStore.fetchTrips()
  } catch (error) {
    console.error('Failed to load trips for visit table trip associations:', error)
  }
}

watch(
  () => props.visits,
  () => {
    void ensureTripsLoaded()
  },
  { immediate: true }
)
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

.city-link {
  color: var(--gp-primary);
  cursor: pointer;
  text-decoration: underline;
  font-weight: 500;
  transition: color 0.2s ease;
}

.city-link:hover {
  color: var(--gp-primary-hover);
}

.place-name {
  font-weight: 500;
  color: var(--gp-text-primary);
}

.trip-tag-chip {
  --trip-tag-color: var(--gp-primary);
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  max-width: 100%;
  padding: 0.2rem 0.5rem;
  border-radius: 999px;
  border: 1px solid color-mix(in srgb, var(--trip-tag-color) 55%, white);
  background: color-mix(in srgb, var(--trip-tag-color) 10%, white);
  color: color-mix(in srgb, var(--trip-tag-color) 75%, black);
  font-size: 0.76rem;
  font-weight: 600;
  line-height: 1.2;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  cursor: pointer;
}

.trip-tag-dot {
  width: 0.42rem;
  height: 0.42rem;
  border-radius: 999px;
  background: var(--trip-tag-color);
  flex: 0 0 auto;
}

.trip-tag-empty {
  color: var(--gp-text-muted);
  font-size: 0.85rem;
}

.trip-tag-chip:focus-visible {
  outline: 2px solid color-mix(in srgb, var(--trip-tag-color) 65%, white);
  outline-offset: 2px;
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

.p-dark .city-link {
  color: var(--gp-primary-light);
}

.p-dark .city-link:hover {
  color: var(--gp-primary);
}

.p-dark .place-name {
  color: var(--gp-text-primary);
}

.p-dark .trip-tag-chip {
  border-color: color-mix(in srgb, var(--trip-tag-color) 45%, var(--gp-border-dark));
  background: color-mix(in srgb, var(--trip-tag-color) 18%, var(--gp-surface-dark));
  color: color-mix(in srgb, var(--trip-tag-color) 70%, white);
}

.p-dark .no-data-title {
  color: var(--gp-text-primary);
}

/* Dark Mode - DataTable */
.p-dark .visits-data-table :deep(.p-datatable) {
  background: var(--gp-surface-dark) !important;
  color: var(--gp-text-primary) !important;
}

.p-dark .visits-data-table :deep(.p-datatable-header) {
  background: var(--gp-surface-darker) !important;
  color: var(--gp-text-primary) !important;
  border-color: var(--gp-border-dark) !important;
}

.p-dark .visits-data-table :deep(.p-datatable-tbody > tr) {
  background: var(--gp-surface-dark) !important;
  color: var(--gp-text-primary) !important;
  border-color: var(--gp-border-dark) !important;
}

.p-dark .visits-data-table :deep(.p-datatable-tbody > tr:hover) {
  background: var(--gp-surface-light) !important;
}

.p-dark .visits-data-table :deep(.p-datatable-tbody > tr > td) {
  color: var(--gp-text-primary) !important;
  border-color: var(--gp-border-dark) !important;
}

.p-dark .visits-data-table :deep(.p-datatable-thead > tr > th) {
  background: var(--gp-surface-darker) !important;
  color: var(--gp-text-primary) !important;
  border-color: var(--gp-border-dark) !important;
}

.p-dark .visits-data-table :deep(.p-datatable-wrapper) {
  background: var(--gp-surface-dark) !important;
}

.p-dark .visits-data-table :deep(.p-paginator) {
  background: var(--gp-surface-darker) !important;
  color: var(--gp-text-primary) !important;
  border-color: var(--gp-border-dark) !important;
}

/* Ensure DataTable wrapper respects parent width */
.visits-data-table :deep(.p-datatable-wrapper) {
  max-width: 100%;
  box-sizing: border-box;
  overflow-x: auto;
}

.visits-data-table :deep(.p-datatable) {
  max-width: 100%;
  box-sizing: border-box;
}

/* Mobile Responsive */
@media (max-width: 768px) {
  .table-header {
    flex-direction: column;
    align-items: stretch;
    gap: var(--gp-spacing-sm);
  }

  .table-title {
    font-size: 1rem;
  }

  .table-count {
    font-size: 0.8rem;
  }

  .export-button {
    width: 100%;
  }

  /* Hide columns on mobile based on context */
  /* For City pages (showLocationName only): Hide End Time and Day of Week */
  /* For Country pages (showCity + showLocationName): Hide Duration, End Time, Day of Week */

  /* Always hide Day of Week on mobile */
  :deep(.day-of-week-column) {
    display: none !important;
  }

  /* Hide End Time on mobile when location columns are shown */
  :deep(.end-time-column) {
    display: none !important;
  }

  /* Hide Duration on mobile for Country pages (when both city and place name shown) */
  :deep(.duration-column-country) {
    display: none !important;
  }

  /* Reduce column widths on mobile */
  :deep(.p-datatable-thead th) {
    font-size: 0.85rem;
    padding: var(--gp-spacing-xs);
  }

  :deep(.p-datatable-tbody td) {
    padding: var(--gp-spacing-xs);
  }

  .datetime-display {
    gap: 1px;
  }

  .date-part {
    font-size: 0.75rem;
  }

  .time-part {
    font-size: 0.85rem;
  }

  .duration-badge {
    font-size: 0.8rem;
    padding: 3px 6px;
  }
}
</style>
