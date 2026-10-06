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
import { useAuthStore } from '@/stores/auth'
import { useFriendsStore } from '@/stores/friends'
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
const friendsStore = useFriendsStore()
const { handleError } = useErrorHandler()

// Store refs
const { canViewAdmin, adminReadOnly } = storeToRefs(useAuthStore())
const { receivedInvitesCount } = storeToRefs(friendsStore)

// Local state
const visible = ref(false)

// Computed
const drawerClasses = computed(() => ({
  'gp-drawer--compact': props.variant === 'compact'
}))

const toggleClasses = computed(() => ({
  'gp-nav-toggle--compact': props.variant === 'compact'
}))

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
      { label: t('nav.items.geofences'), icon: 'pi pi-bullseye', to: '/app/geofences', key: 'geofences' },
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
    title: t('nav.sections.data'),
    items: [
      { label: t('nav.items.location-sources'), icon: 'pi pi-mobile', to: '/app/location-sources', key: 'location-sources' },
      { label: t('nav.items.gps-data'), icon: 'pi pi-database', to: '/app/gps-data', key: 'gps-data' },
      { label: t('nav.items.geocoding-management'), icon: 'pi pi-map-marker', to: '/app/geocoding-management', key: 'geocoding-management' },
      { label: t('nav.items.export'), icon: 'pi pi-download', to: '/app/data-export-import', key: 'export' }
    ]
  },
  ...(canViewAdmin.value ? [adminSection.value] : [])
])

// One flat group, most-used first, so every admin page is a single click from anywhere in the app.
const adminSection = computed(() => ({
  title: t('nav.sections.administration'),
  items: [
    { label: t('nav.items.admin-dashboard'), icon: 'pi pi-th-large', to: '/app/admin', key: 'admin-dashboard' },
    { label: t('nav.items.admin-settings'), icon: 'pi pi-sliders-h', to: '/app/admin/settings', key: 'admin-settings' },
    { label: t('nav.items.admin-users'), icon: 'pi pi-users', to: '/app/admin/users', key: 'admin-users' },
    { label: t('nav.items.admin-invitations'), icon: 'pi pi-send', to: '/app/admin/invitations', key: 'admin-invitations' },
    { label: t('nav.items.admin-oidc-providers'), icon: 'pi pi-key', to: '/app/admin/oidc-providers', key: 'admin-oidc-providers' },
    { label: t('nav.items.admin-backups'), icon: 'pi pi-database', to: '/app/admin/backups', key: 'admin-backups' },
    { label: t('nav.items.admin-timeline-regeneration'), icon: 'pi pi-refresh', to: '/app/admin/timeline-regeneration-campaigns', key: 'admin-timeline-regeneration' },
    { label: t('nav.items.admin-audit-logs'), icon: 'pi pi-history', to: '/app/admin/audit-logs', key: 'admin-audit-logs', disabled: adminReadOnly.value }
  ]
}))

// Methods
const handleItemClick = (item) => {
  if (item.to) {
    router.push(item.to)
    visible.value = false
    emit('navigate', item)
  }
}

// Load received invitations count for the Friends badge
onMounted(async () => {
  try {
    await friendsStore.fetchReceivedInvitations()
  } catch (error) {
    if (!isMaintenanceInterruption(error)) {
      // Use our improved error handler but don't show toast for this background operation
      // Just log it - navigation should still work even if this fails
      handleError(error, { life: 2000, severity: 'warn' })
    }
  }
})
</script>

<style scoped>
/* Navigation Container */
.gp-app-navigation {
  position: relative;
}

/* Navigation Toggle Button */
/* .p-button outranks the global phone rule `.p-button.p-button-sm` in primevue-overrides.css. */
.p-button.gp-nav-toggle--compact {
  padding: var(--gp-spacing-xs);
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

/* Compact variant */
.gp-drawer--compact .gp-nav-header {
  padding: var(--gp-spacing-md);
}

/* Responsive */
@media (max-width: 768px) {
  .gp-nav-header {
    padding: var(--gp-spacing-md);
  }

  .gp-nav-logo-img {
    width: 60px;
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
