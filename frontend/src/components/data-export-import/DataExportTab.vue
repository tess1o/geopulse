<template>
  <div class="tab-section">
    <!-- Export Form -->
    <Card class="export-form-card">
      <template #content>
        <div class="export-form">
          <!-- Export Format Selection -->
          <div class="form-section">
            <h3 class="form-section-title">Export Format</h3>
            <div class="format-options">
              <div
                  v-for="format in exportFormatOptions"
                  :key="format.value"
                  class="format-option"
                  :class="{ 'selected': exportFormat === format.value }"
              >
                <RadioButton
                    v-model="exportFormat"
                    :inputId="format.value"
                    :value="format.value"
                    class="format-radio"
                />
                <div class="format-info">
                  <label :for="format.value" class="format-label">
                    {{ format.label }}
                  </label>
                  <p class="format-description">{{ format.description }}</p>
                </div>
              </div>
            </div>
          </div>

          <!-- Data Types Selection (only for GeoPulse format) -->
          <div v-if="exportFormat === 'geopulse'" class="form-section">
            <div class="form-section-header">
              <h3 class="form-section-title">Select Data Types</h3>
              <Button
                :label="selectedDataTypes.length === availableDataTypes.length ? 'Deselect All' : 'Select All'"
                outlined
                size="small"
                @click="toggleAllExportDataTypes"
                class="select-all-button"
              />
            </div>
            <div class="timeline-info">
              <i class="pi pi-info-circle"></i>
              <span><strong>Timeline Data:</strong> Will be automatically regenerated from your GPS data after import</span>
            </div>
            <div class="data-types-grid">
              <div
                  v-for="dataType in availableDataTypes"
                  :key="dataType.key"
                  class="data-type-option"
              >
                <Checkbox
                    v-model="selectedDataTypes"
                    :inputId="dataType.key"
                    :value="dataType.key"
                    class="data-type-checkbox"
                />
                <div class="data-type-info">
                  <label :for="dataType.key" class="data-type-label">
                    <i :class="dataType.icon" class="data-type-icon"></i>
                    {{ dataType.label }}
                  </label>
                  <p class="data-type-description">{{ dataType.description }}</p>
                </div>
              </div>
            </div>
          </div>

          <!-- GPX Export Options (only for GPX format) -->
          <div v-if="exportFormat === 'gpx'" class="form-section">
            <h3 class="form-section-title">GPX Export Options</h3>
            <div class="gpx-export-options">
              <div
                  class="gpx-option"
                  :class="{ 'selected': gpxExportMode === 'single' }"
                  @click="gpxExportMode = 'single'"
              >
                <RadioButton
                    v-model="gpxExportMode"
                    inputId="gpx-single"
                    value="single"
                    class="gpx-radio"
                />
                <div class="gpx-option-info">
                  <label for="gpx-single" class="gpx-option-label">
                    Single GPX File
                  </label>
                  <p class="gpx-option-description">
                    One GPX file containing all tracks (raw GPS + timeline trips) and waypoints (timeline stays)
                  </p>
                </div>
              </div>

              <div
                  class="gpx-option"
                  :class="{ 'selected': gpxExportMode === 'zip' }"
                  @click="gpxExportMode = 'zip'"
              >
                <RadioButton
                    v-model="gpxExportMode"
                    inputId="gpx-zip"
                    value="zip"
                    class="gpx-radio"
                />
                <div class="gpx-option-info">
                  <label for="gpx-zip" class="gpx-option-label">
                    ZIP Archive
                  </label>
                  <p class="gpx-option-description">
                    Multiple GPX files packaged in a ZIP archive
                  </p>
                </div>
              </div>
            </div>

            <!-- ZIP Grouping Options (shown when ZIP mode is selected) -->
            <div v-if="gpxExportMode === 'zip'" class="gpx-zip-grouping">
              <h4 class="grouping-title">ZIP Grouping Mode</h4>
              <div class="grouping-options">
                <div
                    class="grouping-option"
                    :class="{ 'selected': gpxZipGroupBy === 'individual' }"
                    @click="gpxZipGroupBy = 'individual'"
                >
                  <RadioButton
                      v-model="gpxZipGroupBy"
                      inputId="gpx-group-individual"
                      value="individual"
                      class="grouping-radio"
                  />
                  <div class="grouping-option-info">
                    <label for="gpx-group-individual" class="grouping-option-label">
                      Individual (One file per trip/stay)
                    </label>
                    <p class="grouping-option-description">
                      Each trip and stay gets its own GPX file
                    </p>
                  </div>
                </div>

                <div
                    class="grouping-option"
                    :class="{ 'selected': gpxZipGroupBy === 'daily' }"
                    @click="gpxZipGroupBy = 'daily'"
                >
                  <RadioButton
                      v-model="gpxZipGroupBy"
                      inputId="gpx-group-daily"
                      value="daily"
                      class="grouping-radio"
                  />
                  <div class="grouping-option-info">
                    <label for="gpx-group-daily" class="grouping-option-label">
                      Daily (One file per day)
                    </label>
                    <p class="grouping-option-description">
                      All trips and stays for each day grouped into one GPX file
                    </p>
                  </div>
                </div>
              </div>
            </div>
          </div>

          <!-- Date Range Selection -->
          <div class="form-section">
            <h3 class="form-section-title">Date Range</h3>
            <div class="date-range-controls">
              <div class="date-control">
                <label for="startDate" class="date-label">Start Date</label>
                <Calendar
                    id="startDate"
                    v-model="exportStartDate"
                    dateFormat="yy-mm-dd"
                    placeholder="Select start date"
                    showIcon
                    class="date-picker"
                />
              </div>
              <div class="date-control">
                <label for="endDate" class="date-label">End Date</label>
                <Calendar
                    id="endDate"
                    v-model="exportEndDate"
                    dateFormat="yy-mm-dd"
                    placeholder="Select end date"
                    showIcon
                    class="date-picker"
                />
              </div>
            </div>
            <div class="date-range-presets">
              <Button
                  label="Last 30 Days"
                  outlined
                  size="small"
                  @click="setDateRange(30)"
              />
              <Button
                  label="Last 90 Days"
                  outlined
                  size="small"
                  @click="setDateRange(90)"
              />
              <Button
                  label="Last Year"
                  outlined
                  size="small"
                  @click="setDateRange(365)"
              />
              <Button
                  label="All Time"
                  outlined
                  size="small"
                  @click="setDateRange(null)"
              />
            </div>
          </div>

          <!-- Export Actions -->
          <div class="form-actions">
            <Button
                label="Start Export"
                icon="pi pi-download"
                @click="startExport"
                :loading="isExporting || hasActiveExportJob"
                :disabled="!canStartExport"
                class="export-button"
            />
          </div>
        </div>
      </template>
    </Card>

    <!-- Current Export Job Status -->
    <Card v-if="currentExportJob" class="job-status-card">
      <template #content>
        <div class="job-status">
          <div class="job-header">
            <h3 class="job-title">Current Export Job</h3>
            <Tag
                :value="getStatusDisplayInfo(currentExportJob.status).label"
                :severity="getStatusDisplayInfo(currentExportJob.status).severity"
                :icon="getStatusDisplayInfo(currentExportJob.status).icon"
            />
          </div>

          <div class="job-details">
            <div class="job-detail">
              <span class="detail-label">Data Types:</span>
              <span class="detail-value">
                {{ currentExportJob.dataTypes?.map(getDataTypeDisplayName).join(', ') }}
              </span>
            </div>

            <div class="job-detail" v-if="currentExportJob.dateRange">
              <span class="detail-label">Date Range:</span>
              <span class="detail-value">
                {{ formatDateRange(currentExportJob.dateRange) }}
              </span>
            </div>

            <div class="job-detail" v-if="currentExportJob.progress !== undefined">
              <span class="detail-label">Progress:</span>
              <div class="detail-value">
                <ProgressBar :value="currentExportJob.progress" class="job-progress"/>
                <span class="progress-text">{{ currentExportJob.progress }}%</span>
              </div>
            </div>
          </div>

          <div class="job-actions" v-if="currentExportJob.status === 'completed'">
            <Button
                label="Download"
                icon="pi pi-download"
                @click="downloadExport(currentExportJob.exportJobId)"
                outlined
            />
            <Button
                label="Delete"
                icon="pi pi-trash"
                severity="danger"
                outlined
                @click="confirmDeleteExport(currentExportJob.exportJobId)"
            />
          </div>
        </div>
      </template>
    </Card>

    <!-- Export History (hidden for now) -->
    <Card class="export-history-card" style="display: none;">
      <template #content>
        <div class="export-history">
          <div class="history-header">
            <h3 class="history-title">Export History</h3>
            <Button
                label="Refresh"
                icon="pi pi-refresh"
                outlined
                size="small"
                @click="refreshExportJobs"
            />
          </div>

          <div v-if="exportJobs.length === 0" class="empty-state">
            <i class="pi pi-file-export empty-icon"></i>
            <p class="empty-text">No export jobs found</p>
          </div>

          <div v-else class="export-list">
            <div
                v-for="job in exportJobs"
                :key="job.exportJobId"
                class="export-item"
            >
              <div class="export-item-info">
                <div class="export-item-header">
                  <span class="export-item-date">
                    {{ formatDate(job.createdAt) }}
                  </span>
                  <Tag
                      :value="getStatusDisplayInfo(job.status).label"
                      :severity="getStatusDisplayInfo(job.status).severity"
                      size="small"
                  />
                </div>
                <div class="export-item-details">
                  <span class="export-detail">
                    {{ job.dataTypes?.map(getDataTypeDisplayName).join(', ') }}
                  </span>
                  <span v-if="job.fileSizeBytes" class="export-size">
                    {{ getFileSizeDisplay(job.fileSizeBytes) }}
                  </span>
                </div>
              </div>
              <div class="export-item-actions">
                <Button
                    v-if="job.status === 'completed'"
                    icon="pi pi-download"
                    outlined
                    size="small"
                    @click="downloadExport(job.exportJobId)"
                    :disabled="isJobExpired(job)"
                    v-tooltip="isJobExpired(job) ? 'Export has expired' : 'Download export'"
                />
                <Button
                    icon="pi pi-trash"
                    severity="danger"
                    outlined
                    size="small"
                    @click="confirmDeleteExport(job.exportJobId)"
                />
              </div>
            </div>
          </div>
        </div>
      </template>
    </Card>
  </div>

  <!-- Confirm Dialog -->
  <ConfirmDialog/>
  <Toast/>
</template>

<script setup>
import {ref, computed, onMounted, watch} from 'vue'
import {storeToRefs} from 'pinia'
import {useToast} from 'primevue/usetoast'
import {useConfirm} from 'primevue/useconfirm'
import {useTimezone} from '@/composables/useTimezone'
import {useExportImportStore} from '@/stores/exportImport'

const timezone = useTimezone()
const toast = useToast()
const confirm = useConfirm()
const exportImportStore = useExportImportStore()

// Store refs
const {
  exportJobs,
  currentExportJob,
  isExporting,
  hasActiveExportJob
} = storeToRefs(exportImportStore)

// State
const selectedDataTypes = ref(['rawgps', 'favorites', 'reversegeocodinglocation', 'locationsources', 'userinfo'])
const exportStartDate = ref(null)
const exportEndDate = ref(null)
const exportFormat = ref('geopulse')
const gpxExportMode = ref('single') // 'single' or 'zip'
const gpxZipGroupBy = ref('individual') // 'individual' or 'daily'

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

// Export format options
const exportFormatOptions = ref([
  {label: 'GeoPulse', value: 'geopulse', description: 'Native GeoPulse format with all data types'},
  {label: 'OwnTracks', value: 'owntracks', description: 'Compatible with OwnTracks format (GPS data only)'},
  {label: 'GeoJSON', value: 'geojson', description: 'Standard GeoJSON format compatible with GIS tools (GPS data only)'},
  {label: 'GPX', value: 'gpx', description: 'GPS Exchange Format with tracks and waypoints (compatible with GPXSee, QGIS, Garmin)'}
])

// Computed
const canStartExport = computed(() => {
  const hasValidDates = exportStartDate.value &&
      exportEndDate.value &&
      exportStartDate.value <= exportEndDate.value

  // For OwnTracks, GeoJSON, and GPX, we don't need data type selection
  if (exportFormat.value === 'owntracks' || exportFormat.value === 'geojson' || exportFormat.value === 'gpx') {
    return hasValidDates
  }

  // For GeoPulse, we need at least one data type selected
  return selectedDataTypes.value.length > 0 && hasValidDates
})

// Methods
const setDateRange = (days) => {
  const end = timezone.now().toDate()
  exportEndDate.value = end

  if (days === null) {
    // All time - set to a very early date
    exportStartDate.value = timezone.create('2020-01-01').toDate()
  } else {
    const start = timezone.now().subtract(days, 'day')
    exportStartDate.value = start.toDate()
  }
}

const startExport = async () => {
  try {
    const dateRange = {
      startDate: exportStartDate.value.toISOString(),
      endDate: exportEndDate.value.toISOString()
    }

    // For OwnTracks, use only GPS data and different endpoint
    if (exportFormat.value === 'owntracks') {
      await exportImportStore.createOwnTracksExportJob(dateRange)
    } else if (exportFormat.value === 'geojson') {
      // For GeoJSON, use only GPS data and different endpoint
      await exportImportStore.createGeoJsonExportJob(dateRange)
    } else if (exportFormat.value === 'gpx') {
      // For GPX, use GPX export endpoint with options
      const zipPerTrip = gpxExportMode.value === 'zip'
      const zipGroupBy = gpxZipGroupBy.value
      await exportImportStore.createGpxExportJob(dateRange, zipPerTrip, zipGroupBy)
    } else {
      // For GeoPulse, use selected data types
      await exportImportStore.createExportJob(
          selectedDataTypes.value,
          dateRange,
          'json' // Always JSON for GeoPulse format
      )
    }

    toast.add({
      severity: 'success',
      summary: 'Export Started',
      detail: 'Your export job has been created and is being processed',
      life: 5000
    })

    // Start polling for status updates
    if (currentExportJob.value) {
      exportImportStore.pollJobStatus(currentExportJob.value.exportJobId, true)
    }
  } catch (error) {
    console.error('Export error:', error)
    toast.add({
      severity: 'error',
      summary: 'Export Failed',
      detail: error.message || 'Failed to start export job',
      life: 5000
    })
  }
}

const downloadExport = async (exportJobId) => {
  try {
    await exportImportStore.downloadExportFile(exportJobId)

    toast.add({
      severity: 'success',
      summary: 'Download Started',
      detail: 'Your export file download has started',
      life: 3000
    })
  } catch (error) {
    console.error('Download error:', error)
    toast.add({
      severity: 'error',
      summary: 'Download Failed',
      detail: error.message || 'Failed to download export file',
      life: 5000
    })
  }
}

const confirmDeleteExport = (exportJobId) => {
  confirm.require({
    message: 'Are you sure you want to delete this export? This action cannot be undone.',
    header: 'Delete Export',
    icon: 'pi pi-exclamation-triangle',
    rejectProps: {
      label: 'Cancel',
      severity: 'secondary',
      outlined: true
    },
    acceptProps: {
      label: 'Delete',
      severity: 'danger'
    },
    accept: () => deleteExport(exportJobId)
  })
}

const deleteExport = async (exportJobId) => {
  try {
    await exportImportStore.deleteExportJob(exportJobId)

    toast.add({
      severity: 'success',
      summary: 'Export Deleted',
      detail: 'Export job has been deleted successfully',
      life: 3000
    })
  } catch (error) {
    console.error('Delete error:', error)
    toast.add({
      severity: 'error',
      summary: 'Delete Failed',
      detail: error.message || 'Failed to delete export job',
      life: 5000
    })
  }
}

const refreshExportJobs = async () => {
  try {
    await exportImportStore.fetchExportJobs()
  } catch (error) {
    console.error('Error refreshing export jobs:', error)
    toast.add({
      severity: 'error',
      summary: 'Refresh Failed',
      detail: 'Failed to refresh export jobs',
      life: 5000
    })
  }
}

const isJobExpired = (job) => {
  if (!job.expiresAt) return false
  return timezone.now().isAfter(timezone.fromUtc(job.expiresAt))
}

const formatDate = (dateString) => {
  return timezone.format(dateString, 'YYYY-MM-DD HH:mm:ss')
}

const formatDateRange = (dateRange) => {
  if (!dateRange) return 'All time'
  const start = timezone.fromUtc(dateRange.startDate).format('YYYY-MM-DD')
  const end = timezone.fromUtc(dateRange.endDate).format('YYYY-MM-DD')
  return `${start} - ${end}`
}

// Store utility methods
const {getDataTypeDisplayName, getFileSizeDisplay, getStatusDisplayInfo} = exportImportStore

// Toggle all export data types
const toggleAllExportDataTypes = () => {
  if (selectedDataTypes.value.length === availableDataTypes.value.length) {
    // Deselect all
    selectedDataTypes.value = []
  } else {
    // Select all
    selectedDataTypes.value = availableDataTypes.value.map(dt => dt.key)
  }
}

// Watch for export job completion
watch(() => currentExportJob.value?.status, (newStatus, oldStatus) => {
  if (oldStatus && oldStatus !== 'completed' && newStatus === 'completed') {
    toast.add({
      severity: 'success',
      summary: 'Export Completed',
      detail: 'Your export is ready for download!',
      life: 5000
    })
  }
})

// Initialize default date range (last 30 days)
onMounted(() => {
  setDateRange(30)
})
</script>

<style scoped>
/* Export-specific styles - shared styles are in DataExportImportPage.vue */
.export-form-card,
.export-history-card {
  margin-bottom: 2rem;
}

/* GPX Export Options */
.gpx-export-options {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.gpx-option {
  display: flex;
  align-items: flex-start;
  gap: 1rem;
  padding: 1rem;
  border: 2px solid var(--gp-border-light);
  border-radius: var(--gp-radius-medium);
  cursor: pointer;
  transition: all 0.2s ease;
  background: var(--gp-surface-light);
}

.gpx-option:hover {
  border-color: var(--gp-primary);
  background: var(--gp-surface-hover);
}

.gpx-option.selected {
  border-color: var(--gp-primary);
  background: rgba(59, 130, 246, 0.1);
}

/* Dark mode support for GPX options */
:root[class*="dark"] .gpx-option {
  background: var(--surface-ground);
}

:root[class*="dark"] .gpx-option.selected {
  background: rgba(59, 130, 246, 0.2);
  border-color: var(--primary-400);
}

.gpx-radio {
  flex-shrink: 0;
  margin-top: 0.25rem;
}

.gpx-option-info {
  flex: 1;
}

.gpx-option-label {
  display: block;
  font-weight: 600;
  color: var(--gp-text-primary);
  margin-bottom: 0.25rem;
  cursor: pointer;
}

.gpx-option-description {
  color: var(--gp-text-secondary);
  font-size: 0.875rem;
  margin: 0;
  line-height: 1.4;
}

/* ZIP Grouping Options */
.gpx-zip-grouping {
  margin-top: 1.5rem;
  padding: 1rem;
  border: 1px solid var(--gp-border-light);
  border-radius: var(--gp-radius-medium);
  background: var(--gp-surface-light);
}

.grouping-title {
  font-size: 0.9rem;
  font-weight: 600;
  color: var(--gp-text-primary);
  margin: 0 0 1rem 0;
}

.grouping-options {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.grouping-option {
  display: flex;
  align-items: flex-start;
  gap: 0.75rem;
  padding: 0.75rem;
  border: 2px solid var(--gp-border-light);
  border-radius: var(--gp-radius-medium);
  cursor: pointer;
  transition: all 0.2s ease;
  background: var(--gp-surface-light);
}

.grouping-option:hover {
  border-color: var(--gp-primary);
  background: var(--gp-surface-hover);
}

.grouping-option.selected {
  border-color: var(--gp-primary);
  background: rgba(59, 130, 246, 0.1);
}

/* Dark mode support */
:root[class*="dark"] .grouping-option {
  background: var(--surface-ground);
}

:root[class*="dark"] .grouping-option.selected {
  background: rgba(59, 130, 246, 0.2);
  border-color: var(--primary-400);
}

.grouping-radio {
  flex-shrink: 0;
  margin-top: 0.15rem;
}

.grouping-option-info {
  flex: 1;
}

.grouping-option-label {
  display: block;
  font-weight: 600;
  font-size: 0.875rem;
  color: var(--gp-text-primary);
  margin-bottom: 0.15rem;
  cursor: pointer;
}

.grouping-option-description {
  color: var(--gp-text-secondary);
  font-size: 0.8rem;
  margin: 0;
  line-height: 1.3;
}

/* Export History */
.export-history {
  display: flex;
  flex-direction: column;
  gap: 1.5rem;
}

.export-list {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.export-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 1rem;
  border: 1px solid var(--gp-border-light);
  border-radius: var(--gp-radius-medium);
  background: var(--gp-surface-light);
}

.export-item-info {
  flex: 1;
}

.export-item-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 0.5rem;
}

.export-item-date {
  font-weight: 500;
  color: var(--gp-text-primary);
}

.export-item-details {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.export-detail {
  color: var(--gp-text-secondary);
  font-size: 0.9rem;
}

.export-size {
  color: var(--gp-text-tertiary);
  font-size: 0.8rem;
  font-weight: 500;
}

.export-item-actions {
  display: flex;
  gap: 0.5rem;
  flex-shrink: 0;
}

/* Responsive Design */
@media (max-width: 768px) {
  .export-item {
    flex-direction: column;
    align-items: stretch;
    gap: 1rem;
  }

  .export-item-actions {
    justify-content: center;
  }
}

@media (max-width: 480px) {
  .export-item-header {
    flex-direction: column;
    align-items: flex-start;
    gap: 0.5rem;
  }

  .export-item-details {
    flex-direction: column;
    align-items: flex-start;
    gap: 0.25rem;
  }
}
</style>
