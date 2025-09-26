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

                        <div class="form-field">
                          <label for="timezone" class="form-label">Timezone</label>
                          <Dropdown
                            id="timezone"
                            v-model="profileForm.timezone"
                            :options="timezoneOptions"
                            optionLabel="label"
                            optionValue="value"
                            placeholder="Select your timezone"
                            filter
                            :filterMatchMode="'contains'"
                            :invalid="!!profileErrors.timezone"
                            class="w-full"
                            scrollHeight="300px"
                          />
                          <small v-if="profileErrors.timezone" class="error-message">
                            {{ profileErrors.timezone }}
                          </small>
                          <small v-else class="help-text">
                            Your timezone is used for date displays and statistics
                          </small>
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

            <!-- AI Assistant Settings Tab -->
            <div v-if="activeTab === 'ai'">
                <Card class="ai-settings-card">
                  <template #content>
                    <form @submit.prevent="saveAISettings" class="ai-settings-form">
                      <div class="ai-header">
                        <div class="ai-icon">
                          <i class="pi pi-sparkles text-4xl text-blue-600"></i>
                        </div>
                        <div class="ai-info">
                          <h3 class="ai-title">AI Assistant Configuration</h3>
                          <p class="ai-description">
                            Configure your AI assistant settings to enable AI chat
                          </p>
                        </div>
                      </div>

                      <div class="ai-form-grid">
                        <!-- Enable/Disable Toggle -->
                        <div class="form-group">
                          <label for="ai-enabled" class="form-label">Enable AI Assistant</label>
                          <ToggleSwitch
                            id="ai-enabled"
                            v-model="aiSettings.enabled"
                            class="w-full"
                          />
                          <small class="text-secondary">
                            Enable or disable the AI Assistant functionality
                          </small>
                        </div>

                        <!-- OpenAI Settings -->
                        <div class="provider-settings">
                          <div class="form-group">
                            <label for="openai-api-key" class="form-label">OpenAI API Key</label>
                            <Password
                              id="openai-api-key"
                              v-model="aiSettings.openaiApiKey"
                              :placeholder="aiSettings.openaiApiKeyConfigured ? 'API key is configured (enter new key to replace)' : 'Enter your OpenAI API key'"
                              class="w-full"
                              :feedback="false"
                              toggleMask
                              autocomplete="new-password"
                              :inputProps="{ 
                                autocomplete: 'new-password',
                                'data-lpignore': 'true',
                                'data-form-type': 'other'
                              }"
                            />
                            <small v-if="aiSettings.openaiApiKeyConfigured && !aiSettings.openaiApiKey" class="text-secondary">
                              <i class="pi pi-check-circle" style="color: var(--gp-success);"></i>
                              API key is configured. Leave empty to keep current key.
                            </small>
                            <small v-else-if="!aiSettings.openaiApiKeyConfigured" class="text-secondary">
                              Enter your OpenAI API key to enable the assistant
                            </small>
                          </div>
                          <div class="form-group">
                            <label for="openai-api-url" class="form-label">API Base URL</label>
                            <InputText
                              id="openai-api-url"
                              v-model="aiSettings.openaiApiUrl"
                              placeholder="https://api.openai.com/v1"
                              class="w-full"
                            />
                            <small class="text-secondary">
                              Use default OpenAI URL or enter a custom OpenAI-compatible API endpoint
                            </small>
                          </div>
                          <div class="form-group">
                            <label for="openai-model" class="form-label">Model</label>
                            <Dropdown
                              id="openai-model"
                              v-model="aiSettings.openaiModel"
                              :options="openaiModels"
                              placeholder="Select or enter model name"
                              class="w-full"
                              editable
                            />
                            <small class="text-secondary">
                              Choose from common models or enter a custom model name
                            </small>
                          </div>
                        </div>
                      </div>

                      <!-- Test Connection Button -->
                      <div class="test-connection-section">
                        <Button
                          type="button"
                          label="Test Connection"
                          icon="pi pi-wifi"
                          class="p-button-outlined"
                          @click="testAIConnection"
                          :loading="testConnectionLoading"
                        />
                      </div>

                      <!-- Form Actions -->
                      <div class="form-actions">
                        <Button
                          type="submit"
                          label="Save AI Settings"
                          icon="pi pi-save"
                          :loading="aiSaveLoading"
                          class="p-button-primary"
                        />
                      </div>
                    </form>
                  </template>
                </Card>
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
import { useRoute } from 'vue-router'

// Layout components
import AppLayout from '@/components/ui/layout/AppLayout.vue'
import PageContainer from '@/components/ui/layout/PageContainer.vue'
import TabContainer from '@/components/ui/layout/TabContainer.vue'
import OidcManagement from '@/components/auth/OidcManagement.vue';

// Store
import { useAuthStore } from '@/stores/auth'
import { useImmichStore } from '@/stores/immich'
import apiService from "@/utils/apiService";

// Composables
const toast = useToast()
const route = useRoute()
const authStore = useAuthStore()
const immichStore = useImmichStore()

// Store refs
const { userId, userName, userAvatar, userEmail, hasPassword, userTimezone } = storeToRefs(authStore)
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

// Form data
const profileForm = ref({
  fullName: '',
  timezone: ''
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

const aiSettings = ref({
  enabled: false,
  openaiApiKey: '',
  openaiApiUrl: 'https://api.openai.com/v1',
  openaiModel: 'gpt-3.5-turbo',
  openaiApiKeyConfigured: false
})

// Form errors
const profileErrors = ref({})
const passwordErrors = ref({})
const immichErrors = ref({})
const aiSaveLoading = ref(false)
const testConnectionLoading = ref(false)

// Timezone options (common timezones)
const timezoneOptions = [
  { label: 'UTC', value: 'UTC' },
  { label: 'Europe/London GMT+0', value: 'Europe/London' },
  { label: 'Europe/Paris GMT+1', value: 'Europe/Paris' },
  { label: 'Europe/Berlin GMT+1', value: 'Europe/Berlin' },
  { label: 'Europe/Rome GMT+1', value: 'Europe/Rome' },
  { label: 'Europe/Madrid GMT+1', value: 'Europe/Madrid' },
  { label: 'Europe/Amsterdam GMT+1', value: 'Europe/Amsterdam' },
  { label: 'Europe/Brussels GMT+1', value: 'Europe/Brussels' },
  { label: 'Europe/Vienna GMT+1', value: 'Europe/Vienna' },
  { label: 'Europe/Stockholm GMT+1', value: 'Europe/Stockholm' },
  { label: 'Europe/Copenhagen GMT+1', value: 'Europe/Copenhagen' },
  { label: 'Europe/Oslo GMT+1', value: 'Europe/Oslo' },
  { label: 'Europe/Helsinki GMT+2', value: 'Europe/Helsinki' },
  { label: 'Europe/Athens GMT+2', value: 'Europe/Athens' },
  { label: 'Europe/Bucharest GMT+2', value: 'Europe/Bucharest' },
  { label: 'Europe/Kyiv GMT+2', value: 'Europe/Kyiv' },
  { label: 'Europe/Warsaw GMT+1', value: 'Europe/Warsaw' },
  { label: 'Europe/Prague GMT+1', value: 'Europe/Prague' },
  { label: 'Europe/Budapest GMT+1', value: 'Europe/Budapest' },
  { label: 'Europe/Moscow GMT+3', value: 'Europe/Moscow' },
  { label: 'America/New_York GMT-5', value: 'America/New_York' },
  { label: 'America/Chicago GMT-6', value: 'America/Chicago' },
  { label: 'America/Denver GMT-7', value: 'America/Denver' },
  { label: 'America/Los_Angeles GMT-8', value: 'America/Los_Angeles' },
  { label: 'America/Toronto GMT-5', value: 'America/Toronto' },
  { label: 'America/Vancouver GMT-8', value: 'America/Vancouver' },
  { label: 'America/Mexico_City GMT-6', value: 'America/Mexico_City' },
  { label: 'America/Sao_Paulo GMT-3', value: 'America/Sao_Paulo' },
  { label: 'America/Argentina/Buenos_Aires GMT-3', value: 'America/Argentina/Buenos_Aires' },
  { label: 'Asia/Tokyo GMT+9', value: 'Asia/Tokyo' },
  { label: 'Asia/Shanghai GMT+8', value: 'Asia/Shanghai' },
  { label: 'Asia/Hong_Kong GMT+8', value: 'Asia/Hong_Kong' },
  { label: 'Asia/Singapore GMT+8', value: 'Asia/Singapore' },
  { label: 'Asia/Seoul GMT+9', value: 'Asia/Seoul' },
  { label: 'Asia/Bangkok GMT+7', value: 'Asia/Bangkok' },
  { label: 'Asia/Jakarta GMT+7', value: 'Asia/Jakarta' },
  { label: 'Asia/Manila GMT+8', value: 'Asia/Manila' },
  { label: 'Asia/Kolkata GMT+5:30', value: 'Asia/Kolkata' },
  { label: 'Asia/Dubai GMT+4', value: 'Asia/Dubai' },
  { label: 'Asia/Tehran GMT+3:30', value: 'Asia/Tehran' },
  { label: 'Australia/Sydney GMT+10', value: 'Australia/Sydney' },
  { label: 'Australia/Melbourne GMT+10', value: 'Australia/Melbourne' },
  { label: 'Australia/Perth GMT+8', value: 'Australia/Perth' },
  { label: 'Pacific/Auckland GMT+12', value: 'Pacific/Auckland' },
  { label: 'Africa/Cairo GMT+2', value: 'Africa/Cairo' },
  { label: 'Africa/Johannesburg GMT+2', value: 'Africa/Johannesburg' },
  { label: 'Africa/Lagos GMT+1', value: 'Africa/Lagos' },
  { label: 'Africa/Nairobi GMT+3', value: 'Africa/Nairobi' }
]

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

// Removed unused providerOptions - keeping only OpenAI for simplified implementation

const openaiModels = [
  'gpt-4o',
  'gpt-4o-mini',
  'gpt-3.5-turbo',
  'gpt-4-turbo'
]

// Computed
const hasProfileChanges = computed(() => {
  return profileForm.value.fullName !== userName.value || 
         selectedAvatar.value !== userAvatar.value ||
         profileForm.value.timezone !== userTimezone.value
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
      profileForm.value.timezone,
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
  profileForm.value.timezone = userTimezone.value || 'UTC'
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
  
  // Reset form with current (fresh or cached) data
  resetProfile()
  
  // Load Immich config
  try {
    await immichStore.fetchConfig()
  } catch (error) {
    console.warn('Failed to load Immich config:', error)
  }
  
  // Load AI settings
  await loadAISettings()
  
  // Handle tab query parameter
  const tabParam = route.query.tab
  if (tabParam && ['profile', 'security', 'ai', 'immich'].includes(tabParam)) {
    activeTab.value = tabParam
  }
})

// AI Methods
const saveAISettings = async () => {
  aiSaveLoading.value = true
  
  try {
    // Prepare payload - only include openaiApiKey if user entered a new one
    const payload = {
      enabled: aiSettings.value.enabled,
      openaiApiUrl: aiSettings.value.openaiApiUrl,
      openaiModel: aiSettings.value.openaiModel
    }
    
    // Only include API key if user entered a new one
    if (aiSettings.value.openaiApiKey && aiSettings.value.openaiApiKey.trim()) {
      payload.openaiApiKey = aiSettings.value.openaiApiKey.trim()
    }
    
    await apiService.post('/ai/settings', payload)
    
    // Clear the API key field after successful save
    aiSettings.value.openaiApiKey = ''
    
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
  } finally {
    aiSaveLoading.value = false
  }
}

const testAIConnection = async () => {
  if (!aiSettings.value.openaiApiKey) {
    toast.add({
      severity: 'warn',
      summary: 'Warning',
      detail: 'Please enter your OpenAI API key first'
    })
    return
  }
  
  testConnectionLoading.value = true
  
  try {
    await apiService.post('/ai/test-openai', {
      apiKey: aiSettings.value.openaiApiKey,
      apiUrl: aiSettings.value.openaiApiUrl,
      model: aiSettings.value.openaiModel
    })
    
    toast.add({
      severity: 'success',
      summary: 'Success',
      detail: 'Connection test successful!'
    })
  } catch (error) {
    console.error('Error testing AI connection:', error)
    toast.add({
      severity: 'error',
      summary: 'Connection Failed',
      detail: getErrorMessage(error)
    })
  } finally {
    testConnectionLoading.value = false
  }
}

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
        openaiApiKeyConfigured: data.openaiApiKeyConfigured === true
      }
    }
  } catch (error) {
    console.warn('Failed to load AI settings:', error)
  }
}
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
.immich-card,
.ai-settings-card {
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
.immich-card :deep(.p-card-content),
.ai-settings-card :deep(.p-card-content) {
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

/* AI Assistant Section */
.ai-settings-card {
  background: var(--gp-surface-white);
  border: 1px solid var(--gp-border-light);
  box-shadow: var(--gp-shadow-light);
  width: 100%;
  box-sizing: border-box;
}

.ai-settings-form {
  width: 100%;
}

.ai-header {
  display: flex;
  align-items: center;
  gap: 1rem;
  margin-bottom: 2rem;
  padding: 1rem;
  background: var(--gp-surface-light);
  border-radius: var(--gp-radius-medium);
}

.ai-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 3rem;
  height: 3rem;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  border-radius: 50%;
  font-size: 1.25rem;
  flex-shrink: 0;
}

.ai-info {
  flex: 1;
}

.ai-title {
  font-size: 1.1rem;
  font-weight: 600;
  color: var(--gp-text-primary);
  margin: 0 0 0.25rem 0;
}

.ai-description {
  font-size: 0.9rem;
  color: var(--gp-text-secondary);
  margin: 0;
  line-height: 1.4;
}

.ai-form-grid {
  display: flex;
  flex-direction: column;
  gap: 1.5rem;
  margin-bottom: 2rem;
}

.form-group {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  width: 100%;
  max-width: 100%;
  box-sizing: border-box;
  overflow: hidden;
}

.provider-settings {
  display: flex;
  flex-direction: column;
  gap: 1rem;
  padding: 1rem;
  background: var(--gp-surface-light);
  border-radius: var(--gp-radius-medium);
  border-left: 4px solid var(--gp-primary);
  width: 100%;
  max-width: 100%;
  box-sizing: border-box;
  overflow: hidden;
}

.test-connection-section {
  display: flex;
  justify-content: flex-start;
  margin-bottom: 1rem;
}

/* Toggle Switch Styling */
:deep(.p-toggleswitch) {
  width: auto;
}

:deep(.p-toggleswitch .p-toggleswitch-slider) {
  background: var(--gp-border-medium);
  border-radius: 1rem;
  width: 3rem;
  height: 1.5rem;
  transition: background 0.3s;
}

:deep(.p-toggleswitch.p-toggleswitch-checked .p-toggleswitch-slider) {
  background: var(--gp-primary);
}

:deep(.p-toggleswitch .p-toggleswitch-slider:before) {
  background: white;
  width: 1.25rem;
  height: 1.25rem;
  border-radius: 50%;
  top: 0.125rem;
  left: 0.125rem;
  transition: transform 0.3s;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.2);
}

:deep(.p-toggleswitch.p-toggleswitch-checked .p-toggleswitch-slider:before) {
  transform: translateX(1.5rem);
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
  max-width: 100%;
  box-sizing: border-box;
}

:deep(.p-password-input) {
  border-radius: var(--gp-radius-medium);
  border: 1px solid var(--gp-border-medium);
  padding: 0.75rem 1rem;
  transition: all 0.2s ease;
  width: 100%;
  max-width: 100%;
  box-sizing: border-box;
  overflow: hidden;
  text-overflow: ellipsis;
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
  .immich-header,
  .ai-header {
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
  .immich-card,
  .ai-settings-card {
    width: 100% !important;
    max-width: 100% !important;
  }
}
</style>