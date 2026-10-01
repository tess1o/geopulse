<template>
  <div class="friends-timeline-date-picker">
    <label for="friends-timeline-date-picker" class="date-picker-label">{{ t('friends.datePicker.rangeLabel') }}</label>
    <DateRangePicker
        variant="inline"
        :maxRangeDays="30"
        :showValidation="true"
        :presets="presets"
        :placeholder="t('friends.datePicker.placeholder')"
        :presetPlaceholder="t('friends.datePicker.presetPlaceholder')"
        pickerId="friends-timeline-date-picker"
        @date-change="handleDateChange"
        @validation-error="handleValidationError"
    />
  </div>
</template>

<script setup>
import { computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useDateRangeStore } from '@/stores/dateRange'
import { useTimezone } from '@/composables/useTimezone'
import DateRangePicker from '@/components/ui/DateRangePicker.vue'

const { t } = useI18n()
const timezone = useTimezone()
const dateRangeStore = useDateRangeStore()

const presets = computed(() => [
  { label: t('friends.datePicker.today'), value: 'today' },
  { label: t('friends.datePicker.yesterday'), value: 'yesterday' },
  { label: t('timeline.largeDataset.last7Days'), value: 'lastWeek' },
  { label: t('timeline.largeDataset.last30Days'), value: 'lastMonth' }
])

function handleDateChange() {
  // Date change is handled by the DateRangePicker component
}

function handleValidationError() {
  // Validation errors are handled by the DateRangePicker component
}

// Always initialize with last 7 days for Friends Timeline
onMounted(() => {
  const week = timezone.getLastWeekRange()
  dateRangeStore.setDateRange([week.start, week.end])
})
</script>

<style scoped>
.friends-timeline-date-picker {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  background: var(--gp-surface-card);
  border: 1px solid var(--gp-border);
  border-radius: var(--gp-radius-medium);
  padding: 0.75rem;
}

.date-picker-label {
  font-size: 0.9rem;
  font-weight: 600;
  color: var(--gp-text-primary);
  white-space: nowrap;
  flex-shrink: 0;
}

/* Desktop - wider date picker */
@media (min-width: 769px) {
  .friends-timeline-date-picker {
    justify-content: flex-end;
  }

  .date-picker-label {
    margin-right: auto;
  }

  .friends-timeline-date-picker :deep(.date-range-picker) {
    min-width: 300px;
    max-width: 400px;
  }
}

/* Mobile */
@media (max-width: 480px) {
  .friends-timeline-date-picker {
    padding: 0.5rem;
    gap: 0.5rem;
  }

  .date-picker-label {
    font-size: 0.85rem;
  }
}
</style>
