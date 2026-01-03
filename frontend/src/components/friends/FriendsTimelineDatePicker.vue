<template>
  <div class="friends-timeline-date-picker">
    <!-- Date Range Picker with Presets -->
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
  /* Container for the date picker */
}
</style>
