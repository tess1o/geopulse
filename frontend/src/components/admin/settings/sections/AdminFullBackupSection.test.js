import { flushPromises, mount } from '@vue/test-utils'
import AdminFullBackupSection from './AdminFullBackupSection.vue'

const mocks = vi.hoisted(() => ({
  toast: { add: vi.fn() },
  getBackupConfig: vi.fn(),
  getBackupFiles: vi.fn(),
  getBackupStatus: vi.fn(),
  updateBackupConfig: vi.fn(),
  restoreLocalFullBackup: vi.fn(),
  restoreUploadedFullBackup: vi.fn(),
  refreshMaintenance: vi.fn()
}))

vi.mock('primevue/usetoast', () => ({ useToast: () => mocks.toast }))
vi.mock('@/utils/adminService', () => ({
  default: {
    getBackupConfig: mocks.getBackupConfig,
    getBackupFiles: mocks.getBackupFiles,
    getBackupStatus: mocks.getBackupStatus,
    updateBackupConfig: mocks.updateBackupConfig,
    restoreLocalFullBackup: mocks.restoreLocalFullBackup,
    restoreUploadedFullBackup: mocks.restoreUploadedFullBackup
  }
}))
vi.mock('@/stores/maintenance', () => ({ applyMaintenanceStatus: vi.fn(), refreshMaintenance: mocks.refreshMaintenance }))

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
    vi.useRealTimers()
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
    mocks.restoreLocalFullBackup.mockReset().mockResolvedValue({ operationId: 'restore-operation', state: 'PREPARING' })
    mocks.restoreUploadedFullBackup.mockReset().mockResolvedValue({ operationId: 'restore-operation', state: 'PREPARING' })
    mocks.refreshMaintenance.mockReset().mockResolvedValue()
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

  it('renders restore preparation failures as a persistent visible error', async () => {
    mocks.getBackupStatus.mockResolvedValue({
      state: 'PREPARATION_FAILED',
      status: 'preparation_failed',
      operation: 'restore',
      fileName: 'incompatible-backup.gpb',
      error: 'Backup requires the same application database schema and PostgreSQL major version'
    })

    const wrapper = mountSection()
    await flushPromises()

    expect(wrapper.text()).toContain('Restore preparation failed')
    expect(wrapper.text()).toContain('Backup requires the same application database schema and PostgreSQL major version')
    expect(wrapper.text()).toContain('The original database remains active and no restored data was applied.')
    expect(wrapper.text()).toContain('Backup file: incompatible-backup.gpb')
    wrapper.unmount()
  })

  it('keeps polling after an accepted restore until preparation reaches a terminal failure', async () => {
    vi.useFakeTimers()
    mocks.getBackupStatus
      .mockResolvedValueOnce({ status: 'idle' })
      .mockResolvedValueOnce({ state: 'PREPARING', status: 'preparing', operation: 'restore', restoreRunning: true, progressPercent: 20 })
      .mockResolvedValueOnce({
        state: 'PREPARATION_FAILED',
        status: 'preparation_failed',
        operation: 'restore',
        restoreRunning: false,
        fileName: 'bad-schema.gpb',
        error: 'Backup requires the same application database schema and PostgreSQL major version'
      })

    const wrapper = mountSection()
    await flushPromises()
    wrapper.vm.openRestoreLocalDialog('bad-schema.gpb')
    wrapper.vm.restorePassword = 'source-password'
    await wrapper.vm.restoreFullBackup()
    await flushPromises()

    expect(mocks.restoreLocalFullBackup).toHaveBeenCalledWith('bad-schema.gpb', 'source-password')
    expect(mocks.getBackupStatus).toHaveBeenCalledTimes(2)

    await vi.advanceTimersByTimeAsync(1000)
    await flushPromises()

    expect(mocks.getBackupStatus).toHaveBeenCalledTimes(3)
    expect(wrapper.text()).toContain('Restore preparation failed')
    expect(wrapper.text()).toContain('Backup requires the same application database schema and PostgreSQL major version')
    expect(mocks.refreshMaintenance).toHaveBeenCalled()

    await vi.advanceTimersByTimeAsync(1000)
    await flushPromises()
    expect(mocks.getBackupStatus).toHaveBeenCalledTimes(3)
    wrapper.unmount()
  })

  it('does not show a previous preparation failure while submitting a new restore', async () => {
    let resolveRestore
    mocks.getBackupStatus.mockResolvedValue({
      state: 'PREPARATION_FAILED',
      status: 'preparation_failed',
      operation: 'restore',
      restoreRunning: false,
      fileName: 'old-backup.gpb',
      error: 'Incorrect backup password or damaged backup'
    })
    mocks.restoreUploadedFullBackup.mockReturnValue(new Promise(resolve => { resolveRestore = resolve }))

    const wrapper = mountSection()
    await flushPromises()
    expect(wrapper.text()).toContain('Incorrect backup password or damaged backup')

    wrapper.vm.selectedFullFile = new File(['backup'], 'new-backup.gpb')
    wrapper.vm.openRestoreUploadDialog()
    wrapper.vm.restorePassword = 'new-password'
    const restorePromise = wrapper.vm.restoreFullBackup()
    await wrapper.vm.$nextTick()

    expect(wrapper.text()).toContain('Restore in progress')
    expect(wrapper.text()).toContain('Restoration is being prepared in the background')
    expect(wrapper.text()).not.toContain('Incorrect backup password or damaged backup')

    resolveRestore({ operationId: 'restore-operation', state: 'PREPARING' })
    await restorePromise
    wrapper.unmount()
  })
})
