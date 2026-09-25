<template>
  <div class="trip-summary-bar">
    <div v-for="metric in metrics" :key="metric.label" class="trip-summary-metric">
      <span class="trip-summary-label">{{ metric.label }}</span>
      <strong class="trip-summary-value">{{ metric.value }}</strong>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'

/**
 * Always-visible trip metrics.
 *
 * Previously these lived inside the Overview tab, so they vanished on the Plan tab and
 * were absent entirely for future trips. They are cheap to compute and always relevant,
 * so they are now a permanent strip under the header.
 */
const props = defineProps({
  completionRate: { type: Number, default: null },
  visitedCount: { type: Number, default: 0 },
  totalCount: { type: Number, default: 0 },
  mustVisited: { type: Number, default: 0 },
  mustTotal: { type: Number, default: 0 },
  distanceLabel: { type: String, default: '' },
  durationLabel: { type: String, default: '' }
})

const metrics = computed(() => {
  const list = []

  if (props.completionRate !== null) {
    list.push({ label: 'Completion', value: `${props.completionRate}%` })
  }

  list.push({ label: 'Visited', value: `${props.visitedCount} / ${props.totalCount}` })

  if (props.mustTotal > 0) {
    const percent = Math.round((props.mustVisited / props.mustTotal) * 100)
    list.push({ label: 'Must visits', value: `${props.mustVisited} / ${props.mustTotal} (${percent}%)` })
  }

  const travel = [props.distanceLabel, props.durationLabel].filter(Boolean).join(' / ')
  if (travel) {
    list.push({ label: 'Distance / Duration', value: travel })
  }

  return list
})
</script>

<style scoped>
.trip-summary-bar {
  display: flex;
  flex-wrap: wrap;
  gap: var(--gp-spacing-sm);
  margin-bottom: var(--gp-spacing-sm);
}

.trip-summary-metric {
  display: flex;
  flex-direction: column;
  gap: 2px;
  flex: 1 1 8rem;
  min-width: 0;
  padding: var(--gp-spacing-sm) var(--gp-spacing-md);
  background: var(--gp-surface-light);
  border: 1px solid var(--gp-border-light);
  border-radius: var(--gp-radius-medium);
}

.trip-summary-label {
  font-size: 0.75rem;
  color: var(--gp-text-secondary);
  text-transform: uppercase;
  letter-spacing: 0.04em;
}

.trip-summary-value {
  font-size: 0.95rem;
  color: var(--gp-text-primary);
  overflow-wrap: anywhere;
}

.p-dark .trip-summary-metric {
  background: var(--gp-surface-dark);
  border-color: var(--gp-border-dark);
}

/* Mobile: the desktop flex-wrap grid breaks into two rows under a 390px viewport, which is
   ~120px of chrome above the map. One horizontally scrollable strip is ~52px. Same file,
   same specificity, later in the sheet - so this wins deterministically. */
@media (max-width: 768px) {
  .trip-summary-bar {
    flex-wrap: nowrap;
    gap: var(--gp-spacing-xs);
    overflow-x: auto;
    overscroll-behavior-x: contain;
    scroll-snap-type: x proximity;
    -webkit-overflow-scrolling: touch;
    scrollbar-width: none;
  }

  .trip-summary-bar::-webkit-scrollbar {
    display: none;
  }

  .trip-summary-metric {
    flex: 0 0 auto;
    scroll-snap-align: start;
    justify-content: center;
    min-height: 52px;
    padding: var(--gp-spacing-sm);
    border-radius: var(--gp-radius-small);
  }

  .trip-summary-label {
    font-size: 0.625rem;
  }

  .trip-summary-value {
    font-size: 0.8125rem;
    white-space: nowrap;
  }
}
</style>
