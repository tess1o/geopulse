import { afterEach, describe, expect, it } from 'vitest'
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

    timezone.setDateFormat('YMD')
    expect(timezone.getDateFormatPattern()).toBe('YYYY-MM-DD')
  })

  it('renders the range shape PrimeVue accepts', () => {
    timezone.setDateFormat('DMY')

    expect(timezone.getDateRangeInputPlaceholder()).toBe('DD/MM/YYYY - DD/MM/YYYY')
  })
})
