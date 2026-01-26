<template>
  <div class="settings-section empty-state">
    <div class="empty-state-icon">
      <i :class="iconClass" :style="iconStyle"></i>
    </div>
    <h3>{{ title }}</h3>
    <p class="text-muted">{{ description }}</p>
    <div v-if="plannedFeatures && plannedFeatures.length > 0" class="planned-features">
      <h4>Planned Features:</h4>
      <ul>
        <li v-for="(feature, index) in plannedFeatures" :key="index">
          <strong>{{ feature.name }}:</strong> {{ feature.description }}
        </li>
      </ul>
    </div>
    <div class="coming-soon-badge">
      <Tag severity="info" value="Coming Soon" icon="pi pi-clock" />
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import Tag from 'primevue/tag'

const props = defineProps({
  title: {
    type: String,
    required: true
  },
  description: {
    type: String,
    required: true
  },
  icon: {
    type: String,
    default: 'pi pi-cog'
  },
  iconColor: {
    type: String,
    default: 'var(--blue-500)'
  },
  plannedFeatures: {
    type: Array,
    default: () => []
  }
})

const iconClass = computed(() => `pi ${props.icon}`)
const iconStyle = computed(() => `font-size: 3rem; color: ${props.iconColor};`)
</script>

<style scoped>
@import './admin-settings-common.css';
</style>
