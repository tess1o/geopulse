import { flushPromises, mount } from '@vue/test-utils'
import { createPinia } from 'pinia'
import { describe, expect, it, vi } from 'vitest'
import TimelineEditHistoryDrawer from './TimelineEditHistoryDrawer.vue'
import { useTimelineStore } from '@/stores/timeline'

const drawerStub = { props: ['visible', 'header'], template: '<div><h2>{{ header }}</h2><slot /></div>' }
const buttonStub = {
  props: ['label'],
  emits: ['click'],
  template: '<button type="button" @click="$emit(\'click\')">{{ label }}</button>'
}

describe('TimelineEditHistoryDrawer', () => {
  it('lists active and reverted edits and dispatches their reversible actions', async () => {
    const pinia = createPinia()
    const store = useTimelineStore(pinia)
    vi.spyOn(store, 'fetchCorrections').mockResolvedValue([
      { id: 1, groupId: 'group-1', op: 'SET_LABEL', status: 'IN_EFFECT', applied: true, anchorStart: '2026-10-08T10:00:00Z', anchorEnd: '2026-10-08T11:00:00Z' },
      { id: 2, groupId: 'group-2', op: 'SET_PLACE', status: 'UNDONE', applied: false, anchorStart: '2026-10-08T12:00:00Z', anchorEnd: '2026-10-08T13:00:00Z' }
    ])
    vi.spyOn(store, 'undoCorrection').mockResolvedValue({ groupId: 'group-1', edits: [{ id: 1 }], timelineJobId: 'revert-job' })
    vi.spyOn(store, 'restoreCorrection').mockResolvedValue({ groupId: 'group-2', edits: [{ id: 2 }], timelineJobId: 'restore-job' })
    const wrapper = mount(TimelineEditHistoryDrawer, {
      props: { visible: false },
      global: { plugins: [pinia], stubs: { Drawer: drawerStub, Button: buttonStub } }
    })

    await wrapper.setProps({ visible: true })
    await flushPromises()
    expect(wrapper.text()).toContain('visit label')
    expect(wrapper.text()).toContain('place')

    await wrapper.findAll('button').find(button => button.text() === 'Undo').trigger('click')
    await flushPromises()
    expect(store.undoCorrection).toHaveBeenCalledWith('group-1', false)
    expect(wrapper.emitted('changed')?.[0]?.[0].timelineJobId).toBe('revert-job')

    await wrapper.emitted('changed')[0][0].onUpdated()
    await flushPromises()
    await wrapper.findAll('button').find(button => button.text() === 'Redo').trigger('click')
    await flushPromises()
    expect(store.restoreCorrection).toHaveBeenCalledWith('group-2')
  })

  it('uses supplied timeframe history and refreshes it after undo without loading global history', async () => {
    const pinia = createPinia()
    const store = useTimelineStore(pinia)
    const fetch = vi.spyOn(store, 'fetchCorrections')
    vi.spyOn(store, 'undoCorrection').mockResolvedValue({ groupId: 'group-1', edits: [{ id: 1 }], timelineJobId: 'job' })
    const edit = { id: 1, groupId: 'group-1', op: 'SET_TRIP_TYPE', status: 'IN_EFFECT', applied: false, anchorStart: '2026-10-25T10:00:00Z', anchorEnd: '2026-10-25T11:00:00Z' }
    const reload = vi.fn(async () => { await wrapper.setProps({ edits: [{ ...edit, status: 'UNDONE' }] }) })
    const wrapper = mount(TimelineEditHistoryDrawer, {
      props: { visible: true, edits: [edit], reload },
      global: { plugins: [pinia], stubs: { Drawer: drawerStub, Button: buttonStub } }
    })
    await flushPromises()
    expect(fetch).not.toHaveBeenCalled()
    expect(wrapper.findAll('article')).toHaveLength(1)
    await wrapper.find('button').trigger('click')
    await flushPromises()
    await wrapper.emitted('changed')[0][0].onUpdated()
    await flushPromises()
    expect(reload).toHaveBeenCalledOnce()
    expect(fetch).not.toHaveBeenCalled()
    expect(wrapper.find('button').text()).toBe('Redo')
  })

  it('shows one history entry and one undo for an activity group', async () => {
    const pinia = createPinia(), store = useTimelineStore(pinia)
    const members = [1, 2].map(id => ({ id, groupId: 'group', op: 'PLACE_STAY', status: 'IN_EFFECT', applied: true,
      anchorStart: `2026-10-08T${id + 10}:00:00Z`, anchorEnd: `2026-10-08T${id + 11}:00:00Z` }))
    const revert = vi.spyOn(store, 'undoCorrection').mockResolvedValue({ groupId: 'group', edits: members })
    const wrapper = mount(TimelineEditHistoryDrawer, { props: { visible: true, edits: members },
      global: { plugins: [pinia], stubs: { Drawer: drawerStub, Button: buttonStub } } })
    await flushPromises()
    expect(wrapper.findAll('article')).toHaveLength(1)
    expect(wrapper.text()).toContain('2 manual activities')
    await wrapper.findAll('button').find(button => button.text() === 'Undo').trigger('click')
    await flushPromises()
    expect(revert).toHaveBeenCalledWith('group', false)
  })

  it('keeps the drawer open and shows a load failure' , async () => {
    const pinia = createPinia()
    const store = useTimelineStore(pinia)
    vi.spyOn(store, 'fetchCorrections').mockRejectedValue(new Error('history unavailable'))
    const wrapper = mount(TimelineEditHistoryDrawer, {
      props: { visible: false },
      global: { plugins: [pinia], stubs: { Drawer: drawerStub, Button: buttonStub } }
    })

    await wrapper.setProps({ visible: true })
    await flushPromises()

    expect(wrapper.find('[role="alert"]').text()).toContain('history unavailable')
    expect(wrapper.emitted('close')).toBeUndefined()
  })
})
