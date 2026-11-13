import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import AdminSettingsBackupTab from './AdminSettingsBackupTab.vue'
import { useAuthStore } from '@/stores/auth'

const FullSectionStub = { template: '<div class="full-section-stub">Full backup</div>' }
const SettingsSectionStub = { template: '<div class="settings-section-stub">Settings export</div>' }

const mountTab = () => {
  const pinia = createPinia()
  setActivePinia(pinia)
  useAuthStore().setUser({ id: 'admin', role: 'ADMIN' })
  return mount(AdminSettingsBackupTab, {
    global: {
      plugins: [pinia],
      stubs: {
        AdminFullBackupSection: FullSectionStub,
        AdminSettingsExportSection: SettingsSectionStub
      }
    }
  })
}

describe('AdminSettingsBackupTab tabs', () => {
  it('renders accessible tabs and keeps both panels mounted', async () => {
    const wrapper = mountTab()
    await flushPromises()

    const tabs = wrapper.findAll('[role="tab"]')
    expect(tabs).toHaveLength(2)
    expect(tabs[0].attributes('aria-selected')).toBe('true')
    expect(tabs[1].attributes('aria-selected')).toBe('false')
    expect(wrapper.find('[role="tablist"]').exists()).toBe(true)
    expect(wrapper.findAll('[role="tabpanel"]')).toHaveLength(2)
    expect(wrapper.find('#backup-panel-full').attributes('style') || '').not.toContain('display: none')
    expect(wrapper.find('#backup-panel-settings').attributes('style')).toContain('display: none')
    expect(wrapper.findComponent(FullSectionStub).exists()).toBe(true)
    expect(wrapper.findComponent(SettingsSectionStub).exists()).toBe(true)
    wrapper.unmount()
  })

  it('switches panels with click and keyboard without unmounting the full section', async () => {
    const wrapper = mountTab()
    await flushPromises()

    await wrapper.findAll('[role="tab"]')[1].trigger('click')
    expect(wrapper.findAll('[role="tab"]')[1].attributes('aria-selected')).toBe('true')
    expect(wrapper.find('#backup-panel-full').attributes('style')).toContain('display: none')
    expect(wrapper.find('#backup-panel-settings').attributes('style') || '').not.toContain('display: none')
    expect(wrapper.findComponent(FullSectionStub).exists()).toBe(true)

    await wrapper.findAll('[role="tab"]')[1].trigger('keydown', { key: 'ArrowLeft' })
    expect(wrapper.findAll('[role="tab"]')[0].attributes('aria-selected')).toBe('true')
    expect(wrapper.findComponent(FullSectionStub).exists()).toBe(true)
    wrapper.unmount()
  })
})
