import { flushPromises, shallowMount } from '@vue/test-utils'
import { createPinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import TimelineContainer from './TimelineContainer.vue'
import { useTimelineStore } from '@/stores/timeline'
const { addToast } = vi.hoisted(() => ({ addToast: vi.fn() }))
vi.mock('../dialogs/TripClassificationDialog.vue', () => ({ default: { template: '<div />' } }))
vi.mock('../dialogs/TimelineEventExplanationDialog.vue', () => ({ default: { template: '<div />' } }))
vi.mock('../dialogs/TripStaySplitDialog.vue', () => ({ default: { template: '<div />' } }))
vi.mock('../dialogs/TripMapMatchingDetailsDialog.vue', () => ({ default: { template: '<div />' } }))
vi.mock('primevue/usetoast', () => ({ useToast: () => ({ add: addToast }) }))
const range = ['2026-10-25T00:00:00Z', '2026-10-26T00:00:00Z']
const edit = (id, extra = {}) => ({ id, status: 'ACTIVE', anchorStart: '2026-10-25T15:23:00Z', anchorEnd: '2026-10-25T15:25:00Z', ...extra })
function setup(edits = [], props = {}) {
  const pinia = createPinia()
  const fetch = vi.spyOn(useTimelineStore(pinia), 'fetchTimelineEdits').mockResolvedValue(edits)
  const wrapper = shallowMount(TimelineContainer, {
    props: { dateRange: range, showTimelineLabels: false, loadNotes: false, loadImmichPhotos: false, ...props },
    global: { plugins: [pinia], stubs: { Button: { template: '<button><slot /></button>' } } }
  })
  return { wrapper, fetch }
}
const button = wrapper => wrapper.find('.timeline-history-button')
const drawer = wrapper => wrapper.findComponent({ name: 'TimelineEditHistoryDrawer' })
beforeEach(() => addToast.mockClear())
describe('selected timeframe history', () => {
  it('uses affected intervals or anchors and shares the count and drawer list', async () => {
    const matching = [edit(1), edit(2, { status: 'REVERTED' }), edit(3, { applied: false, target: null }), edit(4, { status: 'SUPERSEDED' }),
      edit(5, { anchorStart: '2026-10-24T23:00:00Z', affectedFrom: '2026-10-24T23:00:00Z', affectedTo: '2026-10-25T01:00:00Z' })]
    const { wrapper } = setup([...matching,
      edit(6, { affectedFrom: '2026-10-27T00:00:00Z', affectedTo: '2026-10-28T00:00:00Z' }),
      edit(7, { anchorStart: range[1], anchorEnd: '2026-10-26T01:00:00Z' }),
      edit(8, { anchorStart: '2026-10-24T23:00:00Z', anchorEnd: range[0] })], { timelineData: [{ activeEditCount: 20 }, { activeEditCount: 20 }] })
    expect(button(wrapper).exists()).toBe(false)
    await flushPromises()
    expect(button(wrapper).text()).toBe('5')
    expect(drawer(wrapper).props('edits')).toEqual(matching)
    await button(wrapper).trigger('click')
    expect(drawer(wrapper).props('visible')).toBe(true)
  })
  it.each(['REVERTED', 'SUPERSEDED', 'ACTIVE'])('keeps %s history reachable without visible cards', async status => {
    const { wrapper } = setup([edit(1, { status, applied: false })])
    await flushPromises()
    expect(button(wrapper).text()).toBe('1')
  })
  it('hides empty history and reports failures', async () => {
    const { wrapper, fetch } = setup()
    await flushPromises()
    expect(button(wrapper).exists()).toBe(false)
    fetch.mockRejectedValue(new Error('history unavailable'))
    await wrapper.setProps({ timelineData: [{ id: 1 }] })
    await flushPromises()
    expect(button(wrapper).exists()).toBe(false)
    expect(addToast).toHaveBeenCalledWith(expect.objectContaining({ severity: 'error', detail: 'history unavailable' }))
  })
  it.each([{ readOnly: true }, { isPublicView: true }])('does not fetch for read-only views: %o', async props => {
    const { wrapper, fetch } = setup([edit(1)], props)
    await flushPromises()
    expect(fetch).not.toHaveBeenCalled()
    expect(button(wrapper).exists()).toBe(false)
  })
  it('refreshes on dates, data windows, timeline data and completed actions', async () => {
    const { wrapper, fetch } = setup([edit(1)])
    await flushPromises()
    await wrapper.setProps({ dateRange: ['2026-10-27T00:00:00Z', '2026-10-28T00:00:00Z'] })
    await flushPromises()
    expect(button(wrapper).exists()).toBe(false)
    await wrapper.setProps({ dataWindow: range })
    await flushPromises()
    expect(button(wrapper).text()).toBe('1')
    fetch.mockResolvedValue([])
    await wrapper.setProps({ timelineData: [{ id: 2 }] })
    await flushPromises()
    expect(button(wrapper).exists()).toBe(false)
    fetch.mockResolvedValue([edit(2)])
    const onUpdated = vi.fn()
    drawer(wrapper).vm.$emit('changed', { operation: 'undo', onUpdated })
    await wrapper.emitted('timeline-edit-submitted')[0][0].onUpdated()
    await flushPromises()
    expect(onUpdated).toHaveBeenCalledOnce()
    expect(button(wrapper).text()).toBe('1')
    expect(fetch).toHaveBeenCalledTimes(5)
  })
  it('ignores stale responses when dates or permissions change', async () => {
    const { wrapper, fetch } = setup()
    await flushPromises()
    let resolve
    fetch.mockImplementationOnce(() => new Promise(done => { resolve = done }))
    await wrapper.setProps({ timelineData: [{ id: 1 }] })
    await wrapper.setProps({ dateRange: ['2026-10-27T00:00:00Z', '2026-10-28T00:00:00Z'] })
    await flushPromises()
    resolve([edit(1)])
    await flushPromises()
    expect(button(wrapper).exists()).toBe(false)
    fetch.mockImplementationOnce(() => new Promise(done => { resolve = done }))
    await wrapper.setProps({ dateRange: range })
    await wrapper.setProps({ readOnly: true })
    resolve([edit(1)])
    await flushPromises()
    expect(drawer(wrapper).props('edits')).toEqual([])
  })
})
