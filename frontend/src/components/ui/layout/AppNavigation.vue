<template>
  <div class="gp-app-navigation">
    <Drawer v-model:visible="visible" :class="drawerClasses">
      <template #container="{ closeCallback }">
        <div class="gp-nav-container">
          <!-- Header with logo and close button -->
          <div class="gp-nav-header">
            <div class="gp-nav-logo">
              <img src="/geopulse-logo.svg" alt="GeoPulse Logo" class="gp-nav-logo-img" />
            </div>
            <BaseButton
              icon="pi pi-times"
              variant="gp-minimal"
              size="small"
              @click="closeCallback"
              class="gp-nav-close"
            />
          </div>

          <!-- Navigation Sections -->
          <nav class="gp-nav-content">
            <!-- Main Features -->
            <NavigationSection 
              title="Main Features" 
              :items="mainItems"
              @item-click="handleItemClick"
            />

            <!-- Account & Settings -->
            <NavigationSection
              title="Account & Settings"
              :items="accountItems"
              @item-click="handleItemClick"
            />

            <!-- Administration (Admin only) -->
            <NavigationSection
              v-if="isAdmin"
              title="Administration"
              :items="adminItems"
              @item-click="handleItemClick"
            />

            <!-- Theme & Settings -->
            <div class="gp-nav-theme">
              <div class="gp-nav-theme-header">
                <span class="gp-nav-section-title">Appearance</span>
              </div>
              <div class="gp-nav-theme-control">
                <span class="gp-theme-label">{{ isDarkMode ? 'Dark Mode' : 'Light Mode' }}</span>
                <ToggleSwitch
                  v-model="isDarkMode"
                  @change="toggleDarkMode"
                  class="toggle-control"
                />
              </div>
            </div>

            <!-- User Profile Section -->
            <div class="gp-nav-user">
              <div class="gp-nav-user-info">
                <span class="gp-nav-user-label">Logged in as:</span>
                <span class="gp-nav-user-name">{{ userName }}</span>
              </div>
              <BaseButton
                icon="pi pi-sign-out"
                label="Logout"
                variant="gp-minimal"
                @click="handleLogout"
                class="gp-nav-logout"
              />
            </div>

            <!-- Version Display -->
            <div class="gp-nav-version">
              <span class="gp-nav-version-label">Version</span>
              <span class="gp-nav-version-number">{{ appVersion }}</span>
            </div>
          </nav>
        </div>
      </template>
    </Drawer>

    <!-- Navigation Toggle Button -->
    <BaseButton
      icon="pi pi-bars"
      variant="gp-primary"
      size="small"
      @click="visible = true"
      :class="toggleClasses"
    />
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { storeToRefs } from 'pinia'
import Drawer from 'primevue/drawer'
import ToggleSwitch from 'primevue/toggleswitch'
import BaseButton from '../base/BaseButton.vue'
import NavigationSection from './NavigationSection.vue'
import { useAuthStore } from '@/stores/auth'
import { useFriendsStore } from '@/stores/friends'
import { useErrorHandler } from '@/composables/useErrorHandler'
import apiService from '@/utils/apiService'

const props = defineProps({
  variant: {
    type: String,
    default: 'default',
    validator: (value) => ['default', 'compact'].includes(value)
  }
})

const emit = defineEmits(['navigate'])

// Composables
const router = useRouter()
const authStore = useAuthStore()
const friendsStore = useFriendsStore()
const { handleError } = useErrorHandler()

// Store refs
const { userName, isAdmin } = storeToRefs(authStore)
const { receivedInvitesCount } = storeToRefs(friendsStore)

// Local state
const visible = ref(false)
const isDarkMode = ref(false)
const appVersion = ref('')

// Computed
const drawerClasses = computed(() => ({
  'gp-drawer--compact': props.variant === 'compact'
}))

const toggleClasses = computed(() => ({
  'gp-nav-toggle--compact': props.variant === 'compact'
}))

const mainItems = computed(() => [
  {
    label: 'Home',
    icon: 'pi pi-home',
    to: '/',
    key: 'home'
  },
  {
    label: 'Timeline',
    icon: 'pi pi-calendar',
    to: '/app/timeline',
    key: 'timeline'
  },
  {
    label: 'Dashboard',
    icon: 'pi pi-chart-bar',
    to: '/app/dashboard',
    key: 'dashboard'
  },
  {
    label: 'Journey Insights',
    icon: 'pi pi-compass',
    to: '/app/journey-insights',
    key: 'journey-insights'
  },
  {
    label: 'Rewind',
    icon: 'pi pi-calendar-clock',
    to: '/app/rewind',
    key: 'rewind'
  },
  {
    label: 'AI Chat',
    icon: 'pi pi-sparkles',
    to: '/app/ai/chat',
    key: 'ai-chat'
  },
  {
    label: 'Friends',
    icon: 'pi pi-users',
    to: '/app/friends',
    key: 'friends',
    badge: receivedInvitesCount.value > 0 ? receivedInvitesCount.value : null,
    badgeType: 'danger'
  }
])

const accountItems = computed(() => [
  {
    label: 'Profile',
    icon: 'pi pi-user',
    to: '/app/profile',
    key: 'profile'
  },
  {
    label: 'Location Sources',
    icon: 'pi pi-mobile',
    to: '/app/location-sources',
    key: 'location-sources'
  },
  {
    label: 'Share Links',
    icon: 'pi pi-share-alt',
    to: '/app/share-links',
    key: 'share-links'
  },
  {
    label: 'Export / Import',
    icon: 'pi pi-download',
    to: '/app/data-export-import',
    key: 'export'
  },
  {
    label: 'GPS Data',
    icon: 'pi pi-database',
    to: '/app/gps-data',
    key: 'gps-data'
  },
  {
    label: 'Geocoding Management',
    icon: 'pi pi-map-marker',
    to: '/app/geocoding-management',
    key: 'geocoding-management'
  },
  {
    label: 'Favorites Management',
    icon: 'pi pi-heart',
    to: '/app/favorites-management',
    key: 'favorites-management'
  },
  {
    label: 'Preferences',
    icon: 'pi pi-cog',
    to: '/app/timeline/preferences',
    key: 'preferences'
  }
])

const adminItems = computed(() => [
  {
    label: 'Admin Dashboard',
    icon: 'pi pi-th-large',
    to: '/app/admin',
    key: 'admin-dashboard'
  },
  {
    label: 'Manage Users',
    icon: 'pi pi-users',
    to: '/app/admin/users',
    key: 'admin-users'
  },
  {
    label: 'System Settings',
    icon: 'pi pi-cog',
    to: '/app/admin/settings',
    key: 'admin-settings'
  }
])

// Methods
const handleItemClick = (item) => {
  if (item.to) {
    router.push(item.to)
    visible.value = false
    emit('navigate', item)
  }
}

const handleLogout = async () => {
  try {
    await authStore.logout()
    await router.push('/')
  } catch (error) {
    console.error('Logout error:', error)
    await router.push('/')
  }
}

// Version fetching
const fetchVersion = async () => {
  try {
    const response = await apiService.get('/version')
    appVersion.value = response.version || 'Unknown'
  } catch (error) {
    console.warn('Failed to fetch app version:', error)
    appVersion.value = 'Unknown'
  }
}

// Dark mode functionality
const toggleDarkMode = () => {
  document.documentElement.classList.toggle('p-dark')
  isDarkMode.value = document.documentElement.classList.contains('p-dark')
  // Save preference to localStorage
  localStorage.setItem('darkMode', isDarkMode.value.toString())
}

// Initialize dark mode from localStorage and load friends data
onMounted(async () => {
  const savedDarkMode = localStorage.getItem('darkMode')
  if (savedDarkMode === 'true') {
    document.documentElement.classList.add('p-dark')
    isDarkMode.value = true
  } else if (savedDarkMode === 'false') {
    document.documentElement.classList.remove('p-dark')
    isDarkMode.value = false
  } else {
    // Check system preference if no saved preference
    isDarkMode.value = window.matchMedia('(prefers-color-scheme: dark)').matches
    if (isDarkMode.value) {
      document.documentElement.classList.add('p-dark')
    }
  }
  
  // Load received invitations count for badge display
  try {
    await friendsStore.fetchReceivedInvitations()
  } catch (error) {
    // Use our improved error handler but don't show toast for this background operation
    // Just log it - navigation should still work even if this fails
    handleError(error, { life: 2000, severity: 'warn' })
  }

  // Load app version
  await fetchVersion()
})
</script>

<style scoped>
/* Navigation Container */
.gp-app-navigation {
  position: relative;
}

/* Navigation Toggle Button */
.gp-nav-toggle--compact {
  padding: var(--gp-spacing-xs) !important;
}

/* Drawer Container */
.gp-nav-container {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: var(--gp-surface-white);
}

/* Navigation Header */
.gp-nav-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--gp-spacing-lg);
  border-bottom: 1px solid var(--gp-border-light);
  flex-shrink: 0;
}

.gp-nav-logo {
  flex: 1;
}

.gp-nav-logo-img {
  width: 80px;
  height: auto;
}

.gp-nav-close {
  flex-shrink: 0;
}

/* Navigation Content */
.gp-nav-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  padding: var(--gp-spacing-md) 0;
  overflow-y: auto;
}

/* Theme Section */
.gp-nav-theme {
  padding: var(--gp-spacing-md) var(--gp-spacing-lg);
  border-top: 1px solid var(--gp-border-light);
  flex-shrink: 0;
}

.gp-nav-theme-header {
  margin-bottom: var(--gp-spacing-md);
}

.gp-nav-section-title {
  font-size: 0.75rem;
  text-transform: uppercase;
  color: var(--gp-text-muted);
  font-weight: 600;
  letter-spacing: 0.025em;
}

.gp-nav-theme-control {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.gp-theme-label {
  font-size: 0.875rem;
  font-weight: 500;
  color: var(--gp-text-primary);
}

/* User Section */
.gp-nav-user {
  margin-top: auto;
  padding: var(--gp-spacing-md) var(--gp-spacing-lg);
  border-top: 1px solid var(--gp-border-light);
  flex-shrink: 0;
}

.gp-nav-user-info {
  margin-bottom: var(--gp-spacing-md);
}

.gp-nav-user-label {
  display: block;
  font-size: 0.75rem;
  text-transform: uppercase;
  color: var(--gp-text-muted);
  font-weight: 500;
  letter-spacing: 0.025em;
  margin-bottom: var(--gp-spacing-xs);
}

.gp-nav-user-name {
  display: block;
  font-size: 0.875rem;
  font-weight: 600;
  color: var(--gp-text-primary);
}

.gp-nav-logout {
  width: 100%;
  justify-content: flex-start;
}

/* Version Section */
.gp-nav-version {
  padding: var(--gp-spacing-sm) var(--gp-spacing-lg);
  border-top: 1px solid var(--gp-border-light);
  text-align: center;
  background: var(--gp-surface-lighter, rgba(0, 0, 0, 0.02));
  flex-shrink: 0;
}

.gp-nav-version-label {
  display: block;
  font-size: 0.625rem;
  text-transform: uppercase;
  color: var(--gp-text-muted);
  font-weight: 500;
  letter-spacing: 0.05em;
  margin-bottom: var(--gp-spacing-xs);
  opacity: 0.7;
}

.gp-nav-version-number {
  display: block;
  font-size: 0.75rem;
  font-weight: 600;
  color: var(--gp-text-secondary);
  font-family: 'SF Mono', Monaco, 'Cascadia Code', 'Roboto Mono', Consolas, 'Courier New', monospace;
}

/* Dark Mode */
.p-dark .gp-nav-container {
  background: var(--gp-surface-dark);
}

.p-dark .gp-nav-header {
  border-bottom-color: var(--gp-border-dark);
}

.p-dark .gp-nav-theme {
  border-top-color: var(--gp-border-dark);
}

.p-dark .gp-nav-section-title {
  color: var(--gp-text-muted);
}

.p-dark .gp-theme-label {
  color: var(--gp-text-primary);
}

/* Dark mode toggle switch */
.p-dark :deep(.p-toggleswitch .p-toggleswitch-slider) {
  background: var(--gp-surface-darker);
}

.p-dark :deep(.p-toggleswitch .p-toggleswitch-handle) {
  display: none;
}

.p-dark :deep(.p-toggleswitch.p-toggleswitch-checked .p-toggleswitch-handle) {
  display: none;
}

.p-dark .gp-nav-user {
  border-top-color: var(--gp-border-dark);
}

.p-dark .gp-nav-user-label {
  color: var(--gp-text-muted);
}

.p-dark .gp-nav-user-name {
  color: var(--gp-text-primary);
}

.p-dark .gp-nav-version {
  background: var(--gp-surface-darker, rgba(255, 255, 255, 0.03));
  border-top-color: var(--gp-border-dark);
}

.p-dark .gp-nav-version-label {
  color: var(--gp-text-muted);
}

.p-dark .gp-nav-version-number {
  color: var(--gp-text-secondary);
}

/* Compact variant */
.gp-drawer--compact .gp-nav-header {
  padding: var(--gp-spacing-md);
}

.gp-drawer--compact .gp-nav-user {
  padding: var(--gp-spacing-sm) var(--gp-spacing-md);
}

.gp-drawer--compact .gp-nav-version {
  padding: var(--gp-spacing-xs) var(--gp-spacing-md);
}

/* Responsive */
@media (max-width: 768px) {
  .gp-nav-header {
    padding: var(--gp-spacing-md);
  }

  .gp-nav-user {
    padding: var(--gp-spacing-sm) var(--gp-spacing-md);
  }

  .gp-nav-logo-img {
    width: 60px;
  }

  .gp-nav-version {
    padding: var(--gp-spacing-xs) var(--gp-spacing-md);
  }
}
</style>

<style>
/* Global Drawer Overrides */
.gp-app-navigation .p-drawer {
  width: 280px;
}

.gp-app-navigation .p-drawer-content {
  padding: 0;
}

.gp-drawer--compact .p-drawer {
  width: 240px;
}

/* Dark mode drawer styling - More specific selectors */
.p-dark .gp-app-navigation .p-drawer.p-component {
  background: var(--gp-surface-dark) !important;
  border: 1px solid var(--gp-border-dark) !important;
  box-shadow: 0 10px 40px rgba(0, 0, 0, 0.4) !important;
}

.p-dark .gp-app-navigation .p-drawer-content {
  background: var(--gp-surface-dark) !important;
  border: none !important;
}

.p-dark .gp-app-navigation .p-drawer-mask {
  background: rgba(0, 0, 0, 0.6) !important;
  border: none !important;
}

/* Additional PrimeVue specific overrides for dark mode */
.p-dark .p-drawer.p-component[data-pc-name="drawer"] {
  background: var(--gp-surface-dark) !important;
  border: 1px solid var(--gp-border-dark) !important;
  color: var(--gp-text-primary) !important;
}

.p-dark .p-drawer[data-pc-name="drawer"][data-p="left open modal"] {
  background: var(--gp-surface-dark) !important;
  border: 1px solid var(--gp-border-dark) !important;
}

/* Override any potential CSS variables that PrimeVue might be using */
.p-dark .p-drawer {
  --p-drawer-background: var(--gp-surface-dark) !important;
  --p-drawer-border-color: var(--gp-border-dark) !important;
  --p-drawer-color: var(--gp-text-primary) !important;
}

/* Responsive drawer */
@media (max-width: 768px) {
  .gp-app-navigation .p-drawer {
    width: 90vw;
    max-width: 280px;
  }
}
</style>