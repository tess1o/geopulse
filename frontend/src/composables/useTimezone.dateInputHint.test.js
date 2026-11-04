import { afterEach, describe, expect, it, vi } from 'vitest'
import { useTimezone } from '@/composables/useTimezone'

describe('useTimezone date input hints', () => {
  const timezone = useTimezone()

  afterEach(() => {
    timezone.setDateFormat('MDY')
  })

  it('renders the date pattern for the active format', () => {
    timezone.setDateFormat('MDY')
    expect(timezone.getDateFormatPattern()).toBe('MM/DD/YYYY')

    timezone.setDateFormat('DMY')
    expect(timezone.getDateFormatPattern()).toBe('DD/MM/YYYY')

    timezone.setDateFormat('DMY_DOT')
    expect(timezone.getDateFormatPattern()).toBe('DD.MM.YYYY')

    timezone.setDateFormat('YMD')
    expect(timezone.getDateFormatPattern()).toBe('YYYY-MM-DD')
  })

  it('renders the range shape PrimeVue accepts', () => {
    timezone.setDateFormat('DMY')

    expect(timezone.getDateRangeInputPlaceholder()).toBe('DD/MM/YYYY - DD/MM/YYYY')
  })

  it('uses dotted dates for display and calendar input while keeping ISO URLs', () => {
    timezone.setDateFormat('DMY_DOT')

    expect(timezone.getPrimeVueDatePickerFormat()).toBe('dd.mm.yy')
    expect(timezone.getPrimeVueFirstDayOfWeek()).toBe(1)
    expect(timezone.getDateRangeInputPlaceholder()).toBe('DD.MM.YYYY - DD.MM.YYYY')
    expect(timezone.formatDateDisplay('2026-11-04T12:00:00Z')).toBe('04.11.2026')
    expect(timezone.formatCalendarDateDisplay('2026-11-04')).toBe('04.11.2026')
    expect(timezone.formatUrlDate('2026-11-04T12:00:00Z')).toBe('2026-11-04')
    expect(timezone.formatUrlDate(timezone.parseUrlDate('04.11.2026'))).toBe('2026-11-04')
    expect(timezone.formatUrlDate(timezone.parseUrlDate('2026-11-04'))).toBe('2026-11-04')
  })

  it('restores the dotted date preference from the cached profile on reload', async () => {
    const previous = localStorage.getItem('userInfo')
    localStorage.setItem('userInfo', JSON.stringify({ dateFormat: 'DMY_DOT' }))
    vi.resetModules()
    try {
      const reloaded = (await import('@/composables/useTimezone')).useTimezone()
      expect(reloaded.getDateFormat()).toBe('DMY_DOT')
      expect(reloaded.getDateFormatPattern()).toBe('DD.MM.YYYY')
    } finally {
      if (previous === null) localStorage.removeItem('userInfo')
      else localStorage.setItem('userInfo', previous)
      vi.resetModules()
    }
  })
})
