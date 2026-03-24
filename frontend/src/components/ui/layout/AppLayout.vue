<template>
  <div class="gp-app-layout" :class="layoutClasses">
    <Toast />
    <Toast group="gp-notifications" position="top-right">
      <template #message="slotProps">
        <button
          type="button"
          class="gp-notification-toast"
          @click="handleNotificationToastClick(slotProps.message)"
        >
          <div class="gp-notification-toast-summary">{{ slotProps.message.summary }}</div>
          <div v-if="slotProps.message.detail" class="gp-notification-toast-detail">{{ slotProps.message.detail }}</div>
          <div class="gp-notification-toast-hint">Open Geofence Events</div>
        </button>
      </template>
    </Toast>
    
    <!-- Navbar Slot -->
    <header class="gp-app-navbar">
      <slot name="navbar">
        <AppNavbar
          :show-invite-friend-button="showInviteFriendButton"
          :show-location-sharing-toggle="showLocationSharingToggle"
          :location-sharing-enabled="locationSharingEnabled"
          @invite-friend="emit('invite-friend')"
          @toggle-location-sharing="emit('toggle-location-sharing', $event)"
        />
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
import { computed, onMounted, onUnmounted, watch } from 'vue'
import Toast from 'primevue/toast'
import { useToast } from 'primevue/usetoast'
import AppNavbar from './AppNavbar.vue'
import { useAuthStore } from '@/stores/auth'
import { useNotificationsStore } from '@/stores/notifications'

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

const emit = defineEmits(['invite-friend', 'toggle-location-sharing'])
const toast = useToast()
const authStore = useAuthStore()
const notificationsStore = useNotificationsStore()

const layoutClasses = computed(() => ({
  [`gp-app-layout--${props.variant}`]: props.variant !== 'default',
  'gp-app-layout--full-height': props.fullHeight,
  [`gp-app-layout--padding-${props.padding}`]: props.padding !== 'default'
}))

const emitNotificationToast = (payload = {}) => {
  toast.add({
    severity: payload.severity || 'info',
    summary: payload.summary || 'Notification',
    detail: payload.detail || '',
    life: payload.life ?? 7000,
    group: 'gp-notifications',
    data: payload.data
  })
}

const handleNotificationToastClick = (message) => {
  notificationsStore.handleToastClick(message?.data)
}

onMounted(() => {
  notificationsStore.setToastHandler(emitNotificationToast)

  if (authStore.isAuthenticated) {
    notificationsStore.startPolling()
  }
})

watch(
  () => authStore.isAuthenticated,
  (isAuthenticated) => {
    if (isAuthenticated) {
      notificationsStore.startPolling()
      return
    }
    notificationsStore.resetSessionState({ clearBacklogWatermark: true })
  }
)

watch(
  () => authStore.userId,
  (newUserId, oldUserId) => {
    if (!authStore.isAuthenticated) {
      return
    }

    if (!oldUserId || !newUserId || oldUserId === newUserId) {
      return
    }

    notificationsStore.resetSessionState()
    notificationsStore.startPolling()
  }
)

onUnmounted(() => {
  notificationsStore.clearToastHandler()
  notificationsStore.stopPolling()
})
</script>

<style scoped>
/* Base Layout */
.gp-app-layout {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
  background: var(--gp-surface-light);
  padding-bottom: env(safe-area-inset-bottom);
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
  padding-top: env(safe-area-inset-top);
  height: calc(60px + env(safe-area-inset-top));
}

/* Main Content */
.gp-app-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  padding-left: env(safe-area-inset-left);
  padding-right: env(safe-area-inset-right);
  max-width: 100%;
  box-sizing: border-box;
  overflow-x: hidden;
}

.gp-app-main > * {
  max-width: 100%;
  box-sizing: border-box;
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

.gp-notification-toast {
  width: 100%;
  border: none;
  background: transparent;
  text-align: left;
  cursor: pointer;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 0.2rem;
}

.gp-notification-toast-summary {
  font-weight: 700;
}

.gp-notification-toast-detail {
  color: var(--gp-text-secondary);
}

.gp-notification-toast-hint {
  font-size: 0.75rem;
  color: var(--gp-primary);
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
    padding: var(--gp-spacing-sm);
  }

  .gp-app-layout--padding-large .gp-app-main {
    padding: var(--gp-spacing-md);
  }

  .gp-app-footer {
    padding: var(--gp-spacing-sm) var(--gp-spacing-md);
  }
}

@media (max-width: 480px) {
  .gp-app-layout .gp-app-main {
    padding: var(--gp-spacing-xs);
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
