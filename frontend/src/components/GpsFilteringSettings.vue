<template>
  <div class="form-section border-t border-gray-200 pt-4">
    <label class="form-label font-semibold text-lg">GPS Data Filtering</label>
    <div class="flex items-center gap-3 mt-2">
      <ToggleSwitch
          :modelValue="settings.filterInaccurateData"
          @update:modelValue="value => emit('update:settings', { ...settings, filterInaccurateData: value })"
          inputId="filterInaccurateData"
      />
      <label for="filterInaccurateData" class="font-medium">Filter inaccurate data points</label>
    </div>
    <p class="text-sm text-gray-500 mt-1">Enable to filter out GPS points with accuracy or speed beyond the defined
      limits.</p>

    <div v-if="settings.filterInaccurateData" class="grid grid-cols-1 gap-4 mt-4">
            <div class="form-field">
              <div class="flex items-center justify-between">
                <label for="maxAccuracy" class="form-label">Max Allowed Accuracy (meters)</label>
                <InputNumber
                  id="maxAccuracy"
                  :modelValue="settings.maxAllowedAccuracy"
                  @update:modelValue="value => emit('update:settings', { ...settings, maxAllowedAccuracy: value })"
                  placeholder="e.g., 100"
                  class="narrow-input"
                />
              </div>
              <small class="text-gray-500 mt-1">Points with accuracy above this value will be rejected.</small>
            </div>
            <div class="form-field">
              <div class="flex items-center justify-between">
                <label for="maxSpeed" class="form-label">Max Allowed Speed (km/h)</label>
                <InputNumber
                  id="maxSpeed"
                  :modelValue="settings.maxAllowedSpeed"
                  @update:modelValue="value => emit('update:settings', { ...settings, maxAllowedSpeed: value })"
                  placeholder="e.g., 250"
                  class="narrow-input"
                />
              </div>
              <small class="text-gray-500 mt-1">Points with speed above this value will be rejected.</small>
            </div>    </div>
  </div>

  <div class="form-section border-t border-gray-200 pt-4 mt-4">
    <label class="form-label font-semibold text-lg">Duplicate Detection</label>
    <div class="flex items-center gap-3 mt-2">
      <ToggleSwitch
          :modelValue="settings.enableDuplicateDetection"
          @update:modelValue="value => emit('update:settings', { ...settings, enableDuplicateDetection: value })"
          inputId="enableDuplicateDetection"
      />
      <label for="enableDuplicateDetection" class="font-medium">Enable duplicate detection</label>
    </div>
    <p class="text-sm text-gray-500 mt-1">Skip GPS points that have the same location within a time window. Useful for devices that send repeated locations when stationary.</p>

    <div v-if="settings.enableDuplicateDetection" class="grid grid-cols-1 gap-4 mt-4">
            <div class="form-field">
              <div class="flex items-center justify-between">
                <label for="duplicateThreshold" class="form-label">Time threshold (minutes)</label>
                <InputNumber
                  id="duplicateThreshold"
                  :modelValue="settings.duplicateDetectionThresholdMinutes"
                  @update:modelValue="value => emit('update:settings', { ...settings, duplicateDetectionThresholdMinutes: value })"
                  placeholder="e.g., 2"
                  class="narrow-input"
                />
              </div>
              <small class="text-gray-500 mt-1">Points with the same coordinates (within ~11m) in this time window will be skipped. Leave empty to use global default.</small>
            </div>
    </div>
  </div>
</template>

<script setup>
import {defineProps, defineEmits} from 'vue'
import ToggleSwitch from 'primevue/toggleswitch'
import InputNumber from 'primevue/inputnumber'

const props = defineProps({
  settings: {
    type: Object,
    default: () => ({
      filterInaccurateData: false,
      maxAllowedAccuracy: null,
      maxAllowedSpeed: null,
      enableDuplicateDetection: false,
      duplicateDetectionThresholdMinutes: null
    })
  }
})

const emit = defineEmits(['update:settings'])
</script>

<style scoped>
.form-section {
  padding: 1rem;
  border-radius: var(--gp-radius-medium);
  background-color: var(--gp-surface-light);
}

.form-label {
  font-weight: 600;
  color: var(--gp-text-primary);
}

.text-gray-500 {
  color: var(--gp-text-secondary);
}

.narrow-input {
  max-width: 15rem;
}


</style>