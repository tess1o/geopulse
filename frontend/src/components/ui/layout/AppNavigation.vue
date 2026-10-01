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
            <NavigationSection
              v-for="section in navigationSections"
              :key="section.title"
              :title="section.title"
              :items="section.items"
              @item-click="handleItemClick"
            />

            <!-- Administration (Admin only) -->
            <template v-if="canViewAdmin">
              <div class="gp-nav-admin-header">
                <span class="gp-nav-section-title">{{ t('nav.sections.administration') }}</span>
              </div>

              <NavigationSection
                :title="t('nav.sections.overview')"
                :items="adminOverviewItems"
                @item-click="handleItemClick"
              />
              <NavigationSection
                :title="t('nav.sections.operations')"
                :items="adminOperationsItems"
                @item-click="handleItemClick"
              />
              <NavigationSection
                :title="t('nav.sections.peopleAndAccess')"
                :items="adminPeopleItems"
                @item-click="handleItemClick"
              />
              <NavigationSection
                :title="t('nav.sections.configuration')"
                :items="adminConfigurationItems"
                @item-click="handleItemClick"
              />
            </template>

            <!-- Theme & Settings -->
            <div class="gp-nav-theme">
              <div class="gp-nav-theme-header">
                <span class="gp-nav-section-title">{{ t('nav.sections.appearance') }}</span>
              </div>
              <div class="gp-nav-theme-control">
                <span class="gp-theme-label">{{ t('nav.theme.label', { mode: themeModeLabel }) }}</span>
                <DarkModeSwitcher class="gp-theme-switcher" />
              </div>
            </div>

            <!-- User Profile Section -->
            <div class="gp-nav-user">
              <div class="gp-nav-user-info">
                <span class="gp-nav-user-label">{{ t('nav.loggedInAs') }}</span>
                <span class="gp-nav-user-name">{{ userName }}</span>
              </div>
              <BaseButton
                icon="pi pi-sign-out"
                :label="t('nav.logout')"
                variant="gp-minimal"
                @click="handleLogout"
                class="gp-nav-logout"
              />
            </div>

            <!-- Version Display -->
            <div class="gp-nav-version">
              <span class="gp-nav-version-label">{{ t('nav.version') }}</span>
              <span class="gp-nav-version-number">{{ appVersion }}</span>
              <a
                v-if="updateAvailable && latestVersion"
                :href="releaseUrl"
                target="_blank"
                rel="noopener noreferrer"
                class="gp-nav-version-update"
              >
                <i class="pi pi-arrow-circle-up" />
                <span>{{ t('nav.newVersionAvailable', { version: latestVersion }) }}</span>
              </a>
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
import { useI18n } from 'vue-i18n'
import { storeToRefs } from 'pinia'
import Drawer from 'primevue/drawer'
import BaseButton from '../base/BaseButton.vue'
import NavigationSection from './NavigationSection.vue'
import DarkModeSwitcher from '@/components/DarkModeSwitcher.vue'
import { useThemeMode } from '@/composables/useThemeMode'
import { useAuthStore } from '@/stores/auth'
import { useFriendsStore } from '@/stores/friends'
import { useNotificationsStore } from '@/stores/notifications'
import { useVersionStore } from '@/stores/version'
import { useErrorHandler } from '@/composables/useErrorHandler'
import { isMaintenanceInterruption } from '@/stores/maintenance'

const { t } = useI18n()

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
const notificationsStore = useNotificationsStore()
const versionStore = useVersionStore()
const { handleError } = useErrorHandler()
const { themeMode, themeModes } = useThemeMode()

// Store refs
const { userName, canViewAdmin, adminReadOnly } = storeToRefs(authStore)
const { receivedInvitesCount } = storeToRefs(friendsStore)
const { unreadCount: notificationUnreadCount } = storeToRefs(notificationsStore)

// Local state
const visible = ref(false)
const appVersion = ref('')
const latestVersion = ref('')
const updateAvailable = ref(false)
const DEFAULT_RELEASE_URL = 'https://github.com/tess1o/geopulse/releases'
const releaseUrl = ref(DEFAULT_RELEASE_URL)

// Computed
const drawerClasses = computed(() => ({
  'gp-drawer--compact': props.variant === 'compact'
}))

const toggleClasses = computed(() => ({
  'gp-nav-toggle--compact': props.variant === 'compact'
}))

const themeModeLabel = computed(() => {
  if (themeMode.value === themeModes.LIGHT) return t('nav.theme.modes.light')
  if (themeMode.value === themeModes.DARK) return t('nav.theme.modes.dark')
  return t('nav.theme.modes.system')
})

// Labels are resolved from the catalogs here rather than stored as display text. This computed reads
// the reactive locale, so the whole navigation re-renders when the language changes. Each entry keeps
// its stable `key`, which is also the catalog key (`nav.items.<key>`) and matches route meta.titleKey.
const navigationSections = computed(() => [
  {
    title: t('nav.sections.timeline'),
    items: [
      { label: t('nav.items.timeline'), icon: 'pi pi-calendar', to: '/app/timeline', key: 'timeline' },
      { label: t('nav.items.dashboard'), icon: 'pi pi-chart-bar', to: '/app/dashboard', key: 'dashboard' },
      { label: t('nav.items.timeline-labels'), icon: 'pi pi-calendar-times', to: '/app/timeline-labels', key: 'timeline-labels' },
      { label: t('nav.items.trips'), icon: 'pi pi-briefcase', to: '/app/trips', key: 'trips' }
    ]
  },
  {
    title: t('nav.sections.explore'),
    items: [
      { label: t('nav.items.location-analytics'), icon: 'pi pi-map', to: '/app/location-analytics', key: 'location-analytics' },
      { label: t('nav.items.journey-insights'), icon: 'pi pi-compass', to: '/app/journey-insights', key: 'journey-insights' },
      { label: t('nav.items.rewind'), icon: 'pi pi-calendar-clock', to: '/app/rewind', key: 'rewind' },
      { label: t('nav.items.coverage-explorer'), icon: 'pi pi-globe', to: '/app/coverage', key: 'coverage-explorer' },
      { label: t('nav.items.ai-chat'), icon: 'pi pi-sparkles', to: '/app/ai/chat', key: 'ai-chat' }
    ]
  },
  {
    title: t('nav.sections.organizeAndShare'),
    items: [
      { label: t('nav.items.favorites-management'), icon: 'pi pi-heart', to: '/app/favorites-management', key: 'favorites-management' },
      { label: t('nav.items.geofences'), icon: 'pi pi-map-marker', to: '/app/geofences', key: 'geofences' },
      {
        label: t('nav.items.friends'),
        icon: 'pi pi-users',
        to: '/app/friends',
        key: 'friends',
        badge: receivedInvitesCount.value > 0 ? receivedInvitesCount.value : null,
        badgeType: 'danger'
      },
      { label: t('nav.items.share-links'), icon: 'pi pi-share-alt', to: '/app/share-links', key: 'share-links' }
    ]
  },
  {
    title: t('nav.sections.settingsAndData'),
    items: [
      { label: t('nav.items.profile'), icon: 'pi pi-user', to: '/app/profile', key: 'profile' },
      {
        label: t('nav.items.notifications'),
        icon: 'pi pi-bell',
        to: '/app/notifications',
        key: 'notifications',
        badge: notificationUnreadCount.value > 0 ? notificationUnreadCount.value : null,
        badgeType: 'danger'
      },
      { label: t('nav.items.location-sources'), icon: 'pi pi-mobile', to: '/app/location-sources', key: 'location-sources' },
      { label: t('nav.items.preferences'), icon: 'pi pi-cog', to: '/app/timeline/preferences', key: 'preferences' },
      { label: t('nav.items.gps-data'), icon: 'pi pi-database', to: '/app/gps-data', key: 'gps-data' },
      { label: t('nav.items.geocoding-management'), icon: 'pi pi-map-marker', to: '/app/geocoding-management', key: 'geocoding-management' },
      { label: t('nav.items.export'), icon: 'pi pi-download', to: '/app/data-export-import', key: 'export' },
      { label: t('nav.items.help'), icon: 'pi pi-question-circle', to: '/app/help', key: 'help' }
    ]
  }
])

const adminOverviewItems = computed(() => [
  {
    label: t('nav.items.admin-dashboard'),
    icon: 'pi pi-th-large',
    to: '/app/admin',
    key: 'admin-dashboard'
  }
])

const adminOperationsItems = computed(() => [
  {
    label: t('nav.items.admin-backups'),
    icon: 'pi pi-database',
    to: '/app/admin/backups',
    key: 'admin-backups'
  },
  {
    label: t('nav.items.admin-timeline-regeneration'),
    icon: 'pi pi-refresh',
    to: '/app/admin/timeline-regeneration-campaigns',
    key: 'admin-timeline-regeneration'
  }
])

const adminPeopleItems = computed(() => [
  {
    label: t('nav.items.admin-users'),
    icon: 'pi pi-users',
    to: '/app/admin/users',
    key: 'admin-users'
  },
  {
    label: t('nav.items.admin-invitations'),
    icon: 'pi pi-send',
    to: '/app/admin/invitations',
    key: 'admin-invitations'
  },
  {
    label: t('nav.items.admin-oidc-providers'),
    icon: 'pi pi-key',
    to: '/app/admin/oidc-providers',
    key: 'admin-oidc-providers'
  },
  {
    label: t('nav.items.admin-audit-logs'),
    icon: 'pi pi-history',
    to: '/app/admin/audit-logs',
    key: 'admin-audit-logs',
    disabled: adminReadOnly.value
  }
])

const adminConfigurationItems = computed(() => [{
  label: t('nav.items.admin-settings'),
  icon: 'pi pi-cog',
  to: '/app/admin/settings',
  key: 'admin-settings'
}])

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
const fetchVersionStatus = async () => {
  try {
    const response = await versionStore.fetchStatus()
    appVersion.value = response.currentVersion || response.version || 'Unknown'
    latestVersion.value = response.latestVersion || ''
    updateAvailable.value = response.updateAvailable === true
    releaseUrl.value = response.releaseUrl || DEFAULT_RELEASE_URL
  } catch (error) {
    console.warn('Failed to fetch version status:', error)
    latestVersion.value = ''
    updateAvailable.value = false
    releaseUrl.value = DEFAULT_RELEASE_URL

    try {
      const response = await versionStore.fetchVersion()
      appVersion.value = response.version || 'Unknown'
    } catch (fallbackError) {
      console.warn('Failed to fetch app version fallback:', fallbackError)
      appVersion.value = 'Unknown'
    }
  }
}

// Load friends data and version status
onMounted(async () => {
  // Load received invitations count for badge display
  try {
    await friendsStore.fetchReceivedInvitations()
  } catch (error) {
    if (!isMaintenanceInterruption(error)) {
      // Use our improved error handler but don't show toast for this background operation
      // Just log it - navigation should still work even if this fails
      handleError(error, { life: 2000, severity: 'warn' })
    }
  }

  // Load app version status
  await fetchVersionStatus()
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
  background: var(--gp-surface-card);
}

/* Navigation Header */
.gp-nav-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--gp-spacing-lg);
  border-bottom: 1px solid var(--gp-border);
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

/* Admin Section Header */
.gp-nav-admin-header {
  padding: var(--gp-spacing-lg) var(--gp-spacing-lg) var(--gp-spacing-sm);
  margin-top: var(--gp-spacing-md);
  border-top: 2px solid var(--gp-primary-text);
}

.gp-nav-admin-header .gp-nav-section-title {
  color: var(--gp-primary-text);
  font-size: 0.875rem;
  font-weight: 700;
}

/* Theme Section */
.gp-nav-theme {
  padding: var(--gp-spacing-md) var(--gp-spacing-lg);
  border-top: 1px solid var(--gp-border);
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
  gap: var(--gp-spacing-sm);
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
  border-top: 1px solid var(--gp-border);
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
  border-top: 1px solid var(--gp-border);
  text-align: center;
  background: var(--gp-surface-muted, rgba(0, 0, 0, 0.02));
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
  font-family: var(--gp-font-mono);
}

.gp-nav-version-update {
  display: inline-flex;
  align-items: center;
  gap: var(--gp-spacing-xs);
  margin-top: var(--gp-spacing-xs);
  padding: 2px 8px;
  border-radius: var(--gp-radius-pill);
  background: var(--gp-warning-soft);
  color: var(--gp-warning-text);
  font-size: 0.75rem;
  font-weight: 600;
  text-decoration: none;
  transition: opacity 0.2s ease;
}

.gp-nav-version-update:hover {
  opacity: 0.85;
}

/* Dark Mode */

.gp-theme-switcher :deep(.p-button) {
  min-width: 2.25rem;
  min-height: 2.25rem;
  padding: 0.5rem;
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

/* Override any potential CSS variables that PrimeVue might be using */

/* Responsive drawer */
@media (max-width: 768px) {
  .gp-app-navigation .p-drawer {
    width: 90vw;
    max-width: 280px;
  }
}
</style>
