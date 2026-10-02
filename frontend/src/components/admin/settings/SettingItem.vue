<template>
  <div class="setting-item" :data-setting-id="setting.key">
    <div class="setting-info">
      <label>{{ setting.label }}</label>
      <small class="text-muted">{{ setting.description }}</small>
    </div>
    <div class="setting-control">
      <slot name="control" :setting="setting" />
      <div class="setting-status">
        <Tag v-if="setting.readOnly" severity="info" :value="t('adminSettings.shell.readOnly')" />
        <Tag v-else-if="setting.isDefault" severity="secondary" :value="t('adminSettings.shell.default')" />
        <Button
          v-else
          :label="resetLabel"
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
import { useI18n } from 'vue-i18n'
import { t as translate } from '@/locales'

const { t } = useI18n()

defineProps({
  setting: {
    type: Object,
    required: true
  },
  resetLabel: {
    type: String,
    default: () => translate('adminSettings.shell.reset')
  }
})

defineEmits(['reset'])
</script>

<style scoped>
@import './admin-settings-common.css';
</style>
