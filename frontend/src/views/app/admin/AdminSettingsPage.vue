<template>
  <AppLayout>
    <div class="admin-settings">
      <Breadcrumb :home="breadcrumbHome" :model="breadcrumbItems" class="admin-breadcrumb" />

      <div class="page-header">
        <h1>System Settings</h1>
        <p class="text-muted">Configure system-wide settings</p>
      </div>

      <TabContainer
        :tabs="tabItems"
        :activeIndex="activeTabIndex"
        @tab-change="handleTabChange"
        class="settings-tabs"
      >
        <keep-alive>
          <component :is="currentTabComponent" :key="activeTab" />
        </keep-alive>
      </TabContainer>

      <Toast />
    </div>
  </AppLayout>
</template>

<script setup>
import { ref, computed, watch, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import Toast from 'primevue/toast'
import Breadcrumb from 'primevue/breadcrumb'
import AppLayout from '@/components/ui/layout/AppLayout.vue'
import TabContainer from '@/components/ui/layout/TabContainer.vue'

// Import tab components
import AuthenticationSettingsTab from '@/components/admin/settings/tabs/AuthenticationSettingsTab.vue'
import GeocodingSettingsTab from '@/components/admin/settings/tabs/GeocodingSettingsTab.vue'
import AISettingsTab from '@/components/admin/settings/tabs/AISettingsTab.vue'
import GPSProcessingSettingsTab from '@/components/admin/settings/tabs/GPSProcessingSettingsTab.vue'
import ImportSettingsTab from '@/components/admin/settings/tabs/ImportSettingsTab.vue'
import ExportSettingsTab from '@/components/admin/settings/tabs/ExportSettingsTab.vue'
import SystemSettingsTab from '@/components/admin/settings/tabs/SystemSettingsTab.vue'

const router = useRouter()
const route = useRoute()

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

const tabItems = ref([
  {
    label: 'Authentication',
    icon: 'pi pi-shield',
    key: 'authentication'
  },
  {
    label: 'Geocoding',
    icon: 'pi pi-map-marker',
    key: 'geocoding'
  },
  {
    label: 'AI Assistant',
    icon: 'pi pi-sparkles',
    key: 'ai'
  },
  {
    label: 'GPS Processing',
    icon: 'pi pi-compass',
    key: 'gps'
  },
  {
    label: 'Import',
    icon: 'pi pi-upload',
    key: 'import'
  },
  {
    label: 'Export',
    icon: 'pi pi-download',
    key: 'export'
  },
  {
    label: 'System',
    icon: 'pi pi-server',
    key: 'system'
  }
])

const validTabs = ['authentication', 'geocoding', 'ai', 'gps', 'import', 'export', 'system']

const activeTabIndex = computed(() => {
  return tabItems.value.findIndex(tab => tab.key === activeTab.value)
})

const currentTabComponent = computed(() => {
  const components = {
    authentication: AuthenticationSettingsTab,
    geocoding: GeocodingSettingsTab,
    ai: AISettingsTab,
    gps: GPSProcessingSettingsTab,
    import: ImportSettingsTab,
    export: ExportSettingsTab,
    system: SystemSettingsTab
  }
  return components[activeTab.value]
})

const handleTabChange = (event) => {
  const selectedTab = tabItems.value[event.index]
  if (selectedTab) {
    activeTab.value = selectedTab.key
    // Update URL without reloading page
    router.push({ query: { tab: selectedTab.key } })
  }
}

// Watch for route changes to update active tab
watch(() => route.query.tab, (newTab) => {
  if (newTab && validTabs.includes(newTab) && newTab !== activeTab.value) {
    activeTab.value = newTab
  }
})

// Initialize tab from URL on mount
onMounted(() => {
  const tabParam = route.query.tab
  if (tabParam && validTabs.includes(tabParam)) {
    activeTab.value = tabParam
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

.page-header h1 {
  margin: 0;
  font-size: 1.75rem;
}

.text-muted {
  color: var(--text-color-secondary);
}

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
