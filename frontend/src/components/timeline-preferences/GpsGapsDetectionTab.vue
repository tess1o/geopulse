<template>
  <PreferencesTabLayout
    title="GPS Gaps Detection Settings"
    description="Configure how GPS data gaps are detected and recorded in your timeline"
  >
    <!-- Data Gap Threshold -->
    <SettingCard
      title="Data Gap Threshold"
      description="Maximum time gap in seconds allowed between GPS points before considering it a GPS data gap"
      details="When the time difference between two consecutive GPS points exceeds this threshold, a GPS Data Gap entity will be created instead of extending the current stay or trip. This prevents artificial extension of activities during periods of missing GPS data."
    >
      <template #control>
        <div class="control-value">{{ Math.floor(modelValue.dataGapThresholdSeconds / 60) }} minutes ({{ modelValue.dataGapThresholdSeconds }}s)</div>
        <SliderControl
          v-if="modelValue.dataGapThresholdSeconds !== undefined"
          :model-value="modelValue.dataGapThresholdSeconds"
          @update:model-value="updatePref('dataGapThresholdSeconds', $event)"
          :min="300"
          :max="86400"
          :step="300"
          :labels="['5 min (Sensitive)', '3 hours (Balanced)', '6 hours (Lenient)']"
          :suffix="' seconds'"
          :input-min="60"
          :input-max="604800"
          :decimal-places="0"
        />
      </template>
    </SettingCard>

    <!-- Min Duration for Gap Recording -->
    <SettingCard
      title="Minimum Gap Duration"
      description="Minimum duration in seconds for a gap to be recorded as a GPS Data Gap"
      details="Gaps shorter than this threshold will be ignored to reduce noise. This prevents very short connectivity issues from creating unnecessary gap records."
    >
      <template #control>
        <div class="control-value">{{ Math.floor(modelValue.dataGapMinDurationSeconds / 60) }} minutes ({{ modelValue.dataGapMinDurationSeconds }}s)</div>
        <SliderControl
          v-if="modelValue.dataGapMinDurationSeconds !== undefined"
          :model-value="modelValue.dataGapMinDurationSeconds"
          @update:model-value="updatePref('dataGapMinDurationSeconds', $event)"
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

    <!-- Gap Stay Inference -->
    <SettingCard
      title="Gap Stay Inference"
      description="Infer stays when GPS data gaps occur but locations before and after are the same"
      :details="{
        'When enabled': 'If you were at a location and GPS data stops, then resumes at the same location, the system infers you stayed there',
        'Use case': 'Overnight gaps at home will show as a stay instead of a data gap'
      }"
    >
      <template #control>
        <div class="control-value">{{ modelValue.gapStayInferenceEnabled ? 'Enabled' : 'Disabled' }}</div>
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
      title="Maximum Gap Duration for Inference"
      description="Maximum duration of GPS data gap to infer a stay"
      :details="{
        'Lower values': 'Only infer stays for shorter gaps (e.g., brief phone downtime)',
        'Higher values': 'Infer stays for longer gaps (e.g., overnight, full day)'
      }"
    >
      <template #control>
        <div class="control-value">{{ modelValue.gapStayInferenceMaxGapHours }} hours</div>
        <SliderControl
          v-if="modelValue.gapStayInferenceMaxGapHours !== undefined"
          :model-value="modelValue.gapStayInferenceMaxGapHours"
          @update:model-value="updatePref('gapStayInferenceMaxGapHours', $event)"
          :min="1"
          :max="72"
          :step="1"
          :labels="['1 hour (Strict)', '24 hours (Default)', '72 hours (Lenient)']"
          suffix=" hours"
          :input-min="1"
          :input-max="168"
          :decimal-places="0"
        />
      </template>
    </SettingCard>

    <!-- Gap Trip Inference -->
    <SettingCard
      title="Gap Trip Inference"
      description="Infer trips when GPS data gaps occur with long-distance movement"
      :details="{
        'When enabled': 'If GPS data stops, then resumes at a distant location, the system infers a trip occurred',
        'Use case': 'Overnight flights or long drives where phone was off will show as inferred trips instead of data gaps',
        'Trip classification': 'Trip mode is automatically determined by distance, duration, and speed (e.g., flight, car, train)'
      }"
    >
      <template #control>
        <div class="control-value">{{ modelValue.gapTripInferenceEnabled ? 'Enabled' : 'Disabled' }}</div>
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
      title="Minimum Distance for Trip Inference"
      description="Minimum distance between GPS points to infer a trip during a gap"
      :details="{
        'Lower values': 'Infer trips for shorter movements (e.g., 10km city trips)',
        'Higher values': 'Only infer trips for longer movements (e.g., 100km+ intercity travel)'
      }"
    >
      <template #control>
        <div class="control-value">{{ (modelValue.gapTripInferenceMinDistanceMeters / 1000).toFixed(0) }} km</div>
        <SliderControl
          v-if="modelValue.gapTripInferenceMinDistanceMeters !== undefined"
          :model-value="modelValue.gapTripInferenceMinDistanceMeters"
          @update:model-value="updatePref('gapTripInferenceMinDistanceMeters', $event)"
          :min="10000"
          :max="500000"
          :step="10000"
          :labels="['10 km (Short trips)', '100 km (Default)', '500 km (Long distance)']"
          suffix=" m"
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
      title="Minimum Gap Duration for Trip Inference"
      description="Minimum gap duration to consider for trip inference"
      :details="{
        'Lower values': 'Infer trips for brief gaps (e.g., 30 minutes)',
        'Higher values': 'Only infer trips for longer gaps (e.g., several hours)'
      }"
    >
      <template #control>
        <div class="control-value">{{ modelValue.gapTripInferenceMinGapHours }} hour{{ modelValue.gapTripInferenceMinGapHours !== 1 ? 's' : '' }}</div>
        <SliderControl
          v-if="modelValue.gapTripInferenceMinGapHours !== undefined"
          :model-value="modelValue.gapTripInferenceMinGapHours"
          @update:model-value="updatePref('gapTripInferenceMinGapHours', $event)"
          :min="0"
          :max="12"
          :step="1"
          :labels="['0 hours (Any gap)', '1 hour (Default)', '12 hours (Long gaps)']"
          suffix=" hours"
          :input-min="0"
          :input-max="24"
          :decimal-places="0"
        />
      </template>
    </SettingCard>

    <!-- Gap Trip Inference - Max Gap Hours -->
    <SettingCard
      v-if="modelValue.gapTripInferenceEnabled"
      title="Maximum Gap Duration for Trip Inference"
      description="Maximum gap duration to infer a trip (longer gaps become data gaps)"
      :details="{
        'Lower values': 'Only infer trips for shorter gaps (e.g., 12 hours)',
        'Higher values': 'Infer trips for longer gaps (e.g., multi-day trips)'
      }"
    >
      <template #control>
        <div class="control-value">{{ modelValue.gapTripInferenceMaxGapHours }} hour{{ modelValue.gapTripInferenceMaxGapHours !== 1 ? 's' : '' }}</div>
        <SliderControl
          v-if="modelValue.gapTripInferenceMaxGapHours !== undefined"
          :model-value="modelValue.gapTripInferenceMaxGapHours"
          @update:model-value="updatePref('gapTripInferenceMaxGapHours', $event)"
          :min="1"
          :max="168"
          :step="1"
          :labels="['1 hour (Strict)', '24 hours (Default)', '168 hours (One week)']"
          suffix=" hours"
          :input-min="1"
          :input-max="336"
          :decimal-places="0"
        />
      </template>
    </SettingCard>
  </PreferencesTabLayout>
</template>

<script setup>
import './shared-styles.css'
import PreferencesTabLayout from './PreferencesTabLayout.vue'
import SettingCard from '@/components/ui/forms/SettingCard.vue'
import SliderControl from '@/components/ui/forms/SliderControl.vue'
import ToggleSwitch from 'primevue/toggleswitch'

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
</script>
