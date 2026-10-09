<template>
  <BaseCard :title="t('data.tables.dataGaps.sectionTitle')" class="data-gaps-table-card">
    <!-- Table Header with Filters and Export -->
    <template #header>
      <div class="table-header">
        <div v-if="!isMobile" class="table-title-section">
          <h3 class="table-title">{{ t('data.tables.dataGaps.sectionTitle') }}</h3>
          <span class="table-count">{{ t('data.tables.dataGaps.count', { count: filteredDataGapsData.length }) }}</span>
        </div>
        <div class="table-actions">
          <div class="filter-controls">
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
              v-tooltip.bottom="exportDisabled ? t('data.tables.exportDisabledDemo') : t('data.tables.dataGaps.exportTooltip')"
              class="export-button"
              :class="{ 'export-button--icon': isMobile }"
          />
        </div>
      </div>
    </template>

    <!-- Data Gaps Data Table -->
    <DataTable
        v-if="!isMobile"
        :value="filteredDataGapsData"
        :loading="loading"
        :paginator="false"
        sortMode="single"
        removableSort
        selectionMode="single"
        @row-select="handleRowSelect"
        class="data-gaps-data-table"
        responsiveLayout="scroll"
        :scrollable="true"
        scrollHeight="600px"
        :virtualScrollerOptions="{
          itemSize: 73
        }"
    >
      <Column
          field="startTime"
          :header="t('data.tables.dataGaps.startTimeHeader')"
          :sortable="true"
          :style="{ 'min-width': '150px' }"
      >
        <template #body="slotProps">
          <div class="datetime-display">
            <div class="date-part">{{ formatDate(slotProps.data.startTime, slotProps.data.startLocationTimezone) }}</div>
            <div class="time-part">{{ formatTime(slotProps.data.startTime, slotProps.data.startLocationTimezone) }}</div>
          </div>
        </template>
      </Column>

      <Column
          field="endTime"
          :header="t('data.tables.dataGaps.endTimeHeader')"
          :sortable="true"
          :style="{ 'min-width': '150px' }"
      >
        <template #body="slotProps">
          <div class="datetime-display">
            <div class="date-part" v-if="!isSameDay(slotProps.data.startTime, slotProps.data.endTime, slotProps.data.startLocationTimezone, slotProps.data.endLocationTimezone)">
              {{ formatDate(slotProps.data.endTime, slotProps.data.endLocationTimezone) }}
            </div>
            <div class="time-part">{{ formatTime(slotProps.data.endTime, slotProps.data.endLocationTimezone) }}</div>
          </div>
        </template>
      </Column>

      <!-- Duration Column -->
      <Column
          field="duration"
          :header="t('data.tables.dataGaps.durationHeader')"
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

    <div
      v-else-if="!loading && filteredDataGapsData.length > 0"
      class="mobile-gap-list"
    >
      <article
        v-for="gap in filteredDataGapsData"
        :key="gap.id || `${gap.startTime}-${gap.endTime}`"
        class="mobile-gap-card"
      >
        <header class="mobile-gap-header">
          <h4 class="mobile-gap-title">{{ t('data.tables.dataGaps.title') }}</h4>
          <span class="duration-badge mobile-gap-duration">{{ formatGapDuration(gap) }}</span>
        </header>

        <div class="mobile-gap-meta">
          <div class="mobile-meta-row">
            <span class="mobile-meta-label">{{ t('data.tables.start') }}</span>
            <span class="mobile-meta-value">{{ formatDate(gap.startTime, gap.startLocationTimezone) }} {{ formatTime(gap.startTime, gap.startLocationTimezone) }}</span>
          </div>
          <div class="mobile-meta-row">
            <span class="mobile-meta-label">{{ t('data.tables.end') }}</span>
            <span class="mobile-meta-value">
              <template v-if="!isSameDay(gap.startTime, gap.endTime, gap.startLocationTimezone, gap.endLocationTimezone)">{{ formatDate(gap.endTime, gap.endLocationTimezone) }} </template>{{ formatTime(gap.endTime, gap.endLocationTimezone) }}
            </span>
          </div>
        </div>
      </article>
    </div>

    <!-- No Data State -->
    <div v-if="!loading && filteredDataGapsData.length === 0" class="gp-empty-state">
      <i class="pi pi-check-circle gp-empty-state-icon"></i>
      <h4 class="gp-empty-state-title">{{ t('data.tables.dataGaps.noDataTitle') }}</h4>
      <p class="gp-empty-state-message">
        {{ t('data.tables.dataGaps.noDataMessage') }}
      </p>
    </div>
  </BaseCard>
</template>

<script setup>
import { computed, ref, onMounted, onUnmounted } from 'vue'
import { useI18n } from 'vue-i18n'
import DataTable from 'primevue/datatable'
import Column from 'primevue/column'
import Select from 'primevue/select'
import Button from 'primevue/button'
import BaseCard from '@/components/ui/base/BaseCard.vue'
import {useTimezone} from '@/composables/useTimezone'
import {useTableFilters} from '@/composables/useTableFilters'
import {formatDurationSmart} from "@/utils/calculationsHelpers"
import { memoizedDateTimeFormat, memoizedDurationFormat } from '@/utils/formatMemoizer'

const { t } = useI18n()
const timezone = useTimezone()

const props = defineProps({
  dataGaps: {
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

const emit = defineEmits(['export', 'analyze', 'row-select'])

// Use shared table filters composable with data gaps-specific options
const {
  durationFilter,
  durationFilterOptions,
  useDataGapsFilter
} = useTableFilters({
  durationOptions: [
    {label: t('data.tables.dataGaps.durationOptions.lessThan1Hour'), value: 'short', maxDuration: 3600},
    {label: t('data.tables.dataGaps.durationOptions.oneToTwoHours'), value: 'medium', minDuration: 3600, maxDuration: 7200},
    {label: t('data.tables.dataGaps.durationOptions.twoToEightHours'), value: 'long', minDuration: 7200, maxDuration: 28800},
    {label: t('data.tables.dataGaps.durationOptions.moreThan8Hours'), value: 'very-long', minDuration: 28800}
  ]
})

// Use shared filter logic
const filteredDataGapsData = useDataGapsFilter(computed(() => props.dataGaps))
const isMobile = ref(false)

// Methods - Using memoized formatters for better performance
// Location-time mode with a resolved zone: format in the item's zone (not memoized -- the memo key has no zone).
const hasLocationZone = (locationTimezone) => timezone.isLocationTimeMode() && !!locationTimezone?.timezone

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

const isSameDay = (startTime, endTime, startLocationTimezone, endLocationTimezone) => {
  if (hasLocationZone(startLocationTimezone) || hasLocationZone(endLocationTimezone)) {
    // Compare the dates as they are displayed, each in its own zone.
    return formatDate(startTime, startLocationTimezone) === formatDate(endTime, endLocationTimezone)
  }
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
  return memoizedDurationFormat(seconds, formatDurationSmart)
}

const handleRowSelect = (event) => {
  emit('row-select', event.data)
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
.data-gaps-table-card {
  margin-bottom: var(--gp-spacing-lg);
}

/* No gaps is good news: the empty state is in the success colour. */
.gp-empty-state-icon,
.gp-empty-state-title {
  color: var(--gp-success);
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

.duration-badge {
  padding: 4px 8px;
  border-radius: 12px;
  font-size: 1rem;
  font-weight: 500;
}

.duration-short {
  background: var(--gp-success-soft);
  color: var(--gp-success-text);
}

.duration-medium {
  background: var(--gp-warning-soft);
  color: var(--gp-warning-text);
}

.duration-long {
  background: var(--gp-danger-soft);
  color: var(--gp-danger-text);
}

.duration-very-long {
  background: var(--gp-danger-soft);
  color: var(--gp-danger-text);
  border: 1px solid var(--gp-danger-border);
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

.mobile-gap-list {
  display: grid;
  gap: var(--gp-spacing-sm);
}

.mobile-gap-card {
  border: 1px solid var(--gp-border);
  border-radius: var(--gp-radius-medium);
  padding: var(--gp-spacing-md);
  background: var(--gp-surface-muted);
  display: flex;
  flex-direction: column;
  gap: var(--gp-spacing-sm);
}

.mobile-gap-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: var(--gp-spacing-sm);
}

.mobile-gap-title {
  margin: 0;
  font-size: 0.95rem;
  color: var(--gp-text-primary);
}

.mobile-gap-duration {
  white-space: nowrap;
  padding: 3px 8px;
}

.mobile-gap-meta {
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

/* Mobile Responsive */
@media (max-width: 768px) {
  .data-gaps-table-card :deep(.gp-card-header) {
    padding: 0.5rem 0.75rem;
  }

  .data-gaps-table-card :deep(.gp-card-content) {
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
    flex: 1;
    min-width: 0;
  }

  .gap-type-filter,
  .duration-filter {
    width: 100%;
    min-width: 0;
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
    max-width: 120px;
  }

  .filter-controls :deep(.p-select-label) {
    font-size: 0.9rem;
    padding-top: 0.5rem;
    padding-bottom: 0.5rem;
  }

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
