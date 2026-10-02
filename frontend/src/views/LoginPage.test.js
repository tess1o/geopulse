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

vi.mock('vue-router', () => ({
  useRoute: () => ({ query: {} }),
  useRouter: () => ({ push: vi.fn(), replace: vi.fn() })
}))
vi.mock('primevue/usetoast', () => ({ useToast: () => ({ add: vi.fn() }) }))

const Inert = { template: '<div />' }
const globalOptions = {
  stubs: {
    Card: { template: '<div><slot name="content" /><slot /></div>' },
    Button: { props: ['label'], template: '<button>{{ label }}</button>' },
    InputText: { props: ['modelValue', 'placeholder'], template: '<input :placeholder="placeholder" />' },
    Password: { props: ['modelValue', 'placeholder'], template: '<input :placeholder="placeholder" />' },
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
