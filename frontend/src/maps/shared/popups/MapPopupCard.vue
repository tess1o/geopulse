<template>
  <div :class="cardClasses">
    <div v-if="hasHeader" class="gp-map-popup-header">
      <img
        v-if="avatarUrl && !avatarFailed"
        class="gp-map-popup-avatar"
        :src="avatarUrl"
        :alt="avatarAlt || title || 'Map popup avatar'"
        @error="avatarFailed = true"
      >
      <i v-else-if="iconClass" :class="['gp-map-popup-icon', iconClass]"></i>
      <div class="gp-map-popup-heading">
        <div v-if="title" class="gp-map-popup-title">{{ title }}</div>
        <div v-if="subtitle" class="gp-map-popup-subtitle">{{ subtitle }}</div>
      </div>
    </div>

    <slot name="beforeRows" />

    <div v-if="normalizedRows.length" class="gp-map-popup-grid">
      <template v-for="row in normalizedRows" :key="row.key">
        <div class="gp-map-popup-label">{{ row.label }}</div>
        <div class="gp-map-popup-value">
          <template v-if="row.lines.length > 1">
            <span
              v-for="(line, lineIndex) in row.lines"
              :key="`${row.key}-${lineIndex}`"
              class="gp-map-popup-value-line"
            >
              {{ line }}
            </span>
          </template>
          <template v-else>{{ row.lines[0] }}</template>
        </div>
      </template>
    </div>

    <slot />

    <slot name="footer" />
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'

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
  rows: {
    type: Array,
    default: () => []
  },
  variant: {
    type: String,
    default: 'compact'
  }
})

const avatarFailed = ref(false)

watch(
  () => props.avatarUrl,
  () => {
    avatarFailed.value = false
  }
)

const hasHeader = computed(() => Boolean(props.title || props.subtitle || props.iconClass || props.avatarUrl))

const cardClasses = computed(() => [
  'gp-map-popup-card',
  props.variant === 'wide' ? 'gp-map-popup-card--wide' : 'gp-map-popup-card--compact'
])

const normalizeValueLines = (value) => {
  if (Array.isArray(value)) {
    return value.map((line) => String(line ?? ''))
  }

  return [String(value ?? '')]
}

const normalizedRows = computed(() => (
  props.rows
    .filter((row) => row && row.label)
    .map((row, index) => ({
      key: row.key || `${row.label}-${index}`,
      label: row.label,
      lines: normalizeValueLines(row.value)
    }))
))
</script>

<style src="../styles/mapPopup.css"></style>
