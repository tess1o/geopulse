<template>
  <BaseCard :title="t('data.tables.stays.title')" class="stays-table-card">
    <!-- Table Header with Filters and Export -->
    <template #header>
      <div class="table-header">
        <div v-if="!isMobile" class="table-title-section">
          <h3 class="table-title">{{ t('data.tables.stays.title') }}</h3>
          <span class="table-count">{{ t('data.tables.stays.count', { count: filteredStaysData.length }) }}</span>
        </div>
        <div class="table-actions">
          <div class="filter-controls">
            <InputText
              v-model="searchTerm"
              :placeholder="t('data.tables.searchLocations')"
              class="search-input"
            />
            <Select
              v-model="durationFilter"
              :options="durationFilterOptions"
              optionLabel="label"
              optionValue="value"
              :placeholder="t('data.tables.durationPlaceholder')"
              showClear
              class="duration-filter"
            />
          </div>
          <Button
            :label="isMobile ? null : t('data.tables.exportCsv')"
            :aria-label="t('data.tables.exportCsv')"
            icon="pi pi-download"
            @click="$emit('export')"
            outlined
            :disabled="exportDisabled"
            v-tooltip.bottom="exportDisabled ? t('data.tables.exportDisabledDemo') : t('data.tables.stays.exportTooltip')"
            class="export-button"
            :class="{ 'export-button--icon': isMobile }"
          />
        </div>
      </div>
    </template>

    <!-- Stays Data Table -->
    <DataTable
      v-if="!isMobile && !loading && filteredStaysData.length > 0"
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
    >
      <!-- Start Time Column -->
      <Column
        field="timestamp"
        :header="t('data.tables.stays.startTimeHeader')"
        :sortable="true"
        :style="{ 'min-width': '150px' }"
      >
        <template #body="slotProps">
          <div class="datetime-display">
            <div class="date-part">{{ formatDate(slotProps.data.timestamp, slotProps.data.locationTimezone) }}</div>
            <div class="time-part">{{ formatTime(slotProps.data.timestamp, slotProps.data.locationTimezone) }}</div>
          </div>
        </template>
      </Column>

      <!-- End Time Column -->
      <Column
        field="endTime"
        :header="t('data.tables.stays.endTimeHeader')"
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
        :header="t('data.tables.stays.durationHeader')"
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
        :header="t('data.tables.stays.locationHeader')"
        :sortable="true"
        :style="{ 'min-width': '200px' }"
      >
        <template #body="slotProps">
          <div class="location-info">
            <div class="location-name-wrapper">
              <span class="location-name">
                {{ slotProps.data.locationName || t('data.tables.unknownLocation') }}
              </span>
              <Button
                v-if="hasPlaceDetails(slotProps.data)"
                icon="pi pi-external-link"
                v-tooltip.top="t('data.tables.viewPlaceDetails')"
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
        :header="t('data.tables.stays.coordinatesHeader')"
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
        :header="t('data.tables.stays.actionsHeader')"
        :exportable="false"
        :style="{ 'min-width': '120px' }"
      >
        <template #body="slotProps">
          <div class="row-actions">
            <Button
              icon="pi pi-info-circle"
              v-tooltip.top="t('data.tables.viewDetails')"
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

    <div
      v-else-if="isMobile && !loading && filteredStaysData.length > 0"
      class="mobile-stay-list"
    >
      <article
        v-for="stay in filteredStaysData"
        :key="stay.id || `${stay.timestamp}-${stay.latitude}-${stay.longitude}`"
        class="mobile-stay-card"
      >
        <header class="mobile-stay-card-header">
          <div class="mobile-location-block">
            <div class="mobile-location-title-row">
              <h4 class="mobile-location-title">{{ stay.locationName || t('data.tables.unknownLocation') }}</h4>
              <Button
                v-if="hasPlaceDetails(stay)"
                icon="pi pi-external-link"
                text
                rounded
                size="small"
                @click="navigateToPlaceDetails(stay)"
                class="place-details-link"
              />
            </div>
            <p v-if="stay.address" class="mobile-location-address">{{ stay.address }}</p>
          </div>
          <span class="duration-badge mobile-duration-badge">
            {{ formatDuration(stay.stayDuration) }}
          </span>
        </header>

        <div class="mobile-stay-meta">
          <div class="mobile-meta-row">
            <span class="mobile-meta-label">{{ t('data.tables.start') }}</span>
            <span class="mobile-meta-value">{{ formatDate(stay.timestamp, stay.locationTimezone) }} {{ formatTime(stay.timestamp, stay.locationTimezone) }}</span>
          </div>
          <div class="mobile-meta-row">
            <span class="mobile-meta-label">{{ t('data.tables.end') }}</span>
            <span class="mobile-meta-value">{{ getEndDate(stay) }} {{ getEndTime(stay) }}</span>
          </div>
          <div v-if="stay.latitude && stay.longitude" class="mobile-meta-row">
            <span class="mobile-meta-label">{{ t('data.tables.coords') }}</span>
            <span class="mobile-meta-value coordinates">{{ formatCoordinates(stay) }}</span>
          </div>
        </div>

        <div class="mobile-stay-actions">
          <Button
            icon="pi pi-info-circle"
            :label="t('data.tables.details')"
            outlined
            size="small"
            @click="showDetails(stay)"
          />
        </div>
      </article>
    </div>

    <!-- No Data State -->
    <div v-if="!loading && filteredStaysData.length === 0" class="gp-empty-state">
      <i class="pi pi-map-marker gp-empty-state-icon"></i>
      <h4 class="gp-empty-state-title">{{ t('data.tables.stays.noDataTitle') }}</h4>
      <p class="gp-empty-state-message">
        {{ t('data.tables.stays.noDataMessage') }}
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
import { ref, computed, defineAsyncComponent, onMounted, onUnmounted } from 'vue'
import { useI18n } from 'vue-i18n'
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

const { t } = useI18n()
const timezone = useTimezone()
const router = useRouter()

const props = defineProps({
  stays: {
    type: Array,
    default: () => []
  },
  dateRange: Array,
  loading: Boolean,
  exportDisabled: {
    type: Boolean,
    default: false
  }
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
    { label: t('data.tables.stays.durationOptions.lessThan1Hour'), value: 'short', maxDuration: 3600 },
    { label: t('data.tables.stays.durationOptions.oneToFourHours'), value: 'medium', minDuration: 3600, maxDuration: 14400 },
    { label: t('data.tables.stays.durationOptions.fourToEightHours'), value: 'long', minDuration: 14400, maxDuration: 28800 },
    { label: t('data.tables.stays.durationOptions.moreThan8Hours'), value: 'overnight', minDuration: 28800 }
  ]
})

// Local state
const selectedStay = ref(null)
const detailsDialogVisible = ref(false)
const selectedStayForDetails = ref(null)
const isMobile = ref(false)

// Use shared filter logic
const filteredStaysData = useStaysFilter(computed(() => props.stays))

// Methods - Using memoized formatters for better performance
// Location-time mode with a resolved zone: format in the item's zone (not memoized -- the memo key has no zone).
const hasLocationZone = (locationTimezone) => timezone.isLocationTimeMode() && !!locationTimezone?.timezone

const endInstant = (startTime, durationSeconds) =>
  new Date(Date.parse(startTime) + durationSeconds * 1000).toISOString()

const formatDate = (timestamp, locationTimezone) => {
  if (hasLocationZone(locationTimezone)) return timezone.formatDateDisplayAt(timestamp, locationTimezone)
  const cacheKeyFormat = `DATE_DISPLAY:${timezone.getDateFormat()}`
  return memoizedDateTimeFormat(timestamp, cacheKeyFormat, (ts) => timezone.formatDateDisplay(ts))
}

const formatTime = (timestamp, locationTimezone) => {
  if (hasLocationZone(locationTimezone)) return timezone.formatTimeAt(timestamp, locationTimezone)
  const cacheKeyFormat = `TIME:${timezone.getTimeFormat()}:m`
  return memoizedDateTimeFormat(timestamp, cacheKeyFormat, (ts) => timezone.formatTime(ts))
}

const formatDuration = (seconds) => {
  return memoizedDurationFormat(seconds || 0, formatDurationSmart)
}

const formatDateTime = (timestamp) => {
  if (!timestamp) return t('data.tables.notAvailable')
  const cacheKeyFormat = `DATETIME_DISPLAY:${timezone.getDateFormat()}:${timezone.getTimeFormat()}:s`
  return memoizedDateTimeFormat(
    timestamp,
    cacheKeyFormat,
    (ts) => `${timezone.formatDateDisplay(ts)} ${timezone.formatTime(ts, { withSeconds: true })}`
  )
}

const getEndDateTime = (stay) => {
  if (!stay?.timestamp || !stay?.stayDuration) return t('data.tables.notAvailable')

  return memoizedEndTimeFormat(
    stay.timestamp,
    stay.stayDuration,
    `DATETIME_DISPLAY:${timezone.getDateFormat()}:${timezone.getTimeFormat()}:s`,
    (startTime, duration) => {
      const start = timezone.fromUtc(startTime)
      const end = start.clone().add(duration, 'seconds')
      return `${timezone.formatDateDisplay(end.toISOString())} ${timezone.formatTime(end.toISOString(), { withSeconds: true })}`
    }
  )
}

const getEndDate = (stay) => {
  if (!stay?.timestamp || !stay?.stayDuration) return t('data.tables.notAvailable')
  if (hasLocationZone(stay.locationTimezone)) {
    return timezone.formatDateDisplayAt(endInstant(stay.timestamp, stay.stayDuration), stay.locationTimezone)
  }

  return memoizedEndTimeFormat(
    stay.timestamp,
    stay.stayDuration,
    `DATE_DISPLAY:${timezone.getDateFormat()}`,
    (startTime, duration) => {
      const start = timezone.fromUtc(startTime)
      const end = start.clone().add(duration, 'seconds')
      return timezone.formatDateDisplay(end.toISOString())
    }
  )
}

const getEndTime = (stay) => {
  if (!stay?.timestamp || !stay?.stayDuration) return t('data.tables.notAvailable')
  if (hasLocationZone(stay.locationTimezone)) {
    return timezone.formatTimeAt(endInstant(stay.timestamp, stay.stayDuration), stay.locationTimezone)
  }

  return memoizedEndTimeFormat(
    stay.timestamp,
    stay.stayDuration,
    `TIME:${timezone.getTimeFormat()}:m`,
    (startTime, duration) => {
      const start = timezone.fromUtc(startTime)
      const end = start.clone().add(duration, 'seconds')
      return timezone.formatTime(end.toISOString())
    }
  )
}

const formatCoordinates = (stay) => {
  if (!stay?.latitude || !stay?.longitude) return t('data.tables.notAvailable')
  return `${stay.latitude.toFixed(4)}, ${stay.longitude.toFixed(4)}`
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

const updateMobileFlag = () => {
  if (typeof window === 'undefined') {
    isMobile.value = false
    return
  }
  isMobile.value = window.innerWidth <= 768
}

onMounted(() => {
  updateMobileFlag()
  if (typeof window !== 'undefined') {
    window.addEventListener('resize', updateMobileFlag)
  }
})

onUnmounted(() => {
  if (typeof window !== 'undefined') {
    window.removeEventListener('resize', updateMobileFlag)
  }
})

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
  font-family: var(--gp-font-mono);
}

.time-part {
  font-size: 0.9rem;
  color: var(--gp-text-primary);
  font-weight: 600;
  font-family: var(--gp-font-mono);
}

.end-time {
  font-size: 0.875rem;
  color: var(--gp-text-secondary);
}

.duration-badge {
  background: var(--gp-primary-soft);
  color: var(--gp-primary-text);
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
  color: var(--gp-primary);
  min-width: 28px;
  width: 28px;
  height: 28px;
  flex-shrink: 0;
}

.place-details-link:hover {
  background-color: var(--gp-primary-light);
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
  font-family: var(--gp-font-mono);
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

.mobile-stay-list {
  display: grid;
  gap: var(--gp-spacing-sm);
}

.mobile-stay-card {
  border: 1px solid var(--gp-border);
  border-radius: var(--gp-radius-medium);
  padding: var(--gp-spacing-md);
  background: var(--gp-surface-muted);
  display: flex;
  flex-direction: column;
  gap: var(--gp-spacing-sm);
}

.mobile-stay-card-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: var(--gp-spacing-sm);
}

.mobile-location-block {
  min-width: 0;
}

.mobile-location-title-row {
  display: flex;
  align-items: center;
  gap: var(--gp-spacing-xs);
}

.mobile-location-title {
  margin: 0;
  font-size: 0.95rem;
  color: var(--gp-text-primary);
}

.mobile-location-address {
  margin: var(--gp-spacing-xs) 0 0;
  font-size: 0.8rem;
  color: var(--gp-text-secondary);
  line-height: 1.35;
}

.mobile-duration-badge {
  white-space: nowrap;
  padding: 3px 8px;
}

.mobile-stay-meta {
  display: grid;
  gap: 6px;
}

.mobile-meta-row {
  display: flex;
  justify-content: space-between;
  gap: var(--gp-spacing-sm);
}

.mobile-meta-label {
  color: var(--gp-text-secondary);
  font-size: 0.78rem;
  text-transform: uppercase;
  letter-spacing: 0.03em;
}

.mobile-meta-value {
  color: var(--gp-text-primary);
  font-size: 0.85rem;
  text-align: right;
  line-height: 1.35;
}

.mobile-stay-actions {
  display: flex;
  justify-content: flex-end;
}

/* Mobile Responsive */
@media (max-width: 768px) {
  .stays-table-card :deep(.gp-card-header) {
    padding: 0.5rem 0.75rem;
  }

  .stays-table-card :deep(.gp-card-content) {
    padding: 0.75rem;
  }

  .table-header {
    margin-bottom: 0.5rem;
  }

  .table-actions {
    display: flex;
    align-items: center;
    gap: 0.5rem;
  }

  .filter-controls {
    display: flex;
    align-items: center;
    gap: 0.5rem;
    flex: 1;
    min-width: 0;
    overflow-x: auto;
    scrollbar-width: thin;
    padding-bottom: 2px;
  }

  .search-input {
    flex: 1 0 170px;
    min-width: 170px;
  }

  .duration-filter {
    flex: 0 0 130px;
    width: 130px;
    min-width: 130px;
  }

  .export-button {
    width: 40px;
    height: 40px;
    min-height: 40px;
    min-width: 40px;
    padding: 0;
  }

  .export-button--icon :deep(.p-button-label) {
    display: none;
  }

  .location-address {
    max-width: 150px;
  }

  .filter-controls :deep(.p-inputtext),
  .filter-controls :deep(.p-select-label) {
    font-size: 0.9rem;
    padding-top: 0.5rem;
    padding-bottom: 0.5rem;
  }

  .filter-controls :deep(.p-inputtext),
  .filter-controls :deep(.p-select) {
    min-height: 40px;
  }

  .filter-controls :deep(.p-select-dropdown) {
    width: 2.1rem;
  }
}

@media (max-width: 480px) {
  .table-header {
    margin-bottom: 0.35rem;
  }
}

</style>
