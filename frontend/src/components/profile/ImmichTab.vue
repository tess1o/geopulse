<template>
  <form class="integration-settings settings-tab" @submit.prevent="handleSubmit">
    <section class="settings-group" aria-labelledby="immich-availability-heading">
      <div class="settings-group-header">
        <h3 id="immich-availability-heading">{{ t('profile.connectedApps.immich.availability.heading') }}</h3>
        <p>{{ t('profile.connectedApps.immich.availability.description') }}</p>
      </div>
      <div class="settings-panel">
        <SettingCard :title="t('profile.connectedApps.immich.availability.enabled.title')" :description="t('profile.connectedApps.immich.availability.enabled.description')" setting-id="immich-enabled">
          <template #control>
            <ToggleSwitch v-model="form.enabled" :disabled="readOnly || loading || saveLoading" :aria-label="t('profile.connectedApps.immich.availability.enabled.ariaLabel')" />
          </template>
        </SettingCard>
      </div>
    </section>

    <section class="settings-group" aria-labelledby="immich-connection-heading">
      <div class="settings-group-header">
        <h3 id="immich-connection-heading">{{ t('profile.connectedApps.immich.connection.heading') }}</h3>
        <p>{{ t('profile.connectedApps.immich.connection.description') }}</p>
      </div>
      <div class="settings-panel">
        <SettingCard :title="t('profile.connectedApps.immich.connection.serverUrl.title')" :description="t('profile.connectedApps.immich.connection.serverUrl.description')" setting-id="immichServerUrl">
          <template #control>
            <div class="field-control">
              <InputText id="immichServerUrl" v-model="form.serverUrl" placeholder="https://photos.example.com" :invalid="!!errors.serverUrl" :disabled="readOnly || !form.enabled || loading || saveLoading" class="w-full" :aria-label="t('profile.connectedApps.immich.connection.serverUrl.ariaLabel')" />
              <small v-if="errors.serverUrl" class="error-message">{{ errors.serverUrl }}</small>
            </div>
          </template>
        </SettingCard>

        <SettingCard :title="t('profile.connectedApps.immich.connection.apiKey.title')" :description="t('profile.connectedApps.immich.connection.apiKey.description')" setting-id="immichApiKey">
          <template #control>
            <div class="field-control">
              <Password
                id="immichApiKey"
                v-model="form.apiKey"
                :placeholder="apiKeyConfigured ? t('profile.connectedApps.immich.connection.apiKey.placeholderConfigured') : t('profile.connectedApps.immich.connection.apiKey.placeholder')"
                :feedback="false"
                toggleMask
                :invalid="!!errors.apiKey"
                :disabled="readOnly || !form.enabled || loading || saveLoading"
                class="w-full"
                :aria-label="t('profile.connectedApps.immich.connection.apiKey.ariaLabel')"
              />
              <small v-if="errors.apiKey" class="error-message">{{ errors.apiKey }}</small>
              <small v-else-if="apiKeyConfigured && !form.apiKey" class="help-text configured-key"><i class="pi pi-check-circle"></i> {{ t('profile.connectedApps.immich.connection.apiKey.configuredNote') }}</small>
            </div>
          </template>
        </SettingCard>
      </div>
    </section>

    <Message v-if="testStatus" :severity="testStatus === 'success' ? 'success' : 'error'" :closable="false" aria-live="polite">
      <strong>{{ testMessage }}</strong><span v-if="testDetails"> {{ testDetails }}</span>
    </Message>

    <div class="settings-actions is-sticky">
      <Button v-if="form.enabled" type="button" :label="t('profile.connectedApps.immich.testConnection')" icon="pi pi-link" outlined :loading="testLoading" :disabled="readOnly || !canTestConnection || loading || saveLoading" @click="handleTestConnection" />
      <Button type="button" :label="t('profile.connectedApps.immich.reset')" outlined @click="handleReset" :disabled="readOnly || loading || saveLoading" />
      <Button type="submit" :label="t('profile.connectedApps.immich.saveSettings')" :loading="saveLoading" :disabled="readOnly || !hasChanges || loading" />
    </div>
  </form>
</template>

<script setup>
import { ref, computed, watch, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import Message from 'primevue/message'
import SettingCard from '@/components/ui/forms/SettingCard.vue'
import { useImmichStore } from '@/stores/immich'
import { formatApiErrorDetail } from '@/utils/apiErrorDetail'

const { t } = useI18n()
const props = defineProps({
  readOnly: { type: Boolean, default: false },
  config: { type: Object, default: null },
  loading: { type: Boolean, default: false }
})

const emit = defineEmits(['save', 'dirty-change'])
const immichStore = useImmichStore()
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

// Backend status code -> catalog key. The codes are the values the store returns and must stay
// verbatim; the sentences are resolved with `t()` where they are used, so they follow the locale.
const connectionMessageKeys = {
  CONNECTED: 'profile.connectedApps.immich.messages.connected',
  USER_NOT_FOUND: 'profile.connectedApps.immich.messages.userNotFound',
  API_KEY_REQUIRED: 'profile.connectedApps.immich.messages.apiKeyRequired',
  AUTHENTICATION_FAILED: 'profile.connectedApps.immich.messages.authenticationFailed',
  SERVER_NOT_FOUND: 'profile.connectedApps.immich.messages.serverNotFound',
  CONNECTION_TIMEOUT: 'profile.connectedApps.immich.messages.connectionTimeout',
  CONNECTION_FAILED: 'profile.connectedApps.immich.messages.connectionFailed'
}

const handleTestConnection = async () => {
  if (props.readOnly || !form.value.serverUrl?.trim()) return
  testLoading.value = true
  testStatus.value = null
  testMessage.value = ''
  testDetails.value = ''
  try {
    const result = await immichStore.testConnection({
      serverUrl: form.value.serverUrl.trim(),
      apiKey: form.value.apiKey?.trim() || null
    })
    const success = result?.success === true
    testStatus.value = success ? 'success' : 'error'
    testMessage.value = t(connectionMessageKeys[result?.status] || (success ? connectionMessageKeys.CONNECTED : 'profile.connectedApps.immich.messages.testFailed'))
    testDetails.value = result?.totalAssets != null
      ? t('profile.connectedApps.immich.messages.assetsAvailable', { count: result.totalAssets }, result.totalAssets)
      : (result?.details || '')
  } catch (error) {
    testStatus.value = 'error'
    testMessage.value = t('profile.connectedApps.immich.messages.testError')
    testDetails.value = formatApiErrorDetail(error, t('profile.connectedApps.immich.messages.unexpected'))
  } finally {
    testLoading.value = false
  }
}

const validate = () => {
  errors.value = {}
  if (form.value.enabled) {
    if (!form.value.serverUrl?.trim()) {
      errors.value.serverUrl = t('profile.connectedApps.immich.errors.serverUrlRequired')
    } else {
      try {
        new URL(form.value.serverUrl.trim())
      } catch {
        errors.value.serverUrl = t('profile.connectedApps.immich.errors.serverUrlInvalid')
      }
    }
    if (!form.value.apiKey?.trim() && !apiKeyConfigured.value) errors.value.apiKey = t('profile.connectedApps.immich.errors.apiKeyRequired')
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
