<template>
  <form class="integration-settings settings-tab" @submit.prevent="handleSubmit">
    <section class="settings-group" aria-labelledby="ai-availability-heading">
      <div class="settings-group-header">
        <h3 id="ai-availability-heading">Assistant availability</h3>
        <p>Control whether AI chat is available in GeoPulse.</p>
      </div>
      <div class="settings-panel">
        <SettingCard title="Enable AI Assistant" description="Allow AI-powered chat and timeline assistance." setting-id="ai-enabled">
          <template #control>
            <ToggleSwitch id="ai-enabled" v-model="form.enabled" :disabled="readOnly" aria-label="Enable AI Assistant" />
          </template>
        </SettingCard>
      </div>
    </section>

    <section class="settings-group" aria-labelledby="ai-provider-heading">
      <div class="settings-group-header">
        <h3 id="ai-provider-heading">Provider</h3>
        <p>Configure the OpenAI or OpenAI-compatible service used by the assistant.</p>
      </div>
      <div class="settings-panel">
        <SettingCard title="API key required" description="Turn off only when the provider accepts unauthenticated requests." setting-id="api-key-required">
          <template #control>
            <ToggleSwitch id="api-key-required" v-model="form.apiKeyRequired" :disabled="readOnly" aria-label="API key required" />
          </template>
        </SettingCard>

        <SettingCard title="API key" description="Enter a new key only when adding or replacing credentials." setting-id="openai-api-key">
          <template #control>
            <div class="field-control">
              <Password
                id="openai-api-key"
                v-model="form.openaiApiKey"
                :placeholder="apiKeyConfigured ? 'API key is configured (enter new key to replace)' : 'Enter your OpenAI API key'"
                class="w-full"
                :feedback="false"
                toggleMask
                autocomplete="new-password"
                :inputProps="{ autocomplete: 'new-password', 'data-lpignore': 'true', 'data-form-type': 'other' }"
                :disabled="readOnly || !form.apiKeyRequired"
                aria-label="OpenAI API key"
              />
              <small v-if="apiKeyConfigured && !form.openaiApiKey && form.apiKeyRequired" class="help-text configured-key"><i class="pi pi-check-circle"></i> API key is configured. Leave empty to keep it.</small>
              <small v-else-if="!apiKeyConfigured && form.apiKeyRequired" class="help-text">Enter an API key to enable authenticated requests.</small>
            </div>
          </template>
        </SettingCard>

        <SettingCard title="API base URL" description="Use OpenAI’s endpoint or another compatible service." setting-id="openai-api-url">
          <template #control>
            <InputText id="openai-api-url" v-model="form.openaiApiUrl" placeholder="https://api.openai.com/v1" class="w-full" :disabled="readOnly" aria-label="API base URL" />
          </template>
        </SettingCard>

        <SettingCard title="Model" description="Choose a listed model or enter its identifier." setting-id="openai-model">
          <template #control>
            <div class="model-select-row">
              <Dropdown id="openai-model" v-model="form.openaiModel" :options="openaiModels" placeholder="Select or enter model name" class="w-full" editable :disabled="readOnly" aria-label="AI model" />
              <Button type="button" icon="pi pi-sync" aria-label="Refresh provider models" @click="fetchModels" :loading="modelsLoading" :disabled="readOnly" v-tooltip.bottom="'Fetch models from server'" />
            </div>
          </template>
        </SettingCard>
      </div>
    </section>

    <section class="settings-group" aria-labelledby="ai-behavior-heading">
      <div class="settings-group-header">
        <h3 id="ai-behavior-heading">Assistant behavior</h3>
        <p>Customize the instruction sent with every conversation.</p>
      </div>
      <div class="settings-panel behavior-panel">
        <SettingCard title="System message" description="Clear the message to restore the server default." details="The system message guides the assistant’s tone and behavior." setting-id="custom-system-message">
          <template #control>
            <Textarea id="custom-system-message" v-model="form.customSystemMessage" placeholder="Loading system message..." rows="8" class="w-full" autoResize :disabled="readOnly" aria-label="AI system message" />
          </template>
        </SettingCard>
      </div>
    </section>

    <Message v-if="testConnectionStatus" :severity="testConnectionStatus === 'success' ? 'success' : 'error'" :closable="false" aria-live="polite">
      {{ testConnectionStatus === 'success' ? 'Connection successful!' : 'Connection failed. Check URL and API key.' }}
    </Message>

    <div class="settings-actions is-sticky">
      <Button type="button" label="Test Connection" icon="pi pi-plug" :loading="testConnectionLoading" :disabled="readOnly" @click="testConnection" outlined />
      <Button type="submit" label="Save AI Settings" icon="pi pi-save" :loading="loading" :disabled="readOnly" />
    </div>
  </form>
</template>

<script setup>
import { computed, ref, watch, onMounted } from 'vue'
import Message from 'primevue/message'
import Textarea from 'primevue/textarea'
import SettingCard from '@/components/ui/forms/SettingCard.vue'
import { useAIStore } from '@/stores/ai'

const props = defineProps({
  readOnly: { type: Boolean, default: false },
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

const emit = defineEmits(['save', 'dirty-change'])
const aiStore = useAIStore()
const loading = ref(false)
const modelsLoading = ref(false)
const testConnectionLoading = ref(false)
const testConnectionStatus = ref(null)
const apiKeyConfigured = ref(false)
const form = ref({ enabled: false, openaiApiKey: '', openaiApiUrl: 'https://api.openai.com/v1', openaiModel: 'gpt-3.5-turbo', apiKeyRequired: true, customSystemMessage: null })
const savedFormSnapshot = ref(null)
const syncingSettings = ref(false)
const openaiModels = ref(['gpt-4o', 'gpt-4o-mini', 'gpt-3.5-turbo', 'gpt-4-turbo'])

const normalizeSettings = (settings = {}) => ({
  enabled: settings.enabled === true,
  openaiApiKey: settings.openaiApiKey || '',
  openaiApiUrl: settings.openaiApiUrl || 'https://api.openai.com/v1',
  openaiModel: settings.openaiModel || 'gpt-3.5-turbo',
  apiKeyRequired: settings.apiKeyRequired !== false,
  customSystemMessage: settings.customSystemMessage?.trim() || null
})

const hasChanges = computed(() => {
  if (syncingSettings.value || !savedFormSnapshot.value) return false
  const current = normalizeSettings(form.value)
  return Object.keys(current).some((key) => current[key] !== savedFormSnapshot.value[key])
})

const fetchModels = async () => {
  if (props.readOnly) return
  modelsLoading.value = true
  testConnectionStatus.value = null
  try {
    openaiModels.value = await aiStore.testConnection({
      openaiApiUrl: form.value.openaiApiUrl,
      openaiApiKey: form.value.openaiApiKey,
      isApiKeyNeeded: form.value.apiKeyRequired
    })
    testConnectionStatus.value = 'success'
  } catch (error) {
    console.error('Failed to fetch models:', error)
    testConnectionStatus.value = 'error'
  } finally {
    modelsLoading.value = false
  }
}

const testConnection = async () => {
  if (props.readOnly) return
  testConnectionLoading.value = true
  await fetchModels()
  testConnectionLoading.value = false
}

const handleSubmit = async () => {
  if (props.readOnly) return
  loading.value = true
  try {
    const payload = {
      enabled: form.value.enabled,
      openaiApiUrl: form.value.openaiApiUrl,
      openaiModel: form.value.openaiModel,
      apiKeyRequired: form.value.apiKeyRequired,
      customSystemMessage: form.value.customSystemMessage?.trim() || null
    }
    if (form.value.apiKeyRequired && form.value.openaiApiKey?.trim()) payload.openaiApiKey = form.value.openaiApiKey.trim()
    await emit('save', payload)
    form.value.openaiApiKey = ''
  } finally {
    loading.value = false
  }
}

const loadSettings = async () => {
  syncingSettings.value = true
  form.value = {
    enabled: props.initialSettings.enabled === true,
    openaiApiKey: '',
    openaiApiUrl: props.initialSettings.openaiApiUrl || 'https://api.openai.com/v1',
    openaiModel: props.initialSettings.openaiModel || 'gpt-3.5-turbo',
    apiKeyRequired: props.initialSettings.apiKeyRequired !== false,
    customSystemMessage: props.initialSettings.customSystemMessage || null
  }
  apiKeyConfigured.value = props.initialSettings.openaiApiKeyConfigured === true
  if (!form.value.customSystemMessage) {
    try {
      const response = await aiStore.fetchDefaultSystemMessage()
      form.value.customSystemMessage = response.message
    } catch (error) {
      console.error('Failed to load default system message:', error)
      form.value.customSystemMessage = ''
    }
  }
  savedFormSnapshot.value = normalizeSettings(form.value)
  syncingSettings.value = false
}

watch(hasChanges, (changed) => emit('dirty-change', Boolean(changed)))
onMounted(loadSettings)
watch(() => props.initialSettings, loadSettings, { deep: true })
</script>

<style scoped>
.integration-settings { width: 100%; }
.help-text { color: var(--gp-text-secondary); font-size: 0.8rem; }
.configured-key i { color: var(--gp-success); }
.model-select-row { display: flex; gap: var(--gp-spacing-sm); width: 100%; min-width: 0; }
:deep(.p-password), :deep(.p-password-input) { width: 100%; min-width: 0; max-width: 100%; box-sizing: border-box; }
.behavior-panel :deep(.setting-layout) { grid-template-columns: 1fr; }
.behavior-panel :deep(.setting-control) { width: 100%; justify-self: stretch; }
</style>
