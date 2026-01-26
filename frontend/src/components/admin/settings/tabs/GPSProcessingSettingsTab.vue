<template>
  <EmptySettingsTab
    title="GPS Processing Settings"
    description="Configure default GPS data processing behavior"
    icon="pi-map-marker"
    iconColor="var(--blue-500)"
    :plannedFeatures="plannedFeatures"
  />
</template>

<script setup>
import EmptySettingsTab from '../EmptySettingsTab.vue'

const plannedFeatures = [
  {
    name: 'Stay Detection Algorithm',
    description: 'Default algorithm for detecting stays from GPS points'
  },
  {
    name: 'Accuracy Filtering',
    description: 'Minimum GPS accuracy threshold for processing'
  },
  {
    name: 'Batch Size',
    description: 'Number of GPS points to process in each batch'
  },
  {
    name: 'Distance Thresholds',
    description: 'Configure stay/trip distance parameters'
  },
  {
    name: 'Time Windows',
    description: 'Minimum/maximum time for stay detection'
  }
]
</script>
