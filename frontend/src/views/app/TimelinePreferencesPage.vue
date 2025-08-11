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
                label="Reset to Defaults"
                icon="pi pi-refresh"
                severity="secondary"
                outlined
                @click="confirmResetDefaults"
                :disabled="loading"
              />
              <Button 
                label="Save Changes"
                icon="pi pi-save"
                @click="confirmSavePreferences"
                :loading="loading"
                :disabled="!hasUnsavedChanges || !isFormValid"
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
                  Changing them will trigger a full timeline re-generation, which may take some time depending on your GPS data volume.
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
                :loading="loading"
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
          <div v-if="activeTab === 'staypoints'">
              <div class="preferences-section">
                <div class="section-header">
                  <h2 class="section-title">Stay Point Detection Settings</h2>
                  <p class="section-description">
                    Configure how GPS data is analyzed to identify places where you've stayed
                  </p>
                </div>

                <div class="settings-grid">
                  <!-- Detection Algorithm -->
                  <SettingCard
                    title="Detection Algorithm"
                    description="The algorithm used to identify stay points from GPS data"
                    :details="{
                      'Original': 'Time-based clustering approach',
                      'Enhanced': 'Advanced velocity and accuracy filtering'
                    }"
                    env-var="geopulse.timeline.staypoint.detection.algorithm"
                  >
                    <template #control>
                      <div class="control-value">{{ prefs.staypointDetectionAlgorithm }}</div>
                      <Select
                        v-model="prefs.staypointDetectionAlgorithm"
                        :options="algorithmOptions"
                        optionLabel="label"
                        optionValue="value"
                        placeholder="Select algorithm"
                        class="w-full"
                      />
                    </template>
                  </SettingCard>

                  <!-- Enhanced Filtering -->
                  <SettingCard
                    title="Enhanced Filtering"
                    description="Use velocity and accuracy data for better stay point detection"
                    details="Filters out poor quality GPS points and improves timeline accuracy"
                    env-var="geopulse.timeline.staypoint.use_velocity_accuracy"
                  >
                    <template #control>
                      <div class="control-value">{{ prefs.useVelocityAccuracy ? 'Enabled' : 'Disabled' }}</div>
                      <ToggleSwitch
                        v-model="prefs.useVelocityAccuracy"
                        class="toggle-control"
                      />
                    </template>
                  </SettingCard>

                  <!-- Velocity Threshold -->
                  <SettingCard
                    v-if="prefs.useVelocityAccuracy"
                    title="Velocity Threshold"
                    description="Maximum velocity to consider a point as stationary"
                    :details="{
                      'Lower values': 'More strict filtering',
                      'Higher values': 'Allow more movement within stays'
                    }"
                    env-var="geopulse.timeline.staypoint.velocity.threshold"
                  >
                    <template #control>
                      <div class="control-value">{{ prefs.staypointVelocityThreshold }} km/h</div>
                      <SliderControl
                        v-model="prefs.staypointVelocityThreshold"
                        :min="1"
                        :max="20"
                        :step="0.5"
                        :labels="['1 km/h (Strict)', '8 km/h (Balanced)', '20 km/h (Lenient)']"
                        suffix=" km/h"
                        :input-min="0.5"
                        :input-max="50"
                        :decimal-places="1"
                      />
                    </template>
                  </SettingCard>

                  <!-- Accuracy Threshold -->
                  <SettingCard
                    v-if="prefs.useVelocityAccuracy"
                    title="GPS Accuracy Threshold"
                    description="Minimum GPS accuracy required to use a location point"
                    :details="{
                      'Lower values': 'Require more accurate GPS',
                      'Higher values': 'Accept less accurate GPS points'
                    }"
                    env-var="geopulse.timeline.staypoint.accuracy.threshold"
                  >
                    <template #control>
                      <div class="control-value">{{ prefs.staypointMaxAccuracyThreshold }}m</div>
                      <SliderControl
                        v-model="prefs.staypointMaxAccuracyThreshold"
                        :min="5"
                        :max="200"
                        :step="5"
                        :labels="['5m (High accuracy)', '60m (Balanced)', '200m (Low accuracy)']"
                        suffix=" m"
                        :input-min="1"
                        :input-max="500"
                        :decimal-places="1"
                      />
                    </template>
                  </SettingCard>

                  <!-- Min Accuracy Ratio -->
                  <SettingCard
                    v-if="prefs.useVelocityAccuracy"
                    title="Minimum Accuracy Ratio"
                    description="Minimum ratio of accurate GPS points required in a stay point cluster"
                    details="Higher values ensure more reliable stay point detection by requiring a higher percentage of accurate GPS points"
                    env-var="geopulse.timeline.staypoint.min_accuracy_ratio"
                  >
                    <template #control>
                      <div class="control-value">{{ Math.round(prefs.staypointMinAccuracyRatio * 100) }}%</div>
                      <SliderControl
                        v-model="prefs.staypointMinAccuracyRatio"
                        :min="0.1"
                        :max="1.0"
                        :step="0.05"
                        :labels="['10% (Lenient)', '50% (Balanced)', '100% (Strict)']"
                        :input-min="0.1"
                        :input-max="1.0"
                        :decimal-places="2"
                      />
                    </template>
                  </SettingCard>
                </div>
              </div>
          </div>

          <!-- Trip Detection Tab -->
          <div v-if="activeTab === 'trips'">
              <div class="preferences-section">
                <div class="section-header">
                  <h2 class="section-title">Trip Detection Settings</h2>
                  <p class="section-description">
                    Configure how movements between stay points are identified as trips
                  </p>
                </div>

                <div class="settings-grid">
                  <!-- Trip Detection Algorithm -->
                  <SettingCard
                    title="Trip Detection Algorithm"
                    description="The algorithm used to identify trips between stay points"
                    :details="{
                      'Single': 'Always one trip between stay points',
                      'Multiple': 'Based on velocity one or more trips between stay points (like CAR → WALK, WALK → CAR, etc)'
                    }"
                    env-var="geopulse.timeline.trip.detection.algorithm"
                  >
                    <template #control>
                      <div class="control-value">{{ prefs.tripDetectionAlgorithm }}</div>
                      <Select
                        v-model="prefs.tripDetectionAlgorithm"
                        :options="tripsAlgorithmOptions"
                        optionLabel="label"
                        optionValue="value"
                        placeholder="Select algorithm"
                        class="w-full"
                      />
                    </template>
                  </SettingCard>

                  <!-- Min Trip Distance -->
                  <SettingCard
                    title="Minimum Trip Distance"
                    description="Minimum distance traveled to register as a trip between locations"
                    :details="{
                      'Lower values': 'Capture shorter movements',
                      'Higher values': 'Only longer trips are recorded'
                    }"
                    env-var="geopulse.timeline.trip.min_distance_meters"
                  >
                    <template #control>
                      <div class="control-value">{{ prefs.tripMinDistanceMeters }}m</div>
                      <SliderControl
                        v-model="prefs.tripMinDistanceMeters"
                        :min="10"
                        :max="500"
                        :step="10"
                        :labels="['10m (Sensitive)', '50m (Balanced)', '500m (Conservative)']"
                        suffix=" m"
                        :input-min="1"
                        :input-max="2000"
                        :decimal-places="0"
                      />
                    </template>
                  </SettingCard>

                  <!-- Min Trip Duration -->
                  <SettingCard
                    title="Minimum Trip Duration"
                    description="Shortest time period to register as a trip between locations"
                    :details="{
                      'Lower values': 'Capture quick movements',
                      'Higher values': 'Only longer journeys are recorded'
                    }"
                    env-var="geopulse.timeline.trip.min_duration_minutes"
                  >
                    <template #control>
                      <div class="control-value">{{ prefs.tripMinDurationMinutes }} minutes</div>
                      <SliderControl
                        v-model="prefs.tripMinDurationMinutes"
                        :min="1"
                        :max="60"
                        :step="1"
                        :labels="['1 min (Sensitive)', '7 min (Balanced)', '60 min (Conservative)']"
                        suffix=" min"
                        :input-min="1"
                        :input-max="300"
                        :decimal-places="0"
                      />
                    </template>
                  </SettingCard>
                </div>
              </div>
          </div>

          <!-- GPS Gaps Detection Tab -->
          <div v-if="activeTab === 'gpsgaps'">
              <div class="preferences-section">
                <div class="section-header">
                  <h2 class="section-title">GPS Gaps Detection Settings</h2>
                  <p class="section-description">
                    Configure how GPS data gaps are detected and recorded in your timeline
                  </p>
                </div>

                <div class="settings-grid">
                  <!-- Data Gap Threshold -->
                  <SettingCard
                    title="Data Gap Threshold"
                    description="Maximum time gap in seconds allowed between GPS points before considering it a GPS data gap"
                    details="When the time difference between two consecutive GPS points exceeds this threshold, a GPS Data Gap entity will be created instead of extending the current stay or trip. This prevents artificial extension of activities during periods of missing GPS data."
                    env-var="geopulse.timeline.data_gap.threshold_seconds"
                  >
                    <template #control>
                      <div class="control-value">{{ Math.floor(prefs.dataGapThresholdSeconds / 60) }} minutes ({{ prefs.dataGapThresholdSeconds }}s)</div>
                      <SliderControl
                        v-model="prefs.dataGapThresholdSeconds"
                        :min="300"
                        :max="21600"
                        :step="300"
                        :labels="['5 min (Sensitive)', '3 hours (Balanced)', '6 hours (Lenient)']"
                        :suffix="' seconds'"
                        :input-min="60"
                        :input-max="43200"
                        :decimal-places="0"
                      />
                    </template>
                  </SettingCard>

                  <!-- Min Duration for Gap Recording -->
                  <SettingCard
                    title="Minimum Gap Duration"
                    description="Minimum duration in seconds for a gap to be recorded as a GPS Data Gap"
                    details="Gaps shorter than this threshold will be ignored to reduce noise. This prevents very short connectivity issues from creating unnecessary gap records."
                    env-var="geopulse.timeline.data_gap.min_duration_seconds"
                  >
                    <template #control>
                      <div class="control-value">{{ Math.floor(prefs.dataGapMinDurationSeconds / 60) }} minutes ({{ prefs.dataGapMinDurationSeconds }}s)</div>
                      <SliderControl
                        v-model="prefs.dataGapMinDurationSeconds"
                        :min="300"
                        :max="7200"
                        :step="300"
                        :labels="['5 min (Strict)', '30 min (Balanced)', '2 hours (Lenient)']"
                        :suffix="' seconds'"
                        :input-min="60"
                        :input-max="14400"
                        :decimal-places="0"
                      />
                    </template>
                  </SettingCard>
                </div>
              </div>
          </div>

          <!-- Stay Point Merging Tab -->
          <div v-if="activeTab === 'merging'">
              <div class="preferences-section">
                <div class="section-header">
                  <h2 class="section-title">Stay Point Merging Settings</h2>
                  <p class="section-description">
                    Configure how nearby stay points are consolidated into single locations
                  </p>
                </div>

                <div class="settings-grid">
                  <!-- Enable Merging -->
                  <SettingCard
                    title="Enable Stay Point Merging"
                    description="Whether to merge nearby stay points that are close in time and distance"
                    details="Helps consolidate multiple GPS clusters at the same general location into single stay points"
                    env-var="geopulse.timeline.staypoint.merge.enabled"
                  >
                    <template #control>
                      <div class="control-value">{{ prefs.isMergeEnabled ? 'Enabled' : 'Disabled' }}</div>
                      <ToggleSwitch
                        v-model="prefs.isMergeEnabled"
                        class="toggle-control"
                      />
                    </template>
                  </SettingCard>

                  <!-- Max Merge Distance -->
                  <SettingCard
                    v-if="prefs.isMergeEnabled"
                    title="Maximum Merge Distance"
                    description="Maximum distance between stay points to consider them for merging"
                    :details="{
                      'Lower values': 'Only merge very close points',
                      'Higher values': 'Merge points further apart'
                    }"
                    env-var="geopulse.timeline.staypoint.merge.max_distance_meters"
                  >
                    <template #control>
                      <div class="control-value">{{ prefs.mergeMaxDistanceMeters }}m</div>
                      <SliderControl
                        v-model="prefs.mergeMaxDistanceMeters"
                        :min="20"
                        :max="500"
                        :step="10"
                        :labels="['20m (Precise)', '150m (Balanced)', '500m (Generous)']"
                        suffix=" m"
                        :input-min="10"
                        :input-max="1000"
                        :decimal-places="0"
                      />
                    </template>
                  </SettingCard>

                  <!-- Max Time Gap -->
                  <SettingCard
                    v-if="prefs.isMergeEnabled"
                    title="Maximum Time Gap"
                    description="Maximum time gap between stay points to consider them for merging"
                    :details="{
                      'Lower values': 'Only merge consecutive stays',
                      'Higher values': 'Merge stays separated by longer gaps'
                    }"
                    env-var="geopulse.timeline.staypoint.merge.max_time_gap_minutes"
                  >
                    <template #control>
                      <div class="control-value">{{ prefs.mergeMaxTimeGapMinutes }} minutes</div>
                      <SliderControl
                        v-model="prefs.mergeMaxTimeGapMinutes"
                        :min="1"
                        :max="60"
                        :step="1"
                        :labels="['1 min (Strict)', '10 min (Balanced)', '60 min (Generous)']"
                        suffix=" min"
                        :input-min="1"
                        :input-max="300"
                        :decimal-places="0"
                      />
                    </template>
                  </SettingCard>
                </div>
              </div>
          </div>

          <!-- GPS Path Simplification Tab -->
          <div v-if="activeTab === 'pathsimplification'">
              <div class="preferences-section">
                <div class="section-header">
                  <h2 class="section-title">GPS Path Simplification Settings</h2>
                  <p class="section-description">
                    Configure how GPS paths are simplified to reduce data while preserving route accuracy
                  </p>
                </div>

                <div class="settings-grid">
                  <!-- Enable Path Simplification -->
                  <SettingCard
                    title="Enable Path Simplification"
                    description="Whether GPS path simplification is enabled for timeline trips"
                    details="When enabled, trip paths will be simplified using the Douglas-Peucker algorithm to reduce the number of GPS points while preserving route accuracy"
                    env-var="geopulse.timeline.path.simplification.enabled"
                  >
                    <template #control>
                      <div class="control-value">{{ prefs.pathSimplificationEnabled ? 'Enabled' : 'Disabled' }}</div>
                      <ToggleSwitch
                        v-model="prefs.pathSimplificationEnabled"
                        class="toggle-control"
                      />
                    </template>
                  </SettingCard>

                  <!-- Simplification Tolerance -->
                  <SettingCard
                    v-if="prefs.pathSimplificationEnabled"
                    title="Simplification Tolerance"
                    description="Base tolerance in meters for GPS path simplification"
                    :details="{
                      'Lower values': 'Preserve more detail, less compression',
                      'Higher values': 'More compression, less detail'
                    }"
                    env-var="geopulse.timeline.path.simplification.tolerance"
                  >
                    <template #control>
                      <div class="control-value">{{ prefs.pathSimplificationTolerance }}m</div>
                      <SliderControl
                        v-model="prefs.pathSimplificationTolerance"
                        :min="1"
                        :max="50"
                        :step="1"
                        :labels="['1m (High detail)', '15m (Balanced)', '50m (High compression)']"
                        suffix=" m"
                        :input-min="1"
                        :input-max="100"
                        :decimal-places="0"
                      />
                    </template>
                  </SettingCard>

                  <!-- Maximum Points -->
                  <SettingCard
                    v-if="prefs.pathSimplificationEnabled"
                    title="Maximum Points"
                    description="Maximum number of GPS points to retain in simplified paths"
                    details="If a simplified path still exceeds this limit, tolerance will be automatically increased until the limit is met. Set to 0 for no limit"
                    env-var="geopulse.timeline.path.simplification.max_points"
                  >
                    <template #control>
                      <div class="control-value">{{ prefs.pathMaxPoints === 0 ? 'No limit' : prefs.pathMaxPoints + ' points' }}</div>
                      <SliderControl
                        v-model="prefs.pathMaxPoints"
                        :min="0"
                        :max="500"
                        :step="10"
                        :labels="['0 (No limit)', '100 (Balanced)', '500 (High limit)']"
                        :suffix="prefs.pathMaxPoints === 0 ? '' : ' points'"
                        :input-min="0"
                        :input-max="1000"
                        :decimal-places="0"
                      />
                    </template>
                  </SettingCard>

                  <!-- Adaptive Simplification -->
                  <SettingCard
                    v-if="prefs.pathSimplificationEnabled"
                    title="Adaptive Simplification"
                    description="Enables adaptive simplification that adjusts tolerance based on trip characteristics"
                    details="When enabled, longer trips use higher tolerance values for better compression while shorter trips maintain higher accuracy with lower tolerance"
                    env-var="geopulse.timeline.path.simplification.adaptive"
                  >
                    <template #control>
                      <div class="control-value">{{ prefs.pathAdaptiveSimplification ? 'Enabled' : 'Disabled' }}</div>
                      <ToggleSwitch
                        v-model="prefs.pathAdaptiveSimplification"
                        class="toggle-control"
                      />
                    </template>
                  </SettingCard>
                </div>
              </div>
          </div>
        </TabContainer>

        <!-- Confirm Dialog -->
        <ConfirmDialog />
        <Toast />
      </div>
    </PageContainer>
  </AppLayout>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { storeToRefs } from 'pinia'
import { useToast } from 'primevue/usetoast'
import { useConfirm } from 'primevue/useconfirm'

// Layout components
import AppLayout from '@/components/ui/layout/AppLayout.vue'
import PageContainer from '@/components/ui/layout/PageContainer.vue'
import TabContainer from '@/components/ui/layout/TabContainer.vue'

// Custom components
import SettingCard from '@/components/ui/forms/SettingCard.vue'
import SliderControl from '@/components/ui/forms/SliderControl.vue'

// Store
import { useTimelinePreferencesStore } from '@/stores/timelinePreferences'

// Composables
const toast = useToast()
const confirm = useConfirm()
const timelinePreferencesStore = useTimelinePreferencesStore()

// Store refs
const { timelinePreferences: originalPrefs } = storeToRefs(timelinePreferencesStore)

// State
const activeTab = ref('staypoints')

// Tab configuration
const tabItems = ref([
  {
    label: 'Stay Point Detection',
    icon: 'pi pi-map-marker',
    key: 'staypoints'
  },
  {
    label: 'Trip Detection',
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
  },
  {
    label: 'GPS Path Simplification',
    icon: 'pi pi-share-alt',
    key: 'pathsimplification'
  }
])

const activeTabIndex = computed(() => {
  return tabItems.value.findIndex(tab => tab.key === activeTab.value)
})
const loading = ref(false)
const prefs = ref({
  // Path simplification defaults
  pathSimplificationEnabled: true,
  pathSimplificationTolerance: 15,
  pathMaxPoints: 100,
  pathAdaptiveSimplification: true,
  // GPS gaps detection defaults
  dataGapThresholdSeconds: 10800,
  dataGapMinDurationSeconds: 1800
})

// Algorithm options
const algorithmOptions = [
  { label: 'Simple Algorithm', value: 'simple' },
  { label: 'Enhanced Algorithm', value: 'enhanced' }
]

const tripsAlgorithmOptions = [
  { label: 'Single trip', value: 'single' },
  { label: 'Multiple trips', value: 'multiple' }
]

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
  }
}

const getChangedPrefs = () => {
  const changed = {}
  for (const key in prefs.value) {
    if (prefs.value[key] !== originalPrefs.value?.[key]) {
      changed[key] = prefs.value[key]
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
  if (!isFormValid.value) return

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

  confirm.require({
    message: 'Changing timeline preferences will trigger a complete re-generation of all your timeline data according to the new settings. This process may take some time depending on the volume of your GPS data. Do you want to proceed?',
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
    accept: savePreferences
  })
}

const savePreferences = async () => {
  if (!isFormValid.value) return

  loading.value = true
  
  try {
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

    await timelinePreferencesStore.updateTimelinePreferences(changes)

    toast.add({
      severity: 'success',
      summary: 'Preferences Saved',
      detail: 'Timeline preferences updated and timeline is successfully recalculated',
      life: 5000
    })
  } catch (error) {
    console.error('Error saving preferences:', error)
    toast.add({
      severity: 'error',
      summary: 'Save Failed',
      detail: 'Failed to save timeline preferences',
      life: 5000
    })
  } finally {
    loading.value = false
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

const resetDefaults = async () => {
  loading.value = true
  
  try {
    await timelinePreferencesStore.resetTimelinePreferencesToDefaults()
    toast.add({
      severity: 'success',
      summary: 'Settings Reset',
      detail: 'All preferences have been reset to defaults',
      life: 3000
    })
  } catch (error) {
    console.error('Error resetting preferences:', error)
    toast.add({
      severity: 'error',
      summary: 'Reset Failed',
      detail: 'Failed to reset preferences to defaults',
      life: 5000
    })
  } finally {
    loading.value = false
  }
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

// Watchers
watch(originalPrefs, (newVal) => {
  if (newVal) {
    prefs.value = {
      ...newVal,
      // Ensure path simplification properties have defaults if not present
      pathSimplificationEnabled: newVal.pathSimplificationEnabled ?? true,
      pathSimplificationTolerance: newVal.pathSimplificationTolerance ?? 15,
      pathMaxPoints: newVal.pathMaxPoints ?? 100,
      pathAdaptiveSimplification: newVal.pathAdaptiveSimplification ?? true,
      // Ensure GPS gaps detection properties have defaults if not present
      dataGapThresholdSeconds: newVal.dataGapThresholdSeconds ?? 10800,
      dataGapMinDurationSeconds: newVal.dataGapMinDurationSeconds ?? 1800
    }
  }
}, { immediate: true })

// Lifecycle
onMounted(() => {
  loadPreferences()
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

/* Preferences Section */
.preferences-section {
  padding: 2rem 0;
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
  word-wrap: break-word;
  overflow-wrap: break-word;
  hyphens: auto;
}

/* Settings Grid */
.settings-grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: 1.5rem;
  width: 100%;
  max-width: 100%;
  box-sizing: border-box;
}

/* Control Value Display */
.control-value {
  display: inline-block;
  padding: 0.5rem 1rem;
  background: var(--gp-surface-light);
  border: 1px solid var(--gp-border-light);
  border-radius: var(--gp-radius-medium);
  font-family: var(--font-mono, monospace);
  font-size: 0.9rem;
  font-weight: 600;
  color: var(--gp-text-primary);
  margin-bottom: 1rem;
  min-width: 120px;
  text-align: center;
  width: 100%;
  box-sizing: border-box;
  max-width: 100%;
}

.toggle-control {
  margin-top: 0.5rem;
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
</style>