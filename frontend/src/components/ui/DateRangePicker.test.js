import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useTimelineLabelsStore } from '@/stores/timelineLabels'
import { useTimezone } from '@/composables/useTimezone'

const DatePickerStub = {
  name: 'DatePicker',
  props: {
    modelValue: { default: null },
    manualInput: { type: Boolean, default: false },
    placeholder: { default: null }
  },
  template: '<input class="date-picker-stub" :data-manual-input="String(manualInput)" :data-placeholder="placeholder" />'
}

const mountPicker = async (props = {}) => {
  const pinia = createPinia()
  setActivePinia(pinia)
  vi.spyOn(useTimelineLabelsStore(), 'fetchTimelineLabels').mockResolvedValue()

  const DateRangePicker = (await import('./DateRangePicker.vue')).default

  return mount(DateRangePicker, {
    props,
    global: {
      plugins: [pinia],
      stubs: {
        DatePicker: DatePickerStub,
        FloatLabel: { template: '<div><slot /></div>' },
        DateRangePresetSelect: { template: '<div />' }
      }
    }
  })
}

describe('DateRangePicker typed input', () => {
  beforeEach(() => {
    useTimezone().setDateFormat('MDY')
  })

  it('accepts typed ranges by default', async () => {
    const input = (await mountPicker()).get('.date-picker-stub')

    expect(input.attributes('data-manual-input')).toBe('true')
    // The placeholder teaches the exact `start - end` shape PrimeVue parses.
    expect(input.attributes('data-placeholder')).toBe('MM/DD/YYYY - MM/DD/YYYY')
  })

  it('still allows picker-only consumers to opt out', async () => {
    const input = (await mountPicker({ manualInput: false })).get('.date-picker-stub')

    expect(input.attributes('data-manual-input')).toBe('false')
  })
})
