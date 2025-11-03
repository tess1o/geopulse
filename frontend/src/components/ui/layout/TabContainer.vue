<template>
  <div class="gp-tab-container" :class="containerClasses">
    <!-- Custom Tab Navigation -->
    <div class="p-tabmenu p-component gp-tab-menu" :class="tabMenuClasses" v-if="tabs && tabs.length > 0">
      <div class="p-tabmenu-tablist">
        <div 
          v-for="(tab, index) in tabs" 
          :key="tab.key || index"
          class="p-tabmenu-item"
          :class="{ 'p-tabmenu-item-active': index === activeIndex }"
          @click="handleTabClick(index)"
        >
          <div class="p-tabmenu-item-link">
            <i v-if="tab.icon" :class="['p-tabmenu-item-icon', tab.icon]"></i>
            <span class="p-tabmenu-item-label">{{ tab.label }}</span>
            <Badge v-if="tab.badge" :value="tab.badge" :severity="tab.badgeType || 'info'" class="p-tabmenu-item-badge"></Badge>
          </div>
        </div>
      </div>
    </div>
    <div class="gp-tab-content" :class="contentClasses">
      <slot />
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import Badge from 'primevue/badge'

const props = defineProps({
  tabs: {
    type: Array,
    required: true
  },
  activeIndex: {
    type: Number,
    default: 0
  },
  variant: {
    type: String,
    default: 'default',
    validator: (value) => ['default', 'compact', 'minimal'].includes(value)
  },
  padding: {
    type: String,
    default: 'default',
    validator: (value) => ['none', 'small', 'default', 'large'].includes(value)
  },
  fullHeight: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['tab-change'])

const containerClasses = computed(() => ({
  [`gp-tab-container--${props.variant}`]: props.variant !== 'default',
  [`gp-tab-container--padding-${props.padding}`]: props.padding !== 'default',
  'gp-tab-container--full-height': props.fullHeight
}))

const tabMenuClasses = computed(() => ({
  [`gp-tab-menu--${props.variant}`]: props.variant !== 'default'
}))

const contentClasses = computed(() => ({
  [`gp-tab-content--padding-${props.padding}`]: props.padding !== 'default',
  'gp-tab-content--full-height': props.fullHeight
}))

const handleTabClick = (index) => {
  emit('tab-change', { index })
}
</script>

<style scoped>
/* Base Tab Container */
.gp-tab-container {
  display: flex;
  flex-direction: column;
  height: 100%;
}

/* Tab Content */
.gp-tab-content {
  flex: 1;
  padding: var(--gp-spacing-xl) 0;
  overflow: visible;
  background: var(--gp-surface-white);
  border: 1px solid var(--gp-border-light);
  border-top: none;
  border-radius: 0 0 var(--gp-radius-large) var(--gp-radius-large);
}

/* Padding Variants */
.gp-tab-content--padding-none {
  padding: 0;
}

.gp-tab-content--padding-small {
  padding: var(--gp-spacing-md);
}

.gp-tab-content--padding-large {
  padding: var(--gp-spacing-xxl, 2rem);
}

/* Full Height */
.gp-tab-container--full-height {
  height: 100vh;
}

.gp-tab-content--full-height {
  height: 100%;
  overflow: hidden;
}

/* Container Variants */
.gp-tab-container--compact .gp-tab-content {
  padding: var(--gp-spacing-lg);
}

.gp-tab-container--minimal .gp-tab-content {
  padding: var(--gp-spacing-md);
}

/* Responsive */
@media (max-width: 768px) {
  .gp-tab-content {
    padding: var(--gp-spacing-lg);
  }

  .gp-tab-content--padding-large {
    padding: var(--gp-spacing-lg);
  }
}

@media (max-width: 480px) {
  .gp-tab-content {
    padding: var(--gp-spacing-md);
  }

  .gp-tab-content--padding-small {
    padding: var(--gp-spacing-sm);
  }
}
</style>

<style>
/* Global Tab Menu Styling */
.gp-tab-menu {
  border: 1px solid var(--gp-border-light);
  border-bottom: 1px solid var(--gp-border-light);
  background: var(--gp-surface-white);
  flex-shrink: 0;
  width: 100%;
  max-width: 100%;
  margin: 0;
  padding: 0;
  box-sizing: border-box;
  border-radius: var(--gp-radius-large) var(--gp-radius-large) 0 0;
}

.gp-tab-menu .p-tabmenu-tablist {
  background: transparent;
  border: none;
  padding: 0 var(--gp-spacing-xl);
  display: flex;
  gap: var(--gp-spacing-sm);
  width: 100%;
  max-width: 100%;
  overflow: hidden;
}

/* Override overflow for mobile/tablet */
@media (max-width: 1024px) {
  .gp-tab-menu .p-tabmenu-tablist {
    overflow: visible;
    overflow-x: auto;
    overflow-y: hidden;
  }
}

.gp-tab-menu .p-tabmenu-item {
  margin: 0;
  flex-shrink: 0;
}

.gp-tab-menu .p-tabmenu-item .p-tabmenu-item-link {
  background-color: var(--gp-surface-light);
  border-radius: var(--gp-radius-medium) var(--gp-radius-medium) 0 0;
  padding: var(--gp-spacing-sm) var(--gp-spacing-lg);
  transition: all 0.2s cubic-bezier(0.25, 0.8, 0.25, 1);
  border: 1px solid var(--gp-border-light);
  border-bottom: none;
  color: var(--gp-text-secondary);
  font-weight: 500;
  font-size: 0.875rem;
  position: relative;
  top: 1px;
  white-space: nowrap;
}

.gp-tab-menu .p-tabmenu-item:not(.p-tabmenu-item-active) .p-tabmenu-item-link:hover {
  background-color: var(--gp-surface-white);
  border-color: var(--gp-border-medium);
  color: var(--gp-primary);
}

.gp-tab-menu .p-tabmenu-item.p-tabmenu-item-active .p-tabmenu-item-link {
  background-color: var(--gp-primary);
  color: white;
  font-weight: 600;
  border-color: var(--gp-primary);
  box-shadow: var(--gp-shadow-button);
  z-index: 1;
}

.gp-tab-menu .p-tabmenu-item.p-tabmenu-item-active .p-tabmenu-item-link .p-tabmenu-item-icon {
  color: white;
}

.gp-tab-menu .p-tabmenu-item.p-tabmenu-item-active .p-tabmenu-item-link:hover {
  background-color: var(--gp-primary-dark);
  border-color: var(--gp-primary-dark);
}

.gp-tab-menu .p-tabmenu-item.p-tabmenu-item-active .p-tabmenu-item-link:hover .p-tabmenu-item-icon {
  color: white;
}

.gp-tab-menu .p-tabmenu-item[data-p-disabled="true"] .p-tabmenu-item-link {
  opacity: 0.6;
  background-color: var(--gp-surface-gray);
  color: var(--gp-text-muted);
  cursor: not-allowed;
  border-color: var(--gp-border-light);
}

.gp-tab-menu .p-tabmenu-item-icon {
  margin-right: var(--gp-spacing-sm);
  font-size: 0.875rem;
}

.p-tabmenu-item-badge {
  margin-left: var(--gp-spacing-sm);
}

/* Compact Variant */
.gp-tab-menu--compact .p-tabmenu-tablist {
  padding: 0 var(--gp-spacing-lg);
}

.gp-tab-menu--compact .p-tabmenu-item .p-tabmenu-item-link {
  padding: var(--gp-spacing-sm) var(--gp-spacing-md);
  font-size: 0.8rem;
}

.gp-tab-menu--compact .p-tabmenu-item-icon {
  font-size: 0.8rem;
}

/* Minimal Variant */
.gp-tab-menu--minimal {
  border-bottom: 1px solid var(--gp-border-subtle);
}

.gp-tab-menu--minimal .p-tabmenu-tablist {
  padding: 0 var(--gp-spacing-md);
}

.gp-tab-menu--minimal .p-tabmenu-item .p-tabmenu-item-link {
  background: transparent;
  border: none;
  border-radius: 0;
  border-bottom: 2px solid transparent;
  color: var(--gp-text-secondary);
}

.gp-tab-menu--minimal .p-tabmenu-item:not(.p-tabmenu-item-active) .p-tabmenu-item-link:hover {
  background: var(--gp-surface-light);
  border-bottom-color: var(--gp-primary-light);
}

.gp-tab-menu--minimal .p-tabmenu-item.p-tabmenu-item-active .p-tabmenu-item-link {
  background: transparent;
  color: var(--gp-primary);
  border-bottom-color: var(--gp-primary);
  font-weight: 600;
  box-shadow: none;
}

/* Dark Mode styles are handled globally in style.css */

/* Responsive */
/* Tablet and mobile - enable wrapping instead of horizontal scrolling */
@media (max-width: 1024px) {
  .gp-tab-menu .p-tabmenu-tablist {
    padding: 0 var(--gp-spacing-lg);
    flex-wrap: wrap;
    gap: var(--gp-spacing-xs);
    align-items: flex-start;
  }

  .gp-tab-menu .p-tabmenu-item {
    flex-shrink: 0;
    margin-bottom: var(--gp-spacing-xs);
  }

  .gp-tab-menu .p-tabmenu-item .p-tabmenu-item-link {
    white-space: nowrap;
    min-width: auto;
  }
}

@media (max-width: 768px) {
  .gp-tab-menu .p-tabmenu-tablist {
    padding: 0 var(--gp-spacing-lg);
    gap: var(--gp-spacing-xs);
  }

  .gp-tab-menu .p-tabmenu-item .p-tabmenu-item-link {
    padding: var(--gp-spacing-sm) var(--gp-spacing-md);
    font-size: 0.8rem;
  }

  .gp-tab-menu .p-tabmenu-item-icon {
    font-size: 0.8rem;
    margin-right: var(--gp-spacing-xs);
  }
}

@media (max-width: 480px) {
  .gp-tab-menu .p-tabmenu-tablist {
    padding: 0 var(--gp-spacing-md);
    gap: var(--gp-spacing-xs);
  }

  .gp-tab-menu .p-tabmenu-item .p-tabmenu-item-link {
    padding: var(--gp-spacing-sm) var(--gp-spacing-md);
    font-size: 0.8rem;
    white-space: nowrap;
    min-height: 40px;
    display: flex;
    align-items: center;
  }

  .gp-tab-menu .p-tabmenu-item-icon {
    font-size: 0.8rem;
  }
}

/* iPhone 16 Pro Max and similar large phones */
@media (max-width: 480px) and (min-width: 430px) {
  .gp-tab-menu .p-tabmenu-item .p-tabmenu-item-link {
    padding: var(--gp-spacing-sm) var(--gp-spacing-lg);
    font-size: 0.875rem;
    min-height: 42px;
  }

  .gp-tab-menu .p-tabmenu-item-icon {
    font-size: 0.875rem;
  }
}

/* Animations */
.gp-tab-menu .p-tabmenu-item .p-tabmenu-item-link {
  transition: all 0.2s cubic-bezier(0.25, 0.8, 0.25, 1);
}

/* Focus states */
.gp-tab-menu .p-tabmenu-item .p-tabmenu-item-link:focus {
  outline: none;
}
</style>