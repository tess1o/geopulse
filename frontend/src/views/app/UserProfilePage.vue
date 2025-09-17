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
            <!-- Profile Information Tab -->
            <div v-if="activeTab === 'profile'">
                <Card class="profile-info-card">
                  <template #content>
                    <form @submit.prevent="saveProfile" class="profile-form">
                      <!-- Avatar Section -->
                      <div class="avatar-section">
                        <div class="avatar-preview">
                          <Avatar 
                            :image="selectedAvatar || '/avatars/avatar1.png'"
                            size="xlarge"
                            class="user-avatar"
                          />
                          <div class="avatar-info">
                            <h3 class="avatar-title">Profile Picture</h3>
                            <p class="avatar-description">Choose your avatar from the options below</p>
                          </div>
                        </div>
                        
                        <div class="avatar-grid">
                          <div 
                            v-for="(avatar, index) in avatarOptions" 
                            :key="index"
                            :class="['avatar-option', { active: avatar === selectedAvatar }]"
                            @click="selectedAvatar = avatar"
                          >
                            <Avatar :image="avatar" size="large" />
                          </div>
                        </div>
                      </div>

                      <!-- Full Name Field -->
                      <div class="form-section">
                        <div class="form-field">
                          <label for="fullName" class="form-label">Full Name</label>
                          <InputText 
                            id="fullName"
                            v-model="profileForm.fullName"
                            placeholder="Enter your full name"
                            :invalid="!!profileErrors.fullName"
                            class="w-full"
                          />
                          <small v-if="profileErrors.fullName" class="error-message">
                            {{ profileErrors.fullName }}
                          </small>
                        </div>

                        <div class="form-field">
                          <label for="email" class="form-label">Email Address</label>
                          <InputText 
                            id="email"
                            :value="userEmail"
                            disabled
                            class="w-full"
                          />
                          <small class="help-text">Email cannot be changed</small>
                        </div>
                      </div>

                      <!-- Action Buttons -->
                      <div class="form-actions">
                        <Button 
                          type="button"
                          label="Reset"
                          outlined
                          @click="resetProfile"
                          :disabled="profileLoading"
                        />
                        <Button 
                          type="submit"
                          label="Save Changes"
                          :loading="profileLoading"
                          :disabled="!hasProfileChanges"
                        />
                      </div>
                    </form>
                  </template>
                </Card>
            </div>

            <!-- Security Tab -->
            <div v-if="activeTab === 'security'">
                <Card class="security-card">
                  <template #content>
                    <form @submit.prevent="changePassword" class="security-form">
                      <div class="security-header">
                        <div class="security-icon">
                          <i class="pi pi-lock"></i>
                        </div>
                        <div class="security-info">
                          <h3 class="security-title">{{ hasPassword ? 'Change Password' : 'Set Password' }}</h3>
                          <p class="security-description">
                            {{ hasPassword ? 'Update your password to keep your account secure' : 'Set a password for your account' }}
                          </p>
                        </div>
                      </div>

                      <div class="form-section">
                        <div v-if="hasPassword" class="form-field">
                          <label for="currentPassword" class="form-label">Current Password</label>
                          <Password 
                            id="currentPassword"
                            v-model="passwordForm.currentPassword"
                            placeholder="Enter current password"
                            :feedback="false"
                            toggleMask
                            :invalid="!!passwordErrors.currentPassword"
                            class="w-full"
                          />
                          <small v-if="passwordErrors.currentPassword" class="error-message">
                            {{ passwordErrors.currentPassword }}
                          </small>
                        </div>

                        <div class="form-field">
                          <label for="newPassword" class="form-label">New Password</label>
                          <Password 
                            id="newPassword"
                            v-model="passwordForm.newPassword"
                            placeholder="Enter new password"
                            :feedback="true"
                            toggleMask
                            :invalid="!!passwordErrors.newPassword"
                            class="w-full"
                          />
                          <small v-if="passwordErrors.newPassword" class="error-message">
                            {{ passwordErrors.newPassword }}
                          </small>
                        </div>

                        <div class="form-field">
                          <label for="confirmPassword" class="form-label">Confirm New Password</label>
                          <Password 
                            id="confirmPassword"
                            v-model="passwordForm.confirmPassword"
                            placeholder="Confirm new password"
                            :feedback="false"
                            toggleMask
                            :invalid="!!passwordErrors.confirmPassword"
                            class="w-full"
                          />
                          <small v-if="passwordErrors.confirmPassword" class="error-message">
                            {{ passwordErrors.confirmPassword }}
                          </small>
                        </div>
                      </div>

                      <!-- Action Buttons -->
                      <div class="form-actions">
                        <Button 
                          type="button"
                          label="Cancel"
                          outlined
                          @click="resetPasswordForm"
                          :disabled="passwordLoading"
                        />
                        <Button 
                          type="submit"
                          :label="hasPassword ? 'Change Password' : 'Set Password'"
                          :loading="passwordLoading"
                          :disabled="!hasPasswordChanges"
                        />
                      </div>
                    </form>
                  </template>
                </Card>

                <OidcManagement class="mt-6" />
            </div>

            <!-- Immich Integration Tab -->
            <div v-if="activeTab === 'immich'">
                <Card class="immich-card">
                  <template #content>
                    <form @submit.prevent="saveImmichConfig" class="immich-form">
                      <div class="immich-header">
                        <div class="immich-icon">
                          <i class="pi pi-images"></i>
                        </div>
                        <div class="immich-info">
                          <h3 class="immich-title">Immich Integration</h3>
                          <p class="immich-description">
                            Connect your Immich photo server to display photos on your timeline
                          </p>
                        </div>
                      </div>

                      <div class="form-section">
                        <!-- Enable/Disable Toggle -->
                        <div class="form-field">
                          <div class="toggle-field">
                            <ToggleButton 
                              v-model="immichForm.enabled"
                              onLabel="Enabled" 
                              offLabel="Disabled"
                              :disabled="immichLoading || immichSaveLoading"
                            />
                            <div class="toggle-info">
                              <span class="toggle-label">Enable Immich Integration</span>
                              <span class="toggle-description">
                                Turn on to sync photos from your Immich server
                              </span>
                            </div>
                          </div>
                        </div>

                        <!-- Server URL Field -->
                        <div class="form-field">
                          <label for="immichServerUrl" class="form-label">Server URL</label>
                          <InputText 
                            id="immichServerUrl"
                            v-model="immichForm.serverUrl"
                            placeholder="https://photos.example.com"
                            :invalid="!!immichErrors.serverUrl"
                            :disabled="!immichForm.enabled || immichLoading || immichSaveLoading"
                            class="w-full"
                          />
                          <small v-if="immichErrors.serverUrl" class="error-message">
                            {{ immichErrors.serverUrl }}
                          </small>
                          <small v-else class="help-text">
                            Enter the full URL to your Immich server
                          </small>
                        </div>

                        <!-- API Key Field -->
                        <div class="form-field">
                          <label for="immichApiKey" class="form-label">API Key</label>
                          <Password 
                            id="immichApiKey"
                            v-model="immichForm.apiKey"
                            :placeholder="immichConfig?.apiKey ? 'API key is set (enter new key to replace)' : 'Enter your Immich API key'"
                            :feedback="false"
                            toggleMask
                            :invalid="!!immichErrors.apiKey"
                            :disabled="!immichForm.enabled || immichLoading || immichSaveLoading"
                            class="w-full"
                          />
                          <small v-if="immichErrors.apiKey" class="error-message">
                            {{ immichErrors.apiKey }}
                          </small>
                          <small v-else-if="immichConfig?.apiKey && !immichForm.apiKey" class="help-text">
                            <i class="pi pi-check-circle" style="color: var(--gp-success);"></i>
                            API key is configured. Leave empty to keep current key.
                          </small>
                          <small v-else class="help-text">
                            Create an API key in your Immich server settings
                          </small>
                        </div>

                        <!-- Connection Status -->
                        <div v-if="immichConfig" class="connection-status">
                          <div class="status-indicator">
                            <i :class="['pi', immichConfig.enabled ? 'pi-check-circle' : 'pi-times-circle']"></i>
                            <span>
                              {{ immichConfig.enabled ? 'Connected' : 'Disconnected' }}
                            </span>
                          </div>
                          <small v-if="immichConfig.enabled && immichConfig.serverUrl" class="help-text">
                            Connected to: {{ immichConfig.serverUrl }}
                          </small>
                        </div>
                      </div>

                      <!-- Action Buttons -->
                      <div class="form-actions">
                        <Button 
                          type="button"
                          label="Reset"
                          outlined
                          @click="resetImmichForm"
                          :disabled="immichLoading || immichSaveLoading"
                        />
                        <Button 
                          type="submit"
                          label="Save Settings"
                          :loading="immichSaveLoading"
                          :disabled="!hasImmichChanges || immichLoading"
                        />
                      </div>
                    </form>
                  </template>
                </Card>
            </div>
          </TabContainer>
        </div>

        <Toast />
      </div>
    </PageContainer>
  </AppLayout>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { storeToRefs } from 'pinia'
import { useToast } from 'primevue/usetoast'

// Layout components
import AppLayout from '@/components/ui/layout/AppLayout.vue'
import PageContainer from '@/components/ui/layout/PageContainer.vue'
import TabContainer from '@/components/ui/layout/TabContainer.vue'
import OidcManagement from '@/components/auth/OidcManagement.vue';

// Store
import { useAuthStore } from '@/stores/auth'
import { useImmichStore } from '@/stores/immich'

// Composables
const toast = useToast()
const authStore = useAuthStore()
const immichStore = useImmichStore()

// Store refs
const { userId, userName, userAvatar, userEmail, hasPassword } = storeToRefs(authStore)
const { config: immichConfig, configLoading: immichLoading } = storeToRefs(immichStore)

// State
const activeTab = ref('profile')
const profileLoading = ref(false)
const passwordLoading = ref(false)
const immichSaveLoading = ref(false)
const selectedAvatar = ref('')

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
    label: 'Immich',
    icon: 'pi pi-images',
    key: 'immich'
  }
])

const activeTabIndex = computed(() => {
  return tabItems.value.findIndex(tab => tab.key === activeTab.value)
})

// Form data
const profileForm = ref({
  fullName: ''
})

const passwordForm = ref({
  currentPassword: '',
  newPassword: '',
  confirmPassword: ''
})

const immichForm = ref({
  serverUrl: '',
  apiKey: '',
  enabled: false
})

// Form errors
const profileErrors = ref({})
const passwordErrors = ref({})
const immichErrors = ref({})

// Avatar options
const avatarOptions = [
  '/avatars/avatar1.png',
  '/avatars/avatar2.png',
  '/avatars/avatar3.png',
  '/avatars/avatar4.png',
  '/avatars/avatar5.png',
  '/avatars/avatar6.png',
  '/avatars/avatar7.png',
  '/avatars/avatar8.png',
  '/avatars/avatar9.png',
  '/avatars/avatar10.png',
  '/avatars/avatar11.png',
  '/avatars/avatar12.png',
  '/avatars/avatar13.png',
  '/avatars/avatar14.png',
  '/avatars/avatar15.png',
  '/avatars/avatar16.png',
  '/avatars/avatar17.png',
  '/avatars/avatar18.png',
  '/avatars/avatar19.png',
  '/avatars/avatar20.png',
]

// Computed
const hasProfileChanges = computed(() => {
  return profileForm.value.fullName !== userName.value || 
         selectedAvatar.value !== userAvatar.value
})

const hasPasswordChanges = computed(() => {
  return passwordForm.value.currentPassword || 
         passwordForm.value.newPassword || 
         passwordForm.value.confirmPassword
})

const hasImmichChanges = computed(() => {
  // If no config exists, check if user has made any changes from defaults
  if (!immichConfig.value) {
    return immichForm.value.enabled || 
           (immichForm.value.serverUrl?.trim() || '') !== '' || 
           (immichForm.value.apiKey?.trim() || '') !== ''
  }
  
  // Compare with existing config
  const serverUrlChanged = immichForm.value.serverUrl !== (immichConfig.value.serverUrl || '')
  const enabledChanged = immichForm.value.enabled !== (immichConfig.value.enabled || false)
  // Check if new API key is provided (since we don't show existing keys)
  const apiKeyChanged = (immichForm.value.apiKey?.trim() || '') !== ''
  
  return serverUrlChanged || enabledChanged || apiKeyChanged
})

// Methods
const handleTabChange = (event) => {
  const selectedTab = tabItems.value[event.index]
  if (selectedTab) {
    activeTab.value = selectedTab.key
  }
}

// Validation
const validateProfile = () => {
  profileErrors.value = {}
  
  if (!profileForm.value.fullName?.trim()) {
    profileErrors.value.fullName = 'Full name is required'
  } else if (profileForm.value.fullName.trim().length < 2) {
    profileErrors.value.fullName = 'Full name must be at least 2 characters'
  }
  
  return Object.keys(profileErrors.value).length === 0
}

const validatePassword = () => {
  passwordErrors.value = {}
  
  // Only require current password if user has a password
  if (hasPassword.value && !passwordForm.value.currentPassword) {
    passwordErrors.value.currentPassword = 'Current password is required'
  }
  
  if (!passwordForm.value.newPassword) {
    passwordErrors.value.newPassword = 'New password is required'
  } else if (passwordForm.value.newPassword.length < 6) {
    passwordErrors.value.newPassword = 'Password must be at least 6 characters'
  }
  
  if (!passwordForm.value.confirmPassword) {
    passwordErrors.value.confirmPassword = 'Please confirm your new password'
  } else if (passwordForm.value.newPassword !== passwordForm.value.confirmPassword) {
    passwordErrors.value.confirmPassword = 'Passwords do not match'
  }
  
  return Object.keys(passwordErrors.value).length === 0
}

const validateImmich = () => {
  immichErrors.value = {}
  
  if (immichForm.value.enabled) {
    if (!immichForm.value.serverUrl?.trim()) {
      immichErrors.value.serverUrl = 'Server URL is required when integration is enabled'
    } else {
      try {
        new URL(immichForm.value.serverUrl.trim())
      } catch {
        immichErrors.value.serverUrl = 'Please enter a valid URL (e.g., https://photos.example.com)'
      }
    }
    
    // Only require API key if no existing key is set, or if user is trying to change it
    if (!immichForm.value.apiKey?.trim() && !immichConfig.value?.apiKey) {
      immichErrors.value.apiKey = 'API Key is required when integration is enabled'
    }
  }
  
  return Object.keys(immichErrors.value).length === 0
}

// Methods
const saveProfile = async () => {
  if (!validateProfile()) return
  
  profileLoading.value = true
  
  try {
    await authStore.updateProfile(
      profileForm.value.fullName.trim(),
      selectedAvatar.value,
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
  } finally {
    profileLoading.value = false
  }
}

const changePassword = async () => {
  if (!validatePassword()) return
  
  passwordLoading.value = true
  
  try {
    await authStore.changePassword(
      hasPassword.value ? passwordForm.value.currentPassword : null,
      passwordForm.value.newPassword,
      userId.value
    )
    
    resetPasswordForm()
    
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
  } finally {
    passwordLoading.value = false
  }
}

const saveImmichConfig = async () => {
  if (!validateImmich()) return
  
  immichSaveLoading.value = true
  
  try {
    const configData = {
      serverUrl: immichForm.value.serverUrl?.trim() || null,
      // Use new API key if provided, otherwise keep existing one
      apiKey: immichForm.value.apiKey?.trim() || immichConfig.value?.apiKey || null,
      enabled: immichForm.value.enabled
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
  } finally {
    immichSaveLoading.value = false
  }
}

const resetProfile = () => {
  profileForm.value.fullName = userName.value || ''
  selectedAvatar.value = userAvatar.value || '/avatars/avatar1.png'
  profileErrors.value = {}
}

const resetPasswordForm = () => {
  passwordForm.value = {
    currentPassword: '',
    newPassword: '',
    confirmPassword: ''
  }
  passwordErrors.value = {}
}

const resetImmichForm = () => {
  immichForm.value = {
    serverUrl: immichConfig.value?.serverUrl || '',
    // Never populate existing API key for security
    apiKey: '',
    enabled: immichConfig.value?.enabled || false
  }
  immichErrors.value = {}
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

// Watchers
watch(() => profileForm.value.fullName, () => {
  if (profileErrors.value.fullName) {
    validateProfile()
  }
})

watch(() => [passwordForm.value.newPassword, passwordForm.value.confirmPassword], () => {
  if (passwordErrors.value.confirmPassword && passwordForm.value.newPassword === passwordForm.value.confirmPassword) {
    delete passwordErrors.value.confirmPassword
  }
})

watch(() => [immichForm.value.serverUrl, immichForm.value.apiKey], () => {
  if (immichErrors.value.serverUrl || immichErrors.value.apiKey) {
    validateImmich()
  }
})

watch(immichConfig, (newConfig) => {
  if (newConfig) {
    resetImmichForm()
  }
}, { immediate: true })

// Lifecycle
onMounted(async () => {
  resetProfile()
  
  // Load Immich config
  try {
    await immichStore.fetchConfig()
  } catch (error) {
    console.warn('Failed to load Immich config:', error)
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

/* Profile Info Card */
.profile-info-card,
.security-card,
.immich-card {
  background: var(--gp-surface-white);
  border: 1px solid var(--gp-border-light);
  box-shadow: var(--gp-shadow-light);
  width: 100%;
  box-sizing: border-box;
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

/* Force consistent card widths */
.profile-info-card :deep(.p-card-content),
.security-card :deep(.p-card-content),
.immich-card :deep(.p-card-content) {
  width: 100%;
  box-sizing: border-box;
  padding: 1.5rem;
}

/* Avatar Section */
.avatar-section {
  margin-bottom: 2rem;
}

.avatar-preview {
  display: flex;
  align-items: center;
  gap: 1.5rem;
  margin-bottom: 1.5rem;
  padding: 1rem;
  background: var(--gp-surface-light);
  border-radius: var(--gp-radius-medium);
}

.user-avatar {
  width: 80px !important;
  height: 80px !important;
  border: 3px solid var(--gp-primary);
  flex-shrink: 0;
}

.avatar-info {
  flex: 1;
}

.avatar-title {
  font-size: 1.1rem;
  font-weight: 600;
  color: var(--gp-text-primary);
  margin: 0 0 0.25rem 0;
}

.avatar-description {
  font-size: 0.9rem;
  color: var(--gp-text-secondary);
  margin: 0;
  line-height: 1.4;
}

.avatar-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(80px, 1fr));
  gap: 0.75rem;
  padding: 1rem;
  background: var(--gp-surface-light);
  border-radius: var(--gp-radius-medium);
  max-height: 200px;
  overflow-y: auto;
}

.avatar-option {
  display: flex;
  justify-content: center;
  align-items: center;
  padding: 0.5rem;
  border: 2px solid transparent;
  border-radius: var(--gp-radius-small);
  cursor: pointer;
  transition: all 0.2s ease;
  background: var(--gp-surface-white);
}

.avatar-option:hover {
  border-color: var(--gp-border-medium);
  transform: translateY(-1px);
  box-shadow: var(--gp-shadow-light);
}

.avatar-option.active {
  border-color: var(--gp-primary);
  background: var(--gp-primary-light);
  box-shadow: 0 0 0 2px rgba(26, 86, 219, 0.1);
}

/* Security Section */
.security-header {
  display: flex;
  align-items: center;
  gap: 1rem;
  margin-bottom: 2rem;
  padding: 1rem;
  background: var(--gp-surface-light);
  border-radius: var(--gp-radius-medium);
}

.security-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 3rem;
  height: 3rem;
  background: var(--gp-primary);
  color: white;
  border-radius: 50%;
  font-size: 1.25rem;
  flex-shrink: 0;
}

.security-info {
  flex: 1;
}

.security-title {
  font-size: 1.1rem;
  font-weight: 600;
  color: var(--gp-text-primary);
  margin: 0 0 0.25rem 0;
}

.security-description {
  font-size: 0.9rem;
  color: var(--gp-text-secondary);
  margin: 0;
  line-height: 1.4;
}

/* Immich Section */
.immich-header {
  display: flex;
  align-items: center;
  gap: 1rem;
  margin-bottom: 2rem;
  padding: 1rem;
  background: var(--gp-surface-light);
  border-radius: var(--gp-radius-medium);
}

.immich-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 3rem;
  height: 3rem;
  background: var(--gp-primary);
  color: white;
  border-radius: 50%;
  font-size: 1.25rem;
  flex-shrink: 0;
}

.immich-info {
  flex: 1;
}

.immich-title {
  font-size: 1.1rem;
  font-weight: 600;
  color: var(--gp-text-primary);
  margin: 0 0 0.25rem 0;
}

.immich-description {
  font-size: 0.9rem;
  color: var(--gp-text-secondary);
  margin: 0;
  line-height: 1.4;
}

.toggle-field {
  display: flex;
  align-items: center;
  gap: 1rem;
  padding: 1rem;
  background: var(--gp-surface-light);
  border-radius: var(--gp-radius-medium);
}

.toggle-info {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
}

.toggle-label {
  font-weight: 600;
  color: var(--gp-text-primary);
  font-size: 0.9rem;
}

.toggle-description {
  font-size: 0.8rem;
  color: var(--gp-text-secondary);
  line-height: 1.4;
}

.connection-status {
  padding: 1rem;
  background: var(--gp-surface-light);
  border-radius: var(--gp-radius-medium);
  margin-top: 0.5rem;
}

.status-indicator {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  font-weight: 600;
  margin-bottom: 0.25rem;
}

.status-indicator i {
  font-size: 1.1rem;
}

.status-indicator .pi-check-circle {
  color: var(--gp-success);
}

.status-indicator .pi-times-circle {
  color: var(--gp-text-secondary);
}

/* Form Sections */
.form-section {
  display: flex;
  flex-direction: column;
  gap: 1.5rem;
  margin-bottom: 2rem;
}

.form-field {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.form-label {
  font-weight: 600;
  color: var(--gp-text-primary);
  font-size: 0.9rem;
}

.help-text {
  color: var(--gp-text-secondary);
  font-size: 0.8rem;
  font-style: italic;
}

.error-message {
  color: var(--gp-danger);
  font-size: 0.85rem;
}

.form-actions {
  display: flex;
  justify-content: flex-end;
  gap: 1rem;
  margin-top: 1rem;
  padding-top: 1rem;
  border-top: 1px solid var(--gp-border-light);
}

/* Profile Content Styling */
.profile-content {
  margin-top: 1.5rem;
}

/* Input Styling */
:deep(.p-inputtext) {
  border-radius: var(--gp-radius-medium);
  border: 1px solid var(--gp-border-medium);
  padding: 0.75rem 1rem;
  transition: all 0.2s ease;
}

:deep(.p-inputtext:focus) {
  border-color: var(--gp-primary);
  box-shadow: 0 0 0 3px rgba(26, 86, 219, 0.1);
}

:deep(.p-inputtext:disabled) {
  background: var(--gp-surface-light);
  color: var(--gp-text-secondary);
}

:deep(.p-password) {
  width: 100%;
}

:deep(.p-password-input) {
  border-radius: var(--gp-radius-medium);
  border: 1px solid var(--gp-border-medium);
  padding: 0.75rem 1rem;
  transition: all 0.2s ease;
  width: 100%;
}

:deep(.p-password-input:focus) {
  border-color: var(--gp-primary);
  box-shadow: 0 0 0 3px rgba(26, 86, 219, 0.1);
}

/* Button Styling */
:deep(.p-button) {
  border-radius: var(--gp-radius-medium);
  font-weight: 600;
  padding: 0.75rem 1.5rem;
  transition: all 0.2s ease;
}

:deep(.p-button:not(.p-button-outlined)) {
  background: var(--gp-primary);
  border-color: var(--gp-primary);
}

:deep(.p-button:not(.p-button-outlined):hover) {
  background: var(--gp-primary-hover);
  border-color: var(--gp-primary-hover);
  transform: translateY(-1px);
  box-shadow: var(--gp-shadow-medium);
}

:deep(.p-button-outlined) {
  border-color: var(--gp-border-medium);
  color: var(--gp-text-primary);
}

:deep(.p-button-outlined:hover) {
  background: var(--gp-surface-light);
  border-color: var(--gp-primary);
  color: var(--gp-primary);
}


/* Responsive Design */
@media (max-width: 768px) {
  .user-profile-page {
    padding: 0 0.5rem;
    max-width: calc(100vw - 1rem);
    box-sizing: border-box;
  }
  
  .page-title {
    font-size: 1.5rem;
  }
  
  .avatar-preview {
    flex-direction: column;
    text-align: center;
    gap: 1rem;
  }
  
  .avatar-grid {
    grid-template-columns: repeat(4, 1fr);
    max-height: 150px;
  }
  
  .security-header,
  .immich-header {
    flex-direction: column;
    text-align: center;
    gap: 1rem;
  }
  
  .toggle-field {
    flex-direction: column;
    text-align: center;
    gap: 1rem;
  }
  
  .form-actions {
    flex-direction: column;
  }
}

@media (max-width: 480px) {
  .user-profile-page {
    padding: 0 0.75rem;
    max-width: calc(100vw - 1.5rem);
    box-sizing: border-box;
  }
  
  .avatar-grid {
    grid-template-columns: repeat(3, 1fr);
  }
  
  .page-header {
    margin-bottom: 1.5rem;
  }
  
  .page-title {
    font-size: 1.3rem;
  }
  
  .page-description {
    font-size: 1rem;
  }
  
  .form-actions .p-button {
    width: 100%;
    min-height: 48px;
  }
  
  .form-label {
    font-size: 0.9rem;
  }
  
  .help-text {
    font-size: 0.75rem;
  }
  
  .error-message {
    font-size: 0.8rem;
  }
}

/* iPhone 16 Pro Max and similar large phones */
@media (max-width: 480px) and (min-width: 430px) {
  .user-profile-page {
    padding: 0 1rem;
    max-width: calc(100vw - 2rem);
  }
  
  .profile-tabs :deep(.gp-tab-content > div) {
    width: 100% !important;
    max-width: none !important;
  }
  
  .profile-info-card,
  .security-card,
  .immich-card {
    width: 100% !important;
    max-width: 100% !important;
  }
}
</style>