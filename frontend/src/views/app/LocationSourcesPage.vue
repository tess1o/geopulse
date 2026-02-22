<template>
  <AppLayout>
    <PageContainer>
      <!-- Onboarding Tour -->
      <OnboardingTour />
      
      <div class="location-sources-page">
        <!-- Page Header -->
        <div class="page-header location-sources-header">
          <div class="header-content">
            <div class="header-text">
              <h1 class="page-title">Location Data Sources</h1>
              <p class="page-description">
                Configure how GeoPulse receives your location data from different tracking apps.
                Set up OwnTracks, GPSLogger, Overland, Dawarich, or Home Assistant to automatically sync your location history.
              </p>
            </div>
            <Button 
              label="Add New Source" 
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
          :has-own-tracks-http="hasOwnTracksHttp"
          :has-own-tracks-mqtt="hasOwnTracksMqtt"
          :has-overland-source="hasOverlandSource"
          :has-gps-logger-source="hasGpsLoggerSource"
          :has-dawarich-source="hasDawarichSource"
          :has-home-assistant-source="hasHomeAssistantSource"
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

// Store setup
const gpsStore = useGpsSourcesStore()
const { gpsSourceConfigs, defaultFilteringValues } = storeToRefs(gpsStore)

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

const hasGpsLoggerSource = computed(() =>
  gpsSourceConfigs.value.some(source => source.type === 'GPSLOGGER')
)

const hasDawarichSource = computed(() => 
  gpsSourceConfigs.value.some(source => source.type === 'DAWARICH')
)

const hasHomeAssistantSource = computed(() => 
  gpsSourceConfigs.value.some(source => source.type === 'HOME_ASSISTANT')
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
      label: isMobile.value ? 'OT-HTTP' : 'OwnTracks (HTTP)',
      icon: 'pi pi-globe',
      key: 'owntracks-http'
    })
    tabs.push({
      label: isMobile.value ? 'OT-MQTT' : 'OwnTracks (MQTT)',
      icon: 'pi pi-send',
      key: 'owntracks-mqtt'
    })
  } else if (hasOwnTracksHttp.value) {
    tabs.push({
      label: 'OwnTracks',
      icon: 'pi pi-mobile',
      key: 'owntracks-http'
    })
  } else if (hasOwnTracksMqtt.value) {
    tabs.push({
      label: 'OwnTracks',
      icon: 'pi pi-mobile',
      key: 'owntracks-mqtt'
    })
  }
  
  if (hasOverlandSource.value) {
    tabs.push({
      label: 'Overland', 
      icon: 'pi pi-map',
      key: 'overland'
    })
  }
  if (hasGpsLoggerSource.value) {
    tabs.push({
      label: 'GPSLogger',
      icon: 'pi pi-compass',
      key: 'gpslogger'
    })
  }
  if (hasDawarichSource.value) {
    tabs.push({
      label: 'Dawarich', 
      icon: 'pi pi-key',
      key: 'dawarich'
    })
  }
  if (hasHomeAssistantSource.value) {
    tabs.push({
      label: isMobile.value ? 'HA' : 'Home Assistant', 
      icon: 'pi pi-home',
      key: 'home_assistant'
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
        summary: 'Source Updated',
        detail: 'Location source has been updated successfully',
        life: 3000
      })
    } else {
      if (formData.type === 'OWNTRACKS' || formData.type === 'GPSLOGGER') {
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
          formData.duplicateDetectionThresholdMinutes
        )
      } else {
        // For Overland, Dawarich, and Home Assistant - only send token, no username/password
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
          formData.duplicateDetectionThresholdMinutes
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
        summary: 'Source Added',
        detail: 'Location source has been added successfully',
        life: 3000
      })
    }
    locationSourceDialogRef.value?.close()
  } catch (error) {
    const errorMessage = error.response?.data?.message || error.message || 'An error occurred'
    toast.add({
      severity: 'error',
      summary: isEditMode ? 'Update Failed' : 'Add Failed',
      detail: errorMessage,
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
      summary: 'Status Updated',
      detail: `Source ${status ? 'enabled' : 'disabled'} successfully`,
      life: 3000
    })
  } catch (error) {
    const errorMessage = error.response?.data?.message || error.message || 'An error occurred'
    toast.add({
      severity: 'error',
      summary: 'Status Update Failed',
      detail: errorMessage,
      life: 5000
    })
  }
}

const confirmDelete = (source) => {
  confirm.require({
    message: `Are you sure you want to delete this ${getSourceDisplayName(source.type)} source?`,
    header: 'Confirm Delete',
    icon: 'pi pi-exclamation-triangle',
    rejectProps: {
      label: 'Cancel',
      severity: 'secondary',
      outlined: true
    },
    acceptProps: {
      label: 'Delete',
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
      summary: 'Source Deleted',
      detail: 'Location source has been deleted successfully',
      life: 3000
    })
  } catch (error) {
    const errorMessage = error.response?.data?.message || error.message || 'An error occurred'
    toast.add({
      severity: 'error',
      summary: 'Delete Failed',
      detail: errorMessage,
      life: 5000
    })
  }
}

const copyToClipboard = async (text) => {
  const success = await copyTextToClipboard(text)

  if (success) {
    toast.add({
      severity: 'success',
      summary: 'Copied',
      detail: 'Text copied to clipboard',
      life: 2000
    })
  } else {
    toast.add({
      severity: 'error',
      summary: 'Copy Failed',
      detail: 'Failed to copy to clipboard',
      life: 3000
    })
  }
}

// Lifecycle
onMounted(async () => {
  try {
    // Fetch both GPS configs and default values in parallel
    await Promise.all([
      gpsStore.fetchGpsConfigSources(),
      gpsStore.fetchDefaultFilteringValues()
    ])

    // Ensure first tab is active after data loads
    await nextTick()
    setFirstTabActive()
  } catch (error) {
    console.error('Error loading GPS source data:', error)
    toast.add({
      severity: 'error',
      summary: 'Loading Failed',
      detail: 'Failed to load location sources',
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
  color: var(--gp-text-primary) !important;
  margin: 0 0 0.5rem 0;
}

.page-description {
  font-size: 1.1rem;
  color: var(--gp-text-secondary);
  margin: 0;
  line-height: 1.5;
}

.add-source-btn {
  flex-shrink: 0;
}

/* Dark Mode */
.p-dark .page-title {
  color: var(--gp-text-primary);
}

.p-dark .page-description {
  color: var(--gp-text-secondary);
}

/* Responsive */
@media (max-width: 768px) {
  .header-content {
    flex-direction: column;
    align-items: stretch;
  }
}
</style>
