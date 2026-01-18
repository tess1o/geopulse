<template>
  <Card class="immich-card">
    <template #content>
      <form @submit.prevent="handleSubmit" class="immich-form">
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
                v-model="form.enabled"
                onLabel="Enabled"
                offLabel="Disabled"
                :disabled="loading || saveLoading"
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
              v-model="form.serverUrl"
              placeholder="https://photos.example.com"
              :invalid="!!errors.serverUrl"
              :disabled="!form.enabled || loading || saveLoading"
              class="w-full"
            />
            <small v-if="errors.serverUrl" class="error-message">
              {{ errors.serverUrl }}
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
              v-model="form.apiKey"
              :placeholder="apiKeyConfigured ? 'API key is set (enter new key to replace)' : 'Enter your Immich API key'"
              :feedback="false"
              toggleMask
              :invalid="!!errors.apiKey"
              :disabled="!form.enabled || loading || saveLoading"
              class="w-full"
            />
            <small v-if="errors.apiKey" class="error-message">
              {{ errors.apiKey }}
            </small>
            <small v-else-if="apiKeyConfigured && !form.apiKey" class="help-text">
              <i class="pi pi-check-circle" style="color: var(--gp-success);"></i>
              API key is configured. Leave empty to keep current key.
            </small>
            <small v-else class="help-text">
              Create an API key in your Immich server settings
            </small>
          </div>

          <!-- Test Connection Button -->
          <div v-if="form.enabled" class="form-field">
            <Button
              type="button"
              label="Test Connection"
              icon="pi pi-link"
              outlined
              :loading="testLoading"
              :disabled="!canTestConnection || loading || saveLoading"
              @click="handleTestConnection"
              class="test-connection-btn"
            />
            <small v-if="!form.serverUrl?.trim()" class="help-text">
              Enter a server URL to test the connection
            </small>
            <small v-else-if="!form.apiKey?.trim() && !apiKeyConfigured" class="help-text">
              Enter an API key to test the connection
            </small>
            <small v-else class="help-text">
              <i class="pi pi-info-circle"></i>
              Click to verify connection to your Immich server
            </small>
          </div>

          <!-- Test Results -->
          <div v-if="testStatus" class="test-results">
            <div v-if="testStatus === 'success'" class="test-success">
              <div class="test-header">
                <i class="pi pi-check-circle"></i>
                <span class="test-title">{{ testMessage }}</span>
              </div>
              <small v-if="testDetails" class="test-details">{{ testDetails }}</small>
            </div>
            <div v-else-if="testStatus === 'error'" class="test-error">
              <div class="test-header">
                <i class="pi pi-times-circle"></i>
                <span class="test-title">{{ testMessage }}</span>
              </div>
              <small v-if="testDetails" class="test-details">{{ testDetails }}</small>
            </div>
          </div>
        </div>

        <!-- Action Buttons -->
        <div class="form-actions">
          <Button
            type="button"
            label="Reset"
            outlined
            @click="handleReset"
            :disabled="loading || saveLoading"
          />
          <Button
            type="submit"
            label="Save Settings"
            :loading="saveLoading"
            :disabled="!hasChanges || loading"
          />
        </div>
      </form>
    </template>
  </Card>
</template>

<script setup>
import { ref, computed, watch, onMounted } from 'vue'
import apiService from '@/utils/apiService'

// Props
const props = defineProps({
  config: {
    type: Object,
    default: null
  },
  loading: {
    type: Boolean,
    default: false
  }
})

// Emits
const emit = defineEmits(['save'])

// State
const saveLoading = ref(false)
const apiKeyConfigured = ref(false)
const testLoading = ref(false)
const testStatus = ref(null) // null, 'success', 'error'
const testMessage = ref('')
const testDetails = ref('')
const form = ref({
  serverUrl: '',
  apiKey: '',
  enabled: false
})
const errors = ref({})

// Computed
const hasChanges = computed(() => {
  // If no config exists, check if user has made any changes from defaults
  if (!props.config) {
    return form.value.enabled ||
           (form.value.serverUrl?.trim() || '') !== '' ||
           (form.value.apiKey?.trim() || '') !== ''
  }

  // Compare with existing config
  const serverUrlChanged = form.value.serverUrl !== (props.config.serverUrl || '')
  const enabledChanged = form.value.enabled !== (props.config.enabled || false)
  // Check if new API key is provided (since we don't show existing keys)
  const apiKeyChanged = (form.value.apiKey?.trim() || '') !== ''

  return serverUrlChanged || enabledChanged || apiKeyChanged
})

const canTestConnection = computed(() => {
  return form.value.serverUrl?.trim() &&
         (form.value.apiKey?.trim() || apiKeyConfigured.value)
})

// Methods
const handleTestConnection = async () => {
  if (!form.value.serverUrl?.trim()) {
    return
  }

  testLoading.value = true
  testStatus.value = null
  testMessage.value = ''
  testDetails.value = ''

  try {
    const requestData = {
      serverUrl: form.value.serverUrl.trim(),
      apiKey: form.value.apiKey?.trim() || null
    }

    const result = await apiService.post('/users/me/immich-config/test', requestData)

    // Debug: Log the response structure
    console.log('Test connection response:', result)
    console.log('result.data:', result?.data)
    console.log('result.data.success:', result?.data?.success, typeof result?.data?.success)

    // Check if the test was successful
    const testSuccessful = result?.data?.success === true

    if (testSuccessful) {
      testStatus.value = 'success'
      testMessage.value = result.data.message || 'Successfully connected to Immich server'
      testDetails.value = result.data.details || ''
    } else {
      testStatus.value = 'error'
      testMessage.value = result.data?.message || 'Failed to test connection'
      testDetails.value = result.data?.details || ''
    }
  } catch (error) {
    testStatus.value = 'error'
    testMessage.value = 'Connection test failed'
    testDetails.value = error.userMessage || error.message || 'An unexpected error occurred'
  } finally {
    testLoading.value = false
  }
}

const validate = () => {
  errors.value = {}

  if (form.value.enabled) {
    if (!form.value.serverUrl?.trim()) {
      errors.value.serverUrl = 'Server URL is required when integration is enabled'
    } else {
      try {
        new URL(form.value.serverUrl.trim())
      } catch {
        errors.value.serverUrl = 'Please enter a valid URL (e.g., https://photos.example.com)'
      }
    }

    // Only require API key if no existing key is set, or if user is trying to change it
    if (!form.value.apiKey?.trim() && !apiKeyConfigured.value) {
      errors.value.apiKey = 'API Key is required when integration is enabled'
    }
  }

  return Object.keys(errors.value).length === 0
}

const handleSubmit = async () => {
  if (!validate()) return

  saveLoading.value = true

  try {
    const configData = {
      serverUrl: form.value.serverUrl?.trim() || null,
      // Use new API key if provided, otherwise keep existing one
      apiKey: form.value.apiKey?.trim() || (apiKeyConfigured.value ? 'KEEP_EXISTING' : null),
      enabled: form.value.enabled
    }

    await emit('save', configData)
  } finally {
    saveLoading.value = false
  }
}

const handleReset = () => {
  form.value = {
    serverUrl: props.config?.serverUrl || '',
    // Never populate existing API key for security
    apiKey: '',
    enabled: props.config?.enabled || false
  }
  errors.value = {}
}

const loadConfig = () => {
  if (props.config) {
    form.value = {
      serverUrl: props.config.serverUrl || '',
      apiKey: '',
      enabled: props.config.enabled || false
    }
    apiKeyConfigured.value = !!props.config.apiKey
  }
}

// Watchers
watch(() => [form.value.serverUrl, form.value.apiKey], () => {
  // Clear test status when form fields change
  testStatus.value = null
  testMessage.value = ''
  testDetails.value = ''

  if (errors.value.serverUrl || errors.value.apiKey) {
    validate()
  }
})

watch(() => props.config, () => {
  loadConfig()
}, { deep: true, immediate: true })

// Initialize
onMounted(() => {
  loadConfig()
})
</script>

<style scoped>
.immich-card {
  background: var(--gp-surface-white);
  border: 1px solid var(--gp-border-light);
  box-shadow: var(--gp-shadow-light);
  width: 100%;
  box-sizing: border-box;
}

.immich-card :deep(.p-card-content) {
  width: 100%;
  box-sizing: border-box;
  padding: 1.5rem;
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

.test-connection-btn {
  width: 100%;
}

.test-results {
  padding: 0;
  border-radius: var(--gp-radius-medium);
  margin-top: 0.5rem;
}

.test-success,
.test-error {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  padding: 1rem;
  border-radius: var(--gp-radius-medium);
}

.test-success {
  background: rgba(34, 197, 94, 0.1);
  border: 1px solid rgba(34, 197, 94, 0.3);
}

.test-error {
  background: rgba(239, 68, 68, 0.1);
  border: 1px solid rgba(239, 68, 68, 0.3);
}

.test-header {
  display: flex;
  align-items: center;
  gap: 0.75rem;
}

.test-header i {
  font-size: 1.25rem;
  flex-shrink: 0;
}

.test-success .test-header i {
  color: var(--gp-success);
}

.test-error .test-header i {
  color: var(--gp-danger);
}

.test-title {
  font-weight: 600;
  font-size: 0.95rem;
  color: var(--gp-text-primary);
}

.test-details {
  font-size: 0.85rem;
  color: var(--gp-text-secondary);
  margin-left: 2rem;
  line-height: 1.4;
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
  .immich-card {
    border-radius: 0;
    border-left: none;
    border-right: none;
  }

  .immich-card :deep(.p-card-content) {
    padding: 1rem;
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
</style>
