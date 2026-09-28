<template>
  <div class="tab-section">
    <!-- Export Form -->
    <Card class="export-form-card">
      <template #content>
        <div class="export-form">
          <!-- Export Format Selection -->
          <div class="form-section">
            <h3 class="form-section-title">{{ t('data.exportTab.formatSectionTitle') }}</h3>
            <div class="format-options">
              <div
                  v-for="format in exportFormatOptions"
                  :key="format.value"
                  class="format-option"
                  :class="{ 'selected': exportFormat === format.value, 'disabled': readOnly }"
              >
                <RadioButton
                    v-model="exportFormat"
                    :inputId="format.value"
                    :value="format.value"
                    :disabled="readOnly"
                    class="format-radio"
                />
                <label :for="format.value" class="format-info">
                  <div class="format-label">
                    {{ format.label }}
                  </div>
                  <p class="format-description">{{ format.description }}</p>
                </label>
              </div>
            </div>
          </div>

          <!-- Data Types Selection (only for GeoPulse format) -->
          <div v-if="exportFormat === 'geopulse'" class="form-section">
            <div class="form-section-header">
              <h3 class="form-section-title">{{ t('data.exportTab.selectDataTypesTitle') }}</h3>
              <Button
                :label="selectedDataTypes.length === availableDataTypes.length ? t('data.exportTab.deselectAll') : t('data.exportTab.selectAll')"
                outlined
                size="small"
                :disabled="readOnly"
                @click="toggleAllExportDataTypes"
                class="select-all-button"
              />
            </div>
            <div class="timeline-info">
              <i class="pi pi-info-circle"></i>
              <span><strong>{{ t('data.exportTab.timelineDataLabel') }}</strong> {{ t('data.exportTab.timelineDataNote') }}</span>
            </div>
            <div class="data-types-grid">
              <div
                  v-for="dataType in availableDataTypes"
                  :key="dataType.key"
                  class="data-type-option"
                  :class="{ 'disabled': readOnly }"
              >
                <Checkbox
                    v-model="selectedDataTypes"
                    :inputId="dataType.key"
                    :value="dataType.key"
                    :disabled="readOnly"
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

          <!-- OwnTracks Export Options (only for OwnTracks format) -->
          <div v-if="exportFormat === 'owntracks'" class="form-section">
            <h3 class="form-section-title">{{ t('data.exportTab.owntracksOptionsTitle') }}</h3>
            <div class="gpx-export-options">
              <div
                  class="gpx-option"
                  :class="{ 'selected': owntracksExportFormat === 'ocat', 'disabled': readOnly }"
                  @click="!readOnly && (owntracksExportFormat = 'ocat')"
              >
                <RadioButton
                    v-model="owntracksExportFormat"
                    inputId="owntracks-ocat"
                    value="ocat"
                    :disabled="readOnly"
                    class="gpx-radio"
                />
                <div class="gpx-option-info">
                  <label for="owntracks-ocat" class="gpx-option-label">
                    {{ t('data.exportTab.owntracksOcatLabel') }}
                  </label>
                  <p class="gpx-option-description">
                    {{ t('data.exportTab.owntracksOcatDescription') }}
                  </p>
                </div>
              </div>

              <div
                  class="gpx-option"
                  :class="{ 'selected': owntracksExportFormat === 'array', 'disabled': readOnly }"
                  @click="!readOnly && (owntracksExportFormat = 'array')"
              >
                <RadioButton
                    v-model="owntracksExportFormat"
                    inputId="owntracks-array"
                    value="array"
                    :disabled="readOnly"
                    class="gpx-radio"
                />
                <div class="gpx-option-info">
                  <label for="owntracks-array" class="gpx-option-label">
                    {{ t('data.exportTab.owntracksArrayLabel') }}
                  </label>
                  <p class="gpx-option-description">
                    {{ t('data.exportTab.owntracksArrayDescription') }}
                  </p>
                </div>
              </div>
            </div>
          </div>

          <!-- GPX Export Options (only for GPX format) -->
          <div v-if="exportFormat === 'gpx'" class="form-section">
            <h3 class="form-section-title">{{ t('data.exportTab.gpxOptionsTitle') }}</h3>
            <div class="gpx-export-options">
              <div
                  class="gpx-option"
                  :class="{ 'selected': gpxExportMode === 'single', 'disabled': readOnly }"
                  @click="!readOnly && (gpxExportMode = 'single')"
              >
                <RadioButton
                    v-model="gpxExportMode"
                    inputId="gpx-single"
                    value="single"
                    :disabled="readOnly"
                    class="gpx-radio"
                />
                <div class="gpx-option-info">
                  <label for="gpx-single" class="gpx-option-label">
                    {{ t('data.exportTab.gpxSingleLabel') }}
                  </label>
                  <p class="gpx-option-description">
                    {{ t('data.exportTab.gpxSingleDescription') }}
                  </p>
                </div>
              </div>

              <div
                  class="gpx-option"
                  :class="{ 'selected': gpxExportMode === 'zip', 'disabled': readOnly }"
                  @click="!readOnly && (gpxExportMode = 'zip')"
              >
                <RadioButton
                    v-model="gpxExportMode"
                    inputId="gpx-zip"
                    value="zip"
                    :disabled="readOnly"
                    class="gpx-radio"
                />
                <div class="gpx-option-info">
                  <label for="gpx-zip" class="gpx-option-label">
                    {{ t('data.exportTab.gpxZipLabel') }}
                  </label>
                  <p class="gpx-option-description">
                    {{ t('data.exportTab.gpxZipDescription') }}
                  </p>
                </div>
              </div>
            </div>

            <!-- ZIP Grouping Options (shown when ZIP mode is selected) -->
            <div v-if="gpxExportMode === 'zip'" class="gpx-zip-grouping">
              <h4 class="grouping-title">{{ t('data.exportTab.gpxZipGroupingTitle') }}</h4>
              <div class="grouping-options">
                <div
                    class="grouping-option"
                    :class="{ 'selected': gpxZipGroupBy === 'individual', 'disabled': readOnly }"
                    @click="!readOnly && (gpxZipGroupBy = 'individual')"
                >
                  <RadioButton
                      v-model="gpxZipGroupBy"
                      inputId="gpx-group-individual"
                      value="individual"
                      :disabled="readOnly"
                      class="grouping-radio"
                  />
                  <div class="grouping-option-info">
                    <label for="gpx-group-individual" class="grouping-option-label">
                      {{ t('data.exportTab.gpxGroupIndividualLabel') }}
                    </label>
                    <p class="grouping-option-description">
                      {{ t('data.exportTab.gpxGroupIndividualDescription') }}
                    </p>
                  </div>
                </div>

                <div
                    class="grouping-option"
                    :class="{ 'selected': gpxZipGroupBy === 'daily', 'disabled': readOnly }"
                    @click="!readOnly && (gpxZipGroupBy = 'daily')"
                >
                  <RadioButton
                      v-model="gpxZipGroupBy"
                      inputId="gpx-group-daily"
                      value="daily"
                      :disabled="readOnly"
                      class="grouping-radio"
                  />
                  <div class="grouping-option-info">
                    <label for="gpx-group-daily" class="grouping-option-label">
                      {{ t('data.exportTab.gpxGroupDailyLabel') }}
                    </label>
                    <p class="grouping-option-description">
                      {{ t('data.exportTab.gpxGroupDailyDescription') }}
                    </p>
                  </div>
                </div>
              </div>
            </div>
          </div>

          <!-- CSV Format Documentation (shown when CSV is selected) -->
          <div v-if="exportFormat === 'csv'" class="csv-format-docs">
            <h4 class="csv-docs-title">{{ t('data.exportTab.csvDocsTitle') }}</h4>

            <div class="csv-docs-section">
              <p class="csv-info-text">
                <i class="pi pi-info-circle" style="margin-right: 0.5rem;"></i>
                {{ t('data.exportTab.csvInfoText') }}
              </p>
            </div>

            <div class="csv-docs-section">
              <h5 class="csv-docs-subtitle">{{ t('data.exportTab.csvFieldsTitle') }}</h5>
              <ul class="csv-field-list">
                <li><strong>timestamp</strong>: {{ t('data.exportTab.csvFields.timestamp') }}</li>
                <li><strong>latitude</strong>: {{ t('data.exportTab.csvFields.latitude') }}</li>
                <li><strong>longitude</strong>: {{ t('data.exportTab.csvFields.longitude') }}</li>
                <li><strong>accuracy</strong>: {{ t('data.exportTab.csvFields.accuracy') }}</li>
                <li><strong>velocity</strong>: {{ t('data.exportTab.csvFields.velocity') }}</li>
                <li><strong>altitude</strong>: {{ t('data.exportTab.csvFields.altitude') }}</li>
                <li><strong>battery</strong>: {{ t('data.exportTab.csvFields.battery') }}</li>
                <li><strong>device_id</strong>: {{ t('data.exportTab.csvFields.deviceId') }}</li>
                <li><strong>source_type</strong>: {{ t('data.exportTab.csvFields.sourceType') }}</li>
              </ul>
            </div>

            <div class="csv-docs-section">
              <h5 class="csv-docs-subtitle">{{ t('data.exportTab.csvExampleTitle') }}</h5>
              <pre class="csv-example-code">timestamp,latitude,longitude,accuracy,velocity,altitude,battery,device_id,source_type
2024-01-15T10:30:00Z,37.7749,-122.4194,10.5,5.2,100.0,85.0,device123,CSV
2024-01-15T10:35:00Z,37.7750,-122.4195,8.3,12.8,105.2,84.8,,CSV</pre>
            </div>
          </div>

          <!-- Date Range Selection -->
          <div class="form-section">
            <h3 class="form-section-title">{{ t('data.exportTab.dateRangeTitle') }}</h3>
            <div class="date-range-controls">
              <div class="date-control">
                <label for="startDate" class="date-label">{{ t('data.exportTab.startDateLabel') }}</label>
                <Calendar
                    id="startDate"
                    v-model="exportStartDate"
                    :dateFormat="timezone.getPrimeVueDatePickerFormat()"
                    :placeholder="t('data.exportTab.startDatePlaceholder')"
                    showIcon
                    :disabled="readOnly"
                    class="date-picker"
                />
              </div>
              <div class="date-control">
                <label for="endDate" class="date-label">{{ t('data.exportTab.endDateLabel') }}</label>
                <Calendar
                    id="endDate"
                    v-model="exportEndDate"
                    :dateFormat="timezone.getPrimeVueDatePickerFormat()"
                    :placeholder="t('data.exportTab.endDatePlaceholder')"
                    showIcon
                    :disabled="readOnly"
                    class="date-picker"
                />
              </div>
            </div>
            <div class="date-range-presets">
              <Button
                  :label="t('data.exportTab.last30Days')"
                  outlined
                  size="small"
                  :disabled="readOnly"
                  @click="setDateRange(30)"
              />
              <Button
                  :label="t('data.exportTab.last90Days')"
                  outlined
                  size="small"
                  :disabled="readOnly"
                  @click="setDateRange(90)"
              />
              <Button
                  :label="t('data.exportTab.lastYear')"
                  outlined
                  size="small"
                  :disabled="readOnly"
                  @click="setDateRange(365)"
              />
              <Button
                  :label="t('data.exportTab.allTime')"
                  outlined
                  size="small"
                  :disabled="readOnly"
                  @click="setDateRange(null)"
              />
            </div>
          </div>

          <!-- Export Actions -->
          <div class="form-actions">
            <Button
                :label="t('data.exportTab.startExport')"
                icon="pi pi-download"
                @click="startExport"
                :loading="isExporting || hasActiveExportJob"
                :disabled="!canStartExport"
                class="export-button"
            />
            <small v-if="readOnly" class="demo-disabled-note">
              {{ t('data.exportTab.demoDisabledNote') }}
            </small>
          </div>
        </div>
      </template>
    </Card>

    <!-- Current Export Job Status -->
    <Card v-if="currentExportJob" class="job-status-card">
      <template #content>
        <div class="job-status">
          <div class="job-header">
            <h3 class="job-title">{{ t('data.exportTab.currentJobTitle') }}</h3>
            <Tag
                :value="getStatusDisplayInfo(currentExportJob.status).label"
                :severity="getStatusDisplayInfo(currentExportJob.status).severity"
                :icon="getStatusDisplayInfo(currentExportJob.status).icon"
            />
          </div>

          <div class="job-details">
            <div class="job-detail">
              <span class="detail-label">{{ t('data.exportTab.dataTypesLabel') }}</span>
              <span class="detail-value">
                {{ currentExportJob.dataTypes?.map(getDataTypeDisplayName).join(', ') }}
              </span>
            </div>

            <div class="job-detail" v-if="currentExportJob.dateRange">
              <span class="detail-label">{{ t('data.exportTab.dateRangeLabel') }}</span>
              <span class="detail-value">
                {{ formatDateRange(currentExportJob.dateRange) }}
              </span>
            </div>

            <div class="job-detail" v-if="currentExportJob.progress !== undefined">
              <span class="detail-label">{{ t('data.exportTab.progressLabel') }}</span>
              <div class="detail-value">
                <ProgressBar :value="currentExportJob.progress" class="job-progress"/>
                <div class="progress-info">
                  <span class="progress-text">{{ currentExportJob.progress }}%</span>
                  <span v-if="currentExportJob.progressMessage" class="progress-message">
                    {{ formatMessageDescriptor(currentExportJob.progressMessage) }}
                  </span>
                </div>
              </div>
            </div>
          </div>

          <div class="job-actions" v-if="currentExportJob.status === 'completed'">
            <Button
                :label="t('data.exportTab.download')"
                icon="pi pi-download"
                @click="downloadExport(currentExportJob.exportJobId)"
                :disabled="readOnly"
                v-tooltip.bottom="readOnly ? t('data.exportTab.downloadsDisabledDemo') : t('data.exportTab.downloadExportTooltip')"
                outlined
            />
            <Button
                :label="t('data.exportTab.delete')"
                icon="pi pi-trash"
                severity="danger"
                outlined
                :disabled="readOnly"
                v-tooltip.bottom="readOnly ? t('data.exportTab.deleteExportsDisabledDemo') : t('data.exportTab.deleteExportTooltip')"
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
            <h3 class="history-title">{{ t('data.exportTab.exportHistoryTitle') }}</h3>
            <Button
                :label="t('data.exportTab.refresh')"
                icon="pi pi-refresh"
                outlined
                size="small"
                @click="refreshExportJobs"
            />
          </div>

          <div v-if="exportJobs.length === 0" class="empty-state">
            <i class="pi pi-file-export empty-icon"></i>
            <p class="empty-text">{{ t('data.exportTab.noExportJobs') }}</p>
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
                    :disabled="readOnly || isJobExpired(job)"
                    v-tooltip="readOnly ? t('data.exportTab.downloadsDisabledDemo') : (isJobExpired(job) ? t('data.exportTab.exportExpired') : t('data.exportTab.downloadExportTooltip'))"
                />
                <Button
                    icon="pi pi-trash"
                    severity="danger"
                    outlined
                    size="small"
                    :disabled="readOnly"
                    v-tooltip="readOnly ? t('data.exportTab.deleteExportsDisabledDemo') : t('data.exportTab.deleteExportTooltip')"
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
import {useI18n} from 'vue-i18n'
import {storeToRefs} from 'pinia'
import {useToast} from 'primevue/usetoast'
import {useConfirm} from 'primevue/useconfirm'
import {useTimezone} from '@/composables/useTimezone'
import {useExportImportStore} from '@/stores/exportImport'
import {showDemoModeToast} from '@/utils/demoMode'
import {formatMessageDescriptor} from '@/utils/messageDescriptor'
import {formatApiErrorDetail} from '@/utils/apiErrorDetail'

const { t } = useI18n()
const timezone = useTimezone()
const toast = useToast()
const confirm = useConfirm()
const exportImportStore = useExportImportStore()

const props = defineProps({
  readOnly: {
    type: Boolean,
    default: false
  }
})

// Store refs
const {
  exportJobs,
  currentExportJob,
  isExporting,
  hasActiveExportJob
} = storeToRefs(exportImportStore)

// State
const selectedDataTypes = ref([
  'rawgps',
  'favorites',
  'reversegeocodinglocation',
  'locationsources',
  'userinfo',
  'timelinelabels',
  'timelineoverrides',
  'tripworkspace',
  'notificationtemplates',
  'geofencing',
  'notes',
  'weathersamples',
  'mapmatching',
  'friends',
  'friendpermissions'
])
const exportStartDate = ref(null)
const exportEndDate = ref(null)
const exportFormat = ref('geopulse')
const owntracksExportFormat = ref('ocat') // 'ocat' or 'array'
const gpxExportMode = ref('single') // 'single' or 'zip'
const gpxZipGroupBy = ref('individual') // 'individual' or 'daily'

// Data types configuration
const availableDataTypes = computed(() => [
  { key: 'rawgps', label: t('data.dataTypes.rawgps.label'), description: t('data.dataTypes.rawgps.description'), icon: 'pi pi-map-marker' },
  { key: 'favorites', label: t('data.dataTypes.favorites.label'), description: t('data.dataTypes.favorites.description'), icon: 'pi pi-heart' },
  { key: 'reversegeocodinglocation', label: t('data.dataTypes.reversegeocodinglocation.label'), description: t('data.dataTypes.reversegeocodinglocation.description'), icon: 'pi pi-map' },
  { key: 'locationsources', label: t('data.dataTypes.locationsources.label'), description: t('data.dataTypes.locationsources.description'), icon: 'pi pi-mobile' },
  { key: 'userinfo', label: t('data.dataTypes.userinfo.label'), description: t('data.dataTypes.userinfo.description'), icon: 'pi pi-user' },
  { key: 'timelinelabels', label: t('data.dataTypes.timelinelabels.label'), description: t('data.dataTypes.timelinelabels.description'), icon: 'pi pi-tags' },
  { key: 'timelineoverrides', label: t('data.dataTypes.timelineoverrides.label'), description: t('data.dataTypes.timelineoverrides.description'), icon: 'pi pi-pencil' },
  { key: 'tripworkspace', label: t('data.dataTypes.tripworkspace.label'), description: t('data.dataTypes.tripworkspace.description'), icon: 'pi pi-briefcase' },
  { key: 'notificationtemplates', label: t('data.dataTypes.notificationtemplates.label'), description: t('data.dataTypes.notificationtemplates.description'), icon: 'pi pi-bell' },
  { key: 'geofencing', label: t('data.dataTypes.geofencing.label'), description: t('data.dataTypes.geofencing.description'), icon: 'pi pi-map-marker' },
  { key: 'notes', label: t('data.dataTypes.notes.label'), description: t('data.dataTypes.notes.description'), icon: 'pi pi-file-edit' },
  { key: 'weathersamples', label: t('data.dataTypes.weathersamples.label'), description: t('data.dataTypes.weathersamples.description'), icon: 'pi pi-cloud' },
  { key: 'mapmatching', label: t('data.dataTypes.mapmatching.label'), description: t('data.dataTypes.mapmatching.description'), icon: 'pi pi-directions' },
  { key: 'friends', label: t('data.dataTypes.friends.label'), description: t('data.dataTypes.friends.description'), icon: 'pi pi-users' },
  { key: 'friendpermissions', label: t('data.dataTypes.friendpermissions.label'), description: t('data.dataTypes.friendpermissions.description'), icon: 'pi pi-lock' }
])

// Export format options
const exportFormatOptions = computed(() => [
  {label: t('data.exportTab.formats.geopulse.label'), value: 'geopulse', description: t('data.exportTab.formats.geopulse.description')},
  {label: t('data.exportTab.formats.owntracks.label'), value: 'owntracks', description: t('data.exportTab.formats.owntracks.description')},
  {label: t('data.exportTab.formats.geojson.label'), value: 'geojson', description: t('data.exportTab.formats.geojson.description')},
  {label: t('data.exportTab.formats.gpx.label'), value: 'gpx', description: t('data.exportTab.formats.gpx.description')},
  {label: t('data.exportTab.formats.csv.label'), value: 'csv', description: t('data.exportTab.formats.csv.description')}
])

// Computed
const canStartExport = computed(() => {
  if (props.readOnly) {
    return false
  }

  const hasValidDates = exportStartDate.value &&
      exportEndDate.value &&
      exportStartDate.value <= exportEndDate.value

  // For OwnTracks, GeoJSON, GPX, and CSV, we don't need data type selection
  if (exportFormat.value === 'owntracks' || exportFormat.value === 'geojson' || exportFormat.value === 'gpx' || exportFormat.value === 'csv') {
    return hasValidDates
  }

  // For GeoPulse, we need at least one data type selected
  return selectedDataTypes.value.length > 0 && hasValidDates
})

// Methods
const setDateRange = (days) => {
  if (props.readOnly) {
    return
  }

  const end = timezone.now().toDate()
  exportEndDate.value = end

  if (days === null) {
    // All time - set to a very early date
    exportStartDate.value = timezone.create('2000-01-01').toDate()
  } else {
    const start = timezone.now().subtract(days, 'day')
    exportStartDate.value = start.toDate()
  }
}

const startExport = async () => {
  if (props.readOnly) {
    showDemoDisabledToast()
    return
  }

  try {
    const dateRange = {
      startDate: exportStartDate.value.toISOString(),
      endDate: exportEndDate.value.toISOString()
    }

    if (exportFormat.value === 'owntracks') {
      await exportImportStore.createOwnTracksExportJob(dateRange, owntracksExportFormat.value)
    } else if (exportFormat.value === 'geojson') {
      // For GeoJSON, use only GPS data and different endpoint
      await exportImportStore.createGeoJsonExportJob(dateRange)
    } else if (exportFormat.value === 'gpx') {
      // For GPX, use GPX export endpoint with options
      const zipPerTrip = gpxExportMode.value === 'zip'
      const zipGroupBy = gpxZipGroupBy.value
      await exportImportStore.createGpxExportJob(dateRange, zipPerTrip, zipGroupBy)
    } else if (exportFormat.value === 'csv') {
      // For CSV, use CSV export endpoint
      await exportImportStore.createCsvExportJob(dateRange)
    } else {
      // For GeoPulse, use selected data types
      await exportImportStore.createExportJob(
          selectedDataTypes.value,
          dateRange,
          'geopulse' // Always JSON for GeoPulse format
      )
    }

    toast.add({
      severity: 'success',
      summary: t('data.exportTab.exportStartedSummary'),
      detail: t('data.exportTab.exportStartedDetail'),
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
      summary: t('data.exportTab.exportFailedSummary'),
      detail: formatApiErrorDetail(error, t('data.exportTab.exportFailedFallback')),
      life: 5000
    })
  }
}

const downloadExport = async (exportJobId) => {
  if (props.readOnly) {
    showDemoDisabledToast()
    return
  }

  try {
    await exportImportStore.downloadExportFile(exportJobId)

    toast.add({
      severity: 'success',
      summary: t('data.exportTab.downloadStartedSummary'),
      detail: t('data.exportTab.downloadStartedDetail'),
      life: 3000
    })
  } catch (error) {
    console.error('Download error:', error)
    toast.add({
      severity: 'error',
      summary: t('data.exportTab.downloadFailedSummary'),
      detail: formatApiErrorDetail(error, t('data.exportTab.downloadFailedFallback')),
      life: 5000
    })
  }
}

const confirmDeleteExport = (exportJobId) => {
  if (props.readOnly) {
    showDemoDisabledToast()
    return
  }

  confirm.require({
    message: t('data.exportTab.deleteExportConfirmMessage'),
    header: t('data.exportTab.deleteExportConfirmHeader'),
    icon: 'pi pi-exclamation-triangle',
    rejectProps: {
      label: t('common.cancel'),
      severity: 'secondary',
      outlined: true
    },
    acceptProps: {
      label: t('data.exportTab.delete'),
      severity: 'danger'
    },
    accept: () => deleteExport(exportJobId)
  })
}

const deleteExport = async (exportJobId) => {
  if (props.readOnly) {
    showDemoDisabledToast()
    return
  }

  try {
    await exportImportStore.deleteExportJob(exportJobId)

    toast.add({
      severity: 'success',
      summary: t('data.exportTab.exportDeletedSummary'),
      detail: t('data.exportTab.exportDeletedDetail'),
      life: 3000
    })
  } catch (error) {
    console.error('Delete error:', error)
    toast.add({
      severity: 'error',
      summary: t('data.exportTab.deleteFailedSummary'),
      detail: formatApiErrorDetail(error, t('data.exportTab.deleteFailedFallback')),
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
      summary: t('data.exportTab.refreshFailedSummary'),
      detail: formatApiErrorDetail(error, t('data.exportTab.refreshFailedFallback')),
      life: 5000
    })
  }
}

const isJobExpired = (job) => {
  if (!job.expiresAt) return false
  return timezone.now().isAfter(timezone.fromUtc(job.expiresAt))
}

const formatDate = (dateString) => {
  return `${timezone.formatDateDisplay(dateString)} ${timezone.formatTime(dateString, { withSeconds: true })}`
}

const formatDateRange = (dateRange) => {
  if (!dateRange) return t('data.exportTab.allTimeLabel')
  const start = timezone.formatDateDisplay(dateRange.startDate)
  const end = timezone.formatDateDisplay(dateRange.endDate)
  return `${start} - ${end}`
}

// Store utility methods
const {getDataTypeDisplayName, getFileSizeDisplay, getStatusDisplayInfo} = exportImportStore

// Toggle all export data types
const toggleAllExportDataTypes = () => {
  if (props.readOnly) {
    return
  }

  if (selectedDataTypes.value.length === availableDataTypes.value.length) {
    // Deselect all
    selectedDataTypes.value = []
  } else {
    // Select all
    selectedDataTypes.value = availableDataTypes.value.map(dt => dt.key)
  }
}

const showDemoDisabledToast = () => {
  showDemoModeToast(toast, t('data.exportTab.demoToast'))
}

// Watch for export job completion
watch(() => currentExportJob.value?.status, (newStatus, oldStatus) => {
  if (oldStatus && oldStatus !== 'completed' && newStatus === 'completed') {
    toast.add({
      severity: 'success',
      summary: t('data.exportTab.exportCompletedSummary'),
      detail: t('data.exportTab.exportCompletedDetail'),
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

.demo-disabled-note {
  text-align: center;
}

.format-option.disabled,
.data-type-option.disabled,
.gpx-option.disabled,
.grouping-option.disabled {
  cursor: not-allowed;
  opacity: 0.72;
}

.format-option.disabled:hover,
.data-type-option.disabled:hover,
.gpx-option.disabled:hover,
.grouping-option.disabled:hover {
  border-color: var(--gp-border-light);
  background: var(--gp-surface-light);
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

/* CSV Format Documentation Styles */
.csv-format-docs {
  margin-bottom: 1.5rem;
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

.csv-info-text {
  display: flex;
  align-items: center;
  color: var(--gp-text-secondary);
  font-size: 0.9rem;
  margin: 0;
  line-height: 1.6;
}

.csv-info-text i {
  color: var(--gp-primary-500);
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

  .csv-example-code {
    font-size: 0.7rem;
  }
}
</style>
