<template>
  <PreferencesTabLayout
    :title="t('timeline.preferences.stayMerging.title')"
    :description="t('timeline.preferences.stayMerging.description')"
  >
    <div class="settings-panel">
    <!-- Enable Merging -->
    <SettingCard
      :title="t('timeline.preferences.stayMerging.enabled.title')"
      :description="t('timeline.preferences.stayMerging.enabled.description')"
      :details="t('timeline.preferences.stayMerging.enabled.details')"
      setting-id="isMergeEnabled"
    >
      <template #control>
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
      :title="t('timeline.preferences.stayMerging.maxDistance.title')"
      :description="t('timeline.preferences.stayMerging.maxDistance.description')"
      :details="maxDistanceDetails"
      setting-id="mergeMaxDistanceMeters"
    >
      <template #control>
        <SliderControl
          v-if="modelValue.mergeMaxDistanceMeters !== undefined"
          :model-value="modelValue.mergeMaxDistanceMeters"
          @update:model-value="updatePref('mergeMaxDistanceMeters', $event)"
          :min="20"
          :max="500"
          :step="10"
          :labels="maxDistanceLabels"
          :suffix="t('timeline.preferences.stayMerging.maxDistance.suffix')"
          :input-min="10"
          :input-max="1000"
          :decimal-places="0"
        />
      </template>
    </SettingCard>

    <!-- Max Time Gap -->
    <SettingCard
      v-if="modelValue.isMergeEnabled"
      :title="t('timeline.preferences.stayMerging.maxTimeGap.title')"
      :description="t('timeline.preferences.stayMerging.maxTimeGap.description')"
      :details="maxTimeGapDetails"
      setting-id="mergeMaxTimeGapMinutes"
    >
      <template #control>
        <SliderControl
          v-if="modelValue.mergeMaxTimeGapMinutes !== undefined"
          :model-value="modelValue.mergeMaxTimeGapMinutes"
          @update:model-value="updatePref('mergeMaxTimeGapMinutes', $event)"
          :min="1"
          :max="60"
          :step="1"
          :labels="maxTimeGapLabels"
          :suffix="t('timeline.preferences.stayMerging.maxTimeGap.suffix')"
          :input-min="1"
          :input-max="300"
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

const maxDistanceDetails = computed(() => ({
  [t('timeline.preferences.stayMerging.maxDistance.detailsLowerLabel')]: t('timeline.preferences.stayMerging.maxDistance.detailsLowerValue'),
  [t('timeline.preferences.stayMerging.maxDistance.detailsHigherLabel')]: t('timeline.preferences.stayMerging.maxDistance.detailsHigherValue')
}))

const maxDistanceLabels = computed(() => [
  t('timeline.preferences.stayMerging.maxDistance.labelLow'),
  t('timeline.preferences.stayMerging.maxDistance.labelMid'),
  t('timeline.preferences.stayMerging.maxDistance.labelHigh')
])

const maxTimeGapDetails = computed(() => ({
  [t('timeline.preferences.stayMerging.maxTimeGap.detailsLowerLabel')]: t('timeline.preferences.stayMerging.maxTimeGap.detailsLowerValue'),
  [t('timeline.preferences.stayMerging.maxTimeGap.detailsHigherLabel')]: t('timeline.preferences.stayMerging.maxTimeGap.detailsHigherValue')
}))

const maxTimeGapLabels = computed(() => [
  t('timeline.preferences.stayMerging.maxTimeGap.labelLow'),
  t('timeline.preferences.stayMerging.maxTimeGap.labelMid'),
  t('timeline.preferences.stayMerging.maxTimeGap.labelHigh')
])
</script>
