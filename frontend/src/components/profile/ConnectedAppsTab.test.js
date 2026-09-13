vi.hoisted(() => {
  Object.defineProperty(globalThis, 'localStorage', {
    configurable: true,
    value: { getItem: vi.fn(), setItem: vi.fn(), removeItem: vi.fn(), clear: vi.fn() }
  })
})

import { mount } from '@vue/test-utils'
import ConnectedAppsTab from './ConnectedAppsTab.vue'

const CardStub = { template: '<section><slot name="content" /></section>' }
const appStub = (name) => ({ name, template: `<div data-app-panel="${name}" />` })
const props = {
  activeApp: 'ai',
  aiSettings: { enabled: true },
  immichConfig: { enabled: true },
  memosConfig: { enabled: false },
  immichLoading: false,
  memosLoading: false
}

const mountTab = () => mount(ConnectedAppsTab, {
  props,
  global: {
    stubs: {
      Card: CardStub,
      AIAssistantTab: appStub('ai'),
      ImmichTab: appStub('immich'),
      MemosTab: appStub('memos')
    }
  }
})

describe('ConnectedAppsTab', () => {
  it('renders compact status tabs and selects an integration', async () => {
    const wrapper = mountTab()
    const tabs = wrapper.findAll('[role="tab"]')

    expect(tabs).toHaveLength(3)
    expect(tabs[0].attributes('aria-selected')).toBe('true')
    expect(tabs[0].get('.app-tab-label-text').text()).toBe('AI Assistant')
    expect(tabs[1].get('.app-tab-label-text').text()).toBe('Immich')
    expect(tabs[2].get('.app-tab-label-text').text()).toBe('Memos')
    expect(tabs[0].text()).toContain('Enabled')
    expect(tabs[1].text()).toContain('Connected')
    expect(tabs[2].text()).toContain('Not configured')
    expect(wrapper.find('[data-app-panel="ai"]').exists()).toBe(true)

    await tabs[1].trigger('click')
    expect(wrapper.emitted('select-app').at(-1)[0]).toBe('immich')
    expect(wrapper.get('#connected-app-panel-immich').attributes('aria-labelledby')).toBe('connected-app-tab-immich')
    expect(wrapper.find('[data-app-panel="immich"]').exists()).toBe(true)
  })

  it('supports keyboard navigation between tabs', async () => {
    const wrapper = mountTab()

    await wrapper.get('#connected-app-tab-ai').trigger('keydown', { key: 'ArrowRight' })
    expect(wrapper.emitted('select-app').at(-1)[0]).toBe('immich')
    expect(wrapper.get('#connected-app-tab-immich').attributes('aria-selected')).toBe('true')
  })
})
