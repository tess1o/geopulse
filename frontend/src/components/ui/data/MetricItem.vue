<template>
  <div class="gp-metric-item" :class="metricClasses">
    <div v-if="icon" class="gp-metric-icon" :class="iconClasses">
      <i :class="icon" />
    </div>
    <div class="gp-metric-content">
      <div class="gp-metric-value" :class="valueClasses">
        {{ formattedValue }}
      </div>
      <div class="gp-metric-label">{{ label }}</div>
      <div v-if="subtitle" class="gp-metric-subtitle">{{ subtitle }}</div>
      <div v-if="change !== undefined" class="gp-metric-change" :class="changeClasses">
        <i :class="changeIcon" />
        <span>{{ formattedChange }}</span>
      </div>
    </div>
    <div v-if="$slots.action" class="gp-metric-action">
      <slot name="action" />
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  icon: {
    type: String,
    default: ''
  },
  iconColor: {
    type: String,
    default: 'primary',
    validator: (value) => ['primary', 'secondary', 'success', 'danger', 'warning', 'info', 'muted'].includes(value)
  },
  value: {
    type: [String, Number],
    required: true
  },
  label: {
    type: String,
    required: true
  },
  subtitle: {
    type: String,
    default: ''
  },
  formatter: {
    type: Function,
    default: null
  },
  change: {
    type: Number,
    default: undefined
  },
  changeFormatter: {
    type: Function,
    default: null
  },
  size: {
    type: String,
    default: 'default',
    validator: (value) => ['small', 'default', 'large'].includes(value)
  },
  layout: {
    type: String,
    default: 'horizontal',
    validator: (value) => ['horizontal', 'vertical'].includes(value)
  },
  variant: {
    type: String,
    default: 'default',
    validator: (value) => ['default', 'card', 'minimal'].includes(value)
  }
})

const formattedValue = computed(() => {
  return props.formatter ? props.formatter(props.value) : props.value
})

const formattedChange = computed(() => {
  if (props.change === undefined) return ''
  if (props.changeFormatter) return props.changeFormatter(props.change)
  
  const absChange = Math.abs(props.change)
  const sign = props.change >= 0 ? '+' : '-'
  return `${sign}${absChange}%`
})

const metricClasses = computed(() => ({
  [`gp-metric-item--${props.size}`]: props.size !== 'default',
  [`gp-metric-item--${props.layout}`]: props.layout !== 'horizontal',
  [`gp-metric-item--${props.variant}`]: props.variant !== 'default'
}))

const iconClasses = computed(() => ({
  [`gp-metric-icon--${props.iconColor}`]: props.iconColor
}))

const valueClasses = computed(() => ({
  [`gp-metric-value--${props.size}`]: props.size !== 'default'
}))

const changeClasses = computed(() => {
  if (props.change === undefined) return {}
  return {
    'gp-metric-change--positive': props.change > 0,
    'gp-metric-change--negative': props.change < 0,
    'gp-metric-change--neutral': props.change === 0
  }
})

const changeIcon = computed(() => {
  if (props.change === undefined) return ''
  if (props.change > 0) return 'pi pi-arrow-up'
  if (props.change < 0) return 'pi pi-arrow-down'
  return 'pi pi-minus'
})
</script>

<style scoped>
/* Base Metric Item */
.gp-metric-item {
  display: flex;
  align-items: flex-start;
  gap: var(--gp-spacing-md);
  padding: var(--gp-spacing-md) 0;
  transition: all 0.2s ease;
}

.gp-metric-item:not(:last-child) {
  border-bottom: 1px solid var(--gp-border-light);
}

/* Icon */
.gp-metric-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border-radius: var(--gp-radius-medium);
  flex-shrink: 0;
  font-size: 1rem;
}

.gp-metric-icon--primary {
  background: var(--gp-timeline-blue);
  color: var(--gp-primary);
  border: 1px solid var(--gp-primary-light);
}

.gp-metric-icon--secondary {
  background: var(--gp-timeline-green);
  color: var(--gp-secondary);
  border: 1px solid var(--gp-secondary-light);
}

.gp-metric-icon--success {
  background: var(--gp-success-light);
  color: var(--gp-success);
  border: 1px solid var(--gp-success);
}

.gp-metric-icon--danger {
  background: var(--gp-danger-light);
  color: var(--gp-danger);
  border: 1px solid var(--gp-danger);
}

.gp-metric-icon--warning {
  background: var(--gp-warning-light);
  color: var(--gp-warning);
  border: 1px solid var(--gp-warning);
}

.gp-metric-icon--info {
  background: var(--gp-info-light);
  color: var(--gp-info);
  border: 1px solid var(--gp-info);
}

.gp-metric-icon--muted {
  background: var(--gp-surface-light);
  color: var(--gp-text-muted);
  border: 1px solid var(--gp-border-light);
}

/* Content */
.gp-metric-content {
  flex: 1;
  min-width: 0;
}

.gp-metric-value {
  font-size: 1.25rem;
  font-weight: 700;
  color: var(--gp-text-primary);
  margin-bottom: 0.25rem;
  line-height: 1.2;
}

.gp-metric-label {
  font-size: 0.8rem;
  color: var(--gp-text-secondary);
  font-weight: 500;
  line-height: 1.3;
  margin-bottom: 0.125rem;
}

.gp-metric-subtitle {
  font-size: 0.75rem;
  color: var(--gp-text-muted);
  font-weight: 500;
  line-height: 1.3;
  margin-bottom: 0.25rem;
}

.gp-metric-change {
  display: flex;
  align-items: center;
  gap: 0.25rem;
  font-size: 0.75rem;
  font-weight: 600;
  margin-top: 0.25rem;
}

.gp-metric-change--positive {
  color: var(--gp-success);
}

.gp-metric-change--negative {
  color: var(--gp-danger);
}

.gp-metric-change--neutral {
  color: var(--gp-text-muted);
}

.gp-metric-change i {
  font-size: 0.7rem;
}

/* Action */
.gp-metric-action {
  flex-shrink: 0;
  margin-left: auto;
}

/* Size Variants */
.gp-metric-item--small {
  gap: var(--gp-spacing-sm);
  padding: var(--gp-spacing-sm) 0;
}

.gp-metric-item--small .gp-metric-icon {
  width: 28px;
  height: 28px;
  font-size: 0.875rem;
}

.gp-metric-item--small .gp-metric-value {
  font-size: 1.125rem;
}

.gp-metric-item--small .gp-metric-label {
  font-size: 0.75rem;
}

.gp-metric-item--small .gp-metric-subtitle {
  font-size: 0.7rem;
}

.gp-metric-item--large {
  gap: var(--gp-spacing-lg);
  padding: var(--gp-spacing-lg) 0;
}

.gp-metric-item--large .gp-metric-icon {
  width: 40px;
  height: 40px;
  font-size: 1.125rem;
}

.gp-metric-item--large .gp-metric-value {
  font-size: 1.5rem;
}

.gp-metric-item--large .gp-metric-label {
  font-size: 0.875rem;
}

.gp-metric-item--large .gp-metric-subtitle {
  font-size: 0.8rem;
}

/* Layout Variants */
.gp-metric-item--vertical {
  flex-direction: column;
  align-items: center;
  text-align: center;
}

.gp-metric-item--vertical .gp-metric-action {
  margin-left: 0;
  margin-top: var(--gp-spacing-sm);
}

/* Style Variants */
.gp-metric-item--card {
  background: var(--gp-surface-light);
  border: 1px solid var(--gp-border-light);
  border-radius: var(--gp-radius-medium);
  padding: var(--gp-spacing-md);
  margin-bottom: var(--gp-spacing-sm);
}

.gp-metric-item--card:not(:last-child) {
  border-bottom: 1px solid var(--gp-border-light);
}

.gp-metric-item--card:hover {
  background: var(--gp-surface-white);
  box-shadow: var(--gp-shadow-light);
  transform: translateY(-1px);
  border-color: var(--gp-border-medium);
}

.gp-metric-item--minimal {
  padding: var(--gp-spacing-sm) 0;
  gap: var(--gp-spacing-sm);
}

.gp-metric-item--minimal .gp-metric-icon {
  width: 24px;
  height: 24px;
  font-size: 0.8rem;
}

.gp-metric-item--minimal .gp-metric-value {
  font-size: 1rem;
  margin-bottom: 0.125rem;
}

.gp-metric-item--minimal .gp-metric-label {
  font-size: 0.7rem;
}

/* Dark Mode */
.p-dark .gp-metric-item:not(:last-child) {
  border-bottom-color: var(--gp-border-dark);
}

.p-dark .gp-metric-value {
  color: var(--gp-text-primary);
}

.p-dark .gp-metric-label {
  color: var(--gp-text-secondary);
}

.p-dark .gp-metric-subtitle {
  color: var(--gp-text-muted);
}

.p-dark .gp-metric-item--card {
  background: var(--gp-surface-darker);
  border-color: var(--gp-border-dark);
}

.p-dark .gp-metric-item--card:hover {
  background: var(--gp-surface-light);
  border-color: var(--gp-border-medium);
  box-shadow: var(--gp-shadow-light);
}

.p-dark .gp-metric-icon--muted {
  background: var(--gp-surface-darker);
  border-color: var(--gp-border-dark);
  color: var(--gp-text-muted);
}

/* Additional dark mode icon refinements */
.p-dark .gp-metric-icon--primary {
  background: rgba(30, 64, 175, 0.2);
  border-color: var(--gp-primary);
}

.p-dark .gp-metric-icon--secondary {
  background: rgba(5, 150, 105, 0.2);
  border-color: var(--gp-secondary);
}

.p-dark .gp-metric-icon--success {
  background: rgba(16, 185, 129, 0.2);
  border-color: var(--gp-success);
}

.p-dark .gp-metric-icon--danger {
  background: rgba(239, 68, 68, 0.2);
  border-color: var(--gp-danger);
}

.p-dark .gp-metric-icon--warning {
  background: rgba(245, 158, 11, 0.2);
  border-color: var(--gp-warning);
}

.p-dark .gp-metric-icon--info {
  background: rgba(6, 182, 212, 0.2);
  border-color: var(--gp-info);
}

/* Responsive */
@media (max-width: 640px) {
  .gp-metric-item {
    gap: var(--gp-spacing-sm);
    padding: var(--gp-spacing-sm) 0;
  }

  .gp-metric-icon {
    width: 28px;
    height: 28px;
    font-size: 0.875rem;
  }

  .gp-metric-value {
    font-size: 1.125rem;
  }

  .gp-metric-label {
    font-size: 0.75rem;
  }

  .gp-metric-subtitle {
    font-size: 0.7rem;
  }

  .gp-metric-change {
    font-size: 0.7rem;
  }

  .gp-metric-item--vertical {
    flex-direction: row;
    text-align: left;
  }
}

/* State Classes for Compatibility */
.gp-metric-item {
  /* This ensures compatibility with existing gp-metric-item usage */
}
</style>