<template>
  <div class="viewer-location-control">
    <button
      type="button"
      class="viewer-location-button"
      :class="{
        active: active,
        requesting: status === 'requesting',
        fallback: status === 'fallback',
        error: hasError
      }"
      :disabled="disabled || status === 'requesting'"
      :title="buttonTitle"
      :aria-label="buttonTitle"
      @click="$emit('locate')"
    >
      <i :class="buttonIcon"></i>
    </button>
    <button
      v-if="active"
      type="button"
      class="viewer-location-stop"
      title="Hide your location"
      aria-label="Hide your location"
      @click="$emit('stop')"
    >
      <i class="pi pi-times"></i>
    </button>
    <div v-if="message" class="viewer-location-message" role="status">
      {{ message }}
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  status: {
    type: String,
    default: 'idle'
  },
  active: {
    type: Boolean,
    default: false
  },
  message: {
    type: String,
    default: ''
  },
  disabled: {
    type: Boolean,
    default: false
  }
})

defineEmits(['locate', 'stop'])

const buttonTitle = computed(() => {
  if (props.status === 'requesting') return 'Finding your location'
  if (props.active) return 'Center on your location'
  return 'Show your location'
})

const buttonIcon = computed(() => {
  if (props.status === 'requesting') return 'pi pi-spin pi-spinner'
  if (props.status === 'fallback') return 'pi pi-history'
  return 'pi pi-map-marker'
})

const hasError = computed(() => ['denied', 'unavailable', 'error'].includes(props.status))
</script>

<style scoped>
.viewer-location-control {
  position: absolute;
  top: var(--gp-spacing-lg, 1rem);
  right: var(--gp-spacing-lg, 1rem);
  z-index: 920;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 0.35rem;
  pointer-events: none;
}

.viewer-location-button,
.viewer-location-stop {
  pointer-events: auto;
  width: 40px;
  height: 40px;
  border: 1px solid var(--surface-border, #d1d5db);
  border-radius: 6px;
  background: var(--surface-card, #ffffff);
  color: var(--text-color, #111827);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  box-shadow: 0 2px 8px rgba(15, 23, 42, 0.18);
  transition: background-color 0.15s ease, color 0.15s ease, border-color 0.15s ease;
}

.viewer-location-button:hover:not(:disabled),
.viewer-location-stop:hover {
  background: var(--surface-hover, #f3f4f6);
}

.viewer-location-button:disabled {
  cursor: wait;
  opacity: 0.8;
}

.viewer-location-button.active {
  background: #0ea5e9;
  border-color: #0284c7;
  color: #ffffff;
}

.viewer-location-button.fallback {
  background: #f59e0b;
  border-color: #d97706;
  color: #111827;
}

.viewer-location-button.error {
  border-color: #f97316;
  color: #c2410c;
}

.viewer-location-stop {
  width: 32px;
  height: 32px;
}

.viewer-location-message {
  pointer-events: auto;
  width: 188px;
  max-width: calc(100vw - 2rem);
  padding: 0.35rem 0.5rem;
  border-radius: 6px;
  background: rgba(15, 23, 42, 0.88);
  color: #ffffff;
  font-size: 0.72rem !important;
  font-weight: 500;
  line-height: 1.25;
  text-align: left;
  box-shadow: 0 6px 18px rgba(15, 23, 42, 0.2);
}
</style>
