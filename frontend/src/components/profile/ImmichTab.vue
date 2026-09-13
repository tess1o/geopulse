<template>
  <form class="integration-settings settings-tab" @submit.prevent="handleSubmit">
    <section class="settings-group" aria-labelledby="immich-availability-heading">
      <div class="settings-group-header">
        <h3 id="immich-availability-heading">Photo integration</h3>
        <p>Control whether Immich photos appear on your timeline.</p>
      </div>
      <div class="settings-panel">
        <SettingCard title="Enable Immich" description="Sync timeline photos from your Immich server." setting-id="immich-enabled">
          <template #control>
            <ToggleSwitch v-model="form.enabled" :disabled="readOnly || loading || saveLoading" aria-label="Enable Immich integration" />
          </template>
        </SettingCard>
      </div>
    </section>

    <section class="settings-group" aria-labelledby="immich-connection-heading">
      <div class="settings-group-header">
        <h3 id="immich-connection-heading">Connection</h3>
        <p>Provide the server address and credentials used to access Immich.</p>
      </div>
      <div class="settings-panel">
        <SettingCard title="Server URL" description="Enter the full address of your Immich server." setting-id="immichServerUrl">
          <template #control>
            <div class="field-control">
              <InputText id="immichServerUrl" v-model="form.serverUrl" placeholder="https://photos.example.com" :invalid="!!errors.serverUrl" :disabled="readOnly || !form.enabled || loading || saveLoading" class="w-full" aria-label="Immich server URL" />
              <small v-if="errors.serverUrl" class="error-message">{{ errors.serverUrl }}</small>
            </div>
          </template>
        </SettingCard>

        <SettingCard title="API key" description="Create an API key in your Immich server settings." setting-id="immichApiKey">
          <template #control>
            <div class="field-control">
              <Password
                id="immichApiKey"
                v-model="form.apiKey"
                :placeholder="apiKeyConfigured ? 'API key is set (enter new key to replace)' : 'Enter your Immich API key'"
                :feedback="false"
                toggleMask
                :invalid="!!errors.apiKey"
                :disabled="readOnly || !form.enabled || loading || saveLoading"
                class="w-full"
                aria-label="Immich API key"
              />
              <small v-if="errors.apiKey" class="error-message">{{ errors.apiKey }}</small>
              <small v-else-if="apiKeyConfigured && !form.apiKey" class="help-text configured-key"><i class="pi pi-check-circle"></i> API key is configured. Leave empty to keep it.</small>
            </div>
          </template>
        </SettingCard>
      </div>
    </section>

    <Message v-if="testStatus" :severity="testStatus === 'success' ? 'success' : 'error'" :closable="false" aria-live="polite">
      <strong>{{ testMessage }}</strong><span v-if="testDetails"> {{ testDetails }}</span>
    </Message>

    <div class="settings-actions is-sticky">
      <Button v-if="form.enabled" type="button" label="Test Connection" icon="pi pi-link" outlined :loading="testLoading" :disabled="readOnly || !canTestConnection || loading || saveLoading" @click="handleTestConnection" />
      <Button type="button" label="Reset" outlined @click="handleReset" :disabled="readOnly || loading || saveLoading" />
      <Button type="submit" label="Save Settings" :loading="saveLoading" :disabled="readOnly || !hasChanges || loading" />
    </div>
  </form>
</template>

<script setup>
import { ref, computed, watch, onMounted } from 'vue'
import Message from 'primevue/message'
import apiService from '@/utils/apiService'
import SettingCard from '@/components/ui/forms/SettingCard.vue'

const props = defineProps({
  readOnly: { type: Boolean, default: false },
  config: { type: Object, default: null },
  loading: { type: Boolean, default: false }
})

const emit = defineEmits(['save', 'dirty-change'])
const saveLoading = ref(false)
const apiKeyConfigured = ref(false)
const testLoading = ref(false)
const testStatus = ref(null)
const testMessage = ref('')
const testDetails = ref('')
const form = ref({ serverUrl: '', apiKey: '', enabled: false })
const errors = ref({})

const hasChanges = computed(() => {
  if (!props.config) {
    return form.value.enabled || (form.value.serverUrl?.trim() || '') !== '' || (form.value.apiKey?.trim() || '') !== ''
  }
  return form.value.serverUrl !== (props.config.serverUrl || '') ||
    form.value.enabled !== (props.config.enabled || false) ||
    (form.value.apiKey?.trim() || '') !== ''
})

watch(hasChanges, (changed) => emit('dirty-change', Boolean(changed)))

const canTestConnection = computed(() => form.value.serverUrl?.trim() && (form.value.apiKey?.trim() || apiKeyConfigured.value))

const handleTestConnection = async () => {
  if (props.readOnly || !form.value.serverUrl?.trim()) return
  testLoading.value = true
  testStatus.value = null
  testMessage.value = ''
  testDetails.value = ''
  try {
    const result = await apiService.post('/users/me/immich-config/test', {
      serverUrl: form.value.serverUrl.trim(),
      apiKey: form.value.apiKey?.trim() || null
    })
    const success = result?.data?.success === true
    testStatus.value = success ? 'success' : 'error'
    testMessage.value = result?.data?.message || (success ? 'Successfully connected to Immich server' : 'Failed to test connection')
    testDetails.value = result?.data?.details || ''
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
    if (!form.value.apiKey?.trim() && !apiKeyConfigured.value) errors.value.apiKey = 'API Key is required when integration is enabled'
  }
  return Object.keys(errors.value).length === 0
}

const handleSubmit = async () => {
  if (props.readOnly || !validate()) return
  saveLoading.value = true
  try {
    await emit('save', {
      serverUrl: form.value.serverUrl?.trim() || null,
      apiKey: form.value.apiKey?.trim() || (apiKeyConfigured.value ? 'KEEP_EXISTING' : null),
      enabled: form.value.enabled
    })
  } finally {
    saveLoading.value = false
  }
}

const handleReset = () => {
  if (props.readOnly) return
  form.value = { serverUrl: props.config?.serverUrl || '', apiKey: '', enabled: props.config?.enabled || false }
  errors.value = {}
}

const loadConfig = () => {
  if (!props.config) return
  form.value = { serverUrl: props.config.serverUrl || '', apiKey: '', enabled: props.config.enabled || false }
  apiKeyConfigured.value = !!props.config.apiKey
}

watch(() => [form.value.serverUrl, form.value.apiKey], () => {
  testStatus.value = null
  testMessage.value = ''
  testDetails.value = ''
  if (errors.value.serverUrl || errors.value.apiKey) validate()
})
watch(() => props.config, loadConfig, { deep: true, immediate: true })
onMounted(loadConfig)
</script>

<style scoped>
.integration-settings { width: 100%; }
.help-text { color: var(--gp-text-secondary); font-size: 0.8rem; }
.configured-key i { color: var(--gp-success); }
:deep(.p-password), :deep(.p-password-input) { width: 100%; min-width: 0; max-width: 100%; box-sizing: border-box; }
</style>
