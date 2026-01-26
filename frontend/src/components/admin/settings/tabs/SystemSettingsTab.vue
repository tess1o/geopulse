<template>
  <EmptySettingsTab
    title="System Performance & Monitoring"
    description="Configure system-wide performance and monitoring settings"
    icon="pi-server"
    iconColor="var(--orange-500)"
    :plannedFeatures="plannedFeatures"
  />
</template>

<script setup>
import EmptySettingsTab from '../EmptySettingsTab.vue'

const plannedFeatures = [
  {
    name: 'Timeline Processing',
    description: 'Background job concurrency and queue settings'
  },
  {
    name: 'Database Maintenance',
    description: 'Auto-vacuum and cleanup schedules'
  },
  {
    name: 'Prometheus Metrics',
    description: 'Enable/disable metrics collection and refresh intervals'
  },
  {
    name: 'Logging Levels',
    description: 'Configure application and SQL logging verbosity'
  }
]
</script>
