import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.hoisted(() => {
  const storage = new Map()
  const shim = {
    getItem: (key) => storage.get(key) || null,
    setItem: (key, value) => storage.set(key, String(value)),
    removeItem: (key) => storage.delete(key),
    clear: () => storage.clear()
  }
  Object.defineProperty(globalThis, 'localStorage', { configurable: true, value: shim })
  Object.defineProperty(window, 'localStorage', { configurable: true, value: shim })
})

const routerState = vi.hoisted(() => ({ query: {}, push: null }))

vi.mock('vue-router', () => ({
  useRoute: () => ({ query: routerState.query }),
  useRouter: () => ({ push: routerState.push || vi.fn(), replace: vi.fn() })
}))
vi.mock('primevue/usetoast', () => ({ useToast: () => ({ add: vi.fn() }) }))

const Inert = { template: '<div />' }
const globalOptions = {
  stubs: {
    Card: { template: '<div><slot name="content" /><slot /></div>' },
    Button: { props: ['label'], template: '<button>{{ label }}</button>' },
    InputText: {
      props: ['modelValue', 'placeholder'],
      template: '<input :placeholder="placeholder" :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />'
    },
    Password: {
      props: ['modelValue', 'placeholder'],
      template: '<input :placeholder="placeholder" :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />'
    },
    Message: { props: ['content'], template: '<div>{{ content }}</div>' },
    Toast: Inert,
    ErrorReferenceToast: Inert,
    OidcProvidersSection: Inert,
    // RouterLink would need a router; a plain anchor is enough for text assertions.
    'router-link': { props: ['to'], template: '<a><slot /></a>' }
  }
}

/**
 * The sign-in page renders in both locales.
 *
 * Also the regression guard for a mechanical-localization mistake: `t(...)` was substituted throughout
 * this file while the component had no i18n import at all, which compiles cleanly and only fails when
 * rendered. `keys.test.js` checks for that statically; this checks the rendered result.
 */
describe('LoginPage localization', () => {
  let LoginPage

  beforeEach(async () => {
    setActivePinia(createPinia())
    const { useAuthStore } = await import('@/stores/auth')
    vi.spyOn(useAuthStore(), 'getAuthStatus').mockResolvedValue({
      passwordLoginEnabled: true,
      oidcLoginEnabled: false,
      passwordRegistrationEnabled: true,
      oidcRegistrationEnabled: false
    })
    vi.spyOn(useAuthStore(), 'getOidcProviders').mockResolvedValue([])
    LoginPage = (await import('./LoginPage.vue')).default
  })

  const mountPage = async () => {
    const wrapper = mount(LoginPage, { global: globalOptions })
    await flushPromises()
    return wrapper
  }

  it('renders the English sign-in form', async () => {
    const text = (await mountPage()).text()

    expect(text).toContain('Welcome Back')
    expect(text).toContain('Sign in to continue your journey')
    expect(text).toContain('Email Address')
    expect(text).toContain('Password')
    expect(text).toContain('Sign In')
    expect(text).toContain("Don't have an account?")
    expect(text).toContain('Create account')
    // No dotted key may survive to the UI.
    expect(text).not.toMatch(/\bauth\.[a-z]/)
  })

  it('renders the Ukrainian sign-in form', async () => {
    const { setLocale } = await import('@/composables/useLocale')
    await setLocale('uk', { persist: false })

    const text = (await mountPage()).text()

    expect(text).toContain('З поверненням')
    expect(text).toContain('Увійдіть, щоб продовжити подорож')
    expect(text).toContain('Електронна пошта')
    expect(text).toContain('Пароль')
    expect(text).toContain('Увійти')
    expect(text).toContain('Немає облікового запису?')
    expect(text).toContain('Створити обліковий запис')
    expect(text).not.toMatch(/\bauth\.[a-z]/)
  })
})

/**
 * A rejected password must read as a rejected password.
 *
 * The regression this guards: the auth store rethrows a normalized error (no axios `response`), so the
 * page's status switch never matched and the 401 fell through to the generic "Session Expired" copy.
 */
describe('LoginPage credential errors', () => {
  it('shows the invalid-credentials message for a 401 from the backend', async () => {
    setActivePinia(createPinia())
    const { useAuthStore } = await import('@/stores/auth')
    const apiService = (await import('@/utils/apiService')).default
    vi.spyOn(useAuthStore(), 'getAuthStatus').mockResolvedValue({
      passwordLoginEnabled: true,
      oidcLoginEnabled: false,
      passwordRegistrationEnabled: true,
      oidcRegistrationEnabled: false
    })
    vi.spyOn(useAuthStore(), 'getOidcProviders').mockResolvedValue([])
    vi.spyOn(apiService, 'login').mockRejectedValue(Object.assign(new Error('Request failed with status code 401'), {
      response: {
        status: 401,
        data: {
          type: 'urn:geopulse:error:INVALID_CREDENTIALS',
          status: 401,
          code: 'INVALID_CREDENTIALS',
          detail: 'Invalid email or password',
          errorId: 'e-1'
        }
      },
      config: { url: '/auth/sessions' }
    }))
    vi.spyOn(console, 'error').mockImplementation(() => {})

    const LoginPage = (await import('./LoginPage.vue')).default
    const wrapper = mount(LoginPage, { global: globalOptions })
    await flushPromises()

    const [email, password] = wrapper.findAll('input')
    await email.setValue('user@example.com')
    await password.setValue('wrong-password')
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    const text = wrapper.text()
    expect(text).toContain('Invalid email or password. Please check your credentials and try again.')
    expect(text).not.toContain('Your session has expired')
  })
})

/**
 * A guest sent to sign-in from an app link (e.g. a Home tip) returns to that page, not the default one.
 */
describe('LoginPage return-to redirect', () => {
  const signInWith = async (query) => {
    setActivePinia(createPinia())
    routerState.query = query
    routerState.push = vi.fn()
    const { useAuthStore } = await import('@/stores/auth')
    const authStore = useAuthStore()
    vi.spyOn(authStore, 'getAuthStatus').mockResolvedValue({
      passwordLoginEnabled: true,
      oidcLoginEnabled: false,
      passwordRegistrationEnabled: true,
      oidcRegistrationEnabled: false
    })
    vi.spyOn(authStore, 'getOidcProviders').mockResolvedValue([])
    vi.spyOn(authStore, 'login').mockResolvedValue({})

    const LoginPage = (await import('./LoginPage.vue')).default
    const wrapper = mount(LoginPage, { global: globalOptions })
    await flushPromises()

    const [email, password] = wrapper.findAll('input')
    await email.setValue('user@example.com')
    await password.setValue('secret-password')
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    const push = routerState.push
    routerState.query = {}
    routerState.push = null
    return push
  }

  it('opens the requested app page after password sign-in', async () => {
    const push = await signInWith({ redirect: '/app/profile?tab=appearance' })

    expect(push).toHaveBeenCalledWith('/app/profile?tab=appearance')
  })

  it('ignores a redirect that leaves the app', async () => {
    const push = await signInWith({ redirect: 'https://example.com/app/timeline' })

    expect(push).toHaveBeenCalledWith('/app/timeline')
  })
})
