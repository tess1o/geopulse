<template>
  <div class="friend-filter-actions">
    <Button :label="t('friends.filters.all')" outlined size="small" @click="$emit('select-all')" :disabled="disabled" />
    <Button :label="t('friends.filters.none')" outlined size="small" @click="$emit('select-none')" :disabled="disabled" />
    <Button :label="t('friends.filters.online')" outlined size="small" @click="$emit('select-online')" :disabled="disabled" />
  </div>
</template>

<script setup>
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

defineProps({
  disabled: {
    type: Boolean,
    default: false
  }
})

defineEmits(['select-all', 'select-none', 'select-online'])
</script>

<style scoped>
.friend-filter-actions {
  display: flex;
  gap: 0.5rem;
  flex-wrap: wrap;
}
</style>
