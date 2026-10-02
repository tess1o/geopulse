<template>
  <AppLayout>
    <div class="gp-admin-page">
      <Breadcrumb :home="breadcrumbHome" :model="breadcrumbItems" class="gp-admin-breadcrumb" />

      <div class="gp-page-header">
        <div class="gp-page-header-content">
          <div class="gp-page-header-text">
            <h1 class="gp-page-title">{{ t('admin.settingsPage.title') }}</h1>
            <p class="gp-page-subtitle">{{ t('admin.settingsPage.subtitle') }}</p>
          </div>
          <div class="gp-page-actions">
            <SettingsSearchTrigger
              page-key="admin"
              :placeholder="t('admin.settingsPage.searchPlaceholder')"
              @navigate="handleSettingsSearchNavigate"
            />
          </div>
        </div>
      </div>

      <DemoReadOnlyBanner />

      <div class="settings-layout">
        <label class="mobile-settings-select">
          <span>{{ t('admin.settingsPage.sectionLabel') }}</span>
          <select :value="activeTab" @change="selectTab($event.target.value)">
            <optgroup v-for="group in settingsGroups" :key="group.label" :label="group.label">
              <option v-for="tab in group.items" :key="tab.key" :value="tab.key">{{ tab.label }}</option>
            </optgroup>
          </select>
        </label>
        <nav class="settings-nav" :aria-label="t('admin.settingsPage.navAriaLabel')">
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
import { useI18n } from 'vue-i18n'
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

const { t } = useI18n()
const router = useRouter()
const route = useRoute()
const toast = useToast()

const breadcrumbHome = ref({
  icon: 'pi pi-home',
  command: () => router.push('/')
})

const breadcrumbItems = computed(() => [
  {
    label: t('admin.breadcrumb.administration'),
    command: () => router.push('/app/admin')
  },
  { label: t('admin.settingsPage.breadcrumbTitle') }
])

// Tab configuration
const activeTab = ref('authentication')

const settingsGroups = computed(() => [
  { label: t('admin.settingsPage.groups.accessSecurity'), items: [
    { label: t('admin.settingsPage.tabs.authentication'), icon: 'pi pi-shield', key: 'authentication' },
    { label: t('admin.settingsPage.tabs.notifications'), icon: 'pi pi-bell', key: 'notifications' }
  ] },
  { label: t('admin.settingsPage.groups.dataProcessing'), items: [
    { label: t('admin.settingsPage.tabs.import'), icon: 'pi pi-upload', key: 'import' },
    { label: t('admin.settingsPage.tabs.export'), icon: 'pi pi-download', key: 'export' }
  ] },
  { label: t('admin.settingsPage.groups.integrationsMaps'), items: [
    { label: t('admin.settingsPage.tabs.geocoding'), icon: 'pi pi-map-marker', key: 'geocoding' },
    { label: t('admin.settingsPage.tabs.weather'), icon: 'pi pi-cloud', key: 'weather' },
    { label: t('admin.settingsPage.tabs.mapMatching'), icon: 'pi pi-map', key: 'map-matching' },
    { label: t('admin.settingsPage.tabs.panoramax'), icon: 'pi pi-images', key: 'panoramax' },
    { label: t('admin.settingsPage.tabs.poiDiscovery'), icon: 'pi pi-compass', key: 'poi' },
    { label: t('admin.settingsPage.tabs.aiAssistant'), icon: 'pi pi-sparkles', key: 'ai' }
  ] },
  { label: t('admin.settingsPage.groups.system'), items: [{ label: t('admin.settingsPage.tabs.system'), icon: 'pi pi-server', key: 'system' }] }
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
        summary: t('admin.settingsPage.settingNotVisibleSummary'),
        detail: hintOverride || t('admin.settingsPage.settingNotVisibleDetail'),
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
  .settings-layout { grid-template-columns: 1fr; gap: 1rem; }
  .settings-nav { display: none; }
  .mobile-settings-select { display: grid; gap: .35rem; color: var(--gp-text-secondary); font-size: .85rem; font-weight: 600; }
  .mobile-settings-select select { width: 100%; min-height: 2.75rem; padding: 0 .75rem; border: 1px solid var(--gp-border-medium); border-radius: var(--gp-radius-medium); background: var(--gp-surface-card); color: var(--gp-text-primary); font: inherit; }

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
    background: var(--gp-border);
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
  .settings-tabs :deep(.tab-menu-item) {
    font-size: 0.8rem;
    padding: 0.4rem 0.75rem;
  }
}
</style>
