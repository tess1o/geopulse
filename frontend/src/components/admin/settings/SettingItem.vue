<template>
  <div class="setting-item">
    <div class="setting-info">
      <label>{{ setting.label }}</label>
      <small class="text-muted">{{ setting.description }}</small>
    </div>
    <div class="setting-control">
      <slot name="control" :setting="setting" />
      <div class="setting-status">
        <Tag v-if="setting.readOnly" severity="info" value="Read-only" />
        <Tag v-else-if="setting.isDefault" severity="secondary" value="Default" />
        <Button
          v-else
          label="Reset"
          icon="pi pi-refresh"
          text
          size="small"
          @click="$emit('reset')"
        />
      </div>
    </div>
  </div>
</template>

<script setup>
import Tag from 'primevue/tag'
import Button from 'primevue/button'

defineProps({
  setting: {
    type: Object,
    required: true
  }
})

defineEmits(['reset'])
</script>

<style scoped>
@import './admin-settings-common.css';
</style>
