<template>
  <section class="backup-section full-backup-section">
    <div class="backup-section-header">
      <div>
        <h3>{{ t('adminSettings.fullBackupSection.title') }}</h3>
        <p class="text-muted">{{ t('adminSettings.fullBackupSection.subtitle') }}</p>
      </div>
    </div>

    <div class="backup-section-body">
      <div class="backup-grid">
        <section class="backup-panel">
          <div class="backup-panel-header">
            <div><h4>{{ t('adminSettings.fullBackupSection.downloadPanel.title') }}</h4><p class="text-muted">{{ t('adminSettings.fullBackupSection.downloadPanel.description') }}</p></div>
            <i class="pi pi-download backup-panel-icon"></i>
          </div>
          <Button :label="t('adminSettings.fullBackupSection.downloadPanel.button')" icon="pi pi-download" :loading="fullDownloading" :disabled="adminReadOnly || operationRunning || !backupConfig.passwordConfigured" @click="downloadFullBackup" />
        </section>

        <section class="backup-panel">
          <div class="backup-panel-header">
            <div><h4>{{ t('adminSettings.fullBackupSection.runNowPanel.title') }}</h4><p class="text-muted">{{ t('adminSettings.fullBackupSection.runNowPanel.description') }}</p></div>
            <i class="pi pi-save backup-panel-icon"></i>
          </div>
          <Button :label="t('adminSettings.fullBackupSection.runNowPanel.button')" icon="pi pi-play" :loading="runningNow" :disabled="adminReadOnly || operationRunning || !backupConfig.passwordConfigured" @click="runBackupNow" />
        </section>
      </div>

      <Message severity="warn" :closable="false">
        {{ t('adminSettings.fullBackupSection.warnMessage') }}
      </Message>

      <Message v-if="restorePreparationFailed" severity="error" :closable="false">
        <div class="restore-failure">
          <div>
            <h4>{{ t('adminSettings.fullBackupSection.restorePreparationFailedTitle') }}</h4>
            <p>{{ restoreFailureMessage }}</p>
            <p class="text-muted">{{ t('adminSettings.fullBackupSection.originalDatabaseActive') }}</p>
            <p v-if="backupStatus?.fileName" class="text-muted">{{ t('adminSettings.fullBackupSection.backupFileLabel', { fileName: backupStatus.fileName }) }}</p>
          </div>
        </div>
      </Message>

      <Message v-if="backupStatus?.state === 'ACTIVATION_RETRYABLE'" severity="warn" :closable="false">
        <div class="retryable-restore">
          <span>{{ backupStatus.error || t('adminSettings.fullBackupSection.activationRetryableFallback') }}</span>
          <div class="file-actions">
            <Button :label="t('adminSettings.fullBackupSection.retryActivation')" icon="pi pi-refresh" :loading="restoring" @click="retryActivation" />
            <Button :label="t('adminSettings.fullBackupSection.discardPreparedRestore')" icon="pi pi-trash" severity="danger" outlined :loading="deleting" @click="discardPreparedRestore" />
          </div>
        </div>
      </Message>

      <div v-if="showBackupProgress" class="backup-progress">
        <div class="progress-heading">
          <div><h4>{{ backupProgressTitle }}</h4><span>{{ backupProgressMessage }}</span></div>
          <strong>{{ backupProgressValue }}%</strong>
        </div>
        <ProgressBar :value="backupProgressValue" :showValue="false" />
        <div class="progress-caption"><span>{{ backupStatus?.fileName || '' }}</span></div>
      </div>

      <section class="backup-subsection">
        <div class="subsection-header"><div><h4>{{ t('adminSettings.fullBackupSection.scheduledSection.title') }}</h4><p class="text-muted">{{ t('adminSettings.fullBackupSection.scheduledSection.description') }}</p></div></div>
        <div class="config-grid">
          <div class="config-group schedule-group">
            <h5>{{ t('adminSettings.fullBackupSection.scheduledSection.backupPassword') }}</h5>
            <label class="config-field">
              <span>{{ backupConfig.passwordConfigured ? t('adminSettings.fullBackupSection.scheduledSection.changePasswordLabel') : t('adminSettings.fullBackupSection.scheduledSection.passwordRequiredLabel') }}</span>
              <InputText v-model="backupPassword" type="password" autocomplete="new-password" minlength="12" maxlength="1024" :disabled="adminReadOnly" />
            </label>
            <label class="config-field"><span>{{ t('adminSettings.fullBackupSection.scheduledSection.confirmNewPassword') }}</span><InputText v-model="backupPasswordConfirmation" type="password" autocomplete="new-password" minlength="12" maxlength="1024" :disabled="adminReadOnly" /></label>
            <small class="text-muted">{{ t('adminSettings.fullBackupSection.scheduledSection.passwordLengthHint') }}</small>
            <h5>{{ t('adminSettings.fullBackupSection.scheduledSection.schedule') }}</h5>
            <label class="config-field" data-setting-id="backup.scheduled.enabled"><span>{{ t('adminSettings.fullBackupSection.scheduledSection.enabled') }}</span><ToggleSwitch v-model="backupConfig.scheduledEnabled" :disabled="adminReadOnly" /></label>
            <label class="config-field" data-setting-id="backup.scheduled.cron"><span>{{ t('adminSettings.fullBackupSection.scheduledSection.cron') }}</span><InputText v-model="backupConfig.scheduledCron" :disabled="adminReadOnly" /></label>
          </div>
          <div class="config-group">
            <h5>{{ t('adminSettings.fullBackupSection.scheduledSection.storage') }}</h5>
            <label class="config-field" data-setting-id="backup.local.path"><span>{{ t('adminSettings.fullBackupSection.scheduledSection.folderPath') }}</span><InputText v-model="backupConfig.localPath" :disabled="adminReadOnly" /></label>
            <div class="config-row">
              <label class="config-field" data-setting-id="backup.retention.count"><span>{{ t('adminSettings.fullBackupSection.scheduledSection.backupsToKeep') }}</span><InputNumber v-model="backupConfig.retentionCount" :min="1" :max="365" :disabled="adminReadOnly" /></label>
              <label class="config-field" data-setting-id="backup.operation.timeout-minutes"><span>{{ t('adminSettings.fullBackupSection.scheduledSection.timeoutMinutes') }}</span><InputNumber v-model="backupConfig.operationTimeoutMinutes" :min="1" :max="1440" :disabled="adminReadOnly" /></label>
            </div>
          </div>
          <div class="config-group">
            <h5>{{ t('adminSettings.fullBackupSection.scheduledSection.healthAlerts') }}</h5>
            <label class="config-field" data-setting-id="backup.health.max-age-days">
              <span>{{ t('adminSettings.fullBackupSection.scheduledSection.warnAfterDays') }}</span>
              <InputNumber v-model="backupConfig.healthMaxAgeDays" :min="0" :max="365" :disabled="adminReadOnly" />
            </label>
            <small class="text-muted">{{ t('adminSettings.fullBackupSection.scheduledSection.healthWarningHint') }}</small>
            <label class="config-field" data-setting-id="backup.health.apprise.enabled">
              <span>{{ t('adminSettings.fullBackupSection.scheduledSection.sendExternalAlert') }}</span>
              <ToggleSwitch v-model="backupConfig.healthAppriseEnabled" :disabled="adminReadOnly || backupConfig.healthMaxAgeDays === 0" />
            </label>
            <template v-if="backupConfig.healthAppriseEnabled">
              <label class="config-field"><span>{{ t('adminSettings.fullBackupSection.scheduledSection.appriseRouting') }}</span><Select v-model="backupConfig.healthAppriseRoutingMode" :options="appriseRoutingOptions" optionLabel="label" optionValue="value" :disabled="adminReadOnly" /></label>
              <label v-if="backupConfig.healthAppriseRoutingMode === 'KEY_TAG'" class="config-field"><span>{{ t('adminSettings.fullBackupSection.scheduledSection.configKey') }}</span><InputText v-model="backupConfig.healthAppriseConfigKey" :disabled="adminReadOnly" /></label>
              <label v-else class="config-field"><span>{{ t('adminSettings.fullBackupSection.scheduledSection.destinationUrls') }}</span><InputText v-model="backupConfig.healthAppriseDestination" :disabled="adminReadOnly" /></label>
              <label v-if="backupConfig.healthAppriseRoutingMode === 'KEY_TAG'" class="config-field"><span>{{ t('adminSettings.fullBackupSection.scheduledSection.tagOptional') }}</span><InputText v-model="backupConfig.healthAppriseTag" :disabled="adminReadOnly" /></label>
            </template>
          </div>
        </div>
        <div class="section-actions"><Button :label="t('adminSettings.fullBackupSection.scheduledSection.saveButton')" icon="pi pi-save" :loading="savingConfig" :disabled="adminReadOnly" @click="saveBackupConfig" /></div>
      </section>

      <section class="backup-subsection">
        <div class="subsection-header local-backups-header">
          <div><h4>{{ t('adminSettings.fullBackupSection.localBackups.title') }}</h4><p class="text-muted">{{ t('adminSettings.fullBackupSection.localBackups.description') }}</p></div>
          <Button icon="pi pi-refresh" text rounded :loading="loadingFiles" :aria-label="t('adminSettings.fullBackupSection.localBackups.refreshAriaLabel')" v-tooltip.top="t('adminSettings.fullBackupSection.localBackups.refreshAriaLabel')" @click="loadBackupFiles" />
        </div>
        <DataTable :value="backupFiles" dataKey="fileName" responsiveLayout="scroll" class="backup-files-table">
          <Column field="fileName" :header="t('adminSettings.fullBackupSection.localBackups.columnFile')" />
          <Column :header="t('adminSettings.fullBackupSection.localBackups.columnSize')"><template #body="{ data }">{{ formatBytes(data.sizeBytes) }}</template></Column>
          <Column :header="t('adminSettings.fullBackupSection.localBackups.columnModified')"><template #body="{ data }">{{ formatDate(data.lastModifiedAt) }}</template></Column>
          <Column :header="t('adminSettings.fullBackupSection.localBackups.columnActions')">
            <template #body="{ data }">
              <div class="file-actions">
                <Button icon="pi pi-download" text rounded :disabled="adminReadOnly || operationRunning" :aria-label="t('adminSettings.fullBackupSection.localBackups.downloadAriaLabel')" v-tooltip.top="t('adminSettings.fullBackupSection.localBackups.downloadAriaLabel')" @click="downloadLocalBackup(data.fileName)" />
                <Button icon="pi pi-undo" text rounded severity="danger" :disabled="adminReadOnly || operationRunning" :aria-label="t('adminSettings.fullBackupSection.localBackups.restoreAriaLabel')" v-tooltip.top="t('adminSettings.fullBackupSection.localBackups.restoreAriaLabel')" @click="openRestoreLocalDialog(data.fileName)" />
                <Button icon="pi pi-trash" text rounded severity="danger" :disabled="adminReadOnly || operationRunning" :aria-label="t('adminSettings.fullBackupSection.localBackups.deleteAriaLabel')" v-tooltip.top="t('adminSettings.fullBackupSection.localBackups.deleteAriaLabel')" @click="openDeleteDialog(data.fileName)" />
              </div>
            </template>
          </Column>
        </DataTable>
      </section>

      <section class="backup-subsection">
        <div class="subsection-header"><div><h4>{{ t('adminSettings.fullBackupSection.restoreUploadSection.title') }}</h4><p class="text-muted">{{ t('adminSettings.fullBackupSection.restoreUploadSection.description') }}</p></div></div>
        <div class="restore-upload-row">
          <div class="inline-upload">
            <FileUpload ref="fullFileUpload" mode="basic" accept=".gpb,application/octet-stream" :chooseLabel="t('adminSettings.fullBackupSection.restoreUploadSection.chooseFile')" :auto="false" :disabled="adminReadOnly || operationRunning" @select="onFullFileSelect" @clear="onFullFileClear" />
            <div v-if="selectedFullFile" class="selected-file"><i class="pi pi-file"></i><span>{{ selectedFullFile.name }}</span></div>
          </div>
          <Button :label="t('adminSettings.fullBackupSection.restoreUploadSection.button')" icon="pi pi-upload" severity="danger" :loading="restoring" :disabled="adminReadOnly || operationRunning || !selectedFullFile" @click="openRestoreUploadDialog" />
        </div>
      </section>
    </div>

    <Dialog v-model:visible="restoreDialogVisible" :header="t('adminSettings.fullBackupSection.restoreDialog.header')" modal :closable="!restoreProgressInDialog" :closeOnEscape="!restoreProgressInDialog" :style="{ width: '34rem' }">
      <div v-if="!restoreProgressInDialog" class="confirm-content">
        <i class="pi pi-exclamation-triangle"></i>
        <div>
          <p>{{ t('adminSettings.fullBackupSection.restoreDialog.warning') }}</p>
          <p class="text-muted">{{ t('adminSettings.fullBackupSection.restoreDialog.note') }}</p>
          <label class="config-field"><span>{{ t('adminSettings.fullBackupSection.restoreDialog.sourcePasswordLabel') }}</span><InputText v-model="restorePassword" type="password" autocomplete="off" maxlength="1024" /></label>
        </div>
      </div>
      <div v-else class="restore-progress">
        <div class="progress-heading"><div><h4>{{ backupProgressTitle }}</h4><span>{{ backupProgressMessage }}</span></div><strong>{{ backupProgressValue }}%</strong></div>
        <ProgressBar :value="backupProgressValue" :showValue="false" />
        <div class="progress-caption"><span>{{ backupStatus?.fileName || '' }}</span></div>
      </div>
      <template #footer>
        <Button v-if="!restoreProgressInDialog" :label="t('common.cancel')" icon="pi pi-times" text @click="restoreDialogVisible = false" />
        <Button v-if="!restoreProgressInDialog" :label="t('adminSettings.fullBackupSection.restoreDialog.restore')" icon="pi pi-undo" severity="danger" :loading="restoring" :disabled="!restorePassword" @click="restoreFullBackup" />
        <Button v-else :label="t('adminSettings.fullBackupSection.restoreDialog.restoring')" icon="pi pi-spin pi-spinner" severity="danger" disabled />
      </template>
    </Dialog>

    <Dialog v-model:visible="deleteDialogVisible" :header="t('adminSettings.fullBackupSection.deleteDialog.header')" modal :style="{ width: '30rem' }">
      <div class="confirm-content"><i class="pi pi-exclamation-triangle"></i><div><p>{{ t('adminSettings.fullBackupSection.deleteDialog.message', { fileName: deleteFileName }) }}</p><p class="text-muted">{{ t('adminSettings.fullBackupSection.deleteDialog.note') }}</p></div></div>
      <template #footer>
        <Button :label="t('common.cancel')" icon="pi pi-times" text @click="deleteDialogVisible = false" />
        <Button :label="t('adminSettings.fullBackupSection.deleteDialog.delete')" icon="pi pi-trash" severity="danger" :loading="deleting" @click="deleteBackup" />
      </template>
    </Dialog>
  </section>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import Button from 'primevue/button'
import Column from 'primevue/column'
import DataTable from 'primevue/datatable'
import Dialog from 'primevue/dialog'
import FileUpload from 'primevue/fileupload'
import InputNumber from 'primevue/inputnumber'
import InputText from 'primevue/inputtext'
import Message from 'primevue/message'
import ProgressBar from 'primevue/progressbar'
import Select from 'primevue/select'
import ToggleSwitch from 'primevue/toggleswitch'
import { useToast } from 'primevue/usetoast'
import { useAdminStore } from '@/stores/admin'
import { applyMaintenanceStatus, refreshMaintenance } from '@/stores/maintenance'
import { showDemoReadOnlyToast } from '@/utils/demoMode'
import { formatApiErrorDetail } from '@/utils/apiErrorDetail'
import { formatMessageDescriptor } from '@/utils/messageDescriptor'

const { t } = useI18n()
const adminService = useAdminStore()
const props = defineProps({ adminReadOnly: { type: Boolean, default: false } })
const toast = useToast()
const backupPassword = ref('')
const backupPasswordConfirmation = ref('')
const restorePassword = ref('')
const fullFileUpload = ref(null)
const fullDownloading = ref(false)
const runningNow = ref(false)
const restoring = ref(false)
const savingConfig = ref(false)
const loadingFiles = ref(false)
const backupFiles = ref([])
const selectedFullFile = ref(null)
const restoreDialogVisible = ref(false)
const restoreSource = ref(null)
const restoreLocalFileName = ref('')
const deleteDialogVisible = ref(false)
const deleteFileName = ref('')
const deleting = ref(false)
const backupStatus = ref(null)
const statusPoller = ref(null)
const restoreStatusPending = ref(false)
const backupConfig = ref({ scheduledEnabled: false, scheduledCron: '0 0 3 * * ?', localPath: '/data/geopulse-backups', retentionCount: 7, operationTimeoutMinutes: 120, healthMaxAgeDays: 2, healthAppriseEnabled: false, healthAppriseRoutingMode: 'URLS', healthAppriseDestination: '', healthAppriseConfigKey: '', healthAppriseTag: '' })
const appriseRoutingOptions = computed(() => [
  { label: t('adminSettings.fullBackupSection.scheduledSection.appriseRoutingOptions.destinationUrls'), value: 'URLS' },
  { label: t('adminSettings.fullBackupSection.scheduledSection.appriseRoutingOptions.configKeyAndTag'), value: 'KEY_TAG' }
])

const RESTORE_TERMINAL_STATES = new Set(['PREPARATION_FAILED', 'ACTIVATION_RETRYABLE', 'ACTIVATION_FAILED', 'COMPLETED', 'DISCARDED'])
const operationRunning = computed(() => backupStatus.value?.backupRunning || backupStatus.value?.restoreRunning || restoring.value || runningNow.value || fullDownloading.value)
const restoreProgressInDialog = computed(() => restoreDialogVisible.value && (restoring.value || backupStatus.value?.restoreRunning))
const restoreStarting = computed(() => restoring.value && restoreStatusPending.value)
const restorePreparationFailed = computed(() => backupStatus.value?.state === 'PREPARATION_FAILED')
const restoreTerminal = computed(() => RESTORE_TERMINAL_STATES.has(backupStatus.value?.state))
const restoreFailureMessage = computed(() => backupStatus.value?.error || formatMessageDescriptor(backupStatus.value?.message) || t('adminSettings.fullBackupSection.restorePreparationFailedFallback'))
const showBackupProgress = computed(() => backupStatus.value?.backupRunning || (backupStatus.value?.restoreRunning && !restoreDialogVisible.value) || restorePreparationFailed.value || ['completed', 'failed'].includes(backupStatus.value?.status))
const backupProgressValue = computed(() => Math.max(0, Math.min(100, backupStatus.value?.progressPercent ?? 0)))
const backupProgressTitle = computed(() => {
  if (restoreStarting.value || backupStatus.value?.restoreRunning) return t('adminSettings.fullBackupSection.progress.restoreInProgress')
  if (restorePreparationFailed.value) return t('adminSettings.fullBackupSection.progress.restorePreparationFailed')
  if (backupStatus.value?.backupRunning) return t('adminSettings.fullBackupSection.progress.backupInProgress')
  if (backupStatus.value?.status === 'completed') return t('adminSettings.fullBackupSection.progress.lastBackupCompleted')
  if (backupStatus.value?.status === 'failed') return t('adminSettings.fullBackupSection.progress.lastBackupFailed')
  return t('adminSettings.fullBackupSection.progress.backupStatus')
})
const backupProgressMessage = computed(() => {
  if (restoreStarting.value) return t('adminSettings.fullBackupSection.progress.restorePreparingMessage')
  return backupStatus.value?.error || formatMessageDescriptor(backupStatus.value?.message) || backupStatus.value?.phase || t('adminSettings.fullBackupSection.progress.waitingForStatus')
})

const onFullFileSelect = (event) => { selectedFullFile.value = event.files?.[0] || null }
const onFullFileClear = () => { selectedFullFile.value = null }
const showError = (summary, error, fallback) => toast.add({ severity: 'error', summary, detail: formatApiErrorDetail(error, fallback), life: 5000 })

const loadBackupStatus = async () => {
  try {
    backupStatus.value = await adminService.getBackupStatus()
    if (restoreTerminal.value) {
      restoreStatusPending.value = false
      await refreshMaintenance()
    }
    const localOperationRunning = restoring.value || runningNow.value || fullDownloading.value
    const remoteOperationRunning = backupStatus.value?.backupRunning || backupStatus.value?.restoreRunning
    if (statusPoller.value && !remoteOperationRunning && !localOperationRunning && (!restoreStatusPending.value || restoreTerminal.value)) stopBackupStatusPolling(false)
  } catch { backupStatus.value = null }
}
const startBackupStatusPolling = () => {
  stopBackupStatusPolling(false)
  loadBackupStatus()
  statusPoller.value = window.setInterval(loadBackupStatus, 1000)
}
const stopBackupStatusPolling = async (refresh = true) => {
  if (statusPoller.value) window.clearInterval(statusPoller.value)
  statusPoller.value = null
  if (refresh) await loadBackupStatus()
}
const loadBackupFiles = async () => {
  loadingFiles.value = true
  try { backupFiles.value = await adminService.getBackupFiles() } catch (error) { showError(t('adminSettings.fullBackupSection.toasts.loadFilesFailed'), error, t('adminSettings.fullBackupSection.toasts.loadFilesFailedFallback')) } finally { loadingFiles.value = false }
}
const loadBackupConfig = async () => {
  try { backupConfig.value = { ...backupConfig.value, ...await adminService.getBackupConfig() } } catch (error) { showError(t('adminSettings.fullBackupSection.toasts.loadFilesFailed'), error, t('adminSettings.fullBackupSection.toasts.loadConfigFailedFallback')) }
}

const downloadFullBackup = async () => {
  if (props.adminReadOnly) return showDemoReadOnlyToast(toast)
  fullDownloading.value = true
  startBackupStatusPolling()
  try {
    await adminService.downloadFullBackup()
    toast.add({ severity: 'success', summary: t('adminSettings.fullBackupSection.toasts.downloadStarted'), detail: t('adminSettings.fullBackupSection.toasts.downloadStartedDetail'), life: 3000 })
  } catch (error) { showError(t('adminSettings.fullBackupSection.toasts.downloadFailed'), error, t('adminSettings.fullBackupSection.toasts.downloadFailedFallback')) } finally { fullDownloading.value = false; await stopBackupStatusPolling() }
}
const runBackupNow = async () => {
  if (props.adminReadOnly) return showDemoReadOnlyToast(toast)
  runningNow.value = true
  startBackupStatusPolling()
  try {
    const result = await adminService.runFullBackupNow()
    toast.add({ severity: 'success', summary: t('adminSettings.fullBackupSection.toasts.backupComplete'), detail: t('adminSettings.fullBackupSection.toasts.backupCompleteDetail', { fileName: result.fileName }), life: 5000 })
    await loadBackupFiles()
  } catch (error) { showError(t('adminSettings.fullBackupSection.toasts.backupFailed'), error, t('adminSettings.fullBackupSection.toasts.backupFailedFallback')) } finally { runningNow.value = false; await stopBackupStatusPolling() }
}
const saveBackupConfig = async () => {
  if (props.adminReadOnly) return showDemoReadOnlyToast(toast)
  const newPasswordLength = backupPassword.value.length
  if (backupPassword.value !== backupPasswordConfirmation.value) return showError(t('adminSettings.fullBackupSection.toasts.saveFailed'), new Error(t('adminSettings.fullBackupSection.toasts.passwordsDoNotMatch')), '')
  if (newPasswordLength > 0 && (newPasswordLength < 12 || newPasswordLength > 1024)) return showError(t('adminSettings.fullBackupSection.toasts.saveFailed'), new Error(t('adminSettings.fullBackupSection.toasts.passwordLengthError')), '')
  savingConfig.value = true
  try {
    backupConfig.value = await adminService.updateBackupConfig({ ...backupConfig.value, password: backupPassword.value || undefined })
    backupPassword.value = ''; backupPasswordConfirmation.value = ''
    toast.add({ severity: 'success', summary: t('adminSettings.fullBackupSection.toasts.settingsSaved'), detail: t('adminSettings.fullBackupSection.toasts.settingsSavedDetail'), life: 3000 })
    await loadBackupFiles()
  } catch (error) { showError(t('adminSettings.fullBackupSection.toasts.saveFailed'), error, t('adminSettings.fullBackupSection.toasts.saveConfigFailedFallback')) } finally { savingConfig.value = false }
}
const downloadLocalBackup = async (fileName) => {
  try { await adminService.downloadLocalBackup(fileName) } catch (error) { showError(t('adminSettings.fullBackupSection.toasts.downloadFailed'), error, t('adminSettings.fullBackupSection.toasts.downloadLocalFailedFallback')) }
}
const openDeleteDialog = (fileName) => {
  if (props.adminReadOnly) return showDemoReadOnlyToast(toast)
  deleteFileName.value = fileName; deleteDialogVisible.value = true
}
const deleteBackup = async () => {
  if (!deleteFileName.value) return
  deleting.value = true
  try {
    await adminService.deleteLocalBackup(deleteFileName.value)
    toast.add({ severity: 'success', summary: t('adminSettings.fullBackupSection.toasts.backupDeleted'), detail: t('adminSettings.fullBackupSection.toasts.backupDeletedDetail', { fileName: deleteFileName.value }), life: 4000 })
    deleteDialogVisible.value = false; deleteFileName.value = ''; await loadBackupFiles()
  } catch (error) { showError(t('adminSettings.fullBackupSection.toasts.deleteFailed'), error, t('adminSettings.fullBackupSection.toasts.deleteFailedFallback')) } finally { deleting.value = false }
}
const openRestoreLocalDialog = (fileName) => {
  if (props.adminReadOnly) return showDemoReadOnlyToast(toast)
  restoreSource.value = 'local'; restoreLocalFileName.value = fileName; restoreDialogVisible.value = true
}
const openRestoreUploadDialog = () => {
  if (props.adminReadOnly) return showDemoReadOnlyToast(toast)
  restoreSource.value = 'upload'; restoreDialogVisible.value = true
}
const restoreFullBackup = async () => {
  if (!restorePassword.value || restorePassword.value.length > 1024) return showError(t('adminSettings.fullBackupSection.toasts.restoreFailed'), new Error(t('adminSettings.fullBackupSection.toasts.restorePasswordLengthError')), '')
  restoring.value = true
  restoreStatusPending.value = true
  backupStatus.value = {
    state: 'PREPARING',
    status: 'preparing',
    operation: 'restore',
    restoreRunning: true,
    progressPercent: 0,
    fileName: restoreSource.value === 'local' ? restoreLocalFileName.value : selectedFullFile.value?.name,
    message: t('adminSettings.fullBackupSection.progress.restorePreparingMessage')
  }
  startBackupStatusPolling()
  try {
    await (restoreSource.value === 'local'
      ? adminService.restoreLocalFullBackup(restoreLocalFileName.value, restorePassword.value)
      : adminService.restoreUploadedFullBackup(selectedFullFile.value, restorePassword.value))
    restoreDialogVisible.value = false; selectedFullFile.value = null; fullFileUpload.value?.clear?.(); restorePassword.value = ''
    applyMaintenanceStatus({ state: 'PREPARING', blocked: false, warning: true, message: t('admin.restoreMaintenanceScreen.restorationPreparingMessage') })
    await refreshMaintenance()
  } catch (error) {
    restoreDialogVisible.value = false
    restoreStatusPending.value = false
    await stopBackupStatusPolling()
    showError(t('adminSettings.fullBackupSection.toasts.restoreFailed'), error, t('adminSettings.fullBackupSection.toasts.restoreFailedFallback'))
  } finally { restoring.value = false }
}
const retryActivation = async () => {
  restoring.value = true
  try { await adminService.retryPreparedRestore(); await refreshMaintenance() } catch (error) { showError(t('adminSettings.fullBackupSection.toasts.activationRetryFailed'), error, t('adminSettings.fullBackupSection.toasts.activationRetryFailedFallback')) } finally { restoring.value = false }
}
const discardPreparedRestore = async () => {
  deleting.value = true
  try { await adminService.discardPreparedRestore(); await Promise.all([loadBackupStatus(), refreshMaintenance()]) } catch (error) { showError(t('adminSettings.fullBackupSection.toasts.discardFailed'), error, t('adminSettings.fullBackupSection.toasts.discardFailedFallback')) } finally { deleting.value = false }
}
const formatBytes = (bytes = 0) => {
  if (!bytes) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB']; const index = Math.min(Math.floor(Math.log(bytes) / Math.log(1024)), units.length - 1)
  return `${(bytes / Math.pow(1024, index)).toFixed(index === 0 ? 0 : 1)} ${units[index]}`
}
const formatDate = (value) => value ? new Date(value).toLocaleString() : ''

onMounted(() => { loadBackupConfig(); loadBackupFiles(); loadBackupStatus() })
onUnmounted(() => { stopBackupStatusPolling(false) })
</script>

<style scoped>
.backup-section { border: 1px solid color-mix(in srgb, var(--gp-primary) 26%, var(--gp-border-medium)); border-left: 4px solid var(--gp-primary); border-radius: 8px; overflow: hidden; background: var(--gp-surface-card); box-shadow: var(--gp-shadow-card); }
.backup-section-header { padding: 1.25rem 1.45rem; border-bottom: 1px solid color-mix(in srgb, var(--gp-primary) 26%, var(--gp-border-medium)); background: color-mix(in srgb, var(--gp-primary) 7%, var(--gp-surface-card)); }
.backup-section-header h3 { margin: 0 0 0.35rem; color: var(--gp-primary); }
.backup-section-header p, .subsection-header p, .backup-panel p { margin: 0; line-height: 1.5; }
.backup-section-body { display: grid; gap: 1.5rem; padding: 1.45rem; }
.backup-grid, .config-grid, .config-row { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 1rem; }
.backup-panel, .backup-subsection, .config-group { border: 1px solid var(--gp-border-medium); border-radius: 8px; padding: 1.15rem; background: var(--gp-surface-muted); }
.backup-panel { display: flex; flex-direction: column; align-items: flex-start; gap: 1rem; }
.backup-panel-header, .subsection-header, .progress-heading, .progress-caption { display: flex; justify-content: space-between; gap: 1rem; width: 100%; }
.backup-panel h4, .subsection-header h4, .progress-heading h4 { margin: 0 0 0.35rem; color: var(--gp-text-primary); }
.backup-panel-icon { color: var(--gp-primary); font-size: 1.25rem; }
.backup-progress { padding: 1.1rem; border: 1px solid color-mix(in srgb, var(--gp-primary) 26%, var(--gp-border-medium)); border-radius: 8px; display: grid; gap: 0.75rem; background: color-mix(in srgb, var(--gp-primary) 8%, var(--gp-surface-card)); }
.progress-heading span, .progress-caption { color: var(--gp-text-secondary); font-size: 0.9rem; }
.restore-progress, .backup-subsection, .config-group, .retryable-restore, .restore-failure { display: grid; gap: 1rem; }
.restore-failure h4 { margin: 0 0 0.5rem; color: var(--gp-text-primary); }
.restore-failure p { margin: 0 0 0.45rem; line-height: 1.5; overflow-wrap: anywhere; }
.restore-failure p:last-child { margin-bottom: 0; }
.config-group { padding: 1rem; }
.config-group h5 { margin: 0; }
.config-field { display: flex; flex-direction: column; gap: 0.35rem; color: var(--gp-text-secondary); font-size: 0.9rem; min-width: 0; }
.config-field :deep(.p-inputtext), .config-field :deep(.p-inputnumber), .config-field :deep(.p-inputnumber-input) { width: 100%; }
.section-actions { display: flex; justify-content: flex-end; }
.restore-upload-row { display: grid; grid-template-columns: minmax(0, 1fr) auto; align-items: center; gap: 1rem; }
.inline-upload { display: flex; align-items: center; flex-wrap: wrap; gap: 0.75rem; width: 100%; }
.selected-file { display: inline-flex; align-items: center; gap: 0.5rem; max-width: 100%; color: var(--gp-text-secondary); font-size: 0.9rem; }
.selected-file span { overflow-wrap: anywhere; }
.file-actions { display: flex; gap: 0.25rem; }
.confirm-content { display: flex; gap: 1rem; align-items: flex-start; }
.confirm-content > i { color: var(--p-red-500); font-size: 1.5rem; }
.confirm-content p { margin: 0 0 0.75rem; line-height: 1.5; }

@media (max-width: 768px) {
  .backup-grid, .config-grid, .config-row, .restore-upload-row { grid-template-columns: 1fr; }
  .backup-section-header, .backup-section-body { padding: 1rem; }
  .backup-section,
  .backup-section-header > div,
  .backup-section-body,
  .backup-panel,
  .backup-panel-header > div,
  .backup-subsection,
  .subsection-header > div,
  .backup-progress,
  .config-group,
  .restore-upload-row,
  .inline-upload,
  .retryable-restore,
  .restore-failure { min-width: 0; }
  .backup-panel,
  .backup-subsection,
  .config-group,
  .backup-progress { padding: 0.85rem; }
  .backup-panel-header,
  .subsection-header { gap: 0.65rem; }
  .backup-panel-icon { flex: 0 0 auto; }
  .backup-section-header h3,
  .backup-panel h4,
  .subsection-header h4,
  .progress-heading h4,
  .backup-section-header p,
  .backup-panel p,
  .subsection-header p,
  .progress-heading span,
  .progress-caption,
  .config-field,
  .selected-file span { overflow-wrap: anywhere; }
  .progress-heading, .progress-caption { flex-direction: column; gap: 0.35rem; }
  .section-actions { justify-content: stretch; }
  .section-actions :deep(.p-button),
  .restore-upload-row > :deep(.p-button),
  .backup-panel > :deep(.p-button),
  .file-actions :deep(.p-button) { max-width: 100%; }
  .section-actions :deep(.p-button),
  .restore-upload-row > :deep(.p-button) { width: 100%; }
  .inline-upload :deep(.p-fileupload-choose) {
    max-width: 100%;
    white-space: normal;
  }
  .full-backup-section :deep(.p-message),
  .full-backup-section :deep(.p-message-text),
  .full-backup-section :deep(.p-message-detail) {
    min-width: 0;
    overflow-wrap: anywhere;
  }
  .backup-files-table :deep(.p-datatable-table) {
    width: 100%;
    min-width: 0;
    table-layout: fixed;
  }
  .backup-files-table :deep(th),
  .backup-files-table :deep(td) {
    padding: 0.65rem 0.45rem;
    white-space: normal;
    overflow-wrap: anywhere;
    vertical-align: top;
  }
  .backup-files-table :deep(th:nth-child(1)),
  .backup-files-table :deep(td:nth-child(1)) { width: 34%; }
  .backup-files-table :deep(th:nth-child(2)),
  .backup-files-table :deep(td:nth-child(2)) { width: 16%; }
  .backup-files-table :deep(th:nth-child(3)),
  .backup-files-table :deep(td:nth-child(3)) { width: 25%; }
  .backup-files-table :deep(th:nth-child(4)),
  .backup-files-table :deep(td:nth-child(4)) { width: 25%; }
  .file-actions {
    gap: 0;
    justify-content: flex-start;
  }
  .file-actions :deep(.p-button.p-button-icon-only) {
    width: 1.85rem;
    min-width: 1.85rem;
    height: 1.85rem;
    padding: 0;
  }
}
</style>

<style>
</style>
