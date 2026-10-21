import { mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { nextTick } from 'vue'
import FocusedDayChip from './FocusedDayChip.vue'
import MapColorLegend from '@/components/maps/MapColorLegend.vue'
import { useHighlightStore } from '@/stores/highlight'
import { useTimezone } from '@/composables/useTimezone'

describe('focused day controls', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    useTimezone().setTimezone('UTC')
  })

  it('shows the focused day with its color and shows all days again on click', async () => {
    const store = useHighlightStore()
    const wrapper = mount(FocusedDayChip, { props: { dayColors: { '2026-10-16': '#1f77b4' } } })
    expect(wrapper.find('button').exists()).toBe(false)

    store.focusDay('2026-10-16')
    await nextTick()
    expect(wrapper.text()).toContain('Showing Fri 16')
    expect(wrapper.find('.focused-day-chip-color').attributes('style')).toContain('background-color')

    await wrapper.find('button').trigger('click')
    expect(store.focusedDay).toBeNull()
  })

  it('makes legend days toggle buttons that mark the focused day', async () => {
    const days = [{ dayKey: '2026-10-15', color: '#1f77b4' }, { dayKey: '2026-10-16', color: '#2ca02c' }]
    const wrapper = mount(MapColorLegend, {
      props: { variant: 'days', days, daysInteractive: true, focusedDay: '2026-10-16' }
    })

    const buttons = wrapper.findAll('button')
    expect(buttons).toHaveLength(2)
    expect(buttons[1].attributes('aria-pressed')).toBe('true')
    expect(buttons[0].classes()).toContain('map-color-legend-day--muted')

    await buttons[0].trigger('click')
    expect(wrapper.emitted('day-click')).toEqual([['2026-10-15']])
  })

  it('keeps the gradient legend and a non-interactive legend free of buttons', () => {
    const days = [{ dayKey: '2026-10-15', color: '#1f77b4' }, { dayKey: '2026-10-16', color: '#2ca02c' }]
    expect(mount(MapColorLegend, { props: { variant: 'days', days } }).findAll('button')).toHaveLength(0)
    expect(mount(MapColorLegend, {
      props: { variant: 'days', days, daysMode: 'gradient', daysInteractive: true }
    }).findAll('button')).toHaveLength(0)
  })
})
