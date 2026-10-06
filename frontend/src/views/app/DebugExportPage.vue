<template>
  <AppLayout>
    <PageContainer>
      <div class="debug-export-page">
        <!-- Page Header -->
        <div class="gp-page-header">
          <div class="gp-page-header-content">
            <div class="gp-page-header-text">
              <h1 class="gp-page-title">{{ t('data.debugExport.pageTitle') }}</h1>
              <p class="gp-page-subtitle">
                {{ t('data.debugExport.pageDescription') }}
              </p>
            </div>
          </div>
        </div>

        <!-- Info Banner -->
        <Card class="info-banner warning">
          <template #content>
            <div class="banner-content">
              <div class="banner-icon">
                <i class="pi pi-shield"></i>
              </div>
              <div class="banner-text">
                <h3 class="banner-title">{{ t('data.debugExport.privacyTitle') }}</h3>
                <p class="banner-description">
                  {{ t('data.debugExport.privacyDescription') }}
                </p>
              </div>
            </div>
          </template>
        </Card>

        <Message v-if="demoModeEnabled" severity="error" :closable="false" class="demo-disabled-message">
          {{ t('data.debugExport.demoDisabledMessage') }}
        </Message>

        <!-- Export Configuration Card -->
        <Card class="export-config-card">
          <template #content>
            <div class="config-section">
              <h2 class="section-title">{{ t('data.debugExport.sectionTitle') }}</h2>

              <!-- Date Range Selection -->
              <div class="form-group">
                <label class="form-label">{{ t('data.debugExport.timeRangeLabel') }}</label>
                <div class="date-range-selector">
                  <Calendar
                      v-model="startDate"
                      hourFormat="24"
                      dateFormat="yy-mm-dd"
                      :placeholder="t('data.debugExport.startDatePlaceholder')"
                      :maxDate="new Date()"
                      :disabled="demoModeEnabled"
                      class="date-input"
                  />
                  <span class="date-separator">{{ t('data.debugExport.dateRangeSeparator') }}</span>
                  <Calendar
                      v-model="endDate"
                      hourFormat="24"
                      dateFormat="yy-mm-dd"
                      :placeholder="t('data.debugExport.endDatePlaceholder')"
                      :maxDate="new Date()"
                      :disabled="demoModeEnabled"
                      class="date-input"
                  />
                </div>
                <small class="form-help-text">
                  {{ t('data.debugExport.timeRangeHelp') }}
                </small>
              </div>

              <!-- Coordinate Shift Configuration -->
              <div class="form-group">
                <div class="form-label-with-action">
                  <label class="form-label">{{ t('data.debugExport.coordinateShiftLabel') }}</label>
                  <Button
                      :label="t('data.debugExport.generateNewShift')"
                      icon="pi pi-refresh"
                      size="small"
                      :disabled="demoModeEnabled"
                      @click="generateRandomShift"
                      text
                  />
                </div>
                <div class="shift-inputs">
                  <div class="shift-input-group">
                    <label class="input-label">{{ t('data.debugExport.latitudeShiftLabel') }}</label>
                    <InputNumber
                        v-model="latitudeShift"
                        :minFractionDigits="6"
                        :maxFractionDigits="6"
                        :allowEmpty="false"
                        :placeholder="t('data.debugExport.latitudeShiftPlaceholder')"
                        :disabled="demoModeEnabled"
                        class="shift-input"
                    />
                  </div>
                  <div class="shift-input-group">
                    <label class="input-label">{{ t('data.debugExport.longitudeShiftLabel') }}</label>
                    <InputNumber
                        v-model="longitudeShift"
                        :minFractionDigits="6"
                        :maxFractionDigits="6"
                        :allowEmpty="false"
                        :placeholder="t('data.debugExport.longitudeShiftPlaceholder')"
                        :disabled="demoModeEnabled"
                        class="shift-input"
                    />
                  </div>
                </div>
                <small class="form-help-text">
                  {{ t('data.debugExport.shiftHelp') }}
                </small>
              </div>

              <!-- Options -->
              <div class="form-group">
                <label class="form-label">{{ t('data.debugExport.exportOptionsLabel') }}</label>
                <div class="checkbox-group">
                  <div class="checkbox-item">
                    <Checkbox v-model="includeConfiguration" :binary="true" inputId="includeConfig" :disabled="demoModeEnabled" />
                    <label for="includeConfig" class="checkbox-label">
                      {{ t('data.debugExport.includeConfiguration') }}
                    </label>
                  </div>
                </div>
                <small class="form-help-text">
                  {{ t('data.debugExport.includeConfigurationHelp') }}
                </small>
              </div>

              <!-- Export Button -->
              <div class="form-actions">
                <Button
                    :label="t('data.debugExport.exportButton')"
                    icon="pi pi-download"
                    :loading="isExporting"
                    :disabled="!isFormValid || demoModeEnabled"
                    @click="exportDebugData"
                    class="export-button"
                />
              </div>

              <!-- Validation Messages -->
              <div v-if="validationError" class="validation-error">
                <i class="pi pi-exclamation-circle"></i>
                {{ validationError }}
              </div>

              <!-- Export Error Message -->
              <div v-if="exportError" class="export-error-box">
                <div class="error-header">
                  <i class="pi pi-times-circle"></i>
                  <span>{{ t('data.debugExport.exportFailedLabel') }}</span>
                </div>
                <div class="error-message">
                  {{ exportError }}
                </div>
              </div>
            </div>
          </template>
        </Card>

        <!-- What's Exported -->
        <Card class="info-card">
          <template #content>
            <div class="info-content">
              <h3 class="info-title">{{ t('data.debugExport.whatWillBeExportedTitle') }}</h3>
              <p class="info-description">
                {{ t('data.debugExport.zipDescription') }}
              </p>
              <ul class="info-list">
                <li>
                  <i class="pi pi-check-circle"></i>
                  <strong>metadata.json</strong> - {{ t('data.debugExport.items.metadata') }}
                </li>
                <li>
                  <i class="pi pi-check-circle"></i>
                  <strong>gps_data.json</strong> - {{ t('data.debugExport.items.gpsData') }}
                </li>
                <li>
                  <i class="pi pi-check-circle"></i>
                  <strong>timeline_config.json</strong> - {{ t('data.debugExport.items.timelineConfig') }}
                </li>
                <li>
                  <i class="pi pi-check-circle"></i>
                  <strong>favorite_locations.json</strong> - {{ t('data.debugExport.items.favoriteLocations') }}
                </li>
                <li>
                  <i class="pi pi-check-circle"></i>
                  <strong>favorite_areas.json</strong> - {{ t('data.debugExport.items.favoriteAreas') }}
                </li>
              </ul>
              <p class="info-note">
                <i class="pi pi-info-circle"></i>
                {{ t('data.debugExport.privacyNote') }}
              </p>
            </div>
          </template>
        </Card>
      </div>
    </PageContainer>
  </AppLayout>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { storeToRefs } from 'pinia'
import { useToast } from 'primevue/usetoast'
import { useTimezone } from '@/composables/useTimezone'
import AppLayout from '@/components/ui/layout/AppLayout.vue'
import PageContainer from '@/components/ui/layout/PageContainer.vue'
import Card from 'primevue/card'
import Calendar from 'primevue/calendar'
import Button from 'primevue/button'
import InputNumber from 'primevue/inputnumber'
import Checkbox from 'primevue/checkbox'
import Message from 'primevue/message'
import { useAuthStore } from '@/stores/auth'
import { useExportImportStore } from '@/stores/exportImport'
import { formatApiErrorDetail } from '@/utils/apiErrorDetail'
import { showDemoModeToast } from '@/utils/demoMode'

const { t } = useI18n()
const toast = useToast()
const timezone = useTimezone()
const authStore = useAuthStore()
const exportImportStore = useExportImportStore()
const { demoModeEnabled } = storeToRefs(authStore)

// Form state
const startDate = ref(null)
const endDate = ref(null)
const latitudeShift = ref(null)
const longitudeShift = ref(null)
const includeConfiguration = ref(true)
const isExporting = ref(false)
const validationError = ref(null)
const exportError = ref(null)

// Initialize with last 30 days
const initializeDates = () => {
  const end = new Date()
  const start = new Date()
  start.setDate(start.getDate() - 30)

  startDate.value = start
  endDate.value = end
}

// Generate random coordinate shift
// Uses conservative ranges to avoid pushing coordinates out of bounds
// Latitude: -20 to +20 (safe for most locations except near poles)
// Longitude: -40 to +40 (longitude wraps, but smaller shifts are safer)
const generateRandomShift = () => {
  if (demoModeEnabled.value) {
    return
  }

  latitudeShift.value = parseFloat((Math.random() * 40 - 20).toFixed(6))
  longitudeShift.value = parseFloat((Math.random() * 80 - 40).toFixed(6))
}

// Initialize on mount
onMounted(() => {
  initializeDates()
  generateRandomShift()
})

// Form validation
const isFormValid = computed(() => {
  if (demoModeEnabled.value) {
    validationError.value = null
    return false
  }

  if (!startDate.value || !endDate.value) {
    validationError.value = t('data.debugExport.validation.selectDates')
    return false
  }

  if (startDate.value > endDate.value) {
    validationError.value = t('data.debugExport.validation.startBeforeEnd')
    return false
  }

  if (startDate.value > new Date()) {
    validationError.value = t('data.debugExport.validation.startNotFuture')
    return false
  }

  if (latitudeShift.value === null || longitudeShift.value === null) {
    validationError.value = t('data.debugExport.validation.enterShift')
    return false
  }

  validationError.value = null
  return true
})

// Export debug data
const exportDebugData = async () => {
  if (demoModeEnabled.value) {
    showDemoModeToast(toast, t('data.debugExport.demoToast'))
    return
  }

  if (!isFormValid.value) {
    return
  }

  // Clear previous export error
  exportError.value = null
  isExporting.value = true

  try {
    // Adjust dates to ensure we capture full days in user's timezone
    // createDateRangeFromPicker already returns UTC ISO strings for start/end of day
    const dateRange = timezone.createDateRangeFromPicker(startDate.value, endDate.value)

    const requestData = {
      startTime: dateRange.start,  // Already UTC ISO string (start of day in user's timezone)
      endTime: dateRange.end,      // Already UTC ISO string (end of day in user's timezone)
      latitudeShift: latitudeShift.value,
      longitudeShift: longitudeShift.value,
      includeConfiguration: includeConfiguration.value
    }

    await exportImportStore.downloadDebugExport(requestData)

    toast.add({
      severity: 'success',
      summary: t('data.debugExport.exportSuccessSummary'),
      detail: t('data.debugExport.exportSuccessDetail'),
      life: 5000
    })
  } catch (error) {
    console.error('Failed to export debug data:', error)

    const errorMessage = formatApiErrorDetail(error, t('data.debugExport.exportFailedFallback'))

    // Set the export error for persistent display
    exportError.value = errorMessage

    // Also show toast notification
    toast.add({
      severity: 'error',
      summary: t('data.debugExport.exportFailedLabel'),
      detail: errorMessage,
      life: 5000
    })
  } finally {
    isExporting.value = false
  }
}
</script>

<style scoped>
.debug-export-page {
  max-width: 900px;
  margin: 0 auto;
  padding: 2rem 0;
}

.info-banner {
  margin-bottom: 2rem;
}

.info-banner.warning {
  border-left: 4px solid var(--gp-primary);
}

.info-banner .banner-content {
  display: flex;
  gap: 1rem;
  align-items: flex-start;
}

.info-banner .banner-content .banner-icon {
  font-size: 1.5rem;
  color: var(--gp-primary);
  flex-shrink: 0;
}

.info-banner .banner-content .banner-text .banner-title {
  font-size: 1.1rem;
  font-weight: 600;
  margin: 0 0 0.5rem 0;
  color: var(--gp-text-primary);
}

.info-banner .banner-content .banner-text .banner-description {
  margin: 0;
  color: var(--gp-text-secondary);
  line-height: 1.5;
}

.export-config-card {
  margin-bottom: 2rem;
}

.export-config-card .config-section .section-title {
  font-size: 1.3rem;
  font-weight: 600;
  margin: 0 0 1.5rem 0;
  color: var(--gp-text-primary);
}

.export-config-card .config-section .form-group {
  margin-bottom: 1.5rem;
}

.export-config-card .config-section .form-group .form-label {
  display: block;
  font-weight: 600;
  margin-bottom: 0.5rem;
  color: var(--gp-text-primary);
}

.export-config-card .config-section .form-group .form-label-with-action {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 0.5rem;
}

.export-config-card .config-section .form-group .form-label-with-action .form-label {
  margin-bottom: 0;
}

.export-config-card .config-section .form-group .date-range-selector {
  display: flex;
  align-items: center;
  gap: 1rem;
  flex-wrap: wrap;
}

.export-config-card .config-section .form-group .date-range-selector .date-input {
  flex: 1;
  min-width: 200px;
}

.export-config-card .config-section .form-group .date-range-selector .date-separator {
  color: var(--gp-text-secondary);
  font-weight: 500;
}

.export-config-card .config-section .form-group .shift-inputs {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 1rem;
}

.export-config-card .config-section .form-group .shift-inputs .shift-input-group .input-label {
  display: block;
  font-size: 0.875rem;
  color: var(--gp-text-secondary);
  margin-bottom: 0.25rem;
}

.export-config-card .config-section .form-group .shift-inputs .shift-input-group .shift-input {
  width: 100%;
}

.export-config-card .config-section .form-group .checkbox-group .checkbox-item {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.5rem 0;
}

.export-config-card .config-section .form-group .checkbox-group .checkbox-item .checkbox-label {
  cursor: pointer;
  color: var(--gp-text-primary);
}

.export-config-card .config-section .form-group .form-help-text {
  display: block;
  margin-top: 0.5rem;
  color: var(--gp-text-secondary);
  font-size: 0.875rem;
  line-height: 1.4;
}

.export-config-card .config-section .form-actions {
  margin-top: 2rem;
  display: flex;
  justify-content: flex-end;
}

.export-config-card .config-section .form-actions .export-button {
  padding: 0.75rem 2rem;
  font-size: 1rem;
  font-weight: 600;
}

.export-config-card .config-section .validation-error {
  margin-top: 1rem;
  padding: 0.75rem;
  background: var(--p-red-50);
  border: 1px solid var(--p-red-200);
  border-radius: 6px;
  color: var(--p-red-700);
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.export-config-card .config-section .validation-error i {
  font-size: 1.1rem;
}

.export-config-card .config-section .export-error-box {
  margin-top: 1.5rem;
  padding: 1.25rem;
  background: #fee;
  border: 3px solid #dc3545;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(220, 53, 69, 0.2);
}

.export-config-card .config-section .export-error-box .error-header {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  font-weight: 700;
  font-size: 1.1rem;
  margin-bottom: 0.75rem;
  color: #dc3545;
}

.export-config-card .config-section .export-error-box .error-header i {
  font-size: 1.4rem;
  color: #dc3545;
}

.export-config-card .config-section .export-error-box .error-message {
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
  background: var(--p-blue-50);
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
  .debug-export-page {
    padding: 1rem 0;
  }

  .export-config-card .config-section .form-group .shift-inputs {
    grid-template-columns: 1fr;
  }

  .export-config-card .config-section .form-actions .export-button {
    width: 100%;
  }
}
</style>
