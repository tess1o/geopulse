vi.hoisted(() => {
  Object.defineProperty(globalThis, 'localStorage', {
    configurable: true,
    value: {
      getItem: vi.fn(),
      setItem: vi.fn(),
      removeItem: vi.fn(),
      clear: vi.fn()
    }
  })
})

import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import AdminSettingsBackupTab from './AdminSettingsBackupTab.vue'
import { useAuthStore } from '@/stores/auth'

const mocks = vi.hoisted(() => ({
  exportAdminSettingsBackup: vi.fn(),
  importAdminSettingsBackup: vi.fn(),
  toastAdd: vi.fn()
}))

vi.mock('@/utils/adminService', () => ({
  default: {
    exportAdminSettingsBackup: mocks.exportAdminSettingsBackup,
    importAdminSettingsBackup: mocks.importAdminSettingsBackup
  }
}))

vi.mock('primevue/usetoast', () => ({
  useToast: () => ({ add: mocks.toastAdd })
}))

const ButtonStub = {
  props: ['label', 'loading', 'disabled', 'severity'],
  emits: ['click'],
  template: '<button :disabled="disabled" @click="$emit(\'click\', $event)">{{ label }}</button>'
}

const FileUploadStub = {
  props: ['disabled', 'chooseLabel'],
  emits: ['select', 'clear'],
  methods: {
    clear() {
      this.$emit('clear')
    },
    selectFile() {
      this.$emit('select', { files: [{ name: 'backup.json', type: 'application/json' }] })
    }
  },
  template: '<button class="file-upload" :disabled="disabled" @click="selectFile">{{ chooseLabel }}</button>'
}

const DialogStub = {
  props: ['visible'],
  emits: ['update:visible'],
  template: '<div v-if="visible" class="dialog"><slot /><slot name="footer" /></div>'
}

const mountTab = ({ adminReadOnly = false } = {}) => {
  const pinia = createPinia()
  setActivePinia(pinia)
  const authStore = useAuthStore()
  authStore.user = {
    id: 'admin-1',
    userId: 'admin-1',
    role: 'ADMIN',
    canViewAdmin: true,
    adminReadOnly
  }

  return mount(AdminSettingsBackupTab, {
    global: {
      plugins: [pinia],
      stubs: {
        Button: ButtonStub,
        Dialog: DialogStub,
        FileUpload: FileUploadStub,
        Message: { template: '<div><slot /></div>' }
      }
    }
  })
}

describe('AdminSettingsBackupTab', () => {
  beforeEach(() => {
    mocks.exportAdminSettingsBackup.mockReset().mockResolvedValue(true)
    mocks.importAdminSettingsBackup.mockReset().mockResolvedValue({
      settingsImported: 4,
      oidcProvidersImported: 1,
      customGeocodingProvidersImported: 1
    })
    mocks.toastAdd.mockReset()
  })

  it('downloads the admin settings backup', async () => {
    const wrapper = mountTab()

    await wrapper.findAll('button').find(button => button.text() === 'Export Settings').trigger('click')
    await flushPromises()

    expect(mocks.exportAdminSettingsBackup).toHaveBeenCalledTimes(1)
    expect(mocks.toastAdd).toHaveBeenCalledWith(expect.objectContaining({
      severity: 'success',
      summary: 'Export Started'
    }))
  })

  it('imports the selected backup file after confirmation', async () => {
    const wrapper = mountTab()

    await wrapper.find('.file-upload').trigger('click')
    await wrapper.findAll('button').find(button => button.text() === 'Import Settings').trigger('click')
    await wrapper.find('.dialog').findAll('button').find(button => button.text() === 'Import').trigger('click')
    await flushPromises()

    expect(mocks.importAdminSettingsBackup).toHaveBeenCalledTimes(1)
    const file = mocks.importAdminSettingsBackup.mock.calls[0][0]
    expect(file.name).toBe('backup.json')
    expect(mocks.toastAdd).toHaveBeenCalledWith(expect.objectContaining({
      severity: 'success',
      detail: 'Restored 4 settings, 1 OIDC providers, and 1 custom geocoding providers.'
    }))
  })

  it('disables actions in admin read-only mode', async () => {
    const wrapper = mountTab({ adminReadOnly: true })

    const exportButton = wrapper.findAll('button').find(button => button.text() === 'Export Settings')
    const fileButton = wrapper.find('.file-upload')
    const importButton = wrapper.findAll('button').find(button => button.text() === 'Import Settings')

    expect(exportButton.attributes('disabled')).toBeDefined()
    expect(fileButton.attributes('disabled')).toBeDefined()
    expect(importButton.attributes('disabled')).toBeDefined()
  })
})
