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
    timezone: { getPrimeVueDatePickerFormat: () => 'dd/mm/yy' },
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
