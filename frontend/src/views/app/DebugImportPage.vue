<template>
  <AppLayout>
    <PageContainer>
      <div class="debug-import-page">
        <!-- Page Header -->
        <div class="page-header">
          <div class="header-content">
            <div class="header-text">
              <h1 class="page-title">{{ t('data.debugImport.pageTitle') }}</h1>
              <p class="page-description">
                {{ t('data.debugImport.pageDescription') }}
              </p>
            </div>
          </div>
        </div>

        <!-- Warning Banner -->
        <Card class="warning-banner">
          <template #content>
            <div class="banner-content">
              <div class="banner-icon">
                <i class="pi pi-exclamation-triangle"></i>
              </div>
              <div class="banner-text">
                <h3 class="banner-title">{{ t('data.debugImport.importantTitle') }}</h3>
                <p class="banner-description">
                  {{ t('data.debugImport.importantDescription') }}
                </p>
              </div>
            </div>
          </template>
        </Card>

        <!-- Upload Card -->
        <Card class="upload-card">
          <template #content>
            <div class="upload-section">
              <h2 class="section-title">{{ t('data.debugImport.sectionTitle') }}</h2>

              <!-- File Upload -->
              <div class="upload-area" @click="triggerFileInput" @drop.prevent="handleDrop" @dragover.prevent>
                <input
                  ref="fileInput"
                  type="file"
                  accept=".zip"
                  @change="handleFileSelect"
                  style="display: none"
                />

                <div v-if="!selectedFile" class="upload-prompt">
                  <i class="pi pi-cloud-upload upload-icon"></i>
                  <p class="upload-text">{{ t('data.debugImport.uploadPromptText') }}</p>
                  <p class="upload-hint">{{ t('data.debugImport.uploadHint') }}</p>
                </div>

                <div v-else class="file-info">
                  <i class="pi pi-file upload-icon"></i>
                  <p class="file-name">{{ selectedFile.name }}</p>
                  <p class="file-size">{{ formatFileSize(selectedFile.size) }}</p>
                  <Button
                    :label="t('data.debugImport.remove')"
                    icon="pi pi-times"
                    size="small"
                    severity="danger"
                    @click.stop="removeFile"
                    text
                  />
                </div>
              </div>

              <!-- Import Options -->
              <div class="form-group">
                <label class="form-label">{{ t('data.debugImport.importOptionsLabel') }}</label>
                <div class="checkbox-group">
                  <div class="checkbox-item">
                    <Checkbox v-model="clearExistingData" :binary="true" inputId="clearData" />
                    <label for="clearData" class="checkbox-label">
                      {{ t('data.debugImport.clearExistingData') }}
                    </label>
                  </div>
                  <small class="checkbox-help">
                    {{ t('data.debugImport.clearExistingDataHelp') }}
                  </small>
                </div>

                <div class="checkbox-group">
                  <div class="checkbox-item">
                    <Checkbox v-model="updateTimelineConfig" :binary="true" inputId="updateConfig" />
                    <label for="updateConfig" class="checkbox-label">
                      {{ t('data.debugImport.updateTimelineConfig') }}
                    </label>
                  </div>
                  <small class="checkbox-help">
                    {{ t('data.debugImport.updateTimelineConfigHelp') }}
                  </small>
                </div>
              </div>

              <!-- Import Button -->
              <div class="form-actions">
                <Button
                  :label="t('data.debugImport.importButton')"
                  icon="pi pi-upload"
                  :loading="isImporting"
                  :disabled="!selectedFile"
                  @click="importData"
                  class="import-button"
                  severity="danger"
                />
              </div>

              <!-- Import Error -->
              <div v-if="importError" class="import-error-box">
                <div class="error-header">
                  <i class="pi pi-times-circle"></i>
                  <span>{{ t('data.debugImport.importFailedLabel') }}</span>
                </div>
                <div class="error-message">
                  {{ importError }}
                </div>
              </div>
            </div>
          </template>
        </Card>

        <!-- Info Card -->
        <Card class="info-card">
          <template #content>
            <div class="info-content">
              <h3 class="info-title">{{ t('data.debugImport.whatWillBeImportedTitle') }}</h3>
              <p class="info-description">
                {{ t('data.debugImport.zipDescription') }}
              </p>
              <ul class="info-list">
                <li>
                  <i class="pi pi-check-circle"></i>
                  <strong>metadata.json</strong> - {{ t('data.debugImport.items.metadata') }}
                </li>
                <li>
                  <i class="pi pi-check-circle"></i>
                  <strong>gps_data.json</strong> - {{ t('data.debugImport.items.gpsData') }}
                </li>
                <li>
                  <i class="pi pi-check-circle"></i>
                  <strong>timeline_config.json</strong> - {{ t('data.debugImport.items.timelineConfig') }}
                </li>
                <li>
                  <i class="pi pi-check-circle"></i>
                  <strong>favorite_locations.json</strong> - {{ t('data.debugImport.items.favoriteLocations') }}
                </li>
                <li>
                  <i class="pi pi-check-circle"></i>
                  <strong>favorite_areas.json</strong> - {{ t('data.debugImport.items.favoriteAreas') }}
                </li>
              </ul>
              <p class="info-note">
                <i class="pi pi-info-circle"></i>
                {{ t('data.debugImport.importNote') }}
              </p>
            </div>
          </template>
        </Card>
      </div>
    </PageContainer>
  </AppLayout>
</template>

<script setup>
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { useToast } from 'primevue/usetoast'
import AppLayout from '@/components/ui/layout/AppLayout.vue'
import PageContainer from '@/components/ui/layout/PageContainer.vue'
import Card from 'primevue/card'
import Button from 'primevue/button'
import Checkbox from 'primevue/checkbox'
import { useExportImportStore } from '@/stores/exportImport'
import { formatApiErrorDetail } from '@/utils/apiErrorDetail'

const { t } = useI18n()
const router = useRouter()
const toast = useToast()
const exportImportStore = useExportImportStore()

// Form state
const selectedFile = ref(null)
const fileInput = ref(null)
const clearExistingData = ref(true)
const updateTimelineConfig = ref(true)
const isImporting = ref(false)
const importError = ref(null)

// File selection
const triggerFileInput = () => {
  fileInput.value?.click()
}

const handleFileSelect = (event) => {
  const file = event.target.files[0]
  if (file) {
    validateAndSetFile(file)
  }
}

const handleDrop = (event) => {
  const file = event.dataTransfer.files[0]
  if (file) {
    validateAndSetFile(file)
  }
}

const validateAndSetFile = (file) => {
  if (!file.name.endsWith('.zip')) {
    toast.add({
      severity: 'error',
      summary: t('data.debugImport.invalidFileSummary'),
      detail: t('data.debugImport.invalidFileDetail'),
      life: 3000
    })
    return
  }

  selectedFile.value = file
  importError.value = null
}

const removeFile = () => {
  selectedFile.value = null
  if (fileInput.value) {
    fileInput.value.value = ''
  }
}

const formatFileSize = (bytes) => {
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / (1024 * 1024)).toFixed(1) + ' MB'
}

// Import functionality
const importData = async () => {
  if (!selectedFile.value) {
    return
  }

  importError.value = null
  isImporting.value = true

  try {
    await exportImportStore.uploadDebugImport(
      selectedFile.value,
      clearExistingData.value,
      updateTimelineConfig.value
    )

    toast.add({
      severity: 'success',
      summary: t('data.debugImport.importSuccessSummary'),
      detail: t('data.debugImport.importSuccessDetail'),
      life: 5000
    })

    // Redirect to timeline page after successful import
    setTimeout(() => {
      router.push('/app/timeline')
    }, 2000)

  } catch (error) {
    console.error('Failed to import debug data:', error)

    const errorMessage = formatApiErrorDetail(error, t('data.debugImport.importFailedFallback'))

    importError.value = errorMessage

    toast.add({
      severity: 'error',
      summary: t('data.debugImport.importFailedLabel'),
      detail: errorMessage,
      life: 5000
    })
  } finally {
    isImporting.value = false
  }
}
</script>

<style scoped>
.debug-import-page {
  max-width: 900px;
  margin: 0 auto;
  padding: 2rem 0;
}

.page-header {
  margin-bottom: 2rem;
}

.header-content .header-text .page-title {
  font-size: 2rem;
  font-weight: 600;
  color: var(--text-color);
  margin: 0 0 0.5rem 0;
}

.header-content .header-text .page-description {
  font-size: 1rem;
  color: var(--text-color-secondary);
  margin: 0;
}

.warning-banner {
  margin-bottom: 2rem;
  border-left: 4px solid var(--orange-500);
}

.warning-banner .banner-content {
  display: flex;
  gap: 1rem;
  align-items: flex-start;
}

.warning-banner .banner-icon {
  font-size: 1.5rem;
  color: var(--orange-500);
  flex-shrink: 0;
}

.warning-banner .banner-text .banner-title {
  font-size: 1.1rem;
  font-weight: 600;
  margin: 0 0 0.5rem 0;
  color: var(--text-color);
}

.warning-banner .banner-text .banner-description {
  margin: 0;
  color: var(--text-color-secondary);
  line-height: 1.5;
}

.upload-card {
  margin-bottom: 2rem;
}

.upload-section .section-title {
  font-size: 1.3rem;
  font-weight: 600;
  margin: 0 0 1.5rem 0;
  color: var(--text-color);
}

.upload-area {
  border: 2px dashed var(--surface-border);
  border-radius: 8px;
  padding: 3rem 2rem;
  text-align: center;
  cursor: pointer;
  transition: all 0.3s;
  margin-bottom: 1.5rem;
}

.upload-area:hover {
  border-color: var(--primary-color);
  background: var(--surface-ground);
}

.upload-prompt .upload-icon,
.file-info .upload-icon {
  font-size: 3rem;
  color: var(--text-color-secondary);
  margin-bottom: 1rem;
}

.upload-prompt .upload-text {
  font-size: 1.1rem;
  font-weight: 500;
  color: var(--text-color);
  margin: 0 0 0.5rem 0;
}

.upload-prompt .upload-hint {
  font-size: 0.875rem;
  color: var(--text-color-secondary);
  margin: 0;
}

.file-info {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.5rem;
}

.file-info .file-name {
  font-size: 1.1rem;
  font-weight: 500;
  color: var(--text-color);
  margin: 0;
}

.file-info .file-size {
  font-size: 0.875rem;
  color: var(--text-color-secondary);
  margin: 0 0 1rem 0;
}

.form-group {
  margin-bottom: 1.5rem;
}

.form-group .form-label {
  display: block;
  font-weight: 600;
  margin-bottom: 0.75rem;
  color: var(--text-color);
}

.checkbox-group {
  margin-bottom: 1rem;
}

.checkbox-group:last-child {
  margin-bottom: 0;
}

.checkbox-item {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.5rem 0;
}

.checkbox-item .checkbox-label {
  cursor: pointer;
  color: var(--text-color);
  font-weight: 500;
}

.checkbox-help {
  display: block;
  margin-left: 1.75rem;
  margin-top: 0.25rem;
  color: var(--text-color-secondary);
  font-size: 0.875rem;
  line-height: 1.4;
}

.form-actions {
  margin-top: 2rem;
  display: flex;
  justify-content: flex-end;
}

.import-button {
  padding: 0.75rem 2rem;
  font-size: 1rem;
  font-weight: 600;
}

.import-error-box {
  margin-top: 1.5rem;
  padding: 1.25rem;
  background: #fee;
  border: 3px solid #dc3545;
  border-radius: 8px;
}

.import-error-box .error-header {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  font-weight: 700;
  font-size: 1.1rem;
  margin-bottom: 0.75rem;
  color: #dc3545;
}

.import-error-box .error-message {
  line-height: 1.7;
  color: #721c24;
  white-space: pre-line;
  font-size: 0.95rem;
  font-weight: 500;
}

.info-card .info-content .info-title {
  font-size: 1.1rem;
  font-weight: 600;
  margin: 0 0 0.75rem 0;
  color: var(--text-color);
}

.info-card .info-content .info-description {
  margin: 0 0 1rem 0;
  color: var(--text-color-secondary);
  line-height: 1.5;
}

.info-card .info-content .info-list {
  margin: 0 0 1rem 0;
  padding-left: 0;
  list-style: none;
}

.info-card .info-content .info-list li {
  padding: 0.5rem 0;
  color: var(--text-color-secondary);
  line-height: 1.6;
  display: flex;
  align-items: flex-start;
  gap: 0.75rem;
}

.info-card .info-content .info-list li i {
  color: var(--green-500);
  font-size: 1.1rem;
  flex-shrink: 0;
  margin-top: 0.1rem;
}

.info-card .info-content .info-list li strong {
  color: var(--text-color);
}

.info-card .info-content .info-note {
  margin: 1rem 0 0 0;
  padding: 0.75rem;
  background: var(--blue-50);
  border-left: 3px solid var(--primary-color);
  border-radius: 4px;
  color: var(--text-color-secondary);
  line-height: 1.5;
  display: flex;
  align-items: flex-start;
  gap: 0.75rem;
}

.info-card .info-content .info-note i {
  color: var(--primary-color);
  font-size: 1.1rem;
  flex-shrink: 0;
  margin-top: 0.1rem;
}

@media (max-width: 768px) {
  .debug-import-page {
    padding: 1rem 0;
  }

  .page-header .header-text .page-title {
    font-size: 1.5rem;
  }

  .upload-area {
    padding: 2rem 1rem;
  }

  .import-button {
    width: 100%;
  }
}
</style>
