import { shallowMount } from '@vue/test-utils'
import { createPinia } from 'pinia'
import { describe, expect, it } from 'vitest'
import DataGapCard from './DataGapCard.vue'
import OvernightDataGapCard from './OvernightDataGapCard.vue'
import TimelineEditTrigger from './TimelineEditTrigger.vue'

const gap = { id: 42, type: 'dataGap', startTime: '2026-10-01T10:00:00Z', endTime: '2026-10-02T11:00:00Z', durationSeconds: 90000, activeEditCount: 2 }

describe.each([['regular', DataGapCard], ['overnight', OvernightDataGapCard]])('%s data gap editing', (_, component) => {
  it.each([false, true])('shows editing only for completed gaps (ongoing=%s), keeping history', async ongoing => {
    const item = { ...gap, ongoing }
    const wrapper = shallowMount(component, {
      props: { dataGapItem: item, currentDate: '2026-10-01' },
      global: { plugins: [createPinia()], renderStubDefaultSlot: true, stubs: { TimelineEditTrigger: false, Card: { template: '<div><slot name="title"/><slot name="content"/></div>' } } }
    })
    expect(wrapper.find('.timeline-edit-button').exists()).toBe(false)
    const addMissing = wrapper.find('[data-testid="add-missing-data"]')
    expect(addMissing.exists()).toBe(!ongoing)
    if (addMissing.exists()) {
      await addMissing.trigger('click')
      expect(wrapper.emitted('reconstruct')).toEqual([[item]])
    }
    await wrapper.find('.timeline-edit-count').trigger('click')
    expect(wrapper.emitted('show-edit-history')).toEqual([[item]])
    wrapper.unmount()
  })
  it.each([false, true])('hides editing and history in read-only views (ongoing=%s)', ongoing => {
    const wrapper = shallowMount(component, {
      props: { dataGapItem: { ...gap, ongoing }, currentDate: '2026-10-01', readOnly: true },
      global: { plugins: [createPinia()], stubs: { TimelineEditTrigger: false, Card: { template: '<div><slot name="title"/><slot name="content"/></div>' } } }
    })
    expect(wrapper.findComponent(TimelineEditTrigger).exists()).toBe(true)
    expect(wrapper.find('.timeline-edit-button').exists()).toBe(false)
    expect(wrapper.find('[data-testid="add-missing-data"]').exists()).toBe(false)
    expect(wrapper.find('.timeline-edit-count').exists()).toBe(false)
    wrapper.unmount()
  })
})
