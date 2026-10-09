<template>
  <AppLayout>
    <PageContainer>
      <div class="user-profile-page">
        <!-- Page Header -->
        <div class="gp-page-header">
          <div class="gp-page-header-content">
            <div class="gp-page-header-text">
              <h1 class="gp-page-title">{{ t('profile.page.title') }}</h1>
              <p class="gp-page-subtitle">
                {{ t('profile.page.description') }}
              </p>
              <p v-if="activeTab === 'general'" class="account-context">{{ t('profile.page.signedInAs', { email: userEmail }) }}</p>
            </div>
            <div class="gp-page-actions">
              <SettingsSearchTrigger
                page-key="profile"
                :placeholder="t('profile.searchPlaceholder')"
                @navigate="handleSettingsSearchNavigate"
              />
            </div>
          </div>
        </div>

        <Message v-if="demoReadOnly" severity="error" :closable="false" class="demo-read-only-message">
          {{ t('profile.demoReadOnly') }}
        </Message>

        <!-- Profile Content -->
        <div class="profile-content">
          <div class="settings-layout">
            <label class="mobile-settings-select">
              <span>{{ t('profile.mobileSectionLabel') }}</span>
              <select :value="activeTab" @change="selectTab($event.target.value)">
                <optgroup v-for="group in settingsGroups" :key="group.label" :label="group.label">
                  <option v-for="tab in group.items" :key="tab.key" :value="tab.key">{{ tab.label }}</option>
                </optgroup>
              </select>
            </label>
            <nav class="settings-nav" :aria-label="t('profile.sectionsAria')">
              <section v-for="group in settingsGroups" :key="group.label" class="settings-nav-group">
                <h2>{{ group.label }}</h2>
                <button v-for="tab in group.items" :key="tab.key" type="button" :class="{ active: activeTab === tab.key }" @click="selectTab(tab.key)">
                  <i :class="tab.icon" aria-hidden="true" />{{ tab.label }}
                </button>
              </section>
            </nav>
            <section class="settings-content">
            <keep-alive>
              <component
                :is="currentTabComponent"
                :key="activeTab"
                v-bind="currentTabProps"
                v-on="currentTabHandlers"
              />
            </keep-alive>
            </section>
          </div>
        </div>

        <Toast />
        <ConfirmDialog group="profile-unsaved-changes" />
      </div>
    </PageContainer>
  </AppLayout>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { storeToRefs } from 'pinia'
import { useToast } from 'primevue/usetoast'
import { useConfirm } from 'primevue/useconfirm'
import { useRoute, useRouter, onBeforeRouteLeave } from 'vue-router'
import ConfirmDialog from 'primevue/confirmdialog'
import Message from 'primevue/message'

// Layout components
import AppLayout from '@/components/ui/layout/AppLayout.vue'
import PageContainer from '@/components/ui/layout/PageContainer.vue'

// Tab components
import ProfileTab from '@/components/profile/ProfileTab.vue'
import SecurityTab from '@/components/profile/SecurityTab.vue'
import TimelineDisplayTab from '@/components/profile/TimelineDisplayTab.vue'
import AppearanceTab from '@/components/profile/AppearanceTab.vue'
import ConnectedAppsTab from '@/components/profile/ConnectedAppsTab.vue'
import NotificationsPreferencesTab from '@/components/profile/NotificationsPreferencesTab.vue'
import SettingsSearchTrigger from '@/components/search/SettingsSearchTrigger.vue'

// Store
import { useAuthStore } from '@/stores/auth'
import { useImmichStore } from '@/stores/immich'
import { useNotesStore } from '@/stores/notes'
import { useAIStore } from '@/stores/ai'
import { PROFILE_SETTINGS_SEARCH_INDEX } from '@/constants/profileSettingsSearchIndex'
import { APPEARANCE_PREFERENCE_DEFAULTS, APPEARANCE_PREFERENCE_KEYS } from '@/maps/shared/mapAppearance'
import { jumpToSetting } from '@/utils/settingJump'
import { showDemoModeToast } from '@/utils/demoMode'
import { formatApiErrorDetail } from '@/utils/apiErrorDetail'

// Composables
const { t } = useI18n()
const toast = useToast()
const confirm = useConfirm()
const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const immichStore = useImmichStore()
const notesStore = useNotesStore()
const aiStore = useAIStore()

// Store refs
const { userId, userName, userAvatar, userEmail, hasPassword, userTimezone, customMapTileUrl, customMapStyleUrl, mapRenderMode, distanceUnit, temperatureUnit, defaultRedirectUrl, dateFormat, timeFormat, timeDisplayMode, language, defaultDateRangePreset, autoShowTripReplayControls, enable3dBuildingsByDefault, mapMatchingAvailable, demoReadOnly } = storeToRefs(authStore)
const { config: immichConfig, configLoading: immichLoading } = storeToRefs(immichStore)
const { memosConfig, configLoading: memosLoading } = storeToRefs(notesStore)

// State
const activeTab = ref('general')
const validTabs = ['general', 'security', 'timeline', 'appearance', 'notifications', 'connectedApps']
const profileUnsavedConfirmGroup = 'profile-unsaved-changes'
const settingHintsById = Object.fromEntries(
  PROFILE_SETTINGS_SEARCH_INDEX
    .filter((item) => item.visibilityHint)
    .map((item) => [item.id, item.visibilityHint])
)

const aiSettings = computed(() => {
  const settings = aiStore.settings || {}
  return {
    enabled: settings.enabled === true,
    openaiApiKey: '',
    openaiApiUrl: settings.openaiApiUrl || 'https://api.openai.com/v1',
    openaiModel: settings.openaiModel || 'gpt-3.5-turbo',
    openaiApiKeyConfigured: settings.openaiApiKeyConfigured === true,
    customSystemMessage: settings.customSystemMessage || null,
    apiKeyRequired: settings.apiKeyRequired !== false
  }
})

// Both the Timeline & Map and the Appearance tabs edit this one preferences document.
const pickAppearancePreferences = (source = {}) => Object.fromEntries(APPEARANCE_PREFERENCE_KEYS.map((key) => {
  const value = source?.[key]
  return [key, value === undefined || value === null ? APPEARANCE_PREFERENCE_DEFAULTS[key] : value]
}))

// Timeline Display Preferences state
const timelineDisplayPrefs = ref({
  ...pickAppearancePreferences(authStore.user || {}),
  customMapTileUrl: customMapTileUrl.value || '',
  customMapStyleUrl: customMapStyleUrl.value || '',
  mapRenderMode: mapRenderMode.value || 'VECTOR',
  pathSimplificationEnabled: true,
  pathSimplificationTolerance: 15.0,
  pathMaxPoints: 0,
  pathAdaptiveSimplification: true,
  defaultDateRangePreset: defaultDateRangePreset.value || '',
  showCurrentLocationTelemetry: true,
  autoShowTripReplayControls: autoShowTripReplayControls.value ?? true,
  enable3dBuildingsByDefault: enable3dBuildingsByDefault.value ?? false,
  mapMatchingEnabled: false,
  mapMatchingExcludedMovementTypes: [],
  mapMatchingAvailable: mapMatchingAvailable.value ?? false
})

// Tab configuration. Labels resolve from the catalogs in this computed, so the navigation and the
// mobile `select` both re-render when the language changes. `key` and `icon` are identities, not copy.
const settingsGroups = computed(() => [
  { label: t('profile.groups.personal'), items: [
    { label: t('profile.tabs.general'), icon: 'pi pi-user', key: 'general' },
    { label: t('profile.tabs.security'), icon: 'pi pi-shield', key: 'security' }
  ] },
  { label: t('profile.groups.experience'), items: [
    { label: t('profile.tabs.timeline'), icon: 'pi pi-map', key: 'timeline' },
    { label: t('profile.tabs.appearance'), icon: 'pi pi-palette', key: 'appearance' },
    { label: t('profile.tabs.notifications'), icon: 'pi pi-bell', key: 'notifications' }
  ] },
  { label: t('profile.groups.connectedApps'), items: [{ label: t('profile.tabs.connectedApps'), icon: 'pi pi-box', key: 'connectedApps' }] }
])
const legacyTabs = { profile: 'general', account: 'general', preferences: 'general', timelineDisplay: 'timeline', accessibility: 'appearance', ai: 'connectedApps', immich: 'connectedApps', memos: 'connectedApps' }
const legacyApps = { ai: 'ai', immich: 'immich', memos: 'memos' }

// Stable map from key → component definition so keep-alive can cache by component name
const tabComponents = {
  general: ProfileTab,
  security: SecurityTab,
  timeline: TimelineDisplayTab,
  appearance: AppearanceTab,
  notifications: NotificationsPreferencesTab,
  connectedApps: ConnectedAppsTab,
}

const currentTabComponent = computed(() => tabComponents[activeTab.value] || null)
const dirtyTabs = ref({})
const hasUnsavedChanges = computed(() => Object.values(dirtyTabs.value).some(Boolean))

const handleTabDirtyChange = (tabKey, isDirty) => {
  dirtyTabs.value = {
    ...dirtyTabs.value,
    [tabKey]: Boolean(isDirty)
  }
}

const currentTabProps = computed(() => {
  const allProps = {
    general: {
      readOnly: demoReadOnly.value,
      userName: userName.value,
      userEmail: userEmail.value,
      userAvatar: userAvatar.value,
      userTimezone: userTimezone.value,
      userDistanceUnit: distanceUnit.value || 'KILOMETERS',
      userTemperatureUnit: temperatureUnit.value || 'CELSIUS',
      userDefaultRedirectUrl: defaultRedirectUrl.value || '',
      userDateFormat: dateFormat.value || 'MDY',
      userTimeFormat: timeFormat.value || '24h',
      userTimeDisplayMode: timeDisplayMode.value || 'profile',
      userLanguage: language.value || 'en'
    },
    security: {
      readOnly: demoReadOnly.value,
      hasPassword: hasPassword.value,
    },
    timeline: {
      readOnly: demoReadOnly.value,
      initialPreferences: timelineDisplayPrefs.value,
    },
    appearance: {
      readOnly: demoReadOnly.value,
      initialPreferences: timelineDisplayPrefs.value,
    },
    notifications: { readOnly: demoReadOnly.value },
    connectedApps: { readOnly: demoReadOnly.value, activeApp: route.query.app || 'ai', aiSettings: aiSettings.value, immichConfig: immichConfig.value, immichLoading: immichLoading.value, memosConfig: memosConfig.value, memosLoading: memosLoading.value },
  }
  return allProps[activeTab.value] || {}
})

const currentTabHandlers = computed(() => {
  const handlers = {
    general: { save: handleProfileSave, 'dirty-change': (isDirty) => handleTabDirtyChange('general', isDirty) },
    security: { save: handlePasswordSave, 'dirty-change': (isDirty) => handleTabDirtyChange('security', isDirty) },
    timeline: {
      save: handleTimelineDisplaySave,
      'dirty-change': (isDirty) => handleTabDirtyChange('timeline', isDirty)
    },
    appearance: {
      save: handleTimelineDisplaySave,
      'dirty-change': (isDirty) => handleTabDirtyChange('appearance', isDirty)
    },
    notifications: { saved: () => toast.add({ severity: 'success', summary: t('profile.save.notificationsSaved'), life: 3000 }) },
    connectedApps: {
      'ai-save': handleAISave, 'immich-save': handleImmichSave, 'memos-save': handleMemosSave,
      'dirty-change': ({ key, dirty }) => handleTabDirtyChange(key, dirty),
      'select-app': (app) => router.replace({ query: { ...route.query, tab: 'connectedApps', app } })
    },
  }
  return handlers[activeTab.value] || {}
})

// Methods
const selectTab = (tab) => {
  if (!validTabs.includes(tab)) return
  activeTab.value = tab
  const nextQuery = { ...route.query, tab }
  delete nextQuery.setting
  if (tab !== 'connectedApps') delete nextQuery.app
  router.replace({ query: nextQuery })
}

const getErrorMessage = (error) => {
  if (error?.isApiError || error?.response?.data?.detail) {
    return formatApiErrorDetail(error)
  }

  if (error.response?.data?.message) {
    return error.response.data.message
  }

  if (error.response?.status === 403) {
    return t('profile.errors.incorrectPassword')
  }

  if (error.response?.status === 400) {
    return t('profile.errors.checkInformation')
  }

  return error.message || t('errors.unexpected')
}

const showDemoReadOnlyToast = () => {
  showDemoModeToast(toast, t('profile.demoReadOnlyToast'), { severity: 'info' })
}

const jumpToRouteSetting = async (settingId, hintOverride = null) => {
  if (!settingId || route.path !== '/app/profile') return false

  const hint = hintOverride || settingHintsById[settingId]
  return jumpToSetting(settingId, {
    onMissing: () => {
      toast.add({
        severity: 'info',
        summary: t('profile.jump.notVisibleTitle'),
        detail: hint || t('profile.jump.notVisibleDetail'),
        life: 4000
      })
    }
  })
}

const handleSettingsSearchNavigate = async (item) => {
  if (!item?.setting) return

  const nextTab = legacyTabs[item.tab] || item.tab || activeTab.value
  const nextApp = legacyApps[item.tab]
  const currentTab = typeof route.query.tab === 'string' ? route.query.tab : activeTab.value
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
  if (nextApp) nextQuery.app = nextApp

  router.replace({ query: nextQuery })
}

// Profile Save Handler
const handleProfileSave = async (data) => {
  if (demoReadOnly.value) {
    showDemoReadOnlyToast()
    return
  }

  try {
    let avatarToSave = data.avatar
    if (data.avatarFile) {
      avatarToSave = await authStore.uploadAvatar(data.avatarFile)
    }

    await authStore.updateProfile({
      fullName: data.fullName,
      avatar: avatarToSave,
      timezone: data.timezone,
      distanceUnit: data.distanceUnit,
      temperatureUnit: data.temperatureUnit,
      defaultRedirectUrl: data.defaultRedirectUrl,
      dateFormat: data.dateFormat,
      timeFormat: data.timeFormat,
      timeDisplayMode: data.timeDisplayMode,
      language: data.language
    })

    toast.add({
      severity: 'success',
      summary: t('profile.save.profileUpdated.title'),
      detail: t('profile.save.profileUpdated.detail'),
      life: 3000
    })
  } catch (error) {
    toast.add({
      severity: 'error',
      summary: t('profile.save.updateFailed'),
      detail: getErrorMessage(error),
      life: 5000
    })
    throw error // Re-throw to let component handle loading state
  }
}

// Timeline Display Save Handler
const handleTimelineDisplaySave = async (displayPrefs) => {
  if (demoReadOnly.value) {
    showDemoReadOnlyToast()
    return false
  }

  try {
    const savedDisplayPrefs = await authStore.updateTimelineDisplayPreferences(displayPrefs)

    // Update local state from canonical backend response when available. The response omits unset
    // (follow-the-scheme) appearance values, so those are re-derived rather than kept from before the save.
    timelineDisplayPrefs.value = savedDisplayPrefs
      ? {
          ...timelineDisplayPrefs.value,
          ...savedDisplayPrefs,
          ...pickAppearancePreferences(savedDisplayPrefs)
        }
      : { ...timelineDisplayPrefs.value, ...displayPrefs }

    toast.add({
      severity: 'success',
      summary: t('profile.save.displayUpdated.title'),
      detail: t('profile.save.displayUpdated.detail'),
      life: 3000
    })

    return true // Success
  } catch (error) {
    toast.add({
      severity: 'error',
      summary: t('profile.save.updateFailed'),
      detail: getErrorMessage(error),
      life: 5000
    })
    return false // Failure
  }
}

// Password Save Handler
const handlePasswordSave = async (data) => {
  if (demoReadOnly.value) {
    showDemoReadOnlyToast()
    return
  }

  try {
    await authStore.changePassword(
      data.currentPassword,
      data.newPassword
    )

    toast.add({
      severity: 'success',
      summary: hasPassword.value ? t('profile.save.passwordChanged.title') : t('profile.save.passwordSet.title'),
      detail: hasPassword.value ? t('profile.save.passwordChanged.detail') : t('profile.save.passwordSet.detail'),
      life: 3000
    })
  } catch (error) {
    toast.add({
      severity: 'error',
      summary: hasPassword.value ? t('profile.save.passwordChangeFailed') : t('profile.save.passwordSetFailed'),
      detail: getErrorMessage(error),
      life: 5000
    })
    throw error
  }
}

// AI Settings Save Handler
const handleAISave = async (payload) => {
  if (demoReadOnly.value) {
    showDemoReadOnlyToast()
    return
  }

  try {
    await aiStore.saveSettings(payload)

    toast.add({
      severity: 'success',
      summary: t('profile.save.aiSaved.title'),
      detail: t('profile.save.aiSaved.detail'),
      life: 3000
    })
  } catch (error) {
    console.error('Error saving AI settings:', error)
    toast.add({
      severity: 'error',
      summary: t('profile.save.aiError'),
      life: 5000,
      detail: getErrorMessage(error)
    })
    throw error
  }
}

// Immich Save Handler
const handleImmichSave = async (configData) => {
  if (demoReadOnly.value) {
    showDemoReadOnlyToast()
    return
  }

  try {
    // Handle special case where we want to keep existing API key
    if (configData.apiKey === 'KEEP_EXISTING') {
      configData.apiKey = immichConfig.value?.apiKey || null
    }

    await immichStore.updateConfig(configData)

    toast.add({
      severity: 'success',
      summary: t('profile.save.immichUpdated.title'),
      detail: t('profile.save.immichUpdated.detail'),
      life: 3000
    })
  } catch (error) {
    toast.add({
      severity: 'error',
      summary: t('profile.save.saveFailed'),
      detail: getErrorMessage(error),
      life: 5000
    })
    throw error
  }
}

// Memos Save Handler
const handleMemosSave = async (configData) => {
  if (demoReadOnly.value) {
    showDemoReadOnlyToast()
    return
  }

  try {
    if (configData.apiKey === 'KEEP_EXISTING') {
      configData.apiKey = memosConfig.value?.apiKey || null
    }

    await notesStore.updateMemosConfig(configData)

    toast.add({
      severity: 'success',
      summary: t('profile.save.memosUpdated.title'),
      detail: t('profile.save.memosUpdated.detail'),
      life: 3000
    })
  } catch (error) {
    toast.add({
      severity: 'error',
      summary: t('profile.save.saveFailed'),
      detail: getErrorMessage(error),
      life: 5000
    })
    throw error
  }
}

// Load AI Settings
const loadAISettings = async () => {
  try {
    await aiStore.fetchSettings()
  } catch (error) {
    console.warn('Failed to load AI settings:', error)
  }
}

// Load Timeline Display Preferences
const loadTimelineDisplayPreferences = async () => {
  try {
    const data = await authStore.fetchTimelineDisplayPreferences()

    if (data) {
      timelineDisplayPrefs.value = {
        customMapTileUrl: data.customMapTileUrl || '',
        customMapStyleUrl: data.customMapStyleUrl || '',
        mapRenderMode: data.mapRenderMode || 'VECTOR',
        pathSimplificationEnabled: data.pathSimplificationEnabled ?? true,
        pathSimplificationTolerance: data.pathSimplificationTolerance ?? 15.0,
        pathMaxPoints: data.pathMaxPoints ?? 0,
        pathAdaptiveSimplification: data.pathAdaptiveSimplification ?? true,
        defaultDateRangePreset: data.defaultDateRangePreset || '',
        showCurrentLocationTelemetry: data.showCurrentLocationTelemetry ?? true,
        autoShowTripReplayControls: data.autoShowTripReplayControls ?? true,
        enable3dBuildingsByDefault: data.enable3dBuildingsByDefault ?? false,
        mapMatchingEnabled: data.mapMatchingEnabled ?? false,
        mapMatchingExcludedMovementTypes: Array.isArray(data.mapMatchingExcludedMovementTypes)
          ? data.mapMatchingExcludedMovementTypes
          : [],
        mapMatchingAvailable: data.mapMatchingAvailable ?? false,
        ...pickAppearancePreferences(data)
      }
    }
  } catch (error) {
    console.warn('Failed to load timeline display preferences:', error)
  }
}

watch(() => route.query.tab, (newTab) => {
  const normalizedTab = legacyTabs[newTab] || newTab
  if (normalizedTab && validTabs.includes(normalizedTab)) {
    activeTab.value = normalizedTab
  }
})

watch(
  () => [route.query.tab, route.query.setting],
  ([tab, setting]) => {
    if (route.path !== '/app/profile') return
    if (!setting || typeof setting !== 'string') return

    const normalizedTab = legacyTabs[tab] || tab
    const tabChanged = typeof normalizedTab === 'string' && normalizedTab !== activeTab.value
    const delayMs = tabChanged ? 240 : 80
    window.setTimeout(() => {
      void jumpToRouteSetting(setting)
    }, delayMs)
  },
  { immediate: true }
)

onBeforeRouteLeave((to, from, next) => {
  if (to.path === from.path || !hasUnsavedChanges.value) {
    next()
    return
  }

  let guardResolved = false
  const resolveGuard = (allowNavigation) => {
    if (guardResolved) {
      return
    }

    guardResolved = true
    if (allowNavigation) {
      next()
    } else {
      next(false)
    }
  }

  confirm.require({
    group: profileUnsavedConfirmGroup,
    message: t('profile.unsaved.message'),
    header: t('profile.unsaved.header'),
    icon: 'pi pi-exclamation-triangle',
    acceptLabel: t('profile.unsaved.leave'),
    rejectLabel: t('profile.unsaved.stay'),
    acceptClass: 'p-button-danger',
    rejectClass: 'p-button-secondary p-button-outlined',
    accept: () => {
      resolveGuard(true)
    },
    reject: () => {
      resolveGuard(false)
    },
    onHide: () => {
      resolveGuard(false)
    }
  })
})

const handleBeforeUnload = (event) => {
  if (!hasUnsavedChanges.value) {
    return
  }

  event.preventDefault()
  event.returnValue = ''
  return ''
}

// Lifecycle
onMounted(async () => {
  window.addEventListener('beforeunload', handleBeforeUnload)

  // Fetch fresh profile data from backend first
  try {
    await authStore.fetchCurrentUserProfile()
  } catch (error) {
    console.warn('Failed to fetch current user profile from backend, using cached data:', error)
    // Show a toast notification to inform user about using cached data
    toast.add({
      severity: 'warn',
      summary: t('profile.cached.title'),
      detail: t('profile.cached.detail'),
      life: 4000
    })
  }

  // Load Immich config
  try {
    await immichStore.fetchConfig()
  } catch (error) {
    console.warn('Failed to load Immich config:', error)
  }

  // Load Memos config
  try {
    await notesStore.fetchMemosConfig()
  } catch (error) {
    console.warn('Failed to load Memos config:', error)
  }

  // Load AI settings
  await loadAISettings()

  // Load Timeline Display Preferences
  await loadTimelineDisplayPreferences()

  // Handle tab query parameter
  const tabParam = route.query.tab
  const normalizedTab = legacyTabs[tabParam] || tabParam
  if (normalizedTab && validTabs.includes(normalizedTab)) {
    activeTab.value = normalizedTab
    if (normalizedTab !== tabParam) {
      router.replace({ query: { ...route.query, tab: normalizedTab, ...(legacyApps[tabParam] ? { app: legacyApps[tabParam] } : {}) } })
    }
  }
})

onUnmounted(() => {
  window.removeEventListener('beforeunload', handleBeforeUnload)
})
</script>

<style scoped>
.user-profile-page {
  width: 100%;
  padding: 0 1rem;
  box-sizing: border-box;
}

.account-context { margin: .35rem 0 0; color: var(--gp-text-secondary); font-size: .9rem; }

/* Profile Content */
.profile-content {
  margin-bottom: 2rem;
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

:deep(.profile-settings-card.p-card) {
  width: 100%;
  background: var(--gp-surface-card);
  border: 1px solid var(--gp-border);
  border-radius: var(--gp-radius-large);
  box-shadow: var(--gp-shadow-light);
  box-sizing: border-box;
  overflow: hidden;
}

:deep(.profile-settings-card .p-card-body) {
  width: 100%;
  padding: var(--gp-spacing-lg);
  box-sizing: border-box;
}

:deep(.profile-settings-card .p-card-content) {
  width: 100%;
  padding: 0;
  box-sizing: border-box;
}

:deep(.settings-tab) {
  display: flex;
  flex-direction: column;
  gap: var(--gp-spacing-xl);
}

:deep(.settings-tab-header) {
  display: flex;
  align-items: center;
  gap: var(--gp-spacing-md);
  padding: var(--gp-spacing-md);
  background: var(--gp-surface-muted);
  border-radius: var(--gp-radius-medium);
}

:deep(.settings-tab-icon) {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 2.5rem;
  height: 2.5rem;
  flex: 0 0 auto;
  border-radius: 50%;
  background: var(--gp-primary);
  color: white;
  font-size: 1.25rem;
}

:deep(.settings-tab-info) {
  min-width: 0;
  flex: 1;
}

:deep(.settings-tab-title),
:deep(.settings-group-header h3) {
  margin: 0;
  color: var(--gp-text-primary);
  font-size: 1rem;
  font-weight: 600;
}

:deep(.settings-tab-description),
:deep(.settings-group-header p) {
  margin: var(--gp-spacing-xs) 0 0;
  color: var(--gp-text-secondary);
  font-size: 0.85rem;
  line-height: 1.4;
}

:deep(.settings-group) {
  display: flex;
  flex-direction: column;
  gap: var(--gp-spacing-sm);
}

:deep(.settings-group-header) {
  padding: 0 var(--gp-spacing-xs);
}

:deep(.settings-group-header.has-action) {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: var(--gp-spacing-lg);
}

:deep(.field-control) {
  display: flex;
  flex-direction: column;
  gap: var(--gp-spacing-xs);
  width: 100%;
  min-width: 0;
}

:deep(.field-sub-label) {
  color: var(--gp-text-secondary);
  font-size: 0.8rem;
  font-weight: 600;
}

:deep(.error-message) {
  color: var(--gp-danger);
  font-size: 0.8rem;
  line-height: 1.3;
}

:deep(.settings-actions) {
  z-index: 1;
  display: flex;
  justify-content: flex-end;
  gap: var(--gp-spacing-sm);
  padding: var(--gp-spacing-sm);
  background: var(--gp-surface-card);
  border: 1px solid var(--gp-border);
  border-radius: var(--gp-radius-medium);
  box-shadow: var(--gp-shadow-light);
}

:deep(.settings-actions.is-sticky) {
  position: sticky;
  bottom: 1rem;
}

:deep(.profile-section-card.p-card) {
  width: 100%;
  background: var(--gp-surface-card);
  border: 1px solid var(--gp-border);
  border-radius: var(--gp-radius-large);
  box-shadow: var(--gp-shadow-light);
  box-sizing: border-box;
  overflow: hidden;
}

:deep(.profile-section-card .p-card-body) {
  width: 100%;
  box-sizing: border-box;
  padding: 1.5rem;
}

:deep(.profile-section-card .p-card-content) {
  width: 100%;
  box-sizing: border-box;
}

/* Responsive Design */
@media (max-width: 768px) {
  .user-profile-page {
    padding: 0;
    max-width: 100%;
    box-sizing: border-box;
  }

  .gp-page-header { padding: 0 1rem; }
  .settings-layout { grid-template-columns: 1fr; gap: 1rem; }
  .settings-nav { display: none; }
  .mobile-settings-select { display: grid; gap: .35rem; color: var(--gp-text-secondary); font-size: .85rem; font-weight: 600; padding: 0 1rem; }
  .mobile-settings-select select { width: 100%; min-height: 2.75rem; padding: 0 .75rem; border: 1px solid var(--gp-border-medium); border-radius: var(--gp-radius-medium); background: var(--gp-surface-card); color: var(--gp-text-primary); font: inherit; }

  :deep(.settings-tab-header) {
    align-items: flex-start;
  }

  :deep(.settings-group-header.has-action),
  :deep(.settings-actions) {
    align-items: stretch;
    flex-direction: column-reverse;
  }

  :deep(.settings-group-header.has-action) {
    flex-direction: column;
  }

  :deep(.settings-actions.is-sticky) {
    bottom: .5rem;
  }

  :deep(.settings-actions button),
  :deep(.settings-group-header.has-action button) {
    width: 100%;
  }
}

@media (max-width: 480px) {
  .user-profile-page {
    padding: 0;
    max-width: 100%;
    box-sizing: border-box;
  }

  :deep(.profile-section-card .p-card-body) {
    padding: 1rem;
  }

  :deep(.profile-settings-card .p-card-body) {
    padding: var(--gp-spacing-md);
  }
}
</style>
