vi.hoisted(() => {
  Object.defineProperty(globalThis, 'localStorage', {
    configurable: true,
    value: { getItem: vi.fn(), setItem: vi.fn(), removeItem: vi.fn(), clear: vi.fn() }
  })
})

const { saveApiToken, push, toastAdd, copyToClipboard } = vi.hoisted(() => ({
  saveApiToken: vi.fn(),
  push: vi.fn(),
  toastAdd: vi.fn(),
  copyToClipboard: vi.fn()
}))

vi.mock('@/stores/auth', () => ({ useAuthStore: () => ({ saveApiToken }) }))
vi.mock('vue-router', () => ({ useRouter: () => ({ push }) }))
vi.mock('primevue/usetoast', () => ({ useToast: () => ({ add: toastAdd }) }))
vi.mock('@/utils/clipboardUtils', () => ({ copyToClipboard }))

import { flushPromises, mount } from '@vue/test-utils'
import McpTab from './McpTab.vue'
import { MCP_TOKEN_PLACEHOLDER } from './mcpClientSnippets'

const SettingCardStub = {
  props: ['title', 'description', 'settingId'],
  template: '<section :data-setting-id="settingId"><h3>{{ title }}</h3><slot name="control" /></section>'
}
const ButtonStub = {
  props: ['label', 'disabled', 'loading'],
  emits: ['click'],
  template: '<button :disabled="disabled" @click="$emit(\'click\')">{{ label }}</button>'
}
const SelectButtonStub = {
  props: ['modelValue', 'options'],
  emits: ['update:modelValue'],
  template: '<div class="select-button"><button v-for="o in options" :key="o.value" :data-client="o.value" @click="$emit(\'update:modelValue\', o.value)">{{ o.label }}</button></div>'
}

const mountTab = (props = {}) => mount(McpTab, {
  props,
  global: { stubs: { SettingCard: SettingCardStub, Button: ButtonStub, SelectButton: SelectButtonStub } }
})

const createButton = (wrapper) => wrapper.findAll('button').find(button => button.text() === 'Create read-only token')
const serverUrl = `${window.location.origin}/mcp`

describe('McpTab', () => {
  beforeEach(() => {
    saveApiToken.mockReset()
    push.mockReset()
    toastAdd.mockReset()
    copyToClipboard.mockReset()
  })

  it('shows the same-origin server URL and a placeholder Claude Desktop config', () => {
    const wrapper = mountTab()

    expect(wrapper.get('[data-setting-id="mcpServerUrl"] code').text()).toBe(serverUrl)
    const snippet = wrapper.get('.snippet pre').text()
    expect(snippet).toContain('mcp-remote@latest')
    expect(snippet).toContain(serverUrl)
    expect(snippet).toContain(`Bearer ${MCP_TOKEN_PLACEHOLDER}`)
    expect(wrapper.text()).toContain(`Replace ${MCP_TOKEN_PLACEHOLDER} with your API token`)
  })

  it('switches the snippet with the selected client', async () => {
    const wrapper = mountTab()

    await wrapper.get('[data-client="claudeCode"]').trigger('click')
    expect(wrapper.get('.snippet pre').text())
      .toBe(`claude mcp add --transport http geopulse ${serverUrl} --header "Authorization: Bearer ${MCP_TOKEN_PLACEHOLDER}"`)

    await wrapper.get('[data-client="other"]').trigger('click')
    expect(wrapper.get('.snippet pre').text()).toContain(`URL:    ${serverUrl}`)
    expect(wrapper.find('a[href$="#chatgpt-through-an-openai-tunnel"]').exists()).toBe(true)
  })

  it('creates a read-only token named after the client and fills it into the snippet', async () => {
    saveApiToken.mockResolvedValue({ token: 'gp_secret', apiToken: { id: 't1' } })
    const wrapper = mountTab()

    await wrapper.get('[data-client="claudeCode"]').trigger('click')
    await createButton(wrapper).trigger('click')
    await flushPromises()

    expect(saveApiToken).toHaveBeenCalledWith(null, { name: 'MCP (Claude Code)', expiresAt: null, scopes: ['read'] })
    expect(wrapper.get('[data-setting-id="mcpApiToken"] code').text()).toBe('gp_secret')
    expect(wrapper.text()).toContain('GeoPulse will not show it again')
    expect(wrapper.get('.snippet pre').text()).toContain('Bearer gp_secret')
    expect(wrapper.text()).not.toContain(`Replace ${MCP_TOKEN_PLACEHOLDER}`)
    expect(createButton(wrapper)).toBeUndefined()
  })

  it('reports a failed token creation and keeps the placeholder', async () => {
    saveApiToken.mockRejectedValue(new Error('boom'))
    const wrapper = mountTab()

    await createButton(wrapper).trigger('click')
    await flushPromises()

    expect(toastAdd).toHaveBeenCalledWith(expect.objectContaining({ severity: 'error', summary: 'Could not create token' }))
    expect(wrapper.get('.snippet pre').text()).toContain(`Bearer ${MCP_TOKEN_PLACEHOLDER}`)
  })

  it('disables token creation when read-only', () => {
    const wrapper = mountTab({ readOnly: true })

    expect(createButton(wrapper).attributes('disabled')).toBeDefined()
  })

  it('copies the snippet and opens token management', async () => {
    copyToClipboard.mockResolvedValue(true)
    const wrapper = mountTab()

    await wrapper.get('.snippet button').trigger('click')
    await flushPromises()
    expect(copyToClipboard).toHaveBeenCalledWith(wrapper.get('.snippet pre').text())
    expect(toastAdd).toHaveBeenCalledWith(expect.objectContaining({ severity: 'success' }))

    await wrapper.findAll('button').find(button => button.text() === 'Manage tokens').trigger('click')
    expect(push).toHaveBeenCalledWith({ query: { tab: 'security', setting: 'api-tokens-heading' } })
  })
})
