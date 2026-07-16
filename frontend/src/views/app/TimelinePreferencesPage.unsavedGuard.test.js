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
import { nextTick } from 'vue'
import TimelinePreferencesPage from './TimelinePreferencesPage.vue'

const mocks = vi.hoisted(() => {
  const makeRef = (value) => ({
    __v_isRef: true,
    value
  })

  return {
    route: {
      path: '/app/timeline/preferences',
      query: {}
    },
    router: {
      push: vi.fn(),
      replace: vi.fn()
    },
    routeLeaveGuard: null,
    confirmRequire: vi.fn(),
    toastAdd: vi.fn(),
    timelinePreferencesStore: {
      timelinePreferences: makeRef({ staypointRadiusMeters: 100, tripDetectionAlgorithm: 'single' }),
      lastUpdateResponseData: null,
      fetchTimelinePreferences: vi.fn().mockResolvedValue({ staypointRadiusMeters: 100, tripDetectionAlgorithm: 'single' }),
      updateTimelinePreferences: vi.fn().mockResolvedValue(null),
      resetTimelinePreferencesToDefaults: vi.fn().mockResolvedValue(null)
    },
    timelineStore: {
      regenerateAllTimeline: vi.fn().mockResolvedValue(null)
    },
    boatSetupStore: {
      status: makeRef(null),
      currentJobId: null,
      fetchStatus: vi.fn().mockResolvedValue(null),
      fetchJob: vi.fn().mockResolvedValue(null),
      startSetup: vi.fn().mockResolvedValue(null)
    },
    checkActiveJob: vi.fn().mockResolvedValue({ hasActiveJob: false, jobId: null }),
    timelineRegeneration: {
      timelineRegenerationVisible: makeRef(false),
      timelineRegenerationType: makeRef('preferences'),
      currentJobId: makeRef(null),
      jobProgress: makeRef(null),
      withTimelineRegeneration: vi.fn()
    }
  }
})

vi.mock('pinia', () => ({
  storeToRefs: (store) => store
}))

vi.mock('vue-router', () => ({
  useRoute: () => mocks.route,
  useRouter: () => mocks.router,
  onBeforeRouteLeave: (guard) => {
    mocks.routeLeaveGuard = guard
  }
}))

vi.mock('primevue/useconfirm', () => ({
  useConfirm: () => ({
    require: mocks.confirmRequire
  })
}))

vi.mock('primevue/usetoast', () => ({
  useToast: () => ({
    add: mocks.toastAdd
  })
}))

vi.mock('@/stores/timelinePreferences', () => ({
  useTimelinePreferencesStore: () => mocks.timelinePreferencesStore
}))

vi.mock('@/stores/timeline', () => ({
  useTimelineStore: () => mocks.timelineStore
}))

vi.mock('@/stores/boatSetup', () => ({
  useBoatSetupStore: () => mocks.boatSetupStore
}))

vi.mock('@/composables/useTimelineRegeneration', () => ({
  useTimelineRegeneration: () => mocks.timelineRegeneration
}))

vi.mock('@/composables/useClassificationValidation', () => ({
  useClassificationValidation: () => ({
    validationWarnings: { value: [] },
    hasWarnings: { value: false },
    hasErrors: { value: false },
    getWarningMessagesForType: vi.fn(() => [])
  })
}))

vi.mock('@/composables/useTimelineJobCheck', () => ({
  useTimelineJobCheck: () => ({
    checkActiveJob: mocks.checkActiveJob
  })
}))

vi.mock('@/utils/settingJump', () => ({
  jumpToSetting: vi.fn().mockResolvedValue(true)
}))

vi.mock('@/components/ui/layout/AppLayout.vue', () => ({
  default: {
    name: 'AppLayout',
    template: '<div><slot /></div>'
  }
}))

vi.mock('@/components/ui/layout/PageContainer.vue', () => ({
  default: {
    name: 'PageContainer',
    template: '<main><slot /></main>'
  }
}))

vi.mock('@/components/ui/layout/TabContainer.vue', () => ({
  default: {
    name: 'TabContainer',
    props: ['tabs', 'activeIndex'],
    emits: ['tab-change'],
    template: '<section><slot /></section>'
  }
}))

vi.mock('@/components/search/SettingsSearchTrigger.vue', () => ({
  default: {
    name: 'SettingsSearchTrigger',
    emits: ['navigate'],
    template: '<div />'
  }
}))

vi.mock('@/components/timeline-preferences/StayPointDetectionTab.vue', () => ({
  default: {
    name: 'StayPointDetectionTab',
    props: ['modelValue'],
    emits: ['update:modelValue'],
    template: '<div />'
  }
}))

vi.mock('@/components/timeline-preferences/TripClassificationTab.vue', () => ({
  default: {
    name: 'TripClassificationTab',
    props: ['modelValue', 'getWarningMessagesForType', 'boatSetupStatus'],
    emits: ['update:modelValue', 'retry-boat-setup'],
    template: '<div />'
  }
}))

vi.mock('@/components/timeline-preferences/GpsGapsDetectionTab.vue', () => ({
  default: {
    name: 'GpsGapsDetectionTab',
    props: ['modelValue'],
    emits: ['update:modelValue'],
    template: '<div />'
  }
}))

vi.mock('@/components/timeline-preferences/StayPointMergingTab.vue', () => ({
  default: {
    name: 'StayPointMergingTab',
    props: ['modelValue'],
    emits: ['update:modelValue'],
    template: '<div />'
  }
}))

vi.mock('@/components/dialogs/TimelineRegenerationModal.vue', () => ({
  default: {
    name: 'TimelineRegenerationModal',
    props: ['visible', 'type', 'jobId', 'jobProgress'],
    emits: ['update:visible'],
    template: '<div />'
  }
}))

const mountPage = async () => {
  const wrapper = mount(TimelinePreferencesPage, {
    global: {
      stubs: {
        Button: true,
        Card: { template: '<section><slot name="content" /><slot /></section>' },
        Message: { template: '<section><slot /></section>' },
        Dialog: { template: '<section><slot /><slot name="footer" /></section>' },
        ConfirmDialog: true,
        Toast: true,
        Menu: true,
        ProgressBar: true
      }
    }
  })

  await flushPromises()
  return wrapper
}

const makeDirty = async (wrapper) => {
  wrapper.findComponent({ name: 'StayPointDetectionTab' }).vm.$emit('update:modelValue', {
    staypointRadiusMeters: 125,
    tripDetectionAlgorithm: 'single'
  })
  await nextTick()
}

describe('TimelinePreferencesPage unsaved changes guard', () => {
  let wrapper

  beforeEach(() => {
    mocks.route.path = '/app/timeline/preferences'
    mocks.route.query = {}
    mocks.routeLeaveGuard = null
    mocks.router.push.mockClear()
    mocks.router.replace.mockClear()
    mocks.confirmRequire.mockClear()
    mocks.toastAdd.mockClear()
    mocks.timelinePreferencesStore.timelinePreferences.value = {
      staypointRadiusMeters: 100,
      tripDetectionAlgorithm: 'single'
    }
    mocks.timelinePreferencesStore.fetchTimelinePreferences.mockClear()
    mocks.boatSetupStore.fetchStatus.mockClear()
    mocks.checkActiveJob.mockClear()
  })

  afterEach(() => {
    wrapper?.unmount()
    wrapper = null
  })

  it('allows route leave without confirmation when timeline preferences are clean', async () => {
    wrapper = await mountPage()
    const next = vi.fn()

    mocks.routeLeaveGuard({ path: '/app/dashboard' }, { path: '/app/timeline/preferences' }, next)

    expect(next.mock.calls[0]).toEqual([])
    expect(mocks.confirmRequire).not.toHaveBeenCalled()
  })

  it('does not treat the initial unloaded preference baseline as dirty', async () => {
    mocks.timelinePreferencesStore.timelinePreferences.value = null
    wrapper = await mountPage()
    const next = vi.fn()

    mocks.routeLeaveGuard({ path: '/app/dashboard' }, { path: '/app/timeline/preferences' }, next)

    expect(next.mock.calls[0]).toEqual([])
    expect(mocks.confirmRequire).not.toHaveBeenCalled()

    const cleanEvent = new Event('beforeunload', { cancelable: true })
    expect(window.dispatchEvent(cleanEvent)).toBe(true)
    expect(cleanEvent.defaultPrevented).toBe(false)
  })

  it('allows same-page query navigation when timeline preferences are dirty', async () => {
    wrapper = await mountPage()
    await makeDirty(wrapper)
    const next = vi.fn()

    mocks.routeLeaveGuard(
      { path: '/app/timeline/preferences', query: { tab: 'trips' } },
      { path: '/app/timeline/preferences', query: { tab: 'staypoints' } },
      next
    )

    expect(next.mock.calls[0]).toEqual([])
    expect(mocks.confirmRequire).not.toHaveBeenCalled()
  })

  it('blocks dirty route leave until the user rejects, accepts, or closes the confirmation', async () => {
    wrapper = await mountPage()
    await makeDirty(wrapper)
    const rejectedNext = vi.fn()

    mocks.routeLeaveGuard({ path: '/app/dashboard' }, { path: '/app/timeline/preferences' }, rejectedNext)

    expect(mocks.confirmRequire).toHaveBeenCalledTimes(1)
    expect(rejectedNext).not.toHaveBeenCalled()

    const rejectedConfirmation = mocks.confirmRequire.mock.calls[0][0]
    expect(rejectedConfirmation.group).toBe('timeline-preferences-unsaved-changes')
    rejectedConfirmation.reject()
    expect(rejectedNext).toHaveBeenCalledWith(false)

    mocks.confirmRequire.mockClear()
    const acceptedNext = vi.fn()
    mocks.routeLeaveGuard({ path: '/app/timeline' }, { path: '/app/timeline/preferences' }, acceptedNext)

    const acceptedConfirmation = mocks.confirmRequire.mock.calls[0][0]
    expect(acceptedConfirmation.group).toBe('timeline-preferences-unsaved-changes')
    acceptedConfirmation.accept()
    expect(acceptedNext.mock.calls[0]).toEqual([])

    mocks.confirmRequire.mockClear()
    const hiddenNext = vi.fn()
    mocks.routeLeaveGuard({ path: '/app/notifications' }, { path: '/app/timeline/preferences' }, hiddenNext)

    const hiddenConfirmation = mocks.confirmRequire.mock.calls[0][0]
    expect(hiddenConfirmation.group).toBe('timeline-preferences-unsaved-changes')
    hiddenConfirmation.onHide()
    hiddenConfirmation.accept()
    expect(hiddenNext).toHaveBeenCalledTimes(1)
    expect(hiddenNext).toHaveBeenCalledWith(false)
  })

  it('uses the native browser unload prompt only when timeline preferences are dirty', async () => {
    wrapper = await mountPage()

    const cleanEvent = new Event('beforeunload', { cancelable: true })
    expect(window.dispatchEvent(cleanEvent)).toBe(true)
    expect(cleanEvent.defaultPrevented).toBe(false)

    await makeDirty(wrapper)

    const dirtyEvent = new Event('beforeunload', { cancelable: true })
    Object.defineProperty(dirtyEvent, 'returnValue', {
      configurable: true,
      writable: true,
      value: undefined
    })

    expect(window.dispatchEvent(dirtyEvent)).toBe(false)
    expect(dirtyEvent.defaultPrevented).toBe(true)
    expect(dirtyEvent.returnValue).toBe('')
  })
})
