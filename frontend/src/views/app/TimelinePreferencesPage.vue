<template>
  <AppLayout>
    <PageContainer>
      <div class="timeline-preferences-page">
        <!-- Page Header -->
        <div class="page-header">
          <div class="header-content">
            <div class="header-text">
              <h1 class="page-title">Timeline Preferences</h1>
              <p class="page-description">
                Fine-tune how your location timeline is generated from GPS data
              </p>
            </div>
            <div class="header-actions">
              <Button
                label="Export Config"
                icon="pi pi-download"
                severity="secondary"
                outlined
                @click="exportPreferences"
              />
              <Button
                label="Import Config"
                icon="pi pi-upload"
                severity="secondary"
                outlined
                @click="openImportPicker"
                :disabled="timelineRegenerationVisible"
              />
              <Button
                v-if="hasActiveJob"
                label="View Active Job"
                icon="pi pi-eye"
                severity="info"
                outlined
                @click="goToActiveJob"
                title="Check if timeline generation is currently running"
              />
              <Button
                label="Reset to Defaults"
                icon="pi pi-refresh"
                severity="secondary"
                outlined
                @click="confirmResetDefaults"
                :disabled="timelineRegenerationVisible"
              />
              <Button
                label="Regenerate Timeline"
                icon="pi pi-replay"
                severity="danger"
                outlined
                @click="confirmRegenerateTimeline"
                :disabled="timelineRegenerationVisible"
              />
              <Button
                label="Save Changes"
                icon="pi pi-save"
                @click="confirmSavePreferences"
                :disabled="!hasUnsavedChanges || !isFormValid || timelineRegenerationVisible"
              />
            </div>
          </div>
        </div>

        <!-- Info Banner -->
        <Card class="info-banner">
          <template #content>
            <div class="banner-content">
              <div class="banner-icon">
                <i class="pi pi-info-circle"></i>
              </div>
              <div class="banner-text">
                <h3 class="banner-title">How Timeline Processing Works</h3>
                <p class="banner-description">
                  Your GPS data is processed to identify meaningful stays and trips.
                  These settings control the sensitivity of this detection and apply only to your account.
                  Some changes (like speed thresholds) will quickly update trip classifications, while others may require full timeline re-generation depending on your GPS data volume.
                  <a href="https://tess1o.github.io/geopulse/docs/user-guide/core-features/timeline" target="_blank" rel="noopener noreferrer" class="documentation-link">
                    Learn more in the documentation <i class="pi pi-external-link"></i>
                  </a>
                </p>
              </div>
            </div>
          </template>
        </Card>

        <!-- Unsaved Changes Warning -->
        <Message v-if="hasUnsavedChanges" severity="warn" class="unsaved-warning">
          <div class="warning-content">
            <div class="warning-text">
              <i class="pi pi-exclamation-triangle mr-2"></i>
              You have unsaved changes
            </div>
            <div class="warning-actions">
              <Button 
                label="Discard" 
                size="small" 
                severity="secondary" 
                outlined
                @click="discardChanges" 
              />
              <Button 
                label="Save Now" 
                size="small" 
                @click="confirmSavePreferences"
                :disabled="timelineRegenerationVisible"
              />
            </div>
          </div>
        </Message>

        <!-- Preferences Tabs -->
        <TabContainer
          :tabs="tabItems"
          :activeIndex="activeTabIndex"
          @tab-change="handleTabChange"
          class="preferences-tabs"
        >
          <!-- Stay Point Detection Tab -->
          <StayPointDetectionTab
            v-if="activeTab === 'staypoints'"
            v-model="prefs"
          />

          <!-- Trip Classification Tab -->
          <TripClassificationTab
            v-if="activeTab === 'trips'"
            v-model="prefs"
            :get-warning-messages-for-type="getWarningMessagesForType"
          />

          <!-- GPS Gaps Detection Tab -->
          <GpsGapsDetectionTab
            v-if="activeTab === 'gpsgaps'"
            v-model="prefs"
          />

          <!-- Stay Point Merging Tab -->
          <StayPointMergingTab
            v-if="activeTab === 'merging'"
            v-model="prefs"
          />
        </TabContainer>

        <input
          ref="importFileInput"
          type="file"
          accept=".json,application/json"
          class="hidden-file-input"
          @change="handleImportFileChange"
        />

        <Dialog
          v-model:visible="importPreviewVisible"
          modal
          :closable="!isImportApplying"
          :dismissableMask="!isImportApplying"
          header="Import Timeline Configuration"
          class="import-preview-dialog"
        >
          <div class="import-preview-content">
            <p class="import-preview-description">
              Review all detected changes before applying this configuration.
            </p>

            <Message :severity="importImpactSeverity">
              {{ importImpactMessage }}
            </Message>

            <div class="import-preview-summary">
              <span class="summary-label">Detected changes:</span>
              <span class="summary-value">{{ importPreviewChanges.length }}</span>
            </div>

            <div class="import-table-wrapper">
              <table class="import-preview-table">
                <thead>
                  <tr>
                    <th>Setting</th>
                    <th>Current</th>
                    <th>Imported</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="item in importPreviewChanges" :key="item.key">
                    <td>
                      <div class="setting-label">{{ item.label }}</div>
                      <div class="setting-key">{{ item.key }}</div>
                    </td>
                    <td>{{ formatPreferenceValue(item.key, item.currentValue) }}</td>
                    <td>{{ formatPreferenceValue(item.key, item.importedValue) }}</td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>

          <template #footer>
            <Button
              label="Cancel"
              severity="secondary"
              outlined
              @click="closeImportPreview"
              :disabled="isImportApplying"
            />
            <Button
              label="Apply Import"
              icon="pi pi-check"
              @click="applyImportedPreferences"
              :loading="isImportApplying"
              :disabled="isImportApplying || importPreviewChanges.length === 0"
            />
          </template>
        </Dialog>

        <!-- Confirm Dialog -->
        <ConfirmDialog />
        <Toast />
        
        <!-- Timeline Regeneration Modal -->
        <TimelineRegenerationModal
          v-model:visible="timelineRegenerationVisible"
          :type="timelineRegenerationType"
          :job-id="currentJobId"
          :job-progress="jobProgress"
        />
      </div>
    </PageContainer>
  </AppLayout>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { storeToRefs } from 'pinia'
import { useToast } from 'primevue/usetoast'
import { useConfirm } from 'primevue/useconfirm'

// Layout components
import AppLayout from '@/components/ui/layout/AppLayout.vue'
import PageContainer from '@/components/ui/layout/PageContainer.vue'
import TabContainer from '@/components/ui/layout/TabContainer.vue'

// Tab components
import StayPointDetectionTab from '@/components/timeline-preferences/StayPointDetectionTab.vue'
import TripClassificationTab from '@/components/timeline-preferences/TripClassificationTab.vue'
import GpsGapsDetectionTab from '@/components/timeline-preferences/GpsGapsDetectionTab.vue'
import StayPointMergingTab from '@/components/timeline-preferences/StayPointMergingTab.vue'

// Custom components
import TimelineRegenerationModal from '@/components/dialogs/TimelineRegenerationModal.vue'

import { useTimelinePreferencesStore } from '@/stores/timelinePreferences'
import { useTimelineStore } from '@/stores/timeline'
import { useTimelineRegeneration } from '@/composables/useTimelineRegeneration'
import { useClassificationValidation } from '@/composables/useClassificationValidation'
import { useTimelineJobCheck } from '@/composables/useTimelineJobCheck'

const TIMELINE_PREFERENCES_SCHEMA_VERSION = 'timeline-preferences.v1'

const TIMELINE_PREFERENCE_LABELS = {
  staypointRadiusMeters: 'Stay Detection Radius',
  staypointMinDurationMinutes: 'Minimum Stay Duration',
  useVelocityAccuracy: 'Enhanced Filtering',
  staypointVelocityThreshold: 'Velocity Threshold',
  staypointMaxAccuracyThreshold: 'GPS Accuracy Threshold',
  staypointMinAccuracyRatio: 'Minimum Accuracy Ratio',
  tripDetectionAlgorithm: 'Trip Detection Algorithm',
  walkingMaxAvgSpeed: 'Walking Maximum Average Speed',
  walkingMaxMaxSpeed: 'Walking Maximum Peak Speed',
  carEnabled: 'Car Detection Enabled',
  carMinAvgSpeed: 'Car Minimum Average Speed',
  carMinMaxSpeed: 'Car Minimum Peak Speed',
  shortDistanceKm: 'Short Trip Distance Threshold',
  bicycleEnabled: 'Bicycle Detection Enabled',
  bicycleMinAvgSpeed: 'Bicycle Minimum Average Speed',
  bicycleMaxAvgSpeed: 'Bicycle Maximum Average Speed',
  bicycleMaxMaxSpeed: 'Bicycle Maximum Peak Speed',
  runningEnabled: 'Running Detection Enabled',
  runningMinAvgSpeed: 'Running Minimum Average Speed',
  runningMaxAvgSpeed: 'Running Maximum Average Speed',
  runningMaxMaxSpeed: 'Running Maximum Peak Speed',
  trainEnabled: 'Train Detection Enabled',
  trainMinAvgSpeed: 'Train Minimum Average Speed',
  trainMaxAvgSpeed: 'Train Maximum Average Speed',
  trainMinMaxSpeed: 'Train Minimum Peak Speed',
  trainMaxMaxSpeed: 'Train Maximum Peak Speed',
  trainMaxSpeedVariance: 'Train Maximum Speed Variance',
  flightEnabled: 'Flight Detection Enabled',
  flightMinAvgSpeed: 'Flight Minimum Average Speed',
  flightMinMaxSpeed: 'Flight Minimum Peak Speed',
  tripArrivalDetectionMinDurationSeconds: 'Arrival Detection Duration',
  tripSustainedStopMinDurationSeconds: 'Sustained Stop Duration',
  tripArrivalMinPoints: 'Minimum Stop Points for Arrival Detection',
  isMergeEnabled: 'Stay Point Merging Enabled',
  mergeMaxDistanceMeters: 'Maximum Merge Distance',
  mergeMaxTimeGapMinutes: 'Maximum Merge Time Gap',
  dataGapThresholdSeconds: 'Data Gap Threshold',
  dataGapMinDurationSeconds: 'Minimum Gap Duration',
  gapStayInferenceEnabled: 'Gap Stay Inference',
  gapStayInferenceMaxGapHours: 'Gap Stay Inference Max Gap Duration',
  gapTripInferenceEnabled: 'Gap Trip Inference',
  gapTripInferenceMinDistanceMeters: 'Gap Trip Inference Minimum Distance',
  gapTripInferenceMinGapHours: 'Gap Trip Inference Minimum Gap Duration',
  gapTripInferenceMaxGapHours: 'Gap Trip Inference Maximum Gap Duration'
}

const CLASSIFICATION_FIELDS = [
  'walkingMaxAvgSpeed', 'walkingMaxMaxSpeed',
  'carEnabled',
  'carMinAvgSpeed', 'carMinMaxSpeed', 'shortDistanceKm',
  'bicycleEnabled', 'bicycleMinAvgSpeed', 'bicycleMaxAvgSpeed', 'bicycleMaxMaxSpeed',
  'runningEnabled', 'runningMinAvgSpeed', 'runningMaxAvgSpeed', 'runningMaxMaxSpeed',
  'trainEnabled', 'trainMinAvgSpeed', 'trainMaxAvgSpeed', 'trainMinMaxSpeed',
  'trainMaxMaxSpeed', 'trainMaxSpeedVariance',
  'flightEnabled', 'flightMinAvgSpeed', 'flightMinMaxSpeed'
]

const STRUCTURAL_FIELDS = [
  'staypointVelocityThreshold', 'staypointRadiusMeters',
  'staypointMinDurationMinutes', 'tripDetectionAlgorithm',
  'useVelocityAccuracy', 'staypointMaxAccuracyThreshold', 'staypointMinAccuracyRatio',
  'isMergeEnabled', 'mergeMaxDistanceMeters', 'mergeMaxTimeGapMinutes',
  'dataGapThresholdSeconds', 'dataGapMinDurationSeconds',
  'gapStayInferenceEnabled', 'gapStayInferenceMaxGapHours',
  'gapTripInferenceEnabled', 'gapTripInferenceMinDistanceMeters',
  'gapTripInferenceMinGapHours', 'gapTripInferenceMaxGapHours',
  'tripArrivalDetectionMinDurationSeconds', 'tripSustainedStopMinDurationSeconds',
  'tripArrivalMinPoints'
]

const MANAGED_TIMELINE_PREFERENCE_KEYS = Object.keys(TIMELINE_PREFERENCE_LABELS)

const PREFERENCE_VALUE_TYPES = {
  staypointRadiusMeters: 'number',
  staypointMinDurationMinutes: 'number',
  useVelocityAccuracy: 'boolean',
  staypointVelocityThreshold: 'number',
  staypointMaxAccuracyThreshold: 'number',
  staypointMinAccuracyRatio: 'number',
  tripDetectionAlgorithm: 'string',
  walkingMaxAvgSpeed: 'number',
  walkingMaxMaxSpeed: 'number',
  carEnabled: 'boolean',
  carMinAvgSpeed: 'number',
  carMinMaxSpeed: 'number',
  shortDistanceKm: 'number',
  bicycleEnabled: 'boolean',
  bicycleMinAvgSpeed: 'number',
  bicycleMaxAvgSpeed: 'number',
  bicycleMaxMaxSpeed: 'number',
  runningEnabled: 'boolean',
  runningMinAvgSpeed: 'number',
  runningMaxAvgSpeed: 'number',
  runningMaxMaxSpeed: 'number',
  trainEnabled: 'boolean',
  trainMinAvgSpeed: 'number',
  trainMaxAvgSpeed: 'number',
  trainMinMaxSpeed: 'number',
  trainMaxMaxSpeed: 'number',
  trainMaxSpeedVariance: 'number',
  flightEnabled: 'boolean',
  flightMinAvgSpeed: 'number',
  flightMinMaxSpeed: 'number',
  tripArrivalDetectionMinDurationSeconds: 'number',
  tripSustainedStopMinDurationSeconds: 'number',
  tripArrivalMinPoints: 'number',
  isMergeEnabled: 'boolean',
  mergeMaxDistanceMeters: 'number',
  mergeMaxTimeGapMinutes: 'number',
  dataGapThresholdSeconds: 'number',
  dataGapMinDurationSeconds: 'number',
  gapStayInferenceEnabled: 'boolean',
  gapStayInferenceMaxGapHours: 'number',
  gapTripInferenceEnabled: 'boolean',
  gapTripInferenceMinDistanceMeters: 'number',
  gapTripInferenceMinGapHours: 'number',
  gapTripInferenceMaxGapHours: 'number'
}

const PREFERENCE_UNITS = {
  staypointRadiusMeters: 'm',
  staypointMinDurationMinutes: 'min',
  staypointVelocityThreshold: 'km/h',
  staypointMaxAccuracyThreshold: 'm',
  walkingMaxAvgSpeed: 'km/h',
  walkingMaxMaxSpeed: 'km/h',
  carMinAvgSpeed: 'km/h',
  carMinMaxSpeed: 'km/h',
  shortDistanceKm: 'km',
  bicycleMinAvgSpeed: 'km/h',
  bicycleMaxAvgSpeed: 'km/h',
  bicycleMaxMaxSpeed: 'km/h',
  runningMinAvgSpeed: 'km/h',
  runningMaxAvgSpeed: 'km/h',
  runningMaxMaxSpeed: 'km/h',
  trainMinAvgSpeed: 'km/h',
  trainMaxAvgSpeed: 'km/h',
  trainMinMaxSpeed: 'km/h',
  trainMaxMaxSpeed: 'km/h',
  flightMinAvgSpeed: 'km/h',
  flightMinMaxSpeed: 'km/h',
  tripArrivalDetectionMinDurationSeconds: 's',
  tripSustainedStopMinDurationSeconds: 's',
  tripArrivalMinPoints: 'points',
  mergeMaxDistanceMeters: 'm',
  mergeMaxTimeGapMinutes: 'min',
  dataGapThresholdSeconds: 's',
  dataGapMinDurationSeconds: 's',
  gapStayInferenceMaxGapHours: 'hours',
  gapTripInferenceMinGapHours: 'hours',
  gapTripInferenceMaxGapHours: 'hours'
}

// Store
const router = useRouter()
const route = useRoute()
const toast = useToast()
const confirm = useConfirm()
const timelinePreferencesStore = useTimelinePreferencesStore()
const timelineStore = useTimelineStore()
const { checkActiveJob } = useTimelineJobCheck()

// Composables
const {
  timelineRegenerationVisible,
  timelineRegenerationType,
  currentJobId,
  jobProgress,
  withTimelineRegeneration
} = useTimelineRegeneration()

// Validation
const {
  validationWarnings,
  hasWarnings,
  hasErrors,
  getWarningMessagesForType
} = useClassificationValidation(computed(() => prefs.value))

// Store refs
const { timelinePreferences: originalPrefs } = storeToRefs(timelinePreferencesStore)

// State - Initialize from URL query parameter
const activeTab = ref(route.query.tab || 'staypoints')

// Tab configuration
const tabItems = ref([
  {
    label: 'Stay Point Detection',
    icon: 'pi pi-map-marker',
    key: 'staypoints'
  },
  {
    label: 'Trip Classification',
    icon: 'pi pi-route',
    key: 'trips'
  },
  {
    label: 'GPS Gaps Detection',
    icon: 'pi pi-exclamation-circle',
    key: 'gpsgaps'
  },
  {
    label: 'Stay Point Merging',
    icon: 'pi pi-sitemap',
    key: 'merging'
  }
])

const activeTabIndex = computed(() => {
  return tabItems.value.findIndex(tab => tab.key === activeTab.value)
})

const prefs = ref({})
const importFileInput = ref(null)
const importPreviewVisible = ref(false)
const importPreviewChanges = ref([])
const importChangesPayload = ref({})
const importSaveType = ref('full')
const isImportApplying = ref(false)
const detectedActiveJobId = ref(null)
const checkingActiveJob = ref(false)
let activeJobPollingTimer = null

// Computed
const hasUnsavedChanges = computed(() => {
  return JSON.stringify(prefs.value) !== JSON.stringify(originalPrefs.value)
})

const isFormValid = computed(() => {
  // Basic validation - can be extended
  return Object.values(prefs.value).every(val => val !== null && val !== undefined)
})

const hasActiveJob = computed(() => {
  return Boolean(currentJobId.value || detectedActiveJobId.value)
})

// Methods
const handleTabChange = (event) => {
  const selectedTab = tabItems.value[event.index]
  if (selectedTab) {
    activeTab.value = selectedTab.key
    // Update URL with tab query parameter
    router.push({ query: { ...route.query, tab: selectedTab.key } })
  }
}

const getManagedPreferencesFromSource = (source = {}) => {
  const managed = {}
  for (const key of MANAGED_TIMELINE_PREFERENCE_KEYS) {
    const value = source?.[key]
    if (value !== undefined && value !== null) {
      managed[key] = value
    }
  }
  return managed
}

const getChangedPrefsFromSource = (source) => {
  const changed = {}
  for (const [key, currentValue] of Object.entries(source)) {
    if (currentValue === null || currentValue === undefined) {
      continue
    }
    if (currentValue !== originalPrefs.value?.[key]) {
      changed[key] = currentValue
    }
  }
  return changed
}

const getChangedPrefs = () => {
  return getChangedPrefsFromSource(prefs.value)
}

const importImpactSeverity = computed(() => {
  return importSaveType.value === 'classification' ? 'info' : 'warn'
})

const importImpactMessage = computed(() => {
  if (importSaveType.value === 'classification') {
    return 'This import will update classification parameters only and recalculate trip movement types.'
  }
  return 'This import includes structural settings and will trigger full timeline regeneration after save.'
})

const formatPreferenceValue = (key, value) => {
  if (value === null || value === undefined) {
    return 'Not set'
  }

  if (typeof value === 'boolean') {
    return value ? 'Enabled' : 'Disabled'
  }

  if (key === 'tripDetectionAlgorithm') {
    if (value === 'single') return 'Single trip'
    if (value === 'multiple') return 'Multiple trips'
  }

  if (key === 'staypointMinAccuracyRatio' && typeof value === 'number') {
    return `${Math.round(value * 100)}%`
  }

  if (key === 'gapTripInferenceMinDistanceMeters' && typeof value === 'number') {
    const kilometers = value / 1000
    return `${kilometers} km`
  }

  const unit = PREFERENCE_UNITS[key]
  if (unit && typeof value === 'number') {
    return `${value} ${unit}`
  }

  return String(value)
}

const buildSaveTypeForChanges = (changes) => {
  const hasClassificationChanges = hasClassificationParameters(changes)
  const hasStructuralChanges = hasStructuralParameters(changes)
  return hasClassificationChanges && !hasStructuralChanges ? 'classification' : 'full'
}

const openImportPicker = () => {
  importFileInput.value?.click()
}

const closeImportPreview = () => {
  importPreviewVisible.value = false
  importPreviewChanges.value = []
  importChangesPayload.value = {}
  importSaveType.value = 'full'
}

const exportPreferences = () => {
  const effectivePrefs = getManagedPreferencesFromSource(originalPrefs.value)
  if (Object.keys(effectivePrefs).length === 0) {
    toast.add({
      severity: 'warn',
      summary: 'Nothing to Export',
      detail: 'No timeline preferences are currently available.',
      life: 3000
    })
    return
  }

  const payload = {
    schemaVersion: TIMELINE_PREFERENCES_SCHEMA_VERSION,
    exportedAt: new Date().toISOString(),
    preferences: effectivePrefs
  }

  if (import.meta.env.VITE_APP_VERSION) {
    payload.appVersion = import.meta.env.VITE_APP_VERSION
  }

  const blob = new Blob([JSON.stringify(payload, null, 2)], { type: 'application/json' })
  const url = URL.createObjectURL(blob)
  const timestamp = new Date().toISOString().replace(/[:.]/g, '-')
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = `timeline-preferences.v1.${timestamp}.json`
  document.body.appendChild(anchor)
  anchor.click()
  document.body.removeChild(anchor)
  URL.revokeObjectURL(url)

  toast.add({
    severity: 'success',
    summary: 'Export Complete',
    detail: 'Timeline configuration exported successfully.',
    life: 3000
  })
}

const sanitizeImportedPreferences = (rawPreferences) => {
  const sanitized = {}
  for (const key of MANAGED_TIMELINE_PREFERENCE_KEYS) {
    const value = rawPreferences?.[key]
    if (value === undefined || value === null) {
      continue
    }
    const expectedType = PREFERENCE_VALUE_TYPES[key]
    if (expectedType && typeof value === expectedType) {
      if (key === 'tripDetectionAlgorithm' && !['single', 'multiple'].includes(value)) {
        continue
      }
      sanitized[key] = value
    }
  }
  return sanitized
}

const handleImportFileChange = async (event) => {
  const file = event.target?.files?.[0]
  event.target.value = ''

  if (!file) {
    return
  }

  try {
    const raw = await file.text()
    const parsed = JSON.parse(raw)

    if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) {
      throw new Error('Invalid import file format.')
    }
    if (parsed.schemaVersion !== TIMELINE_PREFERENCES_SCHEMA_VERSION) {
      throw new Error(`Unsupported schema version. Expected ${TIMELINE_PREFERENCES_SCHEMA_VERSION}.`)
    }
    if (!parsed.preferences || typeof parsed.preferences !== 'object' || Array.isArray(parsed.preferences)) {
      throw new Error('Import file does not contain a valid preferences object.')
    }

    const importedManaged = sanitizeImportedPreferences(parsed.preferences)
    if (Object.keys(importedManaged).length === 0) {
      throw new Error('Import file contains no compatible timeline preference fields.')
    }

    const effectivePrefs = originalPrefs.value || {}
    const changedEntries = Object.entries(importedManaged)
      .filter(([key, importedValue]) => effectivePrefs[key] !== importedValue)
      .map(([key, importedValue]) => ({
        key,
        label: TIMELINE_PREFERENCE_LABELS[key] || key,
        currentValue: effectivePrefs[key],
        importedValue
      }))
      .sort((a, b) => a.label.localeCompare(b.label))

    if (changedEntries.length === 0) {
      toast.add({
        severity: 'info',
        summary: 'No Changes',
        detail: 'Imported configuration matches your current effective settings.',
        life: 3500
      })
      return
    }

    const changedPayload = Object.fromEntries(changedEntries.map(entry => [entry.key, entry.importedValue]))

    importPreviewChanges.value = changedEntries
    importChangesPayload.value = changedPayload
    importSaveType.value = buildSaveTypeForChanges(changedPayload)
    importPreviewVisible.value = true
  } catch (error) {
    console.error('Failed to import timeline preferences file:', error)
    toast.add({
      severity: 'error',
      summary: 'Import Failed',
      detail: error.message || 'Failed to parse imported configuration file.',
      life: 5000
    })
  }
}

const applyImportedPreferences = async () => {
  if (isImportApplying.value) {
    return
  }

  const changes = importChangesPayload.value
  if (!changes || Object.keys(changes).length === 0) {
    closeImportPreview()
    return
  }

  isImportApplying.value = true
  importPreviewVisible.value = false

  try {
    await savePreferences(importSaveType.value, changes)
  } finally {
    isImportApplying.value = false
    closeImportPreview()
  }
}

const loadPreferences = async () => {
  try {
    await timelinePreferencesStore.fetchTimelinePreferences()
  } catch (error) {
    console.error('Error loading preferences:', error)
    toast.add({
      severity: 'error',
      summary: 'Loading Failed',
      detail: 'Failed to load timeline preferences',
      life: 5000
    })
  }
}

const refreshActiveJob = async () => {
  if (currentJobId.value) {
    detectedActiveJobId.value = currentJobId.value
    return
  }

  if (checkingActiveJob.value) {
    return
  }

  checkingActiveJob.value = true
  try {
    const activeJobInfo = await checkActiveJob()
    detectedActiveJobId.value = activeJobInfo?.hasActiveJob ? activeJobInfo.jobId : null
  } finally {
    checkingActiveJob.value = false
  }
}

const confirmSavePreferences = () => {
  if (!isFormValid.value) {
    return;
  }

  const changes = getChangedPrefs()
  if (Object.keys(changes).length === 0) {
    toast.add({
      severity: 'info',
      summary: 'No Changes',
      detail: 'No preferences were modified',
      life: 3000
    })
    return
  }

  // Categorize changes
  const hasClassificationChanges = hasClassificationParameters(changes)
  const hasStructuralChanges = hasStructuralParameters(changes)
  
  if (hasClassificationChanges && !hasStructuralChanges) {
    // Fast path - classification only
    confirm.require({
      message: 'These changes will recalculate movement types for your existing trips. Do you want to proceed?',
      header: 'Update Trip Classifications',
      icon: 'pi pi-refresh',
      rejectProps: {
        label: 'Cancel',
        severity: 'secondary',
        outlined: true
      },
      acceptProps: {
        label: 'Update Classifications',
        severity: 'success'
      },
      accept: () => savePreferences('classification', changes)
    })
  } else {
    // Full regeneration path (current behavior)
    confirm.require({
      message: 'Changing these timeline preferences will trigger a complete re-generation of all your timeline data according to the new settings. This process may take some time depending on the volume of your GPS data. Do you want to proceed?',
      header: 'Save Timeline Preferences', 
      icon: 'pi pi-exclamation-triangle',
      rejectProps: {
        label: 'Cancel',
        severity: 'secondary',
        outlined: true
      },
      acceptProps: {
        label: 'Save & Regenerate',
        severity: 'primary'
      },
      accept: () => savePreferences('full', changes)
    })
  }
}

// Parameter categorization functions
const hasClassificationParameters = (changes) => {
  return CLASSIFICATION_FIELDS.some(field => field in changes)
}

/**
 * Check if changes contain structural parameters that require timeline regeneration.
 * NOTE: Path simplification fields removed - they are now display-only settings
 * managed via User Profile > Timeline Display tab.
 */
const hasStructuralParameters = (changes) => {
  return STRUCTURAL_FIELDS.some(field => field in changes)
}

const savePreferences = async (saveType = 'full', explicitChanges = null) => {
  if (!isFormValid.value && !explicitChanges) return

  const changes = explicitChanges || getChangedPrefs()
  if (!changes || Object.keys(changes).length === 0) {
    toast.add({
      severity: 'info',
      summary: 'No Changes',
      detail: 'No preferences were modified',
      life: 3000
    })
    return
  }

  if (saveType === 'classification') {
    // Fast path: classification-only updates don't need job tracking
    try {
      await timelinePreferencesStore.updateTimelinePreferences(changes)
      toast.add({
        severity: 'success',
        summary: 'Success',
        detail: 'Trip classifications updated successfully.',
        life: 3000
      })
      await loadPreferences()
    } catch (error) {
      console.error('Failed to save preferences:', error)
      toast.add({
        severity: 'error',
        summary: 'Error',
        detail: error.message || 'Failed to save preferences.',
        life: 5000
      })
    }
  } else {
    // Full regeneration path: requires job tracking
    const action = () => {
      return timelinePreferencesStore.updateTimelinePreferences(changes)
    }

    withTimelineRegeneration(
      action,
      {
        modalType: 'preferences',
        successMessage: 'Preferences saved and timeline regeneration started.',
        errorMessage: 'Failed to save preferences.',
        onSuccess: loadPreferences
      }
    )
  }
}

const confirmResetDefaults = () => {
  confirm.require({
    message: 'This will reset all settings to their default values. Are you sure?',
    header: 'Reset to Defaults',
    icon: 'pi pi-exclamation-triangle',
    rejectProps: {
      label: 'Cancel',
      severity: 'secondary',
      outlined: true
    },
    acceptProps: {
      label: 'Reset',
      severity: 'danger'
    },
    accept: resetDefaults
  })
}

const resetDefaults = () => {
  withTimelineRegeneration(
    () => timelinePreferencesStore.resetTimelinePreferencesToDefaults(),
    {
      modalType: 'preferences',
      successMessage: 'Preferences reset and timeline regeneration started.',
      errorMessage: 'Failed to reset preferences.',
      onSuccess: loadPreferences
    }
  )
}

const discardChanges = () => {
  if (originalPrefs.value) {
    prefs.value = { ...originalPrefs.value }
    toast.add({
      severity: 'info',
      summary: 'Changes Discarded',
      detail: 'All unsaved changes have been discarded',
      life: 3000
    })
  }
}

const confirmRegenerateTimeline = () => {
  confirm.require({
    message: 'This will completely delete your current timeline data and regenerate it from scratch.\n\nThis operation may take several minutes depending on your GPS data volume.\n\nDo you want to proceed?',
    header: 'Regenerate Complete Timeline',
    icon: 'pi pi-exclamation-triangle',
    rejectProps: {
      label: 'Cancel',
      severity: 'secondary',
      outlined: true
    },
    acceptProps: {
      label: 'Regenerate Timeline',
      severity: 'danger'
    },
    accept: regenerateTimeline
  })
}

const regenerateTimeline = () => {
  withTimelineRegeneration(
    () => timelineStore.regenerateAllTimeline(),
    {
      modalType: 'general',
      successMessage: 'Timeline regeneration started.',
      errorMessage: 'Failed to start timeline regeneration.'
    }
  )
}

const goToActiveJob = () => {
  const jobId = currentJobId.value || detectedActiveJobId.value
  if (jobId) {
    router.push(`/app/timeline/jobs/${jobId}`)
    return
  }

  router.push('/app/timeline/jobs')
}

watch(originalPrefs, (newVal) => {
  if (newVal) {
    prefs.value = { ...newVal }
  }
}, { immediate: true })

watch(currentJobId, (newJobId) => {
  if (newJobId) {
    detectedActiveJobId.value = newJobId
  } else {
    refreshActiveJob()
  }
})

// Watch for URL changes and validate tab parameter
watch(() => route.query.tab, (newTab) => {
  const validTabs = tabItems.value.map(t => t.key)
  if (newTab && validTabs.includes(newTab)) {
    activeTab.value = newTab
  } else if (newTab && !validTabs.includes(newTab)) {
    // Invalid tab, redirect to default
    router.replace({ query: { ...route.query, tab: 'staypoints' } })
    activeTab.value = 'staypoints'
  }
})

// Lifecycle
onMounted(() => {
  loadPreferences()
  refreshActiveJob()
  activeJobPollingTimer = window.setInterval(refreshActiveJob, 15000)

  // Validate initial tab from URL
  const validTabs = tabItems.value.map(t => t.key)
  const initialTab = route.query.tab
  if (initialTab && !validTabs.includes(initialTab)) {
    router.replace({ query: { ...route.query, tab: 'staypoints' } })
    activeTab.value = 'staypoints'
  } else if (initialTab) {
    activeTab.value = initialTab
  }
})

onUnmounted(() => {
  if (activeJobPollingTimer) {
    window.clearInterval(activeJobPollingTimer)
    activeJobPollingTimer = null
  }
})
</script>

<style scoped>
.timeline-preferences-page {
  max-width: 1200px;
  margin: 0 auto;
  padding: 0 1rem;
  width: 100%;
  box-sizing: border-box;
}

@media (max-width: 430px) {
  .timeline-preferences-page {
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

.header-actions {
  display: flex;
  gap: 1rem;
  flex-shrink: 0;
  flex-wrap: wrap;
  justify-content: flex-end;
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
  margin-bottom: 2rem;
  background: var(--gp-surface-dark) !important;
  border: 1px solid var(--gp-border-dark) !important;
  border-left: 1px solid var(--gp-border-dark) !important;
  border-radius: var(--gp-radius-large) !important;
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

.documentation-link {
  display: inline-flex;
  align-items: center;
  gap: 0.25rem;
  margin-left: 0.5rem;
  color: var(--gp-primary);
  text-decoration: none;
  font-weight: 500;
}

.documentation-link:hover {
  text-decoration: underline;
}

.documentation-link i {
  font-size: 0.75rem;
}

/* Unsaved Changes Warning */
.unsaved-warning {
  margin-bottom: 2rem;
}

.warning-content {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 1rem;
}

.warning-text {
  display: flex;
  align-items: center;
  font-weight: 500;
}

.warning-actions {
  display: flex;
  gap: 0.5rem;
  flex-shrink: 0;
}

/* Preferences Tabs */
.preferences-tabs {
  margin-bottom: 2rem;
}

.hidden-file-input {
  display: none;
}

.import-preview-dialog {
  width: min(920px, 95vw);
}

.import-preview-content {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.import-preview-description {
  margin: 0;
  color: var(--gp-text-secondary);
}

.import-preview-summary {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.summary-label {
  color: var(--gp-text-secondary);
}

.summary-value {
  font-weight: 600;
  color: var(--gp-text-primary);
}

.import-table-wrapper {
  max-height: 420px;
  overflow: auto;
  border: 1px solid var(--gp-border-medium);
  border-radius: var(--gp-radius-medium);
}

.import-preview-table {
  width: 100%;
  border-collapse: collapse;
}

.import-preview-table th,
.import-preview-table td {
  text-align: left;
  padding: 0.75rem;
  border-bottom: 1px solid var(--gp-border-light);
  vertical-align: top;
}

.import-preview-table th {
  position: sticky;
  top: 0;
  z-index: 1;
  background: var(--gp-surface-light);
  color: var(--gp-text-secondary);
  font-size: 0.85rem;
  font-weight: 600;
}

.setting-label {
  font-weight: 600;
  color: var(--gp-text-primary);
}

.setting-key {
  margin-top: 0.2rem;
  font-size: 0.75rem;
  color: var(--gp-text-secondary);
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", "Courier New", monospace;
}

/* Input and Button Styling */
:deep(.p-dropdown) {
  border-radius: var(--gp-radius-medium);
  border: 1px solid var(--gp-border-medium);
  width: 100%;
  max-width: 100%;
  box-sizing: border-box;
}

:deep(.p-dropdown:focus) {
  border-color: var(--gp-primary);
  box-shadow: 0 0 0 3px rgba(26, 86, 219, 0.1);
}

:deep(.p-toggleswitch) {
  width: 3rem;
  height: 1.75rem;
}

:deep(.p-toggleswitch.p-toggleswitch-checked .p-toggleswitch-slider) {
  background: var(--gp-primary);
}

:deep(.p-button) {
  border-radius: var(--gp-radius-medium);
  font-weight: 600;
  transition: all 0.2s ease;
}

:deep(.p-button:not(.p-button-outlined)) {
  background: var(--gp-primary);
  border-color: var(--gp-primary);
}

:deep(.p-button:not(.p-button-outlined):hover) {
  background: var(--gp-primary-hover);
  border-color: var(--gp-primary-hover);
  transform: translateY(-1px);
  box-shadow: var(--gp-shadow-medium);
}

/* Danger button styling */
:deep(.p-button.p-button-danger.p-button-outlined) {
  border-color: #dc3545;
  color: #dc3545;
}

:deep(.p-button.p-button-danger.p-button-outlined:hover) {
  background: #dc3545;
  color: white;
  transform: translateY(-1px);
  box-shadow: var(--gp-shadow-medium);
}

/* Responsive Design */
@media (max-width: 768px) {
  .timeline-preferences-page {
    padding: 0 1rem;
    margin: 0 auto;
    width: 100%;
    max-width: 100vw;
    box-sizing: border-box;
  }
  
  .page-title {
    font-size: 1.5rem;
  }
  
  .header-content {
    flex-direction: column;
    align-items: stretch;
    gap: 1.5rem;
  }
  
  .header-actions {
    justify-content: stretch;
    flex-wrap: wrap;
  }
  
  .header-actions .p-button {
    flex: 1;
    min-height: 44px;
  }

  .import-preview-dialog {
    width: 96vw;
  }

  .import-table-wrapper {
    max-height: 340px;
  }

  .import-preview-table th,
  .import-preview-table td {
    padding: 0.65rem;
    font-size: 0.85rem;
  }
  
  .banner-content {
    flex-direction: column;
    text-align: center;
    gap: 1rem;
  }
  
  .warning-content {
    flex-direction: column;
    align-items: stretch;
    gap: 1rem;
  }
  
  .warning-actions {
    justify-content: center;
  }
  
  .warning-actions .p-button {
    min-height: 44px;
  }
  
  .section-header {
    text-align: left;
    margin-bottom: 1.5rem;
  }
  
  .section-title {
    font-size: 1.3rem;
  }
  
  .section-description {
    font-size: 0.9rem;
    max-width: 100%;
    padding: 0 0.5rem;
    word-wrap: break-word;
    overflow-wrap: break-word;
  }
  
  .preferences-section {
    padding: 1.5rem 0;
  }
  
  .settings-grid {
    gap: 1.25rem;
    padding: 0;
    margin: 0;
  }
  
  .preferences-section {
    padding: 1.5rem 0;
    width: 100%;
    overflow: hidden;
  }
  
  :deep(.p-tabs-tab) {
    padding: 1rem 0.75rem;
    font-size: 0.85rem;
    min-height: 44px;
    display: flex;
    align-items: center;
    justify-content: center;
  }
  
  :deep(.p-tabs-nav) {
    justify-content: space-around;
  }
  
  :deep(.p-tabs-tab .p-tabs-tab-content) {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 0.25rem;
  }
}

@media (max-width: 480px) {
  .timeline-preferences-page {
    padding: 0 0.75rem;
    max-width: calc(100vw - 1.5rem);
  }
  
  .page-header {
    margin-bottom: 1.5rem;
  }
  
  .page-title {
    font-size: 1.3rem;
  }
  
  .page-description {
    font-size: 1rem;
  }
  
  .header-actions {
    flex-direction: column;
    gap: 0.75rem;
  }
  
  .header-actions .p-button {
    width: 100%;
    min-height: 48px;
    font-size: 0.95rem;
  }

  .import-preview-table th,
  .import-preview-table td {
    padding: 0.55rem;
    font-size: 0.8rem;
  }

  .setting-key {
    font-size: 0.7rem;
  }
  
  .banner-icon {
    margin: 0 auto;
    width: 2rem;
    height: 2rem;
    font-size: 1rem;
  }
  
  .banner-title {
    font-size: 1rem;
  }
  
  .banner-description {
    font-size: 0.85rem;
  }
  
  .section-title {
    font-size: 1.2rem;
  }
  
  .section-description {
    font-size: 0.85rem;
    max-width: 100%;
    padding: 0;
    margin: 0;
    word-wrap: break-word;
    overflow-wrap: break-word;
    line-height: 1.4;
  }
  
  .control-value {
    min-width: 0;
    font-size: 0.85rem;
    padding: 0.4rem 0.8rem;
    width: 100%;
    max-width: 100%;
    margin-bottom: 0.75rem;
  }
  
  :deep(.p-dropdown) {
    width: 100%;
    max-width: 100%;
    font-size: 0.9rem;
  }
  
  :deep(.p-dropdown .p-dropdown-label) {
    padding: 0.6rem 0.8rem;
    font-size: 0.9rem;
  }
  
  :deep(.p-toggleswitch) {
    align-self: center;
  }
  
  :deep(.p-tabs-tab) {
    padding: 0.75rem 0.5rem;
    font-size: 0.8rem;
    min-height: 48px;
  }
  
  :deep(.p-tabs-tab .pi) {
    font-size: 0.9rem;
  }
}

/* Responsive Design */</style>
