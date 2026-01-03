<template>
  <div class="date-range-picker" :class="variantClass">
    <FloatLabel v-if="showLabel" variant="on">
      <DatePicker
          :id="pickerId"
          v-model="dateRange"
          selectionMode="range"
          :size="size"
          class="date-picker-input"
          :manualInput="false"
          iconDisplay="input"
          :variant="inputVariant"
          :showIcon="showIcon"
          :showOnFocus="true"
          :showWeek="false"
          dateFormat="mm/dd/yy"
          :maxDate="maxDate"
          :placeholder="placeholder"
      >
        <template #footer>
          <Select
              :options="presets"
              optionLabel="label"
              optionValue="value"
              :placeholder="presetPlaceholder"
              v-model="selectedPreset"
              @change="setPresetRange"
          />
        </template>
      </DatePicker>
      <label v-if="showLabel" :for="pickerId">{{ label }}</label>
    </FloatLabel>

    <DatePicker
        v-else
        :id="pickerId"
        v-model="dateRange"
        selectionMode="range"
        :size="size"
        class="date-picker-input"
        :manualInput="false"
        iconDisplay="input"
        :variant="inputVariant"
        :showIcon="showIcon"
        :showOnFocus="true"
        :showWeek="false"
        dateFormat="mm/dd/yy"
        :maxDate="maxDate"
        :placeholder="placeholder"
    >
      <template #footer>
        <Select
            :options="presets"
            optionLabel="label"
            optionValue="value"
            :placeholder="presetPlaceholder"
            v-model="selectedPreset"
            @change="setPresetRange"
        />
      </template>
    </DatePicker>

    <!-- Validation Message -->
    <div v-if="validationMessage && showValidation" class="validation-message">
      <i class="pi pi-exclamation-triangle"></i>
      <span>{{ validationMessage }}</span>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { storeToRefs } from 'pinia'
import { useDateRangeStore } from '@/stores/dateRange'
import { useTimezone } from '@/composables/useTimezone'
import DatePicker from 'primevue/datepicker'
import FloatLabel from 'primevue/floatlabel'
import Select from 'primevue/select'

const props = defineProps({
  variant: {
    type: String,
    default: 'default',
    validator: (value) => ['default', 'compact', 'inline'].includes(value)
  },
  size: {
    type: String,
    default: 'medium',
    validator: (value) => ['small', 'medium', 'large'].includes(value)
  },
  inputVariant: {
    type: String,
    default: 'filled'
  },
  showIcon: {
    type: Boolean,
    default: true
  },
  showLabel: {
    type: Boolean,
    default: false
  },
  label: {
    type: String,
    default: 'Select Dates'
  },
  placeholder: {
    type: String,
    default: 'Select date range'
  },
  presetPlaceholder: {
    type: String,
    default: 'Select Preset'
  },
  presets: {
    type: Array,
    default: () => [
      { label: 'Today', value: 'today' },
      { label: 'Yesterday', value: 'yesterday' },
      { label: 'Last 7 days', value: 'lastWeek' },
      { label: 'Last 30 days', value: 'lastMonth' }
    ]
  },
  maxRangeDays: {
    type: Number,
    default: null
  },
  showValidation: {
    type: Boolean,
    default: true
  },
  pickerId: {
    type: String,
    default: 'date-range-picker'
  }
})

const emit = defineEmits(['date-change', 'validation-error'])

const timezone = useTimezone()
const dateRangeStore = useDateRangeStore()
const { dateRange: storeDateRange } = storeToRefs(dateRangeStore)

const selectedPreset = ref()
const validationMessage = ref('')

const variantClass = computed(() => `date-range-picker--${props.variant}`)

const maxDate = computed(() => new Date())

// Two-way binding with DatePicker
const dateRange = computed({
  get() {
    if (storeDateRange.value && storeDateRange.value.length === 2) {
      return timezone.convertUtcRangeToCalendarDates(storeDateRange.value[0], storeDateRange.value[1])
    }
    return null
  },
  set(value) {
    // Only process when we have both start and end dates
    if (value && value.length === 2 && value[0] && value[1]) {
      const [start, end] = value

      // Validate range if maxRangeDays is set
      if (props.maxRangeDays) {
        const days = timezone.diffInDays(end, start) + 1
        if (days > props.maxRangeDays) {
          validationMessage.value = `Maximum range is ${props.maxRangeDays} days`
          emit('validation-error', validationMessage.value)

          // Reset to last 7 days after showing error
          setTimeout(() => {
            setPresetByValue('lastWeek')
            validationMessage.value = ''
          }, 2000)
          return
        }
      }

      validationMessage.value = ''
      selectedPreset.value = 'custom'

      // Create UTC date range from picker dates
      const { start: utcStart, end: utcEnd } = timezone.createDateRangeFromPicker(start, end)
      dateRangeStore.setDateRange([utcStart, utcEnd])
      emit('date-change', [utcStart, utcEnd])
    }
  }
})

function setPresetRange() {
  const presetValue = selectedPreset.value
  setPresetByValue(presetValue)
}

function setPresetByValue(presetValue) {
  validationMessage.value = ''
  selectedPreset.value = presetValue

  switch (presetValue) {
    case 'today':
      const today = timezone.getTodayRangeUtc()
      dateRangeStore.setDateRange([today.start, today.end])
      emit('date-change', [today.start, today.end])
      break
    case 'yesterday':
      const yesterday = timezone.getYesterdayRangeUtc()
      dateRangeStore.setDateRange([yesterday.start, yesterday.end])
      emit('date-change', [yesterday.start, yesterday.end])
      break
    case 'lastWeek':
      const week = timezone.getLastWeekRange()
      dateRangeStore.setDateRange([week.start, week.end])
      emit('date-change', [week.start, week.end])
      break
    case 'lastMonth':
      const month = timezone.getLastMonthRange()
      dateRangeStore.setDateRange([month.start, month.end])
      emit('date-change', [month.start, month.end])
      break
  }
}
</script>

<style scoped>
.date-range-picker {
  position: relative;
}

.date-picker-input {
  width: 100%;
}

.validation-message {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  margin-top: 0.5rem;
  padding: 0.5rem;
  background: var(--gp-warning-light);
  color: var(--gp-warning-dark);
  border-radius: var(--gp-radius-small);
  font-size: 0.85rem;
}

.validation-message i {
  flex-shrink: 0;
}

/* Inline variant - for use in cards/panels */
.date-range-picker--inline .date-picker-input {
  border-radius: var(--gp-radius-medium);
}

/* Compact variant */
.date-range-picker--compact .date-picker-input {
  font-size: 0.85rem;
}

/* Dark mode */
.p-dark .validation-message {
  background: rgba(255, 193, 7, 0.2);
  color: var(--gp-warning);
}

/* Mobile responsiveness */
@media (max-width: 768px) {
  .date-picker-input :deep(.p-datepicker-input) {
    min-height: 44px; /* Touch-friendly size */
  }
}

@media (max-width: 480px) {
  .date-picker-input :deep(.p-datepicker-input) {
    font-size: 0.85rem;
    padding: 0.5rem;
  }

  .validation-message {
    font-size: 0.8rem;
    padding: 0.4rem;
  }
}
</style>
