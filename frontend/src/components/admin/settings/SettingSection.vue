<template>
  <div class="settings-section">
    <h3>{{ title }}</h3>
    <slot />
  </div>
</template>

<script setup>
defineProps({
  title: {
    type: String,
    required: true
  }
})
</script>

<style scoped>
@import './admin-settings-common.css';
</style>
