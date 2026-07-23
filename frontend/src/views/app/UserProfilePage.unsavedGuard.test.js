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
import UserProfilePage from './UserProfilePage.vue'

const mocks = vi.hoisted(() => ({
  route: {
    path: '/app/profile',
    query: {}
  },
  router: {
    replace: vi.fn()
  },
  routeLeaveGuard: null,
  confirmRequire: vi.fn(),
  toastAdd: vi.fn(),
  authStore: {
    userId: { value: 1 },
    userName: { value: 'Ada Lovelace' },
    userAvatar: { value: '/avatars/avatar1.png' },
    userEmail: { value: 'ada@example.com' },
    hasPassword: { value: true },
    userTimezone: { value: 'UTC' },
    customMapTileUrl: { value: '' },
    customMapStyleUrl: { value: '' },
    mapRenderMode: { value: 'VECTOR' },
    measureUnit: { value: 'METRIC' },
    defaultRedirectUrl: { value: '' },
    dateFormat: { value: 'MDY' },
    timeFormat: { value: '24h' },
    defaultDateRangePreset: { value: '' },
    autoShowTripReplayControls: { value: true },
    fetchCurrentUserProfile: vi.fn().mockResolvedValue(undefined),
    updateProfile: vi.fn().mockResolvedValue(undefined),
    uploadAvatar: vi.fn().mockResolvedValue('/avatars/uploaded.png'),
    updateTimelineDisplayPreferences: vi.fn().mockResolvedValue({})
  },
  immichStore: {
    config: { value: null },
    configLoading: { value: false },
    fetchConfig: vi.fn().mockResolvedValue(undefined),
    updateConfig: vi.fn().mockResolvedValue(undefined)
  },
  notesStore: {
    memosConfig: { value: null },
    configLoading: { value: false },
    fetchMemosConfig: vi.fn().mockResolvedValue(undefined),
    updateMemosConfig: vi.fn().mockResolvedValue(undefined)
  }
}))

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

vi.mock('@/stores/auth', () => ({
  useAuthStore: () => mocks.authStore
}))

vi.mock('@/stores/immich', () => ({
  useImmichStore: () => mocks.immichStore
}))

vi.mock('@/stores/notes', () => ({
  useNotesStore: () => mocks.notesStore
}))

vi.mock('@/utils/apiService', () => ({
  default: {
    get: vi.fn((url) => {
      if (url === '/ai/settings') {
        return Promise.resolve({
          data: {
            enabled: false,
            openaiApiUrl: 'https://api.openai.com/v1',
            openaiModel: 'gpt-4o-mini',
            openaiApiKeyConfigured: false,
            apiKeyRequired: true,
            customSystemMessage: null
          }
        })
      }

      if (url === '/users/preferences/timeline/display') {
        return Promise.resolve({
          data: {
            customMapTileUrl: '',
            customMapStyleUrl: '',
            mapRenderMode: 'VECTOR',
            defaultDateRangePreset: '',
            pathSimplificationEnabled: true,
            pathSimplificationTolerance: 15,
            pathMaxPoints: 0,
            pathAdaptiveSimplification: true,
            showCurrentLocationTelemetry: true,
            autoShowTripReplayControls: true
          }
        })
      }

      return Promise.resolve({ data: {} })
    }),
    post: vi.fn().mockResolvedValue({})
  }
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
    props: ['tabs', 'activeIndex', 'equalWidth'],
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

vi.mock('@/components/profile/ProfileTab.vue', () => ({
  default: {
    name: 'ProfileTab',
    emits: ['dirty-change', 'save'],
    template: '<div class="profile-tab-stub" />'
  }
}))

vi.mock('@/components/profile/SecurityTab.vue', () => ({
  default: {
    name: 'SecurityTab',
    emits: ['dirty-change', 'save'],
    template: '<div />'
  }
}))

vi.mock('@/components/profile/TimelineDisplayTab.vue', () => ({
  default: {
    name: 'TimelineDisplayTab',
    emits: ['dirty-change', 'save'],
    template: '<div />'
  }
}))

vi.mock('@/components/profile/AIAssistantTab.vue', () => ({
  default: {
    name: 'AIAssistantTab',
    emits: ['dirty-change', 'save'],
    template: '<div />'
  }
}))

vi.mock('@/components/profile/ImmichTab.vue', () => ({
  default: {
    name: 'ImmichTab',
    emits: ['dirty-change', 'save'],
    template: '<div />'
  }
}))

vi.mock('@/components/profile/MemosTab.vue', () => ({
  default: {
    name: 'MemosTab',
    emits: ['dirty-change', 'save'],
    template: '<div />'
  }
}))

const mountPage = async () => {
  const wrapper = mount(UserProfilePage, {
    global: {
      stubs: {
        AppLayout: { template: '<div><slot /></div>' },
        PageContainer: { template: '<main><slot /></main>' },
        TabContainer: { template: '<section><slot /></section>' },
        SettingsSearchTrigger: true,
        Toast: true,
        ConfirmDialog: true
      }
    }
  })

  await flushPromises()
  return wrapper
}

describe('UserProfilePage unsaved changes guard', () => {
  let wrapper

  beforeEach(() => {
    mocks.route.path = '/app/profile'
    mocks.route.query = {}
    mocks.routeLeaveGuard = null
    mocks.router.replace.mockClear()
    mocks.confirmRequire.mockClear()
    mocks.toastAdd.mockClear()
    mocks.authStore.fetchCurrentUserProfile.mockClear()
    mocks.immichStore.fetchConfig.mockClear()
    mocks.notesStore.fetchMemosConfig.mockClear()
  })

  afterEach(() => {
    wrapper?.unmount()
    wrapper = null
  })

  it('allows route leave without confirmation when profile settings are clean', async () => {
    wrapper = await mountPage()
    const next = vi.fn()

    mocks.routeLeaveGuard({ path: '/app/dashboard' }, { path: '/app/profile' }, next)

    expect(next.mock.calls[0]).toEqual([])
    expect(mocks.confirmRequire).not.toHaveBeenCalled()
  })

  it('allows same-page query navigation when profile settings are dirty', async () => {
    wrapper = await mountPage()
    wrapper.findComponent({ name: 'ProfileTab' }).vm.$emit('dirty-change', true)
    await nextTick()
    const next = vi.fn()

    mocks.routeLeaveGuard(
      { path: '/app/profile', query: { tab: 'security' } },
      { path: '/app/profile', query: { tab: 'profile' } },
      next
    )

    expect(next.mock.calls[0]).toEqual([])
    expect(mocks.confirmRequire).not.toHaveBeenCalled()
  })

  it('blocks dirty route leave until the user rejects, accepts, or closes the confirmation', async () => {
    wrapper = await mountPage()
    wrapper.findComponent({ name: 'ProfileTab' }).vm.$emit('dirty-change', true)
    await nextTick()
    const rejectedNext = vi.fn()

    mocks.routeLeaveGuard({ path: '/app/dashboard' }, { path: '/app/profile' }, rejectedNext)

    expect(mocks.confirmRequire).toHaveBeenCalledTimes(1)
    expect(rejectedNext).not.toHaveBeenCalled()

    const rejectedConfirmation = mocks.confirmRequire.mock.calls[0][0]
    expect(rejectedConfirmation.group).toBe('profile-unsaved-changes')
    rejectedConfirmation.reject()
    expect(rejectedNext).toHaveBeenCalledWith(false)

    mocks.confirmRequire.mockClear()
    const acceptedNext = vi.fn()
    mocks.routeLeaveGuard({ path: '/app/timeline' }, { path: '/app/profile' }, acceptedNext)

    expect(mocks.confirmRequire).toHaveBeenCalledTimes(1)
    const acceptedConfirmation = mocks.confirmRequire.mock.calls[0][0]
    expect(acceptedConfirmation.group).toBe('profile-unsaved-changes')
    acceptedConfirmation.accept()
    expect(acceptedNext.mock.calls[0]).toEqual([])

    mocks.confirmRequire.mockClear()
    const hiddenNext = vi.fn()
    mocks.routeLeaveGuard({ path: '/app/notifications' }, { path: '/app/profile' }, hiddenNext)

    const hiddenConfirmation = mocks.confirmRequire.mock.calls[0][0]
    expect(hiddenConfirmation.group).toBe('profile-unsaved-changes')
    hiddenConfirmation.onHide()
    hiddenConfirmation.accept()
    expect(hiddenNext).toHaveBeenCalledTimes(1)
    expect(hiddenNext).toHaveBeenCalledWith(false)
  })

  it('uses the native browser unload prompt only when profile settings are dirty', async () => {
    wrapper = await mountPage()

    const cleanEvent = new Event('beforeunload', { cancelable: true })
    expect(window.dispatchEvent(cleanEvent)).toBe(true)
    expect(cleanEvent.defaultPrevented).toBe(false)

    wrapper.findComponent({ name: 'ProfileTab' }).vm.$emit('dirty-change', true)
    await nextTick()

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
