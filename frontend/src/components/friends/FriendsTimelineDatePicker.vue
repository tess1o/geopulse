<template>
  <div class="friends-timeline-date-picker">
    <label for="friends-timeline-date-picker" class="date-picker-label">Range:</label>
    <DateRangePicker
        variant="inline"
        :maxRangeDays="30"
        :showValidation="true"
        :presets="presets"
        placeholder="Select date range"
        presetPlaceholder="Quick Presets"
        pickerId="friends-timeline-date-picker"
        @date-change="handleDateChange"
        @validation-error="handleValidationError"
    />
  </div>
</template>

<script setup>
import { onMounted } from 'vue'
import { useDateRangeStore } from '@/stores/dateRange'
import { useTimezone } from '@/composables/useTimezone'
import DateRangePicker from '@/components/ui/DateRangePicker.vue'

const timezone = useTimezone()
const dateRangeStore = useDateRangeStore()

const presets = [
  { label: 'Today', value: 'today' },
  { label: 'Yesterday', value: 'yesterday' },
  { label: 'Last 7 Days', value: 'lastWeek' },
  { label: 'Last 30 Days', value: 'lastMonth' }
]

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
  background: var(--gp-surface-white);
  border: 1px solid var(--gp-border-light);
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

/* Dark mode */
.p-dark .friends-timeline-date-picker {
  background: var(--gp-surface-dark);
  border-color: var(--gp-border-dark);
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
