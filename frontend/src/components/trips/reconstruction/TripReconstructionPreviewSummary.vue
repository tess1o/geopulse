<template>
  <div v-if="previewResult" class="preview-box">
    <div class="preview-header">
      <Tag value="Validation Summary" severity="contrast" />
      <span class="preview-range">
        {{ formatDateTime(previewResult.startTime) }} → {{ formatDateTime(previewResult.endTime) }}
      </span>
    </div>

    <div class="preview-metrics">
      <div class="preview-metric">
        <span class="preview-metric-label">Segments</span>
        <strong>{{ previewSummary.stays }} stays · {{ previewSummary.trips }} trips</strong>
      </div>
      <div class="preview-metric">
        <span class="preview-metric-label">Generated GPS points</span>
        <strong>{{ previewResult.estimatedPoints }}</strong>
      </div>
      <div class="preview-metric">
        <span class="preview-metric-label">Covered</span>
        <strong>{{ formatDurationMinutes(previewSummary.coveredMinutes) }}</strong>
      </div>
      <div class="preview-metric">
        <span class="preview-metric-label">Uncovered intervals</span>
        <strong>{{ previewSummary.gapCount }} ({{ formatDurationMinutes(previewSummary.gapMinutes) }})</strong>
      </div>
    </div>

    <div v-if="Array.isArray(previewWarnings) && previewWarnings.length > 0" class="preview-warnings">
      <div
        v-for="(warning, warningIndex) in previewWarnings"
        :key="`preview-warning-${warningIndex}`"
        class="preview-warning-item"
      >
        <i class="pi pi-exclamation-triangle"></i>
        <span>{{ warning }}</span>
      </div>
    </div>
  </div>
</template>

<script setup>
import Tag from 'primevue/tag'

defineProps({
  previewResult: {
    type: Object,
    default: null
  },
  previewSummary: {
    type: Object,
    required: true
  },
  previewWarnings: {
    type: Array,
    default: () => []
  },
  formatDateTime: {
    type: Function,
    required: true
  },
  formatDurationMinutes: {
    type: Function,
    required: true
  }
})
</script>

<style scoped>
.preview-box {
  display: flex;
  flex-direction: column;
  gap: var(--gp-spacing-sm);
  border: 1px solid var(--gp-border-light);
  border-radius: var(--gp-radius-small);
  padding: var(--gp-spacing-sm);
  background: var(--gp-surface-light);
  color: var(--gp-text-primary);
}

.preview-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--gp-spacing-sm);
  flex-wrap: wrap;
}

.preview-range {
  color: var(--gp-text-secondary);
  font-size: 0.82rem;
}

.preview-metrics {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--gp-spacing-xs);
}

.preview-metric {
  display: flex;
  flex-direction: column;
  gap: 0.15rem;
  border: 1px solid var(--gp-border-light);
  border-radius: var(--gp-radius-small);
  background: var(--gp-surface-white);
  padding: 0.42rem 0.5rem;
}

.preview-metric-label {
  color: var(--gp-text-secondary);
  font-size: 0.74rem;
  text-transform: uppercase;
  letter-spacing: 0.02em;
}

.preview-warnings {
  display: flex;
  flex-direction: column;
  gap: 0.15rem;
  margin-top: 0.15rem;
}

.preview-warning-item {
  display: flex;
  align-items: center;
  gap: 0.35rem;
  color: var(--gp-text-secondary);
  font-size: 0.82rem;
}

@media (max-width: 720px) {
  .preview-metrics {
    grid-template-columns: 1fr;
  }
}
</style>
