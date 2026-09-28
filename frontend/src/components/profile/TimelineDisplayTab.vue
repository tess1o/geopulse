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
            <h3 class="settings-tab-title">{{ t('profile.timeline.title') }}</h3>
            <p class="settings-tab-description">
              {{ t('profile.timeline.description') }}
            </p>
          </div>
        </div>

        <section class="settings-group" aria-labelledby="timeline-behavior-heading">
          <div class="settings-group-header">
            <h3 id="timeline-behavior-heading">{{ t('profile.timeline.behavior.heading') }}</h3>
            <p>{{ t('profile.timeline.behavior.description') }}</p>
          </div>

          <div class="settings-panel">
            <SettingCard
              :title="t('profile.timeline.behavior.defaultDateRange.title')"
              :description="t('profile.timeline.behavior.defaultDateRange.description')"
              :details="t('profile.timeline.behavior.defaultDateRange.details')"
              setting-id="defaultDateRangePreset"
            >
              <template #control>
                <Dropdown
                  id="defaultDateRangePreset"
                  v-model="form.defaultDateRangePreset"
                  :options="defaultDateRangePresetOptions"
                  optionLabel="label"
                  optionValue="value"
                  :placeholder="t('profile.timeline.behavior.defaultDateRange.placeholder')"
                  class="w-full"
                  showClear
                />
              </template>
            </SettingCard>

            <SettingCard
              :title="t('profile.timeline.behavior.telemetry.title')"
              :description="t('profile.timeline.behavior.telemetry.description')"
              :details="t('profile.timeline.behavior.telemetry.details')"
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
              :title="t('profile.timeline.behavior.replayControls.title')"
              :description="t('profile.timeline.behavior.replayControls.description')"
              :details="t('profile.timeline.behavior.replayControls.details')"
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
            <h3 id="map-display-heading">{{ t('profile.timeline.sources.heading') }}</h3>
            <p>{{ t('profile.timeline.sources.description') }}</p>
          </div>

          <div class="settings-panel">
            <SettingCard
              :title="t('profile.timeline.sources.renderMode.title')"
              :description="t('profile.timeline.sources.renderMode.description')"
              :details="t('profile.timeline.sources.renderMode.details')"
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
              :title="t('profile.timeline.sources.buildings3d.title')"
              :description="t('profile.timeline.sources.buildings3d.description')"
              :details="t('profile.timeline.sources.buildings3d.details')"
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
              :title="t('profile.timeline.sources.rasterTiles.title')"
              :description="t('profile.timeline.sources.rasterTiles.description')"
              :details="t('profile.timeline.sources.rasterTiles.details')"
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
                    :aria-label="t('profile.timeline.sources.rasterTiles.ariaLabel')"
                  />
                  <small v-if="errors.customMapTileUrl" class="error-message">{{ errors.customMapTileUrl }}</small>
                </div>
              </template>
            </SettingCard>

            <SettingCard
              :title="t('profile.timeline.sources.vectorStyle.title')"
              :description="t('profile.timeline.sources.vectorStyle.description')"
              :details="t('profile.timeline.sources.vectorStyle.details')"
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
                    :aria-label="t('profile.timeline.sources.vectorStyle.ariaLabel')"
                  />
                  <small v-if="errors.customMapStyleUrl" class="error-message">{{ errors.customMapStyleUrl }}</small>
                </div>
              </template>
            </SettingCard>
          </div>
        </section>

        <section class="settings-group" aria-labelledby="map-processing-heading">
          <div class="settings-group-header">
            <h3 id="map-processing-heading">{{ t('profile.timeline.processing.heading') }}</h3>
            <p>{{ t('profile.timeline.processing.description') }}</p>
          </div>

          <div class="settings-panel">
            <SettingCard
              :title="t('profile.timeline.processing.mapMatching.title')"
              :description="mapMatchingDescription"
              :details="mapMatchingDetails"
              setting-id="mapMatchingEnabled"
            >
              <template #control>
                <ToggleSwitch
                  v-model="form.mapMatchingEnabled"
                  class="toggle-control"
                  :aria-label="t('profile.timeline.toggleAria.mapMatching')"
                  :disabled="readOnly || !mapMatchingAvailable"
                />
              </template>
            </SettingCard>

            <SettingCard
              v-if="mapMatchingAvailable && form.mapMatchingEnabled"
              :title="t('profile.timeline.processing.rawGps.title')"
              :description="t('profile.timeline.processing.rawGps.description')"
              :details="t('profile.timeline.processing.rawGps.details')"
              setting-id="mapMatchingExcludedMovementTypes"
            >
              <template #control>
                <MultiSelect
                  id="mapMatchingExcludedMovementTypes"
                  v-model="form.mapMatchingExcludedMovementTypes"
                  :options="mapMatchingMovementTypeOptions"
                  optionLabel="label"
                  optionValue="value"
                  :placeholder="t('profile.timeline.processing.rawGps.placeholder')"
                  display="chip"
                  class="w-full"
                  :aria-label="t('profile.timeline.processing.rawGps.ariaLabel')"
                  :disabled="readOnly"
                />
              </template>
            </SettingCard>

            <SettingCard
              :title="t('profile.timeline.processing.simplification.title')"
              :description="t('profile.timeline.processing.simplification.description')"
              :details="t('profile.timeline.processing.simplification.details')"
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
              :title="t('profile.timeline.processing.tolerance.title')"
              :description="t('profile.timeline.processing.tolerance.description')"
              :details="toleranceDetails"
              setting-id="pathSimplificationTolerance"
            >
              <template #control>
                <SliderControl
                  v-model="form.pathSimplificationTolerance"
                  :min="1"
                  :max="50"
                  :step="1"
                  :labels="toleranceLabels"
                  suffix=" m"
                  :input-min="1"
                  :input-max="100"
                  :decimal-places="0"
                />
              </template>
            </SettingCard>

            <SettingCard
              v-if="form.pathSimplificationEnabled"
              :title="t('profile.timeline.processing.maxPoints.title')"
              :description="t('profile.timeline.processing.maxPoints.description')"
              :details="t('profile.timeline.processing.maxPoints.details')"
              setting-id="pathMaxPoints"
            >
              <template #control>
                <SliderControl
                  v-model="form.pathMaxPoints"
                  :min="0"
                  :max="500"
                  :step="10"
                  :labels="maxPointsLabels"
                  :suffix="form.pathMaxPoints === 0 ? '' : t('profile.timeline.pointsSuffix')"
                  :input-min="0"
                  :input-max="1000"
                  :decimal-places="0"
                />
              </template>
            </SettingCard>

            <SettingCard
              v-if="form.pathSimplificationEnabled"
              :title="t('profile.timeline.processing.adaptive.title')"
              :description="t('profile.timeline.processing.adaptive.description')"
              :details="t('profile.timeline.processing.adaptive.details')"
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
            :label="t('profile.timeline.resetToDefaults')"
            severity="secondary"
            outlined
            @click="handleReset"
            :disabled="loading || readOnly"
          />
          <Button
            type="submit"
            :label="t('profile.timeline.saveChanges')"
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
import { useI18n } from 'vue-i18n'
import Card from 'primevue/card'
import Button from 'primevue/button'
import InputText from 'primevue/inputtext'
import Dropdown from 'primevue/dropdown'
import MultiSelect from 'primevue/multiselect'
import ToggleSwitch from 'primevue/toggleswitch'
import SettingCard from '@/components/ui/forms/SettingCard.vue'
import SliderControl from '@/components/ui/forms/SliderControl.vue'
import { movementTypeOptions } from '@/composables/useTripReconstructionSegments'

const { t } = useI18n()

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
// Option tables carry keys, not text; PrimeVue's `optionLabel` reads a field, so the labels are
// resolved here -- inside a computed, which keeps them reactive to a language change.
const defaultDateRangePresetOptions = computed(() => [
  { label: t('profile.timeline.dateRangeOptions.today'), value: 'today' },
  { label: t('profile.timeline.dateRangeOptions.yesterday'), value: 'yesterday' },
  { label: t('profile.timeline.dateRangeOptions.lastWeek'), value: 'lastWeek' },
  { label: t('profile.timeline.dateRangeOptions.lastMonth'), value: 'lastMonth' }
])
const mapRenderModeOptions = computed(() => [
  { label: t('profile.timeline.renderModeOptions.vector'), value: 'VECTOR' },
  { label: t('profile.timeline.renderModeOptions.raster'), value: 'RASTER' }
])
const toleranceLabels = computed(() => [
  t('profile.timeline.toleranceLabels.low'),
  t('profile.timeline.toleranceLabels.mid'),
  t('profile.timeline.toleranceLabels.high')
])
const maxPointsLabels = computed(() => [
  t('profile.timeline.maxPointsLabels.none'),
  t('profile.timeline.maxPointsLabels.balanced'),
  t('profile.timeline.maxPointsLabels.high')
])
// SettingCard renders an object `details` as `label: value` rows, so both halves are translated.
const toleranceDetails = computed(() => ({
  [t('profile.timeline.processing.tolerance.detailLowerLabel')]: t('profile.timeline.processing.tolerance.detailLowerValue'),
  [t('profile.timeline.processing.tolerance.detailHigherLabel')]: t('profile.timeline.processing.tolerance.detailHigherValue')
}))
const mapMatchingMovementTypeValues = new Set([
  'WALK',
  'RUNNING',
  'BICYCLE',
  'CAR',
  'MOTORCYCLE',
  'PUBLIC_TRANSPORT'
])
const mapMatchingMovementTypeOptions = computed(() => movementTypeOptions
  .filter(option => mapMatchingMovementTypeValues.has(option.value))
  .map(option => ({ label: t(option.labelKey), value: option.value })))
// The order the backend-normalisation helpers expect, kept independent of the display labels.
const mapMatchingMovementTypeOrder = movementTypeOptions
  .filter(option => mapMatchingMovementTypeValues.has(option.value))
  .map(option => option.value)
const normalizeMovementTypeList = (values) => {
  const selected = new Set((Array.isArray(values) ? values : []).map(value => String(value).trim().toUpperCase()))
  return mapMatchingMovementTypeOrder.filter(value => selected.has(value))
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
    ? t('profile.timeline.processing.mapMatching.descriptionAvailable')
    : t('profile.timeline.processing.mapMatching.descriptionUnavailable')
))
const mapMatchingDetails = computed(() => (
  mapMatchingAvailable.value
    ? t('profile.timeline.processing.mapMatching.detailsAvailable')
    : t('profile.timeline.processing.mapMatching.detailsUnavailable')
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
    return t('profile.timeline.validation.tilePlaceholders')
  }

  // Check for valid protocol
  if (!normalizedUrl.startsWith('http://') && !normalizedUrl.startsWith('https://')) {
    return t('profile.timeline.validation.protocol')
  }

  // Check for dangerous patterns
  if (
    normalizedUrl.includes('javascript:') ||
    normalizedUrl.includes('data:') ||
    normalizedUrl.includes('file:')
  ) {
    return t('profile.timeline.validation.invalidProtocol')
  }

  // Check for path traversal
  if (url.includes('..')) {
    return t('profile.timeline.validation.invalidFormat')
  }

  return null
}

const validateCustomMapStyleUrl = (url) => {
  if (!url || url.trim() === '') {
    return null
  }

  const normalizedUrl = url.trim().toLowerCase()

  if (!normalizedUrl.startsWith('http://') && !normalizedUrl.startsWith('https://')) {
    return t('profile.timeline.validation.protocol')
  }

  if (
    normalizedUrl.includes('javascript:') ||
    normalizedUrl.includes('data:') ||
    normalizedUrl.includes('file:')
  ) {
    return t('profile.timeline.validation.invalidProtocol')
  }

  if (url.includes('..')) {
    return t('profile.timeline.validation.invalidFormat')
  }

  const looksLikeStyleUrl = normalizedUrl.endsWith('.json') || normalizedUrl.includes('/style') || normalizedUrl.includes('/styles/')
  if (!looksLikeStyleUrl) {
    return t('profile.timeline.validation.styleEndpoint')
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
