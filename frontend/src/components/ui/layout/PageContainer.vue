<template>
  <div class="gp-page-container" :class="containerClasses">
    <!-- Page Header -->
    <header v-if="title || subtitle || $slots.header || $slots.actions" class="gp-page-header">
      <div class="gp-page-header-content">
        <slot name="header">
          <div class="gp-page-header-text">
            <h1 v-if="title" class="gp-page-title">{{ title }}</h1>
            <p v-if="subtitle" class="gp-page-subtitle">{{ subtitle }}</p>
          </div>
        </slot>
        <div v-if="$slots.actions" class="gp-page-actions">
          <slot name="actions" />
        </div>
      </div>
      <div v-if="$slots.tabs" class="gp-page-tabs">
        <slot name="tabs" />
      </div>
    </header>

    <!-- Page Content -->
    <main class="gp-page-content" :class="contentClasses">
      <slot />
    </main>

    <!-- Page Footer -->
    <footer v-if="$slots.footer" class="gp-page-footer">
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
  subtitle: {
    type: String,
    default: ''
  },
  variant: {
    type: String,
    default: 'default',
    validator: (value) => ['default', 'card', 'fullwidth', 'centered'].includes(value)
  },
  maxWidth: {
    type: String,
    default: 'default',
    validator: (value) => ['none', 'small', 'default', 'large', 'xlarge'].includes(value)
  },
  padding: {
    type: String,
    default: 'default',
    validator: (value) => ['none', 'small', 'default', 'large'].includes(value)
  },
  scrollable: {
    type: Boolean,
    default: false
  },
  loading: {
    type: Boolean,
    default: false
  }
})

const containerClasses = computed(() => ({
  [`gp-page-container--${props.variant}`]: props.variant !== 'default',
  [`gp-page-container--max-width-${props.maxWidth}`]: props.maxWidth !== 'default',
  [`gp-page-container--padding-${props.padding}`]: props.padding !== 'default',
  'gp-page-container--scrollable': props.scrollable,
  'gp-page-container--loading': props.loading
}))

const contentClasses = computed(() => ({
  'gp-page-content--scrollable': props.scrollable
}))
</script>

<style scoped>
/* Base Container */
.gp-page-container {
  display: flex;
  flex-direction: column;
  height: 100%;
  max-width: 1200px;
  margin: 0 auto;
  padding: var(--gp-spacing-lg);
  box-sizing: border-box;
  width: 100%;
}

.gp-page-container > * {
  max-width: 100%;
  box-sizing: border-box;
}

/* Container Variants */
.gp-page-container--card {
  background: var(--gp-surface-card);
  border-radius: var(--gp-radius-large);
  border: 1px solid var(--gp-border);
  box-shadow: var(--gp-shadow-card);
  margin: var(--gp-spacing-lg);
}

.gp-page-container--fullwidth {
  max-width: none;
  padding-left: 0;
  padding-right: 0;
}

.gp-page-container--centered {
  align-items: center;
  justify-content: center;
  text-align: center;
}

/* Max Width Variants */
.gp-page-container--max-width-none {
  max-width: none;
}

.gp-page-container--max-width-small {
  max-width: 600px;
}

.gp-page-container--max-width-large {
  max-width: 1400px;
}

.gp-page-container--max-width-xlarge {
  max-width: 1600px;
}

/* Padding Variants */
.gp-page-container--padding-none {
  padding: 0;
}

.gp-page-container--padding-small {
  padding: var(--gp-spacing-md);
}

.gp-page-container--padding-large {
  padding: var(--gp-spacing-xl);
}

/* Card variant padding adjustment */
.gp-page-container--card.gp-page-container--padding-none {
  padding: 0;
  margin: 0;
}

.gp-page-container--card.gp-page-container--padding-small {
  padding: var(--gp-spacing-md);
}

.gp-page-container--card.gp-page-container--padding-large {
  padding: var(--gp-spacing-xl);
}

/* Page header: the .gp-page-header* classes are shared and live in styles/components.css. */
.gp-page-tabs {
  margin-top: var(--gp-spacing-lg);
  border-top: 1px solid var(--gp-border);
  padding-top: var(--gp-spacing-lg);
}

/* Page Content */
.gp-page-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  max-width: 100%;
  box-sizing: border-box;
  overflow-x: clip;
}

.gp-page-content > * {
  max-width: 100%;
  box-sizing: border-box;
}

.gp-page-content--scrollable {
  overflow-y: auto;
  overflow-x: hidden;
}

/* Page Footer */
.gp-page-footer {
  flex-shrink: 0;
  margin-top: var(--gp-spacing-lg);
  padding-top: var(--gp-spacing-lg);
  border-top: 1px solid var(--gp-border);
}

/* Scrollable Container */
.gp-page-container--scrollable {
  height: 100vh;
  overflow: hidden;
}

.gp-page-container--scrollable .gp-page-content {
  overflow-y: auto;
  overflow-x: hidden;
}

/* Loading State */
.gp-page-container--loading {
  position: relative;
  pointer-events: none;
  opacity: 0.7;
}

.gp-page-container--loading::after {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: color-mix(in srgb, var(--gp-surface-ground) 80%, transparent);
  z-index: 9999;
}

/* Responsive Design */
@media (max-width: 1024px) {
  .gp-page-container {
    max-width: none;
    padding: var(--gp-spacing-md);
  }

  .gp-page-container--card {
    margin: var(--gp-spacing-md);
  }
}

@media (max-width: 768px) {
  .gp-page-container {
    padding: var(--gp-spacing-sm);
  }

  .gp-page-container--card {
    margin: var(--gp-spacing-sm);
    border-radius: var(--gp-radius-medium);
  }

  .gp-page-tabs {
    margin-top: var(--gp-spacing-md);
    padding-top: var(--gp-spacing-md);
  }
}

@media (max-width: 480px) {
  .gp-page-container {
    padding: var(--gp-spacing-xs);
  }

  .gp-page-container--card {
    margin: var(--gp-spacing-xs);
  }
}

/* Print Styles */
@media print {
  .gp-page-container {
    max-width: none;
    padding: 0;
    box-shadow: none;
    border: none;
  }

  .gp-page-container--card {
    background: white;
    border: none;
    box-shadow: none;
    margin: 0;
  }

  .gp-page-tabs {
    display: none;
  }
}

/* Accessibility */
@media (prefers-reduced-motion: reduce) {
  .gp-page-container,
  .gp-page-content {
    transition: none;
  }
}
</style>
