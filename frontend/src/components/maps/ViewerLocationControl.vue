<template>
  <div class="viewer-location-control">
    <!-- Same container and button size as MapControls' groups, so the control stack lines up. -->
    <div class="viewer-location-group">
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
      :title="t('maps.popups.viewerLocation.hideLocation')"
      :aria-label="t('maps.popups.viewerLocation.hideLocation')"
      @click="$emit('stop')"
    >
      <i class="pi pi-times"></i>
    </button>
    </div>
    <div v-if="message" class="viewer-location-message" role="status">
      {{ message }}
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

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
  if (props.status === 'requesting') return t('maps.popups.viewerLocation.findingLocation')
  if (props.active) return t('maps.popups.viewerLocation.centerOnLocation')
  return t('maps.popups.viewerLocation.showLocation')
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
  top: var(--gp-spacing-lg);
  right: var(--gp-spacing-lg);
  z-index: 920;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 0.35rem;
  pointer-events: none;
}

.viewer-location-group {
  pointer-events: auto;
  display: flex;
  flex-direction: column;
  gap: var(--gp-spacing-xs);
  padding: var(--gp-spacing-xs);
  background: var(--gp-surface-card);
  border: 1px solid var(--gp-border);
  border-radius: var(--gp-radius-medium);
  box-shadow: var(--gp-shadow-medium);
}

.viewer-location-button,
.viewer-location-stop {
  width: 40px;
  height: 40px;
  padding: 0;
  border: 1px solid transparent;
  border-radius: var(--gp-radius-small);
  background: transparent;
  color: var(--gp-text-secondary);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: background-color 0.15s ease, color 0.15s ease, border-color 0.15s ease;
}

.viewer-location-button i,
.viewer-location-stop i {
  font-size: 16px;
}

.viewer-location-button:hover:not(:disabled),
.viewer-location-stop:hover {
  background: var(--gp-surface-muted);
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

@media (max-width: 768px), (max-height: 520px) and (pointer: coarse) {
  .viewer-location-button,
  .viewer-location-stop {
    width: 35px;
    height: 35px;
  }

  .viewer-location-button i,
  .viewer-location-stop i {
    font-size: 14px;
  }
}

.viewer-location-message {
  pointer-events: auto;
  width: 188px;
  max-width: calc(100vw - 2rem);
  padding: 0.35rem 0.5rem;
  border-radius: 6px;
  background: rgba(15, 23, 42, 0.88);
  color: #ffffff;
  font-size: 0.72rem;
  font-weight: 500;
  line-height: 1.25;
  text-align: left;
  box-shadow: 0 6px 18px rgba(15, 23, 42, 0.2);
}
</style>
