<template>
  <Button
    :label="label"
    :icon="icon"
    :iconPos="iconPosition"
    :loading="loading"
    :disabled="disabled"
    :size="size"
    :severity="severity"
    :outlined="outlined"
    :text="text"
    :raised="raised"
    :rounded="rounded"
    :link="link"
    :badge="badge"
    :badgeClass="badgeClass"
    :tooltip="tooltip"
    :tooltipOptions="tooltipOptions"
    :class="buttonClasses"
    @click="handleClick"
    @focus="handleFocus"
    @blur="handleBlur"
  >
    <template v-if="$slots.default" #default>
      <slot />
    </template>
  </Button>
</template>

<script setup>
import { computed } from 'vue'
import Button from 'primevue/button'

const props = defineProps({
  label: {
    type: String,
    default: ''
  },
  icon: {
    type: String,
    default: ''
  },
  iconPosition: {
    type: String,
    default: 'left',
    validator: (value) => ['left', 'right', 'top', 'bottom'].includes(value)
  },
  loading: {
    type: Boolean,
    default: false
  },
  disabled: {
    type: Boolean,
    default: false
  },
  size: {
    type: String,
    default: null,
    validator: (value) => !value || ['small', 'large'].includes(value)
  },
  severity: {
    type: String,
    default: null,
    validator: (value) => !value || ['secondary', 'success', 'info', 'warning', 'help', 'danger'].includes(value)
  },
  outlined: {
    type: Boolean,
    default: false
  },
  text: {
    type: Boolean,
    default: false
  },
  raised: {
    type: Boolean,
    default: false
  },
  rounded: {
    type: Boolean,
    default: false
  },
  link: {
    type: Boolean,
    default: false
  },
  badge: {
    type: String,
    default: ''
  },
  badgeClass: {
    type: String,
    default: ''
  },
  tooltip: {
    type: String,
    default: ''
  },
  tooltipOptions: {
    type: Object,
    default: () => ({})
  },
  variant: {
    type: String,
    default: 'default',
    validator: (value) => ['default', 'gp-primary', 'gp-secondary', 'gp-ghost', 'gp-minimal'].includes(value)
  },
  fullWidth: {
    type: Boolean,
    default: false
  },
  compact: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['click', 'focus', 'blur'])

const buttonClasses = computed(() => ({
  [`gp-button--${props.variant}`]: props.variant !== 'default',
  'gp-button--full-width': props.fullWidth,
  'gp-button--compact': props.compact
}))

const handleClick = (event) => {
  if (!props.disabled && !props.loading) {
    emit('click', event)
  }
}

const handleFocus = (event) => {
  emit('focus', event)
}

const handleBlur = (event) => {
  emit('blur', event)
}
</script>

<style>
/* GeoPulse Button Variants */
.gp-button--gp-primary {
  background: var(--gp-primary) !important;
  border-color: var(--gp-primary) !important;
  color: white !important;
  box-shadow: var(--gp-shadow-button) !important;
}

.gp-button--gp-primary:hover:not(:disabled) {
  background: var(--gp-primary-dark) !important;
  border-color: var(--gp-primary-dark) !important;
  box-shadow: var(--gp-shadow-button-hover) !important;
  transform: translateY(-1px);
}

.gp-button--gp-primary:active:not(:disabled) {
  background: var(--gp-primary-dark) !important;
  border-color: var(--gp-primary-dark) !important;
  transform: translateY(0);
}

.gp-button--gp-secondary {
  background: var(--gp-secondary) !important;
  border-color: var(--gp-secondary) !important;
  color: white !important;
  box-shadow: var(--gp-shadow-button) !important;
}

.gp-button--gp-secondary:hover:not(:disabled) {
  background: var(--gp-secondary-dark) !important;
  border-color: var(--gp-secondary-dark) !important;
  box-shadow: var(--gp-shadow-button-hover) !important;
  transform: translateY(-1px);
}

.gp-button--gp-secondary:active:not(:disabled) {
  background: var(--gp-secondary-dark) !important;
  border-color: var(--gp-secondary-dark) !important;
  transform: translateY(0);
}

.gp-button--gp-ghost {
  background: transparent !important;
  border: 1px solid var(--gp-border-light) !important;
  color: var(--gp-text-primary) !important;
  box-shadow: none !important;
}

.gp-button--gp-ghost:hover:not(:disabled) {
  background: var(--gp-surface-light) !important;
  border-color: var(--gp-primary) !important;
  color: var(--gp-primary) !important;
  box-shadow: var(--gp-shadow-subtle) !important;
}

.gp-button--gp-ghost:active:not(:disabled) {
  background: var(--gp-timeline-blue) !important;
}

.gp-button--gp-minimal {
  background: transparent !important;
  border: none !important;
  color: var(--gp-text-secondary) !important;
  box-shadow: none !important;
  padding: var(--gp-spacing-sm) !important;
}

.gp-button--gp-minimal:hover:not(:disabled) {
  background: var(--gp-surface-light) !important;
  color: var(--gp-text-primary) !important;
  border-radius: var(--gp-radius-small) !important;
}

.gp-button--gp-minimal:active:not(:disabled) {
  background: var(--gp-surface-white) !important;
}

/* Full Width */
.gp-button--full-width {
  width: 100% !important;
}

/* Compact */
.gp-button--compact {
  padding: var(--gp-spacing-xs) var(--gp-spacing-sm) !important;
  font-size: 0.75rem !important;
  min-height: auto !important;
}

.gp-button--compact .p-button-icon {
  font-size: 0.7rem !important;
}

/* Enhanced Button Styles */
.p-button {
  border-radius: var(--gp-radius-medium) !important;
  font-weight: 500 !important;
  transition: all 0.2s cubic-bezier(0.25, 0.8, 0.25, 1) !important;
  position: relative !important;
  overflow: hidden !important;
}

.p-button:not(.p-button-text):not(.p-button-outlined) {
  box-shadow: var(--gp-shadow-button) !important;
}

.p-button:hover:not(:disabled):not(.p-button-text) {
  transform: translateY(-1px) !important;
  box-shadow: var(--gp-shadow-button-hover) !important;
}

.p-button:active:not(:disabled) {
  transform: translateY(0) !important;
}

.p-button:focus {
  box-shadow: var(--gp-shadow-button), 0 0 0 2px var(--gp-primary-light) !important;
}

/* Loading State */
.p-button[aria-label*="Loading"] {
  pointer-events: none !important;
}

.p-button .p-button-loading-icon {
  color: currentColor !important;
}

/* Icon Only Buttons */
.p-button.p-button-icon-only {
  width: 2.5rem !important;
  height: 2.5rem !important;
}

.p-button.p-button-icon-only.p-button-sm {
  width: 2rem !important;
  height: 2rem !important;
}

.p-button.p-button-icon-only.p-button-lg {
  width: 3rem !important;
  height: 3rem !important;
}

/* Disabled State */
.p-button:disabled {
  opacity: 0.6 !important;
  cursor: not-allowed !important;
  transform: none !important;
  box-shadow: none !important;
}

/* Dark Mode */
.p-dark .gp-button--gp-ghost {
  border-color: var(--gp-border-dark) !important;
  color: var(--gp-text-primary) !important;
}

.p-dark .gp-button--gp-ghost:hover:not(:disabled) {
  background: var(--gp-surface-darker) !important;
  border-color: var(--gp-primary) !important;
}

.p-dark .gp-button--gp-ghost:active:not(:disabled) {
  background: var(--gp-primary-dark) !important;
}

.p-dark .gp-button--gp-minimal {
  color: var(--gp-text-secondary) !important;
}

.p-dark .gp-button--gp-minimal:hover:not(:disabled) {
  background: var(--gp-surface-darker) !important;
  color: var(--gp-text-primary) !important;
}

.p-dark .gp-button--gp-minimal:active:not(:disabled) {
  background: var(--gp-surface-dark) !important;
}

/* Responsive */
@media (max-width: 640px) {
  .p-button {
    padding: var(--gp-spacing-sm) var(--gp-spacing-md) !important;
    font-size: 0.875rem !important;
  }

  .p-button.p-button-lg {
    padding: var(--gp-spacing-md) var(--gp-spacing-lg) !important;
    font-size: 1rem !important;
  }

  .p-button.p-button-sm {
    padding: var(--gp-spacing-xs) var(--gp-spacing-sm) !important;
    font-size: 0.75rem !important;
  }

  .gp-button--compact {
    padding: var(--gp-spacing-xs) !important;
    font-size: 0.7rem !important;
  }
}

/* Button Group Support */
.p-buttonset .p-button {
  border-radius: 0 !important;
}

.p-buttonset .p-button:first-child {
  border-top-left-radius: var(--gp-radius-medium) !important;
  border-bottom-left-radius: var(--gp-radius-medium) !important;
}

.p-buttonset .p-button:last-child {
  border-top-right-radius: var(--gp-radius-medium) !important;
  border-bottom-right-radius: var(--gp-radius-medium) !important;
}

/* Ripple Effect Override */
.p-button .p-ink {
  background: rgba(255, 255, 255, 0.3) !important;
  border-radius: 50% !important;
}

.gp-button--gp-ghost .p-ink,
.gp-button--gp-minimal .p-ink {
  background: var(--gp-primary) !important;
  opacity: 0.1 !important;
}

/* Badge Support */
.p-button .p-badge {
  min-width: 1rem !important;
  height: 1rem !important;
  line-height: 1rem !important;
  font-size: 0.6rem !important;
  border-radius: 50% !important;
}
</style>