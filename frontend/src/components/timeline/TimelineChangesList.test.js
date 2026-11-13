import { flushPromises, mount } from '@vue/test-utils'
import { createPinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import TimelineChangesList from './TimelineChangesList.vue'
import { useTimelineStore } from '@/stores/timeline'
const confirmation = vi.hoisted(() => ({ require: vi.fn() }))
vi.mock('primevue/useconfirm', () => ({ useConfirm: () => confirmation }))
const start = '2026-10-08T10:00:00Z'
const end = '2026-10-08T11:00:00Z'
const item = { id: 5, type: 'stay', timestamp: start, stayDuration: 3600 }
const edit = { id: 1, seq: 1, kind: 'SET_VISIT_NAME', status: 'ACTIVE', applied: true, anchorStart: start, anchorEnd: end, payload: { visitName: 'Test' }, beforeValues: { visitName: null }, target: { type: 'STAY', id: 5 } }
function setup(edits, selectedItem = item) {
  const pinia = createPinia()
  const store = useTimelineStore(pinia)
  vi.spyOn(store, 'fetchTimelineEdits').mockResolvedValue(edits)
  const wrapper = mount(TimelineChangesList, { props: { visible: true, item: selectedItem }, global: { plugins: [pinia] } })
  return { wrapper, store }
}
describe('TimelineChangesList', () => {
  beforeEach(() => {
    confirmation.require.mockReset()
    confirmation.require.mockImplementation(({accept}) => accept?.())
  })
  it('shows recorded values and distinguishes related changes without inventing previous values', async () => {
    const { wrapper } = setup([edit, { ...edit, id: 2, kind: 'SET_TIMES', payload: { start, end }, beforeValues: null, target: { type: 'STAY', id: 8 } }])
    await flushPromises()
    expect(wrapper.text()).toContain('No visit label')
    expect(wrapper.text()).toContain('Test')
    expect(wrapper.text()).toContain('Previous value unavailable')
    expect(wrapper.findAll('article').filter(entry => entry.text().includes('Related change'))).toHaveLength(1)
  })
  it('focuses a blocking correction on a neighboring event', async () => {
    const neighbor = { ...edit, id: 9, kind: 'MERGE', anchorStart: end, anchorEnd: '2026-10-08T12:00:00Z',
      target: { type: 'STAY', id: 8 }, payload: { direction: 'NEXT' } }
    const { wrapper } = setup([neighbor])
    await flushPromises()
    expect(wrapper.find('[data-edit-id="9"]').exists()).toBe(false)
    await wrapper.vm.focusChange(9)
    expect(wrapper.find('[data-edit-id="9"]').exists()).toBe(true)
  })

  it('undoes a dependent chain together after confirmation', async () => {
    const first = { ...edit, kind: 'SET_TIMES', seq: 1, payload: { start, end }, beforeValues: null }
    const dependent = { ...edit, id: 2, seq: 2, kind: 'SET_TIMES', payload: { start, end }, target: { type: 'STAY', id: 5 },
      beforeValues: { prerequisiteSequences: [1] } }
    const { wrapper, store } = setup([first, dependent])
    vi.spyOn(store, 'revertTimelineEdit').mockResolvedValue({ edit: first, timelineJobId: 'job' })
    await flushPromises()

    await wrapper.find('[data-edit-id="1"] button').trigger('click')
    await flushPromises()

    expect(confirmation.require).toHaveBeenCalledOnce()
    expect(store.revertTimelineEdit).toHaveBeenCalledWith(first.id, true)
  })

  it('keeps the correction when the cascade confirmation is declined', async () => {
    confirmation.require.mockImplementation(({reject}) => reject?.())
    const first = { ...edit, kind: 'SET_TIMES', seq: 1, payload: { start, end }, beforeValues: null }
    const dependent = { ...edit, id: 2, seq: 2, kind: 'SET_TIMES', payload: { start, end }, target: { type: 'STAY', id: 5 },
      beforeValues: { prerequisiteSequences: [1] } }
    const { wrapper, store } = setup([first, dependent])
    vi.spyOn(store, 'revertTimelineEdit').mockResolvedValue({ edit: first, timelineJobId: 'job' })
    await flushPromises()

    await wrapper.find('[data-edit-id="1"] button').trigger('click')
    await flushPromises()

    expect(store.revertTimelineEdit).not.toHaveBeenCalled()
  })

  it('blocks duplicate undo while regeneration runs and reloads after completion', async () => {
    const { wrapper, store } = setup([edit])
    vi.spyOn(store, 'revertTimelineEdit').mockResolvedValue({ edit, timelineJobId: 'job' })
    await flushPromises()
    const undo = wrapper.find('button')
    await undo.trigger('click')
    await flushPromises()
    expect(undo.attributes('disabled')).toBeDefined()
    await undo.trigger('click')
    expect(store.revertTimelineEdit).toHaveBeenCalledOnce()
    store.fetchTimelineEdits.mockResolvedValue([{ ...edit, status: 'REVERTED' }])
    await wrapper.emitted('changed')[0][0].onUpdated()
    await flushPromises()
    expect(wrapper.find('summary').text()).toContain('Previous changes')
    expect(wrapper.find('button').text()).toBe('Redo')
  })
  it('keeps stale edits reachable in global history and clears the warning when they match again', async () => {
    const stale = { ...edit, applied: false, target: null }
    const { wrapper, store } = setup([stale], null)
    await flushPromises()
    expect(wrapper.find('[role="status"]').text()).toContain('Not currently applied')
    expect(wrapper.find('article').text()).toContain('Test')
    expect(wrapper.find('button').text()).toBe('Undo')
    expect(wrapper.find('button').attributes('disabled')).toBeUndefined()
    store.fetchTimelineEdits.mockResolvedValue([{ ...edit, target: { type: 'STAY', id: 99 } }])
    await wrapper.vm.load()
    await flushPromises()
    expect(wrapper.find('[role="status"]').exists()).toBe(false)
    expect(wrapper.findAll('article')).toHaveLength(1)
  })

  it('allows a stale edit to be undone without requiring a current timeline target', async () => {
    const stale = { ...edit, applied: false, target: null }
    const { wrapper, store } = setup([stale], null)
    vi.spyOn(store, 'revertTimelineEdit').mockResolvedValue({ edit: { ...stale, status: 'REVERTED' } })
    await flushPromises()
    await wrapper.find('button').trigger('click')
    await flushPromises()
    expect(store.revertTimelineEdit).toHaveBeenCalledWith(stale.id, false)
    expect(wrapper.emitted('changed')[0][0].operation).toBe('undo')
  })

  it('offers retry after history fails instead of claiming there are no edits', async () => {
    const { wrapper, store } = setup([])
    store.fetchTimelineEdits.mockRejectedValueOnce(new Error('offline'))
    await wrapper.vm.load()
    await flushPromises()
    expect(wrapper.find('[role="alert"]').text()).toContain('offline')
    await wrapper.find('button').trigger('click')
    await flushPromises()
    expect(wrapper.find('[role="alert"]').exists()).toBe(false)
  })
})


describe('event-specific history', () => {
  it('keeps reverted point edits reachable on a zero-duration trip', async () => {
    const pointEdit = { ...edit, kind: 'SET_MOVEMENT_TYPE', anchorEnd: start, status: 'REVERTED', target: null,
      payload: { movementType: 'WALK' }, beforeValues: { movementType: 'UNKNOWN' } }
    const { wrapper } = setup([pointEdit], { id: 7, type: 'trip', timestamp: start, tripDuration: 0 })
    await flushPromises()
    expect(wrapper.findAll('article')).toHaveLength(1)
    expect(wrapper.text()).toContain('Walk')
    expect(wrapper.text()).toContain('Redo')
  })
  const wideRange = { affectedFrom: '2026-10-08T09:00:00Z', affectedTo: '2026-10-08T12:00:00Z' }
  it.each(['ACTIVE', 'REVERTED', 'SUPERSEDED'])('excludes a neighboring visit label from trips when %s', async status => {
    const label = { ...edit, ...wideRange, status, target: status === 'ACTIVE' ? edit.target : null }
    const trip = { id: 7, type: 'trip', timestamp: '2026-10-08T09:00:00Z', tripDuration: 3600 }
    const { wrapper } = setup([label], trip)
    await flushPromises()
    expect(wrapper.find('article').exists()).toBe(false)
    expect(wrapper.find('details').exists()).toBe(false)
    await wrapper.setProps({ item: { ...trip, timestamp: end } })
    await flushPromises()
    expect(wrapper.find('article').exists()).toBe(false)
  })
  it('uses the applied target instead of overlapping times for attribute edits', async () => {
    const { wrapper } = setup([{ ...edit, target: { type: 'STAY', id: 8 } }])
    await flushPromises()
    expect(wrapper.find('article').exists()).toBe(false)
  })
  it('keeps historical attributes with their event type and original interval', async () => {
    const historical = { ...edit, ...wideRange, target: null, status: 'REVERTED' }
    const { wrapper } = setup([historical])
    await flushPromises()
    expect(wrapper.find('details').text()).toContain('Visit label')
    await wrapper.setProps({ item: { ...item, timestamp: '2026-10-08T09:00:00Z' } })
    await flushPromises()
    expect(wrapper.find('article').exists()).toBe(false)
  })
  it('does not include trip movement changes in visits, even during the same interval', async () => {
    const { wrapper } = setup([{ ...edit, kind: 'SET_MOVEMENT_TYPE', target: null, payload: { movementType: 'TRAIN' } }])
    await flushPromises()
    expect(wrapper.find('article').exists()).toBe(false)
  })
  it('retains related time changes and all changes in global history', async () => {
    const timeEdit = { ...edit, ...wideRange, id: 2, kind: 'SET_TIMES', payload: { start, end } }
    const trip = { id: 7, type: 'trip', timestamp: '2026-10-08T09:00:00Z', tripDuration: 3600 }
    const { wrapper } = setup([edit, timeEdit], trip)
    await flushPromises()
    expect(wrapper.findAll('article')).toHaveLength(1)
    expect(wrapper.find('article').text()).toContain('Related change')
    await wrapper.setProps({ item: null })
    await flushPromises()
    expect(wrapper.findAll('article')).toHaveLength(2)
  })
})

it('releases undo after generation fails and refreshes persisted history before retrying', async () => {
  const { wrapper, store } = setup([edit])
  vi.spyOn(store, 'revertTimelineEdit').mockResolvedValue({ edit, timelineJobId: 'failed-job' })
  await flushPromises()
  await wrapper.find('button').trigger('click')
  expect(wrapper.find('button').attributes('disabled')).toBeDefined()
  store.fetchTimelineEdits.mockResolvedValue([{ ...edit, status: 'REVERTED', applied: false }])
  await wrapper.emitted('changed')[0][0].onUpdateFailed()
  await flushPromises()
  expect(wrapper.find('button').text()).toBe('Redo')
  expect(wrapper.find('button').attributes('disabled')).toBeUndefined()
  vi.spyOn(store, 'restoreTimelineEdit').mockResolvedValue({ edit, timelineJobId: 'retry-job' })
  await wrapper.find('button').trigger('click')
  expect(store.restoreTimelineEdit).toHaveBeenCalledWith(edit.id)
  store.fetchTimelineEdits.mockResolvedValue([edit])
  await wrapper.emitted('changed')[1][0].onUpdated()
  await flushPromises()
  expect(wrapper.find('button').text()).toBe('Undo')
})

it('ignores history returned for a previously selected event', async () => {
  const { wrapper, store } = setup([edit])
  await flushPromises()
  let resolveOld
  store.fetchTimelineEdits.mockImplementationOnce(() => new Promise(resolve => { resolveOld = resolve }))
  const old = wrapper.vm.load()
  store.fetchTimelineEdits.mockResolvedValue([{ ...edit, id: 2, payload: { visitName: 'Newest' } }])
  await wrapper.setProps({ item: { ...item } })
  await flushPromises()
  resolveOld([{ ...edit, payload: { visitName: 'Obsolete' } }])
  await old
  await flushPromises()
  expect(wrapper.text()).toContain('Newest')
  expect(wrapper.text()).not.toContain('Obsolete')
})
