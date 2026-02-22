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

        <!-- Quick Setup Guide -->
        <Card v-if="!hasAnySources" class="quick-guide-card quick-setup-guide">
          <template #title>
            <div class="flex items-center gap-2">
              <i class="pi pi-info-circle text-blue-500"></i>
              Quick Setup Guide
            </div>
          </template>
          <template #content>
            <div class="grid grid-cols-1 md:grid-cols-3 gap-6">
              <div v-for="option in quickSetupOptions" :key="option.value" class="source-option">
                <div class="source-header">
                  <i :class="[getSourceIcon(option.value), 'text-2xl', option.accentClass]"></i>
                  <div>
                    <h3 class="source-name">{{ option.label }}</h3>
                    <p class="source-description">{{ option.description }}</p>
                  </div>
                </div>
                <Button 
                  :label="`Setup ${option.label}`"
                  outlined 
                  size="small"
                  @click="startQuickSetup(option.value)"
                />
              </div>
            </div>
          </template>
        </Card>

        <!-- Configured Sources -->
        <div v-if="hasAnySources" class="configured-sources">
          <h2 class="section-title">Configured Sources</h2>
          <div class="sources-grid">
            <Card v-for="source in gpsSourceConfigs" :key="source.id" class="source-card">
              <template #title>
                <div class="source-card-header">
                  <div class="source-info">
                    <i :class="getSourceIcon(source.type)" class="source-icon"></i>
                    <div>
                      <div class="source-type">{{ getSourceDisplayName(source.type) }}</div>
                      <div class="source-identifier">{{ getSourceIdentifier(source) }}</div>
                    </div>
                  </div>
                  <div class="source-status-column">
                    <Badge 
                      :value="source.active ? 'Active' : 'Inactive'" 
                      :severity="source.active ? 'success' : 'secondary'"
                      class="status-badge"
                    />
                    <Badge 
                      v-if="source.type === 'OWNTRACKS'"
                      :value="source.connectionType || 'HTTP'"
                      :severity="source.connectionType === 'MQTT' ? 'info' : 'warn'"
                      class="connection-badge"
                    />
                  </div>
                </div>
              </template>
              <template #content>
                <div class="source-actions">
                  <div class="status-toggle">
                    <ToggleSwitch 
                      v-model="source.active"
                      @update:modelValue="(value) => handleStatusChange(source.id, value)"
                    />
                    <span class="toggle-label">{{ source.active ? 'Enabled' : 'Disabled' }}</span>
                  </div>
                  
                  <div class="action-buttons">
                    <Button 
                      icon="pi pi-eye"
                      label="Instructions"
                      size="small"
                      outlined
                      @click="showInstructions(source)"
                    />
                    <Button 
                      icon="pi pi-pencil"
                      label="Edit"
                      size="small"
                      outlined
                      @click="editSource(source)"
                    />
                    <Button 
                      icon="pi pi-trash"
                      severity="danger"
                      size="small"
                      outlined
                      @click="confirmDelete(source)"
                    />
                  </div>
                </div>
              </template>
            </Card>
          </div>
        </div>

        <!-- Setup Instructions -->
        <Card v-if="hasAnySources" class="instructions-card">
          <template #title>
            <div class="flex items-center gap-2">
              <i class="pi pi-book text-blue-500"></i>
              Setup Instructions
            </div>
          </template>
          <template #content>
            <TabContainer
              v-if="tabItems.length > 0"
              :tabs="tabItems"
              :activeIndex="activeTabIndex"
              @tab-change="handleTabChange"
              class="instructions-tabs"
            >
              <!-- OwnTracks HTTP Tab -->
              <div v-if="activeTab === 'owntracks-http' && hasOwnTracksHttp">
                <div class="instruction-content">
                  <h3 class="instruction-title">OwnTracks Configuration (HTTP)</h3>
                  <div class="instruction-steps">
                    <div class="step">
                      <div class="step-number">1</div>
                      <div class="step-content">
                        <div class="step-title">Server URL</div>
                        <div class="copy-field">
                          <code>{{ getOwntracksUrl() }}</code>
                          <Button 
                            icon="pi pi-copy"
                            size="small"
                            outlined
                            @click="copyToClipboard(getOwntracksUrl())"
                          />
                        </div>
                      </div>
                    </div>
                    
                    <div class="step">
                      <div class="step-number">2</div>
                      <div class="step-content">
                        <div class="step-title">Connection Mode</div>
                        <div class="step-value">HTTP</div>
                      </div>
                    </div>
                    
                    <div class="step">
                      <div class="step-number">3</div>
                      <div class="step-content">
                        <div class="step-title">Authentication</div>
                        <div class="step-value">
                          Use your configured username
                        </div>
                        <div class="step-value">
                          Use your configured password
                        </div>
                      </div>
                    </div>
                  </div>
                </div>
              </div>

              <!-- OwnTracks MQTT Tab -->
              <div v-if="activeTab === 'owntracks-mqtt' && hasOwnTracksMqtt">
                <div class="instruction-content">
                  <h3 class="instruction-title">OwnTracks Configuration (MQTT)</h3>
                  <div class="instruction-steps">
                    <div class="step">
                      <div class="step-number">1</div>
                      <div class="step-content">
                        <div class="step-title">Connection Type</div>
                        <div class="step-value">Select <strong>MQTT</strong> in OwnTracks connection settings</div>
                      </div>
                    </div>
                    
                    <div class="step">
                      <div class="step-number">2</div>
                      <div class="step-content">
                        <div class="step-title">MQTT Broker Host</div>
                        <div class="copy-field">
                          <code>{{ getMqttHost() }}</code>
                          <Button 
                            icon="pi pi-copy"
                            size="small"
                            outlined
                            @click="copyToClipboard(getMqttHost())"
                          />
                        </div>
                        <small class="text-muted">Use your public host or IP address</small>
                      </div>
                    </div>
                    
                    <div class="step">
                      <div class="step-number">3</div>
                      <div class="step-content">
                        <div class="step-title">MQTT Port</div>
                        <div class="copy-field">
                          <code>1883</code>
                          <Button 
                            icon="pi pi-copy"
                            size="small"
                            outlined
                            @click="copyToClipboard('1883')"
                          />
                        </div>
                      </div>
                    </div>
                    
                    <div class="step">
                      <div class="step-number">4</div>
                      <div class="step-content">
                        <div class="step-title">Authentication</div>
                        <div class="step-value">
                          Use your configured username
                        </div>
                        <div class="step-value">
                          Use your configured password
                        </div>
                      </div>
                    </div>
                    
                    <div class="step">
                      <div class="step-number">5</div>
                      <div class="step-content">
                        <div class="step-title">Security Settings</div>
                        <div class="step-value">
                          TLS: <strong>Disabled</strong><br>
                          <small class="text-muted">Leave TLS/SSL settings unchecked</small>
                        </div>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
              
              <!-- Overland Tab -->
              <div v-if="activeTab === 'overland' && hasOverlandSource">
                <div class="instruction-content">
                  <h3 class="instruction-title">Overland Configuration</h3>
                  <div class="instruction-steps">
                    <div class="step">
                      <div class="step-number">1</div>
                      <div class="step-content">
                        <div class="step-title">Receiver Endpoint URL</div>
                        <div class="copy-field">
                          <code>{{ getOverlandUrl() }}</code>
                          <Button 
                            icon="pi pi-copy"
                            size="small"
                            outlined
                            @click="copyToClipboard(getOverlandUrl())"
                          />
                        </div>
                      </div>
                    </div>
                    
                    <div class="step">
                      <div class="step-number">2</div>
                      <div class="step-content">
                        <div class="step-title">Access Token</div>
                        <div class="copy-field">
                          <code>Your configured token</code>
                        </div>
                      </div>
                    </div>
                  </div>
                </div>
              </div>

              <!-- GPSLogger Tab -->
              <div v-if="activeTab === 'gpslogger' && hasGpsLoggerSource">
                <div class="instruction-content">
                  <h3 class="instruction-title">GPSLogger Configuration</h3>
                  <div class="instruction-steps">
                    <div class="step">
                      <div class="step-number">1</div>
                      <div class="step-content">
                        <div class="step-title">Enable Custom URL Logging</div>
                        <div class="step-value">In GPSLogger, enable <strong>Log to custom URL</strong>.</div>
                      </div>
                    </div>

                    <div class="step">
                      <div class="step-number">2</div>
                      <div class="step-content">
                        <div class="step-title">URL</div>
                        <div class="copy-field">
                          <code>{{ getGpsLoggerUrl() }}</code>
                          <Button
                            icon="pi pi-copy"
                            size="small"
                            outlined
                            @click="copyToClipboard(getGpsLoggerUrl())"
                          />
                        </div>
                      </div>
                    </div>

                    <div class="step">
                      <div class="step-number">3</div>
                      <div class="step-content">
                        <div class="step-title">HTTP Method</div>
                        <div class="step-value">POST</div>
                      </div>
                    </div>

                    <div class="step">
                      <div class="step-number">4</div>
                      <div class="step-content">
                        <div class="step-title">HTTP Body (JSON)</div>
                        <div class="copy-field">
                          <pre class="yaml-config">{{ getGpsLoggerHttpBody() }}</pre>
                          <Button
                            icon="pi pi-copy"
                            size="small"
                            outlined
                            @click="copyToClipboard(getGpsLoggerHttpBody())"
                          />
                        </div>
                        <small class="text-muted">GeoPulse treats GPSLogger speed as m/s and converts it to km/h automatically.</small>
                      </div>
                    </div>

                    <div class="step">
                      <div class="step-number">5</div>
                      <div class="step-content">
                        <div class="step-title">Headers</div>
                        <div class="step-value">
                          Add <code>Content-Type: application/json</code><br>
                          Optional: <code>X-Limit-D: my-android-phone</code>
                        </div>
                      </div>
                    </div>

                    <div class="step">
                      <div class="step-number">6</div>
                      <div class="step-content">
                        <div class="step-title">Authentication</div>
                        <div class="step-value">Enable <strong>Basic Authentication</strong> and use the username/password from this source.</div>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
              
              <!-- Dawarich Tab -->
              <div v-if="activeTab === 'dawarich' && hasDawarichSource">
                <div class="instruction-content">
                  <h3 class="instruction-title">Dawarich Configuration</h3>
                  <div class="instruction-steps">
                    <div class="step">
                      <div class="step-number">1</div>
                      <div class="step-content">
                        <div class="step-title">Server URL</div>
                        <div class="copy-field">
                          <code>{{ getDawarichUrl() }}</code>
                          <Button 
                            icon="pi pi-copy"
                            size="small"
                            outlined
                            @click="copyToClipboard(getDawarichUrl())"
                          />
                        </div>
                      </div>
                    </div>
                    
                    <div class="step">
                      <div class="step-number">2</div>
                      <div class="step-content">
                        <div class="step-title">API Key</div>
                        <div class="copy-field">
                          <code>Your configured API Key</code>
                        </div>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
              
              <!-- Home Assistant Tab -->
              <div v-if="activeTab === 'home_assistant' && hasHomeAssistantSource">
                <div class="instruction-content">
                  <h3 class="instruction-title">Home Assistant Configuration</h3>
                  <div class="instruction-steps">
                    <div class="step">
                      <div class="step-number">1</div>
                      <div class="step-content">
                        <div class="step-title">In configuration.yaml add the following:</div>
                        <div class="copy-field">
                          <pre class="yaml-config">{{ getHomeAssistantConfigYaml() }}</pre>
                          <Button 
                            icon="pi pi-copy"
                            size="small"
                            outlined
                            @click="copyToClipboard(getHomeAssistantConfigYaml())"
                          />
                        </div>
                        <div class="step-value">
                          <strong>Replace:</strong><br>
                          • iphone_16 with your device_id (can be found in Home Assistant)<br>
                          • YOUR_CONFIGURED_TOKEN with the token you just created in GeoPulse
                        </div>
                      </div>
                    </div>
                    
                    <div class="step">
                      <div class="step-number">2</div>
                      <div class="step-content">
                        <div class="step-title">In automations.yaml add the following:</div>
                        <div class="copy-field">
                          <pre class="yaml-config">{{ getHomeAssistantAutomationYaml() }}</pre>
                          <Button 
                            icon="pi pi-copy"
                            size="small"
                            outlined
                            @click="copyToClipboard(getHomeAssistantAutomationYaml())"
                          />
                        </div>
                      </div>
                    </div>
                    
                    <div class="step">
                      <div class="step-number">3</div>
                      <div class="step-content">
                        <div class="step-title">Restart Home Assistant server to apply the changes.</div>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </TabContainer>
          </template>
        </Card>

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
import TabContainer from '@/components/ui/layout/TabContainer.vue'
import OnboardingTour from '@/components/OnboardingTour.vue'
import LocationSourceDialog from '@/components/location-sources/LocationSourceDialog.vue'
import {
  LOCATION_SOURCE_OPTIONS,
  getLocationSourceDisplayName,
  getLocationSourceIcon,
  getLocationSourceIdentifier
} from '@/components/location-sources/locationSourceMeta'

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

const QUICK_SETUP_ACCENT_BY_TYPE = Object.freeze({
  OWNTRACKS: 'text-blue-500',
  GPSLOGGER: 'text-cyan-500',
  OVERLAND: 'text-green-500',
  DAWARICH: 'text-purple-500',
  HOME_ASSISTANT: 'text-orange-500'
})

const quickSetupOptions = LOCATION_SOURCE_OPTIONS.map((option) => ({
  ...option,
  accentClass: QUICK_SETUP_ACCENT_BY_TYPE[option.value] || 'text-blue-500'
}))

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
const getSourceIcon = getLocationSourceIcon
const getSourceDisplayName = getLocationSourceDisplayName
const getSourceIdentifier = getLocationSourceIdentifier

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

const handleStatusChange = async (id, status) => {
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

const getMqttHost = () => {
  // Extract hostname from current URL for MQTT broker host
  return window.location.hostname
}

const getOwntracksUrl = () => {
  return `${window.location.origin}/api/owntracks`
}

const getOverlandUrl = () => {
  return `${window.location.origin}/api/overland`
}

const getGpsLoggerUrl = () => {
  return `${window.location.origin}/api/gpslogger`
}

const getDawarichUrl = () => {
  return `${window.location.origin}/api/dawarich`
}

const getGpsLoggerHttpBody = () => {
  return `{
  "_type": "location",
  "t": "u",
  "acc": "%ACC",
  "alt": "%ALT",
  "batt": "%BATT",
  "bs": "%ISCHARGING",
  "lat": "%LAT",
  "lon": "%LON",
  "tst": "%TIMESTAMP",
  "vel": "%SPD"
}`
}


const getHomeAssistantConfigYaml = () => {
  return `rest_command:
  send_gps_data:
    url: "${window.location.origin}/api/homeassistant"
    method: POST
    headers:
      content-type: "application/json"
      Authorization: Bearer YOUR_CONFIGURED_TOKEN
    payload: >
      {
        "device_id": "iphone_16",
        "timestamp": "{{ now().isoformat() }}",
        "location": {
          "latitude": {{ state_attr('device_tracker.iphone_16', 'latitude') }},
          "longitude": {{ state_attr('device_tracker.iphone_16', 'longitude') }},
          "accuracy": {{ state_attr('device_tracker.iphone_16', 'gps_accuracy') | default(0, true) }},
          "altitude": {{ state_attr('device_tracker.iphone_16', 'altitude') | default(0, true) }},
          "speed": {{ state_attr('device_tracker.iphone_16', 'speed') | default(0, true) }}
        },
        "battery": {
          "level": {{ state_attr('device_tracker.iphone_16', 'battery_level') | default(states('sensor.iphone_16_battery_level'), true) | default(0, true) }}
        }
      }`
}

const getHomeAssistantAutomationYaml = () => {
  return `- alias: Send GPS data to server
  trigger:
    - platform: state
      entity_id: device_tracker.iphone_16
  action:
    - service: rest_command.send_gps_data`
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

/* Quick Guide */
.quick-guide-card {
  margin-bottom: 2rem;
}

.source-option {
  display: flex;
  flex-direction: column;
  gap: 1rem;
  padding: 1rem;
  border: 1px solid var(--gp-border-light);
  border-radius: var(--gp-radius-medium);
  background: var(--gp-surface-light);
}

.source-header {
  display: flex;
  align-items: flex-start;
  gap: 1rem;
}

.source-name {
  font-size: 1.1rem;
  font-weight: 600;
  color: var(--gp-text-primary);
  margin: 0 0 0.25rem 0;
}

.source-description {
  font-size: 0.9rem;
  color: var(--gp-text-secondary);
  margin: 0;
  line-height: 1.4;
}

/* Configured Sources */
.configured-sources {
  margin-bottom: 2rem;
}

.section-title {
  font-size: 1.5rem;
  font-weight: 600;
  color: var(--gp-text-primary);
  margin: 0 0 1rem 0;
}

.sources-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(350px, 1fr));
  gap: 1rem;
}

.source-card {
  height: fit-content;
}

.source-card-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 1rem;
}

.source-info {
  display: flex;
  align-items: flex-start;
  gap: 0.75rem;
}

.source-icon {
  font-size: 1.5rem;
  color: var(--gp-primary);
  margin-top: 0.125rem;
}

.source-type {
  font-size: 1.1rem;
  font-weight: 600;
  color: var(--gp-text-primary);
  margin: 0;
}

.source-identifier {
  font-size: 0.9rem;
  color: var(--gp-text-secondary);
  margin: 0.25rem 0 0 0;
  font-family: var(--font-mono, monospace);
}

.source-status-column {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 0.5rem;
  flex-shrink: 0;
}

.status-badge {
  font-size: 0.75rem;
}

.connection-badge {
  font-size: 0.7rem;
  font-weight: 500;
}

.source-actions {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.status-toggle {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.toggle-label {
  font-size: 0.9rem;
  color: var(--gp-text-secondary);
}

.action-buttons {
  display: flex;
  gap: 0.5rem;
  flex-wrap: wrap;
}

/* Instructions */
.instructions-card {
  margin-bottom: 2rem;
}

/* Let TabContainer handle the responsive behavior */

.instruction-content {
  padding: 1rem 0;
  margin-left: 0.5rem;
  max-width: 100%;
  overflow: hidden;
}

.instruction-title {
  font-size: 1.2rem;
  font-weight: 600;
  color: var(--gp-text-primary);
  margin: 0 0 1.5rem 0;
}

.instruction-steps {
  display: flex;
  flex-direction: column;
  gap: 1.5rem;
  max-width: 100%;
  overflow: hidden;
}

.step {
  display: flex;
  gap: 1rem;
  align-items: flex-start;
}

.step-number {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 2rem;
  height: 2rem;
  background: var(--gp-primary);
  color: white;
  border-radius: 50%;
  font-weight: 600;
  font-size: 0.9rem;
  flex-shrink: 0;
}

.step-content {
  flex: 1;
  min-width: 0;
  overflow: hidden;
}

.step-title {
  font-weight: 600;
  color: var(--gp-text-primary);
  margin-bottom: 0.5rem;
}

.step-value {
  color: var(--gp-text-secondary);
  line-height: 1.4;
}

.text-muted {
  color: var(--gp-text-muted, #6b7280);
  font-size: 0.85rem;
  margin-top: 0.25rem;
}

.copy-field {
  display: flex;
  align-items: flex-start;
  gap: 0.5rem;
  padding: 0.75rem;
  background: var(--gp-surface-light);
  border: 1px solid var(--gp-border-light);
  border-radius: var(--gp-radius-small);
  min-width: 0;
  overflow: hidden;
}

.copy-field code {
  flex: 1;
  font-family: var(--font-mono, monospace);
  font-size: 0.9rem;
  color: var(--gp-text-primary);
  word-break: break-all;
  min-width: 0;
  overflow-wrap: anywhere;
}

.yaml-config {
  flex: 1;
  font-family: var(--font-mono, monospace);
  font-size: 0.9rem;
  color: var(--gp-text-primary);
  white-space: pre-wrap;
  margin: 0;
  line-height: 1.4;
  min-width: 0;
  overflow-wrap: anywhere;
  word-break: break-word;
  max-width: 100%;
}

/* Dark Mode */
.p-dark .page-title {
  color: var(--gp-text-primary);
}

.p-dark .page-description {
  color: var(--gp-text-secondary);
}

.p-dark .section-title {
  color: var(--gp-text-primary);
}

.p-dark .source-option {
  background: var(--gp-surface-darker);
  border-color: var(--gp-border-dark);
}

.p-dark .source-name {
  color: var(--gp-text-primary);
}

.p-dark .source-description {
  color: var(--gp-text-secondary);
}

.p-dark .source-type {
  color: var(--gp-text-primary);
}

.p-dark .source-identifier {
  color: var(--gp-text-secondary);
}

.p-dark .toggle-label {
  color: var(--gp-text-secondary);
}

.p-dark .instruction-title {
  color: var(--gp-text-primary);
}

.p-dark .step-title {
  color: var(--gp-text-primary);
}

.p-dark .step-value {
  color: var(--gp-text-secondary);
}

.p-dark .copy-field {
  background: var(--gp-surface-darker);
  border-color: var(--gp-border-dark);
}

.p-dark .copy-field code {
  color: var(--gp-text-primary);
}

.p-dark .yaml-config {
  color: var(--gp-text-primary);
}

.p-dark .text-muted {
  color: var(--gp-text-muted, #9ca3af);
}


/* Card Dark Mode - Comprehensive */
.p-dark .instructions-card,
.p-dark .quick-guide-card,
.p-dark .source-card {
  background: var(--gp-surface-dark) !important;
  border-color: var(--gp-border-dark) !important;
}

.p-dark .instructions-card :deep(.p-card),
.p-dark .quick-guide-card :deep(.p-card),
.p-dark .source-card :deep(.p-card) {
  background: var(--gp-surface-dark) !important;
  border-color: var(--gp-border-dark) !important;
}

.p-dark .instructions-card :deep(.p-card-header),
.p-dark .instructions-card :deep(.p-card-title-section),
.p-dark .quick-guide-card :deep(.p-card-header),
.p-dark .quick-guide-card :deep(.p-card-title-section) {
  background: var(--gp-surface-dark) !important;
  border-bottom-color: var(--gp-border-dark) !important;
}

.p-dark .instructions-card :deep(.p-card-title),
.p-dark .quick-guide-card :deep(.p-card-title) {
  color: var(--gp-text-primary) !important;
}

.p-dark .instructions-card :deep(.p-card-content),
.p-dark .instructions-card :deep(.p-card-body),
.p-dark .quick-guide-card :deep(.p-card-content),
.p-dark .quick-guide-card :deep(.p-card-body) {
  background: var(--gp-surface-dark) !important;
  color: var(--gp-text-primary) !important;
}


/* Responsive */
@media (max-width: 768px) {
  .header-content {
    flex-direction: column;
    align-items: stretch;
  }
  
  .sources-grid {
    grid-template-columns: 1fr;
  }
  
  .action-buttons {
    flex-direction: column;
  }
  
  .step {
    flex-direction: column;
    gap: 0.5rem;
  }
  
  .copy-field {
    flex-direction: column;
    align-items: stretch;
    gap: 0.75rem;
  }
  
  .step-value {
    word-break: break-word;
    overflow-wrap: anywhere;
  }
  
  .yaml-config {
    font-size: 0.8rem !important;
    line-height: 1.3;
  }
  
  /* TabContainer handles mobile tab styling */
}
</style>
