<template>
  <PreferencesTabLayout
    title="Stay Point Detection Settings"
    description="Configure how GPS data is analyzed to identify places where you've stayed"
  >
    <!-- Stay Detection Radius -->
    <SettingCard
      title="Stay Detection Radius"
      description="Distance threshold for grouping GPS points into stay locations. Also defines minimum distance between stays to create a trip"
      :details="{
        'Lower values': 'More sensitive stay detection, separate nearby locations',
        'Higher values': 'Less sensitive, merge nearby locations into single stays'
      }"
    >
      <template #control>
        <div class="control-value">{{ modelValue.staypointRadiusMeters }}m</div>
        <SliderControl
          v-if="modelValue.staypointRadiusMeters !== undefined"
          :model-value="modelValue.staypointRadiusMeters"
          @update:model-value="updatePref('staypointRadiusMeters', $event)"
          :min="10"
          :max="500"
          :step="10"
          :labels="['10m (Sensitive)', '50m (Balanced)', '500m (Conservative)']"
          suffix=" m"
          :input-min="1"
          :input-max="2000"
          :decimal-places="0"
        />
      </template>
    </SettingCard>

    <!-- Min Trip Duration -->
    <SettingCard
      title="Minimum Stay Duration"
      description="The minimum duration (in minutes) for a stay point to be confirmed"
      :details="{
        'Lower values': 'Too short stays can be false positives',
        'Higher values': 'Longer stays are more likely to be real'
      }"
    >
      <template #control>
        <div class="control-value">{{ modelValue.staypointMinDurationMinutes }} minutes</div>
        <SliderControl
          v-if="modelValue.staypointMinDurationMinutes !== undefined"
          :model-value="modelValue.staypointMinDurationMinutes"
          @update:model-value="updatePref('staypointMinDurationMinutes', $event)"
          :min="1"
          :max="60"
          :step="1"
          :labels="['1 min (Sensitive)', '7 min (Balanced)', '60 min (Conservative)']"
          suffix=" min"
          :input-min="1"
          :input-max="300"
          :decimal-places="0"
        />
      </template>
    </SettingCard>

    <!-- Enhanced Filtering -->
    <SettingCard
      title="Enhanced Filtering"
      description="Use velocity and accuracy data for better stay point detection"
      details="Filters out poor quality GPS points and improves timeline accuracy"
    >
      <template #control>
        <div class="control-value">{{ modelValue.useVelocityAccuracy ? 'Enabled' : 'Disabled' }}</div>
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
      title="Velocity Threshold"
      description="Maximum velocity to consider a point as stationary"
      :details="{
        'Lower values': 'More strict filtering',
        'Higher values': 'Allow more movement within stays'
      }"
    >
      <template #control>
        <div class="control-value">{{ modelValue.staypointVelocityThreshold }} km/h</div>
        <SliderControl
          v-if="modelValue.staypointVelocityThreshold !== undefined"
          :model-value="modelValue.staypointVelocityThreshold"
          @update:model-value="updatePref('staypointVelocityThreshold', $event)"
          :min="1"
          :max="20"
          :step="0.5"
          :labels="['1 km/h (Strict)', '8 km/h (Balanced)', '20 km/h (Lenient)']"
          suffix=" km/h"
          :input-min="0.5"
          :input-max="50"
          :decimal-places="1"
        />
      </template>
    </SettingCard>

    <!-- Accuracy Threshold -->
    <SettingCard
      v-if="modelValue.useVelocityAccuracy"
      title="GPS Accuracy Threshold"
      description="Minimum GPS accuracy required to use a location point"
      :details="{
        'Lower values': 'Require more accurate GPS',
        'Higher values': 'Accept less accurate GPS points'
      }"
    >
      <template #control>
        <div class="control-value">{{ modelValue.staypointMaxAccuracyThreshold }}m</div>
        <SliderControl
          v-if="modelValue.staypointMaxAccuracyThreshold !== undefined"
          :model-value="modelValue.staypointMaxAccuracyThreshold"
          @update:model-value="updatePref('staypointMaxAccuracyThreshold', $event)"
          :min="5"
          :max="200"
          :step="5"
          :labels="['5m (High accuracy)', '60m (Balanced)', '200m (Low accuracy)']"
          suffix=" m"
          :input-min="1"
          :input-max="500"
          :decimal-places="1"
        />
      </template>
    </SettingCard>

    <!-- Min Accuracy Ratio -->
    <SettingCard
      v-if="modelValue.useVelocityAccuracy"
      title="Minimum Accuracy Ratio"
      description="Minimum ratio of accurate GPS points required in a stay point cluster"
      details="Higher values ensure more reliable stay point detection by requiring a higher percentage of accurate GPS points"
    >
      <template #control>
        <div class="control-value">{{ Math.round(modelValue.staypointMinAccuracyRatio * 100) }}%</div>
        <SliderControl
          v-if="modelValue.staypointMinAccuracyRatio !== undefined"
          :model-value="modelValue.staypointMinAccuracyRatio"
          @update:model-value="updatePref('staypointMinAccuracyRatio', $event)"
          :min="0.1"
          :max="1.0"
          :step="0.05"
          :labels="['10% (Lenient)', '50% (Balanced)', '100% (Strict)']"
          :input-min="0.1"
          :input-max="1.0"
          :decimal-places="2"
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
