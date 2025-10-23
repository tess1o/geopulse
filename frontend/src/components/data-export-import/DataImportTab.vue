<template>
  <div class="tab-section">
    <!-- Import Form -->
    <Card class="import-form-card">
      <template #content>
        <div class="import-form">
          <!-- Import Format Selection -->
          <div class="form-section">
            <h3 class="form-section-title">Import Format</h3>
            <div class="format-options">
              <div
                  v-for="format in importFormatOptions"
                  :key="format.value"
                  class="format-option"
                  :class="{ 'selected': importFormat === format.value }"
              >
                <RadioButton
                    v-model="importFormat"
                    :inputId="`import-${format.value}`"
                    :value="format.value"
                    class="format-radio"
                />
                <div class="format-info">
                  <label :for="`import-${format.value}`" class="format-label">
                    {{ format.label }}
                  </label>
                  <p class="format-description">{{ format.description }}</p>
                </div>
              </div>
            </div>
          </div>

          <!-- File Upload -->
          <div class="form-section">
            <h3 class="form-section-title">Select Export File</h3>
            <div class="file-upload-container">
              <FileUpload
                  ref="fileUpload"
                  mode="basic"
                  :accept="getCurrentFormatConfig().acceptedFormats"
                  :maxFileSize="getCurrentFormatConfig().maxFileSizeMB * 1024 * 1024"
                  @select="onFileSelect"
                  @clear="onFileClear"
                  chooseLabel="Choose Export File"
                  class="file-uploader"
                  :auto="false"
              />
            </div>
            <small class="upload-note">
              {{ getUploadNote() }}
            </small>
          </div>

          <!-- Import Options -->
          <div v-if="selectedFile" class="form-section">
            <h3 class="form-section-title">Import Options</h3>

            <div class="import-options">
              <div v-if="getCurrentFormatConfig().supportsDataTypeSelection" class="option-group">
                <div class="option-group-header">
                  <label class="option-label">Data types to import:</label>
                  <Button
                    :label="importOptions.dataTypes.length === availableDataTypes.length ? 'Deselect All' : 'Select All'"
                    outlined
                    size="small"
                    @click="toggleAllImportDataTypes"
                    class="select-all-button"
                  />
                </div>
                <div class="timeline-info">
                  <i class="pi pi-info-circle"></i>
                  <span><strong>Timeline Data:</strong> Will be automatically regenerated from your GPS data after import</span>
                </div>
                <div class="import-data-types">
                  <div
                      v-for="dataType in availableDataTypes"
                      :key="dataType.key"
                      class="import-data-type"
                  >
                    <Checkbox
                        v-model="importOptions.dataTypes"
                        :inputId="`import-${dataType.key}`"
                        :value="dataType.key"
                    />
                    <div class="import-type-info">
                      <label :for="`import-${dataType.key}`" class="import-type-label">
                        <i :class="dataType.icon" class="import-type-icon"></i>
                        {{ dataType.label }}
                      </label>
                      <p class="import-type-description">{{ dataType.description }}</p>
                    </div>
                  </div>
                </div>
              </div>

              <div v-if="!getCurrentFormatConfig().supportsDataTypeSelection" class="option-group">
                <div class="format-info">
                  <i class="pi pi-info-circle"></i>
                  <span>{{ getFormatInfoMessage() }}</span>
                </div>
              </div>

              <div class="option-group">
                <div class="checkbox-option">
                  <Checkbox
                      v-model="enableDateFilter"
                      inputId="dateFilter"
                      :binary="true"
                  />
                  <label for="dateFilter" class="option-label">
                    Import only data within date range
                  </label>
                </div>
              </div>

              <div class="option-group">
                <div class="checkbox-option">
                  <Checkbox
                      v-model="clearDataBeforeImport"
                      inputId="clearDataBeforeImport"
                      :binary="true"
                  />
                  <label for="clearDataBeforeImport" class="option-label">
                    Replace existing data in time range
                  </label>
                </div>
                <div class="option-description">
                  <i class="pi pi-info-circle" style="margin-right: 0.5rem; color: var(--gp-primary-500);"></i>
                  When enabled, existing data in the time range being imported will be deleted before importing new data.
                  This is faster than merging and ensures clean data replacement.
                </div>
                <div v-if="clearDataBeforeImport" class="warning-message">
                  <i class="pi pi-exclamation-triangle" style="margin-right: 0.5rem;"></i>
                  <strong>Warning:</strong> This will permanently delete existing data in the import time range. Make sure you have backups if needed.
                </div>
              </div>

              <div v-if="enableDateFilter" class="date-filter-controls">
                  <div class="date-control">
                    <label for="importStartDate" class="date-label">Start Date</label>
                    <Calendar
                        id="importStartDate"
                        v-model="importStartDate"
                        dateFormat="yy-mm-dd"
                        placeholder="Select start date"
                        showIcon
                        class="date-picker"
                    />
                  </div>
                  <div class="date-control">
                    <label for="importEndDate" class="date-label">End Date</label>
                    <Calendar
                        id="importEndDate"
                        v-model="importEndDate"
                        dateFormat="yy-mm-dd"
                        placeholder="Select end date"
                        showIcon
                        class="date-picker"
                    />
                  </div>
                </div>
              </div>
          </div>

          <!-- Import Actions -->
          <div class="form-actions">
            <Button
                label="Start Import"
                icon="pi pi-upload"
                @click="startImport"
                :loading="isImporting || hasActiveImportJob"
                :disabled="!canStartImport"
                class="import-button"
            />
            <div class="import-info">
              <small class="import-note">
                Import process may take several minutes depending on file size.
              </small>
            </div>
          </div>
        </div>
      </template>
    </Card>

    <!-- Current Import Job Status -->
    <Card v-if="currentImportJob" class="job-status-card">
      <template #content>
        <div class="job-status">
          <div class="job-header">
            <h3 class="job-title">Current Import Job</h3>
            <Tag
                :value="getStatusDisplayInfo(currentImportJob.status).label"
                :severity="getStatusDisplayInfo(currentImportJob.status).severity"
                :icon="getStatusDisplayInfo(currentImportJob.status).icon"
            />
          </div>

          <div class="job-details">
            <div class="job-detail">
              <span class="detail-label">File:</span>
              <span class="detail-value">{{ currentImportJob.uploadedFileName }}</span>
            </div>

            <div class="job-detail" v-if="currentImportJob.importedDataTypes">
              <span class="detail-label">Data Types:</span>
              <span class="detail-value">
                {{ currentImportJob.importedDataTypes?.map(getDataTypeDisplayName).join(', ') }}
              </span>
            </div>

            <div class="job-detail" v-if="currentImportJob.progress !== undefined">
              <span class="detail-label">Progress:</span>
              <div class="detail-value">
                <ProgressBar :value="currentImportJob.progress" class="job-progress"/>
                <div class="progress-info">
                  <span class="progress-text">{{ currentImportJob.progress }}%</span>
                  <span class="progress-phase">{{ getProgressPhaseDescription(currentImportJob) }}</span>
                </div>
              </div>
            </div>

            <div v-if="currentImportJob.importSummary && currentImportJob.status === 'completed'"
                 class="import-summary">
              <h4 class="summary-title">Import Summary</h4>
              <div class="summary-items">
                <div v-if="currentImportJob.importSummary.rawGpsPoints" class="summary-item">
                  <i class="pi pi-map-marker"></i>
                  <span>{{ currentImportJob.importSummary.rawGpsPoints.toLocaleString() }} GPS points</span>
                </div>
                <div v-if="currentImportJob.importSummary.timelineItems" class="summary-item">
                  <i class="pi pi-clock"></i>
                  <span>{{
                      currentImportJob.importSummary.timelineItems.toLocaleString()
                    }} timeline items</span>
                </div>
                <div v-if="currentImportJob.importSummary.favoriteLocations" class="summary-item">
                  <i class="pi pi-heart"></i>
                  <span>{{ currentImportJob.importSummary.favoriteLocations.toLocaleString() }} favorite locations</span>
                </div>
                <div v-if="currentImportJob.importSummary.locationSources" class="summary-item">
                  <i class="pi pi-mobile"></i>
                  <span>{{
                      currentImportJob.importSummary.locationSources.toLocaleString()
                    }} location sources</span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </template>
    </Card>

    <!-- Import History (hidden for now) -->
    <Card class="import-history-card" style="display: none;">
      <template #content>
        <div class="import-history">
          <div class="history-header">
            <h3 class="history-title">Import History</h3>
            <Button
                label="Refresh"
                icon="pi pi-refresh"
                outlined
                size="small"
                @click="refreshImportJobs"
            />
          </div>

          <div v-if="importJobs.length === 0" class="empty-state">
            <i class="pi pi-file-import empty-icon"></i>
            <p class="empty-text">No import jobs found</p>
          </div>

          <div v-else class="import-list">
            <div
                v-for="job in importJobs"
                :key="job.importJobId"
                class="import-item"
            >
              <div class="import-item-info">
                <div class="import-item-header">
                  <span class="import-item-date">
                    {{ formatDate(job.createdAt) }}
                  </span>
                  <Tag
                      :value="getStatusDisplayInfo(job.status).label"
                      :severity="getStatusDisplayInfo(job.status).severity"
                      size="small"
                  />
                </div>
                <div class="import-item-details">
                  <span class="import-detail">{{ job.uploadedFileName }}</span>
                  <span v-if="job.fileSizeBytes" class="import-size">
                    {{ getFileSizeDisplay(job.fileSizeBytes) }}
                  </span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </template>
    </Card>
  </div>

  <!-- Toast -->
  <Toast/>
</template>

<script setup>
import {ref, computed, watch} from 'vue'
import {storeToRefs} from 'pinia'
import {useToast} from 'primevue/usetoast'
import {useTimezone} from '@/composables/useTimezone'
import {useExportImportStore} from '@/stores/exportImport'

const timezone = useTimezone()
const toast = useToast()
const exportImportStore = useExportImportStore()

// Store refs
const {
  importJobs,
  currentImportJob,
  isImporting,
  hasActiveImportJob
} = storeToRefs(exportImportStore)

// State
const importFormat = ref('geopulse')
const selectedFile = ref(null)
const fileUpload = ref(null)
const enableDateFilter = ref(false)
const clearDataBeforeImport = ref(false)
const importStartDate = ref(null)
const importEndDate = ref(null)
const importOptions = ref({
  dataTypes: ['rawgps', 'favorites', 'reversegeocodinglocation', 'locationsources', 'userinfo']
})

// Data types configuration
const availableDataTypes = ref([
  {
    key: 'rawgps',
    label: 'Raw GPS Data',
    description: 'All your location points with timestamps and accuracy',
    icon: 'pi pi-map-marker'
  },
  {
    key: 'favorites',
    label: 'Favorite Locations',
    description: 'Your saved favorite places and areas',
    icon: 'pi pi-heart'
  },
  {
    key: 'reversegeocodinglocation',
    label: 'Reverse Geocoding Data',
    description: 'Address information and place names for your locations',
    icon: 'pi pi-map'
  },
  {
    key: 'locationsources',
    label: 'Location Sources',
    description: 'Your configured GPS tracking apps and endpoints',
    icon: 'pi pi-mobile'
  },
  {
    key: 'userinfo',
    label: 'User Information',
    description: 'Your profile and preferences (excludes sensitive data)',
    icon: 'pi pi-user'
  }
])

// Import format options - extensible for future formats
const importFormatOptions = ref([
  {
    label: 'GeoPulse',
    value: 'geopulse',
    description: 'Import from GeoPulse export file',
    fileExtensions: ['.zip'],
    acceptedFormats: '.zip',
    uploadFunction: 'uploadImportFile',
    supportsDataTypeSelection: true,
    maxFileSizeMB: 2000
  },
  {
    label: 'OwnTracks',
    value: 'owntracks',
    description: 'Import from OwnTracks export file',
    fileExtensions: ['.json'],
    acceptedFormats: '.json',
    uploadFunction: 'uploadOwnTracksImportFile',
    supportsDataTypeSelection: false,
    maxFileSizeMB: 2000
  },
  {
    label: 'Google Timeline',
    value: 'google-timeline',
    description: 'Import from Google Timeline/Takeout JSON files',
    fileExtensions: ['.json'],
    acceptedFormats: '.json',
    uploadFunction: 'uploadGoogleTimelineImportFile',
    supportsDataTypeSelection: false,
    maxFileSizeMB: 2000
  },
  {
    label: 'GPX',
    value: 'gpx',
    description: 'Import from GPX track/route files',
    fileExtensions: ['.gpx'],
    acceptedFormats: '.gpx',
    uploadFunction: 'uploadGpxImportFile',
    supportsDataTypeSelection: false,
    maxFileSizeMB: 2000
  }
])

// Computed
const canStartImport = computed(() => {
  const hasValidFile = selectedFile.value
  const hasValidDateFilter = !enableDateFilter.value || (importStartDate.value && importEndDate.value)
  const formatConfig = getCurrentFormatConfig()

  // For formats that don't support data type selection, only check file and date filter
  if (!formatConfig.supportsDataTypeSelection) {
    return hasValidFile && hasValidDateFilter
  }

  // For formats that support data type selection, we need at least one data type selected
  return hasValidFile && importOptions.value.dataTypes.length > 0 && hasValidDateFilter
})

// Methods
const onFileSelect = (event) => {
  selectedFile.value = event.files[0]
}

const onFileClear = () => {
  selectedFile.value = null
}

const startImport = async () => {
  try {
    const options = {importFormat: importFormat.value}
    const formatConfig = getCurrentFormatConfig()

    // Only add data type options for formats that support it
    if (formatConfig.supportsDataTypeSelection) {
      options.dataTypes = importOptions.value.dataTypes
    }

    if (enableDateFilter.value && importStartDate.value && importEndDate.value) {
      options.dateRangeFilter = {
        startDate: importStartDate.value.toISOString(),
        endDate: importEndDate.value.toISOString()
      }
    }

    // Add clear data before import option
    options.clearDataBeforeImport = clearDataBeforeImport.value

    // Use the appropriate upload function based on format configuration
    const uploadFunction = exportImportStore[formatConfig.uploadFunction]
    if (!uploadFunction) {
      throw new Error(`Upload function '${formatConfig.uploadFunction}' not found`)
    }

    await uploadFunction(selectedFile.value, options)

    toast.add({
      severity: 'success',
      summary: 'Import Started',
      detail: 'Your import job has been created and is being processed',
      life: 5000
    })

    // Clear the file selection
    selectedFile.value = null
    if (fileUpload.value) {
      fileUpload.value.clear()
    }

    // Start polling for status updates
    if (currentImportJob.value) {
      exportImportStore.pollJobStatus(currentImportJob.value.importJobId, false)
    }
  } catch (error) {
    console.error('Import error:', error)
    toast.add({
      severity: 'error',
      summary: 'Import Failed',
      detail: error.message || 'Failed to start import job',
      life: 5000
    })
  }
}

const refreshImportJobs = async () => {
  try {
    await exportImportStore.fetchImportJobs()
  } catch (error) {
    console.error('Error refreshing import jobs:', error)
    toast.add({
      severity: 'error',
      summary: 'Refresh Failed',
      detail: 'Failed to refresh import jobs',
      life: 5000
    })
  }
}

const formatDate = (dateString) => {
  return timezone.format(dateString, 'YYYY-MM-DD HH:mm:ss')
}

// Store utility methods
const {getDataTypeDisplayName, getFileSizeDisplay, getStatusDisplayInfo} = exportImportStore

// Toggle all import data types
const toggleAllImportDataTypes = () => {
  if (importOptions.value.dataTypes.length === availableDataTypes.value.length) {
    // Deselect all
    importOptions.value.dataTypes = []
  } else {
    // Select all
    importOptions.value.dataTypes = availableDataTypes.value.map(dt => dt.key)
  }
}

// Helper methods to get current format configuration
const getCurrentFormatConfig = () => {
  return importFormatOptions.value.find(option => option.value === importFormat.value) || importFormatOptions.value[0]
}

const getUploadNote = () => {
  const config = getCurrentFormatConfig()
  const extensions = config.fileExtensions.join(', ')
  return `Only ${config.label} files (${extensions}) are supported. Maximum file size: ${config.maxFileSizeMB}MB`
}

const getFormatInfoMessage = () => {
  const messages = {
    'owntracks': 'OwnTracks import will add GPS location data to your GeoPulse timeline.',
    'google-timeline': 'Google Timeline import will add GPS location data and activities from your Google Takeout data.',
    'gpx': 'GPX import will add GPS track and route data to your GeoPulse timeline.'
  }
  return messages[importFormat.value] || `${getCurrentFormatConfig().label} import will add location data to your GeoPulse timeline.`
}

const getProgressPhaseDescription = (job) => {
  if (!job || job.status === 'completed') return ''

  // Use backend-provided progress message if available
  if (job.progressMessage) {
    return job.progressMessage
  }

  // Fallback to client-side logic for backwards compatibility
  const progress = job.progress || 0

  if (job.status === 'validating') {
    return 'Validating file format...'
  }

  if (job.status === 'processing') {
    if (progress < 25) {
      return 'Parsing import data...'
    } else if (progress < 95) {
      const mode = clearDataBeforeImport.value ? 'bulk inserting' : 'merging with existing data'
      return `Importing GPS points (${mode})...`
    } else {
      return 'Generating timeline...'
    }
  }

  return job.status
}

// Watch for import job completion
watch(() => currentImportJob.value?.status, (newStatus, oldStatus) => {
  if (oldStatus && oldStatus !== 'completed' && newStatus === 'completed') {
    toast.add({
      severity: 'success',
      summary: 'Import Completed',
      detail: 'Your data has been successfully imported!',
      life: 5000
    })
  }
})
</script>

<style scoped>
/* Import-specific styles - shared styles are in DataExportImportPage.vue */
.import-form-card,
.import-history-card {
  margin-bottom: 2rem;
}

/* File Upload */
.file-upload-container {
  display: flex;
  flex-direction: column;
  align-items: center;
  width: 100%;
  gap: 0.5rem;
}

.file-uploader {
  width: 100%;
  max-width: 400px;
}

.file-uploader :deep(.p-fileupload-choose) {
  margin: 0 auto;
  display: block;
}

.upload-note {
  color: var(--gp-text-secondary);
  margin-top: 0.5rem;
  text-align: center;
}

/* Import Options */
.import-options {
  display: flex;
  flex-direction: column;
  gap: 1.5rem;
}

.import-data-types {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
  gap: 1rem;
  margin-left: 0;
  margin-top: 0.5rem;
}

.import-data-type {
  display: flex;
  align-items: flex-start;
  gap: 0.75rem;
  padding: 1rem;
  border: 1px solid var(--gp-border-light);
  border-radius: var(--gp-radius-medium);
  background: var(--gp-surface-light);
  transition: all 0.2s ease;
}

.import-data-type:hover {
  border-color: var(--gp-primary);
  background: var(--gp-surface-hover);
}

.import-type-info {
  flex: 1;
}

.import-type-label {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  font-weight: 600;
  color: var(--gp-text-primary);
  margin-bottom: 0.25rem;
  cursor: pointer;
}

.import-type-icon {
  color: var(--gp-primary);
}

.import-type-description {
  font-size: 0.9rem;
  color: var(--gp-text-secondary);
  margin: 0;
  line-height: 1.4;
}

.date-filter-controls {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 1rem;
  margin-left: 0;
  margin-top: 0.75rem;
}

.warning-message {
  margin-top: 0.5rem;
  padding: 0.75rem;
  background-color: #fff3cd;
  border: 1px solid #ffeaa7;
  border-radius: 6px;
  color: #856404;
  font-size: 0.9rem;
  display: flex;
  align-items: center;
}

/* Import Summary */
.import-summary {
  margin-top: 1rem;
  padding: 1rem;
  background: var(--gp-surface-light);
  border-radius: var(--gp-radius-medium);
}

.summary-title {
  font-size: 1rem;
  font-weight: 600;
  color: var(--gp-text-primary);
  margin: 0 0 0.75rem 0;
}

.summary-items {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.summary-item {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  color: var(--gp-text-primary);
}

.summary-item i {
  color: var(--gp-primary);
  width: 1rem;
}

/* Import History */
.import-history {
  display: flex;
  flex-direction: column;
  gap: 1.5rem;
}

.import-list {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.import-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 1rem;
  border: 1px solid var(--gp-border-light);
  border-radius: var(--gp-radius-medium);
  background: var(--gp-surface-light);
}

.import-item-info {
  flex: 1;
}

.import-item-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 0.5rem;
}

.import-item-date {
  font-weight: 500;
  color: var(--gp-text-primary);
}

.import-item-details {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.import-detail {
  color: var(--gp-text-secondary);
  font-size: 0.9rem;
}

.import-size {
  color: var(--gp-text-tertiary);
  font-size: 0.8rem;
  font-weight: 500;
}

/* Responsive Design */
@media (max-width: 768px) {
  .import-data-types {
    grid-template-columns: 1fr;
    margin-left: 0;
  }

  .import-item {
    flex-direction: column;
    align-items: stretch;
    gap: 1rem;
  }
}

@media (max-width: 480px) {
  .date-filter-controls {
    margin-left: 0;
  }

  .import-item-header {
    flex-direction: column;
    align-items: flex-start;
    gap: 0.5rem;
  }

  .import-item-details {
    flex-direction: column;
    align-items: flex-start;
    gap: 0.25rem;
  }
}
</style>
