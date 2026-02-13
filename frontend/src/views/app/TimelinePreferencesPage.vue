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
import { ref, computed, onMounted, watch } from 'vue'
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

// Store
const router = useRouter()
const route = useRoute()
const toast = useToast()
const confirm = useConfirm()
const timelinePreferencesStore = useTimelinePreferencesStore()
const timelineStore = useTimelineStore()

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

// Computed
const hasUnsavedChanges = computed(() => {
  return JSON.stringify(prefs.value) !== JSON.stringify(originalPrefs.value)
})

const isFormValid = computed(() => {
  // Basic validation - can be extended
  return Object.values(prefs.value).every(val => val !== null && val !== undefined)
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

const getChangedPrefs = () => {
  const changed = {}
  
  for (const key in prefs.value) {
    const currentValue = prefs.value[key]
    const originalValue = originalPrefs.value?.[key]
    
    // Skip null values (they indicate no change requested)
    if (currentValue === null) {
      continue
    }
    
    // Only include fields that have actually changed from their original values
    if (currentValue !== originalValue) {
      changed[key] = currentValue
    }
  }
  return changed
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
      accept: () => savePreferences('classification')
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
      accept: () => savePreferences('full')
    })
  }
}

// Parameter categorization functions
const hasClassificationParameters = (changes) => {
  const classificationFields = [
    'walkingMaxAvgSpeed', 'walkingMaxMaxSpeed',
    'carMinAvgSpeed', 'carMinMaxSpeed', 'shortDistanceKm',
    // Bicycle
    'bicycleEnabled', 'bicycleMinAvgSpeed', 'bicycleMaxAvgSpeed', 'bicycleMaxMaxSpeed',
    // Running
    'runningEnabled', 'runningMinAvgSpeed', 'runningMaxAvgSpeed', 'runningMaxMaxSpeed',
    // Train
    'trainEnabled', 'trainMinAvgSpeed', 'trainMaxAvgSpeed', 'trainMinMaxSpeed',
    'trainMaxMaxSpeed', 'trainMaxSpeedVariance',
    // Flight
    'flightEnabled', 'flightMinAvgSpeed', 'flightMinMaxSpeed'
  ]
  return classificationFields.some(field => field in changes)
}

/**
 * Check if changes contain structural parameters that require timeline regeneration.
 * NOTE: Path simplification fields removed - they are now display-only settings
 * managed via User Profile > Timeline Display tab.
 */
const hasStructuralParameters = (changes) => {
  const structuralFields = [
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
  return structuralFields.some(field => field in changes)
}

const savePreferences = async (saveType = 'full') => {
  if (!isFormValid.value) return

  // Capture changes immediately to avoid closure issues
  const changes = getChangedPrefs()

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
  router.push('/app/timeline/jobs')
}

watch(originalPrefs, (newVal) => {
  if (newVal) {
    prefs.value = { ...newVal }
  }
}, { immediate: true })

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
