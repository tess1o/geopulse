<template>
  <div class="form-section border-t border-surface pt-4">
    <label class="form-label font-semibold text-lg">{{ t('ui.gpsFiltering.title') }}</label>
    <div class="flex items-center gap-3 mt-2">
      <ToggleSwitch
          :modelValue="settings.filterInaccurateData"
          @update:modelValue="value => emit('update:settings', { ...settings, filterInaccurateData: value })"
          inputId="filterInaccurateData"
      />
      <label for="filterInaccurateData" class="font-medium">{{ t('ui.gpsFiltering.filterLabel') }}</label>
    </div>
    <p class="text-sm text-muted-color mt-1">{{ t('ui.gpsFiltering.filterHint') }}</p>

    <div v-if="settings.filterInaccurateData" class="grid grid-cols-1 gap-4 mt-4">
            <div class="form-field">
              <div class="flex items-center justify-between">
                <label for="maxAccuracy" class="form-label">{{ t('ui.gpsFiltering.maxAccuracyLabel') }}</label>
                <InputNumber
                  id="maxAccuracy"
                  :modelValue="settings.maxAllowedAccuracy"
                  @update:modelValue="value => emit('update:settings', { ...settings, maxAllowedAccuracy: value })"
                  :placeholder="t('ui.gpsFiltering.maxAccuracyPlaceholder')"
                  class="narrow-input"
                />
              </div>
              <small class="text-muted-color mt-1">{{ t('ui.gpsFiltering.maxAccuracyHint') }}</small>
            </div>
            <div class="form-field">
              <div class="flex items-center justify-between">
                <label for="maxSpeed" class="form-label">{{ t('ui.gpsFiltering.maxSpeedLabel') }}</label>
                <InputNumber
                  id="maxSpeed"
                  :modelValue="settings.maxAllowedSpeed"
                  @update:modelValue="value => emit('update:settings', { ...settings, maxAllowedSpeed: value })"
                  :placeholder="t('ui.gpsFiltering.maxSpeedPlaceholder')"
                  class="narrow-input"
                />
              </div>
              <small class="text-muted-color mt-1">{{ t('ui.gpsFiltering.maxSpeedHint') }}</small>
            </div>    </div>
  </div>

  <div class="form-section border-t border-surface pt-4 mt-4">
    <label class="form-label font-semibold text-lg">{{ t('ui.gpsFiltering.duplicateDetectionTitle') }}</label>
    <div class="flex items-center gap-3 mt-2">
      <ToggleSwitch
          :modelValue="settings.enableDuplicateDetection"
          @update:modelValue="value => emit('update:settings', { ...settings, enableDuplicateDetection: value })"
          inputId="enableDuplicateDetection"
      />
      <label for="enableDuplicateDetection" class="font-medium">{{ t('ui.gpsFiltering.duplicateDetectionLabel') }}</label>
    </div>
    <p class="text-sm text-muted-color mt-1">{{ t('ui.gpsFiltering.duplicateDetectionHint') }}</p>

    <div v-if="settings.enableDuplicateDetection" class="grid grid-cols-1 gap-4 mt-4">
            <div class="form-field">
              <div class="flex items-center justify-between">
                <label for="duplicateThreshold" class="form-label">{{ t('ui.gpsFiltering.thresholdLabel') }}</label>
                <InputNumber
                  id="duplicateThreshold"
                  :modelValue="settings.duplicateDetectionThresholdMinutes"
                  @update:modelValue="value => emit('update:settings', { ...settings, duplicateDetectionThresholdMinutes: value })"
                  :placeholder="t('ui.gpsFiltering.thresholdPlaceholder')"
                  class="narrow-input"
                />
              </div>
              <small class="text-muted-color mt-1">{{ t('ui.gpsFiltering.thresholdHint') }}</small>
            </div>
    </div>
  </div>
</template>

<script setup>
import {defineProps, defineEmits} from 'vue'
import {useI18n} from 'vue-i18n'
import ToggleSwitch from 'primevue/toggleswitch'
import InputNumber from 'primevue/inputnumber'

const {t} = useI18n()

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
  background-color: var(--gp-surface-muted);
}

.form-label {
  font-weight: 600;
  color: var(--gp-text-primary);
}

.narrow-input {
  max-width: 15rem;
}
</style>