<template>
  <div class="classification-step" :class="stepClass">
    <div class="step-header">
      <div class="step-number">{{ index }}</div>
      <div class="step-type">
        <span class="type-icon">{{ getTransportIcon(step.tripType) }}</span>
        <span class="type-name">{{ step.tripType }}</span>
      </div>
      <div class="step-status">
        <i v-if="!step.checked" class="pi pi-minus-circle status-icon status-icon--skipped"></i>
        <i v-else-if="step.passed" class="pi pi-check-circle status-icon status-icon--passed"></i>
        <i v-else class="pi pi-times-circle status-icon status-icon--failed"></i>
        <span class="status-text">{{ getStatusText() }}</span>
      </div>
    </div>

    <div class="step-reason">
      {{ step.reason }}
    </div>

    <div v-if="step.checks && step.checks.length > 0" class="step-checks">
      <div class="checks-header">Threshold Checks:</div>
      <div class="checks-list">
        <div
          v-for="(check, checkIndex) in step.checks"
          :key="checkIndex"
          class="check-item"
          :class="{ 'check-item--passed': check.passed, 'check-item--failed': !check.passed }"
        >
          <i :class="check.passed ? 'pi pi-check' : 'pi pi-times'" class="check-icon"></i>
          <span class="check-name">{{ check.name }}</span>
          <span class="check-operator">{{ check.operator }}</span>
          <span class="check-threshold">{{ formatValue(check.threshold) }}</span>
          <span class="check-actual">(actual: {{ formatValue(check.actual) }})</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  step: {
    type: Object,
    required: true
  },
  index: {
    type: Number,
    required: true
  },
  isSelected: {
    type: Boolean,
    default: false
  }
})

const stepClass = computed(() => {
  const classes = []
  if (props.isSelected) classes.push('classification-step--selected')
  if (!props.step.checked) classes.push('classification-step--skipped')
  else if (props.step.passed) classes.push('classification-step--passed')
  else classes.push('classification-step--failed')
  return classes.join(' ')
})

const getStatusText = () => {
  if (!props.step.checked) return 'Not Enabled'
  if (props.step.passed) return 'Passed'
  return 'Failed'
}

const getTransportIcon = (type) => {
  const iconMap = {
    'FLIGHT': '✈️',
    'TRAIN': '🚊',
    'BICYCLE': '🚴',
    'RUNNING': '🏃',
    'CAR': '🚗',
    'WALK': '🚶',
    'UNKNOWN': '❓'
  }
  return iconMap[type] || '❓'
}

const formatValue = (value) => {
  if (value === null || value === undefined) return 'N/A'
  if (typeof value === 'number') return value.toFixed(1)
  return value
}
</script>

<style scoped>
.classification-step {
  border: 2px solid var(--gp-border-light);
  border-radius: var(--gp-radius-medium);
  padding: var(--gp-spacing-md);
  background: var(--gp-surface-white);
  margin-bottom: var(--gp-spacing-md);
  transition: all 0.2s ease;
}

.classification-step:last-child {
  margin-bottom: 0;
}

.classification-step--selected {
  border-color: var(--gp-primary);
  background: var(--gp-primary-50);
  box-shadow: var(--gp-shadow-medium);
}

.classification-step--skipped {
  opacity: 0.6;
  border-style: dashed;
}

.classification-step--passed {
  border-left: 4px solid var(--gp-success);
}

.classification-step--failed {
  border-left: 4px solid var(--gp-danger);
}

.step-header {
  display: flex;
  align-items: center;
  gap: var(--gp-spacing-md);
  margin-bottom: var(--gp-spacing-sm);
}

.step-number {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: var(--gp-surface-light);
  color: var(--gp-text-secondary);
  font-weight: 700;
  font-size: 0.9rem;
  flex-shrink: 0;
}

.step-type {
  display: flex;
  align-items: center;
  gap: var(--gp-spacing-sm);
  flex: 1;
}

.type-icon {
  font-size: 1.5rem;
}

.type-name {
  font-weight: 700;
  font-size: 1rem;
  color: var(--gp-text-primary);
}

.step-status {
  display: flex;
  align-items: center;
  gap: var(--gp-spacing-xs);
}

.status-icon {
  font-size: 1.25rem;
}

.status-icon--passed {
  color: var(--gp-success);
}

.status-icon--failed {
  color: var(--gp-danger);
}

.status-icon--skipped {
  color: var(--gp-text-muted);
}

.status-text {
  font-weight: 600;
  font-size: 0.875rem;
}

.classification-step--passed .status-text {
  color: var(--gp-success-700);
}

.classification-step--failed .status-text {
  color: var(--gp-danger-700);
}

.classification-step--skipped .status-text {
  color: var(--gp-text-muted);
}

.step-reason {
  color: var(--gp-text-secondary);
  font-size: 0.9rem;
  line-height: 1.5;
  padding: var(--gp-spacing-sm);
  background: var(--gp-surface-light);
  border-radius: var(--gp-radius-small);
  margin-bottom: var(--gp-spacing-sm);
}

.step-checks {
  margin-top: var(--gp-spacing-sm);
}

.checks-header {
  font-weight: 600;
  font-size: 0.875rem;
  color: var(--gp-text-primary);
  margin-bottom: var(--gp-spacing-xs);
}

.checks-list {
  display: flex;
  flex-direction: column;
  gap: var(--gp-spacing-xs);
}

.check-item {
  display: flex;
  align-items: center;
  gap: var(--gp-spacing-xs);
  padding: var(--gp-spacing-xs);
  border-radius: var(--gp-radius-small);
  font-size: 0.85rem;
  font-family: monospace;
}

.check-item--passed {
  background: var(--gp-success-50);
  color: var(--gp-success-900);
}

.check-item--failed {
  background: var(--gp-danger-50);
  color: var(--gp-danger-900);
}

.check-icon {
  font-size: 0.875rem;
  flex-shrink: 0;
}

.check-name {
  font-weight: 600;
  min-width: 140px;
}

.check-operator {
  font-weight: 700;
  min-width: 24px;
  text-align: center;
}

.check-threshold {
  font-weight: 600;
}

.check-actual {
  color: var(--gp-text-secondary);
  font-style: italic;
}

/* Dark Mode */
.p-dark .classification-step {
  background: var(--gp-surface-dark);
  border-color: var(--gp-border-medium);
}

.p-dark .classification-step--selected {
  background: var(--gp-primary-900);
  border-color: var(--gp-primary);
}

.p-dark .step-number {
  background: var(--gp-surface-darker);
  color: var(--gp-text-primary);
}

.p-dark .type-name {
  color: var(--gp-text-primary);
}

.p-dark .step-reason {
  background: var(--gp-surface-darker);
  color: var(--gp-text-secondary);
}

.p-dark .checks-header {
  color: var(--gp-text-primary);
}

.p-dark .check-item--passed {
  background: var(--gp-success-900);
  color: var(--gp-success-100);
}

.p-dark .check-item--failed {
  background: var(--gp-danger-900);
  color: var(--gp-danger-100);
}

.p-dark .check-actual {
  color: var(--gp-text-muted);
}

/* Mobile Responsive */
@media (max-width: 768px) {
  .classification-step {
    padding: var(--gp-spacing-sm);
  }

  .step-header {
    flex-wrap: wrap;
    gap: var(--gp-spacing-sm);
  }

  .step-number {
    width: 28px;
    height: 28px;
    font-size: 0.8rem;
  }

  .type-icon {
    font-size: 1.25rem;
  }

  .type-name {
    font-size: 0.9rem;
  }

  .step-status {
    width: 100%;
    margin-left: 40px;
  }

  .status-icon {
    font-size: 1rem;
  }

  .status-text {
    font-size: 0.8rem;
  }

  .step-reason {
    font-size: 0.85rem;
    padding: var(--gp-spacing-xs);
  }

  .check-item {
    flex-wrap: wrap;
    font-size: 0.8rem;
  }

  .check-name {
    min-width: auto;
    width: 100%;
  }

  .check-operator {
    min-width: auto;
  }
}
</style>
