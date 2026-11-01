import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import TimelineEditTrigger from './TimelineEditTrigger.vue'
describe('TimelineEditTrigger', () => {
  it.each([[1, '1 change'], [2, '2 changes']])('names its changes action for count %s', async (count, text) => {
    const wrapper = mount(TimelineEditTrigger, { props: { label: 'Edit visit', count } })
    expect(wrapper.find('.timeline-edit-count').text()).toBe(text)
    await wrapper.find('.timeline-edit-count').trigger('click')
    expect(wrapper.emitted('history')).toHaveLength(1)
    expect(wrapper.emitted('edit')).toBeUndefined()
  })
  it('does not expose editing controls in a read-only view', () => {
    const wrapper = mount(TimelineEditTrigger, { props: { label: 'Edit visit', count: 2, readOnly: true } })
    expect(wrapper.find('button').exists()).toBe(false)
  })
})
