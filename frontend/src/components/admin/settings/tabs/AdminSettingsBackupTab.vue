<template>
  <div>
    <div class="settings-section backup-page">
      <section class="backup-section">
        <div class="backup-section-header">
          <div>
            <h3>Admin Settings Export</h3>
            <p class="text-muted">Portable admin-managed settings and provider configuration.</p>
          </div>
        </div>

        <div class="backup-section-body">
          <div class="backup-grid">
            <section class="backup-panel">
              <div class="backup-panel-header">
                <div>
                  <h4>Export Settings</h4>
                  <p class="text-muted">Download a JSON backup with portable admin-managed behavior and provider credentials.</p>
                </div>
                <i class="pi pi-download backup-panel-icon"></i>
              </div>
              <Button label="Export Settings" icon="pi pi-download" :loading="exporting" :disabled="adminReadOnly" @click="exportBackup" />
            </section>

            <section class="backup-panel">
              <div class="backup-panel-header">
                <div>
                  <h4>Import Settings</h4>
                  <p class="text-muted">Restore a JSON backup and replace current admin-managed settings.</p>
                </div>
                <i class="pi pi-upload backup-panel-icon"></i>
              </div>
              <div class="inline-upload">
                <FileUpload ref="fileUpload" mode="basic" accept=".json,application/json" chooseLabel="Choose Backup File" :auto="false" :disabled="adminReadOnly || importing" @select="onFileSelect" @clear="onFileClear" />
                <div v-if="selectedFile" class="selected-file"><i class="pi pi-file"></i><span>{{ selectedFile.name }}</span></div>
              </div>
              <Button label="Import Settings" icon="pi pi-upload" severity="danger" :loading="importing" :disabled="adminReadOnly || !selectedFile" @click="openImportDialog" />
            </section>
          </div>

          <div class="message-stack">
            <Message severity="warn" :closable="false">
              The settings export includes plaintext API keys, OIDC client secrets, tokens, and custom provider headers.
            </Message>
            <Message severity="info" :closable="false">
              Settings export scope is admin-managed app behavior plus OIDC/custom provider configs.
            </Message>
          </div>
        </div>
      </section>

      <section class="backup-section">
        <div class="backup-section-header">
          <div>
            <h3>Full App Backup</h3>
            <p class="text-muted">Password-encrypted PostgreSQL backup of application data and secrets.</p>
          </div>
        </div>

        <div class="backup-section-body">
          <div class="backup-grid">
            <section class="backup-panel">
              <div class="backup-panel-header">
                <div>
                  <h4>Download Full Backup</h4>
                  <p class="text-muted">Generate a full backup and download it in this browser.</p>
                </div>
                <i class="pi pi-download backup-panel-icon"></i>
              </div>
              <Button label="Download Full Backup" icon="pi pi-download" :loading="fullDownloading" :disabled="adminReadOnly || operationRunning || !backupConfig.passwordConfigured" @click="downloadFullBackup" />
            </section>

            <section class="backup-panel">
              <div class="backup-panel-header">
                <div>
                  <h4>Run Backup Now</h4>
                  <p class="text-muted">Create a backup file in the mounted local folder.</p>
                </div>
                <i class="pi pi-save backup-panel-icon"></i>
              </div>
              <Button label="Run Backup Now" icon="pi pi-play" :loading="runningNow" :disabled="adminReadOnly || operationRunning || !backupConfig.passwordConfigured" @click="runBackupNow" />
            </section>
          </div>

          <Message severity="warn" :closable="false" class="backup-warning">
            Full backups are encrypted with your backup password. Save that password outside GeoPulse: without it, the backup cannot be recovered. Restore only trusted backups. Restore preparation runs online; activation briefly stops GeoPulse and restarts the backend automatically.
          </Message>

          <Message v-if="backupStatus?.state === 'ACTIVATION_RETRYABLE'" severity="warn" :closable="false">
            <div class="retryable-restore">
              <span>{{ backupStatus.error || 'Activation did not complete. The original database is active and the prepared restore is retained.' }}</span>
              <div class="file-actions">
                <Button label="Retry Activation" icon="pi pi-refresh" :loading="restoring" @click="retryActivation" />
                <Button label="Discard Prepared Restore" icon="pi pi-trash" severity="danger" outlined :loading="deleting" @click="discardPreparedRestore" />
              </div>
            </div>
          </Message>

          <div v-if="showBackupProgress" class="backup-progress">
            <div class="progress-heading">
              <div>
                <h4>{{ backupProgressTitle }}</h4>
                <span>{{ backupProgressMessage }}</span>
              </div>
              <strong>{{ backupProgressValue }}%</strong>
            </div>
            <ProgressBar :value="backupProgressValue" :showValue="false" />
            <div class="progress-caption">
              <span>{{ backupProgressUsers }}</span>
              <span v-if="backupStatus?.currentUserEmail">{{ backupStatus.currentUserEmail }}</span>
            </div>
          </div>

          <section class="backup-subsection">
            <div class="subsection-header">
              <div>
                <h4>Scheduled Local Backups</h4>
                <p class="text-muted">Configure automatic backups written to the mounted backup folder.</p>
              </div>
            </div>
            <div class="config-grid">
              <div class="config-group schedule-group">
                <h5>Backup password</h5>
                <label class="config-field"><span>{{ backupConfig.passwordConfigured ? 'Change password (leave empty to keep)' : 'Password required for all full backups' }}</span><InputText v-model="backupPassword" type="password" autocomplete="new-password" :disabled="adminReadOnly" /></label>
                <label class="config-field"><span>Confirm new password</span><InputText v-model="backupPasswordConfirmation" type="password" autocomplete="new-password" :disabled="adminReadOnly" /></label>
                <h5>Schedule</h5>
                <label class="config-field toggle-field" data-setting-id="backup.scheduled.enabled"><span>Enabled</span><ToggleSwitch v-model="backupConfig.scheduledEnabled" :disabled="adminReadOnly" /></label>
                <label class="config-field" data-setting-id="backup.scheduled.cron"><span>Cron</span><InputText v-model="backupConfig.scheduledCron" :disabled="adminReadOnly" /></label>
              </div>
              <div class="config-group">
                <h5>Storage</h5>
                <label class="config-field" data-setting-id="backup.local.path"><span>Folder Path</span><InputText v-model="backupConfig.localPath" :disabled="adminReadOnly" /></label>
                <div class="config-row">
                  <label class="config-field" data-setting-id="backup.retention.count"><span>Backups to Keep</span><InputNumber v-model="backupConfig.retentionCount" :min="1" :max="365" :disabled="adminReadOnly" /></label>
                  <label class="config-field" data-setting-id="backup.operation.timeout-minutes"><span>Timeout Minutes</span><InputNumber v-model="backupConfig.operationTimeoutMinutes" :min="1" :max="1440" :disabled="adminReadOnly" /></label>
                </div>
              </div>
            </div>
            <div class="section-actions">
              <Button label="Save Backup Settings" icon="pi pi-save" :loading="savingConfig" :disabled="adminReadOnly" @click="saveBackupConfig" />
            </div>
          </section>

          <section class="backup-subsection">
            <div class="subsection-header local-backups-header">
              <div>
                <h4>Local Backups</h4>
                <p class="text-muted">Files available in the configured server-side backup folder.</p>
              </div>
              <Button icon="pi pi-refresh" text rounded :loading="loadingFiles" aria-label="Refresh local backups" v-tooltip.top="'Refresh local backups'" @click="loadBackupFiles" />
            </div>
            <DataTable :value="backupFiles" dataKey="fileName" responsiveLayout="scroll" class="backup-files-table">
              <Column field="fileName" header="File" />
              <Column header="Size"><template #body="{ data }">{{ formatBytes(data.sizeBytes) }}</template></Column>
              <Column header="Modified"><template #body="{ data }">{{ formatDate(data.lastModifiedAt) }}</template></Column>
              <Column header="Actions">
                <template #body="{ data }">
                  <div class="file-actions">
                    <Button icon="pi pi-download" text rounded :disabled="adminReadOnly || operationRunning" aria-label="Download backup" v-tooltip.top="'Download backup'" @click="downloadLocalBackup(data.fileName)" />
                    <Button icon="pi pi-undo" text rounded severity="danger" :disabled="adminReadOnly || operationRunning" aria-label="Restore backup" v-tooltip.top="'Restore backup'" @click="openRestoreLocalDialog(data.fileName)" />
                    <Button icon="pi pi-trash" text rounded severity="danger" :disabled="adminReadOnly || operationRunning" aria-label="Delete backup" v-tooltip.top="'Delete backup'" @click="openDeleteDialog(data.fileName)" />
                  </div>
                </template>
              </Column>
            </DataTable>
          </section>

          <section class="backup-subsection restore-subsection">
            <div class="subsection-header">
              <div>
                <h4>Restore Uploaded Full Backup</h4>
                <p class="text-muted">Upload an encrypted .gpb backup. Preparation runs in the background while GeoPulse stays available; activation then replaces newer data and restarts the backend.</p>
              </div>
            </div>
            <div class="restore-upload-row">
              <div class="inline-upload">
                <FileUpload ref="fullFileUpload" mode="basic" accept=".gpb,application/octet-stream" chooseLabel="Choose Full Backup" :auto="false" :disabled="adminReadOnly || operationRunning" @select="onFullFileSelect" @clear="onFullFileClear" />
                <div v-if="selectedFullFile" class="selected-file"><i class="pi pi-file"></i><span>{{ selectedFullFile.name }}</span></div>
              </div>
              <Button label="Restore Uploaded Backup" icon="pi pi-upload" severity="danger" :loading="restoring" :disabled="adminReadOnly || operationRunning || !selectedFullFile" @click="openRestoreUploadDialog" />
            </div>
          </section>
        </div>
      </section>
    </div>

    <Dialog v-model:visible="importDialogVisible" header="Replace Admin Settings?" modal :style="{ width: '32rem' }">
      <div class="confirm-content">
        <i class="pi pi-exclamation-triangle"></i>
        <div>
          <p>Importing this file will replace current global settings, OIDC providers, and custom geocoding providers.</p>
          <p class="text-muted">Deployment infrastructure and user data exports are not affected.</p>
        </div>
      </div>
      <template #footer>
        <Button label="Cancel" icon="pi pi-times" text @click="importDialogVisible = false" />
        <Button label="Import" icon="pi pi-upload" severity="danger" :loading="importing" @click="importBackup" />
      </template>
    </Dialog>

    <Dialog
      v-model:visible="restoreDialogVisible"
      header="Restore Full Backup?"
      modal
      :closable="!restoreProgressInDialog"
      :closeOnEscape="!restoreProgressInDialog"
      :style="{ width: '34rem' }"
    >
      <div v-if="!restoreProgressInDialog" class="confirm-content">
        <i class="pi pi-exclamation-triangle"></i>
        <div>
          <p>Restoring a full backup can replace users, app settings, GPS data, friends, and permissions.</p>
          <p class="text-muted">Preparation runs while GeoPulse remains available. Activation briefly blocks application work, replaces newer data, and restarts the backend automatically.</p>
          <label class="config-field"><span>Source backup password</span><InputText v-model="restorePassword" type="password" autocomplete="off" /></label>
        </div>
      </div>
      <div v-else class="restore-progress">
        <div class="progress-heading">
          <div>
            <h4>{{ backupProgressTitle }}</h4>
            <span>{{ backupProgressMessage }}</span>
          </div>
          <strong>{{ backupProgressValue }}%</strong>
        </div>
        <ProgressBar :value="backupProgressValue" :showValue="false" />
        <div class="progress-caption">
          <span>{{ backupProgressUsers }}</span>
          <span v-if="backupStatus?.currentUserEmail">{{ backupStatus.currentUserEmail }}</span>
        </div>
      </div>
      <template #footer>
        <Button v-if="!restoreProgressInDialog" label="Cancel" icon="pi pi-times" text @click="restoreDialogVisible = false" />
        <Button v-if="!restoreProgressInDialog" label="Restore" icon="pi pi-undo" severity="danger" :loading="restoring" :disabled="!restorePassword" @click="restoreFullBackup" />
        <Button v-else label="Restoring" icon="pi pi-spin pi-spinner" severity="danger" disabled />
      </template>
    </Dialog>

    <Dialog v-model:visible="deleteDialogVisible" header="Delete Backup?" modal :style="{ width: '30rem' }">
      <div class="confirm-content">
        <i class="pi pi-exclamation-triangle"></i>
        <div>
          <p>Delete {{ deleteFileName }} from the configured local backup folder?</p>
          <p class="text-muted">This only removes the server-side backup file.</p>
        </div>
      </div>
      <template #footer>
        <Button label="Cancel" icon="pi pi-times" text @click="deleteDialogVisible = false" />
        <Button label="Delete" icon="pi pi-trash" severity="danger" :loading="deleting" @click="deleteBackup" />
      </template>
    </Dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { storeToRefs } from 'pinia'
import Button from 'primevue/button'
import Column from 'primevue/column'
import DataTable from 'primevue/datatable'
import Dialog from 'primevue/dialog'
import FileUpload from 'primevue/fileupload'
import InputNumber from 'primevue/inputnumber'
import InputText from 'primevue/inputtext'
import Message from 'primevue/message'
import ProgressBar from 'primevue/progressbar'
import ToggleSwitch from 'primevue/toggleswitch'
import { useToast } from 'primevue/usetoast'
import { useAuthStore } from '@/stores/auth'
import adminService from '@/utils/adminService'
import { applyMaintenanceStatus, refreshMaintenance } from '@/stores/maintenance'
import { showDemoReadOnlyToast } from '@/utils/demoMode'

const backupPassword = ref('')
const backupPasswordConfirmation = ref('')
const restorePassword = ref('')
const toast = useToast()
const authStore = useAuthStore()
const { adminReadOnly } = storeToRefs(authStore)

const exporting = ref(false)
const importing = ref(false)
const selectedFile = ref(null)
const importDialogVisible = ref(false)
const fileUpload = ref(null)
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
const backupConfig = ref({
  scheduledEnabled: false,
  scheduledCron: '0 0 3 * * ?',
  localPath: '/data/geopulse-backups',
  retentionCount: 7,
  operationTimeoutMinutes: 120
})

const operationRunning = computed(() =>
  backupStatus.value?.backupRunning || backupStatus.value?.restoreRunning || restoring.value || runningNow.value || fullDownloading.value
)
const restoreProgressInDialog = computed(() => restoreDialogVisible.value && (restoring.value || backupStatus.value?.restoreRunning))
const showBackupProgress = computed(() =>
  backupStatus.value?.backupRunning ||
  (backupStatus.value?.restoreRunning && !restoreDialogVisible.value) ||
  ['completed', 'failed'].includes(backupStatus.value?.status)
)
const backupProgressValue = computed(() => Math.max(0, Math.min(100, backupStatus.value?.progressPercent ?? 0)))
const backupProgressTitle = computed(() => {
  if (backupStatus.value?.restoreRunning) return 'Restore in progress'
  if (backupStatus.value?.backupRunning) return 'Backup in progress'
  if (backupStatus.value?.status === 'completed') return 'Last backup operation completed'
  if (backupStatus.value?.status === 'failed') return 'Last backup operation failed'
  return 'Backup status'
})
const backupProgressMessage = computed(() => backupStatus.value?.error || backupStatus.value?.message || backupStatus.value?.phase || 'Waiting for status')
const backupProgressUsers = computed(() => {
  const processed = backupStatus.value?.processedUsers
  const total = backupStatus.value?.totalUsers
  if (Number.isInteger(processed) && Number.isInteger(total)) {
    return `${processed} of ${total} users processed`
  }
  return backupStatus.value?.fileName || ''
})

const onFileSelect = (event) => { selectedFile.value = event.files?.[0] || null }
const onFileClear = () => { selectedFile.value = null }
const onFullFileSelect = (event) => { selectedFullFile.value = event.files?.[0] || null }
const onFullFileClear = () => { selectedFullFile.value = null }

const exportBackup = async () => {
  if (adminReadOnly.value) return showDemoReadOnlyToast(toast)
  exporting.value = true
  try {
    await adminService.exportAdminSettingsBackup()
    toast.add({ severity: 'success', summary: 'Export Started', detail: 'Admin settings backup download has started.', life: 3000 })
  } catch (error) {
    toast.add({ severity: 'error', summary: 'Export Failed', detail: error.message || 'Failed to export admin settings backup', life: 4000 })
  } finally {
    exporting.value = false
  }
}

const openImportDialog = () => {
  if (adminReadOnly.value) return showDemoReadOnlyToast(toast)
  importDialogVisible.value = true
}

const importBackup = async () => {
  if (!selectedFile.value) return
  importing.value = true
  try {
    const result = await adminService.importAdminSettingsBackup(selectedFile.value)
    importDialogVisible.value = false
    selectedFile.value = null
    fileUpload.value?.clear?.()
    toast.add({ severity: 'success', summary: 'Import Complete', detail: buildImportSummary(result), life: 5000 })
  } catch (error) {
    toast.add({ severity: 'error', summary: 'Import Failed', detail: error.response?.data?.message || error.message || 'Failed to import admin settings backup', life: 5000 })
  } finally {
    importing.value = false
  }
}

const buildImportSummary = (result = {}) => {
  const settings = result.settingsImported ?? 0
  const oidc = result.oidcProvidersImported ?? 0
  const custom = result.customGeocodingProvidersImported ?? 0
  return `Restored ${settings} settings, ${oidc} OIDC providers, and ${custom} custom geocoding providers.`
}

const downloadFullBackup = async () => {
  if (adminReadOnly.value) return showDemoReadOnlyToast(toast)
  fullDownloading.value = true
  startBackupStatusPolling()
  try {
    await adminService.downloadFullBackup()
    toast.add({ severity: 'success', summary: 'Download Started', detail: 'Full backup download has started.', life: 3000 })
    await loadBackupStatus()
  } catch (error) {
    toast.add({ severity: 'error', summary: 'Download Failed', detail: error.message || 'Failed to download full backup', life: 5000 })
  } finally {
    fullDownloading.value = false
    stopBackupStatusPolling()
  }
}

const runBackupNow = async () => {
  if (adminReadOnly.value) return showDemoReadOnlyToast(toast)
  runningNow.value = true
  startBackupStatusPolling()
  try {
    const result = await adminService.runFullBackupNow()
    toast.add({ severity: 'success', summary: 'Backup Complete', detail: `Created ${result.fileName}`, life: 5000 })
    await Promise.all([loadBackupFiles(), loadBackupStatus()])
  } catch (error) {
    toast.add({ severity: 'error', summary: 'Backup Failed', detail: error.response?.data?.message || error.message || 'Failed to run backup', life: 5000 })
  } finally {
    runningNow.value = false
    stopBackupStatusPolling()
  }
}

const loadBackupFiles = async () => {
  loadingFiles.value = true
  try {
    backupFiles.value = await adminService.getBackupFiles()
  } catch (error) {
    toast.add({ severity: 'error', summary: 'Load Failed', detail: error.message || 'Failed to load local backups', life: 4000 })
  } finally {
    loadingFiles.value = false
  }
}

const loadBackupConfig = async () => {
  try {
    backupConfig.value = await adminService.getBackupConfig()
  } catch (error) {
    toast.add({ severity: 'error', summary: 'Load Failed', detail: error.message || 'Failed to load backup settings', life: 4000 })
  }
}

const loadBackupStatus = async () => {
  try {
    backupStatus.value = await adminService.getBackupStatus()
    if (statusPoller.value && !backupStatus.value?.backupRunning && !backupStatus.value?.restoreRunning
      && !restoring.value && !runningNow.value && !fullDownloading.value) {
      window.clearInterval(statusPoller.value)
      statusPoller.value = null
    }
  } catch {
    backupStatus.value = null
  }
}

const saveBackupConfig = async () => {
  if (adminReadOnly.value) return showDemoReadOnlyToast(toast)
  savingConfig.value = true
  try {
    if (backupPassword.value !== backupPasswordConfirmation.value) throw new Error('Backup passwords do not match')
    backupConfig.value = await adminService.updateBackupConfig({ ...backupConfig.value, password: backupPassword.value || undefined })
    backupPassword.value = ''
    backupPasswordConfirmation.value = ''
    toast.add({ severity: 'success', summary: 'Settings Saved', detail: 'Backup settings updated.', life: 3000 })
    await loadBackupFiles()
  } catch (error) {
    toast.add({ severity: 'error', summary: 'Save Failed', detail: error.response?.data?.message || error.message || 'Failed to save backup settings', life: 5000 })
  } finally {
    savingConfig.value = false
  }
}

const downloadLocalBackup = async (fileName) => {
  try {
    await adminService.downloadLocalBackup(fileName)
  } catch (error) {
    toast.add({ severity: 'error', summary: 'Download Failed', detail: error.message || 'Failed to download local backup', life: 4000 })
  }
}

const openDeleteDialog = (fileName) => {
  if (adminReadOnly.value) return showDemoReadOnlyToast(toast)
  deleteFileName.value = fileName
  deleteDialogVisible.value = true
}

const deleteBackup = async () => {
  if (!deleteFileName.value) return
  deleting.value = true
  try {
    await adminService.deleteLocalBackup(deleteFileName.value)
    toast.add({ severity: 'success', summary: 'Backup Deleted', detail: `Deleted ${deleteFileName.value}`, life: 4000 })
    deleteDialogVisible.value = false
    deleteFileName.value = ''
    await loadBackupFiles()
  } catch (error) {
    toast.add({ severity: 'error', summary: 'Delete Failed', detail: error.response?.data?.message || error.message || 'Failed to delete backup', life: 5000 })
  } finally {
    deleting.value = false
  }
}

const openRestoreLocalDialog = (fileName) => {
  if (adminReadOnly.value) return showDemoReadOnlyToast(toast)
  restoreSource.value = 'local'
  restoreLocalFileName.value = fileName
  restoreDialogVisible.value = true
}

const openRestoreUploadDialog = () => {
  if (adminReadOnly.value) return showDemoReadOnlyToast(toast)
  restoreSource.value = 'upload'
  restoreDialogVisible.value = true
}

const restoreFullBackup = async () => {
  restoring.value = true
  startBackupStatusPolling()
  try {
    const result = restoreSource.value === 'local'
      ? await adminService.restoreLocalFullBackup(restoreLocalFileName.value, restorePassword.value)
      : await adminService.restoreUploadedFullBackup(selectedFullFile.value, restorePassword.value)
    restoreDialogVisible.value = false
    selectedFullFile.value = null
    fullFileUpload.value?.clear?.()
    restorePassword.value = ''
    applyMaintenanceStatus({
      state: 'PREPARING', blocked: false, warning: true,
      message: 'Restoration is being prepared in the background. GeoPulse remains available, but data and changes newer than this backup will be replaced when restoration activates.'
    })
    await refreshMaintenance()
  } catch (error) {
    restoreDialogVisible.value = false
    await stopBackupStatusPolling()
    toast.add({ severity: 'error', summary: 'Restore Failed', detail: error.response?.data?.message || error.message || 'Failed to restore full backup', life: 6000 })
  } finally {
    restoring.value = false
  }
}

const retryActivation = async () => {
  restoring.value = true
  try {
    await adminService.retryPreparedRestore()
    await refreshMaintenance()
  } catch (error) {
    toast.add({ severity: 'error', summary: 'Activation Retry Failed', detail: error.response?.data?.message || error.message, life: 6000 })
  } finally { restoring.value = false }
}

const discardPreparedRestore = async () => {
  deleting.value = true
  try {
    await adminService.discardPreparedRestore()
    await Promise.all([loadBackupStatus(), refreshMaintenance()])
  } catch (error) {
    toast.add({ severity: 'error', summary: 'Discard Failed', detail: error.response?.data?.message || error.message, life: 6000 })
  } finally { deleting.value = false }
}

const startBackupStatusPolling = () => {
  stopBackupStatusPolling()
  loadBackupStatus()
  statusPoller.value = window.setInterval(loadBackupStatus, 1000)
}

const stopBackupStatusPolling = async () => {
  if (statusPoller.value) {
    window.clearInterval(statusPoller.value)
    statusPoller.value = null
  }
  await loadBackupStatus()
}

const formatBytes = (bytes = 0) => {
  if (!bytes) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB']
  const index = Math.min(Math.floor(Math.log(bytes) / Math.log(1024)), units.length - 1)
  return `${(bytes / Math.pow(1024, index)).toFixed(index === 0 ? 0 : 1)} ${units[index]}`
}

const formatDate = (value) => value ? new Date(value).toLocaleString() : ''

onMounted(() => {
  loadBackupConfig()
  loadBackupFiles()
  loadBackupStatus()
})

onUnmounted(() => {
  if (statusPoller.value) {
    window.clearInterval(statusPoller.value)
  }
})
</script>

<style scoped>
@import '../admin-settings-common.css';

.backup-page {
  --backup-section-bg: var(--gp-surface-white);
  --backup-section-border: color-mix(in srgb, var(--gp-primary) 26%, var(--gp-border-medium));
  --backup-section-header-bg: color-mix(in srgb, var(--gp-primary) 7%, var(--gp-surface-white));
  --backup-panel-bg: var(--gp-surface-light);
  --backup-panel-border: var(--gp-border-medium);
  --backup-progress-bg: color-mix(in srgb, var(--gp-primary) 8%, var(--gp-surface-white));
  --backup-subsection-bg: var(--gp-surface-white);
  --backup-config-bg: var(--gp-surface-light);

  display: grid;
  gap: 1.75rem;
  padding: 0 1rem 1.5rem;
}

.backup-section {
  border: 1px solid var(--backup-section-border);
  border-left: 4px solid var(--gp-primary);
  border-radius: 8px;
  overflow: hidden;
  background: var(--backup-section-bg);
  box-shadow: var(--gp-shadow-card);
}

.backup-section-header {
  padding: 1.25rem 1.45rem;
  border-bottom: 1px solid var(--backup-section-border);
  background: var(--backup-section-header-bg);
}

.backup-section-header h3 {
  margin: 0 0 0.35rem 0;
  padding: 0;
  border: 0;
  color: var(--gp-primary);
}

.backup-section-header p,
.subsection-header p {
  margin: 0;
  line-height: 1.45;
}

.backup-section-body {
  display: grid;
  gap: 1.5rem;
  padding: 1.45rem;
}

.backup-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 1rem;
}

.backup-panel {
  border: 1px solid var(--backup-panel-border);
  border-radius: 8px;
  padding: 1.15rem;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 1rem;
  background: var(--backup-panel-bg);
}

.backup-panel-header {
  width: 100%;
  display: flex;
  justify-content: space-between;
  gap: 1rem;
}

.backup-panel h4 {
  margin: 0 0 0.35rem 0;
  font-size: 1rem;
  color: var(--gp-text-primary);
}

.backup-panel p {
  margin: 0;
  line-height: 1.5;
}

.backup-panel-icon {
  color: var(--gp-primary);
  font-size: 1.25rem;
  margin-top: 0.1rem;
}

.selected-file {
  display: inline-flex;
  align-items: center;
  gap: 0.5rem;
  max-width: 100%;
  color: var(--text-color-secondary);
  font-size: 0.9rem;
}

.selected-file span {
  overflow-wrap: anywhere;
}

.inline-upload {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 0.75rem;
  width: 100%;
}

.message-stack {
  display: grid;
  gap: 0.75rem;
  padding-top: 0.1rem;
}

.backup-warning {
  margin: 0;
}

.backup-progress {
  padding: 1.1rem;
  border: 1px solid var(--backup-section-border);
  border-radius: 8px;
  display: grid;
  gap: 0.75rem;
  background: var(--backup-progress-bg);
}

.restore-progress {
  display: grid;
  gap: 0.75rem;
}

.progress-heading,
.progress-caption {
  display: flex;
  justify-content: space-between;
  gap: 1rem;
}

.progress-heading h4 {
  margin: 0 0 0.25rem 0;
}

.progress-heading span,
.progress-caption {
  color: var(--gp-text-secondary);
  font-size: 0.9rem;
}

.backup-subsection {
  display: flex;
  flex-direction: column;
  gap: 1rem;
  padding: 1.15rem;
  border: 1px solid var(--backup-panel-border);
  border-radius: 8px;
  background: var(--backup-subsection-bg);
}

.subsection-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 1rem;
}

.subsection-header h4 {
  margin: 0 0 0.35rem 0;
  color: var(--gp-text-primary);
}

.config-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 1rem;
}

.config-group {
  display: grid;
  align-content: start;
  gap: 0.9rem;
  min-width: 0;
  padding: 1rem;
  border: 1px solid var(--backup-panel-border);
  border-radius: 8px;
  background: var(--backup-config-bg);
}

.config-group h5 {
  margin: 0;
  color: var(--gp-text-primary);
  font-size: 0.95rem;
}

.config-row {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 1rem;
}

.config-field {
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
  color: var(--gp-text-secondary);
  font-size: 0.9rem;
  min-width: 0;
}

.config-field :deep(.p-inputtext),
.config-field :deep(.p-inputnumber),
.config-field :deep(.p-inputnumber-input) {
  width: 100%;
}

.toggle-field {
  align-items: flex-start;
}

.local-backups-header {
  align-items: center;
}

.section-actions {
  display: flex;
  justify-content: flex-end;
}

.restore-upload-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: center;
  gap: 1rem;
}

.file-actions {
  display: flex;
  gap: 0.25rem;
}

.confirm-content {
  display: flex;
  gap: 1rem;
  align-items: flex-start;
}

.confirm-content > i {
  color: var(--p-red-500);
  font-size: 1.5rem;
  margin-top: 0.1rem;
}

.confirm-content p {
  margin: 0 0 0.75rem 0;
  line-height: 1.5;
}

@media (max-width: 768px) {
  .backup-grid,
  .config-grid,
  .config-row,
  .restore-upload-row {
    grid-template-columns: 1fr;
  }

  .backup-page {
    padding: 0 0.5rem 1rem;
    gap: 1rem;
  }

  .backup-section-header,
  .backup-section-body {
    padding: 1rem;
  }

  .section-actions,
  .restore-upload-row {
    justify-items: stretch;
  }

  .section-actions :deep(.p-button),
  .restore-upload-row :deep(.p-button) {
    width: 100%;
  }

  .progress-heading,
  .progress-caption {
    flex-direction: column;
    gap: 0.35rem;
  }
}
</style>

<style>
.p-dark .admin-settings .backup-page {
  --backup-section-bg: color-mix(in srgb, var(--gp-surface-dark) 62%, var(--gp-surface-darker));
  --backup-section-border: color-mix(in srgb, var(--gp-primary) 36%, var(--gp-border-medium));
  --backup-section-header-bg: linear-gradient(
    90deg,
    color-mix(in srgb, var(--gp-primary) 16%, var(--gp-surface-darker)),
    color-mix(in srgb, var(--gp-surface-dark) 28%, var(--gp-surface-darker))
  );
  --backup-panel-bg: transparent;
  --backup-panel-border: color-mix(in srgb, var(--gp-border-medium) 58%, var(--gp-surface-darker));
  --backup-progress-bg: color-mix(in srgb, var(--gp-primary) 16%, var(--gp-surface-darker));
  --backup-subsection-bg: color-mix(in srgb, var(--gp-surface-dark) 18%, var(--gp-surface-darker));
  --backup-config-bg: color-mix(in srgb, var(--gp-surface-dark) 24%, var(--gp-surface-darker));
}
</style>
