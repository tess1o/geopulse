<template>
  <div class="date-range-picker" :class="variantClass">
    <FloatLabel v-if="showLabel" variant="on">
      <DatePicker
          :id="pickerId"
          v-model="dateRange"
          selectionMode="range"
          :size="size"
          class="date-picker-input"
          :manualInput="manualInput"
          iconDisplay="input"
          :variant="inputVariant"
          :showIcon="showIcon"
          :showOnFocus="true"
          :showWeek="false"
          :dateFormat="primeVueDatePickerFormat"
          :maxDate="maxDate"
          :placeholder="placeholder"
      >
        <template #footer>
          <DateRangePresetSelect
            v-model="selectedPreset"
            :options="presetOptions"
            :placeholder="presetPlaceholder"
            :group-props="selectGroupProps"
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
        :manualInput="manualInput"
        iconDisplay="input"
        :variant="inputVariant"
        :showIcon="showIcon"
        :showOnFocus="true"
        :showWeek="false"
        :dateFormat="primeVueDatePickerFormat"
        :maxDate="maxDate"
        :placeholder="placeholder"
    >
      <template #footer>
        <DateRangePresetSelect
          v-model="selectedPreset"
          :options="presetOptions"
          :placeholder="presetPlaceholder"
          :group-props="selectGroupProps"
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
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { t as translate } from '@/locales'
import { storeToRefs } from 'pinia'
import { useDateRangeStore } from '@/stores/dateRange'
import { useTimelineLabelsStore } from '@/stores/timelineLabels'
import { useTimezone } from '@/composables/useTimezone'
import { normalizeTimelineLabelColor } from '@/utils/timelineLabelHelpers'
import DatePicker from 'primevue/datepicker'
import FloatLabel from 'primevue/floatlabel'
import DateRangePresetSelect from '@/components/ui/DateRangePresetSelect.vue'
import { shouldShowTimelineLabelAsPreset } from '@/utils/dateRangePresetOptions'

const { t } = useI18n()

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
    default: () => translate('ui.dateRangePicker.labelDefault')
  },
  placeholder: {
    type: String,
    default: () => translate('ui.dateRangePicker.placeholderDefault')
  },
  presetPlaceholder: {
    type: String,
    default: () => translate('ui.dateRangePicker.presetPlaceholderDefault')
  },
  presets: {
    type: Array,
    default: () => [
      { label: translate('ui.dateRangePicker.presets.today'), value: 'today' },
      { label: translate('ui.dateRangePicker.presets.yesterday'), value: 'yesterday' },
      { label: translate('ui.dateRangePicker.presets.last7Days'), value: 'lastWeek' },
      { label: translate('ui.dateRangePicker.presets.last30Days'), value: 'lastMonth' }
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
  manualInput: {
    type: Boolean,
    default: false
  },
  pickerId: {
    type: String,
    default: 'date-range-picker'
  }
})

const emit = defineEmits(['date-change', 'validation-error'])

const timezone = useTimezone()
const dateRangeStore = useDateRangeStore()
const timelineLabelsStore = useTimelineLabelsStore()
const { dateRange: storeDateRange } = storeToRefs(dateRangeStore)

const selectedPreset = ref()
const validationMessage = ref('')

const variantClass = computed(() => `date-range-picker--${props.variant}`)
const primeVueDatePickerFormat = computed(() => timezone.getPrimeVueDatePickerFormat())

const maxDate = computed(() => new Date())
const periodPresetPrefix = 'period:'
const maxPresetNameLength = 32

const timelineLabelById = computed(() => {
  const map = new Map()
  for (const tag of timelineLabelsStore.timelineLabels || []) {
    if (tag && tag.id !== null && tag.id !== undefined) {
      map.set(String(tag.id), tag)
    }
  }
  return map
})

const periodPresets = computed(() => {
  const tags = timelineLabelsStore.timelineLabels || []
  if (!tags.length) return []

  const sorted = [...tags]
      .filter((tag) => tag && tag.startTime)
      .filter(shouldShowTimelineLabelAsPreset)
      .sort((a, b) => new Date(b.startTime).getTime() - new Date(a.startTime).getTime())

  return sorted.map((tag) => ({
    label: formatPeriodPresetLabel(tag),
    nameLabel: truncatePresetName(tag.name || t('ui.dateRangePicker.periodFallbackName')),
    value: `${periodPresetPrefix}${tag.id}`,
    kind: 'timeline-label',
    color: normalizeTimelineLabelColor(tag.color),
    dateLabel: formatPeriodPresetDateLabel(tag)
  }))
})

const defaultPresetOptions = computed(() => {
  return (props.presets || []).map((preset) => ({
    ...preset,
    kind: 'default-preset'
  }))
})

const useGroupedPresets = computed(() => periodPresets.value.length > 0)
const presetOptions = computed(() => {
  if (!useGroupedPresets.value) return defaultPresetOptions.value
  return [
    { label: t('ui.dateRangePicker.timelineLabelsGroup'), items: periodPresets.value },
    { label: t('ui.dateRangePicker.presetsGroup'), items: defaultPresetOptions.value }
  ]
})

const selectGroupProps = computed(() => {
  if (!useGroupedPresets.value) return {}
  return {
    optionGroupLabel: 'label',
    optionGroupChildren: 'items'
  }
})

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
          validationMessage.value = t('ui.dateRangePicker.maxRangeError', { days: props.maxRangeDays })
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

function setPresetRange(event) {
  const presetValue = event?.value ?? selectedPreset.value
  setPresetByValue(presetValue)
}

function setPresetByValue(presetValue) {
  validationMessage.value = ''
  selectedPreset.value = presetValue

  if (typeof presetValue === 'string' && presetValue.startsWith(periodPresetPrefix)) {
    const periodId = presetValue.slice(periodPresetPrefix.length)
    const tag = timelineLabelById.value.get(periodId)
    if (tag) {
      const { start, end } = getPeriodDateRange(tag)
      dateRangeStore.setDateRange([start, end])
      emit('date-change', [start, end])
    }
    return
  }

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

function getPeriodDateRange(tag) {
  const start = timezone.fromUtc(tag.startTime).startOf('day').utc().toISOString()
  const endBase = tag.endTime ? timezone.fromUtc(tag.endTime) : timezone.now()
  const end = endBase.endOf('day').utc().toISOString()
  return { start, end }
}

function formatPeriodPresetLabel(tag) {
  const name = truncatePresetName(tag.name || t('ui.dateRangePicker.labelFallbackName'))
  const dateRangeLabel = formatPeriodPresetDateLabel(tag)
  return `${name} (${dateRangeLabel})`
}

function formatPeriodPresetDateLabel(tag) {
  const start = timezone.fromUtc(tag.startTime)
  const endBase = tag.endTime ? timezone.fromUtc(tag.endTime) : timezone.now()
  const isActive = !tag.endTime || tag.isActive
  const nowYear = timezone.now().year()
  const includeYear = start.year() !== endBase.year() || start.year() !== nowYear
  const format = includeYear ? 'MMM D, YYYY' : 'MMM D'
  const startText = start.format(format)
  const endText = isActive ? t('ui.dateRangePicker.presets.today') : endBase.format(format)

  if (start.isSame(endBase, 'day')) {
    return isActive ? t('ui.dateRangePicker.presets.today') : startText
  }

  return `${startText} - ${endText}`
}

function truncatePresetName(name) {
  if (name.length <= maxPresetNameLength) return name
  return `${name.slice(0, maxPresetNameLength - 3)}...`
}

onMounted(async () => {
  try {
    await timelineLabelsStore.fetchTimelineLabels()
  } catch (error) {
    console.warn('Failed to load timeline labels for presets:', error)
  }
})
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
  background: var(--gp-warning-soft);
  color: var(--gp-warning-text);
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

/* Mobile responsiveness */
@media (max-width: 768px) {
  .date-picker-input :deep(.p-datepicker-input) {
    min-height: 44px; /* Touch-friendly size */
    font-size: 16px;
  }
}

@media (max-width: 480px) {
  .date-picker-input :deep(.p-datepicker-input) {
    font-size: 16px;
    padding: 0.5rem;
  }

  .validation-message {
    font-size: 0.8rem;
    padding: 0.4rem;
  }
}
</style>
