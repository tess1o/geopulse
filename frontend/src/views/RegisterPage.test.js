import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const { push, toastAdd } = vi.hoisted(() => {
  const storage = new Map()
  const shim = {
    getItem: (key) => storage.get(key) || null,
    setItem: (key, value) => storage.set(key, String(value)),
    removeItem: (key) => storage.delete(key),
    clear: () => storage.clear()
  }
  Object.defineProperty(globalThis, 'localStorage', { configurable: true, value: shim })
  Object.defineProperty(window, 'localStorage', { configurable: true, value: shim })
  return { push: vi.fn(), toastAdd: vi.fn() }
})

vi.mock('vue-router', () => ({
  useRoute: () => ({ query: {} }),
  useRouter: () => ({ push, replace: vi.fn() })
}))
vi.mock('primevue/usetoast', () => ({ useToast: () => ({ add: toastAdd }) }))

const Inert = { template: '<div />' }
const Field = {
  props: ['modelValue', 'placeholder'],
  emits: ['update:modelValue'],
  template: '<input :placeholder="placeholder" :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />'
}
const globalOptions = {
  stubs: {
    Card: { template: '<div><slot name="content" /><slot /></div>' },
    Button: { props: ['label'], template: '<button>{{ label }}</button>' },
    InputText: Field,
    Password: Field,
    Message: { props: ['content'], template: '<div>{{ content }}</div>' },
    Toast: Inert,
    ErrorReferenceToast: Inert,
    OidcProvidersSection: Inert,
    LocaleSwitcher: Inert,
    'router-link': { props: ['to'], template: '<a><slot /></a>' }
  }
}

const problemError = (status, code, detail) => Object.assign(new Error(`Request failed with status code ${status}`), {
  response: {
    status,
    data: { type: `urn:geopulse:error:${code}`, status, code, detail, errorId: 'e-1' }
  },
  config: { url: '/registrations' }
})

/**
 * Registration failures drive the real page and auth store, so what is asserted is what the user reads.
 * The backend contract for these codes is RegistrationErrorContractTest.
 */
describe('RegisterPage errors', () => {
  let apiService

  beforeEach(async () => {
    push.mockClear()
    toastAdd.mockClear()
    setActivePinia(createPinia())
    const { useAuthStore } = await import('@/stores/auth')
    apiService = (await import('@/utils/apiService')).default
    vi.spyOn(useAuthStore(), 'getRegistrationStatus').mockResolvedValue({
      passwordRegistrationEnabled: true,
      oidcRegistrationEnabled: false
    })
    vi.spyOn(console, 'error').mockImplementation(() => {})
  })

  const submit = async () => {
    const RegisterPage = (await import('./RegisterPage.vue')).default
    const wrapper = mount(RegisterPage, { global: globalOptions })
    await flushPromises()

    const [email, fullName, password, confirmPassword] = wrapper.findAll('input')
    await email.setValue('new-user@example.com')
    await fullName.setValue('New User')
    await password.setValue('password123')
    await confirmPassword.setValue('password123')
    await wrapper.find('form').trigger('submit')
    await flushPromises()
    return wrapper
  }

  // Registration can be switched off while the form is open; that used to read as "email already exists".
  it('says registration is disabled when the backend refuses it', async () => {
    vi.spyOn(apiService, 'post').mockRejectedValue(
      problemError(403, 'PASSWORD_REGISTRATION_DISABLED', 'Password registration is disabled'))

    const text = (await submit()).text()

    expect(text).toContain('Sign up using email/password is currently disabled.')
    expect(text).not.toContain('already exists')
    expect(text).not.toContain('Request failed')
  })

  it('sends the user to sign in when the account was created but the automatic sign-in failed', async () => {
    vi.spyOn(apiService, 'post').mockResolvedValue({ id: 'u-1' })
    vi.spyOn(apiService, 'login').mockRejectedValue(problemError(401, 'INVALID_CREDENTIALS', 'Invalid email or password'))

    const wrapper = await submit()

    expect(push).toHaveBeenCalledWith('/login')
    expect(toastAdd).toHaveBeenCalledWith(expect.objectContaining({
      severity: 'info',
      summary: 'Account created',
      detail: 'Your account is ready. Please sign in to continue.'
    }))
    expect(wrapper.find('.register-error').exists()).toBe(false)
  })
})
