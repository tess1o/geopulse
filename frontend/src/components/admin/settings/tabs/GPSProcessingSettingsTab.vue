<template>
  <EmptySettingsTab
    :title="t('adminSettings.gpsProcessingTab.title')"
    :description="t('adminSettings.gpsProcessingTab.description')"
    icon="pi-map-marker"
    iconColor="var(--p-blue-500)"
    :plannedFeatures="plannedFeatures"
  />
</template>

<script setup>
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import EmptySettingsTab from '../EmptySettingsTab.vue'

const { t } = useI18n()

const plannedFeatures = computed(() => [
  {
    name: t('adminSettings.gpsProcessingTab.features.stayDetection.name'),
    description: t('adminSettings.gpsProcessingTab.features.stayDetection.description')
  },
  {
    name: t('adminSettings.gpsProcessingTab.features.accuracyFiltering.name'),
    description: t('adminSettings.gpsProcessingTab.features.accuracyFiltering.description')
  },
  {
    name: t('adminSettings.gpsProcessingTab.features.batchSize.name'),
    description: t('adminSettings.gpsProcessingTab.features.batchSize.description')
  },
  {
    name: t('adminSettings.gpsProcessingTab.features.distanceThresholds.name'),
    description: t('adminSettings.gpsProcessingTab.features.distanceThresholds.description')
  },
  {
    name: t('adminSettings.gpsProcessingTab.features.timeWindows.name'),
    description: t('adminSettings.gpsProcessingTab.features.timeWindows.description')
  }
])
</script>
