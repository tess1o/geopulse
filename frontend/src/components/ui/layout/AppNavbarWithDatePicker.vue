<template>
  <Toolbar class="gp-app-navbar-toolbar gp-app-navbar-with-datepicker" :class="toolbarClasses">
    <template #start>
      <div class="gp-navbar-start">
        <AppNavigation :variant="navigationVariant" @navigate="handleNavigate"/>
        <div class="gp-navbar-logo">
          <router-link to="/" class="gp-navbar-logo-link">
            <span class="gp-navbar-logo-text">GeoPulse</span>
            <span v-if="demoModeEnabled" class="gp-navbar-demo-badge">{{ t('ui.appNavbar.demoBadge') }}</span>
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
              :manualInput="true"
              :class="datePickerClasses"
              @date-change="handleDateChange"
          />
        </div>
        <NotificationBell />

        <!-- Additional end content -->
        <slot name="end-after"/>
      </div>
    </template>
  </Toolbar>
</template>

<script setup>
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { t as translate } from '@/locales'
import { storeToRefs } from 'pinia'
import Toolbar from 'primevue/toolbar'
import AppNavigation from './AppNavigation.vue'
import NotificationBell from './NotificationBell.vue'
import DateRangePicker from '@/components/ui/DateRangePicker.vue'
import { useAuthStore } from '@/stores/auth'

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
    default: () => translate('ui.dateRangePicker.labelDefault')
  },
  datePickerWidth: {
    type: String,
    default: '260px'
  }
})

const emit = defineEmits(['navigate', 'date-change'])
const { t } = useI18n()
const authStore = useAuthStore()
const { demoModeEnabled } = storeToRefs(authStore)

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
  '--gp-navbar-datepicker-width': props.datePickerWidth
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
  gap: 0.5rem;
  transition: color 0.2s ease;
}

.gp-navbar-logo-link:hover {
  color: var(--gp-primary);
}

.gp-navbar-logo-text {
  font-size: 1.25rem;
  font-weight: 700;
  color: var(--gp-primary-text);
  letter-spacing: 0;
}

.gp-navbar-demo-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 0.125rem 0.375rem;
  border: 1px solid #b91c1c;
  border-radius: 4px;
  background: #dc2626;
  color: #fff;
  font-size: 0.625rem;
  font-weight: 800;
  line-height: 1;
  letter-spacing: 0;
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
  width: var(--gp-navbar-datepicker-width);
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

/* Responsive */
@media (max-width: 1024px) {
  .gp-navbar-datepicker {
    order: -1;
  }

  .gp-navbar-end {
    flex-direction: row-reverse;
  }
}

@media (min-width: 769px) and (max-width: 971px) {
  .gp-navbar-start {
    gap: var(--gp-spacing-md);
    flex-shrink: 0;
  }

  .gp-navbar-end {
    gap: 0.5rem;
    min-width: 0;
    flex: 1 1 auto;
    justify-content: flex-end;
  }

  .gp-navbar-logo-text {
    font-size: 1.125rem;
  }

  .gp-navbar-datepicker {
    width: 220px;
    flex: 0 0 220px;
  }
}

@media (max-width: 768px), (max-height: 520px) and (pointer: coarse) {
  .gp-navbar-start {
    gap: var(--gp-spacing-sm);
    flex-shrink: 0;
  }

  .gp-navbar-end {
    gap: 0.375rem;
    min-width: 0;
    flex: 1;
    justify-content: flex-end;
  }

  .gp-navbar-logo-text {
    font-size: 1.125rem;
  }

  .gp-navbar-datepicker {
    min-width: 0;
    width: clamp(190px, 32vw, 240px);
    flex: 0 1 clamp(190px, 32vw, 240px);
  }
}

@media (max-width: 640px) {
  .gp-navbar-start {
    gap: var(--gp-spacing-sm);
  }

  .gp-navbar-end {
    gap: 0.25rem;
  }

  .gp-navbar-logo-text {
    font-size: 1rem;
  }

  .gp-navbar-datepicker {
    width: clamp(195px, 50vw, 225px);
    flex-basis: clamp(195px, 50vw, 225px);
  }
}

@media (max-width: 480px) {
  .gp-navbar-logo-text {
    display: none;
  }

  .gp-navbar-datepicker {
    width: clamp(205px, 55vw, 220px);
    flex-basis: clamp(205px, 55vw, 220px);
  }
}

/* iPhone 16 Pro Max and similar large phones */
@media (max-width: 480px) and (min-width: 430px) {
  .gp-navbar-datepicker {
    width: clamp(215px, 52vw, 230px);
    flex-basis: clamp(215px, 52vw, 230px);
  }
}
</style>

<style>
/* Global Toolbar Overrides for DatePicker Navbar */
.gp-app-navbar-toolbar.gp-app-navbar-with-datepicker {
  background: var(--gp-surface-card);
  border: none;
  border-bottom: 1px solid var(--gp-border);
  border-radius: 0;
  padding: 0 var(--gp-spacing-lg);
  padding-left: calc(var(--gp-spacing-lg) + env(safe-area-inset-left));
  padding-right: calc(var(--gp-spacing-lg) + env(safe-area-inset-right));
  height: 60px;
  box-shadow: var(--gp-shadow-light);
}

.gp-app-navbar-with-datepicker .p-toolbar-group-start,
.gp-app-navbar-with-datepicker .p-toolbar-group-center,
.gp-app-navbar-with-datepicker .p-toolbar-group-end {
  align-items: center;
  height: 100%;
}

@media (min-width: 769px) and (max-width: 971px) {
  .gp-app-navbar-toolbar.gp-app-navbar-with-datepicker {
    flex-wrap: nowrap;
    padding: 0 var(--gp-spacing-md);
    padding-left: calc(var(--gp-spacing-md) + env(safe-area-inset-left));
    padding-right: calc(var(--gp-spacing-md) + env(safe-area-inset-right));
  }

  .gp-app-navbar-with-datepicker .p-toolbar-group-start,
  .gp-app-navbar-with-datepicker .p-toolbar-group-end {
    min-width: 0;
  }

  .gp-app-navbar-with-datepicker .p-toolbar-group-end {
    flex: 1 1 auto;
    justify-content: flex-end;
  }
}

/* FloatLabel customization */
.gp-navbar-datepicker .p-floatlabel label {
  color: var(--gp-text-secondary);
  font-size: 0.8rem;
  font-weight: 500;
}

.gp-navbar-datepicker .p-floatlabel:focus-within label {
  color: var(--gp-primary-text);
}

/* Compact variant */
.gp-navbar--compact.gp-app-navbar-with-datepicker {
  height: 50px;
  padding: 0 var(--gp-spacing-md);
  padding-left: calc(var(--gp-spacing-md) + env(safe-area-inset-left));
  padding-right: calc(var(--gp-spacing-md) + env(safe-area-inset-right));
}

/* Minimal variant */
.gp-navbar--minimal.gp-app-navbar-with-datepicker {
  box-shadow: none;
  border-bottom: 1px solid var(--gp-border-subtle);
}

/* Transparent variant */
.gp-navbar--transparent.gp-app-navbar-with-datepicker {
  background: transparent;
  border-bottom: none;
  box-shadow: none;
}

@media (max-width: 768px), (max-height: 520px) and (pointer: coarse) {
  .gp-app-navbar-toolbar.gp-app-navbar-with-datepicker {
    padding: 0 var(--gp-spacing-sm);
    padding-left: calc(var(--gp-spacing-sm) + env(safe-area-inset-left));
    padding-right: calc(var(--gp-spacing-sm) + env(safe-area-inset-right));
  }

  .gp-app-navbar-with-datepicker .p-toolbar-group-start,
  .gp-app-navbar-with-datepicker .p-toolbar-group-end {
    min-width: 0;
  }

  .gp-app-navbar-with-datepicker .p-toolbar-group-end {
    flex: 1 1 auto;
    justify-content: flex-end;
  }

  .gp-app-navbar-with-datepicker .gp-bell-trigger {
    width: 2rem !important;
    height: 2rem !important;
    padding: 0 !important;
  }
}

@media (max-width: 480px) {
  .gp-app-navbar-toolbar.gp-app-navbar-with-datepicker {
    padding: 0 var(--gp-spacing-sm);
    padding-left: calc(var(--gp-spacing-sm) + env(safe-area-inset-left));
    padding-right: calc(var(--gp-spacing-sm) + env(safe-area-inset-right));
  }

  .gp-app-navbar-with-datepicker .gp-bell-trigger {
    width: 2rem !important;
    height: 2rem !important;
    padding: 0 !important;
  }

  .gp-navbar-datepicker .p-floatlabel label {
    font-size: 0.75rem;
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
</style>
