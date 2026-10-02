import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import PrimeVue from 'primevue/config'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useTimelineLabelsStore } from '@/stores/timelineLabels'
import { useTripsStore } from '@/stores/trips'

vi.hoisted(() => {
  const storage = new Map()
  const localStorageShim = {
    getItem: (key) => storage.get(key) || null,
    setItem: (key, value) => storage.set(key, String(value)),
    removeItem: (key) => storage.delete(key),
    clear: () => storage.clear()
  }
  Object.defineProperty(globalThis, 'localStorage', { configurable: true, value: localStorageShim })
  Object.defineProperty(globalThis, 'matchMedia', {
    configurable: true,
    value: () => ({ matches: false, addEventListener: vi.fn(), removeEventListener: vi.fn() })
  })
  Object.defineProperty(URL, 'createObjectURL', { configurable: true, value: () => 'blob:test' })
})

vi.mock('vue-router', () => ({ useRouter: () => ({ push: vi.fn() }) }))
vi.mock('@/components/ui/layout/AppLayout.vue', () => ({ default: { template: '<div><slot /></div>' } }))
vi.mock('primevue/usetoast', () => ({ useToast: () => ({ add: vi.fn() }) }))
vi.mock('primevue/useconfirm', () => ({ useConfirm: () => ({ require: vi.fn() }) }))
vi.mock('@/composables/useTimezone', () => ({
  useTimezone: () => ({
    formatDateDisplay: (value) => value,
    formatUrlDate: (value) => String(value)
  })
}))

const passthrough = { template: '<div><slot name="actions" /><slot /></div>' }
const Menu = { name: 'Menu', props: ['model'], methods: { toggle() {} }, template: '<div />' }

const stubs = {
  AppLayout: passthrough,
  PageContainer: passthrough,
  BaseCard: passthrough,
  CreateTimelineLabelDialog: true,
  EditTimelineLabelDialog: true,
  ConfirmDialog: true,
  Dialog: true,
  Menu
}

describe('Timeline Labels action menu', () => {
  let TimelineLabelsManagementPage
  let pinia

  beforeEach(async () => {
    pinia = createPinia()
    setActivePinia(pinia)

    const timelineLabels = useTimelineLabelsStore()
    timelineLabels.$patch({
      timelineLabels: [
        { id: 1, name: 'Active', source: 'owntracks', isActive: true, startTime: '2026-01-01', endTime: null },
        { id: 2, name: 'Linked', source: 'manual', startTime: '2026-02-01', endTime: '2026-02-02' },
        { id: 3, name: 'Completed', source: 'manual', startTime: '2026-03-01', endTime: '2026-03-02' }
      ],
      activeLabel: { id: 1, name: 'Active', source: 'owntracks', isActive: true, startTime: '2026-01-01' }
    })
    vi.spyOn(timelineLabels, 'fetchTimelineLabels').mockResolvedValue()
    vi.spyOn(timelineLabels, 'fetchActiveLabel').mockResolvedValue()

    const trips = useTripsStore()
    trips.$patch({ trips: [{ id: 7, name: 'Linked plan', timelineLabelId: 2, startTime: '2026-02-01', endTime: '2026-02-02' }] })
    vi.spyOn(trips, 'fetchTrips').mockResolvedValue()

    TimelineLabelsManagementPage = (await import('./TimelineLabelsManagementPage.vue')).default
  })

  const menuItemsFor = async (wrapper, name) => {
    await wrapper.get(`[aria-label="More actions for ${name}"]`).trigger('click')
    return wrapper.findComponent({ name: 'Menu' }).props('model')
  }

  it('adapts actions for linked, unlinked, and protected labels', async () => {
    const wrapper = mount(TimelineLabelsManagementPage, {
      global: { plugins: [pinia, PrimeVue], stubs, directives: { tooltip: () => {} } }
    })

    const linked = await menuItemsFor(wrapper, 'Linked')
    expect(linked.filter((item) => item.label).map((item) => item.label)).toEqual([
      'Open Trip Plan', 'Unlink Trip Plan', 'Edit Label', 'Delete Label'
    ])

    const completed = await menuItemsFor(wrapper, 'Completed')
    expect(completed.find((item) => item.label === 'Create Trip Plan').disabled).toBe(false)
    expect(completed.find((item) => item.label === 'Edit Label').disabled).toBe(false)

    const active = await menuItemsFor(wrapper, 'Active')
    expect(active.find((item) => item.label === 'Create Trip Plan').disabled).toBe(true)
    expect(active.find((item) => item.label === 'Edit Label').disabled).toBe(true)
    expect(active.find((item) => item.label === 'Delete Label').disabled).toBe(true)
  })
})
