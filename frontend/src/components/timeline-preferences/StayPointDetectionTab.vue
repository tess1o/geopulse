<template>
  <PreferencesTabLayout
    :title="t('timeline.preferences.stayPointDetection.title')"
    :description="t('timeline.preferences.stayPointDetection.description')"
  >
    <div class="settings-panel">
    <!-- Stay Detection Radius -->
    <SettingCard
      :title="t('timeline.preferences.stayPointDetection.radius.title')"
      :description="t('timeline.preferences.stayPointDetection.radius.description')"
      :details="radiusDetails"
      setting-id="staypointRadiusMeters"
    >
      <template #control>
        <SliderControl
          v-if="modelValue.staypointRadiusMeters !== undefined"
          :model-value="modelValue.staypointRadiusMeters"
          @update:model-value="updatePref('staypointRadiusMeters', $event)"
          :min="10"
          :max="500"
          :step="10"
          :labels="radiusLabels"
          :suffix="t('timeline.preferences.stayPointDetection.radius.suffix')"
          :input-min="1"
          :input-max="2000"
          :decimal-places="0"
        />
      </template>
    </SettingCard>

    <!-- Min Trip Duration -->
    <SettingCard
      :title="t('timeline.preferences.stayPointDetection.minDuration.title')"
      :description="t('timeline.preferences.stayPointDetection.minDuration.description')"
      :details="minDurationDetails"
      setting-id="staypointMinDurationMinutes"
    >
      <template #control>
        <SliderControl
          v-if="modelValue.staypointMinDurationMinutes !== undefined"
          :model-value="modelValue.staypointMinDurationMinutes"
          @update:model-value="updatePref('staypointMinDurationMinutes', $event)"
          :min="1"
          :max="60"
          :step="1"
          :labels="minDurationLabels"
          :suffix="t('timeline.preferences.stayPointDetection.minDuration.suffix')"
          :input-min="1"
          :input-max="300"
          :decimal-places="0"
        />
      </template>
    </SettingCard>

    <!-- Enhanced Filtering -->
    <SettingCard
      :title="t('timeline.preferences.stayPointDetection.enhancedFiltering.title')"
      :description="t('timeline.preferences.stayPointDetection.enhancedFiltering.description')"
      :details="t('timeline.preferences.stayPointDetection.enhancedFiltering.details')"
      setting-id="useVelocityAccuracy"
    >
      <template #control>
        <ToggleSwitch
          :model-value="modelValue.useVelocityAccuracy"
          @update:model-value="updatePref('useVelocityAccuracy', $event)"
          class="toggle-control"
        />
      </template>
    </SettingCard>

    <!-- Velocity Threshold -->
    <SettingCard
      v-if="modelValue.useVelocityAccuracy"
      :title="t('timeline.preferences.stayPointDetection.velocityThreshold.title')"
      :description="t('timeline.preferences.stayPointDetection.velocityThreshold.description')"
      :details="velocityThresholdDetails"
      setting-id="staypointVelocityThreshold"
    >
      <template #control>
        <SliderControl
          v-if="modelValue.staypointVelocityThreshold !== undefined"
          :model-value="modelValue.staypointVelocityThreshold"
          @update:model-value="updatePref('staypointVelocityThreshold', $event)"
          :min="1"
          :max="20"
          :step="0.5"
          :labels="velocityThresholdLabels"
          :suffix="t('timeline.preferences.stayPointDetection.velocityThreshold.suffix')"
          :input-min="0.5"
          :input-max="50"
          :decimal-places="1"
        />
      </template>
    </SettingCard>

    <!-- Accuracy Threshold -->
    <SettingCard
      v-if="modelValue.useVelocityAccuracy"
      :title="t('timeline.preferences.stayPointDetection.accuracyThreshold.title')"
      :description="t('timeline.preferences.stayPointDetection.accuracyThreshold.description')"
      :details="accuracyThresholdDetails"
      setting-id="staypointMaxAccuracyThreshold"
    >
      <template #control>
        <SliderControl
          v-if="modelValue.staypointMaxAccuracyThreshold !== undefined"
          :model-value="modelValue.staypointMaxAccuracyThreshold"
          @update:model-value="updatePref('staypointMaxAccuracyThreshold', $event)"
          :min="5"
          :max="200"
          :step="5"
          :labels="accuracyThresholdLabels"
          :suffix="t('timeline.preferences.stayPointDetection.accuracyThreshold.suffix')"
          :input-min="1"
          :input-max="500"
          :decimal-places="1"
        />
      </template>
    </SettingCard>

    <!-- Min Accuracy Ratio -->
    <SettingCard
      v-if="modelValue.useVelocityAccuracy"
      :title="t('timeline.preferences.stayPointDetection.minAccuracyRatio.title')"
      :description="t('timeline.preferences.stayPointDetection.minAccuracyRatio.description')"
      :details="t('timeline.preferences.stayPointDetection.minAccuracyRatio.details')"
      setting-id="staypointMinAccuracyRatio"
    >
      <template #control>
        <SliderControl
          v-if="modelValue.staypointMinAccuracyRatio !== undefined"
          :model-value="modelValue.staypointMinAccuracyRatio"
          @update:model-value="updatePref('staypointMinAccuracyRatio', $event)"
          :min="0.1"
          :max="1.0"
          :step="0.05"
          :labels="minAccuracyRatioLabels"
          :input-min="0.1"
          :input-max="1.0"
          :decimal-places="2"
        />
      </template>
    </SettingCard>
    </div>
  </PreferencesTabLayout>
</template>

<script setup>
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import './shared-styles.css'
import PreferencesTabLayout from './PreferencesTabLayout.vue'
import SettingCard from '@/components/ui/forms/SettingCard.vue'
import SliderControl from '@/components/ui/forms/SliderControl.vue'
import ToggleSwitch from 'primevue/toggleswitch'

const { t } = useI18n()

const props = defineProps({
  modelValue: {
    type: Object,
    required: true
  }
})

const emit = defineEmits(['update:modelValue'])

const updatePref = (key, value) => {
  emit('update:modelValue', {
    ...props.modelValue,
    [key]: value
  })
}

const NS = 'timeline.preferences.stayPointDetection'

const threeLabels = (field) => computed(() => [
  t(`${NS}.${field}.labelLow`),
  t(`${NS}.${field}.labelMid`),
  t(`${NS}.${field}.labelHigh`)
])

const lowerHigherDetails = (field) => computed(() => ({
  [t(`${NS}.${field}.detailsLowerLabel`)]: t(`${NS}.${field}.detailsLowerValue`),
  [t(`${NS}.${field}.detailsHigherLabel`)]: t(`${NS}.${field}.detailsHigherValue`)
}))

const radiusLabels = threeLabels('radius')
const minDurationLabels = threeLabels('minDuration')
const velocityThresholdLabels = threeLabels('velocityThreshold')
const accuracyThresholdLabels = threeLabels('accuracyThreshold')
const minAccuracyRatioLabels = threeLabels('minAccuracyRatio')

const radiusDetails = lowerHigherDetails('radius')
const minDurationDetails = lowerHigherDetails('minDuration')
const velocityThresholdDetails = lowerHigherDetails('velocityThreshold')
const accuracyThresholdDetails = lowerHigherDetails('accuracyThreshold')
</script>
