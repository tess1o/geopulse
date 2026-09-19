<template>
  <Card class="timeline-display-card profile-settings-card">
    <template #content>
      <form @submit.prevent="handleSubmit" class="timeline-display-form settings-tab">
        <!-- Section Header -->
        <div class="settings-tab-header">
          <div class="settings-tab-icon">
            <i class="pi pi-eye"></i>
          </div>
          <div class="settings-tab-info">
            <h3 class="settings-tab-title">Timeline &amp; Map</h3>
            <p class="settings-tab-description">
              Customize timeline and map presentation without regenerating timeline data.
            </p>
          </div>
        </div>

        <section class="settings-group" aria-labelledby="timeline-behavior-heading">
          <div class="settings-group-header">
            <h3 id="timeline-behavior-heading">Timeline behavior</h3>
            <p>Choose what appears when you open and interact with the timeline.</p>
          </div>

          <div class="settings-panel">
            <SettingCard
              title="Default date range"
              description="Choose the initial range for Timeline, Dashboard, and Timeline Reports."
              details="Clear the selection to use the app default: Today."
              setting-id="defaultDateRangePreset"
            >
              <template #control>
                <Dropdown
                  id="defaultDateRangePreset"
                  v-model="form.defaultDateRangePreset"
                  :options="defaultDateRangePresetOptions"
                  optionLabel="label"
                  optionValue="value"
                  placeholder="Use app default (Today)"
                  class="w-full"
                  showClear
                />
              </template>
            </SettingCard>

            <SettingCard
              title="Current-location telemetry"
              description="Show telemetry values in the current-location map popup."
              details="This affects only popup visibility. Telemetry storage and GPS Data table are unchanged."
              setting-id="showCurrentLocationTelemetry"
            >
              <template #control>
                <ToggleSwitch
                  v-model="form.showCurrentLocationTelemetry"
                  class="toggle-control"
                />
              </template>
            </SettingCard>

            <SettingCard
              title="Auto-show replay controls"
              description="Open the replay control bar when a trip is selected."
              details="When disabled, trip replay remains available from a compact Replay button."
              setting-id="autoShowTripReplayControls"
            >
              <template #control>
                <ToggleSwitch
                  v-model="form.autoShowTripReplayControls"
                  class="toggle-control"
                />
              </template>
            </SettingCard>
          </div>
        </section>

        <section class="settings-group" aria-labelledby="map-display-heading">
          <div class="settings-group-header">
            <h3 id="map-display-heading">Map display &amp; sources</h3>
            <p>Choose how maps are rendered and where their visual data comes from.</p>
          </div>

          <div class="settings-panel">
            <SettingCard
              title="Map render mode"
              description="Choose the renderer used throughout the map views."
              details="Switching modes keeps both custom source URLs."
              setting-id="mapRenderMode"
            >
              <template #control>
                <Dropdown
                  id="mapRenderMode"
                  v-model="form.mapRenderMode"
                  :options="mapRenderModeOptions"
                  optionLabel="label"
                  optionValue="value"
                  class="w-full"
                />
              </template>
            </SettingCard>

            <SettingCard
              title="3D buildings"
              description="Show building shapes on compatible vector maps."
              details="Available only for MapTiler styles with building height data."
              setting-id="enable3dBuildingsByDefault"
            >
              <template #control>
                <ToggleSwitch
                  v-model="form.enable3dBuildingsByDefault"
                  class="toggle-control"
                />
              </template>
            </SettingCard>

            <SettingCard
              title="Custom raster tiles"
              description="Optional source used when Raster mode is selected."
              details="The URL template must use HTTP or HTTPS and include {z}, {x}, and {y}. Leave empty to use OpenStreetMap."
              setting-id="customMapTileUrl"
            >
              <template #control>
                <div class="field-control">
                  <InputText
                    id="customMapTileUrl"
                    v-model="form.customMapTileUrl"
                    placeholder="https://tiles.example.com/{z}/{x}/{y}.png"
                    :invalid="!!errors.customMapTileUrl"
                    class="w-full"
                    aria-label="Custom raster tile URL"
                  />
                  <small v-if="errors.customMapTileUrl" class="error-message">{{ errors.customMapTileUrl }}</small>
                </div>
              </template>
            </SettingCard>

            <SettingCard
              title="Custom vector style"
              description="Optional style used when Vector mode is selected."
              details="Enter an HTTP or HTTPS style JSON URL. Leave empty to use OpenFreeMap."
              setting-id="customMapStyleUrl"
            >
              <template #control>
                <div class="field-control">
                  <InputText
                    id="customMapStyleUrl"
                    v-model="form.customMapStyleUrl"
                    placeholder="https://tiles.openfreemap.org/styles/liberty"
                    :invalid="!!errors.customMapStyleUrl"
                    class="w-full"
                    aria-label="Custom vector style URL"
                  />
                  <small v-if="errors.customMapStyleUrl" class="error-message">{{ errors.customMapStyleUrl }}</small>
                </div>
              </template>
            </SettingCard>
          </div>
        </section>

        <section class="settings-group" aria-labelledby="map-processing-heading">
          <div class="settings-group-header">
            <h3 id="map-processing-heading">Map processing</h3>
            <p>Control optional route matching and display performance.</p>
          </div>

          <div class="settings-panel">
            <SettingCard
              title="Map matching"
              :description="mapMatchingDescription"
              :details="mapMatchingDetails"
              setting-id="mapMatchingEnabled"
            >
              <template #control>
                <ToggleSwitch
                  v-model="form.mapMatchingEnabled"
                  class="toggle-control"
                  aria-label="Enable map matching"
                  :disabled="readOnly || !mapMatchingAvailable"
                />
              </template>
            </SettingCard>

            <SettingCard
              v-if="mapMatchingAvailable && form.mapMatchingEnabled"
              title="Show raw GPS for"
              description="Keep the original GPS path for selected movement types."
              details="Matching may still run in the background, but matched geometry, progress, comparison controls, and details stay hidden."
              setting-id="mapMatchingExcludedMovementTypes"
            >
              <template #control>
                <MultiSelect
                  id="mapMatchingExcludedMovementTypes"
                  v-model="form.mapMatchingExcludedMovementTypes"
                  :options="mapMatchingMovementTypeOptions"
                  optionLabel="label"
                  optionValue="value"
                  placeholder="Use matched routes for all supported types"
                  display="chip"
                  class="w-full"
                  aria-label="Movement types that show raw GPS"
                  :disabled="readOnly"
                />
              </template>
            </SettingCard>

            <SettingCard
              title="Path simplification"
              description="Reduce the number of points drawn for a trip."
              details="Uses the Douglas-Peucker algorithm to simplify paths without affecting your timeline data."
              setting-id="pathSimplificationEnabled"
            >
              <template #control>
                <ToggleSwitch
                  v-model="form.pathSimplificationEnabled"
                  class="toggle-control"
                />
              </template>
            </SettingCard>

            <SettingCard
              v-if="form.pathSimplificationEnabled"
              title="Simplification tolerance"
              description="Set the distance threshold used to simplify paths."
              :details="{
                'Lower values (1-10m)': 'Preserve more detail, show more points',
                'Higher values (20-100m)': 'More compression, show fewer points'
              }"
              setting-id="pathSimplificationTolerance"
            >
              <template #control>
                <SliderControl
                  v-model="form.pathSimplificationTolerance"
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

            <SettingCard
              v-if="form.pathSimplificationEnabled"
              title="Maximum points"
              description="Limit how many GPS points are displayed in a path."
              details="If a path exceeds this limit, tolerance is automatically increased. Set to 0 for no limit."
              setting-id="pathMaxPoints"
            >
              <template #control>
                <SliderControl
                  v-model="form.pathMaxPoints"
                  :min="0"
                  :max="500"
                  :step="10"
                  :labels="['0 (No limit)', '100 (Balanced)', '500 (High limit)']"
                  :suffix="form.pathMaxPoints === 0 ? '' : ' points'"
                  :input-min="0"
                  :input-max="1000"
                  :decimal-places="0"
                />
              </template>
            </SettingCard>

            <SettingCard
              v-if="form.pathSimplificationEnabled"
              title="Adaptive simplification"
              description="Adjust simplification automatically based on trip length."
              details="Longer trips use higher tolerance for better performance; shorter trips retain more detail."
              setting-id="pathAdaptiveSimplification"
            >
              <template #control>
                <ToggleSwitch
                  v-model="form.pathAdaptiveSimplification"
                  class="toggle-control"
                />
              </template>
            </SettingCard>
          </div>
        </section>

        <!-- Action Buttons -->
        <div class="settings-actions is-sticky">
          <Button
            type="button"
            label="Reset to Defaults"
            severity="secondary"
            outlined
            @click="handleReset"
            :disabled="loading || readOnly"
          />
          <Button
            type="submit"
            label="Save Changes"
            :loading="loading"
            icon="pi pi-check"
            :disabled="readOnly"
          />
        </div>
      </form>
    </template>
  </Card>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import Card from 'primevue/card'
import Button from 'primevue/button'
import InputText from 'primevue/inputtext'
import Dropdown from 'primevue/dropdown'
import MultiSelect from 'primevue/multiselect'
import ToggleSwitch from 'primevue/toggleswitch'
import SettingCard from '@/components/ui/forms/SettingCard.vue'
import SliderControl from '@/components/ui/forms/SliderControl.vue'
import { movementTypeOptions } from '@/composables/useTripReconstructionSegments'

const props = defineProps({
  readOnly: {
    type: Boolean,
    default: false
  },
  initialPreferences: {
    type: Object,
    required: true
  },
})

const emit = defineEmits(['save', 'dirty-change'])

// Form state
const form = ref({
  customMapTileUrl: '',
  customMapStyleUrl: '',
  mapRenderMode: 'VECTOR',
  defaultDateRangePreset: '',
  pathSimplificationEnabled: true,
  pathSimplificationTolerance: 15.0,
  pathMaxPoints: 0,
  pathAdaptiveSimplification: true,
  showCurrentLocationTelemetry: true,
  autoShowTripReplayControls: true,
  enable3dBuildingsByDefault: false,
  mapMatchingEnabled: false,
  mapMatchingExcludedMovementTypes: [],
  mapMatchingAvailable: false
})

const errors = ref({
  customMapTileUrl: null,
  customMapStyleUrl: null
})

const loading = ref(false)
const defaultDateRangePresetOptions = [
  { label: 'Today', value: 'today' },
  { label: 'Yesterday', value: 'yesterday' },
  { label: 'Last 7 days', value: 'lastWeek' },
  { label: 'Last 30 days', value: 'lastMonth' }
]
const mapRenderModeOptions = [
  { label: 'Vector (MapLibre)', value: 'VECTOR' },
  { label: 'Raster (Leaflet)', value: 'RASTER' }
]
const mapMatchingMovementTypeValues = new Set([
  'WALK',
  'RUNNING',
  'BICYCLE',
  'CAR',
  'MOTORCYCLE',
  'PUBLIC_TRANSPORT'
])
const mapMatchingMovementTypeOptions = movementTypeOptions.filter(option => (
  mapMatchingMovementTypeValues.has(option.value)
))
const normalizeMovementTypeList = (values) => {
  const selected = new Set((Array.isArray(values) ? values : []).map(value => String(value).trim().toUpperCase()))
  return mapMatchingMovementTypeOptions.map(option => option.value).filter(value => selected.has(value))
}
const movementTypeListsEqual = (left, right) => {
  const normalizedLeft = normalizeMovementTypeList(left)
  const normalizedRight = normalizeMovementTypeList(right)
  return normalizedLeft.length === normalizedRight.length
    && normalizedLeft.every((value, index) => value === normalizedRight[index])
}
const editablePreferenceKeys = [
  'customMapTileUrl',
  'customMapStyleUrl',
  'mapRenderMode',
  'defaultDateRangePreset',
  'pathSimplificationEnabled',
  'pathSimplificationTolerance',
  'pathMaxPoints',
  'pathAdaptiveSimplification',
  'showCurrentLocationTelemetry',
  'autoShowTripReplayControls',
  'enable3dBuildingsByDefault',
  'mapMatchingEnabled',
  'mapMatchingExcludedMovementTypes'
]

const normalizePreferences = (preferences = {}) => ({
  customMapTileUrl: preferences.customMapTileUrl || '',
  customMapStyleUrl: preferences.customMapStyleUrl || '',
  mapRenderMode: preferences.mapRenderMode || 'VECTOR',
  defaultDateRangePreset: preferences.defaultDateRangePreset || '',
  pathSimplificationEnabled: preferences.pathSimplificationEnabled ?? true,
  pathSimplificationTolerance: Number(preferences.pathSimplificationTolerance ?? 15.0),
  pathMaxPoints: Number(preferences.pathMaxPoints ?? 0),
  pathAdaptiveSimplification: preferences.pathAdaptiveSimplification ?? true,
  showCurrentLocationTelemetry: preferences.showCurrentLocationTelemetry ?? true,
  autoShowTripReplayControls: preferences.autoShowTripReplayControls ?? true,
  enable3dBuildingsByDefault: preferences.enable3dBuildingsByDefault ?? false,
  mapMatchingEnabled: preferences.mapMatchingEnabled ?? false,
  mapMatchingExcludedMovementTypes: normalizeMovementTypeList(preferences.mapMatchingExcludedMovementTypes),
  mapMatchingAvailable: preferences.mapMatchingAvailable ?? false
})

const mapMatchingAvailable = computed(() => form.value.mapMatchingAvailable === true)
const mapMatchingDescription = computed(() => (
  mapMatchingAvailable.value
    ? 'Display cached matched trip geometry when available'
    : 'Unavailable until an administrator configures a Valhalla service.'
))
const mapMatchingDetails = computed(() => (
  mapMatchingAvailable.value
    ? 'Requires a configured Valhalla instance. Raw GPS data, exports, and timeline detection are unchanged.'
    : 'An administrator must enable Map Matching and configure Valhalla before you can turn this on.'
))

const hasChanges = computed(() => {
  const current = normalizePreferences(form.value)
  const initial = normalizePreferences(props.initialPreferences)

  return editablePreferenceKeys.some((key) => (
    key === 'mapMatchingExcludedMovementTypes'
      ? !movementTypeListsEqual(current[key], initial[key])
      : current[key] !== initial[key]
  ))
})

// Initialize form from props
watch(
  () => props.initialPreferences,
  (newPrefs) => {
    if (newPrefs) {
      form.value = normalizePreferences(newPrefs)
    }
  },
  { immediate: true }
)

watch(hasChanges, (changed) => {
  emit('dirty-change', changed)
})

// Validation
const validateCustomMapTileUrl = (url) => {
  if (!url || url.trim() === '') {
    return null // Empty is valid (use defaults)
  }

  const normalizedUrl = url.trim().toLowerCase()

  // Check for required placeholders
  if (!url.includes('{z}') || !url.includes('{x}') || !url.includes('{y}')) {
    return 'URL must contain {z}, {x}, and {y} placeholders'
  }

  // Check for valid protocol
  if (!normalizedUrl.startsWith('http://') && !normalizedUrl.startsWith('https://')) {
    return 'URL must use HTTP or HTTPS protocol'
  }

  // Check for dangerous patterns
  if (
    normalizedUrl.includes('javascript:') ||
    normalizedUrl.includes('data:') ||
    normalizedUrl.includes('file:')
  ) {
    return 'Invalid URL protocol'
  }

  // Check for path traversal
  if (url.includes('..')) {
    return 'Invalid URL format'
  }

  return null
}

const validateCustomMapStyleUrl = (url) => {
  if (!url || url.trim() === '') {
    return null
  }

  const normalizedUrl = url.trim().toLowerCase()

  if (!normalizedUrl.startsWith('http://') && !normalizedUrl.startsWith('https://')) {
    return 'URL must use HTTP or HTTPS protocol'
  }

  if (
    normalizedUrl.includes('javascript:') ||
    normalizedUrl.includes('data:') ||
    normalizedUrl.includes('file:')
  ) {
    return 'Invalid URL protocol'
  }

  if (url.includes('..')) {
    return 'Invalid URL format'
  }

  const looksLikeStyleUrl = normalizedUrl.endsWith('.json') || normalizedUrl.includes('/style') || normalizedUrl.includes('/styles/')
  if (!looksLikeStyleUrl) {
    return 'URL should point to a style JSON endpoint'
  }

  return null
}

const validateForm = () => {
  errors.value.customMapTileUrl = validateCustomMapTileUrl(form.value.customMapTileUrl)
  errors.value.customMapStyleUrl = validateCustomMapStyleUrl(form.value.customMapStyleUrl)
  return !errors.value.customMapTileUrl && !errors.value.customMapStyleUrl
}

const handleSubmit = async () => {
  if (props.readOnly) {
    return
  }

  if (!validateForm()) {
    return
  }

  loading.value = true

  try {
    // Save all display preferences including custom map tile URL
    emit('save', {
      customMapTileUrl: form.value.customMapTileUrl,
      customMapStyleUrl: form.value.customMapStyleUrl,
      mapRenderMode: form.value.mapRenderMode || 'VECTOR',
      defaultDateRangePreset: form.value.defaultDateRangePreset ?? '',
      pathSimplificationEnabled: form.value.pathSimplificationEnabled,
      pathSimplificationTolerance: form.value.pathSimplificationTolerance,
      pathMaxPoints: form.value.pathMaxPoints,
      pathAdaptiveSimplification: form.value.pathAdaptiveSimplification,
      showCurrentLocationTelemetry: form.value.showCurrentLocationTelemetry,
      autoShowTripReplayControls: form.value.autoShowTripReplayControls,
      enable3dBuildingsByDefault: form.value.enable3dBuildingsByDefault,
      mapMatchingEnabled: mapMatchingAvailable.value ? form.value.mapMatchingEnabled : false,
      mapMatchingExcludedMovementTypes: normalizeMovementTypeList(form.value.mapMatchingExcludedMovementTypes)
    })
  } finally {
    loading.value = false
  }
}

const handleReset = () => {
  if (props.readOnly) return
  form.value = {
    customMapTileUrl: '',
    customMapStyleUrl: '',
    mapRenderMode: 'VECTOR',
    defaultDateRangePreset: '',
    pathSimplificationEnabled: true,
    pathSimplificationTolerance: 15.0,
    pathMaxPoints: 0,
    pathAdaptiveSimplification: true,
    showCurrentLocationTelemetry: true,
    autoShowTripReplayControls: true,
    enable3dBuildingsByDefault: false,
    mapMatchingEnabled: false,
    mapMatchingExcludedMovementTypes: [],
    mapMatchingAvailable: mapMatchingAvailable.value
  }
  errors.value = {
    customMapTileUrl: null,
    customMapStyleUrl: null
  }
}

</script>
