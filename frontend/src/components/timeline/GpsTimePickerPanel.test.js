import { mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import Panel from './GpsTimePickerPanel.vue'
import { buildGpsTimePickerPoints } from '@/utils/gpsTimePicker'

vi.mock('@/composables/useTimezone', () => ({
  useTimezone: () => ({ formatDateDisplay: value => value.slice(0, 10), formatTime: value => value.slice(11, 19) })
}))

const at = seconds => ({ latitude: 53.959, longitude: -1.08, velocity: 5, battery: 97,
  timestamp: new Date(Date.UTC(2026, 10, 10, 10, 43, 0) + seconds * 1000).toISOString() })
const points = buildGpsTimePickerPoints([0, 5, 10, 387, 392].map(at))
const StripStub = { name: 'GpsTimeStrip', props: ['points', 'range', 'startTime', 'endTime', 'visible', 'otherRanges'], emits: ['update:range', 'inspect', 'hover'], template: '<div class="strip" />' }
const ButtonStub = { props: ['label', 'disabled'], emits: ['click'], template: '<button :disabled="disabled" @click="$emit(\'click\')">{{ label }}</button>' }
const mountPanel = props => mount(Panel, {
  props: { points, range: { from: null, to: null }, ...props },
  global: { stubs: { GpsTimeStrip: StripStub, Button: ButtonStub } }
})

beforeEach(() => setActivePinia(createPinia()))
const selectionParts = wrapper => wrapper.findAll('.gps-picker-selection > span').map(node => node.text())

describe('GpsTimePickerPanel', () => {
  it('lists every sample in time order with its map number and marks the pause between passes', () => {
    const wrapper = mountPanel()
    expect(wrapper.findAll('.gps-picker-number').map(node => node.text())).toEqual(['1', '2', '3', '4', '5'])
    expect(wrapper.findAll('.gps-picker-time').map(node => node.text())).toEqual(['10:43:00', '10:43:05', '10:43:10', '10:49:27', '10:49:32'])
    const gap = wrapper.find('.gps-picker-gap')
    expect(gap.text()).toBe('6 min 17 s without points')
    expect(gap.element.nextElementSibling.dataset.pointIndex).toBe('3')
    expect(wrapper.find('.gps-picker-header').text()).toContain('5 GPS points · 10:43:00 – 10:49:32')
  })

  it('syncs hover and inspection with the map and offers From/To only for the inspected sample', async () => {
    const wrapper = mountPanel({ inspectedIndex: 2, hoveredIndex: 4 })
    expect(wrapper.find('[data-point-index="4"]').classes()).toContain('is-hovered')
    expect(wrapper.findAll('.gps-picker-actions')).toHaveLength(1)
    const row = wrapper.find('[data-point-index="1"] .gps-picker-row-main')
    await row.trigger('mouseenter')
    await row.trigger('click')
    expect(wrapper.emitted('hover').at(-1)).toEqual([1])
    expect(wrapper.emitted('inspect').at(-1)).toEqual([1])
    await wrapper.find('.gps-picker-list').trigger('mouseleave')
    expect(wrapper.emitted('hover').at(-1)).toEqual([null])

    const [setFrom, setTo] = wrapper.find('.gps-picker-actions').findAll('button')
    await setFrom.trigger('click')
    expect(wrapper.emitted('update:range').at(-1)).toEqual([{ from: 2, to: null }])
    await wrapper.setProps({ range: { from: 3, to: 4 } })
    await setTo.trigger('click')
    expect(wrapper.emitted('update:range').at(-1)).toEqual([{ from: null, to: 2 }])
  })

  it('tags From and To rows and highlights the samples between them', () => {
    const wrapper = mountPanel({ range: { from: 1, to: 3 } })
    expect(wrapper.find('[data-point-index="1"] .gps-picker-tag--from').exists()).toBe(true)
    expect(wrapper.find('[data-point-index="3"] .gps-picker-tag--to').exists()).toBe(true)
    expect(wrapper.findAll('.gps-picker-row.is-in-range').map(row => row.attributes('data-point-index'))).toEqual(['1', '2', '3'])
  })

  it('shows the selected interval above the strip and passes strip changes through', async () => {
    const wrapper = mountPanel({ range: { from: 1, to: 3 }, startTime: points[1].time, endTime: points[3].time })
    expect(selectionParts(wrapper)).toEqual(['From 10:43:05', '→', 'To 10:49:27', '6 min 22 s'])
    const strip = wrapper.findComponent(StripStub)
    expect([strip.props('startTime'), strip.props('endTime')]).toEqual([points[1].time, points[3].time])
    strip.vm.$emit('update:range', { from: 2, to: 3 })
    strip.vm.$emit('inspect', 4)
    expect(wrapper.emitted('update:range')).toEqual([[{ from: 2, to: 3 }]])
    expect(wrapper.emitted('inspect')).toEqual([[4]])
    await wrapper.setProps({ range: { from: null, to: null }, startTime: null, endTime: null })
    expect(selectionParts(wrapper)).toEqual(['From Not chosen', '→', 'To Not chosen'])
  })
  it('lists only the visible window and offers to show the hidden points on each side', async () => {
    const wrapper = mountPanel({ visible: { from: 1, to: 3 } })
    expect(wrapper.findAll('.gps-picker-number').map(node => node.text())).toEqual(['2', '3', '4'])
    const hidden = wrapper.findAll('.gps-picker-hidden')
    expect(hidden.map(row => row.find('span').text())).toEqual(['1 earlier point hidden', '1 later point hidden'])
    await hidden[0].find('button').trigger('click')
    await hidden[1].find('button').trigger('click')
    expect(wrapper.emitted('expand')).toEqual([['before'], ['after']])
    expect(wrapper.findComponent(StripStub).props('visible')).toEqual({ from: 1, to: 3 })
  })
  it('offers place actions on the selected point and only places when times are fixed', async () => {
    const placeActions = [{ key: 'stay', label: 'Use as location' }]
    const wrapper = mountPanel({ inspectedIndex: 1, placeActions })
    const labels = () => wrapper.findAll('.gps-picker-actions button').map(button => button.text())
    expect(labels()).toEqual(['Set as From', 'Set as To', 'Use as location'])
    await wrapper.findAll('.gps-picker-actions button')[2].trigger('click')
    expect(wrapper.emitted('place')).toEqual([['stay', 1]])
    await wrapper.setProps({ timesEditable: false })
    expect(labels()).toEqual(['Use as location'])
    expect(wrapper.find('.gps-picker-selection').exists()).toBe(false)
    expect(wrapper.findComponent(StripStub).exists()).toBe(false)
    await wrapper.setProps({ placeActions: [] })
    expect(wrapper.find('.gps-picker-actions').exists()).toBe(false)
  })
})
