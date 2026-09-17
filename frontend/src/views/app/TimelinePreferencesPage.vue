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
              <SettingsSearchTrigger
                class="timeline-search-trigger"
                page-key="timeline"
                placeholder="Search timeline settings..."
                @navigate="handleSettingsSearchNavigate"
              />
              <Button
                label="Regenerate Timeline"
                icon="pi pi-replay"
                severity="danger"
                outlined
                @click="confirmRegenerateTimeline"
                :disabled="timelineRegenerationVisible || demoReadOnly"
              />
              <Button
                label="Save Changes"
                icon="pi pi-save"
                @click="confirmSavePreferences"
                :disabled="!hasUnsavedChanges || !isFormValid || timelineRegenerationVisible || demoReadOnly"
              />

              <div class="toolbar-secondary-actions">
                <Button
                  label="More"
                  icon="pi pi-ellipsis-h"
                  severity="secondary"
                  outlined
                  class="toolbar-more-button"
                  aria-haspopup="true"
                  @click="toggleActionsMenu"
                />
                <Menu
                  ref="actionsMenuRef"
                  :model="headerSecondaryActionsMenu"
                  popup
                />
              </div>
            </div>
          </div>
        </div>

        <div class="timeline-preferences-content">
          <div class="settings-layout">
            <label class="mobile-settings-select">
              <span>Settings section</span>
              <select :value="activeTab" @change="selectTab($event.target.value)">
                <optgroup v-for="group in settingsGroups" :key="group.label" :label="group.label">
                  <option v-for="tab in group.items" :key="tab.key" :value="tab.key">{{ tab.label }}</option>
                </optgroup>
              </select>
            </label>
            <nav class="settings-nav" aria-label="Timeline preference sections">
              <section v-for="group in settingsGroups" :key="group.label" class="settings-nav-group">
                <h2>{{ group.label }}</h2>
                <button v-for="tab in group.items" :key="tab.key" type="button" :class="{ active: activeTab === tab.key }" @click="selectTab(tab.key)">
                  <i :class="tab.icon" aria-hidden="true" />{{ tab.label }}
                </button>
              </section>
            </nav>
            <section :class="['settings-content', { 'demo-readonly-content': demoReadOnly }]">
              <Card class="info-banner">
                <template #content>
                  <div class="banner-content">
                    <div class="banner-icon"><i class="pi pi-info-circle" /></div>
                    <div class="banner-text">
                      <h3 class="banner-title">How Timeline Processing Works</h3>
                      <p class="banner-description">
                        Your GPS data is processed to identify meaningful stays and trips. These settings control the sensitivity of this detection and apply only to your account. Some changes (like speed thresholds) quickly update trip classifications, while others require full timeline re-generation depending on your GPS data volume.
                        <a href="https://geopulse.cc/docs/user-guide/core-features/timeline" target="_blank" rel="noopener noreferrer" class="documentation-link">Learn more in the documentation <i class="pi pi-external-link" /></a>
                      </p>
                    </div>
                  </div>
                </template>
              </Card>

              <Message v-if="demoReadOnly" severity="error" :closable="false" class="demo-read-only-message">
                Demo mode: all timeline preference settings are read-only. Saving changes, importing config, resetting defaults, and regenerating the timeline are disabled.
              </Message>

              <Message v-if="hasUnsavedChanges" severity="warn" class="unsaved-warning">
                <div class="warning-content">
                  <div class="warning-text"><i class="pi pi-exclamation-triangle mr-2" />You have unsaved changes</div>
                  <div class="warning-actions">
                    <Button label="Discard" size="small" severity="secondary" outlined @click="discardChanges" />
                    <Button label="Save Now" size="small" @click="confirmSavePreferences" :disabled="timelineRegenerationVisible || demoReadOnly" />
                  </div>
                </div>
              </Message>

              <StayPointDetectionTab v-if="activeTab === 'staypoints'" v-model="prefs" />
              <TripClassificationTab v-if="activeTab === 'trips'" v-model="prefs" :get-warning-messages-for-type="getWarningMessagesForType" :boat-setup-status="boatSetupStatus" @retry-boat-setup="confirmStartBoatSetup" />
              <GpsGapsDetectionTab v-if="activeTab === 'gpsgaps'" v-model="prefs" />
              <StayPointMergingTab v-if="activeTab === 'merging'" v-model="prefs" />
            </section>
          </div>
        </div>

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

        <Dialog
          v-model:visible="boatSetupVisible"
          modal
          :closable="!boatSetupRunning"
          :dismissableMask="!boatSetupRunning"
          header="Boat Setup"
          class="boat-setup-dialog"
        >
          <div class="boat-setup-dialog-content">
            <p>{{ boatSetupModalIntro }}</p>
            <ProgressBar
              :value="boatSetupStatus?.progressPercentage || 0"
              class="boat-setup-progress-bar"
            />
            <div class="boat-setup-modal-details">
              <span>{{ formatBoatSetupPhase(boatSetupStatus?.phase) }}</span>
              <strong>{{ boatSetupStatus?.progressPercentage || 0 }}%</strong>
            </div>
            <div
              v-if="boatSetupStatus?.datasetStatus === 'DOWNLOADING' && boatSetupStatus?.downloadedBytes"
              class="boat-setup-secondary"
            >
              Downloaded {{ formatBytes(boatSetupStatus.downloadedBytes) }}
              <template v-if="boatSetupStatus.totalBytes">
                / {{ formatBytes(boatSetupStatus.totalBytes) }}
              </template>
            </div>
            <div v-if="boatSetupStatus?.totalGpsPoints" class="boat-setup-secondary">
              GPS points processed:
              {{ (boatSetupStatus.processedGpsPoints || 0).toLocaleString() }}
              / {{ boatSetupStatus.totalGpsPoints.toLocaleString() }}
            </div>
            <Message v-if="boatSetupStatus?.status === 'FAILED'" severity="error">
              <div>
                <strong>{{ boatSetupStatus.errorCode || 'Boat setup failed' }}</strong>
                <div>{{ formatBoatSetupError(boatSetupStatus.error) }}</div>
                <a
                  v-if="boatSetupStatus.docsUrl"
                  :href="boatSetupStatus.docsUrl"
                  target="_blank"
                  rel="noopener noreferrer"
                >
                  Use an offline dataset file <i class="pi pi-external-link"></i>
                </a>
              </div>
            </Message>
          </div>
          <template #footer>
            <Button
              label="Close"
              severity="secondary"
              outlined
              :disabled="boatSetupRunning"
              @click="boatSetupVisible = false"
            />
          </template>
        </Dialog>

        <!-- Confirm Dialog -->
        <ConfirmDialog group="timeline-preferences-unsaved-changes" />
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
import { useRouter, useRoute, onBeforeRouteLeave } from 'vue-router'
import { storeToRefs } from 'pinia'
import { useToast } from 'primevue/usetoast'
import { useConfirm } from 'primevue/useconfirm'
import Menu from 'primevue/menu'
import Message from 'primevue/message'

// Layout components
import AppLayout from '@/components/ui/layout/AppLayout.vue'
import PageContainer from '@/components/ui/layout/PageContainer.vue'

// Tab components
import StayPointDetectionTab from '@/components/timeline-preferences/StayPointDetectionTab.vue'
import TripClassificationTab from '@/components/timeline-preferences/TripClassificationTab.vue'
import GpsGapsDetectionTab from '@/components/timeline-preferences/GpsGapsDetectionTab.vue'
import StayPointMergingTab from '@/components/timeline-preferences/StayPointMergingTab.vue'
import SettingsSearchTrigger from '@/components/search/SettingsSearchTrigger.vue'

// Custom components
import TimelineRegenerationModal from '@/components/dialogs/TimelineRegenerationModal.vue'

import { useTimelinePreferencesStore } from '@/stores/timelinePreferences'
import { useTimelineStore } from '@/stores/timeline'
import { useBoatSetupStore } from '@/stores/boatSetup'
import { useAuthStore } from '@/stores/auth'
import { useTimelineRegeneration } from '@/composables/useTimelineRegeneration'
import { useClassificationValidation } from '@/composables/useClassificationValidation'
import { useTimelineJobCheck } from '@/composables/useTimelineJobCheck'
import {
  TIMELINE_PREFERENCES_SCHEMA_VERSION,
  TIMELINE_PREFERENCE_LABELS,
  TIMELINE_PREFERENCE_VISIBILITY_HINTS
} from '@/constants/timelinePreferencesMetadata'
import { jumpToSetting } from '@/utils/settingJump'
import { showDemoModeToast } from '@/utils/demoMode'
import { formatApiErrorDetail } from '@/utils/apiErrorDetail'
import { formatBoatSetupError, formatBoatSetupPhase } from '@/utils/boatSetupDisplay'

const CLASSIFICATION_FIELDS = [
  'walkingMaxAvgSpeed', 'walkingMaxMaxSpeed',
  'carEnabled', 'motorcycleEnabled', 'publicTransportationEnabled', 'preferredMotorizedType',
  'carMinAvgSpeed', 'carMinMaxSpeed', 'shortDistanceKm',
  'bicycleEnabled', 'bicycleMinAvgSpeed', 'bicycleMaxAvgSpeed', 'bicycleMaxMaxSpeed',
  'runningEnabled', 'runningMinAvgSpeed', 'runningMaxAvgSpeed', 'runningMaxMaxSpeed',
  'trainEnabled', 'trainMinAvgSpeed', 'trainMaxAvgSpeed', 'trainMinMaxSpeed',
  'trainMaxMaxSpeed', 'trainMaxSpeedVariance',
  'flightEnabled', 'flightMinAvgSpeed', 'flightMinMaxSpeed',
  'boatEnabled', 'boatMinWaterRatio', 'boatMinWaterDistanceMeters',
  'boatMinContinuousWaterDistanceMeters', 'boatMaxPlausibleSpeed'
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
  motorcycleEnabled: 'boolean',
  publicTransportationEnabled: 'boolean',
  preferredMotorizedType: 'string',
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
  boatEnabled: 'boolean',
  boatMinWaterRatio: 'number',
  boatMinWaterDistanceMeters: 'number',
  boatMinContinuousWaterDistanceMeters: 'number',
  boatMaxPlausibleSpeed: 'number',
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
  boatMinWaterDistanceMeters: 'm',
  boatMinContinuousWaterDistanceMeters: 'm',
  boatMaxPlausibleSpeed: 'km/h',
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
const boatSetupStore = useBoatSetupStore()
const authStore = useAuthStore()
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
const { demoReadOnly } = storeToRefs(authStore)

// State - Initialize from URL query parameter
const activeTab = ref(route.query.tab || 'staypoints')

// Tab configuration
const settingsGroups = [
  { label: 'Timeline', items: [
    { label: 'Stay Point Detection', icon: 'pi pi-map-marker', key: 'staypoints' },
    { label: 'Trip Classification', icon: 'pi pi-route', key: 'trips' }
  ] },
  { label: 'Data Quality', items: [
    { label: 'GPS Gaps Detection', icon: 'pi pi-exclamation-circle', key: 'gpsgaps' },
    { label: 'Stay Point Merging', icon: 'pi pi-sitemap', key: 'merging' }
  ] }
]

const validTabs = settingsGroups.flatMap((group) => group.items.map((tab) => tab.key))

const prefs = ref({})
const importFileInput = ref(null)
const importPreviewVisible = ref(false)
const importPreviewChanges = ref([])
const importChangesPayload = ref({})
const importSaveType = ref('full')
const isImportApplying = ref(false)
const detectedActiveJobId = ref(null)
const checkingActiveJob = ref(false)
const actionsMenuRef = ref(null)
const boatSetupVisible = ref(false)
let activeJobPollingTimer = null
const timelinePreferencesUnsavedConfirmGroup = 'timeline-preferences-unsaved-changes'

// Computed
const hasLoadedPreferences = computed(() => originalPrefs.value !== null && originalPrefs.value !== undefined)

const hasUnsavedChanges = computed(() => {
  if (!hasLoadedPreferences.value) {
    return false
  }

  return JSON.stringify(prefs.value) !== JSON.stringify(originalPrefs.value)
})

const isFormValid = computed(() => {
  // Basic validation - can be extended
  return Object.values(prefs.value).every(val => val !== null && val !== undefined)
})

const hasActiveJob = computed(() => {
  return Boolean(currentJobId.value || detectedActiveJobId.value)
})

const boatSetupStatus = computed(() => boatSetupStore.status)

const boatSetupRunning = computed(() => {
  return ['QUEUED', 'RUNNING'].includes(boatSetupStatus.value?.status)
})

const boatSetupNeedsAction = computed(() => {
  if (!prefs.value?.boatEnabled) return false
  return !boatSetupStatus.value || ['FAILED', 'PENDING'].includes(boatSetupStatus.value.status)
})

const boatSetupModalIntro = computed(() => {
  if (boatSetupStatus.value?.datasetStatus === 'READY') {
    return 'Boat detection needs a one-time GPS water evidence pre-calculation for your account.'
  }

  return 'Boat detection needs a global water dataset of about 800-900 MB and a one-time GPS pre-calculation for your account.'
})

const headerSecondaryActionsMenu = computed(() => {
  const items = [
    {
      label: 'Export Config',
      icon: 'pi pi-download',
      command: () => exportPreferences()
    },
    {
      label: 'Import Config',
      icon: 'pi pi-upload',
      disabled: timelineRegenerationVisible.value || demoReadOnly.value,
      command: () => openImportPicker()
    },
    {
      label: 'Reset to Defaults',
      icon: 'pi pi-refresh',
      disabled: timelineRegenerationVisible.value || demoReadOnly.value,
      command: () => confirmResetDefaults()
    }
  ]

  if (hasActiveJob.value) {
    items.push({
      separator: true
    })
    items.push({
      label: 'View Active Job',
      icon: 'pi pi-eye',
      command: () => goToActiveJob()
    })
  }

  return items
})

// Methods
const selectTab = (tab) => {
  if (!validTabs.includes(tab)) return
  activeTab.value = tab
  const nextQuery = { ...route.query, tab }
  delete nextQuery.setting
  router.push({ query: nextQuery })
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

const toggleActionsMenu = (event) => {
  actionsMenuRef.value?.toggle(event)
}

const showDemoTimelineReadOnlyToast = () => {
  showDemoModeToast(toast, 'Timeline preference changes are disabled in demo mode.', { severity: 'info' })
}

const openImportPicker = () => {
  if (demoReadOnly.value) {
    showDemoTimelineReadOnlyToast()
    return
  }
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
  if (demoReadOnly.value) {
    showDemoTimelineReadOnlyToast()
    return
  }

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

const refreshBoatSetupStatus = async () => {
  try {
    await boatSetupStore.fetchStatus()
  } catch (error) {
    console.warn('Failed to fetch Boat setup status:', error)
  }
}

const startBoatSetupPolling = () => {
  boatSetupStore.startPolling({
    onSettled: async (status) => {
      if (status.status === 'READY') {
        await loadPreferences()
        toast.add({
          severity: 'success',
          summary: 'Boat Setup Ready',
          detail: 'Boat detection is ready. Timeline generation can now use cached water evidence.',
          life: 5000
        })
      } else if (status?.status === 'FAILED') {
        boatSetupVisible.value = true
        toast.add({
          severity: 'error',
          summary: 'Boat Setup Failed',
          detail: formatBoatSetupError(status.error),
          life: 9000
        })
      }
    },
    onError: (error) => {
      boatSetupVisible.value = true
      toast.add({
        severity: 'error',
        summary: 'Boat Setup Status Failed',
        detail: formatApiErrorDetail(error, 'Could not refresh Boat setup status.'),
        life: 7000
      })
    }
  })
}

const stopBoatSetupPolling = () => {
  boatSetupStore.stopPolling()
}

const formatBytes = (bytes) => {
  if (!bytes || bytes <= 0) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB']
  let value = bytes
  let index = 0
  while (value >= 1024 && index < units.length - 1) {
    value /= 1024
    index++
  }
  return `${value.toFixed(index === 0 ? 0 : 1)} ${units[index]}`
}

const confirmStartBoatSetup = () => {
  if (demoReadOnly.value) {
    showDemoTimelineReadOnlyToast()
    return
  }

  const setupReady = boatSetupStatus.value?.status === 'READY'
  const datasetReady = boatSetupStatus.value?.datasetStatus === 'READY'
  const message = setupReady
    ? 'Boat setup is already ready. Cached water evidence is available for timeline generation.'
    : datasetReady
      ? 'Boat setup will pre-calculate water evidence for your GPS points. Timeline generation will wait until this setup is complete.'
      : 'Boat setup will download or read the water dataset and pre-calculate water evidence for your GPS points. Timeline generation will wait until this setup is complete.'

  confirm.require({
    message,
    header: boatSetupStatus.value?.status === 'FAILED' ? 'Retry Boat Setup' : 'Start Boat Setup',
    icon: 'pi pi-download',
    rejectProps: {
      label: 'Cancel',
      severity: 'secondary',
      outlined: true
    },
    acceptProps: {
      label: boatSetupStatus.value?.status === 'FAILED' ? 'Retry Setup' : 'Start Setup',
      severity: 'primary'
    },
    accept: startBoatSetup
  })
}

const startBoatSetup = async () => {
  if (demoReadOnly.value) {
    showDemoTimelineReadOnlyToast()
    return
  }

  try {
    await boatSetupStore.fetchStatus()
    if (boatSetupStatus.value?.status === 'READY') {
      boatSetupVisible.value = false
      await loadPreferences()
      toast.add({
        severity: 'success',
        summary: 'Boat Setup Ready',
        detail: 'Boat detection is ready. Timeline generation can now use cached water evidence.',
        life: 4000
      })
      return
    }

    boatSetupVisible.value = true
    const setup = await boatSetupStore.startSetup()
    boatSetupStore.currentJobId = setup?.jobId || boatSetupStore.currentJobId

    if (boatSetupStatus.value?.status === 'READY') {
      boatSetupVisible.value = false
      await loadPreferences()
      toast.add({
        severity: 'success',
        summary: 'Boat Setup Ready',
        detail: 'Boat detection is ready. Timeline generation can now use cached water evidence.',
        life: 4000
      })
      return
    }

    startBoatSetupPolling()
    toast.add({
      severity: 'info',
      summary: 'Boat Setup Started',
      detail: 'Water dataset setup and GPS pre-calculation are running.',
      life: 4000
    })
  } catch (error) {
    console.error('Failed to start Boat setup:', error)
    boatSetupVisible.value = true
    toast.add({
      severity: 'error',
      summary: 'Boat Setup Failed',
      detail: formatApiErrorDetail(error, 'Failed to start Boat setup.'),
      life: 7000
    })
  }
}

const getBoatEnablementConfirmation = (status, hasStructuralChanges) => {
  if (status?.status === 'READY') {
    return {
      message: hasStructuralChanges
        ? 'Boat setup is already ready. Saving these preferences will regenerate your timeline with Boat detection enabled.'
        : 'Boat setup is already ready. Saving this preference will update existing trip classifications with Boat detection enabled.',
      icon: 'pi pi-check-circle',
      label: hasStructuralChanges ? 'Enable & Regenerate' : 'Enable Boat',
      saveType: hasStructuralChanges ? 'full' : 'classification'
    }
  }

  if (status?.datasetStatus === 'READY') {
    return {
      message: 'Boat detection requires a one-time GPS water evidence pre-calculation for your account. The global water dataset is already installed, so no large download is needed. Timeline generation will wait until setup is complete.',
      icon: 'pi pi-database',
      label: 'Enable & Start Setup',
      saveType: hasStructuralChanges ? 'full' : 'boat-setup'
    }
  }

  return {
    message: 'Boat detection requires downloading a global water dataset of about 800-900 MB and running a one-time GPS pre-calculation for your account. Timeline generation will wait until this setup is complete.',
    icon: 'pi pi-download',
    label: 'Enable & Start Setup',
    saveType: hasStructuralChanges ? 'full' : 'boat-setup'
  }
}

const confirmSavePreferences = async () => {
  if (demoReadOnly.value) {
    showDemoTimelineReadOnlyToast()
    return
  }

  if (!isFormValid.value) {
    return;
  }

  const changes = getChangedPrefs()
  if (Object.keys(changes).length === 0) {
    if (boatSetupNeedsAction.value) {
      confirmStartBoatSetup()
      return
    }
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
  const isBoatEnablement = changes.boatEnabled === true && originalPrefs.value?.boatEnabled !== true

  if (isBoatEnablement) {
    let currentBoatSetupStatus = boatSetupStatus.value
    try {
      currentBoatSetupStatus = await boatSetupStore.fetchStatus()
    } catch (error) {
      console.warn('Failed to refresh Boat setup status before enabling Boat:', error)
    }

    const confirmation = getBoatEnablementConfirmation(currentBoatSetupStatus, hasStructuralChanges)
    confirm.require({
      message: confirmation.message,
      header: 'Enable Boat Detection',
      icon: confirmation.icon,
      rejectProps: {
        label: 'Cancel',
        severity: 'secondary',
        outlined: true
      },
      acceptProps: {
        label: confirmation.label,
        severity: 'primary'
      },
      accept: () => savePreferences(confirmation.saveType, changes)
    })
    return
  }
  
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
  if (demoReadOnly.value) {
    showDemoTimelineReadOnlyToast()
    return
  }

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

  if (saveType === 'boat-setup') {
    try {
      await timelinePreferencesStore.updateTimelinePreferences(changes)
      const result = timelinePreferencesStore.lastUpdateResponseData
      await loadPreferences()

      if (result?.boatSetupJobId) {
        boatSetupStore.currentJobId = result.boatSetupJobId
        boatSetupStore.status = result.boatSetupStatus || boatSetupStore.status
      } else if (result?.boatSetupStatus) {
        boatSetupStore.status = result.boatSetupStatus
      } else {
        await boatSetupStore.fetchStatus()
      }

      if (boatSetupStatus.value?.status === 'READY') {
        boatSetupVisible.value = false
        toast.add({
          severity: 'success',
          summary: 'Boat Detection Enabled',
          detail: 'Boat setup is ready and the preference has been saved.',
          life: 4000
        })
        return
      }

      boatSetupVisible.value = true
      if (!result?.boatSetupJobId) {
        const setup = await boatSetupStore.startSetup()
        boatSetupStore.currentJobId = setup?.jobId || boatSetupStore.currentJobId

        if (boatSetupStatus.value?.status === 'READY') {
          boatSetupVisible.value = false
          toast.add({
            severity: 'success',
            summary: 'Boat Detection Enabled',
            detail: 'Boat setup is ready and the preference has been saved.',
            life: 4000
          })
          return
        }
      }

      startBoatSetupPolling()
      toast.add({
        severity: 'info',
        summary: 'Boat Setup Started',
        detail: 'Water dataset setup and GPS pre-calculation are running.',
        life: 4000
      })
    } catch (error) {
      console.error('Failed to start Boat setup:', error)
      toast.add({
        severity: 'error',
        summary: 'Boat Setup Failed',
        detail: error.message || 'Failed to enable Boat setup.',
        life: 7000
      })
    }
  } else if (saveType === 'classification') {
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
  if (demoReadOnly.value) {
    showDemoTimelineReadOnlyToast()
    return
  }

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
  if (demoReadOnly.value) {
    showDemoTimelineReadOnlyToast()
    return
  }

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
  if (demoReadOnly.value) {
    showDemoTimelineReadOnlyToast()
    return
  }

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
  if (demoReadOnly.value) {
    showDemoTimelineReadOnlyToast()
    return
  }

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

const jumpToRouteSetting = async (settingKey, hintOverride = null) => {
  if (!settingKey) return

  const hint = hintOverride || TIMELINE_PREFERENCE_VISIBILITY_HINTS[settingKey]
  const jumped = await jumpToSetting(settingKey, {
    onMissing: () => {
      toast.add({
        severity: 'info',
        summary: 'Setting not visible',
        detail: hint || 'This setting is not currently visible. Enable related options to edit it.',
        life: 4000
      })
    }
  })

  return jumped
}

const handleSettingsSearchNavigate = async (item) => {
  if (!item?.setting) return

  const nextTab = item.tab || activeTab.value
  const currentTab = typeof route.query.tab === 'string' ? route.query.tab : activeTab.value
  const currentSetting = typeof route.query.setting === 'string' ? route.query.setting : ''

  if (currentTab === nextTab && currentSetting === item.setting) {
    await jumpToRouteSetting(item.setting)
    return
  }

  const nextQuery = {
    ...route.query,
    tab: nextTab,
    setting: item.setting
  }

  router.replace({ query: nextQuery })
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
  if (newTab && validTabs.includes(newTab)) {
    activeTab.value = newTab
  } else if (newTab && !validTabs.includes(newTab)) {
    // Invalid tab, redirect to default
    router.replace({ query: { ...route.query, tab: 'staypoints' } })
    activeTab.value = 'staypoints'
  }
})

watch(
  () => [route.query.tab, route.query.setting],
  ([tab, setting]) => {
    if (route.path !== '/app/timeline/preferences') return
    if (!setting || typeof setting !== 'string') return

    const tabChanged = typeof tab === 'string' && tab !== activeTab.value
    const delayMs = tabChanged ? 240 : 80

    window.setTimeout(() => {
      void jumpToRouteSetting(setting)
    }, delayMs)
  },
  { immediate: true }
)

onBeforeRouteLeave((to, from, next) => {
  if (to.path === from.path || !hasUnsavedChanges.value) {
    next()
    return
  }

  let guardResolved = false
  const resolveGuard = (allowNavigation) => {
    if (guardResolved) {
      return
    }

    guardResolved = true
    if (allowNavigation) {
      next()
    } else {
      next(false)
    }
  }

  confirm.require({
    group: timelinePreferencesUnsavedConfirmGroup,
    message: 'You have unsaved timeline preference changes. If you leave this page, those changes will be lost.',
    header: 'Unsaved Changes',
    icon: 'pi pi-exclamation-triangle',
    acceptLabel: 'Leave without saving',
    rejectLabel: 'Stay',
    acceptClass: 'p-button-danger',
    rejectClass: 'p-button-secondary p-button-outlined',
    accept: () => {
      resolveGuard(true)
    },
    reject: () => {
      resolveGuard(false)
    },
    onHide: () => {
      resolveGuard(false)
    }
  })
})

const handleBeforeUnload = (event) => {
  if (!hasUnsavedChanges.value) {
    return
  }

  event.preventDefault()
  event.returnValue = ''
  return ''
}

// Lifecycle
onMounted(() => {
  window.addEventListener('beforeunload', handleBeforeUnload)

  loadPreferences()
  refreshBoatSetupStatus()
  refreshActiveJob()
  activeJobPollingTimer = window.setInterval(refreshActiveJob, 15000)

  // Validate initial tab from URL
  const initialTab = route.query.tab
  if (initialTab && !validTabs.includes(initialTab)) {
    router.replace({ query: { ...route.query, tab: 'staypoints' } })
    activeTab.value = 'staypoints'
  } else if (initialTab) {
    activeTab.value = initialTab
  }
})

onUnmounted(() => {
  window.removeEventListener('beforeunload', handleBeforeUnload)

  if (activeJobPollingTimer) {
    window.clearInterval(activeJobPollingTimer)
    activeJobPollingTimer = null
  }
  stopBoatSetupPolling()
})
</script>

<style scoped>
.timeline-preferences-page {
  padding: 0 1rem;
  width: 100%;
  box-sizing: border-box;
}

/* Page Header */
.page-header {
  margin-bottom: 2rem;
}

.header-content { display: flex; justify-content: space-between; align-items: flex-start; gap: 2rem; }
.header-text { flex: 1; }
.header-actions { display: flex; align-items: center; justify-content: flex-end; flex-wrap: wrap; gap: .75rem; flex-shrink: 0; }

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

.toolbar-secondary-actions {
  display: flex;
  align-items: center;
  flex-shrink: 0;
}

.header-actions :deep(.p-button),
.header-actions :deep(.settings-search-trigger) {
  min-height: 3.1rem;
}

.timeline-search-trigger :deep(.settings-search-trigger.p-button.p-button-outlined) {
  border-color: var(--gp-border-medium);
  color: var(--gp-text-primary);
  background: transparent;
}

.timeline-search-trigger :deep(.settings-search-trigger.p-button.p-button-outlined:hover) {
  border-color: var(--gp-primary);
  color: var(--gp-primary);
  background: rgba(59, 130, 246, 0.08);
}

.toolbar-more-button.p-button.p-button-outlined {
  border-color: var(--gp-border-medium);
  color: var(--gp-text-primary);
}

.toolbar-more-button.p-button.p-button-outlined:hover {
  border-color: var(--gp-primary);
  color: var(--gp-primary);
  background: rgba(59, 130, 246, 0.08);
}

.timeline-preferences-content { margin-bottom: 2rem; }
.settings-layout { display: grid; grid-template-columns: 15rem minmax(0, 1fr); gap: 1.5rem; }
.settings-nav { display: grid; align-content: start; gap: 1rem; }
.settings-nav-group { display: grid; gap: .25rem; }
.settings-nav h2 { margin: 0 0 .25rem; color: var(--gp-text-muted); font-size: .75rem; letter-spacing: .05em; text-transform: uppercase; }
.settings-nav button { display: flex; align-items: center; gap: .65rem; width: 100%; padding: .65rem .75rem; border: 0; border-radius: var(--gp-radius-medium); background: transparent; color: var(--gp-text-secondary); font: inherit; text-align: left; cursor: pointer; }
.settings-nav button:hover, .settings-nav button.active { background: var(--gp-timeline-blue); color: var(--gp-primary-dark); }
.settings-nav button.active { font-weight: 600; }
.settings-content { min-width: 0; }
.mobile-settings-select { display: none; }

/* Info Banner */
.info-banner {
  margin-bottom: var(--gp-spacing-md);
  background: var(--gp-surface-light);
  border: 1px solid var(--gp-border-medium);
  border-left: 4px solid var(--gp-primary);
  border-radius: var(--gp-radius-large);
}

.p-dark .info-banner {
  margin-bottom: var(--gp-spacing-md);
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
.demo-readonly-content :deep(.preferences-section) {
  opacity: 0.78;
  pointer-events: none;
}

.hidden-file-input {
  display: none;
}

.boat-setup-dialog {
  width: min(560px, 95vw);
}

.boat-setup-dialog-content {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.boat-setup-progress-bar {
  width: 100%;
}

.boat-setup-modal-details {
  align-items: center;
  display: flex;
  gap: 1rem;
  justify-content: space-between;
}

.boat-setup-secondary {
  color: var(--gp-text-secondary);
  font-size: 0.9rem;
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
    padding: 0;
    max-width: 100%;
  }

  .page-header { padding: 0 1rem; }
  .page-title {
    font-size: 1.5rem;
  }

  .header-content { flex-direction: column; gap: .75rem; }
  .header-actions { width: 100%; justify-content: flex-end; }
  .settings-layout { grid-template-columns: 1fr; gap: 1rem; }
  .settings-nav { display: none; }
  .mobile-settings-select { display: grid; gap: .35rem; color: var(--gp-text-secondary); font-size: .85rem; font-weight: 600; padding: 0 1rem; }
  .mobile-settings-select select { width: 100%; min-height: 2.75rem; padding: 0 .75rem; border: 1px solid var(--gp-border-medium); border-radius: var(--gp-radius-medium); background: var(--gp-surface-white); color: var(--gp-text-primary); font: inherit; }

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
  
}

@media (max-width: 480px) {
  .timeline-preferences-page {
    padding: 0;
    max-width: 100%;
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
  
  .header-actions { align-items: stretch; }
  .header-actions :deep(.p-button),
  .header-actions :deep(.settings-search-trigger) {
    width: 100%;
    min-height: 48px;
    font-size: 0.95rem;
  }

  .toolbar-secondary-actions {
    width: 100%;
    justify-content: stretch;
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
  
}

/* Responsive Design */</style>
