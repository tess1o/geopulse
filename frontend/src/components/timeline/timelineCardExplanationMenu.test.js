import { shallowMount } from '@vue/test-utils'
import { createPinia } from 'pinia'
import { describe, expect, it } from 'vitest'
import DataGapCard from './DataGapCard.vue'
import OvernightDataGapCard from './OvernightDataGapCard.vue'
import OvernightStayCard from './OvernightStayCard.vue'
import OvernightTripCard from './OvernightTripCard.vue'
import StayCard from './StayCard.vue'
import TripCard from './TripCard.vue'
import TimelineExplanationTrigger from './TimelineExplanationTrigger.vue'

const stay = {
  id: 5,
  type: 'stay',
  timestamp: '2026-10-08T22:00:00Z',
  stayDuration: 36_000,
  latitude: 49.84,
  longitude: 24.03,
  locationName: 'Home'
}
const trip = {
  id: 6,
  type: 'trip',
  timestamp: '2026-10-08T23:00:00Z',
  tripDuration: 7_200,
  distanceMeters: 12_000,
  movementType: 'CAR',
  latitude: 49.84,
  longitude: 24.03,
  endLatitude: 49.9,
  endLongitude: 24.1
}
const dataGap = {
  id: 7,
  type: 'dataGap',
  startTime: '2026-10-08T22:00:00Z',
  endTime: '2026-10-09T06:00:00Z',
  durationSeconds: 28_800
}

const cards = [
  ['StayCard', StayCard, { stayItem: stay }, stay, 'Why this stay?'],
  ['OvernightStayCard', OvernightStayCard, { stayItem: stay, currentDate: '2026-10-09' }, stay, 'Why this stay?'],
  ['TripCard', TripCard, { tripItem: trip }, trip, 'Why this trip?'],
  ['OvernightTripCard', OvernightTripCard, { tripItem: trip, currentDate: '2026-10-09' }, trip, 'Why this trip?'],
  ['DataGapCard', DataGapCard, { dataGapItem: dataGap }, dataGap, 'Why a data gap?'],
  ['OvernightDataGapCard', OvernightDataGapCard, { dataGapItem: dataGap, currentDate: '2026-10-09' }, dataGap, 'Why a data gap?']
]

const mountCard = (component, props) => shallowMount(component, {
  props,
  global: {
    plugins: [createPinia()],
    stubs: {
      ContextMenu: { name: 'ContextMenu', props: ['model'], template: '<div />' },
      // Renders the card's slots, so the icon in the title row is there
      Card: { template: '<div><slot name="title" /><slot name="subtitle" /><slot name="content" /></div>' }
    }
  }
})

const menuItems = (wrapper) => wrapper.findComponent({ name: 'ContextMenu' }).props('model')

describe('timeline card explanation menu item', () => {
  it.each(cards)('%s opens the explanation dialog for its item', (_, component, props, item, label) => {
    const wrapper = mountCard(component, props)

    const explain = menuItems(wrapper).find(entry => entry.label === label)
    expect(explain).toBeTruthy()
    explain.command()

    expect(wrapper.emitted('show-explanation')).toEqual([[item]])
  })

  it.each(cards)('%s has no explanation item where the dialog is not available', (_, component, props, __, label) => {
    const wrapper = mountCard(component, { ...props, allowExplanation: false })
    expect(menuItems(wrapper).find(entry => entry.label === label)).toBeUndefined()
  })
})

describe('timeline card explanation icon', () => {
  it.each(cards)('%s has an info icon that opens the explanation dialog', (_, component, props, item) => {
    const wrapper = mountCard(component, props)

    const trigger = wrapper.findComponent(TimelineExplanationTrigger)
    expect(trigger.exists()).toBe(true)
    trigger.vm.$emit('show-explanation')

    expect(wrapper.emitted('show-explanation')).toEqual([[item]])
  })

  it.each(cards)('%s has no info icon where the dialog is not available', (_, component, props) => {
    const wrapper = mountCard(component, { ...props, allowExplanation: false })
    expect(wrapper.findComponent(TimelineExplanationTrigger).exists()).toBe(false)
  })
})
