import { flushPromises, shallowMount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useDigestStore } from '@/stores/digest'
import TimeDigestPage from './TimeDigestPage.vue'

const { route } = vi.hoisted(() => ({ route: { query: {} } }))
vi.mock('vue-router', () => ({ useRoute: () => route, useRouter: () => ({ push: vi.fn() }) }))
vi.mock('@/router', () => ({ default: { push: vi.fn() } }))
vi.mock('primevue/usetoast', () => ({ useToast: () => ({ add: vi.fn() }) }))
vi.mock('@/utils/apiService', () => ({ default: { get: vi.fn() } }))
vi.mock('@/composables/useErrorHandler', () => ({ useErrorHandler: () => ({ handleError: vi.fn() }) }))
vi.mock('@/composables/useTimezone', async () => {
  const { default: dayjs } = await import('dayjs')
  return { useTimezone: () => ({ now: () => dayjs('2026-03-01'), create: dayjs }) }
})

describe('Rewind release hold', () => {
  beforeEach(() => setActivePinia(createPinia()))

  it.each(['monthly', 'yearly'])('hides playback but preserves the %s digest and export', async (viewMode) => {
    route.query = { viewMode, year: '2026', month: '3' }
    const store = useDigestStore()
    store.currentDigest = { metrics: { tripCount: 2 }, topPlaces: [] }
    vi.spyOn(store, 'fetchMonthlyDigest').mockResolvedValue(store.currentDigest)
    vi.spyOn(store, 'fetchYearlyDigest').mockResolvedValue(store.currentDigest)
    const wrapper = shallowMount(TimeDigestPage, {
      global: {
        stubs: {
          AppLayout: { template: '<div><slot /></div>' },
          PageContainer: { template: '<div><slot name="actions" /><slot /></div>' },
          Button: { props: ['label', 'icon'], template: '<button :data-icon="icon">{{ label }}</button>' }
        }
      }
    })
    await flushPromises()

    expect(wrapper.find('[data-icon="pi pi-play-circle"]').exists()).toBe(false)
    expect(wrapper.find('[data-icon="pi pi-file-pdf"]').exists()).toBe(true)
    expect(wrapper.findComponent({ name: 'DigestMetrics' }).exists()).toBe(true)
    expect(wrapper.findComponent({ name: 'DigestMemories' }).exists()).toBe(true)
    expect(wrapper.findComponent({ name: 'CinematicRewindDialog' }).props('visible')).toBe(false)
    wrapper.unmount()
  })
})
