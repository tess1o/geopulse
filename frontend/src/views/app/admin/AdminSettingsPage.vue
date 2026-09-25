<template>
  <AppLayout>
    <div class="admin-settings">
      <Breadcrumb :home="breadcrumbHome" :model="breadcrumbItems" class="admin-breadcrumb" />

      <div class="page-header">
        <div class="header-content">
          <div class="header-text">
            <h1>System Settings</h1>
            <p class="text-muted">Configure system-wide settings</p>
          </div>
          <div class="header-actions">
            <SettingsSearchTrigger
              page-key="admin"
              placeholder="Search system settings..."
              @navigate="handleSettingsSearchNavigate"
            />
          </div>
        </div>
      </div>

      <DemoReadOnlyBanner />

      <div class="settings-layout">
        <label class="mobile-settings-select">
          <span>Settings section</span>
          <select :value="activeTab" @change="selectTab($event.target.value)">
            <optgroup v-for="group in settingsGroups" :key="group.label" :label="group.label">
              <option v-for="tab in group.items" :key="tab.key" :value="tab.key">{{ tab.label }}</option>
            </optgroup>
          </select>
        </label>
        <nav class="settings-nav" aria-label="System settings sections">
          <section v-for="group in settingsGroups" :key="group.label" class="settings-nav-group">
            <h2>{{ group.label }}</h2>
            <button v-for="tab in group.items" :key="tab.key" type="button"
                    :class="{ active: activeTab === tab.key }" @click="selectTab(tab.key)">
              <i :class="tab.icon" aria-hidden="true" />{{ tab.label }}
            </button>
          </section>
        </nav>
        <section class="settings-content">
        <keep-alive>
          <component :is="currentTabComponent" :key="activeTab" />
        </keep-alive>
        </section>
      </div>

      <Toast />
    </div>
  </AppLayout>
</template>

<script setup>
import { ref, computed, watch, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import Toast from 'primevue/toast'
import { useToast } from 'primevue/usetoast'
import Breadcrumb from 'primevue/breadcrumb'
import AppLayout from '@/components/ui/layout/AppLayout.vue'
import SettingsSearchTrigger from '@/components/search/SettingsSearchTrigger.vue'
import DemoReadOnlyBanner from '@/components/admin/DemoReadOnlyBanner.vue'
import { jumpToSetting } from '@/utils/settingJump'

// Import tab components
import AuthenticationSettingsTab from '@/components/admin/settings/tabs/AuthenticationSettingsTab.vue'
import GeocodingSettingsTab from '@/components/admin/settings/tabs/GeocodingSettingsTab.vue'
import WeatherSettingsTab from '@/components/admin/settings/tabs/WeatherSettingsTab.vue'
import MapMatchingSettingsTab from '@/components/admin/settings/tabs/MapMatchingSettingsTab.vue'
import PanoramaxSettingsTab from '@/components/admin/settings/tabs/PanoramaxSettingsTab.vue'
import PoiSettingsTab from '@/components/admin/settings/tabs/PoiSettingsTab.vue'
import AISettingsTab from '@/components/admin/settings/tabs/AISettingsTab.vue'
import GPSProcessingSettingsTab from '@/components/admin/settings/tabs/GPSProcessingSettingsTab.vue'
import ImportSettingsTab from '@/components/admin/settings/tabs/ImportSettingsTab.vue'
import ExportSettingsTab from '@/components/admin/settings/tabs/ExportSettingsTab.vue'
import NotificationsSettingsTab from '@/components/admin/settings/tabs/NotificationsSettingsTab.vue'
import SystemSettingsTab from '@/components/admin/settings/tabs/SystemSettingsTab.vue'

const router = useRouter()
const route = useRoute()
const toast = useToast()

const breadcrumbHome = ref({
  icon: 'pi pi-home',
  command: () => router.push('/')
})

const breadcrumbItems = ref([
  {
    label: 'Administration',
    command: () => router.push('/app/admin')
  },
  { label: 'Settings' }
])

// Tab configuration
const activeTab = ref('authentication')

const settingsGroups = ref([
  { label: 'Access & Security', items: [
    { label: 'Authentication', icon: 'pi pi-shield', key: 'authentication' },
    { label: 'Notifications', icon: 'pi pi-bell', key: 'notifications' }
  ] },
  { label: 'Data Processing', items: [
    { label: 'Import', icon: 'pi pi-upload', key: 'import' },
    { label: 'Export', icon: 'pi pi-download', key: 'export' }
  ] },
  { label: 'Integrations & Maps', items: [
    { label: 'Geocoding', icon: 'pi pi-map-marker', key: 'geocoding' },
    { label: 'Weather', icon: 'pi pi-cloud', key: 'weather' },
    { label: 'Map Matching', icon: 'pi pi-map', key: 'map-matching' },
    { label: 'Panoramax', icon: 'pi pi-images', key: 'panoramax' },
    { label: 'Place discovery', icon: 'pi pi-compass', key: 'poi' },
    { label: 'AI Assistant', icon: 'pi pi-sparkles', key: 'ai' }
  ] },
  { label: 'System', items: [{ label: 'System', icon: 'pi pi-server', key: 'system' }] }
])

const legacyTabAliases = {
  'settings-backup': 'backup'
}

const normalizeTabKey = (tabKey) => {
  if (typeof tabKey !== 'string') return tabKey
  return legacyTabAliases[tabKey] || tabKey
}

const validTabs = ['authentication', 'geocoding', 'weather', 'map-matching', 'panoramax', 'poi', 'ai', 'gps', 'import', 'export', 'notifications', 'system']

const currentTabComponent = computed(() => {
  const components = {
    authentication: AuthenticationSettingsTab,
    geocoding: GeocodingSettingsTab,
    weather: WeatherSettingsTab,
    'map-matching': MapMatchingSettingsTab,
    panoramax: PanoramaxSettingsTab,
    poi: PoiSettingsTab,
    ai: AISettingsTab,
    gps: GPSProcessingSettingsTab,
    import: ImportSettingsTab,
    export: ExportSettingsTab,
    notifications: NotificationsSettingsTab,
    system: SystemSettingsTab
  }
  return components[activeTab.value]
})

const selectTab = (tab) => {
  activeTab.value = tab
  router.push({ query: { ...route.query, tab } })
}

const jumpToRouteSetting = async (settingKey, hintOverride = null) => {
  if (!settingKey || route.path !== '/app/admin/settings') return false

  return jumpToSetting(settingKey, {
    onMissing: () => {
      toast.add({
        severity: 'info',
        summary: 'Setting not visible',
        detail: hintOverride || 'This setting is not currently visible. Open the correct settings tab and try again.',
        life: 4000
      })
    }
  })
}

const handleSettingsSearchNavigate = async (item) => {
  if (!item?.setting) return

  const nextTab = normalizeTabKey(item.tab || activeTab.value)
  const currentTab = normalizeTabKey(typeof route.query.tab === 'string' ? route.query.tab : activeTab.value)
  const currentSetting = typeof route.query.setting === 'string' ? route.query.setting : ''

  if (currentTab === nextTab && currentSetting === item.setting) {
    await jumpToRouteSetting(item.setting)
    return
  }

  const nextQuery = {
    ...route.query,
    tab: nextTab,
    setting: item.setting
  }

  router.replace({ query: nextQuery })
}

// Watch for route changes to update active tab
watch(() => route.query.tab, (newTab) => {
  const normalizedTab = normalizeTabKey(newTab)
  if (normalizedTab === 'backup') {
    router.replace('/app/admin/backups')
    return
  }
  if (newTab && normalizedTab !== newTab) {
    router.replace({ query: { ...route.query, tab: normalizedTab } })
    return
  }
  if (normalizedTab && validTabs.includes(normalizedTab) && normalizedTab !== activeTab.value) {
    activeTab.value = normalizedTab
  }
})

watch(
  () => [route.query.tab, route.query.setting],
  ([tab, setting]) => {
    if (route.path !== '/app/admin/settings') return
    if (!setting || typeof setting !== 'string') return

    const tabChanged = typeof tab === 'string' && tab !== activeTab.value
    const delayMs = tabChanged ? 240 : 80
    window.setTimeout(() => {
      void jumpToRouteSetting(setting)
    }, delayMs)
  },
  { immediate: true }
)

// Initialize tab from URL on mount
onMounted(() => {
  const tabParam = route.query.tab
  const normalizedTab = normalizeTabKey(tabParam)
  if (normalizedTab === 'backup') {
    router.replace('/app/admin/backups')
    return
  }
  if (tabParam && normalizedTab !== tabParam) {
    router.replace({ query: { ...route.query, tab: normalizedTab } })
  }
  if (normalizedTab && validTabs.includes(normalizedTab)) {
    activeTab.value = normalizedTab
  }
})
</script>

<style scoped>
.admin-settings {
  padding: 1.5rem;
}

.admin-breadcrumb {
  margin-bottom: 1.5rem;
}

.page-header {
  margin-bottom: 1.5rem;
}

.header-content {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 1rem;
}

.header-text {
  flex: 1;
}

.page-header h1 {
  margin: 0;
  font-size: 1.75rem;
}

.text-muted {
  color: var(--text-color-secondary);
}

.header-actions {
  flex-shrink: 0;
}

.settings-layout { display: grid; grid-template-columns: 15rem minmax(0, 1fr); gap: 1.5rem; }
.settings-nav { display: grid; align-content: start; gap: 1rem; }
.settings-nav-group { display: grid; gap: .25rem; }
.settings-nav h2 { margin: 0 0 .25rem; color: var(--gp-text-muted); font-size: .75rem; letter-spacing: .05em; text-transform: uppercase; }
.settings-nav button { display: flex; align-items: center; gap: .65rem; width: 100%; padding: .65rem .75rem; border: 0; border-radius: var(--gp-radius-medium); background: transparent; color: var(--gp-text-secondary); font: inherit; text-align: left; cursor: pointer; }
.settings-nav button:hover, .settings-nav button.active { background: var(--gp-timeline-blue); color: var(--gp-primary-dark); }
.settings-nav button.active { font-weight: 600; }
.settings-content { min-width: 0; }
.mobile-settings-select { display: none; }

/* Mobile Responsive Styles */
@media (max-width: 768px) {
  .admin-settings {
    padding: 0.75rem;
  }

  .admin-breadcrumb {
    margin-bottom: 0.75rem;
  }

  .page-header {
    margin-bottom: 1rem;
  }

  .page-header h1 {
    font-size: 1.5rem;
  }

  .header-content {
    flex-direction: column;
    gap: 0.75rem;
  }

  .header-actions {
    width: 100%;
    display: flex;
    justify-content: flex-end;
  }

  .settings-layout { grid-template-columns: 1fr; gap: 1rem; }
  .settings-nav { display: none; }
  .mobile-settings-select { display: grid; gap: .35rem; color: var(--gp-text-secondary); font-size: .85rem; font-weight: 600; }
  .mobile-settings-select select { width: 100%; min-height: 2.75rem; padding: 0 .75rem; border: 1px solid var(--gp-border-medium); border-radius: var(--gp-radius-medium); background: var(--gp-surface-white); color: var(--gp-text-primary); font: inherit; }

  /* Override TabContainer for horizontal scroll */
  .settings-tabs :deep(.tab-menu) {
    display: flex;
    flex-wrap: nowrap;
    overflow-x: auto;
    overflow-y: hidden;
    -webkit-overflow-scrolling: touch;
    scrollbar-width: thin;
    gap: 0.5rem;
    padding-bottom: 0.5rem;
  }

  .settings-tabs :deep(.tab-menu::-webkit-scrollbar) {
    height: 4px;
  }

  .settings-tabs :deep(.tab-menu::-webkit-scrollbar-thumb) {
    background: var(--surface-border);
    border-radius: 2px;
  }

  .settings-tabs :deep(.tab-menu-item) {
    flex-shrink: 0;
    white-space: nowrap;
    font-size: 0.875rem;
    padding: 0.5rem 1rem;
  }
}

/* Extra small screens */
@media (max-width: 480px) {
  .admin-settings {
    padding: 0.5rem;
  }

  .page-header h1 {
    font-size: 1.25rem;
  }

  .settings-tabs :deep(.tab-menu-item) {
    font-size: 0.8rem;
    padding: 0.4rem 0.75rem;
  }
}
</style>
