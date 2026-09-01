import { flushPromises, mount } from '@vue/test-utils'
import AdminFullBackupSection from './AdminFullBackupSection.vue'

const mocks = vi.hoisted(() => ({
  toast: { add: vi.fn() },
  getBackupConfig: vi.fn(),
  getBackupFiles: vi.fn(),
  getBackupStatus: vi.fn(),
  updateBackupConfig: vi.fn()
}))

vi.mock('primevue/usetoast', () => ({ useToast: () => mocks.toast }))
vi.mock('@/utils/adminService', () => ({
  default: {
    getBackupConfig: mocks.getBackupConfig,
    getBackupFiles: mocks.getBackupFiles,
    getBackupStatus: mocks.getBackupStatus,
    updateBackupConfig: mocks.updateBackupConfig
  }
}))
vi.mock('@/stores/maintenance', () => ({ applyMaintenanceStatus: vi.fn(), refreshMaintenance: vi.fn() }))

const ButtonStub = {
  props: ['label', 'disabled', 'loading'],
  emits: ['click'],
  template: '<button :disabled="disabled" @click="$emit(\'click\')">{{ label }}</button>'
}
const InputTextStub = {
  props: ['modelValue', 'type', 'minlength', 'maxlength'],
  emits: ['update:modelValue'],
  template: '<input :type="type" :value="modelValue" :minlength="minlength" :maxlength="maxlength" @input="$emit(\'update:modelValue\', $event.target.value)" />'
}

const mountSection = () => mount(AdminFullBackupSection, {
  props: { adminReadOnly: false },
  global: {
    stubs: {
      Button: ButtonStub,
      Column: true,
      DataTable: { template: '<div><slot /></div>' },
      Dialog: { template: '<div><slot /><slot name="footer" /></div>' },
      FileUpload: true,
      InputNumber: true,
      InputText: InputTextStub,
      Message: { template: '<div><slot /></div>' },
      ProgressBar: true,
      ToggleSwitch: true
    },
    directives: { tooltip: () => {} }
  }
})

describe('AdminFullBackupSection password validation', () => {
  beforeEach(() => {
    mocks.toast.add.mockReset()
    mocks.getBackupConfig.mockReset().mockResolvedValue({
      passwordConfigured: true,
      scheduledEnabled: false,
      scheduledCron: '0 0 3 * * ?',
      localPath: '/backups',
      retentionCount: 7,
      operationTimeoutMinutes: 120
    })
    mocks.getBackupFiles.mockReset().mockResolvedValue([])
    mocks.getBackupStatus.mockReset().mockResolvedValue({ status: 'idle' })
    mocks.updateBackupConfig.mockReset().mockImplementation(async config => ({ ...config, passwordConfigured: true }))
  })

  it('enforces the new-password bounds before calling the API', async () => {
    const wrapper = mountSection()
    await flushPromises()
    const passwordInputs = wrapper.findAll('input[type="password"]')
    expect(passwordInputs[0].attributes('minlength')).toBe('12')
    expect(passwordInputs[0].attributes('maxlength')).toBe('1024')

    await passwordInputs[0].setValue('too-short')
    await passwordInputs[1].setValue('too-short')
    await wrapper.findAll('button').find(button => button.text() === 'Save Backup Settings').trigger('click')
    await flushPromises()

    expect(mocks.updateBackupConfig).not.toHaveBeenCalled()
    expect(mocks.toast.add).toHaveBeenCalledWith(expect.objectContaining({ detail: 'New backup password must contain 12–1024 characters' }))

    await passwordInputs[0].setValue('twelve-chars!')
    await passwordInputs[1].setValue('twelve-chars!')
    await wrapper.findAll('button').find(button => button.text() === 'Save Backup Settings').trigger('click')
    await flushPromises()

    expect(mocks.updateBackupConfig).toHaveBeenCalledWith(expect.objectContaining({ password: 'twelve-chars!' }))
    wrapper.unmount()
  })
})
