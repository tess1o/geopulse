import { nextTick } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('@/composables/useTimezone', () => ({
  useTimezone: () => ({
    createDateTimeRangeFromPicker: (date) => ({ start: date.toISOString() }),
    formatDateTimeDisplay: (value) => value,
    fromUtc: (value) => {
      const date = new Date(value)
      return {
        isValid: () => Number.isFinite(date.getTime()),
        year: () => date.getFullYear(),
        month: () => date.getMonth(),
        date: () => date.getDate(),
        hour: () => date.getHours(),
        minute: () => date.getMinutes(),
        second: () => date.getSeconds(),
        millisecond: () => date.getMilliseconds()
      }
    }
  })
}))

const { useGpsPointFilters } = await import('./useGpsPointFilters')

describe('useGpsPointFilters', () => {
  let onChange
  let state

  beforeEach(() => {
    onChange = vi.fn()
    state = useGpsPointFilters({ onChange })
  })

  it('applies a date range only once both ends are set and in order', async () => {
    state.startDateTime.value = new Date(2026, 0, 2)
    expect(state.canApplyDateFilter.value).toBe(false)

    state.endDateTime.value = new Date(2026, 0, 1)
    expect(state.dateFilterValidationKey.value).toBe('technicalData.page.filters.fromBeforeTo')
    await state.applyDateFilter()
    expect(onChange).not.toHaveBeenCalled()

    state.endDateTime.value = new Date(2026, 0, 3)
    await state.applyDateFilter()
    expect(onChange).toHaveBeenCalledTimes(1)
    expect(state.hasDateFilter.value).toBe(true)
    expect(state.buildFilterParams()).toEqual({
      from: new Date(2026, 0, 2).toISOString(),
      to: new Date(2026, 0, 3).toISOString()
    })
  })

  it('counts each kind of active filter once and builds its params', async () => {
    state.filters.value.accuracyMin = 0
    state.filters.value.accuracyMax = 50
    state.filters.value.sourceTypes = ['OWNTRACKS', 'GPX']
    await nextTick()

    expect(state.activeFilterCount.value).toBe(2)
    expect(state.hasActiveFilters.value).toBe(true)
    expect(state.buildFilterParams()).toEqual({ accuracyMin: 0, accuracyMax: 50, sourceTypes: 'OWNTRACKS,GPX' })
    expect(onChange).toHaveBeenCalled()
  })

  it('reloads once when clearing all filters', async () => {
    state.filters.value.speedMin = 5
    await nextTick()
    onChange.mockClear()

    state.clearAllFilters()
    await nextTick()

    expect(onChange).toHaveBeenCalledTimes(1)
    expect(state.hasActiveFilters.value).toBe(false)
  })

  it('takes a valid range from the route query and ignores a reversed one', () => {
    state.hydrateFromRouteQuery({ startTime: '2026-01-03T00:00:00Z', endTime: '2026-01-01T00:00:00Z' })
    expect(state.hasDateFilter.value).toBe(false)

    state.hydrateFromRouteQuery({ startTime: ['2026-01-01T00:00:00Z'], endTime: '2026-01-03T00:00:00Z' })
    expect(state.hasDateFilter.value).toBe(true)
    expect(onChange).not.toHaveBeenCalled()
  })
})
