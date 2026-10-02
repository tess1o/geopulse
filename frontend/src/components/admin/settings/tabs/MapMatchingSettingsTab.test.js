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
  resetSetting: vi.fn(),
  toastAdd: vi.fn()
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
  useToast: () => ({ add: mocks.toastAdd })
}))

const statusPayload = {
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
      Dialog: {
        props: ['visible', 'header'],
        template: '<div v-if="visible" class="dialog-stub"><h3>{{ header }}</h3><slot /><slot name="footer" /></div>'
      },
      InputNumber: true,
      InputSwitch: true,
      InputText: true,
      Message: { props: ['severity'], template: '<div><slot /></div>' },
      ProgressBar: { props: ['value'], template: '<div class="progress" :data-value="value" />' },
      RadioButton: {
        name: 'RadioButton',
        props: ['modelValue', 'value', 'inputId'],
        emits: ['update:modelValue'],
        template: '<input type="radio" :value="value" :checked="modelValue === value" '
          + '@change="$emit(\'update:modelValue\', value)" />'
      },
      Select: true,
      SettingItem: { name: 'SettingItem', props: ['setting'], template: '<div class="setting-item-stub" />' },
      SettingSection: { props: ['title'], template: '<section><h2>{{ title }}</h2><slot /></section>' },
      Tag: { props: ['value'], template: '<span>{{ value }}</span>' }
    }
  }
})

describe('MapMatchingSettingsTab processing status', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    mocks.get.mockReset().mockResolvedValue(statusPayload)
    mocks.post.mockReset()
    mocks.loadSettings.mockReset().mockResolvedValue([])
    mocks.resetSetting.mockReset()
    mocks.toastAdd.mockReset()
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
      ...statusPayload,
      worker: {
        ...statusPayload.worker,
        running: false,
        phase: 'IDLE'
      },
      backfill: {
        ...statusPayload.backfill,
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
        ...statusPayload.diagnostics,
        pendingReconciliations: 2,
        pendingReconciliationsBySource: { AUTOMATIC: 2 },
        nextReconciliationEligibleAt: '2999-08-22T21:20:00Z'
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

  const mountIdleTab = async () => {
    mocks.get.mockResolvedValue({
      ...statusPayload,
      worker: {
        ...statusPayload.worker,
        running: false,
        phase: 'IDLE'
      }
    })
    const wrapper = mountTab()
    await flushPromises()
    return wrapper
  }

  const buttonWithText = (wrapper, text) => wrapper.findAll('button').find(button => button.text() === text)

  it('re-runs map matching for failed trips by default', async () => {
    mocks.post.mockResolvedValue({ mode: 'UNSUCCESSFUL', queuedUsers: 2, affectedTargets: 5, purgedDetachedTargets: 0 })
    const wrapper = await mountIdleTab()

    const rerunButton = buttonWithText(wrapper, 'Re-run Map Matching')
    expect(rerunButton).toBeTruthy()

    await rerunButton.trigger('click')
    await flushPromises()
    expect(mocks.post).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('Retry failed and skipped trips')
    expect(wrapper.text()).toContain('Keeps routes that are already matched')

    await buttonWithText(wrapper, 'Re-run').trigger('click')
    await flushPromises()

    expect(mocks.post).toHaveBeenCalledWith('/admin/settings/map-matching/rebuilds?mode=UNSUCCESSFUL')
    expect(mocks.get).toHaveBeenCalledTimes(2)

    wrapper.unmount()
  })

  it('re-matches every trip when the all-trips mode is selected', async () => {
    mocks.post.mockResolvedValue({ mode: 'ALL', queuedUsers: 3, affectedTargets: 466, purgedDetachedTargets: 0 })
    const wrapper = await mountIdleTab()

    await buttonWithText(wrapper, 'Re-run Map Matching').trigger('click')
    await flushPromises()

    const radios = wrapper.findAll('input[type="radio"]')
    expect(radios).toHaveLength(2)
    await radios[1].trigger('change')
    await flushPromises()

    expect(wrapper.text()).toContain('Re-match all trips')
    expect(wrapper.text()).toContain('Matched routes disappear from the map')

    await buttonWithText(wrapper, 'Re-run').trigger('click')
    await flushPromises()

    expect(mocks.post).toHaveBeenCalledWith('/admin/settings/map-matching/rebuilds?mode=ALL')

    wrapper.unmount()
  })

  const givenSettings = (settingsFixture) => {
    mocks.loadSettings.mockResolvedValue(settingsFixture)
    mocks.resetSetting.mockResolvedValue({})
  }

  it('asks whether to re-run after resetting a matching setting', async () => {
    const settingsFixture = [
      { key: 'map-matching.max-input-points', label: 'Max Input Points', valueType: 'INTEGER', currentValue: 100 },
      { key: 'map-matching.worker.batch-size', label: 'Worker Batch Size', valueType: 'INTEGER', currentValue: 5 }
    ]
    givenSettings(settingsFixture)
    const wrapper = await mountIdleTab()

    const settingItems = wrapper.findAllComponents({ name: 'SettingItem' })
    expect(settingItems).toHaveLength(2)

    settingItems[0].vm.$emit('reset')
    await flushPromises()

    expect(mocks.resetSetting).toHaveBeenCalledWith(settingsFixture[0])
    expect(wrapper.text()).toContain('Re-run map matching?')
    expect(wrapper.text()).toContain('Matching settings changed')
    // Nothing is queued until the admin confirms in the follow-up dialog.
    expect(mocks.post).not.toHaveBeenCalled()

    wrapper.unmount()
  })

  it('opens the mode dialog on the all-trips mode and queues nothing before confirming', async () => {
    givenSettings([
      { key: 'map-matching.valhalla.base-url', label: 'Valhalla Base URL', valueType: 'STRING', currentValue: 'http://valhalla:8002' }
    ])
    const wrapper = await mountIdleTab()

    wrapper.findAllComponents({ name: 'SettingItem' })[0].vm.$emit('reset')
    await flushPromises()

    await buttonWithText(wrapper, 'Re-run now').trigger('click')
    await flushPromises()

    // Applying new settings means recomputing routes that are already matched, which only the ALL mode
    // does - so it is preselected rather than the failure-only default.
    const radios = wrapper.findAll('input[type="radio"]')
    expect(radios[0].element.checked).toBe(false)
    expect(radios[1].element.checked).toBe(true)
    expect(mocks.post).not.toHaveBeenCalled()

    wrapper.unmount()
  })

  it('keeps the reminder in the status card when the prompt is dismissed', async () => {
    givenSettings([
      { key: 'map-matching.max-input-points', label: 'Max Input Points', valueType: 'INTEGER', currentValue: 100 }
    ])
    const wrapper = await mountIdleTab()

    wrapper.findAllComponents({ name: 'SettingItem' })[0].vm.$emit('reset')
    await flushPromises()

    await buttonWithText(wrapper, 'Later').trigger('click')
    await flushPromises()

    expect(wrapper.text()).not.toContain('Re-run map matching?')
    expect(wrapper.text()).toContain('Matching settings changed')

    wrapper.unmount()
  })

  it('does not prompt when a setting outside the cache key is reset', async () => {
    givenSettings([
      { key: 'map-matching.worker.batch-size', label: 'Worker Batch Size', valueType: 'INTEGER', currentValue: 5 }
    ])
    const wrapper = await mountIdleTab()

    wrapper.findAllComponents({ name: 'SettingItem' })[0].vm.$emit('reset')
    await flushPromises()

    expect(mocks.resetSetting).toHaveBeenCalled()
    expect(wrapper.text()).not.toContain('Matching settings changed')

    wrapper.unmount()
  })
})
