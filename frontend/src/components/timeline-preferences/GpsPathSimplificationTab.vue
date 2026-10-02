<template>
  <PreferencesTabLayout
    :title="t('timeline.preferences.gpsPathSimplification.title')"
    :description="t('timeline.preferences.gpsPathSimplification.description')"
  >
    <div class="settings-panel">
    <!-- Enable Path Simplification -->
    <SettingCard
      :title="t('timeline.preferences.gpsPathSimplification.enabled.title')"
      :description="t('timeline.preferences.gpsPathSimplification.enabled.description')"
      :details="t('timeline.preferences.gpsPathSimplification.enabled.details')"
    >
      <template #control>
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
      :title="t('timeline.preferences.gpsPathSimplification.tolerance.title')"
      :description="t('timeline.preferences.gpsPathSimplification.tolerance.description')"
      :details="toleranceDetails"
    >
      <template #control>
        <SliderControl
          v-if="modelValue.pathSimplificationTolerance !== undefined"
          :model-value="modelValue.pathSimplificationTolerance"
          @update:model-value="updatePref('pathSimplificationTolerance', $event)"
          :min="1"
          :max="50"
          :step="1"
          :labels="toleranceLabels"
          :suffix="t('timeline.preferences.gpsPathSimplification.tolerance.suffix')"
          :input-min="1"
          :input-max="100"
          :decimal-places="0"
        />
      </template>
    </SettingCard>

    <!-- Maximum Points -->
    <SettingCard
      v-if="modelValue.pathSimplificationEnabled"
      :title="t('timeline.preferences.gpsPathSimplification.maxPoints.title')"
      :description="t('timeline.preferences.gpsPathSimplification.maxPoints.description')"
      :details="t('timeline.preferences.gpsPathSimplification.maxPoints.details')"
    >
      <template #control>
        <SliderControl
          v-if="modelValue.pathMaxPoints !== undefined"
          :model-value="modelValue.pathMaxPoints"
          @update:model-value="updatePref('pathMaxPoints', $event)"
          :min="0"
          :max="500"
          :step="10"
          :labels="maxPointsLabels"
          :suffix="modelValue.pathMaxPoints === 0 ? '' : t('timeline.preferences.gpsPathSimplification.maxPoints.suffix')"
          :input-min="0"
          :input-max="1000"
          :decimal-places="0"
        />
      </template>
    </SettingCard>

    <!-- Adaptive Simplification -->
    <SettingCard
      v-if="modelValue.pathSimplificationEnabled"
      :title="t('timeline.preferences.gpsPathSimplification.adaptive.title')"
      :description="t('timeline.preferences.gpsPathSimplification.adaptive.description')"
      :details="t('timeline.preferences.gpsPathSimplification.adaptive.details')"
    >
      <template #control>
        <ToggleSwitch
          :model-value="modelValue.pathAdaptiveSimplification"
          @update:model-value="updatePref('pathAdaptiveSimplification', $event)"
          class="toggle-control"
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

const toleranceDetails = computed(() => ({
  [t('timeline.preferences.gpsPathSimplification.tolerance.detailsLowerLabel')]: t('timeline.preferences.gpsPathSimplification.tolerance.detailsLowerValue'),
  [t('timeline.preferences.gpsPathSimplification.tolerance.detailsHigherLabel')]: t('timeline.preferences.gpsPathSimplification.tolerance.detailsHigherValue')
}))

const toleranceLabels = computed(() => [
  t('timeline.preferences.gpsPathSimplification.tolerance.labelLow'),
  t('timeline.preferences.gpsPathSimplification.tolerance.labelMid'),
  t('timeline.preferences.gpsPathSimplification.tolerance.labelHigh')
])

const maxPointsLabels = computed(() => [
  t('timeline.preferences.gpsPathSimplification.maxPoints.labelLow'),
  t('timeline.preferences.gpsPathSimplification.maxPoints.labelMid'),
  t('timeline.preferences.gpsPathSimplification.maxPoints.labelHigh')
])
</script>
