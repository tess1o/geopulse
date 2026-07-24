<template>
  <MapPopupCard
    :title="title"
    :subtitle="subtitle"
    :icon-class="iconClass"
    :avatar-url="avatarUrl"
    :avatar-alt="avatarAlt"
    :rows="rows"
    :variant="variant"
  >
    <template #beforeRows>
      <p v-if="description" class="gp-map-popup-description">{{ description }}</p>
    </template>

    <div
      v-for="section in normalizedSections"
      :key="section.key"
      class="gp-map-popup-section"
    >
      <div v-if="section.title" class="gp-map-popup-section-title">{{ section.title }}</div>
      <div class="gp-map-popup-grid">
        <template v-for="row in section.rows" :key="row.key">
          <div class="gp-map-popup-label">{{ row.label }}</div>
          <div class="gp-map-popup-value">{{ row.value }}</div>
        </template>
      </div>
    </div>

    <template #footer>
      <div v-if="actions.length" class="gp-map-popup-actions">
        <component
          :is="action.href ? 'a' : 'button'"
          v-for="action in actions"
          :key="action.key || action.label"
          :type="action.href ? undefined : 'button'"
          :href="action.href || undefined"
          :target="action.target || undefined"
          :rel="action.rel || undefined"
          :class="['gp-map-popup-action', action.variant ? `gp-map-popup-action--${action.variant}` : '']"
          @click.stop="handleAction(action, $event)"
        >
          <i v-if="action.iconClass" :class="action.iconClass"></i>
          <span>{{ action.label }}</span>
        </component>
      </div>
    </template>
  </MapPopupCard>
</template>

<script setup>
import { computed } from 'vue'
import MapPopupCard from './MapPopupCard.vue'

const props = defineProps({
  title: {
    type: String,
    default: ''
  },
  subtitle: {
    type: String,
    default: ''
  },
  iconClass: {
    type: String,
    default: ''
  },
  avatarUrl: {
    type: String,
    default: ''
  },
  avatarAlt: {
    type: String,
    default: ''
  },
  description: {
    type: String,
    default: ''
  },
  rows: {
    type: Array,
    default: () => []
  },
  sections: {
    type: Array,
    default: () => []
  },
  actions: {
    type: Array,
    default: () => []
  },
  variant: {
    type: String,
    default: 'compact'
  }
})

const normalizedSections = computed(() => (
  props.sections
    .filter((section) => Array.isArray(section?.rows) && section.rows.length > 0)
    .map((section, index) => ({
      key: section.key || section.title || `section-${index}`,
      title: section.title || '',
      rows: section.rows
        .filter((row) => row && row.label)
        .map((row, rowIndex) => ({
          key: row.key || `${row.label}-${rowIndex}`,
          label: row.label,
          value: String(row.value ?? '')
        }))
    }))
))

const handleAction = (action, event) => {
  if (typeof action?.onClick === 'function') {
    action.onClick(event)
  }
}
</script>
