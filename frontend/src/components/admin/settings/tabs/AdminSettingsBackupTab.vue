<template>
  <div>
    <div class="settings-section">
      <h3>Settings Backup</h3>

      <div class="backup-grid">
        <section class="backup-panel">
          <div class="backup-panel-header">
            <div>
              <h4>Export Settings</h4>
              <p class="text-muted">Download a JSON backup with portable admin-managed behavior and provider credentials.</p>
            </div>
            <i class="pi pi-download backup-panel-icon"></i>
          </div>
          <Button
            label="Export Settings"
            icon="pi pi-download"
            :loading="exporting"
            :disabled="adminReadOnly"
            @click="exportBackup"
          />
        </section>

        <section class="backup-panel">
          <div class="backup-panel-header">
            <div>
              <h4>Import Settings</h4>
              <p class="text-muted">Restore a JSON backup and replace current admin-managed settings.</p>
            </div>
            <i class="pi pi-upload backup-panel-icon"></i>
          </div>

          <FileUpload
            ref="fileUpload"
            mode="basic"
            accept=".json,application/json"
            chooseLabel="Choose Backup File"
            :auto="false"
            :disabled="adminReadOnly || importing"
            @select="onFileSelect"
            @clear="onFileClear"
          />

          <div v-if="selectedFile" class="selected-file">
            <i class="pi pi-file"></i>
            <span>{{ selectedFile.name }}</span>
          </div>

          <Button
            label="Import Settings"
            icon="pi pi-upload"
            severity="danger"
            :loading="importing"
            :disabled="adminReadOnly || !selectedFile"
            @click="openImportDialog"
          />
        </section>
      </div>

      <Message severity="warn" :closable="false" class="sensitive-warning">
        The export includes plaintext API keys, OIDC client secrets, tokens, and custom provider headers.
      </Message>
      <Message severity="info" :closable="false" class="scope-note">
        Backup scope is admin-managed app behavior plus OIDC/custom provider configs. Deployment/runtime infrastructure such as database, JWT keys, cookie/CORS, local filesystem paths, encryption key locations, metrics, warmup, and scheduler tuning is excluded.
      </Message>
    </div>

    <Dialog
      v-model:visible="importDialogVisible"
      header="Replace Admin Settings?"
      modal
      :style="{ width: '32rem' }"
    >
      <div class="confirm-content">
        <i class="pi pi-exclamation-triangle"></i>
        <div>
          <p>
            Importing this file will replace current global settings, OIDC providers, and custom geocoding providers.
          </p>
          <p class="text-muted">
            Deployment infrastructure and user data exports are not affected.
          </p>
        </div>
      </div>
      <template #footer>
        <Button label="Cancel" icon="pi pi-times" text @click="importDialogVisible = false" />
        <Button
          label="Import"
          icon="pi pi-upload"
          severity="danger"
          :loading="importing"
          @click="importBackup"
        />
      </template>
    </Dialog>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { storeToRefs } from 'pinia'
import Button from 'primevue/button'
import Dialog from 'primevue/dialog'
import FileUpload from 'primevue/fileupload'
import Message from 'primevue/message'
import { useToast } from 'primevue/usetoast'
import { useAuthStore } from '@/stores/auth'
import adminService from '@/utils/adminService'
import { showDemoReadOnlyToast } from '@/utils/demoMode'

const toast = useToast()
const authStore = useAuthStore()
const { adminReadOnly } = storeToRefs(authStore)

const exporting = ref(false)
const importing = ref(false)
const selectedFile = ref(null)
const importDialogVisible = ref(false)
const fileUpload = ref(null)

const onFileSelect = (event) => {
  selectedFile.value = event.files?.[0] || null
}

const onFileClear = () => {
  selectedFile.value = null
}

const exportBackup = async () => {
  if (adminReadOnly.value) {
    showDemoReadOnlyToast(toast)
    return
  }

  exporting.value = true
  try {
    await adminService.exportAdminSettingsBackup()
    toast.add({
      severity: 'success',
      summary: 'Export Started',
      detail: 'Admin settings backup download has started.',
      life: 3000
    })
  } catch (error) {
    toast.add({
      severity: 'error',
      summary: 'Export Failed',
      detail: error.message || 'Failed to export admin settings backup',
      life: 4000
    })
  } finally {
    exporting.value = false
  }
}

const openImportDialog = () => {
  if (adminReadOnly.value) {
    showDemoReadOnlyToast(toast)
    return
  }
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

    toast.add({
      severity: 'success',
      summary: 'Import Complete',
      detail: buildImportSummary(result),
      life: 5000
    })
  } catch (error) {
    const detail = error.response?.data?.message || error.message || 'Failed to import admin settings backup'
    toast.add({
      severity: 'error',
      summary: 'Import Failed',
      detail,
      life: 5000
    })
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
</script>

<style scoped>
@import '../admin-settings-common.css';

.backup-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 1rem;
  padding: 0 1rem;
}

.backup-panel {
  border: 1px solid var(--surface-border);
  border-radius: 8px;
  padding: 1rem;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 1rem;
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

.sensitive-warning {
  margin: 1rem;
}

.scope-note {
  margin: 1rem;
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
  .backup-grid {
    grid-template-columns: 1fr;
    padding: 0 0.5rem;
  }

  .sensitive-warning {
    margin: 1rem 0.5rem;
  }
}
</style>
