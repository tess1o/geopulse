<template>
  <Toolbar class="gp-app-navbar-toolbar gp-app-navbar-with-datepicker" :class="toolbarClasses">
    <template #start>
      <div class="gp-navbar-start">
        <AppNavigation :variant="navigationVariant" @navigate="handleNavigate"/>
        <div class="gp-navbar-logo">
          <router-link to="/" class="gp-navbar-logo-link">
            <span class="gp-navbar-logo-text">GeoPulse</span>
          </router-link>
        </div>
      </div>
    </template>

    <template #center>
      <slot name="center"/>
    </template>

    <template #end>
      <div class="gp-navbar-end">
        <!-- Custom end content -->
        <slot name="end-before"/>

        <!-- Date Picker -->
        <div class="gp-navbar-datepicker" :style="datePickerStyle">
          <DateRangePicker
              :variant="datePickerVariant"
              :size="datePickerSize"
              :showLabel="true"
              :label="datePickerLabel"
              inputVariant="filled"
              pickerId="navbar-date-selector"
              :class="datePickerClasses"
              @date-change="handleDateChange"
          />
        </div>

        <!-- Additional end content -->
        <slot name="end-after"/>
      </div>
    </template>
  </Toolbar>
</template>

<script setup>
import { computed } from 'vue'
import Toolbar from 'primevue/toolbar'
import AppNavigation from './AppNavigation.vue'
import DateRangePicker from '@/components/ui/DateRangePicker.vue'

const props = defineProps({
  variant: {
    type: String,
    default: 'default',
    validator: (value) => ['default', 'compact', 'minimal'].includes(value)
  },
  fixed: {
    type: Boolean,
    default: false
  },
  transparent: {
    type: Boolean,
    default: false
  },
  datePickerLabel: {
    type: String,
    default: 'Select Dates'
  },
  datePickerWidth: {
    type: String,
    default: '220px'
  }
})

const emit = defineEmits(['navigate', 'date-change'])

// Computed properties
const toolbarClasses = computed(() => ({
  [`gp-navbar--${props.variant}`]: props.variant !== 'default',
  'gp-navbar--fixed': props.fixed,
  'gp-navbar--transparent': props.transparent
}))

const navigationVariant = computed(() => {
  return props.variant === 'compact' ? 'compact' : 'default'
})

const datePickerSize = computed(() => {
  return props.variant === 'compact' ? 'small' : 'medium'
})

const datePickerVariant = computed(() => {
  return props.variant === 'compact' ? 'compact' : 'default'
})

const datePickerClasses = computed(() => ({
  'gp-datepicker--compact': props.variant === 'compact'
}))

const datePickerStyle = computed(() => ({
  width: props.datePickerWidth
}))

// Methods
const handleDateChange = (range) => {
  emit('date-change', range)
}

const handleNavigate = (item) => {
  emit('navigate', item)
}
</script>

<style scoped>

* {
  --p-datepicker-date-range-selected-background: rgba(59, 130, 246, 0.25);
  --p-datepicker-date-range-selected-color: var(--gp-text-primary);
}

/* Navbar Start Section */
.gp-navbar-start {
  display: flex;
  align-items: center;
  gap: var(--gp-spacing-lg);
}

/* Logo */
.gp-navbar-logo {
  flex-shrink: 0;
}

.gp-navbar-logo-link {
  text-decoration: none;
  color: inherit;
  display: flex;
  align-items: center;
  transition: color 0.2s ease;
}

.gp-navbar-logo-link:hover {
  color: var(--gp-primary);
}

.gp-navbar-logo-text {
  font-size: 1.25rem;
  font-weight: 700;
  color: var(--gp-primary);
  letter-spacing: -0.025em;
}

/* Navbar End Section */
.gp-navbar-end {
  display: flex;
  align-items: center;
  gap: var(--gp-spacing-lg);
}

/* Date Picker Section */
.gp-navbar-datepicker {
  flex-shrink: 0;
  position: relative;
}

.gp-datepicker-label {
  font-size: 0.8rem;
  font-weight: 500;
  color: var(--gp-text-secondary);
}

/* Navbar Variants */
.gp-navbar--compact .gp-navbar-start {
  gap: var(--gp-spacing-md);
}

.gp-navbar--compact .gp-navbar-end {
  gap: var(--gp-spacing-md);
}

.gp-navbar--compact .gp-navbar-logo-text {
  font-size: 1.125rem;
}

.gp-navbar--compact .gp-datepicker-label {
  font-size: 0.75rem;
}

.gp-navbar--minimal .gp-navbar-logo-text {
  font-weight: 600;
  color: var(--gp-text-primary);
}

/* Fixed Navbar */
.gp-navbar--fixed {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  z-index: 1000;
}

/* Transparent Navbar */
.gp-navbar--transparent {
  background: transparent !important;
  border-bottom: none !important;
  box-shadow: none !important;
}

/* Dark Mode */
.p-dark .gp-navbar-logo-text {
  color: var(--gp-primary-light);
}

.p-dark .gp-navbar--minimal .gp-navbar-logo-text {
  color: var(--gp-text-primary);
}

.p-dark .gp-datepicker-label {
  color: var(--gp-text-secondary);
}

/* Responsive */
@media (max-width: 1024px) {
  .gp-navbar-datepicker {
    order: -1;
  }

  .gp-navbar-end {
    flex-direction: row-reverse;
  }
}

@media (max-width: 768px) {
  .gp-navbar-start {
    gap: var(--gp-spacing-md);
  }

  .gp-navbar-end {
    gap: var(--gp-spacing-md);
  }

  .gp-navbar-logo-text {
    font-size: 1.125rem;
  }

  .gp-navbar-datepicker {
    min-width: 200px;
    flex: 1;
    max-width: 240px;
  }
}

@media (max-width: 640px) {
  .gp-navbar-start {
    gap: var(--gp-spacing-sm);
  }

  .gp-navbar-end {
    gap: var(--gp-spacing-sm);
  }

  .gp-navbar-logo-text {
    font-size: 1rem;
  }

  .gp-navbar-datepicker {
    min-width: 180px;
    flex: 1;
    max-width: 220px;
  }
}

@media (max-width: 480px) {
  .gp-navbar-logo-text {
    display: none;
  }

  .gp-navbar-datepicker {
    min-width: 180px;
    flex: 1;
    max-width: 220px;
  }
}

/* iPhone 16 Pro Max and similar large phones */
@media (max-width: 480px) and (min-width: 430px) {
  .gp-navbar-datepicker {
    min-width: 200px;
    max-width: 280px;
  }
}
</style>

<style>
/* Global Toolbar Overrides for DatePicker Navbar */
.gp-app-navbar-with-datepicker {
  background: var(--gp-surface-white) !important;
  border: none !important;
  border-bottom: 1px solid var(--gp-border-light) !important;
  border-radius: 0 !important;
  padding: 0 var(--gp-spacing-lg) !important;
  height: 60px !important;
  box-shadow: var(--gp-shadow-light) !important;
}

.gp-app-navbar-with-datepicker .p-toolbar-group-start,
.gp-app-navbar-with-datepicker .p-toolbar-group-center,
.gp-app-navbar-with-datepicker .p-toolbar-group-end {
  align-items: center;
  height: 100%;
}

/* DatePicker Customization */
.gp-navbar-datepicker .gp-datepicker {
  border-radius: var(--gp-radius-medium) !important;
  border: 1px solid var(--gp-border-light) !important;
  background: var(--gp-surface-white) !important;
  transition: all 0.2s ease !important;
}

.gp-navbar-datepicker .gp-datepicker:focus {
  border-color: var(--gp-primary) !important;
  box-shadow: 0 0 0 2px rgba(26, 86, 219, 0.1) !important;
}

.gp-navbar-datepicker .gp-datepicker:hover {
  border-color: var(--gp-primary-light) !important;
}

/* Compact DatePicker */
.gp-datepicker--compact {
  padding: var(--gp-spacing-xs) var(--gp-spacing-sm) !important;
  font-size: 0.8rem !important;
}

/* FloatLabel customization */
.gp-navbar-datepicker .p-floatlabel label {
  color: var(--gp-text-secondary) !important;
  font-size: 0.8rem !important;
  font-weight: 500 !important;
}

.gp-navbar-datepicker .p-floatlabel:focus-within label {
  color: var(--gp-primary) !important;
}

/* Compact variant */
.gp-navbar--compact.gp-app-navbar-with-datepicker {
  height: 50px !important;
  padding: 0 var(--gp-spacing-md) !important;
}

/* Minimal variant */
.gp-navbar--minimal.gp-app-navbar-with-datepicker {
  box-shadow: none !important;
  border-bottom: 1px solid var(--gp-border-subtle) !important;
}

/* Transparent variant */
.gp-navbar--transparent.gp-app-navbar-with-datepicker {
  background: transparent !important;
  border-bottom: none !important;
  box-shadow: none !important;
}

.gp-navbar--transparent .gp-navbar-datepicker .gp-datepicker {
  background: rgba(255, 255, 255, 0.9) !important;
  backdrop-filter: blur(8px) !important;
}

/* Dark mode */
.p-dark .gp-app-navbar-with-datepicker {
  background: var(--gp-surface-dark) !important;
  border-bottom-color: var(--gp-border-dark) !important;
}

.p-dark .gp-navbar--minimal.gp-app-navbar-with-datepicker {
  border-bottom-color: var(--gp-border-dark) !important;
}

.p-dark .gp-navbar-datepicker .gp-datepicker {
  background: var(--gp-surface-dark) !important;
  border-color: var(--gp-border-dark) !important;
  color: var(--gp-text-primary) !important;
}

.p-dark .gp-navbar-datepicker .gp-datepicker input {
  background: var(--gp-surface-dark) !important;
  border-color: var(--gp-border-dark) !important;
  color: var(--gp-text-primary) !important;
}

.p-dark .gp-navbar-datepicker .p-floatlabel label {
  color: var(--gp-text-secondary) !important;
}

.p-dark .gp-navbar-datepicker .p-floatlabel:focus-within label {
  color: var(--gp-primary-light) !important;
}

.p-dark .gp-navbar-datepicker .p-datepicker-trigger-icon {
  color: var(--gp-text-secondary) !important;
}

.p-dark .gp-navbar--transparent .gp-navbar-datepicker .gp-datepicker {
  background: rgba(0, 0, 0, 0.8) !important;
  border-color: var(--gp-border-dark) !important;
}

/* Dark mode for DatePicker popup */
.p-dark .p-datepicker {
  background: var(--gp-surface-dark) !important;
  border-color: var(--gp-border-dark) !important;
  color: var(--gp-text-primary) !important;
}

.p-dark .p-datepicker .p-datepicker-header {
  background: var(--gp-surface-dark) !important;
  border-bottom-color: var(--gp-border-dark) !important;
  color: var(--gp-text-primary) !important;
}

.p-dark .p-datepicker .p-datepicker-calendar td span {
  color: var(--gp-text-primary) !important;
}

.p-dark .p-datepicker .p-datepicker-calendar td span:hover {
  background: var(--gp-primary-light) !important;
  color: white !important;
}

/* Fix today's date color to be more distinct from selected dates */
.p-dark .p-datepicker .p-datepicker-calendar td.p-datepicker-today span {
  background: var(--gp-secondary) !important;
  color: white !important;
  font-weight: 600 !important;
}

/* Fix selected date styling in dark mode */
.p-dark .p-datepicker .p-datepicker-calendar td.p-datepicker-selected span {
  background: var(--gp-primary) !important;
  color: white !important;
  font-weight: 600 !important;
}

/* Date range highlighting is now handled in global styles with correct PrimeVue classes */

/* Responsive */
@media (max-width: 768px) {
  .gp-app-navbar-with-datepicker {
    padding: 0 var(--gp-spacing-md) !important;
  }
}

@media (max-width: 480px) {
  .gp-app-navbar-with-datepicker {
    padding: 0 var(--gp-spacing-sm) !important;
  }

  .gp-navbar-datepicker .gp-datepicker {
    font-size: 0.8rem !important;
    padding: var(--gp-spacing-sm) !important;
    min-height: 44px;
    width: 100% !important;
  }

  .gp-navbar-datepicker .p-floatlabel label {
    font-size: 0.75rem !important;
  }
}

/* Animation for fixed navbar */
.gp-navbar--fixed.gp-app-navbar-with-datepicker {
  transition: all 0.3s cubic-bezier(0.25, 0.8, 0.25, 1);
}

/* Focus states */
/*.gp-app-navbar-with-datepicker:focus-within {
  outline: 2px solid var(--gp-primary);
  outline-offset: -2px;
}*/

/* DatePicker overlay z-index fix */
.gp-navbar-datepicker :deep(.p-datepicker) {
  z-index: 1100 !important;
}
</style>