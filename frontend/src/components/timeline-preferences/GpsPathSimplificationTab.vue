<template>
  <PreferencesTabLayout
    title="GPS Path Simplification Settings"
    description="Configure how GPS paths are simplified to reduce data while preserving route accuracy"
  >
    <!-- Enable Path Simplification -->
    <SettingCard
      title="Enable Path Simplification"
      description="Whether GPS path simplification is enabled for timeline trips"
      details="When enabled, trip paths will be simplified using the Douglas-Peucker algorithm to reduce the number of GPS points while preserving route accuracy"
    >
      <template #control>
        <div class="control-value">{{ modelValue.pathSimplificationEnabled ? 'Enabled' : 'Disabled' }}</div>
        <ToggleSwitch
          :model-value="modelValue.pathSimplificationEnabled"
          @update:model-value="updatePref('pathSimplificationEnabled', $event)"
          class="toggle-control"
        />
      </template>
    </SettingCard>

    <!-- Simplification Tolerance -->
    <SettingCard
      v-if="modelValue.pathSimplificationEnabled"
      title="Simplification Tolerance"
      description="Base tolerance in meters for GPS path simplification"
      :details="{
        'Lower values': 'Preserve more detail, less compression',
        'Higher values': 'More compression, less detail'
      }"
    >
      <template #control>
        <div class="control-value">{{ modelValue.pathSimplificationTolerance }}m</div>
        <SliderControl
          v-if="modelValue.pathSimplificationTolerance !== undefined"
          :model-value="modelValue.pathSimplificationTolerance"
          @update:model-value="updatePref('pathSimplificationTolerance', $event)"
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
      v-if="modelValue.pathSimplificationEnabled"
      title="Maximum Points"
      description="Maximum number of GPS points to retain in simplified paths"
      details="If a simplified path still exceeds this limit, tolerance will be automatically increased until the limit is met. Set to 0 for no limit"
    >
      <template #control>
        <div class="control-value">{{ modelValue.pathMaxPoints === 0 ? 'No limit' : modelValue.pathMaxPoints + ' points' }}</div>
        <SliderControl
          v-if="modelValue.pathMaxPoints !== undefined"
          :model-value="modelValue.pathMaxPoints"
          @update:model-value="updatePref('pathMaxPoints', $event)"
          :min="0"
          :max="500"
          :step="10"
          :labels="['0 (No limit)', '100 (Balanced)', '500 (High limit)']"
          :suffix="modelValue.pathMaxPoints === 0 ? '' : ' points'"
          :input-min="0"
          :input-max="1000"
          :decimal-places="0"
        />
      </template>
    </SettingCard>

    <!-- Adaptive Simplification -->
    <SettingCard
      v-if="modelValue.pathSimplificationEnabled"
      title="Adaptive Simplification"
      description="Enables adaptive simplification that adjusts tolerance based on trip characteristics"
      details="When enabled, longer trips use higher tolerance values for better compression while shorter trips maintain higher accuracy with lower tolerance"
    >
      <template #control>
        <div class="control-value">{{ modelValue.pathAdaptiveSimplification ? 'Enabled' : 'Disabled' }}</div>
        <ToggleSwitch
          :model-value="modelValue.pathAdaptiveSimplification"
          @update:model-value="updatePref('pathAdaptiveSimplification', $event)"
          class="toggle-control"
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
