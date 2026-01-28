<template>
  <PreferencesTabLayout
    title="Stay Point Merging Settings"
    description="Configure how nearby stay points are consolidated into single locations"
  >
    <!-- Enable Merging -->
    <SettingCard
      title="Enable Stay Point Merging"
      description="Whether to merge nearby stay points that are close in time and distance"
      details="Helps consolidate multiple GPS clusters at the same general location into single stay points"
    >
      <template #control>
        <div class="control-value">{{ modelValue.isMergeEnabled ? 'Enabled' : 'Disabled' }}</div>
        <ToggleSwitch
          :model-value="modelValue.isMergeEnabled"
          @update:model-value="updatePref('isMergeEnabled', $event)"
          class="toggle-control"
        />
      </template>
    </SettingCard>

    <!-- Max Merge Distance -->
    <SettingCard
      v-if="modelValue.isMergeEnabled"
      title="Maximum Merge Distance"
      description="Maximum distance between stay points to consider them for merging"
      :details="{
        'Lower values': 'Only merge very close points',
        'Higher values': 'Merge points further apart'
      }"
    >
      <template #control>
        <div class="control-value">{{ modelValue.mergeMaxDistanceMeters }}m</div>
        <SliderControl
          v-if="modelValue.mergeMaxDistanceMeters !== undefined"
          :model-value="modelValue.mergeMaxDistanceMeters"
          @update:model-value="updatePref('mergeMaxDistanceMeters', $event)"
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
      v-if="modelValue.isMergeEnabled"
      title="Maximum Time Gap"
      description="Maximum time gap between stay points to consider them for merging"
      :details="{
        'Lower values': 'Only merge consecutive stays',
        'Higher values': 'Merge stays separated by longer gaps'
      }"
    >
      <template #control>
        <div class="control-value">{{ modelValue.mergeMaxTimeGapMinutes }} minutes</div>
        <SliderControl
          v-if="modelValue.mergeMaxTimeGapMinutes !== undefined"
          :model-value="modelValue.mergeMaxTimeGapMinutes"
          @update:model-value="updatePref('mergeMaxTimeGapMinutes', $event)"
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
