<template>
  <div class="gp-dashboard-grid" :class="gridClasses">
    <slot />
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  columns: {
    type: [Number, String],
    default: 3,
    validator: (value) => {
      if (typeof value === 'number') {
        return value >= 1 && value <= 6
      }
      return ['auto', 'fit'].includes(value)
    }
  },
  gap: {
    type: String,
    default: 'default',
    validator: (value) => ['small', 'default', 'large'].includes(value)
  },
  variant: {
    type: String,
    default: 'default',
    validator: (value) => ['default', 'masonry', 'equal-height'].includes(value)
  },
  responsive: {
    type: Boolean,
    default: true
  },
  minItemWidth: {
    type: String,
    default: '300px'
  }
})

const gridClasses = computed(() => ({
  [`gp-dashboard-grid--${props.gap}-gap`]: props.gap !== 'default',
  [`gp-dashboard-grid--${props.variant}`]: props.variant !== 'default',
  'gp-dashboard-grid--responsive': props.responsive,
  [`gp-dashboard-grid--columns-${props.columns}`]: typeof props.columns === 'number',
  [`gp-dashboard-grid--${props.columns}`]: typeof props.columns === 'string'
}))
</script>

<style scoped>
/* Base Grid */
.gp-dashboard-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: var(--gp-spacing-xl);
  width: 100%;
}

/* Column Variants */
.gp-dashboard-grid--columns-1 { grid-template-columns: 1fr; }
.gp-dashboard-grid--columns-2 { grid-template-columns: repeat(2, minmax(0, 1fr)); }
.gp-dashboard-grid--columns-3 { grid-template-columns: repeat(3, minmax(0, 1fr)); }
.gp-dashboard-grid--columns-4 { grid-template-columns: repeat(4, minmax(0, 1fr)); }
.gp-dashboard-grid--columns-5 { grid-template-columns: repeat(5, minmax(0, 1fr)); }
.gp-dashboard-grid--columns-6 { grid-template-columns: repeat(6, minmax(0, 1fr)); }

.gp-dashboard-grid--auto {
  grid-template-columns: repeat(auto-fit, minmax(v-bind(minItemWidth), 1fr));
}

.gp-dashboard-grid--fit {
  grid-template-columns: repeat(auto-fit, minmax(v-bind(minItemWidth), 1fr));
}

/* Gap Variants */
.gp-dashboard-grid--small-gap {
  gap: var(--gp-spacing-md);
}

.gp-dashboard-grid--large-gap {
  gap: var(--gp-spacing-xxl, 2rem);
}

/* Grid Variants */
.gp-dashboard-grid--equal-height > * {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.gp-dashboard-grid--masonry {
  grid-template-rows: masonry;
  align-items: start;
}

/* Responsive Behavior */
.gp-dashboard-grid--responsive {
  /* Default: 3 columns */
}

@media (max-width: 1200px) {
  .gp-dashboard-grid--responsive.gp-dashboard-grid--columns-4,
  .gp-dashboard-grid--responsive.gp-dashboard-grid--columns-5,
  .gp-dashboard-grid--responsive.gp-dashboard-grid--columns-6 {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

/* Tablet: 2 columns for larger tablets only */
@media (max-width: 1024px) and (min-width: 950px) {
  .gp-dashboard-grid--responsive:not(.gp-dashboard-grid--columns-1) {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

/* Mobile: 1 column for phones and small tablets */
@media (max-width: 949px) {
  .gp-dashboard-grid--responsive {
    grid-template-columns: 1fr;
    gap: var(--gp-spacing-lg);
  }
  
  .gp-dashboard-grid--responsive.gp-dashboard-grid--small-gap {
    gap: var(--gp-spacing-md);
  }
  
  .gp-dashboard-grid--responsive.gp-dashboard-grid--large-gap {
    gap: var(--gp-spacing-lg);
  }
  
  .gp-dashboard-grid--auto,
  .gp-dashboard-grid--fit {
    grid-template-columns: 1fr;
  }
}

/* Items that need to span multiple columns */
.gp-dashboard-grid > .span-2 {
  grid-column: span 2;
}

.gp-dashboard-grid > .span-3 {
  grid-column: span 3;
}

.gp-dashboard-grid > .span-full {
  grid-column: 1 / -1;
}

/* Responsive span adjustments */
@media (max-width: 1024px) and (min-width: 950px) {
  .gp-dashboard-grid > .span-3 {
    grid-column: span 2;
  }
}

@media (max-width: 949px) {
  .gp-dashboard-grid > .span-2,
  .gp-dashboard-grid > .span-3 {
    grid-column: span 1;
  }
}

/* Loading placeholder grid items */
.gp-dashboard-grid > .gp-loading-placeholder {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 200px;
  background: var(--gp-surface-light);
  border-radius: var(--gp-radius-large);
  border: 1px solid var(--gp-border-light);
}

/* Dark Mode */
.p-dark .gp-dashboard-grid > .gp-loading-placeholder {
  background: var(--gp-surface-darker);
  border-color: var(--gp-border-dark);
}

/* Print styles */
@media print {
  .gp-dashboard-grid {
    display: block;
    gap: 0;
  }
  
  .gp-dashboard-grid > * {
    break-inside: avoid;
    margin-bottom: 1rem;
  }
}

/* Animation for grid items */
.gp-dashboard-grid > * {
  opacity: 0;
  transform: translateY(10px);
  animation: fadeInUp 0.4s ease forwards;
}

.gp-dashboard-grid > *:nth-child(1) { animation-delay: 0.1s; }
.gp-dashboard-grid > *:nth-child(2) { animation-delay: 0.2s; }
.gp-dashboard-grid > *:nth-child(3) { animation-delay: 0.3s; }
.gp-dashboard-grid > *:nth-child(4) { animation-delay: 0.4s; }
.gp-dashboard-grid > *:nth-child(5) { animation-delay: 0.5s; }
.gp-dashboard-grid > *:nth-child(6) { animation-delay: 0.6s; }

@keyframes fadeInUp {
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

/* Disable animations if user prefers reduced motion */
@media (prefers-reduced-motion: reduce) {
  .gp-dashboard-grid > * {
    animation: none;
    opacity: 1;
    transform: none;
  }
}
</style>