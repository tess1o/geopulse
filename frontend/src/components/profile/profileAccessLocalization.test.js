import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

/**
 * The Security tab's access surfaces and the Connected Apps panels, rendered in Ukrainian.
 *
 * Rather than enumerating copy, each panel is mounted and its rendered text is asserted to contain no
 * dotted key path: an unresolved key renders as its own path, which is the failure mode this whole
 * group is exposed to (most of their strings are only reachable through a dialog, a tooltip or a
 * status branch that an ordinary assertion would miss).
 */

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

vi.mock('@/utils/apiService', () => ({
  default: {
    get: vi.fn().mockResolvedValue({}),
    post: vi.fn().mockResolvedValue({}),
    delete: vi.fn().mockResolvedValue({})
  }
}))
vi.mock('@/utils/demoMode', () => ({ showDemoModeToast: vi.fn() }))
// Several panels toast or confirm; the service is provided by the app plugin, which these mounts skip.
vi.mock('primevue/usetoast', () => ({ useToast: () => ({ add: vi.fn(), remove: vi.fn() }) }))
vi.mock('primevue/useconfirm', () => ({ useConfirm: () => ({ require: vi.fn() }) }))
vi.mock('@/stores/immich', () => ({
  useImmichStore: () => ({ testImmichConnection: vi.fn(), testConnection: vi.fn(), updateConfig: vi.fn() })
}))
vi.mock('@/stores/notes', () => ({
  useNotesStore: () => ({ testMemosConfig: vi.fn(), updateMemosConfig: vi.fn() })
}))
vi.mock('@/stores/ai', () => ({ useAIStore: () => ({ testConnection: vi.fn(), saveSettings: vi.fn() }) }))

const Inert = { template: '<div />' }
const globalOptions = {
  stubs: {
    Card: { template: '<div><slot name="content" /></div>' },
    SettingCard: { props: ['title', 'description', 'details'], template: '<section><h3>{{ title }}</h3><p>{{ description }}</p><slot name="control" /></section>' },
    Button: { props: ['label'], template: '<button>{{ label }}</button>' },
    Dropdown: { props: ['options', 'placeholder'], template: '<div><span v-for="o in (options || [])" :key="o.value">{{ o.label }}</span></div>' },
    MultiSelect: Inert, InputSwitch: Inert, ToggleSwitch: Inert, InputText: Inert,
    InputNumber: Inert, Password: Inert, Textarea: Inert, Message: Inert, Tag: Inert,
    DataTable: Inert, Column: Inert, Dialog: Inert, DatePicker: Inert, IconField: Inert,
    InputIcon: Inert, Checkbox: Inert, Avatar: Inert
  }
}

// Any dotted path of two or more lowercase segments reads as a leaked key.
const LEAKED_KEY = /\b(profile|insights|nav|errors|weather|movementTypes|common|settings)\.[a-z][\w-]*(\.[a-z][\w-]*)+\b/

describe('security access surfaces and connected app panels in Ukrainian', () => {
  beforeEach(async () => {
    setActivePinia(createPinia())
    // The panels read lists straight off these calls; a bare `{}` leaves them undefined.
    const { useAuthStore } = await import('@/stores/auth')
    vi.spyOn(useAuthStore(), 'getOidcProviders').mockResolvedValue([])
    vi.spyOn(useAuthStore(), 'getLinkedProviders').mockResolvedValue([])
    vi.spyOn(useAuthStore(), 'listApiTokens').mockResolvedValue([])
    const { setLocale } = await import('@/composables/useLocale')
    await setLocale('uk', { persist: false })
  })

  const panels = [
    ['OidcManagement', '@/components/auth/OidcManagement.vue', { readOnly: false }],
    ['ApiTokensManagement', '@/components/profile/ApiTokensManagement.vue', { readOnly: false }],
    ['AIAssistantTab', '@/components/profile/AIAssistantTab.vue', { readOnly: false, initialSettings: {} }],
    ['ImmichTab', '@/components/profile/ImmichTab.vue', { readOnly: false, config: {} }],
    ['MemosTab', '@/components/profile/MemosTab.vue', { readOnly: false, config: {} }],
    ['ConnectedAppsTab', '@/components/profile/ConnectedAppsTab.vue', {
      readOnly: false, activeApp: 'ai', aiSettings: {}, immichConfig: {}, memosConfig: {}
    }]
  ]

  it.each(panels)('%s renders without leaking a translation key', async (_name, path, props) => {
    const Component = (await import(/* @vite-ignore */ path)).default
    const wrapper = mount(Component, { props, global: globalOptions })
    await flushPromises()
    await wrapper.vm.$nextTick()

    expect(wrapper.text()).not.toMatch(LEAKED_KEY)
    expect(wrapper.html()).not.toMatch(LEAKED_KEY)
  })

  it('translates the Connected Apps status chips', async () => {
    const Component = (await import('@/components/profile/ConnectedAppsTab.vue')).default
    const wrapper = mount(Component, {
      props: { readOnly: false, activeApp: 'ai', aiSettings: { enabled: true }, immichConfig: { enabled: true }, memosConfig: {} },
      global: globalOptions
    })

    const text = wrapper.text()
    expect(text).toContain('Підключені застосунки')
    expect(text).toContain('Увімкнено')
    expect(text).toContain('Підключено')
    expect(text).toContain('Не налаштовано')
  })
})
