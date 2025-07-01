<template>
  <div class="gp-app-layout" :class="layoutClasses">
    <Toast />
    
    <!-- Navbar Slot -->
    <header class="gp-app-navbar">
      <slot name="navbar">
        <AppNavbar />
      </slot>
    </header>
    
    <!-- Main Content -->
    <main class="gp-app-main">
      <slot />
    </main>
    
    <!-- Footer Slot (optional) -->
    <footer v-if="$slots.footer" class="gp-app-footer">
      <slot name="footer" />
    </footer>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import Toast from 'primevue/toast'
import AppNavbar from './AppNavbar.vue'

const props = defineProps({
  variant: {
    type: String,
    default: 'default',
    validator: (value) => ['default', 'app', 'minimal'].includes(value)
  },
  fullHeight: {
    type: Boolean,
    default: true
  },
  padding: {
    type: String,
    default: 'default',
    validator: (value) => ['none', 'small', 'default', 'large'].includes(value)
  }
})

const layoutClasses = computed(() => ({
  [`gp-app-layout--${props.variant}`]: props.variant !== 'default',
  'gp-app-layout--full-height': props.fullHeight,
  [`gp-app-layout--padding-${props.padding}`]: props.padding !== 'default'
}))
</script>

<style scoped>
/* Base Layout */
.gp-app-layout {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
  background: var(--gp-surface-light);
}

.gp-app-layout--full-height {
  min-height: 100vh;
}

/* Navbar */
.gp-app-navbar {
  flex-shrink: 0;
  z-index: 1000;
  height: 60px;
  background: var(--gp-surface-white);
  border-bottom: 1px solid var(--gp-border-light);
  box-shadow: var(--gp-shadow-light);
}

/* Main Content */
.gp-app-main {
  flex: 1;
  display: flex;
  flex-direction: column;
}

/* Default padding */
.gp-app-layout .gp-app-main {
  padding: var(--gp-spacing-lg);
}

/* Padding Variants */
.gp-app-layout--padding-none .gp-app-main {
  padding: 0;
}

.gp-app-layout--padding-small .gp-app-main {
  padding: var(--gp-spacing-md);
}

.gp-app-layout--padding-large .gp-app-main {
  padding: var(--gp-spacing-xl);
}

/* Footer */
.gp-app-footer {
  flex-shrink: 0;
  background: var(--gp-surface-white);
  border-top: 1px solid var(--gp-border-light);
  padding: var(--gp-spacing-md) var(--gp-spacing-lg);
}

/* Layout Variants */
.gp-app-layout--app .gp-app-main {
  background: var(--gp-surface-white);
}

.gp-app-layout--minimal .gp-app-navbar {
  border-bottom: none;
  box-shadow: none;
}

.gp-app-layout--minimal .gp-app-main {
  background: transparent;
}

/* Dark Mode */
.p-dark .gp-app-navbar {
  background: var(--gp-surface-dark);
  border-bottom-color: var(--gp-border-dark);
}

.p-dark .gp-app-layout {
  background: var(--gp-surface-darker);
}

.p-dark .gp-app-layout--app .gp-app-main {
  background: var(--gp-surface-dark);
}

.p-dark .gp-app-footer {
  background: var(--gp-surface-dark);
  border-top-color: var(--gp-border-dark);
}

/* Responsive */
@media (max-width: 768px) {
  .gp-app-layout .gp-app-main {
    padding: var(--gp-spacing-md);
  }

  .gp-app-layout--padding-large .gp-app-main {
    padding: var(--gp-spacing-lg);
  }

  .gp-app-footer {
    padding: var(--gp-spacing-sm) var(--gp-spacing-md);
  }
}

@media (max-width: 480px) {
  .gp-app-layout .gp-app-main {
    padding: var(--gp-spacing-sm);
  }

  .gp-app-layout--padding-small .gp-app-main {
    padding: var(--gp-spacing-xs);
  }
}

/* Scrolling behavior - removed to prevent layout issues */

/* Print styles */
@media print {
  .gp-app-navbar,
  .gp-app-footer {
    display: none;
  }
  
  .gp-app-layout {
    height: auto;
    min-height: auto;
  }
  
  .gp-app-main {
    padding: 0;
    overflow: visible;
  }
}
</style>