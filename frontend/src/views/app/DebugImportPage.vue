<template>
  <AppLayout>
    <PageContainer>
      <div class="debug-import-page">
        <!-- Page Header -->
        <div class="gp-page-header">
          <div class="gp-page-header-content">
            <div class="gp-page-header-text">
              <h1 class="gp-page-title">{{ t('data.debugImport.pageTitle') }}</h1>
              <p class="gp-page-subtitle">
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
              <FileDropZone
                v-model="selectedFile"
                accept=".zip"
                :prompt-text="t('data.debugImport.uploadPromptText')"
                :hint-text="t('data.debugImport.uploadHint')"
                :remove-label="t('data.debugImport.remove')"
                class="upload-area"
                @invalid="showInvalidFileToast"
              />

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
                <div class="checkbox-group">
                  <div class="checkbox-item">
                    <Checkbox v-model="updateTimezone" :binary="true" inputId="updateTimezone" />
                    <label for="updateTimezone" class="checkbox-label">
                      {{ t('data.debugImport.updateTimezone') }}
                    </label>
                  </div>
                  <small class="checkbox-help">
                    {{ t('data.debugImport.updateTimezoneHelp') }}
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

              <!-- Import Progress -->
              <div v-if="isImporting && importJob" class="import-progress">
                <ProgressBar :value="importJob.progress || 0" />
                <small class="checkbox-help">{{ importProgressMessage }}</small>
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

        <!-- Report -->
        <Card v-if="report" class="report-card">
          <template #content>
            <h2 class="section-title">{{ t('data.debugImport.report.title') }}</h2>
            <DebugImportReport :report="report" @open-timeline="openTimeline" />
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
                <li>
                  <i class="pi pi-check-circle"></i>
                  <strong>reverse_geocoding.json</strong> - {{ t('data.debugImport.items.reverseGeocoding') }}
                </li>
                <li>
                  <i class="pi pi-check-circle"></i>
                  <strong>timeline_overrides.json</strong> - {{ t('data.debugImport.items.timelineOverrides') }}
                </li>
                <li>
                  <i class="pi pi-check-circle"></i>
                  <strong>timeline.json</strong> - {{ t('data.debugImport.items.timeline') }}
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
import { computed, onUnmounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { useToast } from 'primevue/usetoast'
import AppLayout from '@/components/ui/layout/AppLayout.vue'
import PageContainer from '@/components/ui/layout/PageContainer.vue'
import Card from 'primevue/card'
import Button from 'primevue/button'
import Checkbox from 'primevue/checkbox'
import FileDropZone from '@/components/ui/forms/FileDropZone.vue'
import ProgressBar from 'primevue/progressbar'
import DebugImportReport from '@/components/data-export-import/DebugImportReport.vue'
import { useAuthStore } from '@/stores/auth'
import { pollJob } from '@/utils/jobPolling'
import { formatMessageDescriptor } from '@/utils/messageDescriptor'
import { useExportImportStore } from '@/stores/exportImport'
import { formatApiErrorDetail } from '@/utils/apiErrorDetail'

const { t } = useI18n()
const router = useRouter()
const toast = useToast()
const exportImportStore = useExportImportStore()

// Form state
const selectedFile = ref(null)
const clearExistingData = ref(true)
const updateTimelineConfig = ref(true)
const updateTimezone = ref(true)
const isImporting = ref(false)
const importError = ref(null)
const importJob = ref(null)
const report = ref(null)
const authStore = useAuthStore()
let importPolling = null

const importProgressMessage = computed(() =>
  formatMessageDescriptor(importJob.value?.progressMessage) || t('data.debugImport.importInProgress'))

onUnmounted(() => {
  importPolling?.cancel()
})

const openTimeline = (query) => {
  router.push({ path: '/app/timeline', query })
}

const showInvalidFileToast = () => {
  toast.add({
    severity: 'error',
    summary: t('data.debugImport.invalidFileSummary'),
    detail: t('data.debugImport.invalidFileDetail'),
    life: 3000
  })
}

watch(selectedFile, () => {
  importError.value = null
})

// Import functionality
const importData = async () => {
  if (!selectedFile.value) {
    return
  }

  importError.value = null
  isImporting.value = true

  report.value = null
  try {
    const created = await exportImportStore.uploadDebugImport(selectedFile.value, {
      clearExistingData: clearExistingData.value,
      updateTimelineConfig: updateTimelineConfig.value,
      updateTimezone: updateTimezone.value
    })
    importJob.value = created.job

    // The import runs in the background: regenerating a long timeline can take a while
    importPolling = pollJob({
      fetchJob: () => exportImportStore.fetchDebugImport(created.job.importJobId),
      statusOf: response => response?.job?.status,
      onUpdate: response => { importJob.value = response.job }
    })
    const finished = await importPolling.promise
    if (!finished) {
      return
    }
    if (finished.job.status === 'failed') {
      throw new Error(formatMessageDescriptor(finished.job.error) || t('data.debugImport.importFailedFallback'))
    }

    report.value = finished.report
    if (finished.report?.timezoneApplied) {
      // Days in the timeline start and end in the imported user's timezone now
      await authStore.fetchCurrentUserProfile().catch(() => {})
    }

    toast.add({
      severity: 'success',
      summary: t('data.debugImport.importSuccessSummary'),
      detail: t('data.debugImport.importSuccessDetail'),
      life: 5000
    })

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
    importPolling = null
  }
}
</script>

<style scoped>
.import-progress {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  margin-top: 1rem;
}

.report-card {
  margin-bottom: 2rem;
}

.debug-import-page {
  max-width: 900px;
  margin: 0 auto;
  padding: 2rem 0;
}

.warning-banner {
  margin-bottom: 2rem;
  border-left: 4px solid var(--p-orange-500);
}

.warning-banner .banner-content {
  display: flex;
  gap: 1rem;
  align-items: flex-start;
}

.warning-banner .banner-icon {
  font-size: 1.5rem;
  color: var(--p-orange-500);
  flex-shrink: 0;
}

.warning-banner .banner-text .banner-title {
  font-size: 1.1rem;
  font-weight: 600;
  margin: 0 0 0.5rem 0;
  color: var(--gp-text-primary);
}

.warning-banner .banner-text .banner-description {
  margin: 0;
  color: var(--gp-text-secondary);
  line-height: 1.5;
}

.upload-card {
  margin-bottom: 2rem;
}

.upload-section .section-title {
  font-size: 1.3rem;
  font-weight: 600;
  margin: 0 0 1.5rem 0;
  color: var(--gp-text-primary);
}

.upload-area {
  margin-bottom: 1.5rem;
}

.form-group {
  margin-bottom: 1.5rem;
}

.form-group .form-label {
  display: block;
  font-weight: 600;
  margin-bottom: 0.75rem;
  color: var(--gp-text-primary);
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
  color: var(--gp-text-primary);
  font-weight: 500;
}

.checkbox-help {
  display: block;
  margin-left: 1.75rem;
  margin-top: 0.25rem;
  color: var(--gp-text-secondary);
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
  color: var(--gp-text-primary);
}

.info-card .info-content .info-description {
  margin: 0 0 1rem 0;
  color: var(--gp-text-secondary);
  line-height: 1.5;
}

.info-card .info-content .info-list {
  margin: 0 0 1rem 0;
  padding-left: 0;
  list-style: none;
}

.info-card .info-content .info-list li {
  padding: 0.5rem 0;
  color: var(--gp-text-secondary);
  line-height: 1.6;
  display: flex;
  align-items: flex-start;
  gap: 0.75rem;
}

.info-card .info-content .info-list li i {
  color: var(--p-green-500);
  font-size: 1.1rem;
  flex-shrink: 0;
  margin-top: 0.1rem;
}

.info-card .info-content .info-list li strong {
  color: var(--gp-text-primary);
}

.info-card .info-content .info-note {
  margin: 1rem 0 0 0;
  padding: 0.75rem;
  background: var(--gp-primary-soft);
  border-left: 3px solid var(--gp-primary);
  border-radius: 4px;
  color: var(--gp-text-secondary);
  line-height: 1.5;
  display: flex;
  align-items: flex-start;
  gap: 0.75rem;
}

.info-card .info-content .info-note i {
  color: var(--gp-primary);
  font-size: 1.1rem;
  flex-shrink: 0;
  margin-top: 0.1rem;
}

@media (max-width: 768px) {
  .debug-import-page {
    padding: 1rem 0;
  }

  .import-button {
    width: 100%;
  }
}
</style>
