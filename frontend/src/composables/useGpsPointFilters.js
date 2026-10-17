import { computed, ref, watch } from 'vue'
import { useTimezone } from '@/composables/useTimezone'

const emptyFilters = () => ({
  sourceTypes: [],
  accuracyMin: null,
  accuracyMax: null,
  speedMin: null,
  speedMax: null
})

const isSet = (value) => value !== null && value !== undefined

const isValidDateValue = (date) => date instanceof Date && Number.isFinite(date.getTime())

const getFirstQueryValue = (value) => {
  if (Array.isArray(value)) return value[0]
  return typeof value === 'string' ? value : null
}

const startOfDay = (date) => new Date(date.getFullYear(), date.getMonth(), date.getDate(), 0, 0, 0)

const endOfDay = (date) => new Date(date.getFullYear(), date.getMonth(), date.getDate(), 23, 59, 59)

const lastDaysRange = (days) => {
  const end = new Date()
  const start = new Date()
  start.setDate(start.getDate() - days)
  start.setHours(0, 0, 0, 0)
  end.setHours(23, 59, 59, 999)
  return { start, end }
}

// Quick date presets: [start, end] in the picker's local time.
const DATE_PRESETS = {
  today: () => {
    const now = new Date()
    return { start: startOfDay(now), end: endOfDay(now) }
  },
  yesterday: () => {
    const yesterday = new Date()
    yesterday.setDate(yesterday.getDate() - 1)
    return { start: startOfDay(yesterday), end: endOfDay(yesterday) }
  },
  last7Days: () => lastDaysRange(7),
  last30Days: () => lastDaysRange(30)
}

/**
 * Filter state of the GPS points table (Technical data page): a draft date range that is applied explicitly,
 * plus source/accuracy/speed filters that apply as soon as they change.
 *
 * @param {Object} options
 * @param {Function} options.onChange called after any applied filter changes, to reload the data
 */
export function useGpsPointFilters({ onChange }) {
  const timezone = useTimezone()

  // Draft range bound to the pickers, and the range the data is currently filtered by.
  const startDateTime = ref(null)
  const endDateTime = ref(null)
  const appliedStartDateTime = ref(null)
  const appliedEndDateTime = ref(null)
  const filters = ref(emptyFilters())

  const hasDateFilterDraft = computed(() => Boolean(startDateTime.value || endDateTime.value))
  const hasCompleteDateFilterDraft = computed(() =>
    isValidDateValue(startDateTime.value) && isValidDateValue(endDateTime.value)
  )
  // Returns a validation message key, or '' when the draft range is valid (or incomplete).
  const dateFilterValidationKey = computed(() => {
    if (!hasCompleteDateFilterDraft.value) return ''
    return startDateTime.value.getTime() > endDateTime.value.getTime() ? 'technicalData.page.filters.fromBeforeTo' : ''
  })
  const canApplyDateFilter = computed(() => hasCompleteDateFilterDraft.value && !dateFilterValidationKey.value)
  const hasDateFilter = computed(() =>
    isValidDateValue(appliedStartDateTime.value) && isValidDateValue(appliedEndDateTime.value)
  )

  const hasAccuracyFilter = computed(() => isSet(filters.value.accuracyMin) || isSet(filters.value.accuracyMax))
  const hasSpeedFilter = computed(() => isSet(filters.value.speedMin) || isSet(filters.value.speedMax))
  const hasSourceFilter = computed(() => Boolean(filters.value.sourceTypes?.length))

  const activeFilterCount = computed(() =>
    [hasDateFilter, hasAccuracyFilter, hasSpeedFilter, hasSourceFilter].filter((active) => active.value).length
  )
  const hasActiveFilters = computed(() => activeFilterCount.value > 0)

  const formatDateForAPI = (date) => {
    if (!date) return null

    // Preserve selected hour/minute from DatePicker while still interpreting in user timezone.
    const { start } = timezone.createDateTimeRangeFromPicker(date, date)
    return start
  }

  const formatPickerDateTime = (date) => {
    const utcDateTime = formatDateForAPI(date)
    return utcDateTime ? timezone.formatDateTimeDisplay(utcDateTime) : '-'
  }

  const appliedDateRangeText = computed(() => {
    if (!hasDateFilter.value) return ''
    return `${formatPickerDateTime(appliedStartDateTime.value)} - ${formatPickerDateTime(appliedEndDateTime.value)}`
  })

  const utcInstantToPickerDate = (value) => {
    const utcValue = getFirstQueryValue(value)
    if (!utcValue) return null

    const dateTime = timezone.fromUtc(utcValue)
    if (!dateTime.isValid()) return null

    return new Date(
      dateTime.year(),
      dateTime.month(),
      dateTime.date(),
      dateTime.hour(),
      dateTime.minute(),
      dateTime.second(),
      dateTime.millisecond()
    )
  }

  /** Applies a startTime/endTime (UTC instants) pair from the route query, if it forms a valid range. */
  const hydrateFromRouteQuery = (query) => {
    const queryStart = utcInstantToPickerDate(query?.startTime)
    const queryEnd = utcInstantToPickerDate(query?.endTime)

    if (!isValidDateValue(queryStart) || !isValidDateValue(queryEnd) || queryStart.getTime() > queryEnd.getTime()) {
      return
    }

    startDateTime.value = queryStart
    endDateTime.value = queryEnd
    appliedStartDateTime.value = new Date(queryStart.getTime())
    appliedEndDateTime.value = new Date(queryEnd.getTime())
  }

  const applyDateFilter = async () => {
    if (!canApplyDateFilter.value) return
    appliedStartDateTime.value = new Date(startDateTime.value.getTime())
    appliedEndDateTime.value = new Date(endDateTime.value.getTime())
    await onChange()
  }

  const applyDatePreset = (preset) => {
    const { start, end } = DATE_PRESETS[preset]()
    startDateTime.value = start
    endDateTime.value = end
    return applyDateFilter()
  }

  const resetDateRange = () => {
    startDateTime.value = null
    endDateTime.value = null
    appliedStartDateTime.value = null
    appliedEndDateTime.value = null
  }

  const clearDateFilter = async () => {
    resetDateRange()
    await onChange()
  }

  // Replacing the filters object triggers the watcher below, which reloads the data.
  const clearAllFilters = () => {
    resetDateRange()
    filters.value = emptyFilters()
  }

  const clearAccuracyFilter = () => {
    filters.value.accuracyMin = null
    filters.value.accuracyMax = null
  }

  const clearSpeedFilter = () => {
    filters.value.speedMin = null
    filters.value.speedMax = null
  }

  const clearSourceFilter = () => {
    filters.value.sourceTypes = []
  }

  /** Query parameters for the GPS points API. */
  const buildFilterParams = () => {
    const params = {}

    if (hasDateFilter.value) {
      params.from = formatDateForAPI(appliedStartDateTime.value)
      params.to = formatDateForAPI(appliedEndDateTime.value)
    }

    for (const key of ['accuracyMin', 'accuracyMax', 'speedMin', 'speedMax']) {
      if (isSet(filters.value[key])) {
        params[key] = filters.value[key]
      }
    }
    if (hasSourceFilter.value) {
      params.sourceTypes = filters.value.sourceTypes.join(',')
    }

    return params
  }

  watch(filters, () => onChange(), { deep: true })

  return {
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
    hydrateFromRouteQuery,
    applyDateFilter,
    applyDatePreset,
    clearDateFilter,
    clearAllFilters,
    clearAccuracyFilter,
    clearSpeedFilter,
    clearSourceFilter,
    buildFilterParams
  }
}
