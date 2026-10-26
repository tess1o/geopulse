import { mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it } from 'vitest'
import { movementTypeOptions } from '@/composables/useTripReconstructionSegments'

/**
 * Guards the movement-type option contract.
 *
 * The shared `movementTypeOptions` table carries `labelKey` (not `label`), so every consumer has to
 * resolve the label itself -- PrimeVue's `optionLabel` reads a field and cannot call `t()`. Nothing
 * else covers this component, so a consumer left reading `.label` would silently render blank
 * options in the trip reconstruction editor.
 */
const SelectStub = {
  props: ['options', 'optionLabel', 'modelValue'],
  template: '<div class="select-stub"><span v-for="o in (options || [])" :key="o.value" class="opt" :data-value="o.value" :data-label="o[optionLabel]">{{ o[optionLabel] }}</span></div>'
}

const mountPanel = (Panel) => mount(Panel, {
  props: {
    reconstructionHelpMessage: 'help',
    segments: [{ movementType: 'CAR', segmentType: 'MOVE', waypoints: [] }],
    activeSegmentId: null,
    segmentTypeOptions: [{ label: 'Move', value: 'MOVE' }],
    movementTypeOptions,
    timezone: { getPrimeVueDatePickerFormat: () => 'dd/mm/yy', getTimeFormat: () => '24h' },
    locationSourceLabel: () => '',
    waypointLabel: () => '',
    waypointTagSeverity: () => 'info',
    disabled: false
  },
  global: { stubs: { Select: SelectStub, DatePicker: true, InputNumber: true, InputText: true, Button: true, Tag: true, Message: true } }
})

describe('TripReconstructionSegmentsPanel movement types', () => {
  let Panel

  beforeEach(async () => {
    Panel = (await import('./TripReconstructionSegmentsPanel.vue')).default
  })

  it('renders English labels from the keyed option table', () => {
    const options = mountPanel(Panel).findAll('.opt')

    expect(options.length).toBeGreaterThan(0)
    expect(options.map(o => o.attributes('data-label'))).toContain('Car')
    expect(options.map(o => o.attributes('data-label'))).toContain('Public Transportation')
    // A raw key leaking through would mean the label was never resolved.
    expect(options.some(o => /^movementTypes\./.test(o.attributes('data-label')))).toBe(false)
  })

  it('renders Ukrainian labels once the locale is switched', async () => {
    const { setLocale } = await import('@/composables/useLocale')
    await setLocale('uk', { persist: false })

    const labels = mountPanel(Panel).findAll('.opt').map(o => o.attributes('data-label'))

    expect(labels).toContain('Автомобіль')
    expect(labels).toContain('Громадський транспорт')
    // The enum values behind the labels must be untouched by translation.
    expect(mountPanel(Panel).findAll('.opt').map(o => o.attributes('data-value'))).toContain('CAR')
  })
})

const DatePickerStub = {
  name: 'DatePicker',
  props: {
    modelValue: { default: null },
    manualInput: { type: Boolean, default: false },
    hourFormat: { default: null },
    dateFormat: { default: null },
    inputId: { default: null },
    invalid: { type: Boolean, default: false }
  },
  template: '<input class="date-picker-stub" :data-input-id="inputId" :data-manual-input="String(manualInput)" :data-hour-format="hourFormat" :data-invalid="String(invalid)" />'
}

const timeSegment = {
  id: 42,
  segmentType: 'STAY',
  startTime: new Date('2026-10-24T10:00:00Z'),
  endTime: new Date('2026-10-24T11:00:00Z'),
  locationName: '',
  latitude: 52.5,
  longitude: 13.4,
  movementType: 'CAR',
  waypoints: []
}

const mountTimePanel = (Panel, { timeFormat = '24h', segment = timeSegment } = {}) => mount(Panel, {
  props: {
    reconstructionHelpMessage: 'help',
    segments: [segment],
    activeSegmentId: null,
    segmentTypeOptions: [{ label: 'Stay', value: 'STAY' }],
    movementTypeOptions,
    timezone: { getPrimeVueDatePickerFormat: () => 'dd/mm/yy', getTimeFormat: () => timeFormat },
    locationSourceLabel: () => '',
    waypointLabel: () => '',
    waypointTagSeverity: () => 'info'
  },
  global: {
    stubs: {
      Select: SelectStub,
      DatePicker: DatePickerStub,
      InputNumber: true,
      InputText: true,
      Button: true,
      Tag: true,
      Message: true
    }
  }
})

describe('TripReconstructionSegmentsPanel date-time inputs', () => {
  let Panel

  beforeEach(async () => {
    Panel = (await import('./TripReconstructionSegmentsPanel.vue')).default
  })

  it('allows typing into both date-time fields', () => {
    const inputs = mountTimePanel(Panel).findAll('.date-picker-stub')

    expect(inputs).toHaveLength(2)
    expect(inputs.every(input => input.attributes('data-manual-input') === 'true')).toBe(true)
  })

  it('uses the profile 12/24-hour format', () => {
    const inputs = mountTimePanel(Panel, { timeFormat: '12h' }).findAll('.date-picker-stub')

    expect(inputs.map(input => input.attributes('data-hour-format'))).toEqual(['12', '12'])
  })

  it('associates the time labels with their fields', () => {
    const wrapper = mountTimePanel(Panel)

    expect(wrapper.find('label[for="trip-reconstruction-segment-42-start-time"]').exists()).toBe(true)
    expect(wrapper.find('label[for="trip-reconstruction-segment-42-end-time"]').exists()).toBe(true)
  })

  it('marks an end time before the start as invalid', () => {
    const wrapper = mountTimePanel(Panel, {
      segment: { ...timeSegment, endTime: new Date('2026-10-24T09:00:00Z') }
    })
    const inputs = wrapper.findAll('.date-picker-stub')

    expect(inputs[0].attributes('data-invalid')).toBe('false')
    expect(inputs[1].attributes('data-invalid')).toBe('true')
    expect(wrapper.text()).toContain('End time must be after the start time.')
  })
})
