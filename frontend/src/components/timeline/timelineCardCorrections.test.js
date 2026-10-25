import { shallowMount } from '@vue/test-utils'
import { createPinia } from 'pinia'
import { describe, expect, it } from 'vitest'
import DataGapCard from './DataGapCard.vue'
import OvernightStayCard from './OvernightStayCard.vue'
import StayCard from './StayCard.vue'

const stay = () => ({
  id: 5,
  type: 'stay',
  timestamp: '2026-10-08T22:00:00Z',
  stayDuration: 3_600,
  latitude: 49.84,
  longitude: 24.03,
  locationName: 'Home'
})

const dataGap = () => ({
  id: 7,
  type: 'dataGap',
  startTime: '2026-10-08T22:00:00Z',
  endTime: '2026-10-09T06:00:00Z',
  durationSeconds: 28_800
})

const cardStub = {
  template: '<div><slot name="subtitle" /><slot name="content" /></div>'
}

const menuStub = {
  name: 'ContextMenu',
  props: ['model'],
  template: '<div />'
}

const mountCard = (component, props) => shallowMount(component, {
  props,
  global: {
    plugins: [createPinia()],
    stubs: {
      Card: cardStub,
      ContextMenu: menuStub
    }
  }
})

const resetButtons = (wrapper) => wrapper.findAll('.location-reset-icon-btn')

const resetMenuItems = (wrapper) => wrapper.findComponent({ name: 'ContextMenu' })
  .props('model')
  .filter(item => item.icon === 'pi pi-refresh')

describe('stay correction actions', () => {
  it('offers a separate reset for the split and for the gap conversion on one stay', async () => {
    const wrapper = mountCard(StayCard, {
      stayItem: { ...stay(), tripSplitOverrideId: 41, dataGapOverrideIds: [42] }
    })

    expect(resetButtons(wrapper)).toHaveLength(2)
    expect(resetButtons(wrapper).map(button => button.attributes('aria-label')))
      .toEqual(['Undo manual trip split', 'Reset to automatic data gap'])

    await resetButtons(wrapper)[0].trigger('click')
    await resetButtons(wrapper)[1].trigger('click')

    expect(wrapper.emitted('reset-trip-split-override')[0][1]).toBe(41)
    expect(wrapper.emitted('reset-data-gap-override')[0][1]).toBe(42)
  })

  it('offers one reset for every data gap that converted into the stay', async () => {
    const wrapper = mountCard(StayCard, {
      stayItem: { ...stay(), dataGapOverrideIds: [42, 43] }
    })

    expect(resetButtons(wrapper)).toHaveLength(2)
    expect(resetMenuItems(wrapper).map(item => item.label)).toEqual([
      'Reset data gap correction 1 of 2',
      'Reset data gap correction 2 of 2'
    ])

    await resetButtons(wrapper)[0].trigger('click')
    await resetButtons(wrapper)[1].trigger('click')

    expect(wrapper.emitted('reset-data-gap-override').map(([, overrideId]) => overrideId))
      .toEqual([42, 43])
    expect(wrapper.emitted('reset-trip-split-override')).toBeUndefined()
  })

  it('keeps every correction action visible but disabled in a read-only view', async () => {
    const wrapper = mountCard(OvernightStayCard, {
      stayItem: { ...stay(), tripSplitOverrideId: 41, dataGapOverrideIds: [42, 43] },
      currentDate: '2026-10-09',
      readOnly: true
    })

    expect(resetButtons(wrapper)).toHaveLength(3)
    for (const button of resetButtons(wrapper)) {
      expect(button.attributes('disabled')).toBeDefined()
      await button.trigger('click')
    }

    expect(wrapper.emitted('reset-trip-split-override')).toBeUndefined()
    expect(wrapper.emitted('reset-data-gap-override')).toBeUndefined()
  })
})

describe('data gap conversion in a read-only view', () => {
  it('hides the convert action', () => {
    const wrapper = mountCard(DataGapCard, {
      dataGapItem: dataGap(),
      readOnly: true
    })

    expect(wrapper.find('.convert-gap-btn').exists()).toBe(false)
  })

  it('shows the convert action where the timeline may be edited', () => {
    const wrapper = mountCard(DataGapCard, {
      dataGapItem: dataGap()
    })

    expect(wrapper.find('.convert-gap-btn').exists()).toBe(true)
  })
})
