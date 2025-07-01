<template>
  <AppLayout>
    <PageContainer>
      <div class="data-export-import-page">
        <!-- Page Header -->
        <div class="page-header">
          <div class="header-content">
            <div class="header-text">
              <h1 class="page-title">Data Export & Import</h1>
              <p class="page-description">
                Export your GeoPulse data for backup or import previously exported data
              </p>
            </div>
          </div>
        </div>

<!--        Info Banner-->
        <Card class="info-banner">
          <template #content>
            <div class="banner-content">
              <div class="banner-icon">
                <i class="pi pi-info-circle"></i>
              </div>
              <div class="banner-text">
                <h3 class="banner-title">Data Security & Privacy</h3>
                <p class="banner-description">
                  Export files are not stored on the server. Make sure you download them before leaving this page.
                </p>
              </div>
            </div>
          </template>
        </Card>

        <!-- Main Tabs -->
        <TabContainer
            :tabs="tabItems"
            :activeIndex="activeTabIndex"
            @tab-change="handleTabChange"
            class="export-import-tabs"
        >
          <!-- Export Tab -->
          <div v-if="activeTab === 'export'">
            <div class="tab-section">
              <!--              <div class="section-header">-->
              <!--                <h2 class="section-title">Export Your Data</h2>-->
              <!--                <p class="section-description">-->
              <!--                  Select the data types and date range you want to export-->
              <!--                </p>-->
              <!--              </div>-->

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
                      <!--                      <div class="export-info">-->
                      <!--                        <small class="export-note">-->
                      <!--                          Export files are available for 7 days and will be automatically deleted for security.-->
                      <!--                        </small>-->
                      <!--                      </div>-->
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
          </div>

          <!-- Import Tab -->
          <div v-if="activeTab === 'import'">
            <div class="tab-section">
              <!--              <div class="section-header">-->
              <!--                <h2 class="section-title">Import Your Data</h2>-->
              <!--                <p class="section-description">-->
              <!--                  Upload a previously exported GeoPulse data file-->
              <!--                </p>-->
              <!--              </div>-->

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
                            :accept="importFormat === 'owntracks' ? '.json' : '.zip'"
                            :maxFileSize="10485760000"
                            @select="onFileSelect"
                            @clear="onFileClear"
                            chooseLabel="Choose Export File"
                            class="file-uploader"
                            :auto="false"
                        />
                      </div>
                      <small class="upload-note">
                        {{
                          importFormat === 'owntracks'
                              ? 'Only OwnTracks JSON files are supported. Maximum file size: 100MB'
                              : 'Only GeoPulse export files (.zip) are supported. Maximum file size: 100MB'
                        }}
                      </small>
                    </div>

                    <!-- Import Options -->
                    <div v-if="selectedFile" class="form-section">
                      <h3 class="form-section-title">Import Options</h3>

                      <div class="import-options">
                        <div v-if="importFormat === 'geopulse'" class="option-group">
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

                        <div v-if="importFormat === 'owntracks'" class="option-group">
                          <div class="owntracks-info">
                            <i class="pi pi-info-circle"></i>
                            <span>OwnTracks import will add GPS location data to your GeoPulse timeline.</span>
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
                          <span class="progress-text">{{ currentImportJob.progress }}%</span>
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
          </div>
        </TabContainer>

        <!-- Confirm Dialog -->
        <ConfirmDialog/>
        <Toast/>
      </div>
    </PageContainer>
  </AppLayout>
</template>

<script setup>
import {ref, computed, onMounted, watch} from 'vue'
import {storeToRefs} from 'pinia'
import {useToast} from 'primevue/usetoast'
import {useConfirm} from 'primevue/useconfirm'

// Layout components
import AppLayout from '@/components/ui/layout/AppLayout.vue'
import PageContainer from '@/components/ui/layout/PageContainer.vue'
import TabContainer from '@/components/ui/layout/TabContainer.vue'

// Store
import {useExportImportStore} from '@/stores/exportImport'

// Composables
const toast = useToast()
const confirm = useConfirm()
const exportImportStore = useExportImportStore()

// Store refs
const {
  exportJobs,
  importJobs,
  currentExportJob,
  currentImportJob,
  isExporting,
  isImporting,
  hasActiveExportJob,
  hasActiveImportJob
} = storeToRefs(exportImportStore)

// State
const activeTab = ref('export')
const selectedDataTypes = ref(['rawgps', 'timeline', 'favorites', 'reversegeocodinglocation', 'locationsources', 'userinfo'])
const exportStartDate = ref(null)
const exportEndDate = ref(null)
const exportFormat = ref('geopulse')
const importFormat = ref('geopulse')
const selectedFile = ref(null)
const fileUpload = ref(null)
const enableDateFilter = ref(false)
const importStartDate = ref(null)
const importEndDate = ref(null)
const importOptions = ref({
  dataTypes: ['rawgps', 'timeline', 'favorites', 'reversegeocodinglocation', 'locationsources', 'userinfo']
})

// Tab configuration
const tabItems = ref([
  {
    label: 'Export Data',
    icon: 'pi pi-download',
    key: 'export'
  },
  {
    label: 'Import Data',
    icon: 'pi pi-upload',
    key: 'import'
  }
])

const activeTabIndex = computed(() => {
  return tabItems.value.findIndex(tab => tab.key === activeTab.value)
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
    key: 'timeline',
    label: 'Timeline Data',
    description: 'Processed stays and trips from your location data',
    icon: 'pi pi-clock'
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
  {label: 'OwnTracks', value: 'owntracks', description: 'Compatible with OwnTracks format (GPS data only)'}
])

// Import format options
const importFormatOptions = ref([
  {label: 'GeoPulse', value: 'geopulse', description: 'Import from GeoPulse export file'},
  {label: 'OwnTracks', value: 'owntracks', description: 'Import from OwnTracks export file'}
])

// Computed
const canStartExport = computed(() => {
  const hasValidDates = exportStartDate.value &&
      exportEndDate.value &&
      exportStartDate.value <= exportEndDate.value

  // For OwnTracks, we don't need data type selection
  if (exportFormat.value === 'owntracks') {
    return hasValidDates
  }

  // For GeoPulse, we need at least one data type selected
  return selectedDataTypes.value.length > 0 && hasValidDates
})

const canStartImport = computed(() => {
  const hasValidFile = selectedFile.value
  const hasValidDateFilter = !enableDateFilter.value || (importStartDate.value && importEndDate.value)

  // For OwnTracks, we don't need data type selection
  if (importFormat.value === 'owntracks') {
    return hasValidFile && hasValidDateFilter
  }

  // For GeoPulse, we need at least one data type selected
  return hasValidFile && importOptions.value.dataTypes.length > 0 && hasValidDateFilter
})

// Methods
const handleTabChange = (event) => {
  const selectedTab = tabItems.value[event.index]
  if (selectedTab) {
    activeTab.value = selectedTab.key
  }
}

const setDateRange = (days) => {
  const end = new Date()
  exportEndDate.value = end

  if (days === null) {
    // All time - set to a very early date
    exportStartDate.value = new Date('2020-01-01')
  } else {
    const start = new Date()
    start.setDate(start.getDate() - days)
    exportStartDate.value = start
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

const onFileSelect = (event) => {
  selectedFile.value = event.files[0]
}

const onFileClear = () => {
  selectedFile.value = null
}

const startImport = async () => {
  try {
    const options = {importFormat: importFormat.value}

    // Only add GeoPulse-specific options for GeoPulse imports
    if (importFormat.value === 'geopulse') {
      options.dataTypes = importOptions.value.dataTypes
    }

    if (enableDateFilter.value && importStartDate.value && importEndDate.value) {
      options.dateRangeFilter = {
        startDate: importStartDate.value.toISOString(),
        endDate: importEndDate.value.toISOString()
      }
    }

    if (importFormat.value === 'owntracks') {
      await exportImportStore.uploadOwnTracksImportFile(selectedFile.value, options)
    } else {
      await exportImportStore.uploadImportFile(selectedFile.value, options)
    }

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

const isJobExpired = (job) => {
  if (!job.expiresAt) return false
  return new Date() > new Date(job.expiresAt)
}

const formatDate = (dateString) => {
  return new Date(dateString).toLocaleString()
}

const formatDateRange = (dateRange) => {
  if (!dateRange) return 'All time'
  const start = new Date(dateRange.startDate).toLocaleDateString()
  const end = new Date(dateRange.endDate).toLocaleDateString()
  return `${start} - ${end}`
}

// Store utility methods
const {getDataTypeDisplayName, getFileSizeDisplay, getStatusDisplayInfo} = exportImportStore

// Initialize default date range (last 30 days)
const initializeDateRange = () => {
  setDateRange(30)
}

// Smart selection logic
const handleDataTypeChange = (dataTypes, isImport = false) => {
  // If timeline is selected, automatically select favorites and reverse geocoding data
  if (dataTypes.includes('timeline')) {
    if (!dataTypes.includes('favorites')) {
      dataTypes.push('favorites')
    }
    if (!dataTypes.includes('reversegeocodinglocation')) {
      dataTypes.push('reversegeocodinglocation')
    }
  }

  // If timeline is deselected, optionally deselect favorites and reverse geocoding (but only if user hasn't manually selected it)
  // For now, we'll keep them selected to be safe
}

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

// Watch for changes in export data types
watch(selectedDataTypes, (newDataTypes) => {
  handleDataTypeChange(newDataTypes, false)
}, {deep: true})

// Watch for changes in import data types
watch(() => importOptions.value.dataTypes, (newDataTypes) => {
  handleDataTypeChange(newDataTypes, true)
}, {deep: true})

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

// Lifecycle
onMounted(async () => {
  initializeDateRange()

  // Don't load job history on page load - history sections are hidden
  // try {
  //   await Promise.all([
  //     exportImportStore.fetchExportJobs(),
  //     exportImportStore.fetchImportJobs()
  //   ])
  // } catch (error) {
  //   console.error('Error loading job history:', error)
  // }
})
</script>

<style scoped>
.data-export-import-page {
  max-width: 1200px;
  margin: 0 auto;
  padding: 0 1rem;
  width: 100%;
  box-sizing: border-box;
}

@media (max-width: 430px) {
  .data-export-import-page {
    padding: 0 0.75rem;
    max-width: calc(100vw - 1.5rem);
    box-sizing: border-box;
  }
}

/* Page Header */
.page-header {
  margin-bottom: 2rem;
}

.header-content {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 2rem;
}

.header-text {
  flex: 1;
}

.page-title {
  font-size: 2rem;
  font-weight: 600;
  color: var(--gp-text-primary);
  margin: 0 0 0.5rem 0;
}

.page-description {
  font-size: 1.1rem;
  color: var(--gp-text-secondary);
  margin: 0;
  line-height: 1.5;
}

/* Info Banner */
.info-banner {
  margin-bottom: 2rem;
  background: var(--gp-surface-light);
  border: 1px solid var(--gp-border-medium);
  border-left: 4px solid var(--gp-primary);
  border-radius: var(--gp-radius-large);
}

.p-dark .info-banner {
  background: var(--gp-surface-dark) !important;
  border: 1px solid var(--gp-border-dark) !important;
  border-left: 4px solid var(--gp-primary) !important;
}

.banner-content {
  display: flex;
  align-items: flex-start;
  gap: 1rem;
}

.banner-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 2.5rem;
  height: 2.5rem;
  background: var(--gp-primary);
  color: white;
  border-radius: 50%;
  font-size: 1.25rem;
  flex-shrink: 0;
}

.banner-text {
  flex: 1;
}

.banner-title {
  font-size: 1.1rem;
  font-weight: 600;
  color: var(--gp-text-primary);
  margin: 0 0 0.5rem 0;
}

.banner-description {
  font-size: 0.9rem;
  color: var(--gp-text-secondary);
  margin: 0;
  line-height: 1.4;
}

/* Tab Sections */
.export-import-tabs {
  margin-bottom: 2rem;
}

.tab-section {
  padding: 0.5rem 0;
}

.section-header {
  margin-bottom: 2rem;
  text-align: center;
}

.section-title {
  font-size: 1.5rem;
  font-weight: 600;
  color: var(--gp-text-primary);
  margin: 0 0 0.5rem 0;
}

.section-description {
  font-size: 1rem;
  color: var(--gp-text-secondary);
  line-height: 1.5;
  max-width: 600px;
  margin: 0 auto;
}

/* Form Cards */
.export-form-card,
.import-form-card,
.job-status-card,
.export-history-card,
.import-history-card {
  margin-bottom: 2rem;
}

.export-form,
.import-form {
  display: flex;
  flex-direction: column;
  gap: 2rem;
}

.form-section {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.form-section-title {
  font-size: 1.1rem;
  font-weight: 600;
  color: var(--gp-text-primary);
  margin: 0;
}

/* Form Section Header */
.form-section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 1rem;
}

.form-section-header .form-section-title {
  margin: 0;
}

.select-all-button {
  flex-shrink: 0;
}

/* Option Group Header */
.option-group-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 0.75rem;
}

.option-group-header .option-label {
  margin-bottom: 0;
}

/* Data Types Grid */
.data-types-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
  gap: 1rem;
}

.data-type-option {
  display: flex;
  align-items: flex-start;
  gap: 0.75rem;
  padding: 1rem;
  border: 1px solid var(--gp-border-light);
  border-radius: var(--gp-radius-medium);
  background: var(--gp-surface-light);
  transition: all 0.2s ease;
}

.data-type-option:hover {
  border-color: var(--gp-primary);
  background: var(--gp-surface-hover);
}

.data-type-checkbox {
  margin-top: 0.25rem;
}

.data-type-info {
  flex: 1;
}

.data-type-label {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  font-weight: 600;
  color: var(--gp-text-primary);
  margin-bottom: 0.25rem;
  cursor: pointer;
}

.data-type-icon {
  color: var(--gp-primary);
}

.data-type-description {
  font-size: 0.9rem;
  color: var(--gp-text-secondary);
  margin: 0;
  line-height: 1.4;
}

/* Date Range Controls */
.date-range-controls {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 1rem;
}

.date-control {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.date-label {
  font-weight: 500;
  color: var(--gp-text-primary);
}

.date-picker {
  width: 100%;
}

/* Fix calendar icon color in dark mode */
.p-dark .date-picker :deep(.p-datepicker-dropdown .p-icon) {
  color: var(--gp-text-primary) !important;
}

.p-dark .date-picker :deep(.p-datepicker-dropdown) {
  background: var(--gp-surface-dark) !important;
  border-color: var(--gp-border-dark) !important;
}

.date-range-presets {
  display: flex;
  gap: 0.5rem;
  flex-wrap: wrap;
  margin-top: 0.5rem;
}

/* Format Selector */
.format-selector {
  width: 100%;
}

/* Form Actions */
.form-actions {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 1rem;
  padding-top: 1rem;
  border-top: 1px solid var(--gp-border-light);
}

.export-button,
.import-button {
  min-width: 200px;
}

.export-info,
.import-info {
  text-align: center;
}

.export-note,
.import-note {
  color: var(--gp-text-secondary);
  font-style: italic;
}

/* Job Status */
.job-status {
  display: flex;
  flex-direction: column;
  gap: 1.5rem;
}

.job-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.job-title {
  font-size: 1.1rem;
  font-weight: 600;
  color: var(--gp-text-primary);
  margin: 0;
}

.job-details {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.job-detail {
  display: flex;
  align-items: center;
  gap: 1rem;
}

.detail-label {
  font-weight: 500;
  color: var(--gp-text-secondary);
  min-width: 100px;
}

.detail-value {
  color: var(--gp-text-primary);
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.job-progress {
  width: 200px;
}

.progress-text {
  font-size: 0.9rem;
  font-weight: 500;
}

.job-actions {
  display: flex;
  gap: 1rem;
  justify-content: flex-start;
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

/* History */
.export-history,
.import-history {
  display: flex;
  flex-direction: column;
  gap: 1.5rem;
}

.history-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.history-title {
  font-size: 1.1rem;
  font-weight: 600;
  color: var(--gp-text-primary);
  margin: 0;
}

.empty-state {
  text-align: center;
  padding: 3rem 1rem;
  color: var(--gp-text-secondary);
}

.empty-icon {
  font-size: 3rem;
  margin-bottom: 1rem;
  opacity: 0.5;
}

.empty-text {
  font-size: 1.1rem;
  margin: 0;
}

/* Export/Import Lists */
.export-list,
.import-list {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.export-item,
.import-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 1rem;
  border: 1px solid var(--gp-border-light);
  border-radius: var(--gp-radius-medium);
  background: var(--gp-surface-light);
}

.export-item-info,
.import-item-info {
  flex: 1;
}

.export-item-header,
.import-item-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 0.5rem;
}

.export-item-date,
.import-item-date {
  font-weight: 500;
  color: var(--gp-text-primary);
}

.export-item-details,
.import-item-details {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.export-detail,
.import-detail {
  color: var(--gp-text-secondary);
  font-size: 0.9rem;
}

.export-size,
.import-size {
  color: var(--gp-text-tertiary);
  font-size: 0.8rem;
  font-weight: 500;
}

.export-item-actions,
.import-item-actions {
  display: flex;
  gap: 0.5rem;
  flex-shrink: 0;
}

/* Import Options */
.import-options {
  display: flex;
  flex-direction: column;
  gap: 1.5rem;
}

.option-group {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.checkbox-option {
  display: flex;
  align-items: center;
  gap: 0.75rem;
}

.checkbox-option .option-label {
  font-weight: 500;
  color: var(--gp-text-primary);
  cursor: pointer;
  margin: 0;
}

.option-group > .option-label {
  font-weight: 500;
  color: var(--gp-text-primary);
  display: block;
  margin-bottom: 0.5rem;
}

.option-description {
  color: var(--gp-text-secondary);
  margin-left: 2rem;
  font-size: 0.9rem;
  line-height: 1.4;
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

/* Format Options */
.format-options {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 1rem;
}

.format-option {
  display: flex;
  align-items: flex-start;
  gap: 0.75rem;
  padding: 1rem;
  border: 1px solid var(--gp-border-light);
  border-radius: var(--gp-radius-medium);
  background: var(--gp-surface-light);
  transition: all 0.2s ease;
  cursor: pointer;
}

.format-option:hover {
  border-color: var(--gp-primary);
  background: var(--gp-surface-hover);
}

.format-option.selected {
  border-color: var(--gp-primary);
  background: var(--gp-primary-50);
}

.format-radio {
  margin-top: 0.25rem;
}

.format-info {
  flex: 1;
}

.format-label {
  display: block;
  font-weight: 600;
  color: var(--gp-text-primary);
  margin-bottom: 0.25rem;
  cursor: pointer;
}

.format-description {
  font-size: 0.9rem;
  color: var(--gp-text-secondary);
  margin: 0;
  line-height: 1.4;
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

/* OwnTracks Info */
.owntracks-info {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.75rem;
  background: var(--gp-primary-50);
  border-radius: var(--gp-radius-small);
  color: var(--gp-primary-700);
  font-size: 0.9rem;
}

/* Responsive Design */
@media (max-width: 768px) {
  .data-export-import-page {
    padding: 0 1rem;
  }

  .page-title {
    font-size: 1.5rem;
  }

  .header-content {
    flex-direction: column;
    align-items: stretch;
    gap: 1.5rem;
  }

  .data-types-grid {
    grid-template-columns: 1fr;
  }

  .date-range-controls,
  .date-filter-controls {
    grid-template-columns: 1fr;
  }

  .date-range-presets {
    justify-content: center;
  }

  .job-detail {
    flex-direction: column;
    align-items: flex-start;
    gap: 0.5rem;
  }

  .detail-label {
    min-width: auto;
    font-size: 0.9rem;
  }

  .job-progress {
    width: 100%;
  }

  .export-item,
  .import-item {
    flex-direction: column;
    align-items: stretch;
    gap: 1rem;
  }

  .export-item-actions,
  .import-item-actions {
    justify-content: center;
  }

  .import-data-types {
    grid-template-columns: 1fr;
    margin-left: 0;
  }

  .banner-content {
    flex-direction: column;
    text-align: center;
    gap: 1rem;
  }
}

@media (max-width: 480px) {
  .data-export-import-page {
    padding: 0 0.75rem;
  }

  .page-title {
    font-size: 1.3rem;
  }

  .section-title {
    font-size: 1.3rem;
  }

  .section-description {
    font-size: 0.9rem;
  }

  .data-type-option {
    padding: 0.75rem;
  }

  .form-actions {
    gap: 0.75rem;
  }

  .export-button,
  .import-button {
    min-width: auto;
    width: 100%;
  }

  .job-actions {
    flex-direction: column;
    gap: 0.5rem;
  }

  .job-actions .p-button {
    width: 100%;
  }

  .export-item-header,
  .import-item-header {
    flex-direction: column;
    align-items: flex-start;
    gap: 0.5rem;
  }

  .export-item-details,
  .import-item-details {
    flex-direction: column;
    align-items: flex-start;
    gap: 0.25rem;
  }

  .option-description {
    margin-left: 0;
    margin-top: 0.25rem;
  }

  .date-filter-controls {
    margin-left: 0;
  }

  .format-options {
    grid-template-columns: 1fr;
  }
  
  .form-section-header,
  .option-group-header {
    flex-direction: column;
    align-items: flex-start;
    gap: 0.5rem;
  }
  
  .select-all-button {
    align-self: flex-end;
  }
}
</style>