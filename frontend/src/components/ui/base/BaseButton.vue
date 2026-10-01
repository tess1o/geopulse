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
/* GeoPulse button variants. Global PrimeVue button tweaks live in styles/primevue-overrides.css. */
.gp-button--gp-primary {
  background: var(--gp-primary);
  border-color: var(--gp-primary);
  color: var(--gp-primary-contrast);
  box-shadow: var(--gp-shadow-button);
}

.gp-button--gp-primary:hover:not(:disabled),
.gp-button--gp-primary:active:not(:disabled) {
  background: var(--gp-primary-hover);
  border-color: var(--gp-primary-hover);
}

.gp-button--gp-primary:hover:not(:disabled) {
  box-shadow: var(--gp-shadow-button-hover);
  transform: translateY(-1px);
}

.gp-button--gp-secondary {
  background: var(--gp-secondary);
  border-color: var(--gp-secondary);
  color: var(--gp-primary-contrast);
  box-shadow: var(--gp-shadow-button);
}

.gp-button--gp-secondary:hover:not(:disabled),
.gp-button--gp-secondary:active:not(:disabled) {
  background: var(--gp-secondary-dark);
  border-color: var(--gp-secondary-dark);
}

.gp-button--gp-secondary:hover:not(:disabled) {
  box-shadow: var(--gp-shadow-button-hover);
  transform: translateY(-1px);
}

.gp-button--gp-ghost {
  background: transparent;
  border: 1px solid var(--gp-border);
  color: var(--gp-text-primary);
  box-shadow: none;
}

.gp-button--gp-ghost:hover:not(:disabled) {
  background: var(--gp-surface-ground);
  border-color: var(--gp-primary);
  color: var(--gp-primary-text);
  box-shadow: var(--gp-shadow-subtle);
}

.gp-button--gp-ghost:active:not(:disabled) {
  background: var(--gp-timeline-blue);
}

.gp-button--gp-minimal {
  background: transparent;
  border: none;
  color: var(--gp-text-secondary);
  box-shadow: none;
  padding: var(--gp-spacing-sm);
}

.gp-button--gp-minimal:hover:not(:disabled) {
  background: var(--gp-surface-ground);
  color: var(--gp-text-primary);
  border-radius: var(--gp-radius-small);
}

.gp-button--gp-minimal:active:not(:disabled) {
  background: var(--gp-surface-card);
}

.gp-button--full-width {
  width: 100%;
}

.gp-button--compact {
  padding: var(--gp-spacing-xs) var(--gp-spacing-sm);
  font-size: 0.75rem;
  min-height: auto;
}

.gp-button--compact .p-button-icon {
  font-size: 0.7rem;
}

@media (max-width: 640px) {
  .gp-button--compact {
    padding: var(--gp-spacing-xs);
    font-size: 0.7rem;
  }
}
</style>
