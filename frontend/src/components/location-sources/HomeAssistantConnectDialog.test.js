vi.hoisted(() => {
  Object.defineProperty(globalThis, 'localStorage', {
    configurable: true,
    value: { getItem: vi.fn(), setItem: vi.fn(), removeItem: vi.fn(), clear: vi.fn() }
  })
})

import { mount } from '@vue/test-utils'
import HomeAssistantConnectDialog from './HomeAssistantConnectDialog.vue'
import { HOME_ASSISTANT_ADD_INTEGRATION_URL, HOME_ASSISTANT_INTEGRATION_URL } from '@/constants/homeAssistantIntegration'

const DialogStub = { template: '<div><slot /><slot name="footer" /></div>' }

const mountDialog = (props) => mount(HomeAssistantConnectDialog, {
  props: {
    visible: true,
    serverUrl: 'https://geopulse.example.com',
    ...props
  },
  global: { stubs: { Dialog: DialogStub, Message: { template: '<div><slot /></div>' } } }
})

describe('HomeAssistantConnectDialog', () => {
  it('walks through installing and adding the integration with the server URL and new token', () => {
    const wrapper = mountDialog({ tokenSecret: 'gp_sa_secret', tokenName: 'Home Assistant' })
    const hrefs = wrapper.findAll('a').map(link => link.attributes('href'))

    expect(hrefs).toContain(HOME_ASSISTANT_INTEGRATION_URL)
    expect(hrefs).toContain(HOME_ASSISTANT_ADD_INTEGRATION_URL)
    expect(wrapper.text()).toContain('https://geopulse.example.com')
    expect(wrapper.text()).not.toContain('/api/v1/gps/ingest')
    expect(wrapper.find('[data-testid="ha-connect-token"]').text()).toBe('gp_sa_secret')
  })

  it('points to the saved token when an existing one was connected', () => {
    const wrapper = mountDialog({ tokenSecret: null, tokenName: 'Phone' })

    expect(wrapper.find('[data-testid="ha-connect-token"]').exists()).toBe(false)
    expect(wrapper.text()).toContain('"Phone"')
  })
})
