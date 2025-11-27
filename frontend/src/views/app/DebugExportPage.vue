<template>
  <AppLayout>
    <PageContainer>
      <div class="debug-export-page">
        <!-- Page Header -->
        <div class="page-header">
          <div class="header-content">
            <div class="header-text">
              <h1 class="page-title">Debug Data Export</h1>
              <p class="page-description">
                Export your GPS data with privacy-preserving coordinate shifts for debugging timeline issues
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
                <h3 class="banner-title">Privacy Protection</h3>
                <p class="banner-description">
                  All GPS coordinates will be shifted by a constant offset to protect your privacy.
                  The shift preserves relative distances and shapes for accurate timeline debugging.
                  Your actual location coordinates will not be revealed in the export.
                </p>
              </div>
            </div>
          </template>
        </Card>

        <!-- Export Configuration Card -->
        <Card class="export-config-card">
          <template #content>
            <div class="config-section">
              <h2 class="section-title">Export Configuration</h2>

              <!-- Date Range Selection -->
              <div class="form-group">
                <label class="form-label">Time Range</label>
                <div class="date-range-selector">
                  <Calendar
                      v-model="startDate"
                      hourFormat="24"
                      dateFormat="yy-mm-dd"
                      placeholder="Start Date"
                      :maxDate="new Date()"
                      class="date-input"
                  />
                  <span class="date-separator">to</span>
                  <Calendar
                      v-model="endDate"
                      hourFormat="24"
                      dateFormat="yy-mm-dd"
                      placeholder="End Date"
                      :maxDate="new Date()"
                      class="date-input"
                  />
                </div>
                <small class="form-help-text">
                  Export includes full days in your timezone (00:00:00 to 23:59:59) to ensure no data is missed.
                </small>
              </div>

              <!-- Coordinate Shift Configuration -->
              <div class="form-group">
                <div class="form-label-with-action">
                  <label class="form-label">Coordinate Shift (degrees)</label>
                  <Button
                      label="Generate New Random Shift"
                      icon="pi pi-refresh"
                      size="small"
                      @click="generateRandomShift"
                      text
                  />
                </div>
                <div class="shift-inputs">
                  <div class="shift-input-group">
                    <label class="input-label">Latitude Shift</label>
                    <InputNumber
                        v-model="latitudeShift"
                        :minFractionDigits="6"
                        :maxFractionDigits="6"
                        :allowEmpty="false"
                        placeholder="e.g., 12.345678"
                        class="shift-input"
                    />
                  </div>
                  <div class="shift-input-group">
                    <label class="input-label">Longitude Shift</label>
                    <InputNumber
                        v-model="longitudeShift"
                        :minFractionDigits="6"
                        :maxFractionDigits="6"
                        :allowEmpty="false"
                        placeholder="e.g., 45.678901"
                        class="shift-input"
                    />
                  </div>
                </div>
                <small class="form-help-text">
                  A random shift has been generated automatically. You can regenerate it or modify the values manually.
                  The same shift is applied to all coordinates to preserve relative positions.
                </small>
              </div>

              <!-- Options -->
              <div class="form-group">
                <label class="form-label">Export Options</label>
                <div class="checkbox-group">
                  <div class="checkbox-item">
                    <Checkbox v-model="includeConfiguration" :binary="true" inputId="includeConfig" />
                    <label for="includeConfig" class="checkbox-label">
                      Include Timeline Configuration
                    </label>
                  </div>
                </div>
                <small class="form-help-text">
                  Timeline configuration helps reproduce the exact timeline generation parameters used.
                </small>
              </div>

              <!-- Export Button -->
              <div class="form-actions">
                <Button
                    label="Export Debug Data"
                    icon="pi pi-download"
                    :loading="isExporting"
                    :disabled="!isFormValid"
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
                  <span>Export Failed</span>
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
              <h3 class="info-title">What Will Be Exported?</h3>
              <p class="info-description">
                The export will create a ZIP file containing:
              </p>
              <ul class="info-list">
                <li>
                  <i class="pi pi-check-circle"></i>
                  <strong>metadata.json</strong> - Export metadata (date range, counts, version)
                </li>
                <li>
                  <i class="pi pi-check-circle"></i>
                  <strong>gps_data.json</strong> - All GPS points in OwnTracks format with shifted coordinates
                </li>
                <li>
                  <i class="pi pi-check-circle"></i>
                  <strong>timeline_config.json</strong> - Your complete timeline configuration (if selected)
                </li>
                <li>
                  <i class="pi pi-check-circle"></i>
                  <strong>favorite_locations.json</strong> - Anonymized favorite locations with shifted coordinates
                </li>
                <li>
                  <i class="pi pi-check-circle"></i>
                  <strong>favorite_areas.json</strong> - Anonymized favorite areas with shifted boundaries
                </li>
              </ul>
              <p class="info-note">
                <i class="pi pi-info-circle"></i>
                All coordinates are shifted by the same offset. Favorite location names are anonymized
                (e.g., "Home" becomes "Location 1"). This preserves timeline structure while protecting privacy.
                The coordinate shift values are NOT included in the export for privacy protection.
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
import { useToast } from 'primevue/usetoast'
import { useTimezone } from '@/composables/useTimezone'
import AppLayout from '@/components/ui/layout/AppLayout.vue'
import PageContainer from '@/components/ui/layout/PageContainer.vue'
import Card from 'primevue/card'
import Calendar from 'primevue/calendar'
import Button from 'primevue/button'
import InputNumber from 'primevue/inputnumber'
import Checkbox from 'primevue/checkbox'
import apiService from "@/utils/apiService";

const toast = useToast()
const timezone = useTimezone()

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
  if (!startDate.value || !endDate.value) {
    validationError.value = 'Please select start and end dates'
    return false
  }

  if (startDate.value > endDate.value) {
    validationError.value = 'Start date must be before end date'
    return false
  }

  if (startDate.value > new Date()) {
    validationError.value = 'Start date cannot be in the future'
    return false
  }

  if (latitudeShift.value === null || longitudeShift.value === null) {
    validationError.value = 'Please generate or enter coordinate shift values'
    return false
  }

  validationError.value = null
  return true
})

// Export debug data
const exportDebugData = async () => {
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
      startDate: dateRange.start,  // Already UTC ISO string (start of day in user's timezone)
      endDate: dateRange.end,      // Already UTC ISO string (end of day in user's timezone)
      latitudeShift: latitudeShift.value,
      longitudeShift: longitudeShift.value,
      includeConfiguration: includeConfiguration.value
    }

    const response = await apiService.post('/export/debug/create', requestData, {
      responseType: 'blob'
    })

    // Ensure we have valid blob data
    if (!response.data || !(response.data instanceof Blob)) {
      throw new Error('Invalid response data received')
    }

    // Create download link (response.data is already a Blob)
    const url = window.URL.createObjectURL(response.data)
    const link = document.createElement('a')
    link.href = url

    // Generate filename
    const timestamp = new Date().toISOString().replace(/[:.]/g, '-').split('T')[0]
    link.download = `geopulse-debug-${timestamp}.zip`

    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    window.URL.revokeObjectURL(url)

    toast.add({
      severity: 'success',
      summary: 'Export Successful',
      detail: 'Debug data has been downloaded as ZIP file',
      life: 5000
    })
  } catch (error) {
    console.error('Failed to export debug data:', error)

    let errorMessage = 'Failed to export debug data'

    // Handle different error response formats
    if (error.response?.data) {
      // Check if error.response.data is a Blob (from responseType: 'blob')
      if (error.response.data instanceof Blob) {
        // If we got a Blob error response, it's actually JSON
        try {
          const text = await error.response.data.text()
          const errorData = JSON.parse(text)
          if (errorData.error?.message) {
            errorMessage = errorData.error.message
          }
        } catch (e) {
          console.error('Failed to parse blob error:', e)
        }
      } else if (error.response.data.error?.message) {
        // Regular JSON error response
        errorMessage = error.response.data.error.message
      }
    } else if (error.message) {
      errorMessage = error.message
    }

    // Set the export error for persistent display
    exportError.value = errorMessage

    // Also show toast notification
    toast.add({
      severity: 'error',
      summary: 'Export Failed',
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

.info-banner {
  margin-bottom: 2rem;
}

.info-banner.warning {
  border-left: 4px solid var(--primary-color);
}

.info-banner .banner-content {
  display: flex;
  gap: 1rem;
  align-items: flex-start;
}

.info-banner .banner-content .banner-icon {
  font-size: 1.5rem;
  color: var(--primary-color);
  flex-shrink: 0;
}

.info-banner .banner-content .banner-text .banner-title {
  font-size: 1.1rem;
  font-weight: 600;
  margin: 0 0 0.5rem 0;
  color: var(--text-color);
}

.info-banner .banner-content .banner-text .banner-description {
  margin: 0;
  color: var(--text-color-secondary);
  line-height: 1.5;
}

.export-config-card {
  margin-bottom: 2rem;
}

.export-config-card .config-section .section-title {
  font-size: 1.3rem;
  font-weight: 600;
  margin: 0 0 1.5rem 0;
  color: var(--text-color);
}

.export-config-card .config-section .form-group {
  margin-bottom: 1.5rem;
}

.export-config-card .config-section .form-group .form-label {
  display: block;
  font-weight: 600;
  margin-bottom: 0.5rem;
  color: var(--text-color);
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
  color: var(--text-color-secondary);
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
  color: var(--text-color-secondary);
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
  color: var(--text-color);
}

.export-config-card .config-section .form-group .form-help-text {
  display: block;
  margin-top: 0.5rem;
  color: var(--text-color-secondary);
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
  background: var(--red-50);
  border: 1px solid var(--red-200);
  border-radius: 6px;
  color: var(--red-700);
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
  .debug-export-page {
    padding: 1rem 0;
  }

  .page-header .header-text .page-title {
    font-size: 1.5rem;
  }

  .export-config-card .config-section .form-group .shift-inputs {
    grid-template-columns: 1fr;
  }

  .export-config-card .config-section .form-actions .export-button {
    width: 100%;
  }
}
</style>
