vi.hoisted(() => {
  Object.defineProperty(globalThis, 'localStorage', {
    configurable: true,
    value: {
      getItem: vi.fn(),
      setItem: vi.fn(),
      removeItem: vi.fn(),
      clear: vi.fn()
    }
  })
})

import { flushPromises, mount } from '@vue/test-utils'
import { createPinia } from 'pinia'
import MapMatchingSettingsTab from './MapMatchingSettingsTab.vue'

const mocks = vi.hoisted(() => ({
  get: vi.fn(),
  post: vi.fn(),
  loadSettings: vi.fn().mockResolvedValue([]),
  resetSetting: vi.fn()
}))

vi.mock('@/utils/apiService', () => ({
  default: {
    get: mocks.get,
    post: mocks.post
  }
}))

vi.mock('@/composables/useAdminSettings', () => ({
  useAdminSettings: () => ({
    loadSettings: mocks.loadSettings,
    resetSetting: mocks.resetSetting
  })
}))

vi.mock('primevue/usetoast', () => ({
  useToast: () => ({ add: vi.fn() })
}))

const statusResponse = {
  data: {
    enabled: true,
    configured: true,
    worker: {
      running: true,
      phase: 'MATCHING',
      trigger: 'startup',
      startedAt: '2026-08-22T20:50:00Z',
      lastActivityAt: '2026-08-22T21:11:00Z',
      lastError: null
    },
    backfill: {
      enabled: true,
      totalTrips: 18341,
      scannedTrips: 12850,
      remainingTrips: 5491,
      percent: 70.0616,
      totalUsers: 49,
      completedUsers: 13,
      remainingUsers: 36
    },
    queue: {
      queued: 265,
      processing: 9,
      oldestQueuedAt: '2026-08-22T20:42:00Z'
    },
    diagnostics: {
      pendingReconciliations: 0,
      pendingReconciliationsBySource: {},
      nextReconciliationEligibleAt: null,
      lastWorkerCycleCompletedAt: null,
      targetsByStatus: { MATCHED: 12709, FAILED: 348 },
      targetsBySource: { HISTORICAL: 13331 }
    }
  }
}

const ButtonStub = {
  props: ['label', 'loading', 'disabled'],
  emits: ['click'],
  template: '<button :disabled="disabled" @click="$emit(\'click\', $event)">{{ label }}</button>'
}

const mountTab = () => mount(MapMatchingSettingsTab, {
  global: {
    plugins: [createPinia()],
    stubs: {
      Button: ButtonStub,
      InputNumber: true,
      InputSwitch: true,
      InputText: true,
      Message: { props: ['severity'], template: '<div><slot /></div>' },
      ProgressBar: { props: ['value'], template: '<div class="progress" :data-value="value" />' },
      Select: true,
      SettingItem: true,
      SettingSection: { props: ['title'], template: '<section><h2>{{ title }}</h2><slot /></section>' },
      Tag: { props: ['value'], template: '<span>{{ value }}</span>' }
    }
  }
})

describe('MapMatchingSettingsTab processing status', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    mocks.get.mockReset().mockResolvedValue(statusResponse)
    mocks.post.mockReset()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('shows durable backfill progress and keeps diagnostics collapsed', async () => {
    const wrapper = mountTab()
    await flushPromises()

    expect(wrapper.text()).toContain('12,850 / 18,341 trips inspected')
    expect(wrapper.text()).toContain('5,491 trips remaining')
    expect(wrapper.text()).toContain('13 / 49 users complete')
    expect(wrapper.find('.progress').attributes('data-value')).toBe('70.0616')
    expect(wrapper.find('.advanced-settings').attributes('open')).toBeUndefined()
    expect(wrapper.find('.status-diagnostics').attributes('open')).toBeUndefined()
    expect(wrapper.text()).not.toContain('Process Now')

    await wrapper.find('.status-diagnostics summary').trigger('click')
    expect(wrapper.text()).toContain('Stored cache records')
    expect(wrapper.text()).toContain('All cache versions')
    expect(wrapper.text()).toContain('By status')
    expect(wrapper.text()).toContain('By source')
    expect(wrapper.findAll('.outcome-item')).toHaveLength(3)

    wrapper.unmount()
  })

  it('shows scheduled reconciliation work while waiting for quiet period', async () => {
    mocks.get.mockResolvedValue({
      data: {
        ...statusResponse.data,
        worker: {
          ...statusResponse.data.worker,
          running: false,
          phase: 'IDLE'
        },
        backfill: {
          ...statusResponse.data.backfill,
          scannedTrips: 18341,
          remainingTrips: 0,
          percent: 100,
          completedUsers: 49,
          remainingUsers: 0
        },
        queue: {
          queued: 0,
          processing: 0,
          oldestQueuedAt: null
        },
        diagnostics: {
          ...statusResponse.data.diagnostics,
          pendingReconciliations: 2,
          pendingReconciliationsBySource: { AUTOMATIC: 2 },
          nextReconciliationEligibleAt: '2999-08-22T21:20:00Z'
        }
      }
    })

    const wrapper = mountTab()
    await flushPromises()

    expect(wrapper.text()).toContain('SCHEDULED')
    expect(wrapper.text()).toContain('Work is scheduled')
    expect(wrapper.text()).toContain('Scheduled ranges')
    expect(wrapper.text()).toContain('2')

    await wrapper.find('.status-diagnostics summary').trigger('click')
    expect(wrapper.text()).toContain('Pending ranges')
    expect(wrapper.text()).toContain('Automatic')

    wrapper.unmount()
  })

  it('refreshes every three seconds while running and stops after unmount', async () => {
    const wrapper = mountTab()
    await flushPromises()
    expect(mocks.get).toHaveBeenCalledTimes(1)

    await vi.advanceTimersByTimeAsync(3000)
    await flushPromises()
    expect(mocks.get).toHaveBeenCalledTimes(2)

    wrapper.unmount()
    await vi.advanceTimersByTimeAsync(6000)
    expect(mocks.get).toHaveBeenCalledTimes(2)
  })

  it('rebuilds the historical queue and refreshes status', async () => {
    mocks.get.mockResolvedValue({
      data: {
        ...statusResponse.data,
        worker: {
          ...statusResponse.data.worker,
          running: false,
          phase: 'IDLE'
        }
      }
    })
    mocks.post.mockResolvedValue({
      data: {
        queuedUsers: 2,
        message: 'Historical map matching queue rebuilt'
      }
    })
    const wrapper = mountTab()
    await flushPromises()

    const rebuildButton = wrapper.findAll('button')
      .find(button => button.text() === 'Rebuild Historical Queue')
    expect(rebuildButton).toBeTruthy()

    await rebuildButton.trigger('click')
    await flushPromises()

    expect(mocks.post).toHaveBeenCalledWith('/admin/settings/map-matching/historical/rebuild')
    expect(mocks.get).toHaveBeenCalledTimes(2)

    wrapper.unmount()
  })
})
