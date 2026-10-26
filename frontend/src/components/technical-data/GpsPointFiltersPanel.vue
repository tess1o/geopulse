<template>
  <BaseCard class="filter-section">
    <div class="filter-header">
      <h3 class="filter-title">{{ t('technicalData.page.filters.title') }}</h3>
      <div class="filter-header-actions">
        <Button
          v-if="activeFilterCount > 0"
          :label="t('technicalData.page.filters.activeCount', { count: activeFilterCount })"
          severity="info"
          size="small"
          outlined
          :badge="activeFilterCount.toString()"
        />
        <Button
          v-if="hasActiveFilters"
          :label="t('technicalData.page.filters.clearAll')"
          severity="secondary"
          size="small"
          text
          @click="clearAllFilters"
        />
        <Button
          :icon="showAdvancedFilters ? 'pi pi-chevron-up' : 'pi pi-chevron-down'"
          :label="showAdvancedFilters ? t('technicalData.page.filters.hideAdvanced') : t('technicalData.page.filters.showAdvanced')"
          text
          size="small"
          @click="showAdvancedFilters = !showAdvancedFilters"
        />
      </div>
    </div>

    <!-- Quick Date Presets -->
    <div class="quick-presets">
      <Button :label="t('technicalData.page.filters.today')" size="small" outlined @click="applyDatePreset('today')" />
      <Button :label="t('technicalData.page.filters.yesterday')" size="small" outlined @click="applyDatePreset('yesterday')" />
      <Button :label="t('technicalData.page.filters.last7Days')" size="small" outlined @click="applyDatePreset('last7Days')" />
      <Button :label="t('technicalData.page.filters.last30Days')" size="small" outlined @click="applyDatePreset('last30Days')" />
    </div>

    <div class="filter-controls">
      <div class="date-time-filter-group">
        <div class="date-time-field">
          <label class="filter-label" for="gps-start-date-time-input">{{ t('technicalData.page.filters.from') }}</label>
          <DatePicker
            input-id="gps-start-date-time-input"
            v-model="startDateTime"
            show-time
            :hour-format="timezone.getTimeFormat() === '12h' ? '12' : '24'"
            show-icon
            icon-display="input"
            :date-format="timezone.getPrimeVueDatePickerFormat()"
            :placeholder="t('technicalData.page.filters.startPlaceholder')"
            class="date-picker date-time-picker"
          />
        </div>
        <div class="date-time-field">
          <label class="filter-label" for="gps-end-date-time-input">{{ t('technicalData.page.filters.to') }}</label>
          <DatePicker
            input-id="gps-end-date-time-input"
            v-model="endDateTime"
            show-time
            :hour-format="timezone.getTimeFormat() === '12h' ? '12' : '24'"
            show-icon
            icon-display="input"
            :date-format="timezone.getPrimeVueDatePickerFormat()"
            :placeholder="t('technicalData.page.filters.endPlaceholder')"
            class="date-picker date-time-picker"
          />
        </div>
      </div>
      <Button
        :label="t('technicalData.page.filters.apply')"
        icon="pi pi-check"
        size="small"
        :disabled="!canApplyDateFilter"
        @click="applyDateFilter"
        class="date-filter-apply-button"
      />
      <Button
        v-if="hasDateFilter || hasDateFilterDraft"
        :label="t('technicalData.page.filters.clear')"
        severity="secondary"
        size="small"
        text
        icon="pi pi-times"
        @click="clearDateFilter"
      />
    </div>
    <div v-if="dateFilterValidationKey" class="filter-validation-message">
      <i class="pi pi-exclamation-triangle"></i>
      <span>{{ t(dateFilterValidationKey) }}</span>
    </div>

    <!-- Advanced Filters -->
    <div v-if="showAdvancedFilters" class="advanced-filters">
      <div class="filter-row">
        <div class="filter-field">
          <label class="filter-label">{{ t('technicalData.page.filters.sourceTypesLabel') }}</label>
          <MultiSelect
            v-model="filters.sourceTypes"
            :options="sourceTypeOptions"
            option-label="label"
            option-value="value"
            :placeholder="t('technicalData.page.filters.allSources')"
            display="chip"
            class="filter-input"
            :showToggleAll="false"
          />
        </div>
      </div>

      <div class="filter-row">
        <div class="filter-field">
          <label class="filter-label">{{ t('technicalData.page.filters.accuracyLabel') }}</label>
          <div class="range-inputs">
            <InputNumber
              v-model="filters.accuracyMin"
              :placeholder="t('technicalData.page.filters.min')"
              :min="0"
              :max="10000"
              class="range-input"
            />
            <span class="range-separator">{{ t('technicalData.page.filters.rangeTo') }}</span>
            <InputNumber
              v-model="filters.accuracyMax"
              :placeholder="t('technicalData.page.filters.max')"
              :min="0"
              :max="10000"
              class="range-input"
            />
          </div>
        </div>

        <div class="filter-field">
          <label class="filter-label">{{ t('technicalData.page.filters.speedLabel') }}</label>
          <div class="range-inputs">
            <InputNumber
              v-model="filters.speedMin"
              :placeholder="t('technicalData.page.filters.min')"
              :min="0"
              :max="500"
              class="range-input"
            />
            <span class="range-separator">{{ t('technicalData.page.filters.rangeTo') }}</span>
            <InputNumber
              v-model="filters.speedMax"
              :placeholder="t('technicalData.page.filters.max')"
              :min="0"
              :max="500"
              class="range-input"
            />
          </div>
        </div>
      </div>
    </div>

    <!-- Active Filter Chips -->
    <div v-if="hasActiveFilters" class="active-filter-chips">
      <Chip
        v-if="hasDateFilter"
        :label="t('technicalData.page.filters.dateChip', { range: appliedDateRangeText })"
        removable
        @remove="clearDateFilter"
      />
      <Chip
        v-if="hasAccuracyFilter"
        :label="t('technicalData.page.filters.accuracyChip', { min: filters.accuracyMin || 0, max: filters.accuracyMax || '∞' })"
        removable
        @remove="clearAccuracyFilter"
      />
      <Chip
        v-if="hasSpeedFilter"
        :label="t('technicalData.page.filters.speedChip', { min: filters.speedMin || 0, max: filters.speedMax || '∞' })"
        removable
        @remove="clearSpeedFilter"
      />
      <Chip
        v-if="hasSourceFilter"
        :label="t('technicalData.page.filters.sourcesChip', { count: filters.sourceTypes.length })"
        removable
        @remove="clearSourceFilter"
      />
    </div>
  </BaseCard>
</template>

<script setup>
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import Button from 'primevue/button'
import Chip from 'primevue/chip'
import DatePicker from 'primevue/datepicker'
import InputNumber from 'primevue/inputnumber'
import MultiSelect from 'primevue/multiselect'
import BaseCard from '@/components/ui/base/BaseCard.vue'
import { useTimezone } from '@/composables/useTimezone'

// Filter card of the GPS points table: date range, quick presets, source/accuracy/speed filters and the
// active-filter chips. All filter state lives in the useGpsPointFilters instance the page passes in.
const props = defineProps({
  filtersState: { type: Object, required: true }
})

const { t } = useI18n()
const timezone = useTimezone()

const {
  startDateTime,
  endDateTime,
  filters,
  hasDateFilterDraft,
  dateFilterValidationKey,
  canApplyDateFilter,
  hasDateFilter,
  hasAccuracyFilter,
  hasSpeedFilter,
  hasSourceFilter,
  activeFilterCount,
  hasActiveFilters,
  appliedDateRangeText,
  applyDateFilter,
  applyDatePreset,
  clearDateFilter,
  clearAllFilters,
  clearAccuracyFilter,
  clearSpeedFilter,
  clearSourceFilter
} = props.filtersState

const showAdvancedFilters = ref(false)

// Source type options for multi-select
// Brand/format names (OwnTracks, GPSLogger, Overland, Traccar, Google Timeline, GPX, Dawarich,
// Home Assistant, Colota, GeoJSON, CSV) are never translated - see locales/en/locationSources.js.
// "Manual Reconstruction" is a real label, so the whole array is a computed to stay reactive to it.
const sourceTypeOptions = computed(() => [
  { label: 'OwnTracks', value: 'OWNTRACKS' },
  { label: 'GPSLogger', value: 'GPSLOGGER' },
  { label: 'Overland', value: 'OVERLAND' },
  { label: 'Traccar', value: 'TRACCAR' },
  { label: 'Google Timeline', value: 'GOOGLE_TIMELINE' },
  { label: 'GPX', value: 'GPX' },
  { label: 'Dawarich', value: 'DAWARICH' },
  { label: 'Home Assistant', value: 'HOME_ASSISTANT' },
  { label: 'Colota', value: 'COLOTA' },
  { label: 'GeoJSON', value: 'GEOJSON' },
  { label: 'CSV', value: 'CSV' },
  { label: t('technicalData.page.filters.sourceOptions.manualReconstruction'), value: 'MANUAL' }
])
</script>

<style scoped>
.filter-controls {
  display: flex;
  align-items: flex-end;
  gap: var(--gp-spacing-lg);
  flex-wrap: wrap;
}

.filter-group {
  display: flex;
  align-items: center;
  gap: var(--gp-spacing-sm);
  flex: 1;
  min-width: 250px;
}

.filter-label {
  font-weight: 500;
  color: var(--gp-text-secondary);
  white-space: nowrap;
}

.date-picker {
  flex: 1;
  max-width: 300px;
}

.date-time-filter-group {
  display: grid;
  grid-template-columns: repeat(2, minmax(220px, 1fr));
  gap: var(--gp-spacing-md);
  flex: 1 1 560px;
  max-width: 760px;
}

.date-time-field {
  display: flex;
  flex-direction: column;
  gap: var(--gp-spacing-xs);
  min-width: 0;
}

.date-time-picker {
  width: 100%;
  max-width: none;
}

.date-filter-apply-button {
  flex-shrink: 0;
}

.filter-validation-message {
  display: flex;
  align-items: center;
  gap: var(--gp-spacing-xs);
  margin-top: var(--gp-spacing-sm);
  color: var(--p-red-600);
  font-size: 0.875rem;
}

.filter-validation-message i {
  font-size: 0.875rem;
}

/* Large screen filter optimizations */
@media (min-width: 1440px) {
  .filter-controls {
    justify-content: flex-start;
    flex-wrap: nowrap;
  }

  .filter-group {
    flex: 0 0 auto;
    min-width: 300px;
  }

  .date-picker {
    max-width: 400px;
  }

  .date-time-picker {
    max-width: none;
  }
}

.filter-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: var(--gp-spacing-md);
  padding-bottom: var(--gp-spacing-sm);
  border-bottom: 1px solid var(--gp-border);
}

.filter-title {
  font-size: 1.1rem;
  font-weight: 600;
  margin: 0;
  color: var(--gp-text-primary);
}

.filter-header-actions {
  display: flex;
  gap: var(--gp-spacing-sm);
  align-items: center;
}

.quick-presets {
  display: flex;
  gap: var(--gp-spacing-sm);
  margin-bottom: var(--gp-spacing-md);
  flex-wrap: wrap;
}

.advanced-filters {
  margin-top: var(--gp-spacing-md);
  padding-top: var(--gp-spacing-md);
  border-top: 1px solid var(--gp-border);
}

.filter-row {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
  gap: var(--gp-spacing-md);
  margin-bottom: var(--gp-spacing-md);
}

.filter-field {
  display: flex;
  flex-direction: column;
  gap: var(--gp-spacing-xs);
}

.range-inputs {
  display: flex;
  align-items: center;
  gap: var(--gp-spacing-sm);
}

.range-input {
  flex: 1;
}

.range-separator {
  color: var(--gp-text-muted);
  font-size: 0.9rem;
}

.filter-input {
  width: 100%;
}

.active-filter-chips {
  display: flex;
  flex-wrap: wrap;
  gap: var(--gp-spacing-sm);
  margin-top: var(--gp-spacing-md);
  padding-top: var(--gp-spacing-md);
  border-top: 1px solid var(--gp-border);
}

@media (max-width: 768px) {
  .filter-controls {
    flex-direction: column;
    align-items: stretch;
    gap: var(--gp-spacing-md);
    width: 100%;
    max-width: 100%;
    box-sizing: border-box;
  }

  .filter-group {
    flex-direction: column;
    align-items: stretch;
    min-width: unset;
    width: 100%;
    max-width: 100%;
  }

  .date-time-filter-group {
    grid-template-columns: 1fr;
    flex-basis: auto;
    max-width: 100%;
    width: 100%;
  }

  .date-picker,
  .date-time-picker {
    max-width: 100%;
    width: 100%;
    min-width: unset;
  }

  .date-filter-apply-button,
  .filter-controls > .p-button {
    width: 100%;
    justify-content: center;
  }

  .filter-header {
    flex-direction: column;
    align-items: flex-start;
  }

  .filter-header-actions {
    width: 100%;
    justify-content: flex-end;
  }

  .quick-presets {
    display: grid;
    grid-template-columns: 1fr 1fr;
    width: 100%;
  }

  .filter-row {
    grid-template-columns: 1fr;
  }
}
</style>
