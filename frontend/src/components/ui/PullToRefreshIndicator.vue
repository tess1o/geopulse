<template>
  <div
    class="pull-refresh-zone"
    :class="{
      'pull-refresh-zone--enabled': enabled,
      'pull-refresh-zone--refreshing': refreshing
    }"
    :style="pullRefreshStyle"
  >
    <div
      v-if="enabled || refreshing"
      class="pull-refresh-indicator"
      :class="`pull-refresh-indicator--${state}`"
      aria-live="polite"
    >
      <i :class="['pull-refresh-icon', indicatorIcon]"></i>
      <span>{{ indicatorText }}</span>
    </div>

    <slot />
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  enabled: {
    type: Boolean,
    default: false
  },
  refreshing: {
    type: Boolean,
    default: false
  },
  distance: {
    type: Number,
    default: 0
  },
  state: {
    type: String,
    default: 'idle',
    validator: (value) => ['idle', 'pulling', 'ready', 'refreshing', 'error'].includes(value)
  },
  pullText: {
    type: String,
    default: 'Pull to refresh'
  },
  readyText: {
    type: String,
    default: 'Release to refresh'
  },
  refreshingText: {
    type: String,
    default: 'Refreshing...'
  }
})

const pullRefreshStyle = computed(() => ({
  '--pull-distance': `${props.distance}px`
}))

const indicatorText = computed(() => {
  if (props.state === 'refreshing') return props.refreshingText
  if (props.state === 'ready') return props.readyText
  return props.pullText
})

const indicatorIcon = computed(() => {
  if (props.state === 'refreshing') return 'pi pi-spinner pi-spin'
  if (props.state === 'ready') return 'pi pi-arrow-up'
  return 'pi pi-arrow-down'
})
</script>

<style scoped>
.pull-refresh-zone {
  --pull-distance: 0px;
  position: relative;
}

.pull-refresh-zone--enabled {
  overscroll-behavior-y: contain;
}

.pull-refresh-indicator {
  position: absolute;
  top: calc(-2.75rem - env(safe-area-inset-top));
  left: 50%;
  z-index: 20;
  display: inline-flex;
  align-items: center;
  gap: 0.5rem;
  min-height: 2.25rem;
  padding: 0.45rem 0.8rem;
  border-radius: 999px;
  border: 1px solid var(--gp-border-light);
  background: var(--gp-surface-white);
  color: var(--gp-text-secondary);
  box-shadow: var(--gp-shadow-card);
  font-size: 0.82rem;
  font-weight: 600;
  pointer-events: none;
  opacity: 0;
  transform: translate(-50%, var(--pull-distance));
  transition: opacity 0.15s ease, transform 0.15s ease;
  white-space: nowrap;
}

.pull-refresh-indicator--pulling,
.pull-refresh-indicator--ready,
.pull-refresh-indicator--refreshing {
  opacity: 1;
  transition: none;
}

.pull-refresh-indicator--ready {
  color: var(--gp-primary);
  border-color: var(--gp-primary);
}

.pull-refresh-indicator--refreshing {
  opacity: 1;
  transform: translate(-50%, 3rem);
}

.pull-refresh-icon {
  font-size: 0.95rem;
}

.p-dark .pull-refresh-indicator {
  background: var(--gp-surface-dark);
  border-color: var(--gp-border-dark);
  color: var(--gp-text-primary);
}
</style>
