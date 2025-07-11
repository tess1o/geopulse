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
                Set up OwnTracks or Overland to automatically sync your location history.
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
            <div class="grid grid-cols-1 md:grid-cols-2 gap-6">
              <div class="source-option">
                <div class="source-header">
                  <i class="pi pi-mobile text-2xl text-blue-500"></i>
                  <div>
                    <h3 class="source-name">OwnTracks</h3>
                    <p class="source-description">Open-source location tracking with username/password authentication</p>
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
                  <div class="source-status">
                    <Badge 
                      :value="source.active ? 'Active' : 'Inactive'" 
                      :severity="source.active ? 'success' : 'secondary'"
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
              <!-- OwnTracks Tab -->
              <div v-if="activeTab === 'owntracks' && hasOwnTracksSource">
                <div class="instruction-content">
                  <h3 class="instruction-title">OwnTracks Configuration</h3>
                  <div class="instruction-steps">
                    <div class="step">
                      <div class="step-number">1</div>
                      <div class="step-content">
                        <div class="step-title">Server URL</div>
                        <div class="copy-field">
                          <code>{{ gpsSourcesEndpoints?.owntracksUrl }}</code>
                          <Button 
                            icon="pi pi-copy"
                            size="small"
                            outlined
                            @click="copyToClipboard(gpsSourcesEndpoints?.owntracksUrl)"
                          />
                        </div>
                      </div>
                    </div>
                    
                    <div class="step">
                      <div class="step-number">2</div>
                      <div class="step-content">
                        <div class="step-title">Mode</div>
                        <div class="step-value">HTTP</div>
                      </div>
                    </div>
                    
                    <div class="step">
                      <div class="step-number">3</div>
                      <div class="step-content">
                        <div class="step-title">Authentication</div>
                        <div class="step-value">
                          Use username: <strong>{{ ownTracksUsername || 'your-username' }}</strong>
                          and your configured password
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
                          <code>{{ gpsSourcesEndpoints?.overlandUrl }}</code>
                          <Button 
                            icon="pi pi-copy"
                            size="small"
                            outlined
                            @click="copyToClipboard(gpsSourcesEndpoints?.overlandUrl)"
                          />
                        </div>
                      </div>
                    </div>
                    
                    <div class="step">
                      <div class="step-number">2</div>
                      <div class="step-content">
                        <div class="step-title">Access Token</div>
                        <div class="copy-field">
                          <code>{{ overlandToken || 'your-token-here' }}</code>
                          <Button 
                            v-if="overlandToken"
                            icon="pi pi-copy"
                            size="small"
                            outlined
                            @click="copyToClipboard(overlandToken)"
                          />
                        </div>
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
const { gpsSourceConfigs, gpsSourcesEndpoints } = storeToRefs(gpsStore)

// Services
const toast = useToast()
const confirm = useConfirm()

// State
const showAddDialog = ref(false)
const isEditMode = ref(false)
const editingSource = ref(null)
const saving = ref(false)
const activeInstructionTab = ref('owntracks')
const activeTab = ref('owntracks')

// Form data
const formData = ref({
  type: 'OWNTRACKS',
  username: '',
  password: '',
  token: ''
})

const formErrors = ref({})

// Source type options
const sourceTypes = [
  {
    value: 'OWNTRACKS',
    label: 'OwnTracks',
    description: 'Open-source location tracking with username/password authentication',
    icon: 'pi pi-mobile'
  },
  {
    value: 'OVERLAND',
    label: 'Overland',
    description: 'Simple HTTP endpoint with token-based authentication',
    icon: 'pi pi-map'
  }
]

// Computed
const hasAnySources = computed(() => gpsSourceConfigs.value.length > 0)

const hasOwnTracksSource = computed(() => 
  gpsSourceConfigs.value.some(source => source.type === 'OWNTRACKS')
)

const hasOverlandSource = computed(() => 
  gpsSourceConfigs.value.some(source => source.type === 'OVERLAND')
)

const ownTracksUsername = computed(() => {
  const ownTracksSource = gpsSourceConfigs.value.find(s => s.type === 'OWNTRACKS')
  return ownTracksSource?.username
})

const overlandToken = computed(() => {
  const overlandSource = gpsSourceConfigs.value.find(s => s.type === 'OVERLAND')
  return overlandSource?.token
})

// Tab configuration
const tabItems = computed(() => {
  const tabs = []
  if (hasOwnTracksSource.value) {
    tabs.push({
      label: 'OwnTracks',
      icon: 'pi pi-mobile',
      key: 'owntracks'
    })
  }
  if (hasOverlandSource.value) {
    tabs.push({
      label: 'Overland', 
      icon: 'pi pi-map',
      key: 'overland'
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
  return type === 'OWNTRACKS' ? 'pi pi-mobile' : 'pi pi-map'
}

const getSourceDisplayName = (type) => {
  return type === 'OWNTRACKS' ? 'OwnTracks' : 'Overland'
}

const getSourceIdentifier = (source) => {
  return source.type === 'OWNTRACKS' ? source.username : `Token: ${source.token?.substring(0, 8)}...`
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
  activeTab.value = source.type.toLowerCase()
  activeInstructionTab.value = source.type.toLowerCase()
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
    token: source.token || ''
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
    token: ''
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
      await gpsStore.addGpsConfigSource(
        formData.value.type,
        formData.value.username,
        formData.value.password,
        formData.value.token
      )
      
      // Set the newly created source's tab as active
      const sourceType = formData.value.type.toLowerCase()
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

// Lifecycle
onMounted(async () => {
  try {
    await gpsStore.fetchAllGpsData()
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

.instruction-content {
  padding: 1rem 0;
  margin-left: 0.5rem;
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

.copy-field {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.75rem;
  background: var(--gp-surface-light);
  border: 1px solid var(--gp-border-light);
  border-radius: var(--gp-radius-small);
}

.copy-field code {
  flex: 1;
  font-family: var(--font-mono, monospace);
  font-size: 0.9rem;
  color: var(--gp-text-primary);
  word-break: break-all;
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
}
</style>