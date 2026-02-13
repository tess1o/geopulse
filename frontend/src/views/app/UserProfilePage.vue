<template>
  <AppLayout>
    <PageContainer>
      <div class="user-profile-page">
        <!-- Page Header -->
        <div class="page-header">
          <div class="header-content">
            <div class="header-text">
              <h1 class="page-title">User Profile</h1>
              <p class="page-description">
                Manage your personal information and security settings
              </p>
            </div>
          </div>
        </div>

        <!-- Profile Content -->
        <div class="profile-content">
          <TabContainer
            :tabs="tabItems"
            :activeIndex="activeTabIndex"
            @tab-change="handleTabChange"
            class="profile-tabs"
          >
            <keep-alive>
              <component :is="currentTabComponent" :key="activeTab" />
            </keep-alive>
          </TabContainer>
        </div>

        <Toast />
      </div>
    </PageContainer>
  </AppLayout>
</template>

<script setup>
import { ref, computed, onMounted, h } from 'vue'
import { storeToRefs } from 'pinia'
import { useToast } from 'primevue/usetoast'
import { useRoute } from 'vue-router'

// Layout components
import AppLayout from '@/components/ui/layout/AppLayout.vue'
import PageContainer from '@/components/ui/layout/PageContainer.vue'
import TabContainer from '@/components/ui/layout/TabContainer.vue'

// Tab components
import ProfileTab from '@/components/profile/ProfileTab.vue'
import SecurityTab from '@/components/profile/SecurityTab.vue'
import AIAssistantTab from '@/components/profile/AIAssistantTab.vue'
import ImmichTab from '@/components/profile/ImmichTab.vue'
import TimelineDisplayTab from '@/components/profile/TimelineDisplayTab.vue'

// Store
import { useAuthStore } from '@/stores/auth'
import { useImmichStore } from '@/stores/immich'
import apiService from "@/utils/apiService"

// Composables
const toast = useToast()
const route = useRoute()
const authStore = useAuthStore()
const immichStore = useImmichStore()

// Store refs
const { userId, userName, userAvatar, userEmail, hasPassword, userTimezone, customMapTileUrl, measureUnit, defaultRedirectUrl } = storeToRefs(authStore)
const { config: immichConfig, configLoading: immichLoading } = storeToRefs(immichStore)

// State
const activeTab = ref('profile')

// AI Settings state
const aiSettings = ref({
  enabled: false,
  openaiApiKey: '',
  openaiApiUrl: 'https://api.openai.com/v1',
  openaiModel: 'gpt-3.5-turbo',
  openaiApiKeyConfigured: false,
  customSystemMessage: null,
})

// Timeline Display Preferences state
const timelineDisplayPrefs = ref({
  pathSimplificationEnabled: true,
  pathSimplificationTolerance: 15.0,
  pathMaxPoints: 0,
  pathAdaptiveSimplification: true
})

// Tab configuration
const tabItems = ref([
  {
    label: 'Profile',
    icon: 'pi pi-user',
    key: 'profile'
  },
  {
    label: 'Security',
    icon: 'pi pi-shield',
    key: 'security'
  },
  {
    label: 'Display',
    icon: 'pi pi-eye',
    key: 'timelineDisplay'
  },
  {
    label: 'AI Assistant',
    icon: 'pi pi-sparkles',
    key: 'ai'
  },
  {
    label: 'Immich',
    icon: 'pi pi-images',
    key: 'immich'
  }
])

const activeTabIndex = computed(() => {
  return tabItems.value.findIndex(tab => tab.key === activeTab.value)
})

const currentTabComponent = computed(() => {
  const components = {
    profile: {
      component: ProfileTab,
      props: {
        userName: userName.value,
        userEmail: userEmail.value,
        userAvatar: userAvatar.value,
        userTimezone: userTimezone.value,
        userMeasureUnit: measureUnit.value || 'METRIC',
        userDefaultRedirectUrl: defaultRedirectUrl.value || ''
      },
      handlers: {
        onSave: handleProfileSave
      }
    },
    security: {
      component: SecurityTab,
      props: {
        hasPassword: hasPassword.value
      },
      handlers: {
        onSave: handlePasswordSave
      }
    },
    timelineDisplay: {
      component: TimelineDisplayTab,
      props: {
        initialPreferences: timelineDisplayPrefs.value
      },
      handlers: {
        onSave: handleTimelineDisplaySave
      }
    },
    ai: {
      component: AIAssistantTab,
      props: {
        initialSettings: aiSettings.value
      },
      handlers: {
        onSave: handleAISave
      }
    },
    immich: {
      component: ImmichTab,
      props: {
        config: immichConfig.value,
        loading: immichLoading.value
      },
      handlers: {
        onSave: handleImmichSave
      }
    }
  }

  const tabConfig = components[activeTab.value]
  if (!tabConfig) return null

  return h(tabConfig.component, { ...tabConfig.props, ...tabConfig.handlers })
})

// Methods
const handleTabChange = (event) => {
  const selectedTab = tabItems.value[event.index]
  if (selectedTab) {
    activeTab.value = selectedTab.key
  }
}

const getErrorMessage = (error) => {
  if (error.response?.data?.message) {
    return error.response.data.message
  }

  if (error.response?.status === 403) {
    return 'Current password is incorrect'
  }

  if (error.response?.status === 400) {
    return 'Please check your information and try again'
  }

  return error.message || 'An unexpected error occurred'
}

// Profile Save Handler
const handleProfileSave = async (data) => {
  try {
    await authStore.updateProfile(
      data.fullName,
      data.avatar,
      data.timezone,
      null, // customMapTileUrl - now handled in Timeline Display tab
      data.measureUnit,
      data.defaultRedirectUrl,
      userId.value
    )

    toast.add({
      severity: 'success',
      summary: 'Profile Updated',
      detail: 'Your profile has been updated successfully',
      life: 3000
    })
  } catch (error) {
    toast.add({
      severity: 'error',
      summary: 'Update Failed',
      detail: getErrorMessage(error),
      life: 5000
    })
    throw error // Re-throw to let component handle loading state
  }
}

// Timeline Display Save Handler
const handleTimelineDisplaySave = async (displayPrefs) => {
  try {
    await apiService.put('/users/preferences/timeline/display', displayPrefs)

    // Update local state
    timelineDisplayPrefs.value = { ...displayPrefs }

    // Update custom map tile URL in auth store if it changed
    if (displayPrefs.customMapTileUrl !== undefined) {
      // Update the store directly to reflect the change in UI
      if (authStore.user) {
        authStore.user.customMapTileUrl = displayPrefs.customMapTileUrl
      }
      // Also update localStorage
      const userInfo = JSON.parse(localStorage.getItem('userInfo') || '{}')
      userInfo.customMapTileUrl = displayPrefs.customMapTileUrl
      localStorage.setItem('userInfo', JSON.stringify(userInfo))
    }

    toast.add({
      severity: 'success',
      summary: 'Display Settings Updated',
      detail: 'Your timeline display preferences have been saved. Changes are visible immediately.',
      life: 3000
    })

    return true // Success
  } catch (error) {
    toast.add({
      severity: 'error',
      summary: 'Update Failed',
      detail: getErrorMessage(error),
      life: 5000
    })
    return false // Failure
  }
}

// Password Save Handler
const handlePasswordSave = async (data) => {
  try {
    await authStore.changePassword(
      data.currentPassword,
      data.newPassword,
      userId.value
    )

    // Update hasPassword flag if this was the first time setting a password
    if (!hasPassword.value && authStore.user) {
      authStore.user.hasPassword = true
      const userInfo = JSON.parse(localStorage.getItem('userInfo') || '{}')
      userInfo.hasPassword = true
      localStorage.setItem('userInfo', JSON.stringify(userInfo))
    }

    toast.add({
      severity: 'success',
      summary: hasPassword.value ? 'Password Changed' : 'Password Set',
      detail: hasPassword.value ? 'Your password has been changed successfully' : 'Your password has been set successfully',
      life: 3000
    })
  } catch (error) {
    toast.add({
      severity: 'error',
      summary: hasPassword.value ? 'Password Change Failed' : 'Password Set Failed',
      detail: getErrorMessage(error),
      life: 5000
    })
    throw error
  }
}

// AI Settings Save Handler
const handleAISave = async (payload) => {
  try {
    await apiService.post('/ai/settings', payload)

    // Reload settings to get updated configuration status
    await loadAISettings()

    toast.add({
      severity: 'success',
      summary: 'Success',
      detail: 'AI settings saved successfully',
      life: 3000
    })
  } catch (error) {
    console.error('Error saving AI settings:', error)
    toast.add({
      severity: 'error',
      summary: 'Error',
      life: 5000,
      detail: getErrorMessage(error)
    })
    throw error
  }
}

// Immich Save Handler
const handleImmichSave = async (configData) => {
  try {
    // Handle special case where we want to keep existing API key
    if (configData.apiKey === 'KEEP_EXISTING') {
      configData.apiKey = immichConfig.value?.apiKey || null
    }

    await immichStore.updateConfig(configData)

    toast.add({
      severity: 'success',
      summary: 'Immich Settings Updated',
      detail: 'Your Immich integration settings have been saved successfully',
      life: 3000
    })
  } catch (error) {
    toast.add({
      severity: 'error',
      summary: 'Save Failed',
      detail: getErrorMessage(error),
      life: 5000
    })
    throw error
  }
}

// Load AI Settings
const loadAISettings = async () => {
  try {
    const response = await apiService.get('/ai/settings')

    // Update AI settings with loaded data
    const data = response.data || response
    if (data) {
      aiSettings.value = {
        enabled: data.enabled === true,
        openaiApiKey: '', // Always empty since backend doesn't send actual key
        openaiApiUrl: data.openaiApiUrl || 'https://api.openai.com/v1',
        openaiModel: data.openaiModel || 'gpt-3.5-turbo',
        openaiApiKeyConfigured: data.openaiApiKeyConfigured === true,
        customSystemMessage: data.customSystemMessage,
        apiKeyRequired: data.apiKeyRequired,
      }
    }
  } catch (error) {
    console.warn('Failed to load AI settings:', error)
  }
}

// Load Timeline Display Preferences
const loadTimelineDisplayPreferences = async () => {
  try {
    const response = await apiService.get('/users/preferences/timeline/display')
    const data = response.data || response

    if (data) {
      timelineDisplayPrefs.value = {
        customMapTileUrl: data.customMapTileUrl || '',
        pathSimplificationEnabled: data.pathSimplificationEnabled ?? true,
        pathSimplificationTolerance: data.pathSimplificationTolerance ?? 15.0,
        pathMaxPoints: data.pathMaxPoints ?? 0,
        pathAdaptiveSimplification: data.pathAdaptiveSimplification ?? true
      }
    }
  } catch (error) {
    console.warn('Failed to load timeline display preferences:', error)
  }
}

// Lifecycle
onMounted(async () => {
  // Fetch fresh profile data from backend first
  try {
    await authStore.fetchCurrentUserProfile()
  } catch (error) {
    console.warn('Failed to fetch current user profile from backend, using cached data:', error)
    // Show a toast notification to inform user about using cached data
    toast.add({
      severity: 'warn',
      summary: 'Using Cached Data',
      detail: 'Unable to fetch latest profile data. Showing cached information.',
      life: 4000
    })
  }

  // Load Immich config
  try {
    await immichStore.fetchConfig()
  } catch (error) {
    console.warn('Failed to load Immich config:', error)
  }

  // Load AI settings
  await loadAISettings()

  // Load Timeline Display Preferences
  await loadTimelineDisplayPreferences()

  // Handle tab query parameter
  const tabParam = route.query.tab
  if (tabParam && ['profile', 'security', 'timelineDisplay', 'ai', 'immich'].includes(tabParam)) {
    activeTab.value = tabParam
  }
})
</script>

<style scoped>
.user-profile-page {
  max-width: 900px;
  margin: 0 auto;
  padding: 0 1rem;
}

/* Page Header */
.page-header {
  margin-bottom: 2rem;
}

.header-content {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 2rem;
}

.header-text {
  flex: 1;
}

.page-title {
  font-size: 2rem;
  font-weight: 600;
  color: var(--gp-text-primary);
  margin: 0 0 0.5rem 0;
}

.page-description {
  font-size: 1.1rem;
  color: var(--gp-text-secondary);
  margin: 0;
  line-height: 1.5;
}

/* Profile Content */
.profile-content {
  margin-bottom: 2rem;
}

.profile-tabs {
  width: 100%;
}

/* Fixed width tabs for consistent appearance */
.profile-tabs :deep(.p-tabmenu-item) {
  flex: 1;
  min-width: 120px;
}

.profile-tabs :deep(.p-tabmenu-item .p-tabmenu-item-link) {
  justify-content: center;
  text-align: center;
  width: 100%;
  min-width: 120px;
  padding: var(--gp-spacing-md) var(--gp-spacing-sm);
}

/* Ensure all tab content areas have consistent width */
.profile-tabs :deep(.gp-tab-content) {
  width: 100%;
  box-sizing: border-box;
}

.profile-tabs :deep(.gp-tab-content > div) {
  width: 100% !important;
  max-width: 100% !important;
  box-sizing: border-box;
  margin: 0;
  padding: 0;
}

/* Responsive Design */
@media (max-width: 768px) {
  .user-profile-page {
    padding: 0;
    max-width: 100%;
    box-sizing: border-box;
  }

  .page-header {
    padding: 0 1rem;
  }

  .page-title {
    font-size: 1.5rem;
  }
}

@media (max-width: 480px) {
  .user-profile-page {
    padding: 0;
    max-width: 100%;
    box-sizing: border-box;
  }

  .page-header {
    margin-bottom: 1.5rem;
    padding: 0 1rem;
  }

  .page-title {
    font-size: 1.3rem;
  }

  .page-description {
    font-size: 1rem;
  }
}

/* iPhone 16 Pro Max and similar large phones */
@media (max-width: 480px) and (min-width: 430px) {
  .user-profile-page {
    padding: 0;
    max-width: 100%;
  }

  .profile-tabs :deep(.gp-tab-content > div) {
    width: 100% !important;
    max-width: none !important;
  }
}
</style>
