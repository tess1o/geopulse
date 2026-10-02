import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.hoisted(() => {
  const storage = new Map()
  const localStorageShim = {
    getItem: (key) => storage.get(key) || null,
    setItem: (key, value) => storage.set(key, String(value)),
    removeItem: (key) => storage.delete(key),
    clear: () => storage.clear()
  }
  Object.defineProperty(globalThis, 'localStorage', { configurable: true, value: localStorageShim })
  Object.defineProperty(window, 'localStorage', { configurable: true, value: localStorageShim })
})

vi.mock('vue-router', () => ({
  useRoute: () => ({ path: '/app/profile', query: {} }),
  useRouter: () => ({ replace: vi.fn(), push: vi.fn() }),
  onBeforeRouteLeave: vi.fn()
}))

vi.mock('primevue/usetoast', () => ({ useToast: () => ({ add: vi.fn() }) }))
vi.mock('primevue/useconfirm', () => ({ useConfirm: () => ({ require: vi.fn() }) }))
vi.mock('@/utils/settingJump', () => ({ jumpToSetting: vi.fn().mockResolvedValue(true) }))
vi.mock('@/utils/demoMode', () => ({ showDemoModeToast: vi.fn() }))

// The stores stay real: the page reads them through `storeToRefs`, which requires an actual Pinia
// store rather than a plain object. Their network calls are stubbed per test instead.

// The tab components pull in large subtrees that are irrelevant here; the page's own chrome is the
// subject, so they render as inert stubs that still expose their props.
const stub = { template: '<div />' }
vi.mock('@/components/ui/layout/AppLayout.vue', () => ({ default: { template: '<div><slot /></div>' } }))
vi.mock('@/components/ui/layout/PageContainer.vue', () => ({ default: { template: '<div><slot /></div>' } }))
vi.mock('@/components/profile/ProfileTab.vue', () => ({ default: stub }))
vi.mock('@/components/profile/SecurityTab.vue', () => ({ default: stub }))
vi.mock('@/components/profile/TimelineDisplayTab.vue', () => ({ default: stub }))
vi.mock('@/components/profile/ConnectedAppsTab.vue', () => ({ default: stub }))
vi.mock('@/components/profile/NotificationsPreferencesTab.vue', () => ({ default: stub }))
vi.mock('@/components/search/SettingsSearchTrigger.vue', () => ({
  default: { props: ['placeholder'], template: '<input :placeholder="placeholder" />' }
}))

describe('UserProfilePage', () => {
  let UserProfilePage
  let pinia

  beforeEach(async () => {
    pinia = createPinia()
    setActivePinia(pinia)
    vi.clearAllMocks()

    // Stub the network calls the page makes on mount so no request leaves the test.
    const { useAuthStore } = await import('@/stores/auth')
    const { useImmichStore } = await import('@/stores/immich')
    const { useNotesStore } = await import('@/stores/notes')
    const { useAIStore } = await import('@/stores/ai')

    vi.spyOn(useAuthStore(), 'fetchCurrentUserProfile').mockResolvedValue()
    vi.spyOn(useAuthStore(), 'fetchTimelineDisplayPreferences').mockResolvedValue(null)
    vi.spyOn(useImmichStore(), 'fetchConfig').mockResolvedValue()
    vi.spyOn(useNotesStore(), 'fetchMemosConfig').mockResolvedValue()
    vi.spyOn(useAIStore(), 'fetchSettings').mockResolvedValue()

    UserProfilePage = (await import('./UserProfilePage.vue')).default
  })

  // Toast and ConfirmDialog are auto-imported by unplugin-vue-components at build time, which vitest
  // does not run, so they are stubbed rather than left unresolved.
  const stubs = { Toast: true, ConfirmDialog: true }

  const mountPage = () => mount(UserProfilePage, { global: { plugins: [pinia], stubs } })

  it('renders the page chrome and tab navigation in English', () => {
    const wrapper = mountPage()

    expect(wrapper.text()).toContain('Personal Settings')
    expect(wrapper.text()).toContain('Manage your account, preferences, and connected apps')
    expect(wrapper.text()).toContain('Personal')
    expect(wrapper.text()).toContain('Experience')
    expect(wrapper.text()).toContain('General')
    expect(wrapper.text()).toContain('Security')
    expect(wrapper.text()).toContain('Timeline & Map')
    expect(wrapper.text()).toContain('Connected Apps')
    expect(wrapper.find('input').attributes('placeholder')).toBe('Search profile settings...')
    expect(wrapper.find('.settings-nav').attributes('aria-label')).toBe('Personal settings sections')
    expect(wrapper.find('.mobile-settings-select span').text()).toBe('Settings section')
  })

  it('renders the mobile select with the same grouped tabs', () => {
    const wrapper = mountPage()
    const options = wrapper.findAll('option').map((option) => option.text())

    // A regression guard for turning settingsGroups into a computed: both the nav and the mobile
    // select read from it, so they must stay in step.
    expect(options).toEqual(['General', 'Security', 'Timeline & Map', 'Appearance', 'Notifications', 'Connected Apps'])
    const groups = wrapper.findAll('optgroup').map((group) => group.attributes('label'))
    expect(groups).toEqual(['Personal', 'Experience', 'Connected Apps'])
  })

  it('renders the Ukrainian chrome and tabs once the locale is switched', async () => {
    const { setLocale } = await import('@/composables/useLocale')
    await setLocale('uk', { persist: false })

    const wrapper = mountPage()

    expect(wrapper.text()).toContain('Персональні налаштування')
    expect(wrapper.text()).toContain('Керуйте своїм обліковим записом, налаштуваннями та підключеними застосунками')
    expect(wrapper.text()).toContain('Особисте')
    expect(wrapper.text()).toContain('Взаємодія')
    expect(wrapper.text()).toContain('Хронологія та карта')
    expect(wrapper.text()).toContain('Сповіщення')
    expect(wrapper.find('input').attributes('placeholder')).toBe('Пошук налаштувань профілю...')
    expect(wrapper.find('.settings-nav').attributes('aria-label')).toBe('Розділи персональних налаштувань')

    // A validity check on the tab keys: they are identities and must survive translation.
    const options = wrapper.findAll('option').map((option) => option.attributes('value'))
    expect(options).toEqual(['general', 'security', 'timeline', 'appearance', 'notifications', 'connectedApps'])
  })

  it('shows the demo read-only notice translated', async () => {
    const { useAuthStore } = await import('@/stores/auth')
    // demoReadOnly is `(demoModeEnabled || user.demoMode) && role !== 'ADMIN'`.
    useAuthStore().$patch({ user: { id: 'u1', email: 'demo@example.com', role: 'USER', demoMode: true } })
    const { setLocale } = await import('@/composables/useLocale')
    await setLocale('uk', { persist: false })

    const notice = mountPage().find('.demo-read-only-message')

    expect(notice.exists()).toBe(true)
    expect(notice.text()).toContain('Демо-режим')
  })
})
