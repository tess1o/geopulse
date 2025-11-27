<template>
  <div class="stat-card" :class="severityClass">
    <div class="stat-icon">
      <i :class="icon"></i>
    </div>
    <div class="stat-content">
      <div class="stat-label">{{ label }}</div>
      <div class="stat-value">{{ value }}</div>
      <div v-if="hint" class="stat-hint">{{ hint }}</div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  icon: {
    type: String,
    required: true
  },
  label: {
    type: String,
    required: true
  },
  value: {
    type: [String, Number],
    required: true
  },
  hint: {
    type: String,
    default: null
  },
  severity: {
    type: String,
    default: 'info', // info, success, warn, danger
    validator: (value) => ['info', 'success', 'warn', 'danger'].includes(value)
  }
})

const severityClass = computed(() => `stat-card--${props.severity}`)
</script>

<style scoped>
.stat-card {
  display: flex;
  align-items: center;
  gap: var(--gp-spacing-md);
  padding: var(--gp-spacing-md);
  border-radius: var(--gp-radius-medium);
  border: 1px solid var(--gp-border-light);
  background: var(--gp-surface-white);
  transition: all 0.2s ease;
}

.stat-card:hover {
  transform: translateY(-2px);
  box-shadow: var(--gp-shadow-medium);
}

.stat-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 48px;
  height: 48px;
  border-radius: var(--gp-radius-medium);
  font-size: 1.5rem;
  flex-shrink: 0;
}

.stat-content {
  flex: 1;
  min-width: 0;
}

.stat-label {
  font-size: 0.875rem;
  color: var(--gp-text-secondary);
  font-weight: 500;
  margin-bottom: 4px;
}

.stat-value {
  font-size: 1.25rem;
  color: var(--gp-text-primary);
  font-weight: 700;
  line-height: 1.2;
}

.stat-hint {
  font-size: 0.75rem;
  color: var(--gp-text-muted);
  font-style: italic;
  margin-top: 2px;
}

/* Severity variants */
.stat-card--info .stat-icon {
  background: var(--gp-info-50);
  color: var(--gp-info-700);
}

.stat-card--success .stat-icon {
  background: var(--gp-success-50);
  color: var(--gp-success-700);
}

.stat-card--warn .stat-icon {
  background: var(--gp-warn-50);
  color: var(--gp-warn-700);
}

.stat-card--danger .stat-icon {
  background: var(--gp-danger-50);
  color: var(--gp-danger-700);
}

/* Dark Mode */
.p-dark .stat-card {
  background: var(--gp-surface-dark);
  border-color: var(--gp-border-medium);
}

.p-dark .stat-card:hover {
  box-shadow: var(--gp-shadow-medium);
}

.p-dark .stat-card--info .stat-icon {
  background: var(--gp-info-900);
  color: var(--gp-info-300);
}

.p-dark .stat-card--success .stat-icon {
  background: var(--gp-success-900);
  color: var(--gp-success-300);
}

.p-dark .stat-card--warn .stat-icon {
  background: var(--gp-warn-900);
  color: var(--gp-warn-300);
}

.p-dark .stat-card--danger .stat-icon {
  background: var(--gp-danger-900);
  color: var(--gp-danger-300);
}

.p-dark .stat-label {
  color: var(--gp-text-secondary);
}

.p-dark .stat-value {
  color: var(--gp-text-primary);
}

.p-dark .stat-hint {
  color: var(--gp-text-muted);
}

/* Mobile Responsive */
@media (max-width: 768px) {
  .stat-card {
    padding: var(--gp-spacing-sm);
    gap: var(--gp-spacing-sm);
  }

  .stat-icon {
    width: 40px;
    height: 40px;
    font-size: 1.25rem;
  }

  .stat-label {
    font-size: 0.8rem;
  }

  .stat-value {
    font-size: 1.1rem;
  }

  .stat-hint {
    font-size: 0.7rem;
  }
}
</style>
