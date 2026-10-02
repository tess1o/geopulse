<template>
  <AppLayout>
    <PageContainer>
      <!-- Onboarding Tour -->
      <OnboardingTour />
      
      <div class="location-sources-page">
        <!-- Page Header -->
        <div class="gp-page-header location-sources-header">
          <div class="gp-page-header-content">
            <div class="gp-page-header-text">
              <h1 class="gp-page-title">{{ t('locationSources.page.title') }}</h1>
              <p class="gp-page-subtitle">
                {{ t('locationSources.page.description') }}
              </p>
            </div>
            <Button
              :label="t('locationSources.page.addNewSource')"
              icon="pi pi-plus"
              @click="openAddDialog()"
              class="add-source-btn"
              data-tour="add-source-btn"
            />
          </div>
        </div>

        <LocationSourceQuickSetupGuide
          v-if="!hasAnySources"
          @quick-setup="startQuickSetup"
        />

        <LocationSourcesList
          v-if="hasAnySources"
          :sources="gpsSourceConfigs"
          @status-change="handleStatusChange"
          @show-instructions="showInstructions"
          @edit-source="editSource"
          @delete-source="confirmDelete"
        />

        <LocationSourceInstructionsCard
          v-if="hasAnySources"
          :tab-items="tabItems"
          :active-tab-index="activeTabIndex"
          :active-tab="activeTab"
          :own-tracks-mqtt-config="ownTracksMqttConfig"
          :has-own-tracks-http="hasOwnTracksHttp"
          :has-own-tracks-mqtt="hasOwnTracksMqtt"
          :has-overland-source="hasOverlandSource"
          :has-traccar-source="hasTraccarSource"
          :has-gps-logger-source="hasGpsLoggerSource"
          :has-dawarich-source="hasDawarichSource"
          :has-home-assistant-source="hasHomeAssistantSource"
          :has-colota-source="hasColotaSource"
          @tab-change="handleTabChange"
          @copy-text="copyToClipboard"
        />

        <LocationSourceDialog
          ref="locationSourceDialogRef"
          :saving="saving"
          :defaultFilteringValues="defaultFilteringValues"
          @submit="handleLocationSourceDialogSubmit"
        />

        <!-- Confirm Delete Dialog -->
        <ConfirmDialog />
        <Toast />
      </div>
    </PageContainer>
  </AppLayout>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, watch, nextTick } from 'vue'
import { useI18n } from 'vue-i18n'
import { storeToRefs } from 'pinia'
import { useToast } from 'primevue/usetoast'
import { useConfirm } from 'primevue/useconfirm'

// Layout components
import AppLayout from '@/components/ui/layout/AppLayout.vue'
import PageContainer from '@/components/ui/layout/PageContainer.vue'
import OnboardingTour from '@/components/OnboardingTour.vue'
import LocationSourceDialog from '@/components/location-sources/LocationSourceDialog.vue'
import LocationSourceQuickSetupGuide from '@/components/location-sources/LocationSourceQuickSetupGuide.vue'
import LocationSourceInstructionsCard from '@/components/location-sources/LocationSourceInstructionsCard.vue'
import LocationSourcesList from '@/components/location-sources/LocationSourcesList.vue'
import { getLocationSourceDisplayName } from '@/components/location-sources/locationSourceMeta'

// Store
import { useGpsSourcesStore } from '@/stores/gpsSources'
import { copyToClipboard as copyTextToClipboard } from '@/utils/clipboardUtils'
import { formatApiErrorDetail } from '@/utils/apiErrorDetail'

// Store setup
const { t } = useI18n()
const gpsStore = useGpsSourcesStore()
const { gpsSourceConfigs, defaultFilteringValues, ownTracksMqttConfig } = storeToRefs(gpsStore)

// Services
const toast = useToast()
const confirm = useConfirm()

// State
const locationSourceDialogRef = ref(null)
const saving = ref(false)
const activeTab = ref('owntracks-http')

// Computed
const hasAnySources = computed(() => gpsSourceConfigs.value.length > 0)

const ownTracksSources = computed(() => 
  gpsSourceConfigs.value.filter(source => source.type === 'OWNTRACKS')
)

const hasOwnTracksHttp = computed(() => 
  ownTracksSources.value.some(source => source.connectionType === 'HTTP' || !source.connectionType)
)

const hasOwnTracksMqtt = computed(() => 
  ownTracksSources.value.some(source => source.connectionType === 'MQTT')
)

const hasOverlandSource = computed(() => 
  gpsSourceConfigs.value.some(source => source.type === 'OVERLAND')
)

const hasTraccarSource = computed(() =>
  gpsSourceConfigs.value.some(source => source.type === 'TRACCAR')
)

const hasGpsLoggerSource = computed(() =>
  gpsSourceConfigs.value.some(source => source.type === 'GPSLOGGER')
)

const hasDawarichSource = computed(() => 
  gpsSourceConfigs.value.some(source => source.type === 'DAWARICH')
)

const hasHomeAssistantSource = computed(() => 
  gpsSourceConfigs.value.some(source => source.type === 'HOME_ASSISTANT')
)

const hasColotaSource = computed(() =>
  gpsSourceConfigs.value.some(source => source.type === 'COLOTA')
)

// Mobile detection
const windowWidth = ref(typeof window !== 'undefined' ? window.innerWidth : 1024)

const isMobile = computed(() => windowWidth.value <= 768)

// Update window width on resize
if (typeof window !== 'undefined') {
  const handleResize = () => {
    windowWidth.value = window.innerWidth
  }
  onMounted(() => {
    window.addEventListener('resize', handleResize)
    handleResize()
  })
  onUnmounted(() => {
    window.removeEventListener('resize', handleResize)
  })
}

// Tab configuration
const tabItems = computed(() => {
  const tabs = []
  
  // Add separate tabs for HTTP and MQTT OwnTracks if both exist
  if (hasOwnTracksHttp.value && hasOwnTracksMqtt.value) {
    tabs.push({
      label: isMobile.value ? t('locationSources.page.tabOwnTracksHttpShort') : t('locationSources.page.tabOwnTracksHttp'),
      icon: 'pi pi-globe',
      key: 'owntracks-http'
    })
    tabs.push({
      label: isMobile.value ? t('locationSources.page.tabOwnTracksMqttShort') : t('locationSources.page.tabOwnTracksMqtt'),
      icon: 'pi pi-send',
      key: 'owntracks-mqtt'
    })
  } else if (hasOwnTracksHttp.value) {
    tabs.push({
      label: t('locationSources.page.tabOwnTracks'),
      icon: 'pi pi-mobile',
      key: 'owntracks-http'
    })
  } else if (hasOwnTracksMqtt.value) {
    tabs.push({
      label: t('locationSources.page.tabOwnTracks'),
      icon: 'pi pi-mobile',
      key: 'owntracks-mqtt'
    })
  }

  if (hasOverlandSource.value) {
    tabs.push({
      label: t('locationSources.page.tabOverland'),
      icon: 'pi pi-map',
      key: 'overland'
    })
  }
  if (hasTraccarSource.value) {
    tabs.push({
      label: t('locationSources.page.tabTraccar'),
      icon: 'pi pi-car',
      key: 'traccar'
    })
  }
  if (hasGpsLoggerSource.value) {
    tabs.push({
      label: t('locationSources.page.tabGpsLogger'),
      icon: 'pi pi-compass',
      key: 'gpslogger'
    })
  }
  if (hasDawarichSource.value) {
    tabs.push({
      label: t('locationSources.page.tabDawarich'),
      icon: 'pi pi-key',
      key: 'dawarich'
    })
  }
  if (hasHomeAssistantSource.value) {
    tabs.push({
      label: isMobile.value ? t('locationSources.page.tabHomeAssistantShort') : t('locationSources.page.tabHomeAssistant'),
      icon: 'pi pi-home',
      key: 'home_assistant'
    })
  }
  if (hasColotaSource.value) {
    tabs.push({
      label: t('locationSources.page.tabColota'),
      icon: 'pi pi-map-marker',
      key: 'colota'
    })
  }
  return tabs
})

const activeTabIndex = computed(() => {
  return tabItems.value.findIndex(tab => tab.key === activeTab.value)
})

// Helper function to set the first available tab as active
const setFirstTabActive = () => {
  if (tabItems.value.length > 0) {
    const firstTab = tabItems.value[0]
    activeTab.value = firstTab.key
  }
}

// Watch for tab changes and ensure a valid tab is active
watch(tabItems, (newTabs) => {
  // If current active tab doesn't exist in new tabs, set first available
  if (newTabs.length > 0) {
    const currentTabExists = newTabs.some(tab => tab.key === activeTab.value)
    if (!currentTabExists) {
      setFirstTabActive()
    }
  }
}, { immediate: true })

// Methods
const getSourceDisplayName = getLocationSourceDisplayName
const handleTabChange = (event) => {
  const selectedTab = tabItems.value[event.index]
  if (selectedTab) {
    activeTab.value = selectedTab.key
  }
}

const openAddDialog = () => {
  locationSourceDialogRef.value?.openAdd()
}

const startQuickSetup = (type) => {
  locationSourceDialogRef.value?.openQuickSetup(type)
}

const showInstructions = (source) => {
  let tabKey = source.type.toLowerCase()
  
  // Handle OwnTracks connection type specific tabs
  if (source.type === 'OWNTRACKS') {
    const connectionType = source.connectionType || 'HTTP'
    tabKey = `owntracks-${connectionType.toLowerCase()}`
  }
  
  activeTab.value = tabKey
  // Scroll to instructions
  document.querySelector('.instructions-card')?.scrollIntoView({ behavior: 'smooth' })
}

const editSource = (source) => {
  locationSourceDialogRef.value?.openEdit(source)
}

const handleLocationSourceDialogSubmit = async ({ isEditMode, editingSource, formData }) => {
  saving.value = true
  
  try {
    if (isEditMode) {
      await gpsStore.updateGpsSource({
        ...editingSource,
        ...formData
      })
      toast.add({
        severity: 'success',
        summary: t('locationSources.page.toasts.sourceUpdatedSummary'),
        detail: t('locationSources.page.toasts.sourceUpdatedDetail'),
        life: 3000
      })
    } else {
      if (formData.type === 'OWNTRACKS' || formData.type === 'GPSLOGGER' || formData.type === 'COLOTA') {
        await gpsStore.addGpsConfigSource(
          formData.type,
          formData.username,
          formData.password,
          null, // token not used for OwnTracks
          formData.type === 'OWNTRACKS' ? formData.connectionType : 'HTTP',
          formData.filterInaccurateData,
          formData.maxAllowedAccuracy,
          formData.maxAllowedSpeed,
          formData.enableDuplicateDetection,
          formData.duplicateDetectionThresholdMinutes,
          null,
          formData.type === 'OWNTRACKS' ? formData.payloadEncryptionSecret : null
        )
      } else {
        // Token-only sources (Overland, Traccar, Dawarich, Home Assistant).
        await gpsStore.addGpsConfigSource(
          formData.type,
          null, // username not used
          null, // password not used
          formData.token,
          'HTTP', // always HTTP for these types
          formData.filterInaccurateData,
          formData.maxAllowedAccuracy,
          formData.maxAllowedSpeed,
          formData.enableDuplicateDetection,
          formData.duplicateDetectionThresholdMinutes,
          formData.type === 'TRACCAR' ? formData.deviceId : null
        )
      }
      
      // Set the newly created source's tab as active
      let sourceType = formData.type.toLowerCase()
      if (formData.type === 'OWNTRACKS') {
        const connectionType = formData.connectionType.toLowerCase()
        sourceType = `owntracks-${connectionType}`
      }
      await nextTick() // Wait for DOM update
      activeTab.value = sourceType
      
      toast.add({
        severity: 'success',
        summary: t('locationSources.page.toasts.sourceAddedSummary'),
        detail: t('locationSources.page.toasts.sourceAddedDetail'),
        life: 3000
      })
    }
    locationSourceDialogRef.value?.close()
  } catch (error) {
    toast.add({
      severity: 'error',
      summary: isEditMode ? t('locationSources.page.toasts.updateFailedSummary') : t('locationSources.page.toasts.addFailedSummary'),
      detail: formatApiErrorDetail(error, t('locationSources.page.toasts.saveFailedFallback')),
      life: 5000
    })
  } finally {
    saving.value = false
  }
}

const handleStatusChange = async ({ id, status }) => {
  try {
    await gpsStore.updateGpsSourceStatus(id, status)
    toast.add({
      severity: 'success',
      summary: t('locationSources.page.toasts.statusUpdatedSummary'),
      detail: status ? t('locationSources.page.toasts.statusUpdatedDetailEnabled') : t('locationSources.page.toasts.statusUpdatedDetailDisabled'),
      life: 3000
    })
  } catch (error) {
    toast.add({
      severity: 'error',
      summary: t('locationSources.page.toasts.statusUpdateFailedSummary'),
      detail: formatApiErrorDetail(error, t('locationSources.page.toasts.statusUpdateFailedFallback')),
      life: 5000
    })
  }
}

const confirmDelete = (source) => {
  confirm.require({
    message: t('locationSources.page.confirmDeleteMessage', { name: getSourceDisplayName(source.type) }),
    header: t('locationSources.page.confirmDeleteHeader'),
    icon: 'pi pi-exclamation-triangle',
    rejectProps: {
      label: t('locationSources.page.cancel'),
      severity: 'secondary',
      outlined: true
    },
    acceptProps: {
      label: t('locationSources.page.delete'),
      severity: 'danger'
    },
    accept: () => deleteSource(source.id)
  })
}

const deleteSource = async (id) => {
  try {
    await gpsStore.deleteGpsSource(id)
    toast.add({
      severity: 'success',
      summary: t('locationSources.page.toasts.sourceDeletedSummary'),
      detail: t('locationSources.page.toasts.sourceDeletedDetail'),
      life: 3000
    })
  } catch (error) {
    toast.add({
      severity: 'error',
      summary: t('locationSources.page.toasts.deleteFailedSummary'),
      detail: formatApiErrorDetail(error, t('locationSources.page.toasts.deleteFailedFallback')),
      life: 5000
    })
  }
}

const copyToClipboard = async (text) => {
  const success = await copyTextToClipboard(text)

  if (success) {
    toast.add({
      severity: 'success',
      summary: t('locationSources.page.toasts.copiedSummary'),
      detail: t('locationSources.page.toasts.copiedDetail'),
      life: 2000
    })
  } else {
    toast.add({
      severity: 'error',
      summary: t('locationSources.page.toasts.copyFailedSummary'),
      detail: t('locationSources.page.toasts.copyFailedDetail'),
      life: 3000
    })
  }
}

// Lifecycle
onMounted(async () => {
  try {
    // Fetch source list, filtering defaults, and MQTT instruction config in parallel
    await Promise.all([
      gpsStore.fetchGpsConfigSources(),
      gpsStore.fetchDefaultFilteringValues(),
      gpsStore.fetchOwnTracksMqttConfig()
    ])

    // Ensure first tab is active after data loads
    await nextTick()
    setFirstTabActive()
  } catch (error) {
    console.error('Error loading GPS source data:', error)
    toast.add({
      severity: 'error',
      summary: t('locationSources.page.toasts.loadingFailedSummary'),
      detail: t('locationSources.page.toasts.loadingFailedDetail'),
      life: 5000
    })
  }
})
</script>

<style scoped>
.location-sources-page {
  max-width: 1200px;
  margin: 0 auto;
  padding: 0 1rem;
}

.add-source-btn {
  flex-shrink: 0;
}
</style>
