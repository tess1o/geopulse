<template>
  <form class="integration-settings settings-tab" @submit.prevent="handleSubmit">
    <section class="settings-group" aria-labelledby="memos-availability-heading">
      <div class="settings-group-header">
        <h3 id="memos-availability-heading">{{ t('profile.connectedApps.memos.availability.heading') }}</h3>
        <p>{{ t('profile.connectedApps.memos.availability.description') }}</p>
      </div>
      <div class="settings-panel">
        <SettingCard :title="t('profile.connectedApps.memos.availability.enabled.title')" :description="t('profile.connectedApps.memos.availability.enabled.description')" setting-id="memos-enabled">
          <template #control>
            <ToggleSwitch v-model="form.enabled" :disabled="readOnly || loading || saveLoading" :aria-label="t('profile.connectedApps.memos.availability.enabled.ariaLabel')" />
          </template>
        </SettingCard>
      </div>
    </section>

    <section class="settings-group" aria-labelledby="memos-connection-heading">
      <div class="settings-group-header">
        <h3 id="memos-connection-heading">{{ t('profile.connectedApps.memos.connection.heading') }}</h3>
        <p>{{ t('profile.connectedApps.memos.connection.description') }}</p>
      </div>
      <div class="settings-panel">
        <SettingCard :title="t('profile.connectedApps.memos.connection.serverUrl.title')" :description="t('profile.connectedApps.memos.connection.serverUrl.description')" setting-id="memosServerUrl">
          <template #control>
            <div class="field-control">
              <InputText id="memosServerUrl" v-model="form.serverUrl" placeholder="https://memos.example.com" :invalid="!!errors.serverUrl" :disabled="readOnly || !form.enabled || loading || saveLoading" class="w-full" :aria-label="t('profile.connectedApps.memos.connection.serverUrl.ariaLabel')" />
              <small v-if="errors.serverUrl" class="error-message">{{ errors.serverUrl }}</small>
            </div>
          </template>
        </SettingCard>

        <SettingCard :title="t('profile.connectedApps.memos.connection.apiKey.title')" :description="t('profile.connectedApps.memos.connection.apiKey.description')" setting-id="memosApiKey">
          <template #control>
            <div class="field-control">
              <Password
                id="memosApiKey"
                v-model="form.apiKey"
                :placeholder="apiKeyConfigured ? t('profile.connectedApps.memos.connection.apiKey.placeholderConfigured') : t('profile.connectedApps.memos.connection.apiKey.placeholder')"
                :feedback="false"
                toggleMask
                :invalid="!!errors.apiKey"
                :disabled="readOnly || !form.enabled || loading || saveLoading"
                class="w-full"
                :aria-label="t('profile.connectedApps.memos.connection.apiKey.ariaLabel')"
              />
              <small v-if="errors.apiKey" class="error-message">{{ errors.apiKey }}</small>
              <small v-else-if="apiKeyConfigured && !form.apiKey" class="help-text configured-key"><i class="pi pi-check-circle"></i> {{ t('profile.connectedApps.memos.connection.apiKey.configuredNote') }}</small>
            </div>
          </template>
        </SettingCard>
      </div>
    </section>

    <section v-if="form.enabled" class="settings-group" aria-labelledby="memos-defaults-heading">
      <div class="settings-group-header">
        <h3 id="memos-defaults-heading">{{ t('profile.connectedApps.memos.defaults.heading') }}</h3>
        <p>{{ t('profile.connectedApps.memos.defaults.description') }}</p>
      </div>
      <div class="settings-panel">
        <SettingCard :title="t('profile.connectedApps.memos.defaults.destination.title')" :description="t('profile.connectedApps.memos.defaults.destination.description')" setting-id="memosDefaultDestination">
          <template #control>
            <Select id="memosDefaultDestination" v-model="form.defaultSaveDestination" :options="destinationOptions" optionLabel="label" optionValue="value" :disabled="readOnly || loading || saveLoading" class="w-full" :aria-label="t('profile.connectedApps.memos.defaults.destination.ariaLabel')" />
          </template>
        </SettingCard>
        <SettingCard :title="t('profile.connectedApps.memos.defaults.visibility.title')" :description="t('profile.connectedApps.memos.defaults.visibility.description')" setting-id="memosDefaultVisibility">
          <template #control>
            <Select id="memosDefaultVisibility" v-model="form.defaultVisibility" :options="visibilityOptions" optionLabel="label" optionValue="value" :disabled="readOnly || loading || saveLoading" class="w-full" :aria-label="t('profile.connectedApps.memos.defaults.visibility.ariaLabel')" />
          </template>
        </SettingCard>
      </div>
    </section>

    <section v-if="form.enabled" class="settings-group" aria-labelledby="memos-filtering-heading">
      <div class="settings-group-header">
        <h3 id="memos-filtering-heading">{{ t('profile.connectedApps.memos.filtering.heading') }}</h3>
        <p>{{ t('profile.connectedApps.memos.filtering.description') }}</p>
      </div>
      <div class="settings-panel">
        <SettingCard :title="t('profile.connectedApps.memos.filtering.searchCache.title')" :description="t('profile.connectedApps.memos.filtering.searchCache.description')" setting-id="memosSearchCacheEnabled">
          <template #control>
            <ToggleSwitch v-model="form.searchCacheEnabled" :disabled="readOnly || loading || saveLoading" :aria-label="t('profile.connectedApps.memos.filtering.searchCache.ariaLabel')" />
          </template>
        </SettingCard>

        <SettingCard class="tag-setting" :title="t('profile.connectedApps.memos.filtering.includeTags.title')" :description="t('profile.connectedApps.memos.filtering.includeTags.description')" setting-id="memosIncludeTags">
          <template #control>
            <AutoComplete
              v-model="form.includeTags"
              inputId="memosIncludeTags"
              multiple
              :typeahead="false"
              :suggestions="[]"
              :placeholder="t('profile.connectedApps.memos.filtering.includeTags.placeholder')"
              :disabled="readOnly || loading || saveLoading"
              class="w-full tag-input"
              @change="normalizeFormTags('includeTags')"
              @blur="commitPendingTag('includeTags', $event)"
            />
          </template>
        </SettingCard>

        <SettingCard class="tag-setting" :title="t('profile.connectedApps.memos.filtering.excludeTags.title')" :description="t('profile.connectedApps.memos.filtering.excludeTags.description')" setting-id="memosExcludeTags">
          <template #control>
            <AutoComplete
              v-model="form.excludeTags"
              inputId="memosExcludeTags"
              multiple
              :typeahead="false"
              :suggestions="[]"
              :placeholder="t('profile.connectedApps.memos.filtering.excludeTags.placeholder')"
              :disabled="readOnly || loading || saveLoading"
              class="w-full tag-input"
              @change="normalizeFormTags('excludeTags')"
              @blur="commitPendingTag('excludeTags', $event)"
            />
          </template>
        </SettingCard>
      </div>
    </section>

    <Message v-if="testStatus" :severity="testStatus === 'success' ? 'success' : 'error'" :closable="false" aria-live="polite">
      <strong>{{ testMessage }}</strong><span v-if="testDetails"> {{ testDetails }}</span>
    </Message>

    <div class="settings-actions is-sticky">
      <Button v-if="form.enabled" type="button" :label="t('profile.connectedApps.memos.testConnection')" icon="pi pi-link" outlined :loading="testLoading" :disabled="readOnly || !canTestConnection || loading || saveLoading" @click="handleTestConnection" />
      <Button type="button" :label="t('profile.connectedApps.memos.reset')" outlined @click="handleReset" :disabled="readOnly || loading || saveLoading" />
      <Button type="submit" :label="t('profile.connectedApps.memos.saveSettings')" :loading="saveLoading" :disabled="readOnly || !hasChanges || loading" />
    </div>
  </form>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import ToggleSwitch from 'primevue/toggleswitch'
import InputText from 'primevue/inputtext'
import Password from 'primevue/password'
import Select from 'primevue/select'
import AutoComplete from 'primevue/autocomplete'
import Button from 'primevue/button'
import Message from 'primevue/message'
import { useNotesStore } from '@/stores/notes'
import SettingCard from '@/components/ui/forms/SettingCard.vue'
import { formatApiErrorDetail } from '@/utils/apiErrorDetail'

const { t } = useI18n()
const props = defineProps({
  readOnly: { type: Boolean, default: false },
  config: { type: Object, default: null },
  loading: { type: Boolean, default: false }
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
// Labels only; the `value` half stays the untouched backend enum. PrimeVue's `optionLabel` reads a
// field, so the labels resolve inside a computed -- which keeps them reactive to a language change.
const destinationOptions = computed(() => [
  { label: t('profile.connectedApps.memos.defaults.destination.options.geopulse'), value: 'GEOPULSE' },
  { label: t('profile.connectedApps.memos.defaults.destination.options.memos'), value: 'MEMOS' }
])
const visibilityOptions = computed(() => [
  { label: t('profile.connectedApps.memos.defaults.visibility.options.private'), value: 'PRIVATE' },
  { label: t('profile.connectedApps.memos.defaults.visibility.options.protected'), value: 'PROTECTED' },
  { label: t('profile.connectedApps.memos.defaults.visibility.options.public'), value: 'PUBLIC' }
])
// Backend status code -> catalog key. The codes are the values the store returns and must stay
// verbatim; the sentences are resolved with `t()` where they are used, so they follow the locale.
const connectionMessageKeys = {
  CONNECTED: 'profile.connectedApps.memos.messages.connected',
  USER_NOT_FOUND: 'profile.connectedApps.memos.messages.userNotFound',
  API_KEY_REQUIRED: 'profile.connectedApps.memos.messages.apiKeyRequired',
  CONNECTION_FAILED: 'profile.connectedApps.memos.messages.connectionFailed'
}
const form = ref({ serverUrl: '', apiKey: '', enabled: false, defaultSaveDestination: 'GEOPULSE', defaultVisibility: 'PRIVATE', searchCacheEnabled: true, includeTags: [], excludeTags: [] })

const normalizeTagList = (tags) => {
  if (!Array.isArray(tags)) return []
  const seen = new Set()
  const normalizedTags = []
  tags.forEach((tag) => {
    if (tag == null) return
    let normalized = String(tag).trim()
    while (normalized.startsWith('#')) normalized = normalized.slice(1).trim()
    if (!normalized || seen.has(normalized)) return
    seen.add(normalized)
    normalizedTags.push(normalized)
  })
  return normalizedTags
}

const tagListsEqual = (left, right) => {
  const normalizedLeft = normalizeTagList(left)
  const normalizedRight = normalizeTagList(right)
  return normalizedLeft.length === normalizedRight.length && normalizedLeft.every((tag, index) => tag === normalizedRight[index])
}

const hasChanges = computed(() => {
  if (!props.config) {
    return form.value.enabled ||
      (form.value.serverUrl?.trim() || '') !== '' ||
      (form.value.apiKey?.trim() || '') !== '' ||
      form.value.defaultSaveDestination !== 'GEOPULSE' ||
      form.value.defaultVisibility !== 'PRIVATE' ||
      form.value.searchCacheEnabled !== true ||
      normalizeTagList(form.value.includeTags).length > 0 ||
      normalizeTagList(form.value.excludeTags).length > 0
  }
  return form.value.serverUrl !== (props.config.serverUrl || '') ||
    form.value.enabled !== (props.config.enabled || false) ||
    (form.value.apiKey?.trim() || '') !== '' ||
    form.value.defaultSaveDestination !== (props.config.defaultSaveDestination || 'GEOPULSE') ||
    form.value.defaultVisibility !== (props.config.defaultVisibility || 'PRIVATE') ||
    form.value.searchCacheEnabled !== (props.config.searchCacheEnabled ?? true) ||
    !tagListsEqual(form.value.includeTags, props.config.includeTags || []) ||
    !tagListsEqual(form.value.excludeTags, props.config.excludeTags || [])
})

const canTestConnection = computed(() => form.value.serverUrl?.trim() && (form.value.apiKey?.trim() || apiKeyConfigured.value))
watch(hasChanges, (changed) => emit('dirty-change', Boolean(changed)))

function loadConfig() {
  form.value = {
    serverUrl: props.config?.serverUrl || '',
    apiKey: '',
    enabled: props.config?.enabled || false,
    defaultSaveDestination: props.config?.defaultSaveDestination || 'GEOPULSE',
    defaultVisibility: props.config?.defaultVisibility || 'PRIVATE',
    searchCacheEnabled: props.config?.searchCacheEnabled ?? true,
    includeTags: normalizeTagList(props.config?.includeTags || []),
    excludeTags: normalizeTagList(props.config?.excludeTags || [])
  }
  apiKeyConfigured.value = !!props.config?.apiKey
  errors.value = {}
}

watch(() => props.config, loadConfig, { deep: true, immediate: true })
watch(() => [form.value.serverUrl, form.value.apiKey], () => {
  testStatus.value = null
  testMessage.value = ''
  testDetails.value = ''
})

const validate = () => {
  errors.value = {}
  if (form.value.enabled) {
    if (!form.value.serverUrl?.trim()) {
      errors.value.serverUrl = t('profile.connectedApps.memos.errors.serverUrlRequired')
    } else {
      try {
        new URL(form.value.serverUrl.trim())
      } catch {
        errors.value.serverUrl = t('profile.connectedApps.memos.errors.serverUrlInvalid')
      }
    }
    if (!form.value.apiKey?.trim() && !apiKeyConfigured.value) errors.value.apiKey = t('profile.connectedApps.memos.errors.apiKeyRequired')
  }
  return Object.keys(errors.value).length === 0
}

const normalizeFormTags = (field) => { form.value[field] = normalizeTagList(form.value[field]) }
const commitPendingTag = (field, event) => {
  const pendingTag = event?.target?.value?.trim()
  if (pendingTag) {
    form.value[field] = normalizeTagList([...(form.value[field] || []), pendingTag])
    event.target.value = ''
  } else {
    normalizeFormTags(field)
  }
}

const handleTestConnection = async () => {
  if (props.readOnly || !validate()) return
  testLoading.value = true
  try {
    const payload = await notesStore.testMemosConfig({ serverUrl: form.value.serverUrl.trim(), apiKey: form.value.apiKey?.trim() || null })
    testStatus.value = payload?.success ? 'success' : 'error'
    testMessage.value = t(connectionMessageKeys[payload?.status] || (payload?.success ? connectionMessageKeys.CONNECTED : connectionMessageKeys.CONNECTION_FAILED))
    testDetails.value = payload?.memoCount == null ? (payload?.details || '') : t('profile.connectedApps.memos.messages.memoCount', { count: payload.memoCount }, payload.memoCount)
  } catch (error) {
    testStatus.value = 'error'
    testMessage.value = t('profile.connectedApps.memos.messages.testError')
    testDetails.value = formatApiErrorDetail(error, '')
  } finally {
    testLoading.value = false
  }
}

const handleSubmit = async () => {
  if (props.readOnly || !validate()) return
  saveLoading.value = true
  try {
    await emit('save', {
      serverUrl: form.value.serverUrl?.trim() || null,
      apiKey: form.value.apiKey?.trim() || (apiKeyConfigured.value ? 'KEEP_EXISTING' : null),
      enabled: form.value.enabled,
      defaultSaveDestination: form.value.defaultSaveDestination,
      defaultVisibility: form.value.defaultVisibility,
      searchCacheEnabled: form.value.searchCacheEnabled,
      includeTags: normalizeTagList(form.value.includeTags),
      excludeTags: normalizeTagList(form.value.excludeTags)
    })
  } finally {
    saveLoading.value = false
  }
}

const handleReset = () => {
  if (props.readOnly) return
  loadConfig()
}
</script>

<style scoped>
.integration-settings { width: 100%; }
.help-text { color: var(--gp-text-secondary); font-size: 0.8rem; }
.configured-key i { color: var(--gp-success); }
:deep(.p-password), :deep(.p-password-input) { width: 100%; min-width: 0; max-width: 100%; box-sizing: border-box; }
.tag-input { width: 100%; }
.tag-input :deep(.p-autocomplete-input-multiple) { width: 100%; min-height: 2.75rem; background: var(--gp-surface-card); border-color: var(--gp-border-medium); color: var(--gp-text-primary); box-shadow: var(--gp-shadow-subtle); }
.tag-input :deep(.p-autocomplete-input-chip input), .tag-input :deep(.p-autocomplete-chip .p-chip-label) { color: var(--gp-text-primary); }
.tag-input :deep(.p-autocomplete-input-chip input::placeholder), .tag-input :deep(.p-autocomplete-chip .p-chip-remove-icon) { color: var(--gp-text-muted); }
.tag-input :deep(.p-autocomplete-chip) { background: var(--gp-surface-muted); border: 1px solid var(--gp-border); color: var(--gp-text-primary); }
.tag-setting :deep(.setting-layout) { grid-template-columns: 1fr; }
.tag-setting :deep(.setting-control) { width: 100%; justify-self: stretch; }
</style>
