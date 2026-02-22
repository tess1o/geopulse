<template>
  <Card class="quick-guide-card quick-setup-guide">
    <template #title>
      <div class="flex items-center gap-2">
        <i class="pi pi-info-circle text-blue-500"></i>
        Quick Setup Guide
      </div>
    </template>
    <template #content>
      <div class="grid grid-cols-1 md:grid-cols-3 gap-6">
        <div v-for="option in quickSetupOptions" :key="option.value" class="source-option">
          <div class="source-header">
            <i :class="[getLocationSourceIcon(option.value), 'text-2xl', option.accentClass]"></i>
            <div>
              <h3 class="source-name">{{ option.label }}</h3>
              <p class="source-description">{{ option.description }}</p>
            </div>
          </div>
          <Button
            :label="`Setup ${option.label}`"
            outlined
            size="small"
            @click="emit('quick-setup', option.value)"
          />
        </div>
      </div>
    </template>
  </Card>
</template>

<script setup>
import { computed } from 'vue'

import {
  LOCATION_SOURCE_OPTIONS,
  getLocationSourceIcon
} from '@/components/location-sources/locationSourceMeta'

const emit = defineEmits(['quick-setup'])

const QUICK_SETUP_ACCENT_BY_TYPE = Object.freeze({
  OWNTRACKS: 'text-blue-500',
  GPSLOGGER: 'text-cyan-500',
  OVERLAND: 'text-green-500',
  DAWARICH: 'text-purple-500',
  HOME_ASSISTANT: 'text-orange-500'
})

const quickSetupOptions = computed(() => (
  LOCATION_SOURCE_OPTIONS.map((option) => ({
    ...option,
    accentClass: QUICK_SETUP_ACCENT_BY_TYPE[option.value] || 'text-blue-500'
  }))
))
</script>

<style scoped>
.quick-guide-card {
  margin-bottom: 2rem;
}

.source-option {
  display: flex;
  flex-direction: column;
  gap: 1rem;
  padding: 1rem;
  border: 1px solid var(--gp-border-light);
  border-radius: var(--gp-radius-medium);
  background: var(--gp-surface-light);
}

.source-header {
  display: flex;
  align-items: flex-start;
  gap: 1rem;
}

.source-name {
  font-size: 1.1rem;
  font-weight: 600;
  color: var(--gp-text-primary);
  margin: 0 0 0.25rem 0;
}

.source-description {
  font-size: 0.9rem;
  color: var(--gp-text-secondary);
  margin: 0;
  line-height: 1.4;
}

.p-dark .source-option {
  background: var(--gp-surface-darker);
  border-color: var(--gp-border-dark);
}

.p-dark .source-name {
  color: var(--gp-text-primary);
}

.p-dark .source-description {
  color: var(--gp-text-secondary);
}

.p-dark .quick-guide-card {
  background: var(--gp-surface-dark) !important;
  border-color: var(--gp-border-dark) !important;
}

.p-dark .quick-guide-card :deep(.p-card) {
  background: var(--gp-surface-dark) !important;
  border-color: var(--gp-border-dark) !important;
}

.p-dark .quick-guide-card :deep(.p-card-header),
.p-dark .quick-guide-card :deep(.p-card-title-section) {
  background: var(--gp-surface-dark) !important;
  border-bottom-color: var(--gp-border-dark) !important;
}

.p-dark .quick-guide-card :deep(.p-card-title) {
  color: var(--gp-text-primary) !important;
}

.p-dark .quick-guide-card :deep(.p-card-content),
.p-dark .quick-guide-card :deep(.p-card-body) {
  background: var(--gp-surface-dark) !important;
  color: var(--gp-text-primary) !important;
}
</style>
