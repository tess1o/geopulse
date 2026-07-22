<template>
  <Card class="memos-card">
    <template #content>
      <form class="memos-form" @submit.prevent="handleSubmit">
        <div class="memos-header">
          <div class="memos-icon">
            <i class="pi pi-file-edit"></i>
          </div>
          <div class="memos-info">
            <h3 class="memos-title">Memos Integration</h3>
            <p class="memos-description">
              Connect Memos to display timestamped notes on your timeline.
            </p>
          </div>
        </div>

        <div class="form-section">
          <div class="form-field" data-setting-id="memos-enabled">
            <label class="form-label">Enable Memos Integration</label>
            <ToggleSwitch v-model="form.enabled" :disabled="loading || saveLoading" />
            <small class="help-text">Turn on to fetch notes from your Memos server</small>
          </div>

          <div class="form-field" data-setting-id="memosServerUrl">
            <label for="memosServerUrl" class="form-label">Server URL</label>
            <InputText
              id="memosServerUrl"
              v-model="form.serverUrl"
              placeholder="https://memos.example.com"
              :invalid="!!errors.serverUrl"
              :disabled="!form.enabled || loading || saveLoading"
              class="w-full"
            />
            <small v-if="errors.serverUrl" class="error-message">{{ errors.serverUrl }}</small>
            <small v-else class="help-text">Enter the full URL to your Memos server</small>
          </div>

          <div class="form-field" data-setting-id="memosApiKey">
            <label for="memosApiKey" class="form-label">API Key</label>
            <Password
              id="memosApiKey"
              v-model="form.apiKey"
              :placeholder="apiKeyConfigured ? 'API key is set (enter new key to replace)' : 'Enter your Memos API key'"
              :feedback="false"
              toggleMask
              :invalid="!!errors.apiKey"
              :disabled="!form.enabled || loading || saveLoading"
              class="w-full"
            />
            <small v-if="errors.apiKey" class="error-message">{{ errors.apiKey }}</small>
            <small v-else-if="apiKeyConfigured && !form.apiKey" class="help-text">
              <i class="pi pi-check-circle" style="color: var(--gp-success);"></i>
              API key is configured. Leave empty to keep current key.
            </small>
            <small v-else class="help-text">Create an API token in Memos settings</small>
          </div>

          <div v-if="form.enabled" class="form-grid">
            <div class="form-field" data-setting-id="memosDefaultDestination">
              <label for="memosDefaultDestination" class="form-label">Default save destination</label>
              <Select
                id="memosDefaultDestination"
                v-model="form.defaultSaveDestination"
                :options="destinationOptions"
                optionLabel="label"
                optionValue="value"
                :disabled="loading || saveLoading"
              />
            </div>

            <div class="form-field" data-setting-id="memosDefaultVisibility">
              <label for="memosDefaultVisibility" class="form-label">Default Memos visibility</label>
              <Select
                id="memosDefaultVisibility"
                v-model="form.defaultVisibility"
                :options="visibilityOptions"
                optionLabel="label"
                optionValue="value"
                :disabled="loading || saveLoading"
              />
            </div>

            <div class="form-field" data-setting-id="memosSearchCacheEnabled">
              <label class="form-label">Enable Memos search cache</label>
              <ToggleSwitch v-model="form.searchCacheEnabled" :disabled="loading || saveLoading" />
              <small class="help-text">Reuse recent Memos searches for faster timeline note loading</small>
            </div>
          </div>

          <div v-if="form.enabled" class="form-field">
            <Button
              type="button"
              label="Test Connection"
              icon="pi pi-link"
              outlined
              :loading="testLoading"
              :disabled="!canTestConnection || loading || saveLoading"
              @click="handleTestConnection"
            />
          </div>

          <div v-if="testStatus" class="test-results">
            <Message :severity="testStatus === 'success' ? 'success' : 'error'" :closable="false">
              <strong>{{ testMessage }}</strong>
              <span v-if="testDetails"> {{ testDetails }}</span>
            </Message>
          </div>
        </div>

        <div class="form-actions">
          <Button type="button" label="Reset" outlined @click="handleReset" :disabled="loading || saveLoading" />
          <Button type="submit" label="Save Settings" :loading="saveLoading" :disabled="!hasChanges || loading" />
        </div>
      </form>
    </template>
  </Card>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import Card from 'primevue/card'
import ToggleSwitch from 'primevue/toggleswitch'
import InputText from 'primevue/inputtext'
import Password from 'primevue/password'
import Select from 'primevue/select'
import Button from 'primevue/button'
import Message from 'primevue/message'
import { useNotesStore } from '@/stores/notes'

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

const emit = defineEmits(['save', 'dirty-change'])
const notesStore = useNotesStore()
const saveLoading = ref(false)
const testLoading = ref(false)
const testStatus = ref(null)
const testMessage = ref('')
const testDetails = ref('')
const apiKeyConfigured = ref(false)
const errors = ref({})

const destinationOptions = [
  { label: 'GeoPulse', value: 'GEOPULSE' },
  { label: 'Memos', value: 'MEMOS' }
]

const visibilityOptions = [
  { label: 'Private', value: 'PRIVATE' },
  { label: 'Protected', value: 'PROTECTED' },
  { label: 'Public', value: 'PUBLIC' }
]

const form = ref({
  serverUrl: '',
  apiKey: '',
  enabled: false,
  defaultSaveDestination: 'GEOPULSE',
  defaultVisibility: 'PRIVATE',
  searchCacheEnabled: true
})

const hasChanges = computed(() => {
  if (!props.config) {
    return form.value.enabled ||
      (form.value.serverUrl?.trim() || '') !== '' ||
      (form.value.apiKey?.trim() || '') !== '' ||
      form.value.defaultSaveDestination !== 'GEOPULSE' ||
      form.value.defaultVisibility !== 'PRIVATE' ||
      form.value.searchCacheEnabled !== true
  }

  return form.value.serverUrl !== (props.config.serverUrl || '') ||
    form.value.enabled !== (props.config.enabled || false) ||
    (form.value.apiKey?.trim() || '') !== '' ||
    form.value.defaultSaveDestination !== (props.config.defaultSaveDestination || 'GEOPULSE') ||
    form.value.defaultVisibility !== (props.config.defaultVisibility || 'PRIVATE') ||
    form.value.searchCacheEnabled !== (props.config.searchCacheEnabled ?? true)
})

const canTestConnection = computed(() => (
  form.value.serverUrl?.trim() &&
  (form.value.apiKey?.trim() || apiKeyConfigured.value)
))

watch(hasChanges, (changed) => {
  emit('dirty-change', Boolean(changed))
})

function loadConfig() {
  form.value = {
    serverUrl: props.config?.serverUrl || '',
    apiKey: '',
    enabled: props.config?.enabled || false,
    defaultSaveDestination: props.config?.defaultSaveDestination || 'GEOPULSE',
    defaultVisibility: props.config?.defaultVisibility || 'PRIVATE',
    searchCacheEnabled: props.config?.searchCacheEnabled ?? true
  }
  apiKeyConfigured.value = !!props.config?.apiKey
  errors.value = {}
}

watch(() => props.config, () => {
  loadConfig()
}, { deep: true, immediate: true })

watch(() => [form.value.serverUrl, form.value.apiKey], () => {
  testStatus.value = null
  testMessage.value = ''
  testDetails.value = ''
})

const validate = () => {
  errors.value = {}
  if (form.value.enabled) {
    if (!form.value.serverUrl?.trim()) {
      errors.value.serverUrl = 'Server URL is required when integration is enabled'
    } else {
      try {
        new URL(form.value.serverUrl.trim())
      } catch {
        errors.value.serverUrl = 'Please enter a valid URL'
      }
    }

    if (!form.value.apiKey?.trim() && !apiKeyConfigured.value) {
      errors.value.apiKey = 'API key is required when integration is enabled'
    }
  }
  return Object.keys(errors.value).length === 0
}

const handleTestConnection = async () => {
  if (!validate()) return
  testLoading.value = true
  try {
    const result = await notesStore.testMemosConfig({
      serverUrl: form.value.serverUrl.trim(),
      apiKey: form.value.apiKey?.trim() || null
    })
    const payload = result
    testStatus.value = payload?.success ? 'success' : 'error'
    testMessage.value = payload?.message || (payload?.success ? 'Successfully connected to Memos server' : 'Connection failed')
    testDetails.value = payload?.details || ''
  } catch (error) {
    testStatus.value = 'error'
    testMessage.value = 'Connection test failed'
    testDetails.value = error.userMessage || error.message || ''
  } finally {
    testLoading.value = false
  }
}

const handleSubmit = async () => {
  if (!validate()) return
  saveLoading.value = true
  try {
    await emit('save', {
      serverUrl: form.value.serverUrl?.trim() || null,
      apiKey: form.value.apiKey?.trim() || (apiKeyConfigured.value ? 'KEEP_EXISTING' : null),
      enabled: form.value.enabled,
      defaultSaveDestination: form.value.defaultSaveDestination,
      defaultVisibility: form.value.defaultVisibility,
      searchCacheEnabled: form.value.searchCacheEnabled
    })
  } finally {
    saveLoading.value = false
  }
}

const handleReset = () => {
  loadConfig()
}
</script>

<style scoped>
.memos-card {
  width: 100%;
  box-sizing: border-box;
}

.memos-header {
  display: flex;
  align-items: center;
  gap: 1rem;
  margin-bottom: 2rem;
  padding: 1rem;
  background: var(--gp-surface-light);
  border-radius: var(--gp-radius-medium);
}

.memos-icon {
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

.memos-title {
  margin: 0;
  color: var(--gp-text-primary);
}

.memos-description {
  margin: 0.25rem 0 0;
  color: var(--gp-text-secondary);
}

.form-section,
.memos-form {
  display: flex;
  flex-direction: column;
  gap: 1.25rem;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
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

.help-text {
  color: var(--gp-text-secondary);
}

.error-message {
  color: var(--gp-danger);
}

.form-actions {
  display: flex;
  justify-content: flex-end;
  gap: 0.75rem;
  padding-top: 1rem;
}

@media (max-width: 768px) {
  .form-grid {
    grid-template-columns: 1fr;
  }
}
</style>
