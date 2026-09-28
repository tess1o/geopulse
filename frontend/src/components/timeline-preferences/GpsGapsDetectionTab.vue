<template>
  <PreferencesTabLayout
    :title="t('timeline.preferences.gpsGaps.title')"
    :description="t('timeline.preferences.gpsGaps.description')"
  >
    <div class="settings-panel">
    <!-- Data Gap Threshold -->
    <SettingCard
      :title="t('timeline.preferences.gpsGaps.threshold.title')"
      :description="t('timeline.preferences.gpsGaps.threshold.description')"
      :details="t('timeline.preferences.gpsGaps.threshold.details')"
      setting-id="dataGapThresholdSeconds"
    >
      <template #control>
        <SliderControl
          v-if="modelValue.dataGapThresholdSeconds !== undefined"
          :model-value="modelValue.dataGapThresholdSeconds"
          @update:model-value="updatePref('dataGapThresholdSeconds', $event)"
          :min="300"
          :max="86400"
          :step="300"
          :labels="thresholdLabels"
          :suffix="t('timeline.preferences.gpsGaps.threshold.suffix')"
          :input-min="60"
          :input-max="604800"
          :decimal-places="0"
        />
      </template>
    </SettingCard>

    <!-- Min Duration for Gap Recording -->
    <SettingCard
      :title="t('timeline.preferences.gpsGaps.minDuration.title')"
      :description="t('timeline.preferences.gpsGaps.minDuration.description')"
      :details="t('timeline.preferences.gpsGaps.minDuration.details')"
      setting-id="dataGapMinDurationSeconds"
    >
      <template #control>
        <SliderControl
          v-if="modelValue.dataGapMinDurationSeconds !== undefined"
          :model-value="modelValue.dataGapMinDurationSeconds"
          @update:model-value="updatePref('dataGapMinDurationSeconds', $event)"
          :min="300"
          :max="7200"
          :step="300"
          :labels="minDurationLabels"
          :suffix="t('timeline.preferences.gpsGaps.minDuration.suffix')"
          :input-min="60"
          :input-max="14400"
          :decimal-places="0"
        />
      </template>
    </SettingCard>

    <!-- Gap Stay Inference -->
    <SettingCard
      :title="t('timeline.preferences.gpsGaps.stayInferenceEnabled.title')"
      :description="t('timeline.preferences.gpsGaps.stayInferenceEnabled.description')"
      :details="stayInferenceEnabledDetails"
      setting-id="gapStayInferenceEnabled"
    >
      <template #control>
        <ToggleSwitch
          :model-value="modelValue.gapStayInferenceEnabled"
          @update:model-value="updatePref('gapStayInferenceEnabled', $event)"
          class="toggle-control"
        />
      </template>
    </SettingCard>

    <!-- Gap Stay Inference Max Gap Hours -->
    <SettingCard
      v-if="modelValue.gapStayInferenceEnabled"
      :title="t('timeline.preferences.gpsGaps.stayInferenceMaxGap.title')"
      :description="t('timeline.preferences.gpsGaps.stayInferenceMaxGap.description')"
      :details="stayInferenceMaxGapDetails"
      setting-id="gapStayInferenceMaxGapHours"
    >
      <template #control>
        <SliderControl
          v-if="modelValue.gapStayInferenceMaxGapHours !== undefined"
          :model-value="modelValue.gapStayInferenceMaxGapHours"
          @update:model-value="updatePref('gapStayInferenceMaxGapHours', $event)"
          :min="1"
          :max="72"
          :step="1"
          :labels="stayInferenceMaxGapLabels"
          :suffix="t('timeline.preferences.gpsGaps.stayInferenceMaxGap.suffix')"
          :input-min="1"
          :input-max="168"
          :decimal-places="0"
        />
      </template>
    </SettingCard>

    <!-- Gap Trip Inference -->
    <SettingCard
      :title="t('timeline.preferences.gpsGaps.tripInferenceEnabled.title')"
      :description="t('timeline.preferences.gpsGaps.tripInferenceEnabled.description')"
      :details="tripInferenceEnabledDetails"
      setting-id="gapTripInferenceEnabled"
    >
      <template #control>
        <ToggleSwitch
          :model-value="modelValue.gapTripInferenceEnabled"
          @update:model-value="updatePref('gapTripInferenceEnabled', $event)"
          class="toggle-control"
        />
      </template>
    </SettingCard>

    <!-- Gap Trip Inference - Min Distance -->
    <SettingCard
      v-if="modelValue.gapTripInferenceEnabled"
      :title="t('timeline.preferences.gpsGaps.tripInferenceMinDistance.title')"
      :description="t('timeline.preferences.gpsGaps.tripInferenceMinDistance.description')"
      :details="tripInferenceMinDistanceDetails"
      setting-id="gapTripInferenceMinDistanceMeters"
    >
      <template #control>
        <SliderControl
          v-if="modelValue.gapTripInferenceMinDistanceMeters !== undefined"
          :model-value="modelValue.gapTripInferenceMinDistanceMeters"
          @update:model-value="updatePref('gapTripInferenceMinDistanceMeters', $event)"
          :min="10000"
          :max="500000"
          :step="10000"
          :labels="tripInferenceMinDistanceLabels"
          :suffix="t('timeline.preferences.gpsGaps.tripInferenceMinDistance.suffix')"
          :input-min="1000"
          :input-max="1000000"
          :decimal-places="0"
          :display-transform="(val) => `${(val / 1000).toFixed(0)} km`"
        />
      </template>
    </SettingCard>

    <!-- Gap Trip Inference - Min Gap Hours -->
    <SettingCard
      v-if="modelValue.gapTripInferenceEnabled"
      :title="t('timeline.preferences.gpsGaps.tripInferenceMinGap.title')"
      :description="t('timeline.preferences.gpsGaps.tripInferenceMinGap.description')"
      :details="tripInferenceMinGapDetails"
      setting-id="gapTripInferenceMinGapHours"
    >
      <template #control>
        <SliderControl
          v-if="modelValue.gapTripInferenceMinGapHours !== undefined"
          :model-value="modelValue.gapTripInferenceMinGapHours"
          @update:model-value="updatePref('gapTripInferenceMinGapHours', $event)"
          :min="0"
          :max="12"
          :step="1"
          :labels="tripInferenceMinGapLabels"
          :suffix="t('timeline.preferences.gpsGaps.tripInferenceMinGap.suffix')"
          :input-min="0"
          :input-max="24"
          :decimal-places="0"
        />
      </template>
    </SettingCard>

    <!-- Gap Trip Inference - Max Gap Hours -->
    <SettingCard
      v-if="modelValue.gapTripInferenceEnabled"
      :title="t('timeline.preferences.gpsGaps.tripInferenceMaxGap.title')"
      :description="t('timeline.preferences.gpsGaps.tripInferenceMaxGap.description')"
      :details="tripInferenceMaxGapDetails"
      setting-id="gapTripInferenceMaxGapHours"
    >
      <template #control>
        <SliderControl
          v-if="modelValue.gapTripInferenceMaxGapHours !== undefined"
          :model-value="modelValue.gapTripInferenceMaxGapHours"
          @update:model-value="updatePref('gapTripInferenceMaxGapHours', $event)"
          :min="1"
          :max="168"
          :step="1"
          :labels="tripInferenceMaxGapLabels"
          :suffix="t('timeline.preferences.gpsGaps.tripInferenceMaxGap.suffix')"
          :input-min="1"
          :input-max="336"
          :decimal-places="0"
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

const NS = 'timeline.preferences.gpsGaps'

const threeLabels = (field) => computed(() => [
  t(`${NS}.${field}.labelLow`),
  t(`${NS}.${field}.labelMid`),
  t(`${NS}.${field}.labelHigh`)
])

const lowerHigherDetails = (field) => computed(() => ({
  [t(`${NS}.${field}.detailsLowerLabel`)]: t(`${NS}.${field}.detailsLowerValue`),
  [t(`${NS}.${field}.detailsHigherLabel`)]: t(`${NS}.${field}.detailsHigherValue`)
}))

const thresholdLabels = threeLabels('threshold')
const minDurationLabels = threeLabels('minDuration')
const stayInferenceMaxGapLabels = threeLabels('stayInferenceMaxGap')
const tripInferenceMinDistanceLabels = threeLabels('tripInferenceMinDistance')
const tripInferenceMinGapLabels = threeLabels('tripInferenceMinGap')
const tripInferenceMaxGapLabels = threeLabels('tripInferenceMaxGap')

const stayInferenceMaxGapDetails = lowerHigherDetails('stayInferenceMaxGap')
const tripInferenceMinDistanceDetails = lowerHigherDetails('tripInferenceMinDistance')
const tripInferenceMinGapDetails = lowerHigherDetails('tripInferenceMinGap')
const tripInferenceMaxGapDetails = lowerHigherDetails('tripInferenceMaxGap')

const stayInferenceEnabledDetails = computed(() => ({
  [t(`${NS}.stayInferenceEnabled.detailsWhenLabel`)]: t(`${NS}.stayInferenceEnabled.detailsWhenValue`),
  [t(`${NS}.stayInferenceEnabled.detailsUseCaseLabel`)]: t(`${NS}.stayInferenceEnabled.detailsUseCaseValue`)
}))

const tripInferenceEnabledDetails = computed(() => ({
  [t(`${NS}.tripInferenceEnabled.detailsWhenLabel`)]: t(`${NS}.tripInferenceEnabled.detailsWhenValue`),
  [t(`${NS}.tripInferenceEnabled.detailsUseCaseLabel`)]: t(`${NS}.tripInferenceEnabled.detailsUseCaseValue`),
  [t(`${NS}.tripInferenceEnabled.detailsClassificationLabel`)]: t(`${NS}.tripInferenceEnabled.detailsClassificationValue`)
}))
</script>
