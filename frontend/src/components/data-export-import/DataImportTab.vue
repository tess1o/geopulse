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
                <label :for="`import-${format.value}`" class="format-info">
                  <div class="format-label">
                    {{ format.label }}
                  </div>
                  <p class="format-description">{{ format.description }}</p>
                </label>
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

          <!-- CSV Format Documentation (shown when CSV is selected, before file upload) -->
          <div v-if="importFormat === 'csv'" class="csv-format-docs">
            <h4 class="csv-docs-title">CSV Format Specification</h4>

            <div class="csv-download-template">
              <Button
                label="Download CSV Template"
                icon="pi pi-download"
                size="small"
                outlined
                @click="downloadCsvTemplate"
                class="template-button"
              />
            </div>

            <div class="csv-docs-section">
              <h5 class="csv-docs-subtitle">Required Fields</h5>
              <ul class="csv-field-list">
                <li><strong>timestamp</strong>: ISO-8601 format (e.g., 2024-01-15T10:30:00Z)</li>
                <li><strong>latitude</strong>: Decimal degrees, -90 to 90</li>
                <li><strong>longitude</strong>: Decimal degrees, -180 to 180</li>
              </ul>
            </div>

            <div class="csv-docs-section">
              <h5 class="csv-docs-subtitle">Optional Fields</h5>
              <ul class="csv-field-list">
                <li><strong>accuracy</strong>: GPS accuracy in meters</li>
                <li><strong>velocity</strong>: Speed in km/h</li>
                <li><strong>altitude</strong>: Altitude in meters</li>
                <li><strong>battery</strong>: Battery percentage (0-100)</li>
                <li><strong>device_id</strong>: Device identifier (text)</li>
                <li><strong>source_type</strong>: Data source (CSV, OWNTRACKS, GPX, GEOJSON, etc.)</li>
              </ul>
            </div>

            <div class="csv-docs-section">
              <h5 class="csv-docs-subtitle">Example CSV</h5>
              <pre class="csv-example-code">timestamp,latitude,longitude,accuracy,velocity,altitude,battery,device_id,source_type
2024-01-15T10:30:00Z,37.7749,-122.4194,10.5,5.2,100.0,85.0,device123,CSV
2024-01-15T10:35:00Z,37.7750,-122.4195,8.3,12.8,105.2,84.8,,CSV
2024-01-15T10:40:00Z,37.7751,-122.4196,,15.5,,,device789,GPX</pre>
            </div>

            <div class="csv-docs-section">
              <h5 class="csv-docs-subtitle">Format Rules</h5>
              <ul class="csv-field-list">
                <li>UTF-8 encoding required</li>
                <li>Header row required (case-sensitive)</li>
                <li>Empty optional fields: leave blank (e.g., ,,)</li>
                <li>Decimal separator: period (.)</li>
                <li>Timestamps must be UTC (ending with Z)</li>
              </ul>
            </div>
          </div>

          <!-- Import Options -->
          <div v-if="selectedFile" class="form-section">
            <h3 class="form-section-title">Import Options</h3>
            <div class="warning-message">
              <i class="pi pi-info-circle" style="margin-right: 0.5rem;"></i>
              <strong>Note: When importing data for the first time, choose “Replace existing data in time range” to make
                the process faster.</strong>
            </div>

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
                  <i class="pi pi-info-circle" style="margin-right: 0.5rem;"></i>
                  <span> {{ getFormatInfoMessage() }}</span>
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
                  When enabled, existing data in the time range being imported will be deleted before importing new
                  data.
                  This is faster than merging and ensures clean data replacement.
                </div>
                <div v-if="clearDataBeforeImport" class="warning-message">
                  <i class="pi pi-exclamation-triangle" style="margin-right: 0.5rem;"></i>
                  <strong>Warning:</strong> This will permanently delete existing data in the import time range. Make
                  sure you have backups if needed.
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

    <!-- Upload Progress (shown during file upload) -->
    <Card v-if="isUploading" class="upload-progress-card">
      <template #content>
        <div class="upload-status">
          <div class="upload-header">
            <h3 class="upload-title">Uploading File</h3>
            <Tag value="Uploading" severity="info" icon="pi pi-spin pi-spinner"/>
          </div>

          <div class="upload-details">
            <div class="upload-detail">
              <span class="detail-label">File:</span>
              <span class="detail-value">{{ selectedFile?.name || 'Unknown' }}</span>
            </div>

            <div class="upload-detail" v-if="selectedFile">
              <span class="detail-label">Size:</span>
              <span class="detail-value">{{ getFileSizeDisplay(selectedFile.size) }}</span>
            </div>

            <div class="upload-detail">
              <span class="detail-label">Upload Progress:</span>
              <div class="detail-value">
                <ProgressBar :value="uploadProgress" class="upload-progress-bar"/>
                <div class="progress-info">
                  <span class="progress-text">{{ uploadProgress }}%</span>
                  <span class="progress-phase">{{ getUploadPhaseDescription() }}</span>
                </div>
              </div>
            </div>
          </div>

          <div class="upload-info-message">
            <i class="pi pi-info-circle"></i>
            <span>Large files may take several minutes to upload depending on your network speed.</span>
          </div>
        </div>
      </template>
    </Card>

    <!-- Current Import Job Status -->
    <Card v-if="currentImportJob && !isUploading" class="job-status-card">
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

            <!-- Timeline Generation Progress (Compact Inline Display) -->
            <div v-if="currentImportJob.timelineJobId && currentImportJob.status === 'processing' && currentImportJob.progress >= 70"
                 class="timeline-progress-container">
              <div class="timeline-progress-header">
                <h4 class="timeline-progress-title">Timeline Generation</h4>
                <span v-if="timelineJobProgress" class="timeline-progress-percentage">
                  {{ timelineJobProgress.progressPercentage }}%
                </span>
              </div>

              <ProgressBar
                  v-if="timelineJobProgress"
                  :value="timelineJobProgress.progressPercentage"
                  :showValue="false"
                  class="timeline-progress-bar"
              />

              <div class="timeline-progress-details">
                <div v-if="timelineJobProgress" class="timeline-current-step">
                  <i class="pi pi-sync pi-spin" style="font-size: 0.875rem; margin-right: 0.5rem;"></i>
                  <span>{{ timelineJobProgress.currentStep }}</span>
                </div>
                <div v-else class="timeline-current-step">
                  <i class="pi pi-sync pi-spin" style="font-size: 0.875rem; margin-right: 0.5rem;"></i>
                  <span>Initializing timeline generation...</span>
                </div>

                <div v-if="timelineKeyMetric" class="timeline-key-metric">
                  <i :class="['pi', timelineKeyMetric.icon]"></i>
                  <span>{{ timelineKeyMetric.text }}</span>
                </div>
              </div>

              <Button
                  label="View Detailed Progress"
                  icon="pi pi-external-link"
                  severity="info"
                  outlined
                  size="small"
                  @click="openTimelineJobDetails(currentImportJob.timelineJobId)"
                  class="timeline-details-btn"
              />
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
import {ref, computed, watch, onMounted, onUnmounted} from 'vue'
import {storeToRefs} from 'pinia'
import {useRouter} from 'vue-router'
import {useToast} from 'primevue/usetoast'
import {useTimezone} from '@/composables/useTimezone'
import {useExportImportStore} from '@/stores/exportImport'
import {useTimelineJobProgress} from '@/composables/useTimelineJobProgress'

const router = useRouter()
const timezone = useTimezone()
const toast = useToast()
const exportImportStore = useExportImportStore()

// Timeline job progress tracking
const {jobProgress: timelineJobProgress, startPolling: startTimelinePolling, stopPolling: stopTimelinePolling} = useTimelineJobProgress()

// Import polling canceller
let importPollCanceller = null

// Store refs
const {
  importJobs,
  currentImportJob,
  isImporting,
  hasActiveImportJob,
  uploadProgress,
  isUploading
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
    supportsDataTypeSelection: true
  },
  {
    label: 'OwnTracks',
    value: 'owntracks',
    description: 'Import from OwnTracks export file',
    fileExtensions: ['.json'],
    acceptedFormats: '.json',
    uploadFunction: 'uploadOwnTracksImportFile',
    supportsDataTypeSelection: false
  },
  {
    label: 'Google Timeline',
    value: 'google-timeline',
    description: 'Import from Google Timeline/Takeout JSON files',
    fileExtensions: ['.json'],
    acceptedFormats: '.json',
    uploadFunction: 'uploadGoogleTimelineImportFile',
    supportsDataTypeSelection: false
  },
  {
    label: 'GPX',
    value: 'gpx',
    description: 'Import from GPX track/route files or ZIP archives containing multiple GPX files',
    fileExtensions: ['.gpx', '.zip'],
    acceptedFormats: '.gpx,.zip',
    uploadFunction: 'uploadGpxImportFile',
    supportsDataTypeSelection: false
  },
  {
    label: 'GeoJSON',
    value: 'geojson',
    description: 'Import from GeoJSON files (Point or LineString features)',
    fileExtensions: ['.json', '.geojson'],
    acceptedFormats: '.json,.geojson',
    uploadFunction: 'uploadGeoJsonImportFile',
    supportsDataTypeSelection: false
  },
  {
    label: 'CSV',
    value: 'csv',
    description: 'Import GPS data from CSV file',
    fileExtensions: ['.csv'],
    acceptedFormats: '.csv',
    uploadFunction: 'uploadCsvImportFile',
    supportsDataTypeSelection: false
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
      importPollCanceller = exportImportStore.pollJobStatus(currentImportJob.value.importJobId, false)
    }
  } catch (error) {
    console.error('Import error:', error)

    const errorMessage = error.response?.data?.error?.message || error.message || 'Failed to start import job'

    if (error.response?.data?.error?.code === 'ACTIVE_JOB_EXISTS') {
      toast.add({
        severity: 'warn',
        summary: 'Import in Progress',
        detail: errorMessage,
        life: 5000
      })
    } else {
      toast.add({
        severity: 'error',
        summary: 'Import Failed',
        detail: errorMessage,
        life: 5000
      })
    }
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

const downloadCsvTemplate = async () => {
  try {
    await exportImportStore.downloadCsvTemplate()
    toast.add({
      severity: 'success',
      summary: 'Template Downloaded',
      detail: 'CSV template has been downloaded successfully',
      life: 3000
    })
  } catch (error) {
    console.error('Error downloading CSV template:', error)
    toast.add({
      severity: 'error',
      summary: 'Download Failed',
      detail: 'Failed to download CSV template',
      life: 3000
    })
  }
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
  return `Only ${config.label} files (${extensions}) are supported`
}

const getFormatInfoMessage = () => {
  const messages = {
    'owntracks': 'OwnTracks import will add GPS location data to your GeoPulse timeline.',
    'google-timeline': 'Google Timeline import will add GPS location data and activities from your Google Takeout data.',
    'gpx': 'GPX import will add GPS track and route data to your GeoPulse timeline. You can import a single GPX file or a ZIP archive containing multiple GPX files.',
    'geojson': 'GeoJSON import will add GPS location data from GeoJSON Point and LineString features to your GeoPulse timeline.',
    'csv': 'CSV import will add GPS location data from a structured CSV file to your GeoPulse timeline. Use the CSV template below to prepare your data.'
  }
  return messages[importFormat.value] || `${getCurrentFormatConfig().label} import will add location data to your GeoPulse timeline.`
}

const getUploadPhaseDescription = () => {
  const progress = uploadProgress.value || 0

  if (progress < 10) {
    return 'Starting upload...'
  } else if (progress < 50) {
    return 'Uploading file to server...'
  } else if (progress < 90) {
    return 'Upload in progress...'
  } else if (progress < 100) {
    return 'Finalizing upload...'
  } else {
    return 'Processing file...'
  }
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

const openTimelineJobDetails = (timelineJobId) => {
  if (timelineJobId) {
    // Open in new tab
    const url = router.resolve(`/app/timeline/jobs/${timelineJobId}`).href
    window.open(url, '_blank')
  }
}

// Extract timeline job ID as computed to avoid watch triggering on every poll update
const currentTimelineJobId = computed(() => currentImportJob.value?.timelineJobId)
const currentImportStatus = computed(() => currentImportJob.value?.status)

// Watch for timeline job ID changes to start/stop polling
watch(currentTimelineJobId, (newTimelineJobId, oldTimelineJobId) => {
  // Only start polling when ID first appears (null -> uuid)
  if (newTimelineJobId && !oldTimelineJobId && currentImportStatus.value === 'processing') {
    // Start polling timeline job progress
    startTimelinePolling(newTimelineJobId)
  } else if (!newTimelineJobId && oldTimelineJobId) {
    // Stop polling if timeline job ID is removed
    stopTimelinePolling()
  }
}, { immediate: true })

// Stop polling when import completes
watch(() => currentImportJob.value?.status, (newStatus) => {
  if (newStatus === 'completed' || newStatus === 'failed') {
    stopTimelinePolling()
  }
})

// Cleanup on unmount
onUnmounted(() => {
  // Cancel import polling
  if (importPollCanceller) {
    importPollCanceller.cancel()
    importPollCanceller = null
  }

  // Stop timeline polling
  stopTimelinePolling()
})

// Computed property for timeline progress key metric
const timelineKeyMetric = computed(() => {
  if (!timelineJobProgress.value?.details) return null

  const details = timelineJobProgress.value.details

  // GPS loading phase
  if (details.gpsPointsLoaded !== undefined && details.totalGpsPoints) {
    return {
      icon: 'pi-map-marker',
      text: `${details.gpsPointsLoaded.toLocaleString()} / ${details.totalGpsPoints.toLocaleString()} GPS points loaded`
    }
  }

  // Geocoding phase
  if (details.totalLocations !== undefined) {
    const resolved = details.totalResolved || 0
    const pending = details.externalPending || 0
    const pendingText = pending > 0 ? ` (${pending} pending)` : ''
    return {
      icon: 'pi-globe',
      text: `${resolved} / ${details.totalLocations} locations geocoded${pendingText}`
    }
  }

  return null
})

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

// On component mount, fetch import jobs and resume polling if there's an active job
onMounted(async () => {
  try {
    await exportImportStore.fetchImportJobs()

    // Find the most recent active import job and set it as current
    const activeJob = importJobs.value.find(job =>
        ['processing', 'validating'].includes(job.status)
    )

    if (activeJob) {
      console.log('Found active import job on mount:', activeJob.importJobId)
      exportImportStore.setCurrentImportJob(activeJob)
      importPollCanceller = exportImportStore.pollJobStatus(activeJob.importJobId, false)
    } else if (currentImportJob.value && ['processing', 'validating'].includes(currentImportJob.value.status)) {
      // Fallback: if currentImportJob is set but not in the jobs list, poll it anyway
      console.log('Resuming polling for current import job:', currentImportJob.value.importJobId)
      importPollCanceller = exportImportStore.pollJobStatus(currentImportJob.value.importJobId, false)
    }
  } catch (error) {
    console.error('Failed to fetch import jobs on mount:', error)
  }
})
</script>

<style scoped>
/* Import-specific styles - shared styles are in DataExportImportPage.vue */
.import-form-card,
.import-history-card,
.upload-progress-card {
  margin-bottom: 2rem;
}

/* Upload Progress */
.upload-status {
  display: flex;
  flex-direction: column;
  gap: 1.5rem;
}

.upload-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.upload-title {
  font-size: 1.25rem;
  font-weight: 600;
  color: var(--gp-text-primary);
  margin: 0;
}

.upload-details {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.upload-detail {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.upload-progress-bar {
  margin-top: 0.5rem;
}

.upload-info-message {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.75rem;
  background: var(--gp-surface-light);
  border-radius: var(--gp-radius-medium);
  color: var(--gp-text-secondary);
  font-size: 0.9rem;
}

.upload-info-message i {
  color: var(--gp-primary-500);
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

/* Timeline Progress (Compact Inline) */
.timeline-progress-container {
  margin-top: 1rem;
  padding: 1rem;
  background: var(--gp-surface-light);
  border: 1px solid var(--gp-border-light);
  border-radius: var(--gp-radius-medium);
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.timeline-progress-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.timeline-progress-title {
  font-size: 0.95rem;
  font-weight: 600;
  color: var(--gp-text-primary);
  margin: 0;
}

.timeline-progress-percentage {
  font-size: 0.95rem;
  font-weight: 600;
  color: var(--gp-primary-color);
}

.timeline-progress-bar {
  margin: 0.25rem 0;
}

.timeline-progress-details {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.timeline-current-step {
  display: flex;
  align-items: center;
  font-size: 0.9rem;
  color: var(--gp-text-primary);
  font-weight: 500;
}

.timeline-key-metric {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  font-size: 0.875rem;
  color: var(--gp-text-secondary);
  padding-left: 1.375rem; /* Align with step text (icon width + gap) */
}

.timeline-key-metric i {
  color: var(--gp-primary-color);
  font-size: 0.875rem;
  flex-shrink: 0;
}

.timeline-details-btn {
  align-self: flex-start;
  margin-top: 0.25rem;
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

/* CSV Format Documentation Styles */
.csv-format-docs {
  margin-top: 1.5rem;
  padding: 1.5rem;
  background: var(--gp-surface-light);
  border: 1px solid var(--gp-border-light);
  border-radius: var(--gp-radius-medium);
}

.csv-docs-title {
  font-size: 1rem;
  font-weight: 600;
  color: var(--gp-text-primary);
  margin: 0 0 1rem 0;
}

.csv-download-template {
  margin-bottom: 1.5rem;
}

.template-button {
  width: 100%;
  max-width: 250px;
}

.csv-docs-section {
  margin-bottom: 1.5rem;
}

.csv-docs-section:last-child {
  margin-bottom: 0;
}

.csv-docs-subtitle {
  font-size: 0.9rem;
  font-weight: 600;
  color: var(--gp-text-primary);
  margin: 0 0 0.75rem 0;
}

.csv-field-list {
  list-style: none;
  padding: 0;
  margin: 0;
}

.csv-field-list li {
  padding: 0.5rem 0;
  color: var(--gp-text-secondary);
  font-size: 0.875rem;
  line-height: 1.5;
}

.csv-field-list li strong {
  color: var(--gp-text-primary);
  font-family: monospace;
  font-size: 0.9em;
}

.csv-example-code {
  background: var(--gp-surface-0);
  border: 1px solid var(--gp-border-light);
  border-radius: var(--gp-radius-small);
  padding: 1rem;
  font-family: 'Courier New', Courier, monospace;
  font-size: 0.8rem;
  line-height: 1.5;
  color: var(--gp-text-primary);
  overflow-x: auto;
  margin: 0;
}

/* Dark Mode */
:root[class*="dark"] .csv-format-docs {
  background: var(--surface-ground);
  border-color: var(--gp-border-dark);
}

:root[class*="dark"] .csv-example-code {
  background: var(--surface-800);
  border-color: var(--gp-border-dark);
}

@media (max-width: 768px) {
  .csv-format-docs {
    padding: 1rem;
  }

  .template-button {
    width: 100%;
    max-width: none;
  }

  .csv-example-code {
    font-size: 0.7rem;
  }
}
</style>
