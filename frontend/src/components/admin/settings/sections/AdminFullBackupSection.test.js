import { flushPromises, mount } from '@vue/test-utils'
import AdminFullBackupSection from './AdminFullBackupSection.vue'

const mocks = vi.hoisted(() => ({
  toast: { add: vi.fn() },
  getBackupConfig: vi.fn(),
  getBackupFiles: vi.fn(),
  getBackupStatus: vi.fn(),
  updateBackupConfig: vi.fn(),
  updateBackupPassword: vi.fn(),
  downloadFullBackup: vi.fn(),
  downloadLocalBackup: vi.fn(),
  restoreLocalFullBackup: vi.fn(),
  restoreUploadedFullBackup: vi.fn(),
  refreshMaintenance: vi.fn()
}))

vi.mock('primevue/usetoast', () => ({ useToast: () => mocks.toast }))
vi.mock('@/stores/admin', () => ({
  useAdminStore: () => ({
    getBackupConfig: mocks.getBackupConfig,
    getBackupFiles: mocks.getBackupFiles,
    getBackupStatus: mocks.getBackupStatus,
    updateBackupConfig: mocks.updateBackupConfig,
    updateBackupPassword: mocks.updateBackupPassword,
    restoreLocalFullBackup: mocks.restoreLocalFullBackup,
    restoreUploadedFullBackup: mocks.restoreUploadedFullBackup,
    deleteLocalBackup: vi.fn(),
    discardPreparedRestore: vi.fn(),
    downloadFullBackup: mocks.downloadFullBackup,
    downloadLocalBackup: mocks.downloadLocalBackup,
    retryPreparedRestore: vi.fn(),
    runFullBackupNow: vi.fn()
  })
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
      Dialog: {
        props: ['visible', 'header'],
        template: '<div v-if="visible" class="dialog-stub"><span class="dialog-header">{{ header }}</span><slot /><slot name="footer" /></div>'
      },
      FileUpload: true,
      InputNumber: true,
      InputText: InputTextStub,
      Message: { template: '<div><slot /></div>' },
      ProgressBar: {
        props: ['value', 'mode', 'showValue'],
        template: '<div class="progress-stub" :data-mode="mode || \'determinate\'" :data-value="value" />'
      },
      Select: true,
      ToggleSwitch: true
    },
    directives: { tooltip: () => {} }
  }
})

const button = (wrapper, label) => wrapper.findAll('button').find(item => item.text() === label)
const config = (overrides = {}) => ({
  passwordConfigured: true,
  scheduledEnabled: false,
  scheduledCron: '0 0 3 * * ?',
  localPath: '/backups',
  retentionCount: 7,
  operationTimeoutMinutes: 120,
  ...overrides
})

describe('AdminFullBackupSection backup UX', () => {
  beforeEach(() => {
    vi.useRealTimers()
    mocks.toast.add.mockReset()
    mocks.getBackupConfig.mockReset().mockResolvedValue(config())
    mocks.getBackupFiles.mockReset().mockResolvedValue([])
    mocks.getBackupStatus.mockReset().mockResolvedValue({ status: 'idle' })
    mocks.updateBackupConfig.mockReset().mockImplementation(async value => ({ ...value, passwordConfigured: true }))
    mocks.updateBackupPassword.mockReset().mockResolvedValue(config({ passwordConfigured: true }))
    mocks.downloadFullBackup.mockReset()
    mocks.downloadLocalBackup.mockReset()
    mocks.restoreLocalFullBackup.mockReset().mockResolvedValue({ operationId: 'restore-operation', state: 'PREPARING' })
    mocks.restoreUploadedFullBackup.mockReset().mockResolvedValue({ operationId: 'restore-operation', state: 'PREPARING' })
    mocks.refreshMaintenance.mockReset().mockResolvedValue()
  })

  it('keeps download enabled without a password and opens the password dialog on click', async () => {
    mocks.getBackupConfig.mockResolvedValue(config({ passwordConfigured: false }))
    const wrapper = mountSection()
    await flushPromises()

    const download = button(wrapper, 'Download Full Backup')
    expect(download.attributes('disabled')).toBeUndefined()

    await download.trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('Set Backup Password')
    expect(wrapper.findAll('input[type="password"]')).toHaveLength(2)
    expect(mocks.downloadFullBackup).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it('validates and saves the password, then resumes the download', async () => {
    mocks.getBackupConfig.mockResolvedValue(config({ passwordConfigured: false }))
    const wrapper = mountSection()
    await flushPromises()

    await button(wrapper, 'Download Full Backup').trigger('click')
    await flushPromises()
    let inputs = wrapper.findAll('input[type="password"]')
    await inputs[0].setValue('short')
    await inputs[1].setValue('short')
    await button(wrapper, 'Save Password').trigger('click')
    await flushPromises()

    expect(mocks.updateBackupPassword).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('must contain 12–1024 characters')

    inputs = wrapper.findAll('input[type="password"]')
    await inputs[0].setValue('twelve-chars!')
    await inputs[1].setValue('twelve-chars!')
    await button(wrapper, 'Save Password').trigger('click')
    await flushPromises()

    expect(mocks.updateBackupPassword).toHaveBeenCalledWith('twelve-chars!')
    expect(mocks.downloadFullBackup).toHaveBeenCalledTimes(1)
    expect(wrapper.text()).not.toContain('Set Backup Password')
    wrapper.unmount()
  })

  it('renders indeterminate progress with phase and bytes while a backup runs', async () => {
    mocks.getBackupStatus.mockResolvedValue({
      status: 'running',
      operation: 'download',
      backupRunning: true,
      progressPercent: null,
      bytesWritten: 123456,
      phase: 'dumping',
      startedAt: new Date().toISOString()
    })
    const wrapper = mountSection()
    await flushPromises()

    const progress = wrapper.find('.progress-stub')
    expect(progress.attributes('data-mode')).toBe('indeterminate')
    expect(wrapper.text()).toContain('Dumping database data…')
    expect(wrapper.text()).toContain('120.6 KB written')
    expect(wrapper.text()).not.toContain('0%')
    wrapper.unmount()
  })

  it('keeps determinate restore progress percentages', async () => {
    mocks.getBackupStatus.mockResolvedValue({
      state: 'PREPARING',
      status: 'preparing',
      operation: 'restore',
      restoreRunning: true,
      progressPercent: 20
    })
    const wrapper = mountSection()
    await flushPromises()

    const progress = wrapper.find('.progress-stub')
    expect(progress.attributes('data-mode')).toBe('determinate')
    expect(progress.attributes('data-value')).toBe('20')
    expect(wrapper.text()).toContain('20%')
    wrapper.unmount()
  })

  it('does not send a password with scheduled backup settings', async () => {
    const wrapper = mountSection()
    await flushPromises()

    wrapper.vm.backupConfig.healthMaxAgeDays = 0
    wrapper.vm.backupConfig.healthAppriseEnabled = false
    await wrapper.vm.saveBackupConfig()

    expect(mocks.updateBackupConfig).toHaveBeenCalledWith(expect.not.objectContaining({ password: expect.anything() }))
    wrapper.unmount()
  })

  it('opens the password dialog before saving schedules without a password', async () => {
    mocks.getBackupConfig.mockResolvedValue(config({ passwordConfigured: false }))
    const wrapper = mountSection()
    await flushPromises()

    wrapper.vm.backupConfig.scheduledEnabled = true
    await wrapper.vm.saveBackupConfig()
    await flushPromises()
    expect(mocks.updateBackupConfig).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('Set Backup Password')

    const inputs = wrapper.findAll('input[type="password"]')
    await inputs[0].setValue('twelve-chars!')
    await inputs[1].setValue('twelve-chars!')
    await button(wrapper, 'Save Password').trigger('click')
    await flushPromises()

    expect(mocks.updateBackupPassword).toHaveBeenCalledWith('twelve-chars!')
    expect(mocks.updateBackupConfig).toHaveBeenCalledTimes(1)
    expect(mocks.updateBackupConfig.mock.calls[0][0]).not.toHaveProperty('password')
    wrapper.unmount()
  })

  it('tracks an anchor-triggered download through status and reports completion once', async () => {
    vi.useFakeTimers()
    const startedAt = new Date().toISOString()
    mocks.getBackupStatus
      .mockResolvedValueOnce({ status: 'completed', operation: 'download', operationId: 'old', startedAt: '2025-01-01T00:00:00Z', completedAt: '2025-01-01T00:00:00Z' })
      .mockResolvedValueOnce({ status: 'idle' })
      .mockResolvedValueOnce({ status: 'running', operation: 'download', backupRunning: true, operationId: 'new', phase: 'dumping', progressPercent: null, startedAt })
      .mockResolvedValueOnce({ status: 'completed', operation: 'download', operationId: 'new', fileName: 'backup.gpb', sizeBytes: 100, startedAt, completedAt: new Date().toISOString(), downloadTransfer: 'running', downloadTransferredBytes: 25 })
      .mockResolvedValueOnce({ status: 'completed', operation: 'download', operationId: 'new', fileName: 'backup.gpb', sizeBytes: 100, startedAt, completedAt: new Date().toISOString(), downloadTransfer: 'completed', downloadTransferredBytes: 100 })
    const wrapper = mountSection()
    await flushPromises()
    const fileRequests = mocks.getBackupFiles.mock.calls.length

    await button(wrapper, 'Download Full Backup').trigger('click')
    await flushPromises()
    expect(mocks.downloadFullBackup).toHaveBeenCalledTimes(1)

    await vi.advanceTimersByTimeAsync(1000)
    await flushPromises()
    await vi.advanceTimersByTimeAsync(1000)
    await flushPromises()

    // The server built the archive and is still streaming it to the browser.
    expect(wrapper.text()).toContain('Downloading backup')
    expect(wrapper.text()).toContain('25 B of 100 B transferred')
    expect(mocks.toast.add).not.toHaveBeenCalledWith(expect.objectContaining({ summary: 'Download Complete' }))

    await vi.advanceTimersByTimeAsync(1000)
    await flushPromises()

    const successToasts = mocks.toast.add.mock.calls.filter(([value]) => value.summary === 'Download Complete')
    expect(successToasts).toHaveLength(1)
    expect(successToasts[0][0].detail).toContain('choose Keep')
    expect(wrapper.text()).toContain('Download complete — backup.gpb')
    expect(wrapper.text()).not.toContain('Downloading backup')
    expect(mocks.getBackupFiles.mock.calls.length).toBe(fileRequests)
    wrapper.unmount()
  })

  it('reports an interrupted transfer and keeps a visible result', async () => {
    vi.useFakeTimers()
    const startedAt = new Date().toISOString()
    mocks.getBackupStatus
      .mockResolvedValueOnce({ status: 'idle' })
      .mockResolvedValueOnce({ status: 'idle' })
      .mockResolvedValueOnce({ status: 'running', operation: 'download', backupRunning: true, operationId: 'new', phase: 'dumping', progressPercent: null, startedAt })
      .mockResolvedValueOnce({ status: 'completed', operation: 'download', operationId: 'new', fileName: 'backup.gpb', sizeBytes: 100, startedAt, downloadTransfer: 'failed', downloadTransferredBytes: 25 })
    const wrapper = mountSection()
    await flushPromises()

    await button(wrapper, 'Download Full Backup').trigger('click')
    await flushPromises()
    await vi.advanceTimersByTimeAsync(1000)
    await flushPromises()
    await vi.advanceTimersByTimeAsync(1000)
    await flushPromises()

    expect(mocks.toast.add).toHaveBeenCalledWith(expect.objectContaining({ severity: 'error', summary: 'Download Interrupted' }))
    expect(wrapper.text()).toContain('Download interrupted.')
    expect(wrapper.text()).not.toContain('Downloading backup')
    wrapper.unmount()
  })

  it('reports a download that never reaches the server', async () => {
    vi.useFakeTimers()
    mocks.getBackupStatus.mockResolvedValue({ status: 'idle' })
    const wrapper = mountSection()
    await flushPromises()

    await button(wrapper, 'Download Full Backup').trigger('click')
    await flushPromises()
    await vi.advanceTimersByTimeAsync(20000)
    await flushPromises()

    expect(mocks.toast.add).toHaveBeenCalledWith(expect.objectContaining({ summary: 'Download Did Not Start' }))
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
