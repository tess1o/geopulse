<template>
  <Toolbar class="gp-app-navbar-toolbar" :class="toolbarClasses">
    <template #start>
      <div class="gp-navbar-start">
        <AppNavigation :variant="navigationVariant" @navigate="handleNavigate" />
        <div class="gp-navbar-logo">
          <router-link to="/" class="gp-navbar-logo-link">
            <span class="gp-navbar-logo-text">GeoPulse</span>
            <span v-if="demoModeEnabled" class="gp-navbar-demo-badge">{{ t('ui.appNavbar.demoBadge') }}</span>
          </router-link>
        </div>
      </div>
    </template>
    
    <template #center>
      <slot name="center" />
    </template>
    
    <template #end>
      <div class="gp-navbar-end">
        <!-- Location Sharing Toggle -->
        <div v-if="showLocationSharingToggle" class="location-sharing-navbar">
          <i
            :class="['sharing-icon-navbar', locationSharingEnabled ? 'pi pi-eye' : 'pi pi-eye-slash']"
            v-tooltip.bottom="locationSharingEnabled ? t('ui.appNavbar.locationSharingEnabledTooltip') : t('ui.appNavbar.locationSharingDisabledTooltip')"
          ></i>
          <ToggleSwitch
            :modelValue="locationSharingEnabled"
            @update:modelValue="$emit('toggle-location-sharing', $event)"
            class="sharing-toggle-navbar"
          />
          <span class="sharing-label-desktop">{{ t('ui.appNavbar.shareLocation') }}</span>
        </div>

        <!-- Invite Friend Button -->
        <Button
          v-if="showInviteFriendButton"
          icon="pi pi-user-plus"
          :label="inviteButtonLabel"
          @click="$emit('invite-friend')"
          :disabled="inviteDisabled"
          :aria-label="inviteDisabled ? t('ui.appNavbar.inviteFriendDisabledAriaLabel') : t('ui.appNavbar.inviteFriend')"
          v-tooltip.bottom="inviteDisabled ? t('ui.appNavbar.inviteFriendDisabledTooltip') : t('ui.appNavbar.inviteFriend')"
          :class="inviteButtonClass"
        />
        <NotificationBell />
        <slot name="end" />
      </div>
    </template>
  </Toolbar>
</template>

<script setup>
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { storeToRefs } from 'pinia'
import Toolbar from 'primevue/toolbar'
import AppNavigation from './AppNavigation.vue'
import NotificationBell from './NotificationBell.vue'
import { useAuthStore } from '@/stores/auth'

const { t } = useI18n()

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
  showInviteFriendButton: {
    type: Boolean,
    default: false
  },
  showLocationSharingToggle: {
    type: Boolean,
    default: false
  },
  locationSharingEnabled: {
    type: Boolean,
    default: true
  }
})

const emit = defineEmits(['navigate', 'invite-friend', 'toggle-location-sharing'])
const authStore = useAuthStore()
const { demoModeEnabled, demoReadOnly } = storeToRefs(authStore)

const inviteButtonLabel = computed(() => {
  // Show label on desktop, hide on mobile
  return window.innerWidth > 768 ? t('ui.appNavbar.inviteFriend') : ''
})

const inviteButtonClass = computed(() => {
  // Rounded small button on mobile, regular button on desktop
  return window.innerWidth > 768 ? '' : 'p-button-sm p-button-rounded'
})

const inviteDisabled = computed(() => demoReadOnly.value)

const toolbarClasses = computed(() => ({
  [`gp-navbar--${props.variant}`]: props.variant !== 'default',
  'gp-navbar--fixed': props.fixed,
  'gp-navbar--transparent': props.transparent
}))

const navigationVariant = computed(() => {
  return props.variant === 'compact' ? 'compact' : 'default'
})

const handleNavigate = (item) => {
  emit('navigate', item)
}
</script>

<style scoped>
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
  gap: var(--gp-spacing-md);
  padding-right: var(--gp-spacing-md);
}

/* Location Sharing in Navbar */
.location-sharing-navbar {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.375rem 0.75rem;
  background: var(--gp-surface-muted);
  border-radius: var(--gp-radius-medium);
  border: 1px solid var(--gp-border);
}

.sharing-icon-navbar {
  font-size: 1rem;
  color: var(--gp-primary);
}

.sharing-icon-navbar.pi-eye-slash {
  color: var(--gp-text-secondary);
}

.sharing-label-desktop {
  font-size: 0.875rem;
  font-weight: 500;
  color: var(--gp-text-primary);
  white-space: nowrap;
}

/* Navbar Variants */
.gp-navbar--compact .gp-navbar-start {
  gap: var(--gp-spacing-md);
}

.gp-navbar--compact .gp-navbar-logo-text {
  font-size: 1.125rem;
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
  background: transparent;
  border-bottom: none;
  box-shadow: none;
}

/* Responsive */
@media (max-width: 768px) {
  .gp-navbar-start {
    gap: var(--gp-spacing-md);
  }

  .gp-navbar-logo-text {
    font-size: 1.125rem;
  }

  .gp-navbar-end {
    gap: var(--gp-spacing-sm);
  }

  /* Compact location sharing on mobile */
  .location-sharing-navbar {
    padding: 0.25rem 0.5rem;
    gap: 0.375rem;
  }

  .sharing-icon-navbar {
    font-size: 0.875rem;
  }

  /* Hide label text on mobile */
  .sharing-label-desktop {
    display: none;
  }
}

@media (max-width: 480px) {
  .gp-navbar-start {
    gap: var(--gp-spacing-sm);
  }

  .gp-navbar-logo-text {
    font-size: 1rem;
  }
}
</style>

<style>
/* Global Toolbar Overrides for GeoPulse */
.gp-app-navbar-toolbar {
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

.gp-app-navbar-toolbar .p-toolbar-group-start,
.gp-app-navbar-toolbar .p-toolbar-group-center,
.gp-app-navbar-toolbar .p-toolbar-group-end {
  align-items: center;
  height: 100%;
}

/* Compact variant */
.gp-navbar--compact.gp-app-navbar-toolbar {
  height: 50px;
  padding: 0 var(--gp-spacing-md);
}

/* Minimal variant */
.gp-navbar--minimal.gp-app-navbar-toolbar {
  box-shadow: none;
  border-bottom: 1px solid var(--gp-border-subtle);
}

/* Transparent variant */
.gp-navbar--transparent.gp-app-navbar-toolbar {
  background: transparent;
  border-bottom: none;
  box-shadow: none;
}

/* Responsive */
@media (max-width: 768px) {
  .gp-app-navbar-toolbar {
    padding: 0 var(--gp-spacing-md);
    padding-left: calc(var(--gp-spacing-md) + env(safe-area-inset-left));
    padding-right: calc(var(--gp-spacing-md) + env(safe-area-inset-right));
  }
}

@media (max-width: 480px) {
  .gp-app-navbar-toolbar {
    padding: 0 var(--gp-spacing-sm);
    padding-left: calc(var(--gp-spacing-sm) + env(safe-area-inset-left));
    padding-right: calc(var(--gp-spacing-sm) + env(safe-area-inset-right));
  }
}

/* Animation for fixed navbar */
.gp-navbar--fixed.gp-app-navbar-toolbar {
  transition: all 0.3s cubic-bezier(0.25, 0.8, 0.25, 1);
}

/* Focus states */
.gp-app-navbar-toolbar:focus-within {
/*  outline: 2px solid var(--gp-primary);
  outline-offset: -2px;*/
}
</style>
