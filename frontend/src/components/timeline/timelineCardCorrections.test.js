import OvernightDataGapCard from './OvernightDataGapCard.vue'
import { shallowMount } from '@vue/test-utils'
import { createPinia } from 'pinia'
import { describe, expect, it, vi } from 'vitest'
import DataGapCard from './DataGapCard.vue'
import OvernightStayCard from './OvernightStayCard.vue'
import StayCard from './StayCard.vue'
import TripCard from './TripCard.vue'
import OvernightTripCard from './OvernightTripCard.vue'

vi.mock('vue-router', () => ({ useRouter: () => ({ push: vi.fn() }) }))

const base = { id: 5, timestamp: '2026-10-08T22:00:00Z', latitude: 49.84, longitude: 24.03 }
const stay = { ...base, type: 'stay', stayDuration: 3600, locationName: 'Home', activeEditCount: 2 }
const trip = { ...base, type: 'trip', tripDuration: 3600, movementType: 'CAR', movementTypeSource: 'AUTO' }
const gap = { id: 7, type: 'dataGap', startTime: base.timestamp, endTime: '2026-10-09T06:00:00Z', durationSeconds: 28800 }
const menuStub = { name: 'ContextMenu', props: ['model'], template: '<div />' }
const triggerStub = { name: 'TimelineEditTrigger', props: ['label', 'count', 'readOnly', 'showEdit'], emits: ['edit', 'history'], template: '<button v-if="showEdit !== false" class="edit-stub" @click="$emit(\'edit\')" /><button v-else class="history-stub" />' }

const mountCard = (component, props) => shallowMount(component, {
  props,
  global: { plugins: [createPinia()], stubs: { Card: { template: '<div><slot name="title"/><slot name="subtitle"/><slot name="content"/></div>' }, ContextMenu: menuStub, TimelineEditTrigger: triggerStub } }
})
const editEntries = wrapper => wrapper.findComponent({ name: 'ContextMenu' }).props('model')
  .filter(item => item.icon === 'pi pi-pencil')

describe.each([
  ['stay', StayCard, { stayItem: stay }, 'Edit visit…'],
  ['trip', TripCard, { tripItem: trip }, 'Edit trip…']
])('%s editing entry', (_name, component, props, label) => {
  it('offers one pencil and one context-menu entry', async () => {
    const wrapper = mountCard(component, props)
    expect(wrapper.findAll('.edit-stub')).toHaveLength(1)
    expect(editEntries(wrapper).map(item => item.label)).toEqual([label])
    await wrapper.find('.edit-stub').trigger('click')
    expect(wrapper.emitted('edit')).toHaveLength(1)
  })

  it('hides editing in read-only mode', () => {
    const wrapper = mountCard(component, { ...props, readOnly: true })
    expect(wrapper.find('.edit-stub').exists()).toBe(true)
    expect(wrapper.findComponent({ name: 'TimelineEditTrigger' }).props('readOnly')).toBe(true)
    expect(editEntries(wrapper)).toHaveLength(0)
  })
})

it('uses the same logical edit affordance for an overnight card', () => {
  const wrapper = mountCard(OvernightStayCard, { stayItem: stay, currentDate: '2026-10-09' })
  expect(wrapper.findAllComponents({ name: 'TimelineEditTrigger' }).map(trigger => trigger.props('count'))).toEqual([0, 2])
  expect(editEntries(wrapper).map(item => item.label)).toEqual(['Edit visit…'])
})

it.each([StayCard, OvernightStayCard])('keeps the place visible alongside the visit label', component => {
  const wrapper = mountCard(component, { stayItem: { ...stay, visitName: 'Test' }, currentDate: '2026-10-09' })
  expect(wrapper.find('.location-name').text()).toBe('Home')
  expect(wrapper.find('.visit-label').text()).toBe('Test')
})


it.each([TripCard, OvernightTripCard])('uses only the pencil to edit a manually adjusted trip', async component => {
  const editedTrip = { ...trip, movementType: 'WALK', movementTypeSource: 'MANUAL', activeEditCount: 2 }
  const wrapper = mountCard(component, { tripItem: editedTrip, ...(component === OvernightTripCard ? { currentDate: '2026-10-09' } : {}) })
  expect(wrapper.find('span.movement-edited-indicator').text()).toBe('Edited')
  expect(wrapper.find('button.movement-edited-indicator').exists()).toBe(false)
  expect(wrapper.find('.timeline-edit-metadata').exists()).toBe(false)
  expect(wrapper.findAllComponents({ name: 'TimelineEditTrigger' })).toHaveLength(1)
  expect(wrapper.findComponent({ name: 'TimelineEditTrigger' }).props('count')).toBe(0)
  await wrapper.find('.edit-stub').trigger('click')
  expect(wrapper.emitted('edit')[0]).toEqual([editedTrip])
  expect(wrapper.emitted('show-edit-history')).toBeUndefined()
})
it.each([TripCard, OvernightTripCard])('does not mark an automatic movement as edited', component => {
  const wrapper = mountCard(component, { tripItem: { ...trip, activeEditCount: 1 }, currentDate: '2026-10-09' })
  expect(wrapper.find('.movement-edited-indicator').exists()).toBe(false)
})
it.each([TripCard, OvernightTripCard])('shows a noninteractive Edited label in read-only views', component => {
  const wrapper = mountCard(component, { tripItem: { ...trip, movementTypeSource: 'MANUAL' }, currentDate: '2026-10-09', readOnly: true })
  expect(wrapper.find('span.movement-edited-indicator').text()).toBe('Edited')
  expect(wrapper.find('button.movement-edited-indicator').exists()).toBe(false)
})

// The real trigger is tested separately; this checks that every card forwards read-only state.
it.each([
  [StayCard, { stayItem: stay }], [TripCard, { tripItem: trip }], [DataGapCard, { dataGapItem: gap }],
  [OvernightStayCard, { stayItem: stay, currentDate: '2026-10-09' }],
  [OvernightTripCard, { tripItem: trip, currentDate: '2026-10-09' }],
  [OvernightDataGapCard, { dataGapItem: gap, currentDate: '2026-10-09' }]
])('hides edit menu actions and forwards read-only state for every card', (component, props) => {
  const wrapper = mountCard(component, { ...props, readOnly: true })
  expect(editEntries(wrapper)).toEqual([])
  for (const trigger of wrapper.findAllComponents({ name: 'TimelineEditTrigger' })) expect(trigger.props('readOnly')).toBe(true)
})
