<template>
  <div class="settings-section backup-page">
    <section class="backup-section">
      <div class="backup-section-header">
        <div>
          <h3>{{ t('adminSettings.backupTab.exportSection.title') }}</h3>
          <p class="text-muted">{{ t('adminSettings.backupTab.exportSection.subtitle') }}</p>
        </div>
      </div>

      <div class="backup-section-body">
        <div class="backup-grid">
          <section class="backup-panel">
            <div class="backup-panel-header">
              <div>
                <h4>{{ t('adminSettings.backupTab.exportSection.panelTitle') }}</h4>
                <p class="text-muted">{{ t('adminSettings.backupTab.exportSection.panelDescription') }}</p>
              </div>
              <i class="pi pi-download backup-panel-icon"></i>
            </div>
            <Button :label="t('adminSettings.backupTab.exportSection.button')" icon="pi pi-download" :loading="exporting" :disabled="adminReadOnly" @click="exportBackup" />
          </section>

          <section class="backup-panel">
            <div class="backup-panel-header">
              <div>
                <h4>{{ t('adminSettings.backupTab.importSection.panelTitle') }}</h4>
                <p class="text-muted">{{ t('adminSettings.backupTab.importSection.panelDescription') }}</p>
              </div>
              <i class="pi pi-upload backup-panel-icon"></i>
            </div>
            <div class="inline-upload">
              <FileUpload ref="fileUpload" mode="basic" accept=".json,application/json" :chooseLabel="t('adminSettings.backupTab.importSection.chooseFile')" :auto="false" :disabled="adminReadOnly || importing" @select="onFileSelect" @clear="onFileClear" />
              <div v-if="selectedFile" class="selected-file"><i class="pi pi-file"></i><span>{{ selectedFile.name }}</span></div>
            </div>
            <Button :label="t('adminSettings.backupTab.importSection.button')" icon="pi pi-upload" severity="danger" :loading="importing" :disabled="adminReadOnly || !selectedFile" @click="openImportDialog" />
          </section>
        </div>

        <div class="message-stack">
          <Message severity="warn" :closable="false">
            {{ t('adminSettings.backupTab.warnMessage') }}
          </Message>
          <Message severity="info" :closable="false">
            {{ t('adminSettings.backupTab.infoMessage') }}
          </Message>
        </div>
      </div>
    </section>

    <AdminFullBackupSection :admin-read-only="adminReadOnly" />

    <Dialog v-model:visible="importDialogVisible" :header="t('adminSettings.backupTab.importDialog.header')" modal :style="{ width: '32rem' }">
      <div class="confirm-content">
        <i class="pi pi-exclamation-triangle"></i>
        <div>
          <p>{{ t('adminSettings.backupTab.importDialog.message') }}</p>
          <p class="text-muted">{{ t('adminSettings.backupTab.importDialog.note') }}</p>
        </div>
      </div>
      <template #footer>
        <Button :label="t('common.cancel')" icon="pi pi-times" text @click="importDialogVisible = false" />
        <Button :label="t('adminSettings.backupTab.importDialog.import')" icon="pi pi-upload" severity="danger" :loading="importing" @click="importBackup" />
      </template>
    </Dialog>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { storeToRefs } from 'pinia'
import { useI18n } from 'vue-i18n'
import Button from 'primevue/button'
import Dialog from 'primevue/dialog'
import FileUpload from 'primevue/fileupload'
import Message from 'primevue/message'
import { useToast } from 'primevue/usetoast'
import AdminFullBackupSection from '@/components/admin/settings/sections/AdminFullBackupSection.vue'
import { useAuthStore } from '@/stores/auth'
import { useAdminStore } from '@/stores/admin'
import { showDemoReadOnlyToast } from '@/utils/demoMode'
import { formatApiErrorDetail } from '@/utils/apiErrorDetail'

const { t } = useI18n()
const adminService = useAdminStore()
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
    toast.add({ severity: 'success', summary: t('adminSettings.backupTab.toasts.exportStarted'), detail: t('adminSettings.backupTab.toasts.exportStartedDetail'), life: 3000 })
  } catch (error) {
    toast.add({ severity: 'error', summary: t('adminSettings.backupTab.toasts.exportFailed'), detail: error.message || t('adminSettings.backupTab.toasts.exportFailedFallback'), life: 4000 })
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
    toast.add({ severity: 'success', summary: t('adminSettings.backupTab.toasts.importComplete'), detail: t('adminSettings.backupTab.toasts.importCompleteDetail', { settings, oidc, custom }), life: 5000 })
  } catch (error) {
    toast.add({ severity: 'error', summary: t('adminSettings.backupTab.toasts.importFailed'), detail: formatApiErrorDetail(error, t('adminSettings.backupTab.toasts.importFailedFallback')), life: 5000 })
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
  background: var(--gp-surface-card);
  box-shadow: var(--gp-shadow-card);
}

.backup-section-header {
  padding: 1.25rem 1.45rem;
  border-bottom: 1px solid color-mix(in srgb, var(--gp-primary) 26%, var(--gp-border-medium));
  background: color-mix(in srgb, var(--gp-primary) 7%, var(--gp-surface-card));
}

.backup-section-header h3 { margin: 0 0 0.35rem; color: var(--gp-primary); }
.backup-section-header p, .backup-panel p { margin: 0; line-height: 1.5; }
.backup-section-body { display: grid; gap: 1.5rem; padding: 1.45rem; }
.backup-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 1rem; }
.backup-panel { border: 1px solid var(--gp-border-medium); border-radius: 8px; padding: 1.15rem; display: flex; flex-direction: column; align-items: flex-start; gap: 1rem; background: var(--gp-surface-muted); }
.backup-panel-header { width: 100%; display: flex; justify-content: space-between; gap: 1rem; }
.backup-panel h4 { margin: 0 0 0.35rem; font-size: 1rem; color: var(--gp-text-primary); }
.backup-panel-icon { color: var(--gp-primary); font-size: 1.25rem; }
.inline-upload { display: flex; align-items: center; flex-wrap: wrap; gap: 0.75rem; width: 100%; }
.selected-file { display: inline-flex; align-items: center; gap: 0.5rem; max-width: 100%; color: var(--gp-text-secondary); font-size: 0.9rem; }
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
</style>
