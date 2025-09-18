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
                Set up OwnTracks, Overland, Dawarich, or Home Assistant to automatically sync your location history.
              </p>
            </div>
            <Button 
              label="Add New Source" 
              icon="pi pi-plus"
              @click="showAddDialog = true"
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
              <div class="source-option">
                <div class="source-header">
                  <i class="pi pi-mobile text-2xl text-blue-500"></i>
                  <div>
                    <h3 class="source-name">OwnTracks</h3>
                    <p class="source-description">Open-source location tracking with HTTP or MQTT connections</p>
                  </div>
                </div>
                <Button 
                  label="Setup OwnTracks" 
                  outlined 
                  size="small"
                  @click="startQuickSetup('OWNTRACKS')"
                />
              </div>
              
              <div class="source-option">
                <div class="source-header">
                  <i class="pi pi-map text-2xl text-green-500"></i>
                  <div>
                    <h3 class="source-name">Overland</h3>
                    <p class="source-description">Simple HTTP endpoint with token-based authentication</p>
                  </div>
                </div>
                <Button 
                  label="Setup Overland" 
                  outlined 
                  size="small"
                  @click="startQuickSetup('OVERLAND')"
                />
              </div>
              
              <div class="source-option">
                <div class="source-header">
                  <i class="pi pi-key text-2xl text-purple-500"></i>
                  <div>
                    <h3 class="source-name">Dawarich</h3>
                    <p class="source-description">Privacy-focused location tracking with API key authentication</p>
                  </div>
                </div>
                <Button 
                  label="Setup Dawarich"
                  outlined 
                  size="small"
                  @click="startQuickSetup('DAWARICH')"
                />
              </div>
              
              <div class="source-option">
                <div class="source-header">
                  <i class="pi pi-home text-2xl text-orange-500"></i>
                  <div>
                    <h3 class="source-name">Home Assistant</h3>
                    <p class="source-description">Integrate with Home Assistant automation for automatic location tracking</p>
                  </div>
                </div>
                <Button 
                  label="Setup Home Assistant"
                  outlined 
                  size="small"
                  @click="startQuickSetup('HOME_ASSISTANT')"
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
<!--                          <Button -->
<!--                            v-if="dawarichApiKey"-->
<!--                            icon="pi pi-copy"-->
<!--                            size="small"-->
<!--                            outlined-->
<!--                            @click="copyToClipboard(dawarichApiKey)"-->
<!--                          />-->
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
                          • BACKEND_SERVER_URL with your backend url (e.g., http://192.168.100.1:8080 or https://geopulse.mydomain.com)<br>
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

        <!-- Add/Edit Source Dialog -->
        <Dialog 
          v-model:visible="showAddDialog"
          :header="isEditMode ? 'Edit Location Source' : 'Add Location Source'"
          modal
          class="source-dialog"
        >
          <div class="dialog-content">
            <div class="source-type-selection">
              <label class="form-label">Source Type</label>
              <div class="source-types">
                <div 
                  v-for="type in sourceTypes" 
                  :key="type.value"
                  :class="['source-type-option', { active: formData.type === type.value }]"
                  @click="formData.type = type.value"
                >
                  <i :class="type.icon"></i>
                  <div>
                    <div class="type-name">{{ type.label }}</div>
                    <div class="type-description">{{ type.description }}</div>
                  </div>
                </div>
              </div>
            </div>

            <div v-if="formData.type === 'OWNTRACKS'" class="form-section">
              <div class="form-field">
                <label for="connectionType" class="form-label">Connection Type</label>
                <div class="connection-type-selection">
                  <div 
                    :class="['connection-type-option', { active: formData.connectionType === 'HTTP' }]"
                    @click="formData.connectionType = 'HTTP'"
                  >
                    <i class="pi pi-globe"></i>
                    <div>
                      <div class="connection-type-name">HTTP</div>
                      <div class="connection-type-description">Standard HTTP endpoint</div>
                    </div>
                  </div>
                  <div 
                    :class="['connection-type-option', { active: formData.connectionType === 'MQTT' }]"
                    @click="formData.connectionType = 'MQTT'"
                  >
                    <i class="pi pi-send"></i>
                    <div>
                      <div class="connection-type-name">MQTT</div>
                      <div class="connection-type-description">MQTT broker connection</div>
                    </div>
                  </div>
                </div>
              </div>
              
              <div class="form-field">
                <label for="username" class="form-label">Username</label>
                <InputText 
                  id="username"
                  v-model="formData.username"
                  placeholder="Enter username"
                  :invalid="!!formErrors.username"
                />
                <small v-if="formErrors.username" class="error-message">{{ formErrors.username }}</small>
              </div>
              
              <div class="form-field">
                <label for="password" class="form-label">Password</label>
                <Password 
                  id="password"
                  v-model="formData.password"
                  :placeholder="isEditMode ? 'Enter new password (leave empty to keep current)' : 'Enter password'"
                  :feedback="false"
                  toggleMask
                  :invalid="!!formErrors.password"
                />
                <small v-if="formErrors.password" class="error-message">{{ formErrors.password }}</small>
              </div>
            </div>

            <div v-else-if="formData.type === 'OVERLAND'" class="form-section">
              <div class="form-field">
                <label for="token" class="form-label">Access Token</label>
                <InputText 
                  id="token"
                  v-model="formData.token"
                  placeholder="Enter access token"
                  :invalid="!!formErrors.token"
                />
                <small v-if="formErrors.token" class="error-message">{{ formErrors.token }}</small>
              </div>
            </div>

            <div v-else-if="formData.type === 'DAWARICH'" class="form-section">
              <div class="form-field">
                <label for="apiKey" class="form-label">API Key</label>
                <InputText 
                  id="apiKey"
                  v-model="formData.token"
                  placeholder="Enter API key"
                  :invalid="!!formErrors.token"
                />
                <small v-if="formErrors.token" class="error-message">{{ formErrors.token }}</small>
              </div>
            </div>

            <div v-else-if="formData.type === 'HOME_ASSISTANT'" class="form-section">
              <div class="form-field">
                <label for="token" class="form-label">Token</label>
                <InputText 
                  id="token"
                  v-model="formData.token"
                  placeholder="Enter token"
                  :invalid="!!formErrors.token"
                />
                <small v-if="formErrors.token" class="error-message">{{ formErrors.token }}</small>
              </div>
            </div>
          </div>

          <template #footer>
            <div class="dialog-footer">
              <Button label="Cancel" outlined @click="closeDialog" />
              <Button 
                :label="isEditMode ? 'Save Changes' : 'Add Source'"
                @click="saveSource"
                :loading="saving"
              />
            </div>
          </template>
        </Dialog>

        <!-- Confirm Delete Dialog -->
        <ConfirmDialog />
        <Toast />
      </div>
    </PageContainer>
  </AppLayout>
</template>

<script setup>
import { ref, computed, onMounted, watch, nextTick } from 'vue'
import { storeToRefs } from 'pinia'
import { useToast } from 'primevue/usetoast'
import { useConfirm } from 'primevue/useconfirm'

// Layout components
import AppLayout from '@/components/ui/layout/AppLayout.vue'
import PageContainer from '@/components/ui/layout/PageContainer.vue'
import TabContainer from '@/components/ui/layout/TabContainer.vue'
import OnboardingTour from '@/components/OnboardingTour.vue'

// Store
import { useGpsSourcesStore } from '@/stores/gpsSources'

// Store setup
const gpsStore = useGpsSourcesStore()
const { gpsSourceConfigs } = storeToRefs(gpsStore)

// Services
const toast = useToast()
const confirm = useConfirm()

// State
const showAddDialog = ref(false)
const isEditMode = ref(false)
const editingSource = ref(null)
const saving = ref(false)
const activeInstructionTab = ref('owntracks')
const activeTab = ref('owntracks-http')

// Form data
const formData = ref({
  type: 'OWNTRACKS',
  username: '',
  password: '',
  token: '',
  connectionType: 'HTTP'
})

const formErrors = ref({})

// Source type options
const sourceTypes = [
  {
    value: 'OWNTRACKS',
    label: 'OwnTracks',
    description: 'Open-source location tracking with HTTP or MQTT connections',
    icon: 'pi pi-mobile'
  },
  {
    value: 'OVERLAND',
    label: 'Overland',
    description: 'Simple HTTP endpoint with token-based authentication',
    icon: 'pi pi-map'
  },
  {
    value: 'DAWARICH',
    label: 'Dawarich',
    description: 'Privacy-focused location tracking with API key authentication',
    icon: 'pi pi-key'
  },
  {
    value: 'HOME_ASSISTANT',
    label: 'Home Assistant',
    description: 'Integrate with Home Assistant automation for automatic location tracking',
    icon: 'pi pi-home'
  }
]

// Computed
const hasAnySources = computed(() => gpsSourceConfigs.value.length > 0)

const hasOwnTracksSource = computed(() => 
  gpsSourceConfigs.value.some(source => source.type === 'OWNTRACKS')
)

const ownTracksSources = computed(() => 
  gpsSourceConfigs.value.filter(source => source.type === 'OWNTRACKS')
)

const hasOwnTracksHttp = computed(() => 
  ownTracksSources.value.some(source => source.connectionType === 'HTTP' || !source.connectionType)
)

const hasOwnTracksMqtt = computed(() => 
  ownTracksSources.value.some(source => source.connectionType === 'MQTT')
)

const ownTracksHttpSources = computed(() => 
  ownTracksSources.value.filter(source => source.connectionType === 'HTTP' || !source.connectionType)
)

const ownTracksMqttSources = computed(() => 
  ownTracksSources.value.filter(source => source.connectionType === 'MQTT')
)

const hasOverlandSource = computed(() => 
  gpsSourceConfigs.value.some(source => source.type === 'OVERLAND')
)

const hasDawarichSource = computed(() => 
  gpsSourceConfigs.value.some(source => source.type === 'DAWARICH')
)

const hasHomeAssistantSource = computed(() => 
  gpsSourceConfigs.value.some(source => source.type === 'HOME_ASSISTANT')
)

const ownTracksUsername = computed(() => {
  // Show the first OwnTracks source
  const ownTracksSource = gpsSourceConfigs.value.find(s => s.type === 'OWNTRACKS')
  return ownTracksSource?.username
})

const ownTracksConnectionType = computed(() => {
  // Show the first OwnTracks source
  const ownTracksSource = gpsSourceConfigs.value.find(s => s.type === 'OWNTRACKS')
  return ownTracksSource?.connectionType || 'HTTP'
})

const overlandToken = computed(() => {
  const overlandSource = gpsSourceConfigs.value.find(s => s.type === 'OVERLAND')
  return overlandSource?.token
})

const dawarichApiKey = computed(() => {
  const dawarichSource = gpsSourceConfigs.value.find(s => s.type === 'DAWARICH')
  return dawarichSource?.token
})

const homeAssistantToken = computed(() => {
  const homeAssistantSource = gpsSourceConfigs.value.find(s => s.type === 'HOME_ASSISTANT')
  return homeAssistantSource?.token
})

// Mobile detection
const windowWidth = ref(typeof window !== 'undefined' ? window.innerWidth : 1024)

const isMobile = computed(() => windowWidth.value <= 768)

// Update window width on resize
if (typeof window !== 'undefined') {
  const handleResize = () => {
    windowWidth.value = window.innerWidth
  }
  window.addEventListener('resize', handleResize)
  onMounted(() => {
    handleResize()
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
    activeInstructionTab.value = firstTab.key
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
const getSourceIcon = (type) => {
  if (type === 'OWNTRACKS') return 'pi pi-mobile'
  if (type === 'OVERLAND') return 'pi pi-map'
  if (type === 'DAWARICH') return 'pi pi-key'
  if (type === 'HOME_ASSISTANT') return 'pi pi-home'
  return 'pi pi-question'
}

const getSourceDisplayName = (type) => {
  if (type === 'OWNTRACKS') return 'OwnTracks'
  if (type === 'OVERLAND') return 'Overland'
  if (type === 'DAWARICH') return 'Dawarich'
  if (type === 'HOME_ASSISTANT') return 'Home Assistant'
  return type
}

const getSourceIdentifier = (source) => {
  if (source.type === 'OWNTRACKS') return source.username || 'No username'
  if (source.type === 'OVERLAND') return source.token ? `Token: ${source.token.substring(0, 8)}...` : 'No token'
  if (source.type === 'DAWARICH') return source.token ? `API Key: ${source.token.substring(0, 8)}...` : 'No API key'
  if (source.type === 'HOME_ASSISTANT') return source.token ? `Token: ${source.token.substring(0, 8)}...` : 'No token'
  return `Unknown type: ${source.type}`
}

const handleTabChange = (event) => {
  const selectedTab = tabItems.value[event.index]
  if (selectedTab) {
    activeTab.value = selectedTab.key
    activeInstructionTab.value = selectedTab.key
  }
}

const startQuickSetup = (type) => {
  formData.value.type = type
  showAddDialog.value = true
}

const showInstructions = (source) => {
  let tabKey = source.type.toLowerCase()
  
  // Handle OwnTracks connection type specific tabs
  if (source.type === 'OWNTRACKS') {
    const connectionType = source.connectionType || 'HTTP'
    tabKey = `owntracks-${connectionType.toLowerCase()}`
  }
  
  activeTab.value = tabKey
  activeInstructionTab.value = tabKey
  // Scroll to instructions
  document.querySelector('.instructions-card')?.scrollIntoView({ behavior: 'smooth' })
}

const editSource = (source) => {
  isEditMode.value = true
  editingSource.value = source
  formData.value = {
    type: source.type,
    username: source.username || '',
    password: '',
    token: source.token || '',
    connectionType: source.connectionType || 'HTTP'
  }
  showAddDialog.value = true
}

const closeDialog = () => {
  showAddDialog.value = false
  isEditMode.value = false
  editingSource.value = null
  formData.value = {
    type: 'OWNTRACKS',
    username: '',
    password: '',
    token: '',
    connectionType: 'HTTP'
  }
  formErrors.value = {}
}

const validateForm = () => {
  formErrors.value = {}
  
  if (formData.value.type === 'OWNTRACKS') {
    if (!formData.value.username) {
      formErrors.value.username = 'Username is required'
    }
    if (!formData.value.password && !isEditMode.value) {
      formErrors.value.password = 'Password is required'
    }
  } else if (formData.value.type === 'OVERLAND') {
    if (!formData.value.token) {
      formErrors.value.token = 'Access token is required'
    }
  } else if (formData.value.type === 'DAWARICH') {
    if (!formData.value.token) {
      formErrors.value.token = 'API key is required'
    }
  } else if (formData.value.type === 'HOME_ASSISTANT') {
    if (!formData.value.token) {
      formErrors.value.token = 'Token is required'
    }
  }
  
  return Object.keys(formErrors.value).length === 0
}

const saveSource = async () => {
  if (!validateForm()) return
  
  saving.value = true
  
  try {
    if (isEditMode.value) {
      await gpsStore.updateGpsSource({
        ...editingSource.value,
        ...formData.value
      })
      toast.add({
        severity: 'success',
        summary: 'Source Updated',
        detail: 'Location source has been updated successfully',
        life: 3000
      })
    } else {
      if (formData.value.type === 'OWNTRACKS') {
        await gpsStore.addGpsConfigSource(
          formData.value.type,
          formData.value.username,
          formData.value.password,
          null, // token not used for OwnTracks
          formData.value.connectionType
        )
      } else {
        // For Overland, Dawarich, and Home Assistant - only send token, no username/password
        await gpsStore.addGpsConfigSource(
          formData.value.type,
          null, // username not used
          null, // password not used
          formData.value.token,
          'HTTP' // always HTTP for these types
        )
      }
      
      // Set the newly created source's tab as active
      let sourceType = formData.value.type.toLowerCase()
      if (formData.value.type === 'OWNTRACKS') {
        const connectionType = formData.value.connectionType.toLowerCase()
        sourceType = `owntracks-${connectionType}`
      }
      await nextTick() // Wait for DOM update
      activeTab.value = sourceType
      activeInstructionTab.value = sourceType
      
      toast.add({
        severity: 'success',
        summary: 'Source Added',
        detail: 'Location source has been added successfully',
        life: 3000
      })
    }
    closeDialog()
  } catch (error) {
    const errorMessage = error.response?.data?.message || error.message || 'An error occurred'
    toast.add({
      severity: 'error',
      summary: isEditMode.value ? 'Update Failed' : 'Add Failed',
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
  try {
    await navigator.clipboard.writeText(text)
    toast.add({
      severity: 'success',
      summary: 'Copied',
      detail: 'Text copied to clipboard',
      life: 2000
    })
  } catch (error) {
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

const getDawarichUrl = () => {
  return `${window.location.origin}/api/dawarich`
}


const getHomeAssistantConfigYaml = () => {
  return `rest_command:
  send_gps_data:
    url: "http://BACKEND_SERVER_URL/api/homeassistant"
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
          "accuracy": {{ state_attr('device_tracker.iphone_16', 'gps_accuracy') | default(0) }},
          "altitude": {{ state_attr('device_tracker.iphone_16', 'altitude') | default(0) }},
          "speed": {{ state_attr('device_tracker.iphone_16', 'speed') | default(0) }}
        },
        "battery": {
          "level": {{ state_attr('device_tracker.iphone_16', 'battery_level') | default(0) }}
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
    await gpsStore.fetchGpsConfigSources()
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

/* Dialog */
.source-dialog {
  min-width: 500px;
}

.dialog-content {
  display: flex;
  flex-direction: column;
  gap: 1.5rem;
}

.source-type-selection {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.source-types {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.source-type-option {
  display: flex;
  align-items: center;
  gap: 1rem;
  padding: 1rem;
  border: 2px solid var(--gp-border-light);
  border-radius: var(--gp-radius-medium);
  cursor: pointer;
  transition: all 0.2s ease;
  background: var(--gp-surface-white);
}

.source-type-option:hover {
  border-color: var(--gp-border-medium);
  background: var(--gp-surface-light);
}

.source-type-option.active {
  border-color: var(--gp-primary);
  background: var(--gp-surface-white);
  box-shadow: 0 0 0 3px rgba(26, 86, 219, 0.1);
}

.source-type-option.active:hover {
  background: var(--gp-surface-white);
}

.type-name {
  font-weight: 600;
  color: var(--gp-text-primary);
  margin-bottom: 0.25rem;
}

.type-description {
  font-size: 0.85rem;
  color: var(--gp-text-secondary);
  line-height: 1.3;
}

.source-type-option.active .type-description {
  color: var(--gp-text-secondary);
}

.form-section {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.form-field {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.form-label {
  font-weight: 600;
  color: var(--gp-text-primary);
}

.connection-type-selection {
  display: flex;
  gap: 0.75rem;
}

.connection-type-option {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  padding: 0.75rem;
  border: 2px solid var(--gp-border-light);
  border-radius: var(--gp-radius-medium);
  cursor: pointer;
  transition: all 0.2s ease;
  background: var(--gp-surface-white);
  flex: 1;
}

.connection-type-option:hover {
  border-color: var(--gp-border-medium);
  background: var(--gp-surface-light);
}

.connection-type-option.active {
  border-color: var(--gp-primary);
  background: var(--gp-surface-white);
  box-shadow: 0 0 0 3px rgba(26, 86, 219, 0.1);
}

.connection-type-option.active:hover {
  background: var(--gp-surface-white);
}

.connection-type-name {
  font-weight: 600;
  color: var(--gp-text-primary);
  margin-bottom: 0.125rem;
  font-size: 0.9rem;
}

.connection-type-description {
  font-size: 0.8rem;
  color: var(--gp-text-secondary);
  line-height: 1.2;
}

.error-message {
  color: var(--gp-danger);
  font-size: 0.85rem;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 1rem;
  padding-top: 1rem;
  border-top: 1px solid var(--gp-border-light);
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

.p-dark .source-type-option {
  background: var(--gp-surface-dark);
  border-color: var(--gp-border-dark);
}

.p-dark .source-type-option:hover {
  background: var(--gp-surface-light);
  border-color: var(--gp-border-medium);
}

.p-dark .source-type-option.active {
  background: var(--gp-surface-dark);
  border-color: var(--gp-primary);
  box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.1);
}

.p-dark .source-type-option.active:hover {
  background: var(--gp-surface-dark);
}

.p-dark .type-name {
  color: var(--gp-text-primary);
}

.p-dark .type-description {
  color: var(--gp-text-secondary);
}

.p-dark .form-label {
  color: var(--gp-text-primary);
}

.p-dark .dialog-footer {
  border-top-color: var(--gp-border-dark);
}

.p-dark .connection-type-option {
  background: var(--gp-surface-dark);
  border-color: var(--gp-border-dark);
}

.p-dark .connection-type-option:hover {
  background: var(--gp-surface-light);
  border-color: var(--gp-border-medium);
}

.p-dark .connection-type-option.active {
  background: var(--gp-surface-dark);
  border-color: var(--gp-primary);
  box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.1);
}

.p-dark .connection-type-option.active:hover {
  background: var(--gp-surface-dark);
}

.p-dark .connection-type-name {
  color: var(--gp-text-primary);
}

.p-dark .connection-type-description {
  color: var(--gp-text-secondary);
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
  
  .source-dialog {
    min-width: 90vw;
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