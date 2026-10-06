import { mount, flushPromises, RouterLinkStub } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { ref } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useAuthStore } from '@/stores/auth'
import { useNotificationsStore } from '@/stores/notifications'

const { push, handleError, locale, setLocale, apiService } = vi.hoisted(() => {
  const storage = new Map()
  Object.defineProperty(globalThis, 'localStorage', {
    configurable: true,
    value: {
      getItem: (key) => storage.get(key) || null,
      setItem: (key, value) => storage.set(key, String(value)),
      removeItem: (key) => storage.delete(key),
      clear: () => storage.clear()
    }
  })
  Object.defineProperty(globalThis, 'matchMedia', {
    configurable: true,
    value: () => ({ matches: false, addEventListener: () => {}, removeEventListener: () => {} })
  })
  Object.defineProperty(URL, 'createObjectURL', { configurable: true, value: () => 'blob:test' })
  return {
    push: vi.fn(),
    handleError: vi.fn(),
    locale: { value: 'en' },
    setLocale: vi.fn(),
    apiService: {
      get: vi.fn(),
      patch: vi.fn(),
      logout: vi.fn(),
      clearAuthData: vi.fn()
    }
  }
})

vi.mock('vue-router', () => ({ useRouter: () => ({ push }) }))
vi.mock('@/router', () => ({ default: { push } }))
vi.mock('@/composables/useErrorHandler', () => ({ useErrorHandler: () => ({ handleError }) }))
vi.mock('@/utils/apiService', () => ({ default: apiService }))
vi.mock('@/composables/useLocale', async () => {
  const { ref: vueRef } = await import('vue')
  const current = vueRef('en')
  Object.defineProperty(locale, 'value', { get: () => current.value, set: (v) => { current.value = v } })
  return {
    useLocale: () => ({
      locale: current,
      localeOptions: [{ value: 'en', label: 'English' }, { value: 'uk', label: 'Українська' }],
      setLocale
    })
  }
})
vi.mock('@/composables/useThemeMode', () => ({
  useThemeMode: () => ({
    themeMode: ref('system'),
    setThemeMode: vi.fn(),
    themeModes: { LIGHT: 'light', DARK: 'dark', SYSTEM: 'system' }
  })
}))

const Popover = {
  name: 'Popover',
  methods: { toggle() {}, hide() {} },
  template: '<div><slot /></div>'
}

const Select = {
  name: 'Select',
  props: ['modelValue', 'options'],
  emits: ['update:modelValue'],
  template: '<div />'
}

const UserMenuHint = {
  name: 'UserMenuHint',
  props: ['anchor'],
  methods: { dismiss: vi.fn() },
  template: '<div />'
}

const stubs = {
  UserMenuHint,
  Popover,
  Select,
  SelectButton: true,
  Avatar: true,
  RouterLink: RouterLinkStub
}

describe('UserMenu', () => {
  let UserMenu
  let pinia

  beforeEach(async () => {
    vi.clearAllMocks()
    locale.value = 'en'
    setLocale.mockImplementation(async (next) => { locale.value = next })
    apiService.get.mockResolvedValue({ currentVersion: '2.0.0', latestVersion: '', updateAvailable: false })

    pinia = createPinia()
    setActivePinia(pinia)
    useAuthStore().$patch({
      user: { fullName: 'Test User', email: 'test@example.com', role: 'USER', language: 'en' },
      isAuthenticated: true
    })
    UserMenu = (await import('./UserMenu.vue')).default
  })

  const mountMenu = () => mount(UserMenu, { global: { plugins: [pinia], stubs } })
  const routes = (wrapper) => wrapper.findAllComponents(RouterLinkStub).map((link) => link.props('to'))

  it('shows the account, settings and help destinations', async () => {
    const wrapper = mountMenu()
    await flushPromises()

    expect(wrapper.text()).toContain('Test User')
    expect(wrapper.text()).toContain('test@example.com')
    expect(wrapper.text()).toContain('Version 2.0.0')
    expect(routes(wrapper)).toEqual(['/app/notifications', '/app/profile', '/app/timeline/preferences', '/app/help'])
    expect(wrapper.text()).not.toContain('Admin')
  })

  it('gives admins a badge and shortcuts to the dashboard and system settings', () => {
    useAuthStore().$patch({ user: { fullName: 'Admin', role: 'ADMIN', canViewAdmin: true } })
    const wrapper = mountMenu()

    expect(wrapper.find('.gp-user-menu-badge').text()).toBe('Admin')
    expect(routes(wrapper).filter((route) => route.startsWith('/app/admin'))).toEqual(['/app/admin', '/app/admin/settings'])
    expect(wrapper.text()).toContain('Admin Dashboard')
    expect(wrapper.text()).toContain('System Settings')
  })

  it('carries the unread notification count for phones, where the bell is hidden', () => {
    useNotificationsStore().$patch({ unreadCount: 4 })
    const wrapper = mountMenu()

    expect(wrapper.find('.gp-user-menu-unread-badge').text()).toBe('4')
    expect(wrapper.find('.gp-user-menu-count').text()).toBe('4')
  })

  it('switches the language right away and saves it to the profile', async () => {
    apiService.patch.mockResolvedValue({ id: 'u1', fullName: 'Test User', uiPreferences: { language: 'uk' } })
    const wrapper = mountMenu()

    wrapper.findComponent(Select).vm.$emit('update:modelValue', 'uk')
    await flushPromises()

    expect(setLocale).toHaveBeenCalledWith('uk', { persistTo: 'profile' })
    // fullName is resent because PATCH /users/me would otherwise clear it.
    expect(apiService.patch).toHaveBeenCalledWith('/users/me', {
      fullName: 'Test User',
      avatar: undefined,
      timezone: undefined,
      uiPreferences: { language: 'uk' }
    })
    expect(locale.value).toBe('uk')
  })

  it('reverts the language when saving fails', async () => {
    apiService.patch.mockRejectedValue(new Error('boom'))
    const wrapper = mountMenu()

    wrapper.findComponent(Select).vm.$emit('update:modelValue', 'uk')
    await flushPromises()

    expect(setLocale).toHaveBeenLastCalledWith('en', { persistTo: 'profile' })
    expect(locale.value).toBe('en')
    expect(handleError).toHaveBeenCalledWith(expect.any(Error), { summary: 'Could not save your language preference' })
  })

  it('keeps a demo read-only language switch local', async () => {
    useAuthStore().$patch({ authStatus: { demoModeEnabled: true } })
    const wrapper = mountMenu()

    wrapper.findComponent(Select).vm.$emit('update:modelValue', 'uk')
    await flushPromises()

    expect(setLocale).toHaveBeenCalledWith('uk', { persistTo: 'profile' })
    expect(apiService.patch).not.toHaveBeenCalled()
  })

  it('dismisses the what-moved hint when the menu is opened', async () => {
    const wrapper = mountMenu()

    await wrapper.find('.gp-user-menu-trigger').trigger('click')

    expect(UserMenuHint.methods.dismiss).toHaveBeenCalled()
    expect(wrapper.findComponent(UserMenuHint).props('anchor')).toBe(wrapper.find('.gp-user-menu-trigger').element)
  })

  it('logs out and returns to the landing page', async () => {
    apiService.logout.mockResolvedValue()
    const wrapper = mountMenu()

    await wrapper.find('.gp-user-menu-logout').trigger('click')
    await flushPromises()

    expect(apiService.logout).toHaveBeenCalled()
    expect(push).toHaveBeenCalledWith('/')
  })
})
