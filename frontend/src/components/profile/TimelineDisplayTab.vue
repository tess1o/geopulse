<template>
  <Card class="timeline-display-card">
    <template #content>
      <form @submit.prevent="handleSubmit" class="timeline-display-form">
        <!-- Section Header -->
        <div class="display-header">
          <div class="display-icon">
            <i class="pi pi-eye"></i>
          </div>
          <div class="display-info">
            <h3 class="display-title">Display Settings</h3>
            <p class="display-description">
              These settings affect only how your timeline is displayed in the UI.
              Changes take effect immediately and do not require timeline regeneration.
            </p>
          </div>
        </div>

        <!-- Map Tile Provider Section -->
        <div class="section">
          <h3 class="section-title">Map Tile Provider</h3>
          <p class="section-description">
            Choose rendering mode and configure both raster and vector map sources
          </p>

          <div class="form-field">
            <label for="mapRenderMode" class="form-label">
              Map Render Mode
            </label>
            <Dropdown
              id="mapRenderMode"
              v-model="form.mapRenderMode"
              :options="mapRenderModeOptions"
              optionLabel="label"
              optionValue="value"
              class="w-full"
            />
            <small class="help-text">
              Switching modes keeps both custom URLs so you can toggle anytime.
            </small>
          </div>

          <div class="form-field">
            <label for="customMapTileUrl" class="form-label">
              Custom Raster Tile URL
              <i class="pi pi-info-circle" v-tooltip.right="'Optional: Raster tile template. Must include {z}, {x}, and {y} placeholders.'"></i>
            </label>
            <InputText
              id="customMapTileUrl"
              v-model="form.customMapTileUrl"
              placeholder="https://api.maptiler.com/maps/streets/{z}/{x}/{y}.png?key=YOUR_KEY"
              :invalid="!!errors.customMapTileUrl"
              class="w-full"
            />
            <small v-if="errors.customMapTileUrl" class="error-message">
              {{ errors.customMapTileUrl }}
            </small>
            <small v-else class="help-text">
              Used when render mode is Raster. Leave empty to use default OSM raster tiles.
            </small>
          </div>

          <div class="form-field">
            <label for="customMapStyleUrl" class="form-label">
              Custom Vector Style URL
              <i class="pi pi-info-circle" v-tooltip.right="'Optional: Vector style URL (style.json). Must use HTTP or HTTPS.'"></i>
            </label>
            <InputText
              id="customMapStyleUrl"
              v-model="form.customMapStyleUrl"
              placeholder="https://tiles.openfreemap.org/styles/liberty"
              :invalid="!!errors.customMapStyleUrl"
              class="w-full"
            />
            <small v-if="errors.customMapStyleUrl" class="error-message">
              {{ errors.customMapStyleUrl }}
            </small>
            <small v-else class="help-text">
              Used when render mode is Vector. Leave empty to use default OpenFreeMap style.
            </small>
          </div>
        </div>

        <!-- Default Date Range Section -->
        <div class="section">
          <h3 class="section-title">Default Date Range</h3>
          <p class="section-description">
            Choose the preset used by default on Timeline, Dashboard, and Timeline Reports
          </p>

          <div class="form-field">
            <label for="defaultDateRangePreset" class="form-label">
              Default Date Range Preset
              <i class="pi pi-info-circle" v-tooltip.right="'If not set, GeoPulse keeps the current default behavior (Today).'"></i>
            </label>
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
          </div>
        </div>

        <div class="section">
          <h3 class="section-title">Current Location Popup</h3>
          <p class="section-description">
            Control whether telemetry from the latest point is shown in the current-location popup
          </p>

          <SettingCard
            title="Show Telemetry In Current Location Popup"
            description="Display mapped telemetry values in the map popup for your current location"
            details="This affects only popup visibility. Telemetry storage and GPS Data table are unchanged."
          >
            <template #control>
              <div class="control-value">{{ form.showCurrentLocationTelemetry ? 'Enabled' : 'Hidden' }}</div>
              <ToggleSwitch
                v-model="form.showCurrentLocationTelemetry"
                class="toggle-control"
              />
            </template>
          </SettingCard>
        </div>

        <!-- GPS Path Simplification Section -->
        <div class="section">
          <h3 class="section-title">GPS Path Simplification</h3>
          <p class="section-description">
            Configure how GPS paths are simplified when displayed on the map
          </p>

          <!-- Enable Path Simplification -->
          <SettingCard
            title="Enable Path Simplification"
            description="Reduce the number of GPS points displayed while preserving route accuracy"
            details="Uses the Douglas-Peucker algorithm to simplify paths without affecting your timeline data"
          >
            <template #control>
              <div class="control-value">{{ form.pathSimplificationEnabled ? 'Enabled' : 'Disabled' }}</div>
              <ToggleSwitch
                v-model="form.pathSimplificationEnabled"
                class="toggle-control"
              />
            </template>
          </SettingCard>

          <!-- Simplification Tolerance -->
          <SettingCard
            v-if="form.pathSimplificationEnabled"
            title="Simplification Tolerance"
            description="Distance threshold in meters for simplifying paths"
            :details="{
              'Lower values (1-10m)': 'Preserve more detail, show more points',
              'Higher values (20-100m)': 'More compression, show fewer points'
            }"
          >
            <template #control>
              <div class="control-value">{{ form.pathSimplificationTolerance }}m</div>
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

          <!-- Maximum Points -->
          <SettingCard
            v-if="form.pathSimplificationEnabled"
            title="Maximum Points"
            description="Maximum number of GPS points to display in a path"
            details="If a path exceeds this limit, tolerance is automatically increased. Set to 0 for no limit"
          >
            <template #control>
              <div class="control-value">{{ form.pathMaxPoints === 0 ? 'No limit' : form.pathMaxPoints + ' points' }}</div>
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

          <!-- Adaptive Simplification -->
          <SettingCard
            v-if="form.pathSimplificationEnabled"
            title="Adaptive Simplification"
            description="Automatically adjust simplification based on trip length"
            details="Longer trips use higher tolerance for better performance, shorter trips maintain higher detail"
          >
            <template #control>
              <div class="control-value">{{ form.pathAdaptiveSimplification ? 'Enabled' : 'Disabled' }}</div>
              <ToggleSwitch
                v-model="form.pathAdaptiveSimplification"
                class="toggle-control"
              />
            </template>
          </SettingCard>
        </div>

        <!-- Action Buttons -->
        <div class="form-actions">
          <Button
            type="button"
            label="Reset to Defaults"
            severity="secondary"
            outlined
            @click="handleReset"
            :disabled="loading"
          />
          <Button
            type="submit"
            label="Save Changes"
            :loading="loading"
            icon="pi pi-check"
          />
        </div>
      </form>
    </template>
  </Card>
</template>

<script setup>
import { ref, watch } from 'vue'
import Card from 'primevue/card'
import Button from 'primevue/button'
import InputText from 'primevue/inputtext'
import Dropdown from 'primevue/dropdown'
import ToggleSwitch from 'primevue/toggleswitch'
import SettingCard from '@/components/ui/forms/SettingCard.vue'
import SliderControl from '@/components/ui/forms/SliderControl.vue'

const props = defineProps({
  initialPreferences: {
    type: Object,
    required: true
  },
})

const emit = defineEmits(['save'])

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
  showCurrentLocationTelemetry: true
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

// Initialize form from props
watch(
  () => props.initialPreferences,
  (newPrefs) => {
    if (newPrefs) {
      form.value = {
        customMapTileUrl: newPrefs.customMapTileUrl || '',
        customMapStyleUrl: newPrefs.customMapStyleUrl || '',
        mapRenderMode: newPrefs.mapRenderMode || 'VECTOR',
        defaultDateRangePreset: newPrefs.defaultDateRangePreset || '',
        pathSimplificationEnabled: newPrefs.pathSimplificationEnabled ?? true,
        pathSimplificationTolerance: newPrefs.pathSimplificationTolerance ?? 15.0,
        pathMaxPoints: newPrefs.pathMaxPoints ?? 0,
        pathAdaptiveSimplification: newPrefs.pathAdaptiveSimplification ?? true,
        showCurrentLocationTelemetry: newPrefs.showCurrentLocationTelemetry ?? true
      }
    }
  },
  { immediate: true }
)

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
      showCurrentLocationTelemetry: form.value.showCurrentLocationTelemetry
    })
  } finally {
    loading.value = false
  }
}

const handleReset = () => {
  form.value = {
    customMapTileUrl: '',
    customMapStyleUrl: '',
    mapRenderMode: 'VECTOR',
    defaultDateRangePreset: '',
    pathSimplificationEnabled: true,
    pathSimplificationTolerance: 15.0,
    pathMaxPoints: 0,
    pathAdaptiveSimplification: true,
    showCurrentLocationTelemetry: true
  }
  errors.value = {
    customMapTileUrl: null,
    customMapStyleUrl: null
  }
}
</script>

<style scoped>
.timeline-display-card {
  width: 100%;
  box-sizing: border-box;
}

.timeline-display-form {
  display: flex;
  flex-direction: column;
  gap: 2rem;
}

/* Section Header — matches Security / Immich / AI tab header pattern */
.display-header {
  display: flex;
  align-items: center;
  gap: 1rem;
  margin-bottom: 2rem;
  padding: 1rem;
  background: var(--gp-surface-light);
  border-radius: var(--gp-radius-medium);
}

.display-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 3rem;
  height: 3rem;
  background: var(--gp-primary);
  color: white;
  border-radius: 50%;
  font-size: 1.25rem;
  flex-shrink: 0;
}

.display-info {
  flex: 1;
}

.display-title {
  font-size: 1.1rem;
  font-weight: 600;
  color: var(--gp-text-primary);
  margin: 0 0 0.25rem 0;
}

.display-description {
  font-size: 0.9rem;
  color: var(--gp-text-secondary);
  margin: 0;
  line-height: 1.4;
}

/* Section */
.section {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.section-title {
  font-size: 1.25rem;
  font-weight: 600;
  color: var(--gp-text-primary);
  margin: 0 0 0.25rem 0;
}

.section-description {
  font-size: 0.9rem;
  color: var(--gp-text-secondary);
  margin: 0 0 0.5rem 0;
}

/* Form Field */
.form-field {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.form-label {
  font-weight: 500;
  color: var(--gp-text-primary);
  font-size: 0.95rem;
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.form-label .pi-info-circle {
  color: var(--gp-text-secondary);
  cursor: help;
}

.help-text {
  color: var(--gp-text-secondary);
  font-size: 0.875rem;
  line-height: 1.4;
}

.error-message {
  color: var(--gp-danger);
  font-size: 0.875rem;
}

/* Control Styles */
.control-value {
  font-weight: 500;
  color: var(--gp-text-primary);
  min-width: 80px;
  text-align: right;
}

.toggle-control {
  margin-left: auto;
}

/* Form Actions */
.form-actions {
  display: flex;
  gap: 1rem;
  justify-content: flex-end;
  padding-top: 1rem;
  border-top: 1px solid var(--gp-border);
}

/* Responsive */
@media (max-width: 768px) {
  .display-header {
    flex-direction: column;
    text-align: center;
    gap: 1rem;
  }

  .form-actions {
    flex-direction: column-reverse;
  }

  .form-actions button {
    width: 100%;
  }
}
</style>
