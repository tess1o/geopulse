<template>
  <div class="gp-card" :class="cardClasses">
    <header v-if="title || $slots.header" class="gp-card-header">
      <slot name="header">
        <h3 class="gp-card-title">{{ title }}</h3>
        <span v-if="period" class="gp-period-badge">{{ period }}</span>
      </slot>
    </header>
    <main class="gp-card-content">
      <slot />
    </main>
    <footer v-if="$slots.footer" class="gp-card-footer">
      <slot name="footer" />
    </footer>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  title: {
    type: String,
    default: ''
  },
  period: {
    type: String,
    default: ''
  },
  variant: {
    type: String,
    default: 'default',
    validator: (value) => ['default', 'highlighted', 'subtle'].includes(value)
  },
  size: {
    type: String,
    default: 'default',
    validator: (value) => ['small', 'default', 'large'].includes(value)
  }
})

const cardClasses = computed(() => ({
  [`gp-card--${props.variant}`]: props.variant !== 'default',
  [`gp-card--${props.size}`]: props.size !== 'default'
}))
</script>

<style scoped>
/* Base Card Styles */
.gp-card {
  background: var(--gp-surface-white);
  border: 1px solid var(--gp-border-light);
  border-radius: var(--gp-radius-large);
  box-shadow: var(--gp-shadow-card);
  overflow: hidden;
  transition: all 0.2s ease;
  max-width: 100%;
  box-sizing: border-box;
}

.gp-card:hover {
  box-shadow: var(--gp-shadow-card-hover);
  transform: translateY(-1px);
}

/* Header */
.gp-card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--gp-spacing-lg) var(--gp-spacing-lg) var(--gp-spacing-md);
  border-bottom: 1px solid var(--gp-border-light);
  background: var(--gp-surface-light);
  max-width: 100%;
  box-sizing: border-box;
}

.gp-card-title {
  font-size: 0.875rem;
  font-weight: 700;
  color: var(--gp-text-primary);
  margin: 0;
  letter-spacing: 0.05em;
  text-transform: uppercase;
}

.gp-period-badge {
  background: var(--gp-primary);
  color: white;
  font-size: 0.75rem;
  font-weight: 600;
  padding: var(--gp-spacing-xs) var(--gp-spacing-sm);
  border-radius: var(--gp-radius-medium);
  text-transform: uppercase;
  letter-spacing: 0.025em;
}

/* Content */
.gp-card-content {
  padding: var(--gp-spacing-lg);
  flex: 1;
  display: flex;
  flex-direction: column;
  max-width: 100%;
  box-sizing: border-box;
  overflow: hidden;
}

.gp-card-content > * {
  max-width: 100%;
  box-sizing: border-box;
}

/* Footer */
.gp-card-footer {
  padding: var(--gp-spacing-md) var(--gp-spacing-lg);
  border-top: 1px solid var(--gp-border-light);
  background: var(--gp-surface-light);
}

/* Variants */
.gp-card--highlighted {
  border-color: var(--gp-primary);
  box-shadow: var(--gp-shadow-card-highlighted);
}

.gp-card--highlighted .gp-card-header {
  background: var(--gp-timeline-blue);
}

.gp-card--subtle {
  background: var(--gp-surface-light);
  border-color: var(--gp-border-subtle);
  box-shadow: var(--gp-shadow-subtle);
}

.gp-card--subtle:hover {
  box-shadow: var(--gp-shadow-card);
}

/* Sizes */
.gp-card--small .gp-card-header {
  padding: var(--gp-spacing-md) var(--gp-spacing-md) var(--gp-spacing-sm);
}

.gp-card--small .gp-card-content {
  padding: var(--gp-spacing-md);
}

.gp-card--small .gp-card-footer {
  padding: var(--gp-spacing-sm) var(--gp-spacing-md);
}

.gp-card--small .gp-card-title {
  font-size: 0.8rem;
}

.gp-card--large .gp-card-header {
  padding: var(--gp-spacing-xl) var(--gp-spacing-xl) var(--gp-spacing-lg);
}

.gp-card--large .gp-card-content {
  padding: var(--gp-spacing-xl);
}

.gp-card--large .gp-card-footer {
  padding: var(--gp-spacing-lg) var(--gp-spacing-xl);
}

.gp-card--large .gp-card-title {
  font-size: 1rem;
}

/* Dark Mode */
.p-dark .gp-card {
  background: var(--gp-surface-dark);
  border-color: var(--gp-border-dark);
}

.p-dark .gp-card-header {
  background: var(--gp-surface-darker);
  border-bottom-color: var(--gp-border-dark);
}

.p-dark .gp-card-footer {
  background: var(--gp-surface-darker);
  border-top-color: var(--gp-border-dark);
}

.p-dark .gp-card-title {
  color: var(--gp-text-primary);
}

.p-dark .gp-card--highlighted .gp-card-header {
  background: var(--gp-timeline-blue);
}

.p-dark .gp-card--subtle {
  background: var(--gp-surface-darker);
  border-color: var(--gp-border-subtle);
}

/* Responsive */
@media (max-width: 640px) {
  .gp-card-header {
    padding: var(--gp-spacing-md);
    flex-direction: column;
    align-items: flex-start;
    gap: var(--gp-spacing-sm);
  }

  .gp-card-content {
    padding: var(--gp-spacing-md);
  }

  .gp-card-footer {
    padding: var(--gp-spacing-sm) var(--gp-spacing-md);
  }

  .gp-card-title {
    font-size: 0.8rem;
  }

  .gp-period-badge {
    font-size: 0.7rem;
    padding: var(--gp-spacing-xs);
  }
}

/* No Data States */
.gp-card .no-data-container {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 120px;
  padding: var(--gp-spacing-lg);
}

.gp-card .no-data-content {
  text-align: center;
}

.gp-card .no-data-icon {
  font-size: 2rem;
  color: var(--gp-text-muted);
  margin-bottom: var(--gp-spacing-md);
  display: block;
}

.gp-card .no-data-title {
  font-size: 1rem;
  font-weight: 600;
  color: var(--gp-text-secondary);
  margin: 0 0 var(--gp-spacing-sm);
}

.gp-card .no-data-message {
  font-size: 0.875rem;
  color: var(--gp-text-muted);
  margin: 0;
  max-width: 250px;
  line-height: 1.4;
}
</style>