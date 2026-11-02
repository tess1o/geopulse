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
    vi.spyOn(store, 'fetchTimelineEdits').mockResolvedValue([
      { id: 1, kind: 'SET_VISIT_NAME', status: 'ACTIVE', applied: true, anchorStart: '2026-10-08T10:00:00Z', anchorEnd: '2026-10-08T11:00:00Z' },
      { id: 2, kind: 'SET_PLACE', status: 'REVERTED', applied: false, anchorStart: '2026-10-08T12:00:00Z', anchorEnd: '2026-10-08T13:00:00Z' }
    ])
    vi.spyOn(store, 'revertTimelineEdit').mockResolvedValue({ edit: { id: 1 }, timelineJobId: 'revert-job' })
    vi.spyOn(store, 'restoreTimelineEdit').mockResolvedValue({ edit: { id: 2 }, timelineJobId: 'restore-job' })
    const wrapper = mount(TimelineEditHistoryDrawer, {
      props: { visible: false },
      global: { plugins: [pinia], stubs: { Drawer: drawerStub, Button: buttonStub } }
    })

    await wrapper.setProps({ visible: true })
    await flushPromises()
    expect(wrapper.text()).toContain('Visit label')
    expect(wrapper.text()).toContain('Location')

    await wrapper.findAll('button').find(button => button.text() === 'Undo').trigger('click')
    await flushPromises()
    expect(store.revertTimelineEdit).toHaveBeenCalledWith(1)
    expect(wrapper.emitted('changed')?.[0]?.[0].timelineJobId).toBe('revert-job')

    await wrapper.emitted('changed')[0][0].onUpdated()
    await flushPromises()
    await wrapper.findAll('button').find(button => button.text() === 'Redo').trigger('click')
    await flushPromises()
    expect(store.restoreTimelineEdit).toHaveBeenCalledWith(2)
  })

  it('uses supplied timeframe history and refreshes it after undo without loading global history', async () => {
    const pinia = createPinia()
    const store = useTimelineStore(pinia)
    const fetch = vi.spyOn(store, 'fetchTimelineEdits')
    vi.spyOn(store, 'revertTimelineEdit').mockResolvedValue({ edit: { id: 1 }, timelineJobId: 'job' })
    const edit = { id: 1, kind: 'SET_MOVEMENT_TYPE', status: 'ACTIVE', applied: false, anchorStart: '2026-10-25T10:00:00Z', anchorEnd: '2026-10-25T11:00:00Z' }
    const reload = vi.fn(async () => { await wrapper.setProps({ edits: [{ ...edit, status: 'REVERTED' }] }) })
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

  it('keeps the drawer open and shows a load failure' , async () => {
    const pinia = createPinia()
    const store = useTimelineStore(pinia)
    vi.spyOn(store, 'fetchTimelineEdits').mockRejectedValue(new Error('history unavailable'))
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
