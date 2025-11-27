<template>
  <AppLayout>
    <PageContainer>
      <div class="debug-import-page">
        <!-- Page Header -->
        <div class="page-header">
          <div class="header-content">
            <div class="header-text">
              <h1 class="page-title">Import Debug Data</h1>
              <p class="page-description">
                Import GPS data and timeline configuration from a debug export ZIP file
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
                <h3 class="banner-title">Important</h3>
                <p class="banner-description">
                  This import is designed for troubleshooting purposes. It will import shifted GPS data,
                  anonymized favorite locations, and timeline configuration. By default, it will clear
                  all your existing data before import.
                </p>
              </div>
            </div>
          </template>
        </Card>

        <!-- Upload Card -->
        <Card class="upload-card">
          <template #content>
            <div class="upload-section">
              <h2 class="section-title">Upload Debug Export ZIP</h2>

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
                  <p class="upload-text">Click to select or drag & drop a ZIP file</p>
                  <p class="upload-hint">Only .zip files from debug export are accepted</p>
                </div>

                <div v-else class="file-info">
                  <i class="pi pi-file upload-icon"></i>
                  <p class="file-name">{{ selectedFile.name }}</p>
                  <p class="file-size">{{ formatFileSize(selectedFile.size) }}</p>
                  <Button
                    label="Remove"
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
                <label class="form-label">Import Options</label>
                <div class="checkbox-group">
                  <div class="checkbox-item">
                    <Checkbox v-model="clearExistingData" :binary="true" inputId="clearData" />
                    <label for="clearData" class="checkbox-label">
                      Clear existing data before import
                    </label>
                  </div>
                  <small class="checkbox-help">
                    This will delete all your GPS points, timeline data, and favorite locations before importing.
                    Recommended for troubleshooting on a fresh user account.
                  </small>
                </div>

                <div class="checkbox-group">
                  <div class="checkbox-item">
                    <Checkbox v-model="updateTimelineConfig" :binary="true" inputId="updateConfig" />
                    <label for="updateConfig" class="checkbox-label">
                      Update timeline configuration
                    </label>
                  </div>
                  <small class="checkbox-help">
                    This will replace your current timeline settings with the configuration from the ZIP file.
                    Required to reproduce the exact same timeline.
                  </small>
                </div>
              </div>

              <!-- Import Button -->
              <div class="form-actions">
                <Button
                  label="Import Debug Data"
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
                  <span>Import Failed</span>
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
              <h3 class="info-title">What Will Be Imported?</h3>
              <p class="info-description">
                The ZIP file should contain the following files from a debug export:
              </p>
              <ul class="info-list">
                <li>
                  <i class="pi pi-check-circle"></i>
                  <strong>metadata.json</strong> - Export metadata and validation info
                </li>
                <li>
                  <i class="pi pi-check-circle"></i>
                  <strong>gps_data.json</strong> - GPS points with shifted coordinates
                </li>
                <li>
                  <i class="pi pi-check-circle"></i>
                  <strong>timeline_config.json</strong> - Timeline generation settings
                </li>
                <li>
                  <i class="pi pi-check-circle"></i>
                  <strong>favorite_locations.json</strong> - Anonymized favorite locations
                </li>
                <li>
                  <i class="pi pi-check-circle"></i>
                  <strong>favorite_areas.json</strong> - Anonymized favorite areas
                </li>
              </ul>
              <p class="info-note">
                <i class="pi pi-info-circle"></i>
                After import, the timeline will be automatically regenerated using the imported data and configuration.
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
import { useRouter } from 'vue-router'
import { useToast } from 'primevue/usetoast'
import AppLayout from '@/components/ui/layout/AppLayout.vue'
import PageContainer from '@/components/ui/layout/PageContainer.vue'
import Card from 'primevue/card'
import Button from 'primevue/button'
import Checkbox from 'primevue/checkbox'
import apiService from '@/utils/apiService'

const router = useRouter()
const toast = useToast()

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
      summary: 'Invalid File',
      detail: 'Please select a ZIP file',
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
    const formData = new FormData()
    formData.append('file', selectedFile.value)
    formData.append('clearExistingData', clearExistingData.value)
    formData.append('updateTimelineConfig', updateTimelineConfig.value)

    await apiService.post('/import/debug/upload', formData, {
      headers: {
        'Content-Type': 'multipart/form-data'
      }
    })

    toast.add({
      severity: 'success',
      summary: 'Import Successful',
      detail: 'Debug data has been imported and timeline regenerated',
      life: 5000
    })

    // Redirect to timeline page after successful import
    setTimeout(() => {
      router.push('/app/timeline')
    }, 2000)

  } catch (error) {
    console.error('Failed to import debug data:', error)

    let errorMessage = 'Failed to import debug data'
    if (error.response?.data?.error?.message) {
      errorMessage = error.response.data.error.message
    } else if (error.message) {
      errorMessage = error.message
    }

    importError.value = errorMessage

    toast.add({
      severity: 'error',
      summary: 'Import Failed',
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
