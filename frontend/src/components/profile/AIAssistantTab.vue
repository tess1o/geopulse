<template>
  <Card class="ai-settings-card">
    <template #content>
      <form @submit.prevent="handleSubmit" class="ai-settings-form">
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
              v-model="form.enabled"
              class="w-full"
            />
            <small class="text-secondary">
              Enable or disable the AI Assistant functionality
            </small>
          </div>

          <!-- OpenAI Settings -->
          <div class="provider-settings">
            <div class="form-group">
              <label for="api-key-required" class="form-label">API Key Required</label>
              <ToggleSwitch
                id="api-key-required"
                v-model="form.apiKeyRequired"
                class="w-full"
              />
              <small class="text-secondary">
                Disable if your self-hosted LLM does not require an API key
              </small>
            </div>
            <div class="form-group">
              <label for="openai-api-key" class="form-label">OpenAI API Key</label>
              <Password
                id="openai-api-key"
                v-model="form.openaiApiKey"
                :placeholder="apiKeyConfigured ? 'API key is configured (enter new key to replace)' : 'Enter your OpenAI API key'"
                class="w-full"
                :feedback="false"
                toggleMask
                autocomplete="new-password"
                :inputProps="{
                  autocomplete: 'new-password',
                  'data-lpignore': 'true',
                  'data-form-type': 'other'
                }"
                :disabled="!form.apiKeyRequired"
              />
              <small v-if="apiKeyConfigured && !form.openaiApiKey && form.apiKeyRequired" class="text-secondary">
                <i class="pi pi-check-circle" style="color: var(--gp-success);"></i>
                API key is configured. Leave empty to keep current key.
              </small>
              <small v-else-if="!apiKeyConfigured && form.apiKeyRequired" class="text-secondary">
                Enter your OpenAI API key to enable the assistant
              </small>
            </div>
            <div class="form-group">
              <label for="openai-api-url" class="form-label">API Base URL</label>
              <InputText
                id="openai-api-url"
                v-model="form.openaiApiUrl"
                placeholder="https://api.openai.com/v1"
                class="w-full"
              />
              <small class="text-secondary">
                Use default OpenAI URL or enter a custom OpenAI-compatible API endpoint
              </small>
            </div>
            <div class="form-group">
              <label for="openai-model" class="form-label">Model</label>
              <div class="flex gap-2">
                <Dropdown
                  id="openai-model"
                  v-model="form.openaiModel"
                  :options="openaiModels"
                  placeholder="Select or enter model name"
                  class="w-full"
                  editable
                />
                <Button
                  type="button"
                  icon="pi pi-sync"
                  @click="fetchModels"
                  :loading="modelsLoading"
                  v-tooltip.bottom="'Fetch models from server'"
                />
              </div>
              <small class="text-secondary">
                Choose from common models or enter a custom model name
              </small>
            </div>
            <div class="form-group">
              <label for="custom-system-message" class="form-label">
                System Message
                <Button
                  type="button"
                  icon="pi pi-info-circle"
                  class="p-button-text p-button-sm"
                  v-tooltip.right="'Customize the AI assistant behavior. Clear the field to reset to default.'"
                  style="padding: 0; margin-left: 0.25rem; vertical-align: middle;"
                />
              </label>
              <Textarea
                id="custom-system-message"
                v-model="form.customSystemMessage"
                placeholder="Loading system message..."
                rows="8"
                class="w-full"
                autoResize
              />
              <small class="text-secondary">
                Edit the AI system message to customize behavior. Clear the field to reset to default.
              </small>
            </div>
          </div>
          <div v-if="testConnectionStatus" class="connection-status">
            <Message v-if="testConnectionStatus === 'success'" severity="success">Connection successful!</Message>
            <Message v-if="testConnectionStatus === 'error'" severity="error">Connection failed. Check URL and API key.</Message>
          </div>
        </div>

        <!-- Form Actions -->
        <div class="form-actions">
          <Button
            type="button"
            label="Test Connection"
            icon="pi pi-plug"
            :loading="testConnectionLoading"
            @click="testConnection"
            class="p-button-secondary"
          />
          <Button
            type="submit"
            label="Save AI Settings"
            icon="pi pi-save"
            :loading="loading"
            class="p-button-primary"
          />
        </div>
      </form>
    </template>
  </Card>
</template>

<script setup>
import { ref, watch, onMounted } from 'vue';
import Message from 'primevue/message';
import Textarea from 'primevue/textarea';

// Props
const props = defineProps({
  initialSettings: {
    type: Object,
    default: () => ({
      enabled: false,
      openaiApiKey: '',
      openaiApiUrl: 'https://api.openai.com/v1',
      openaiModel: 'gpt-3.5-turbo',
      openaiApiKeyConfigured: false,
      apiKeyRequired: true,
      customSystemMessage: null
    })
  }
})

// Emits
const emit = defineEmits(['save'])

// State
const loading = ref(false)
const modelsLoading = ref(false);
const testConnectionLoading = ref(false);
const testConnectionStatus = ref(null); // null, 'success', or 'error'
const apiKeyConfigured = ref(false)
const form = ref({
  enabled: false,
  openaiApiKey: '',
  openaiApiUrl: 'https://api.openai.com/v1',
  openaiModel: 'gpt-3.5-turbo',
  apiKeyRequired: true,
  customSystemMessage: null
})

import apiService from '@/utils/apiService';

const openaiModels = ref([
  'gpt-4o',
  'gpt-4o-mini',
  'gpt-3.5-turbo',
  'gpt-4-turbo'
]);

const fetchModels = async () => {
  modelsLoading.value = true;
  testConnectionStatus.value = null;
  try {
    const payload = {
      openaiApiUrl: form.value.openaiApiUrl,
      openaiApiKey: form.value.openaiApiKey,
      isApiKeyNeeded: form.value.apiKeyRequired
    };
    const models = await apiService.post('/ai/test-connection', payload);
    openaiModels.value = models;
    testConnectionStatus.value = 'success';
  } catch (error) {
    console.error('Failed to fetch models:', error);
    testConnectionStatus.value = 'error';
  } finally {
    modelsLoading.value = false;
  }
};

const testConnection = async () => {
  testConnectionLoading.value = true;
  await fetchModels();
  testConnectionLoading.value = false;
};

// Methods
const handleSubmit = async () => {
  loading.value = true

  try {
    const payload = {
      enabled: form.value.enabled,
      openaiApiUrl: form.value.openaiApiUrl,
      openaiModel: form.value.openaiModel,
      apiKeyRequired: form.value.apiKeyRequired,
      customSystemMessage: form.value.customSystemMessage && form.value.customSystemMessage.trim()
        ? form.value.customSystemMessage.trim()
        : null
    }

    // Only include API key if user entered a new one and it's required
    if (form.value.apiKeyRequired && form.value.openaiApiKey && form.value.openaiApiKey.trim()) {
      payload.openaiApiKey = form.value.openaiApiKey.trim()
    }

    await emit('save', payload)

    // Clear the API key field after successful save
    form.value.openaiApiKey = ''
  } finally {
    loading.value = false
  }
}

const loadSettings = async () => {
  form.value = {
    enabled: props.initialSettings.enabled === true,
    openaiApiKey: '', // Always empty since backend doesn't send actual key
    openaiApiUrl: props.initialSettings.openaiApiUrl || 'https://api.openai.com/v1',
    openaiModel: props.initialSettings.openaiModel || 'gpt-3.5-turbo',
    apiKeyRequired: props.initialSettings.apiKeyRequired !== false,
    customSystemMessage: props.initialSettings.customSystemMessage || null
  }
  apiKeyConfigured.value = props.initialSettings.openaiApiKeyConfigured === true

  // Load the effective system message (custom or default)
  if (!form.value.customSystemMessage) {
    try {
      const response = await apiService.get('/ai/default-system-message');
      form.value.customSystemMessage = response.message;
    } catch (error) {
      console.error('Failed to load default system message:', error);
      form.value.customSystemMessage = '';
    }
  }
}

// Initialize
onMounted(() => {
  loadSettings()
})

// Watch props changes
watch(() => props.initialSettings, () => {
  loadSettings()
}, { deep: true })
</script>

<style scoped>
.ai-settings-card {
  background: var(--gp-surface-white);
  border: 1px solid var(--gp-border-light);
  box-shadow: var(--gp-shadow-light);
  width: 100%;
  max-width: 1200px;
  box-sizing: border-box;
  margin: 0 auto;
}

.ai-settings-card :deep(.p-card-content) {
  width: 100%;
  box-sizing: border-box;
  padding: 2rem;
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
  box-sizing: border-box;
  overflow: hidden;
}

.provider-settings {
  display: flex;
  flex-direction: column;
  gap: 1rem;
  padding: 1.5rem;
  background: var(--gp-surface-light);
  border-radius: var(--gp-radius-medium);
  border-left: 4px solid var(--gp-primary);
  width: 100%;
  box-sizing: border-box;
  overflow: hidden;
}

.form-label {
  font-weight: 600;
  color: var(--gp-text-primary);
  font-size: 0.9rem;
}

.text-secondary {
  color: var(--gp-text-secondary);
  font-size: 0.8rem;
}

.form-actions {
  display: flex;
  justify-content: flex-end;
  gap: 1rem;
  margin-top: 1rem;
  padding-top: 1rem;
  border-top: 1px solid var(--gp-border-light);
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

/* Textarea Styling */
:deep(.p-inputtextarea) {
  border-radius: var(--gp-radius-medium);
  border: 1px solid var(--gp-border-medium);
  padding: 1rem;
  transition: all 0.2s ease;
  font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
  font-size: 0.875rem;
  line-height: 1.6;
  min-height: 300px;
  background: var(--surface-ground);
  color: var(--text-color);
}

:deep(.p-inputtextarea:focus) {
  border-color: var(--gp-primary);
  box-shadow: 0 0 0 3px rgba(26, 86, 219, 0.1);
  background: var(--surface-card);
}

/* Button Styling */
:deep(.p-button) {
  border-radius: var(--gp-radius-medium);
  font-weight: 600;
  padding: 0.75rem 1.5rem;
  transition: all 0.2s ease;
}

:deep(.p-button.p-button-primary) {
  background: var(--gp-primary);
  border-color: var(--gp-primary);
}

:deep(.p-button.p-button-primary:hover) {
  background: var(--gp-primary-hover);
  border-color: var(--gp-primary-hover);
  transform: translateY(-1px);
  box-shadow: var(--gp-shadow-medium);
}

/* Responsive Design */
@media (max-width: 1280px) {
  .ai-settings-card {
    max-width: 100%;
  }
}

@media (max-width: 768px) {
  .ai-header {
    flex-direction: column;
    text-align: center;
    gap: 1rem;
  }

  .form-actions {
    flex-direction: column;
  }

  .ai-settings-card :deep(.p-card-content) {
    padding: 1.5rem;
  }

  .provider-settings {
    padding: 1rem;
  }
}

@media (max-width: 480px) {
  .ai-settings-card {
    border-radius: 0;
    border-left: none;
    border-right: none;
  }

  .ai-settings-card :deep(.p-card-content) {
    padding: 1rem;
  }

  .provider-settings {
    border-radius: 0;
    padding: 1rem;
  }

  .form-actions .p-button {
    width: 100%;
    min-height: 48px;
  }

  .form-label {
    font-size: 0.9rem;
  }

  .text-secondary {
    font-size: 0.75rem;
  }
}
</style>
