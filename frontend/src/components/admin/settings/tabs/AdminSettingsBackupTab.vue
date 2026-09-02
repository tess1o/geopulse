<template>
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

    <AdminFullBackupSection :admin-read-only="adminReadOnly" />

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
import AdminFullBackupSection from '@/components/admin/settings/sections/AdminFullBackupSection.vue'
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

const onFileSelect = (event) => { selectedFile.value = event.files?.[0] || null }
const onFileClear = () => { selectedFile.value = null }

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
    const settings = result.settingsImported ?? 0
    const oidc = result.oidcProvidersImported ?? 0
    const custom = result.customGeocodingProvidersImported ?? 0
    toast.add({ severity: 'success', summary: 'Import Complete', detail: `Restored ${settings} settings, ${oidc} OIDC providers, and ${custom} custom geocoding providers.`, life: 5000 })
  } catch (error) {
    toast.add({ severity: 'error', summary: 'Import Failed', detail: error.response?.data?.message || error.message || 'Failed to import admin settings backup', life: 5000 })
  } finally {
    importing.value = false
  }
}
</script>

<style scoped>
@import '../admin-settings-common.css';

.backup-page {
  display: grid;
  gap: 1.75rem;
  padding: 0 1rem 1.5rem;
}

.backup-section {
  border: 1px solid color-mix(in srgb, var(--gp-primary) 26%, var(--gp-border-medium));
  border-left: 4px solid var(--gp-primary);
  border-radius: 8px;
  overflow: hidden;
  background: var(--gp-surface-white);
  box-shadow: var(--gp-shadow-card);
}

.backup-section-header {
  padding: 1.25rem 1.45rem;
  border-bottom: 1px solid color-mix(in srgb, var(--gp-primary) 26%, var(--gp-border-medium));
  background: color-mix(in srgb, var(--gp-primary) 7%, var(--gp-surface-white));
}

.backup-section-header h3 { margin: 0 0 0.35rem; color: var(--gp-primary); }
.backup-section-header p, .backup-panel p { margin: 0; line-height: 1.5; }
.backup-section-body { display: grid; gap: 1.5rem; padding: 1.45rem; }
.backup-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 1rem; }
.backup-panel { border: 1px solid var(--gp-border-medium); border-radius: 8px; padding: 1.15rem; display: flex; flex-direction: column; align-items: flex-start; gap: 1rem; background: var(--gp-surface-light); }
.backup-panel-header { width: 100%; display: flex; justify-content: space-between; gap: 1rem; }
.backup-panel h4 { margin: 0 0 0.35rem; font-size: 1rem; color: var(--gp-text-primary); }
.backup-panel-icon { color: var(--gp-primary); font-size: 1.25rem; }
.inline-upload { display: flex; align-items: center; flex-wrap: wrap; gap: 0.75rem; width: 100%; }
.selected-file { display: inline-flex; align-items: center; gap: 0.5rem; max-width: 100%; color: var(--text-color-secondary); font-size: 0.9rem; }
.selected-file span { overflow-wrap: anywhere; }
.message-stack { display: grid; gap: 0.75rem; }
.confirm-content { display: flex; gap: 1rem; align-items: flex-start; }
.confirm-content > i { color: var(--p-red-500); font-size: 1.5rem; }
.confirm-content p { margin: 0 0 0.75rem; line-height: 1.5; }

@media (max-width: 768px) {
  .backup-grid { grid-template-columns: 1fr; }
  .backup-page { padding: 0 0.5rem 1rem; gap: 1rem; }
  .backup-section-header, .backup-section-body { padding: 1rem; }
}
</style>

<style>
.p-dark .admin-settings .backup-page > .backup-section {
  background: color-mix(in srgb, var(--gp-surface-dark) 62%, var(--gp-surface-darker));
  border-color: color-mix(in srgb, var(--gp-primary) 36%, var(--gp-border-medium));
}
.p-dark .admin-settings .backup-page > .backup-section .backup-section-header {
  background: color-mix(in srgb, var(--gp-primary) 16%, var(--gp-surface-darker));
}
.p-dark .admin-settings .backup-page > .backup-section .backup-panel { background: transparent; }
</style>
