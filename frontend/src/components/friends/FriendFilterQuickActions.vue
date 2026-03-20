<template>
  <div class="friend-filter-actions">
    <Button label="All" outlined size="small" @click="$emit('select-all')" :disabled="disabled" />
    <Button label="None" outlined size="small" @click="$emit('select-none')" :disabled="disabled" />
    <Button label="Online" outlined size="small" @click="$emit('select-online')" :disabled="disabled" />
  </div>
</template>

<script setup>
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
