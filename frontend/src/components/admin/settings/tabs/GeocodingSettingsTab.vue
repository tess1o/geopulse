<template>
  <div>
    <div v-if="hasUnsavedChanges" class="save-actions">
      <Message severity="warn" :closable="false" class="unsaved-message">
        You have unsaved changes
      </Message>
      <div class="buttons">
        <Button
          label="Discard Changes"
          severity="secondary"
          outlined
          @click="discardChanges"
          :disabled="isSaving"
        />
        <Button
          label="Save Changes"
          icon="pi pi-save"
          @click="saveAllChanges"
          :loading="isSaving"
        />
      </div>
    </div>

    <SettingSection title="Routing">
      <SettingItem
        v-for="setting in routingSettings"
        :key="setting.key"
        :setting="setting"
        @reset="handleReset(setting)"
      >
        <template #control="{ setting }">
          <Select
            v-if="setting.key === 'geocoding.primary-provider'"
            v-model="setting.currentValue"
            :options="providerOptions"
            optionLabel="label"
            optionValue="value"
            @change="markDirty"
            placeholder="Select primary provider"
            class="routing-select"
          />
          <Select
            v-else-if="setting.key === 'geocoding.fallback-provider'"
            v-model="setting.currentValue"
            :options="fallbackProviderOptions"
            optionLabel="label"
            optionValue="value"
            @change="markDirty"
            placeholder="Select fallback provider"
            class="routing-select"
          />
          <InputNumber
            v-else-if="setting.valueType === 'INTEGER'"
            v-model="setting.currentValue"
            @update:modelValue="markDirty"
            :min="0"
            :step="100"
            class="delay-input"
          />
        </template>
      </SettingItem>
    </SettingSection>

    <Message severity="info" :closable="false" class="provider-switch-note">
      Changing primary/fallback provider affects new lookups only. Existing cached geocoding records remain unchanged until reconciled.
    </Message>

    <SettingSection title="Providers">
      <div class="providers-workspace">
        <div class="providers-workspace-header">
          <div class="workspace-panel-heading">Provider list</div>
          <div class="workspace-panel-heading">Configure provider</div>
        </div>

        <div class="providers-workspace-body">
          <div class="provider-list-panel">
            <div class="provider-list" role="list">
              <button
                v-for="provider in providerDefinitions"
                :key="provider.id"
                type="button"
                class="provider-row"
                :class="{ selected: selectedProviderId === provider.id }"
                @click="selectProvider(provider.id)"
              >
                <div class="provider-row-main">
                  <span class="provider-name">{{ provider.label }}</span>
                  <span class="provider-chips">
                    <Tag v-if="isPrimaryProvider(provider)" value="Primary" severity="info" />
                    <Tag v-if="isFallbackProvider(provider)" value="Fallback" severity="warning" />
                    <Tag
                      v-if="provider.requiresCredential"
                      :value="providerCredentialAvailable(provider) ? 'Saved' : 'Credential missing'"
                      :severity="providerCredentialAvailable(provider) ? 'success' : 'danger'"
                    />
                  </span>
                </div>

                <div class="provider-row-actions" @click.stop>
                  <InputSwitch
                    v-if="getSetting(provider.enabledKey)"
                    v-model="getSetting(provider.enabledKey).currentValue"
                    @change="handleProviderEnabledChange(provider)"
                  />
                  <Tag
                    v-if="selectedProviderId === provider.id"
                    value="Selected"
                    severity="info"
                  />
                  <Button
                    v-else
                    label="Configure"
                    size="small"
                    severity="secondary"
                    text
                    @click="selectProvider(provider.id)"
                  />
                </div>
              </button>
            </div>
          </div>

          <div v-if="selectedProvider" class="provider-details">
            <div class="provider-details-header">
              <div>
                <h4>{{ selectedProvider.label }} settings</h4>
                <div class="provider-details-chips">
                  <Tag v-if="isPrimaryProvider(selectedProvider)" value="Primary" severity="info" />
                  <Tag v-if="isFallbackProvider(selectedProvider)" value="Fallback" severity="warning" />
                </div>
              </div>
              <InputSwitch
                v-if="getSetting(selectedProvider.enabledKey)"
                v-model="getSetting(selectedProvider.enabledKey).currentValue"
                @change="handleProviderEnabledChange(selectedProvider)"
              />
            </div>

            <Message
              v-if="isProviderEnabled(selectedProvider) && selectedProvider.requiresCredential && !providerCredentialAvailable(selectedProvider)"
              severity="warn"
              :closable="false"
              class="provider-warning"
            >
              {{ selectedProvider.label }} requires {{ selectedProvider.credentialLabel.toLowerCase() }} before it can be saved as enabled.
            </Message>

            <div v-if="selectedProvider.requiresCredential && selectedProviderCredentialSetting" class="detail-row credential-detail-row">
              <div class="detail-label">
                <label>{{ selectedProvider.credentialLabel }}</label>
                <small class="text-muted">Encrypted credential used by {{ selectedProvider.label }}.</small>
              </div>
              <div class="detail-control">
                <div class="credential-control">
                  <div class="credential-actions">
                    <span
                      class="credential-state"
                      :class="{ missing: !credentialStored(selectedProviderCredentialSetting) && !credentialDraftPresent(selectedProviderCredentialSetting.key) }"
                    >
                      {{ credentialStateText(selectedProviderCredentialSetting) }}
                    </span>
                    <Button
                      :label="credentialStored(selectedProviderCredentialSetting) ? 'Replace' : 'Set'"
                      icon="pi pi-key"
                      size="small"
                      severity="secondary"
                      outlined
                      @click="startCredentialEdit(selectedProviderCredentialSetting)"
                    />
                    <Button
                      v-if="credentialStored(selectedProviderCredentialSetting) || credentialDraftPresent(selectedProviderCredentialSetting.key)"
                      label="Clear"
                      icon="pi pi-times"
                      size="small"
                      severity="danger"
                      text
                      @click="clearCredential(selectedProviderCredentialSetting)"
                    />
                  </div>

                  <div v-if="credentialEditModes[selectedProviderCredentialSetting.key]" class="credential-edit">
                    <Password
                      v-model="credentialDrafts[selectedProviderCredentialSetting.key]"
                      :feedback="false"
                      toggleMask
                      autocomplete="new-password"
                      :placeholder="`Enter ${selectedProvider.credentialLabel.toLowerCase()}`"
                      :inputProps="credentialInputProps(selectedProviderCredentialSetting)"
                      @input="markDirty"
                      class="credential-input"
                    />
                    <Button
                      label="Cancel"
                      severity="secondary"
                      text
                      size="small"
                      @click="cancelCredentialEdit(selectedProviderCredentialSetting)"
                    />
                  </div>
                </div>
              </div>
            </div>

            <div class="provider-settings">
              <div
                v-for="setting in selectedProviderSettings"
                :key="setting.key"
                class="detail-row"
                :data-setting-id="setting.key"
              >
                <div class="detail-label">
                  <label>{{ setting.label }}</label>
                  <small class="text-muted">{{ setting.description }}</small>
                </div>

                <div class="detail-control">
                  <InputSwitch
                    v-if="setting.valueType === 'BOOLEAN'"
                    v-model="setting.currentValue"
                    @change="markDirty"
                  />

                  <InputNumber
                    v-else-if="setting.valueType === 'INTEGER'"
                    v-model="setting.currentValue"
                    @update:modelValue="markDirty"
                    :min="0"
                    :step="100"
                    class="delay-input"
                  />

                  <InputText
                    v-else
                    v-model="setting.currentValue"
                    @input="markDirty"
                    :placeholder="getPlaceholder(setting)"
                    class="setting-text-input"
                  />

                  <div class="detail-status">
                    <Tag v-if="setting.readOnly" severity="info" value="Read-only" />
                    <Tag v-else-if="setting.isDefault" severity="secondary" value="Default" />
                    <Button
                      v-else
                      label="Reset"
                      icon="pi pi-refresh"
                      text
                      size="small"
                      @click="handleReset(setting)"
                    />
                  </div>
                </div>
              </div>

              <div v-if="selectedProviderSettings.length === 0" class="empty-provider-settings">
                No additional settings for this provider.
              </div>
            </div>
          </div>
        </div>
      </div>
    </SettingSection>
  </div>
</template>

<script setup>
import { ref, onMounted, computed } from 'vue'
import { useToast } from 'primevue/usetoast'
import InputSwitch from 'primevue/inputswitch'
import InputNumber from 'primevue/inputnumber'
import InputText from 'primevue/inputtext'
import Password from 'primevue/password'
import Select from 'primevue/select'
import Button from 'primevue/button'
import Message from 'primevue/message'
import Tag from 'primevue/tag'
import SettingSection from '../SettingSection.vue'
import SettingItem from '../SettingItem.vue'
import { useAdminSettings } from '@/composables/useAdminSettings'
import { GEOCODING_PROVIDER_OPTIONS } from '@/constants/adminSettingsMetadata'
import { getPlaceholder as getPlaceholderHelper, parseSettingValue } from '@/utils/settingHelpers'
import apiService from '@/utils/apiService'

const { loadSettings } = useAdminSettings()
const toast = useToast()
const geocodingSettings = ref([])
const ALLOWED_PHOTON_LANGUAGES = ['de', 'pl', 'el', 'en', 'es', 'fa', 'fr', 'it', 'ja', 'ko']
const ALLOWED_PHOTON_LANGUAGE_SET = new Set(ALLOWED_PHOTON_LANGUAGES)
const PHOTON_LANGUAGE_EXAMPLES = ALLOWED_PHOTON_LANGUAGES.join(', ')

const hasUnsavedChanges = ref(false)
const originalSettings = ref([])
const isSaving = ref(false)
const selectedProviderId = ref(null)
const credentialDrafts = ref({})
const credentialEditModes = ref({})
const credentialCleared = ref({})

const providerDefinitions = [
  {
    id: 'nominatim',
    label: 'Nominatim',
    enabledKey: 'geocoding.nominatim.enabled',
    requiresCredential: false,
    settingsKeys: [
      'geocoding.nominatim.url',
      'geocoding.nominatim.language',
      'geocoding.nominatim.public-host-forward-search-enabled'
    ]
  },
  {
    id: 'photon',
    label: 'Photon',
    enabledKey: 'geocoding.photon.enabled',
    requiresCredential: false,
    settingsKeys: [
      'geocoding.photon.url',
      'geocoding.photon.language'
    ]
  },
  {
    id: 'googlemaps',
    label: 'Google Maps',
    enabledKey: 'geocoding.googlemaps.enabled',
    credentialKey: 'geocoding.googlemaps.api-key',
    credentialLabel: 'API Key',
    requiresCredential: true,
    settingsKeys: [
      'geocoding.googlemaps.language'
    ]
  },
  {
    id: 'mapbox',
    label: 'Mapbox',
    enabledKey: 'geocoding.mapbox.enabled',
    credentialKey: 'geocoding.mapbox.access-token',
    credentialLabel: 'Access Token',
    requiresCredential: true,
    settingsKeys: []
  },
  {
    id: 'geoapify',
    label: 'Geoapify',
    enabledKey: 'geocoding.geoapify.enabled',
    credentialKey: 'geocoding.geoapify.api-key',
    credentialLabel: 'API Key',
    requiresCredential: true,
    settingsKeys: [
      'geocoding.geoapify.language',
      'geocoding.geoapify.delay-ms'
    ]
  },
  {
    id: 'chibigeo',
    label: 'ChibiGeo',
    enabledKey: 'geocoding.chibigeo.enabled',
    credentialKey: 'geocoding.chibigeo.api-key',
    credentialLabel: 'API Key',
    requiresCredential: true,
    settingsKeys: [
      'geocoding.chibigeo.url',
      'geocoding.chibigeo.language',
      'geocoding.chibigeo.delay-ms'
    ]
  }
]

const routingKeys = ['geocoding.primary-provider', 'geocoding.fallback-provider', 'geocoding.delay-ms']

const routingSettings = computed(() =>
  routingKeys
    .map(key => getSetting(key))
    .filter(Boolean)
)

const enabledProviders = computed(() =>
  providerDefinitions
    .filter(provider => isProviderEnabled(provider))
    .map(provider => provider.id)
)

const providerOptions = computed(() =>
  GEOCODING_PROVIDER_OPTIONS.filter(opt => enabledProviders.value.includes(opt.value))
)

const fallbackProviderOptions = computed(() => [
  { label: 'None', value: '' },
  ...GEOCODING_PROVIDER_OPTIONS.filter(opt => enabledProviders.value.includes(opt.value))
])

const selectedProvider = computed(() =>
  providerDefinitions.find(provider => provider.id === selectedProviderId.value) || providerDefinitions[0]
)

const selectedProviderCredentialSetting = computed(() => {
  if (!selectedProvider.value?.credentialKey) {
    return null
  }
  return getSetting(selectedProvider.value.credentialKey)
})

const selectedProviderSettings = computed(() =>
  (selectedProvider.value?.settingsKeys || [])
    .map(key => getSetting(key))
    .filter(Boolean)
)

const getSetting = (key) => geocodingSettings.value.find(setting => setting.key === key)

const getOriginalSetting = (key) => originalSettings.value.find(setting => setting.key === key)

const getSettingValue = (key) => getSetting(key)?.currentValue

const isProviderEnabled = (provider) => getSetting(provider.enabledKey)?.currentValue === true

const isPrimaryProvider = (provider) => getSettingValue('geocoding.primary-provider') === provider.id

const isFallbackProvider = (provider) => getSettingValue('geocoding.fallback-provider') === provider.id

const getPlaceholder = (setting) => getPlaceholderHelper(setting)

const credentialStored = (setting) =>
  !credentialCleared.value[setting.key] &&
  setting.currentValue != null &&
  String(setting.currentValue).trim() !== ''

const credentialDraftPresent = (key) =>
  credentialDrafts.value[key] != null &&
  String(credentialDrafts.value[key]).trim() !== ''

const providerCredentialAvailable = (provider) => {
  if (!provider.requiresCredential) {
    return true
  }
  const setting = getSetting(provider.credentialKey)
  return !!setting && (credentialStored(setting) || credentialDraftPresent(provider.credentialKey))
}

const credentialStateText = (setting) => {
  if (credentialDraftPresent(setting.key)) {
    return 'New value ready to save'
  }
  if (credentialCleared.value[setting.key]) {
    return 'Will be cleared on save'
  }
  if (credentialStored(setting)) {
    return 'Saved'
  }
  return 'Not set'
}

const credentialInputProps = (setting) => ({
  autocomplete: 'new-password',
  name: `${setting.key.replace(/[^a-zA-Z0-9]+/g, '_')}_credential`,
  'data-lpignore': 'true',
  'data-1p-ignore': 'true',
  'data-form-type': 'other',
  autocapitalize: 'off',
  spellcheck: 'false'
})

const markDirty = () => {
  hasUnsavedChanges.value = true
}

const resolveDefaultSelectedProviderId = () => {
  const primaryProvider = getSettingValue('geocoding.primary-provider')
  if (providerDefinitions.some(provider => provider.id === primaryProvider)) {
    return primaryProvider
  }

  return providerDefinitions.find(provider => isProviderEnabled(provider))?.id || 'nominatim'
}

const resetProviderUiState = () => {
  selectedProviderId.value = resolveDefaultSelectedProviderId()
}

const resetCredentialUiState = () => {
  credentialDrafts.value = {}
  credentialEditModes.value = {}
  credentialCleared.value = {}
}

const selectProvider = (providerId) => {
  selectedProviderId.value = providerId
}

const handleProviderEnabledChange = (provider) => {
  markDirty()
  selectProvider(provider.id)
}

const startCredentialEdit = (setting) => {
  credentialDrafts.value = {
    ...credentialDrafts.value,
    [setting.key]: ''
  }
  credentialEditModes.value = {
    ...credentialEditModes.value,
    [setting.key]: true
  }
}

const cancelCredentialEdit = (setting) => {
  const nextDrafts = { ...credentialDrafts.value }
  const nextModes = { ...credentialEditModes.value }
  delete nextDrafts[setting.key]
  delete nextModes[setting.key]
  credentialDrafts.value = nextDrafts
  credentialEditModes.value = nextModes
}

const clearCredential = (setting) => {
  const nextDrafts = { ...credentialDrafts.value }
  const nextModes = { ...credentialEditModes.value }
  delete nextDrafts[setting.key]
  delete nextModes[setting.key]
  credentialDrafts.value = nextDrafts
  credentialEditModes.value = nextModes
  credentialCleared.value = {
    ...credentialCleared.value,
    [setting.key]: true
  }
  setting.currentValue = ''
  markDirty()
}

const validatePhotonLanguage = (value) => {
  const raw = value == null ? '' : String(value)
  const trimmed = raw.trim()
  if (!trimmed) {
    return null
  }

  const normalized = trimmed.toLowerCase()
  if (ALLOWED_PHOTON_LANGUAGE_SET.has(normalized)) {
    return null
  }

  const prefixMatch = normalized.match(/^([a-z]{2,3})[-_].*$/)
  const suggestion = prefixMatch && ALLOWED_PHOTON_LANGUAGE_SET.has(prefixMatch[1]) ? prefixMatch[1] : null
  const suggestionPart = suggestion ? ` Try "${suggestion}".` : ''

  return `Invalid Photon language "${trimmed}". Use a simple language code (for example: ${PHOTON_LANGUAGE_EXAMPLES}) or leave empty for provider default.${suggestionPart}`
}

const reloadGeocodingSettings = async () => {
  geocodingSettings.value = await loadSettings('geocoding')
}

onMounted(async () => {
  await reloadGeocodingSettings()
  originalSettings.value = JSON.parse(JSON.stringify(geocodingSettings.value))
  resetProviderUiState()
})

const validateAllSettings = () => {
  if (enabledProviders.value.length === 0) {
    return 'At least one geocoding provider must be enabled'
  }

  for (const provider of providerDefinitions) {
    if (isProviderEnabled(provider) && provider.requiresCredential && !providerCredentialAvailable(provider)) {
      return `Cannot enable ${provider.label} without providing ${provider.credentialLabel.toLowerCase()}`
    }
  }

  const primaryProvider = getSettingValue('geocoding.primary-provider')
  const fallbackProvider = getSettingValue('geocoding.fallback-provider')

  if (primaryProvider && !enabledProviders.value.includes(primaryProvider)) {
    return `Primary provider "${primaryProvider}" is not enabled. Please enable it first or choose a different provider.`
  }

  if (fallbackProvider && fallbackProvider !== '') {
    if (!enabledProviders.value.includes(fallbackProvider)) {
      return `Fallback provider "${fallbackProvider}" is not enabled. Please enable it first or choose a different provider.`
    }
    if (fallbackProvider === primaryProvider) {
      return 'Fallback provider cannot be the same as primary provider'
    }
  }

  const photonLanguageError = validatePhotonLanguage(getSettingValue('geocoding.photon.language'))
  if (photonLanguageError) {
    return photonLanguageError
  }

  const chibiGeoLanguageError = validatePhotonLanguage(getSettingValue('geocoding.chibigeo.language'))
  if (chibiGeoLanguageError) {
    return chibiGeoLanguageError.replace('Photon', 'ChibiGeo')
  }

  return null
}

const encryptedSettingPayload = (setting) => {
  if (credentialDraftPresent(setting.key)) {
    return {
      key: setting.key,
      value: credentialDrafts.value[setting.key]
    }
  }

  if (credentialCleared.value[setting.key]) {
    const original = getOriginalSetting(setting.key)
    if (original?.currentValue) {
      return {
        key: setting.key,
        value: ''
      }
    }
  }

  return null
}

const buildChangedSettings = () => {
  const changed = []

  for (const setting of geocodingSettings.value) {
    if (setting.valueType === 'ENCRYPTED') {
      const encryptedPayload = encryptedSettingPayload(setting)
      if (encryptedPayload) {
        changed.push(encryptedPayload)
      }
      continue
    }

    const original = getOriginalSetting(setting.key)
    if (JSON.stringify(setting.currentValue) !== JSON.stringify(original?.currentValue)) {
      changed.push({
        key: setting.key,
        value: parseSettingValue(setting)
      })
    }
  }

  return changed
}

const saveAllChanges = async () => {
  const validationError = validateAllSettings()
  if (validationError) {
    toast.add({
      severity: 'error',
      summary: 'Validation Error',
      detail: validationError,
      life: 5000
    })
    return
  }

  isSaving.value = true
  try {
    const changedSettings = buildChangedSettings()

    if (changedSettings.length === 0) {
      hasUnsavedChanges.value = false
      return
    }

    const response = await apiService.post('/admin/settings/bulk', {
      settings: changedSettings
    })

    await reloadGeocodingSettings()
    originalSettings.value = JSON.parse(JSON.stringify(geocodingSettings.value))
    resetCredentialUiState()
    resetProviderUiState()
    hasUnsavedChanges.value = false

    toast.add({
      severity: 'success',
      summary: 'Settings Saved',
      detail: `Successfully saved ${response.updated} settings`,
      life: 3000
    })
  } catch (error) {
    console.error('Failed to save settings:', error)
    const errorDetail = error.response?.data?.message || error.message || 'Failed to save settings'
    const errorKey = error.response?.data?.key

    toast.add({
      severity: 'error',
      summary: 'Save Failed',
      detail: errorKey ? `${errorKey}: ${errorDetail}` : errorDetail,
      life: 5000
    })

    await reloadGeocodingSettings()
    originalSettings.value = JSON.parse(JSON.stringify(geocodingSettings.value))
    resetCredentialUiState()
    resetProviderUiState()
    hasUnsavedChanges.value = false
  } finally {
    isSaving.value = false
  }
}

const discardChanges = () => {
  geocodingSettings.value = JSON.parse(JSON.stringify(originalSettings.value))
  resetCredentialUiState()
  resetProviderUiState()
  hasUnsavedChanges.value = false

  toast.add({
    severity: 'info',
    summary: 'Changes Discarded',
    detail: 'All unsaved changes have been discarded',
    life: 3000
  })
}

const handleReset = (setting) => {
  if (setting.valueType === 'ENCRYPTED') {
    clearCredential(setting)
    return
  }

  setting.currentValue = setting.valueType === 'INTEGER'
    ? parseInt(setting.defaultValue)
    : setting.defaultValue
  markDirty()
}
</script>

<style scoped>
.save-actions {
  position: sticky;
  top: 0;
  z-index: 10;
  background: var(--surface-section);
  padding: 1rem;
  margin: -1rem -1rem 1rem -1rem;
  border-bottom: 1px solid var(--surface-border);
  display: flex;
  align-items: center;
  gap: 1rem;
  flex-wrap: wrap;
}

.save-actions .unsaved-message {
  margin: 1rem;
  flex: 0 0 auto;
  font-weight: bold;
}

.save-actions .unsaved-message :deep(.p-message-wrapper) {
  padding: 0.5rem 0.75rem;
}

.save-actions .buttons {
  display: flex;
  gap: 0.5rem;
  margin-left: auto;
}

.routing-select {
  width: 220px;
}

.delay-input {
  width: 160px;
}

.setting-text-input {
  width: 320px;
}

.provider-switch-note {
  margin-bottom: 1rem;
}

.providers-workspace {
  width: 100%;
  max-width: 1240px;
  border: 1px solid var(--surface-border);
  border-radius: var(--gp-radius-medium);
  background: var(--surface-card);
  overflow: hidden;
}

.providers-workspace-header,
.providers-workspace-body {
  display: grid;
  grid-template-columns: minmax(360px, 480px) minmax(420px, 760px);
}

.providers-workspace-header {
  border-bottom: 1px solid var(--surface-border);
  background: var(--surface-section);
}

.provider-list-panel {
  width: 100%;
  min-width: 0;
  padding: 0.75rem 0;
  border-right: 1px solid var(--surface-border);
}

.workspace-panel-heading {
  margin: 0;
  padding: 0.75rem 1rem;
  color: var(--text-color-secondary);
  font-size: 0.78rem;
  font-weight: 700;
  letter-spacing: 0.04em;
  text-transform: uppercase;
}

.provider-list {
  overflow: hidden;
  background: transparent;
}

.provider-row {
  width: 100%;
  min-height: 56px;
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: center;
  gap: 0.75rem;
  padding: 0.7rem 1rem;
  border: 0;
  border-bottom: 1px solid var(--surface-border);
  background: transparent;
  color: var(--text-color);
  text-align: left;
  cursor: pointer;
}

.provider-row:last-child {
  border-bottom: none;
}

.provider-row:hover,
.provider-row.selected {
  background: var(--surface-hover);
}

.provider-row.selected {
  background: var(--surface-hover);
  box-shadow: inset 3px 0 0 var(--gp-primary);
}

.provider-row-main {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
}

.provider-name {
  font-weight: 600;
  color: var(--gp-text-primary);
}

.provider-chips,
.provider-details-chips {
  display: flex;
  align-items: center;
  gap: 0.35rem;
  flex-wrap: wrap;
}

.provider-row-actions {
  display: flex;
  align-items: center;
  gap: 0.35rem;
}

.provider-details {
  width: 100%;
  max-width: none;
  background: var(--surface-card);
  padding: 1rem 1.25rem;
}

.provider-details-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 1rem;
  padding-bottom: 0.85rem;
  border-bottom: 1px solid var(--surface-border);
}

.provider-details-header h4 {
  margin: 0 0 0.45rem 0;
  font-size: 1rem;
  font-weight: 600;
  color: var(--gp-text-primary);
}

.provider-warning {
  margin: 1rem 0 0 0;
}

.detail-row {
  display: grid;
  grid-template-columns: minmax(180px, 240px) minmax(0, 1fr);
  gap: 1rem;
  align-items: start;
  padding: 0.9rem 0;
  border-bottom: 1px solid var(--surface-border);
}

.detail-row:last-child {
  border-bottom: none;
}

.credential-detail-row {
  margin-top: 0.15rem;
}

.detail-label label {
  display: block;
  font-weight: 500;
  margin-bottom: 0.25rem;
  color: var(--gp-text-primary);
}

.detail-label small {
  display: block;
  line-height: 1.35;
}

.detail-control {
  display: flex;
  align-items: flex-start;
  justify-content: flex-start;
  gap: 0.75rem;
  flex-wrap: wrap;
}

.detail-status {
  min-width: 76px;
}

.provider-settings {
  margin-top: 0.15rem;
}

.empty-provider-settings {
  color: var(--text-color-secondary);
  font-size: 0.9rem;
  padding: 0.8rem 0 0 0;
}

.credential-control {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 0.5rem;
}

.credential-actions,
.credential-edit {
  display: flex;
  align-items: center;
  justify-content: flex-start;
  gap: 0.5rem;
  flex-wrap: wrap;
}

.credential-state {
  color: var(--green-600);
  font-size: 0.875rem;
  font-weight: 600;
}

.credential-state.missing {
  color: var(--orange-500);
}

.credential-input {
  width: 300px;
}

@media (max-width: 768px) {
  .save-actions {
    flex-direction: column;
    align-items: stretch;
  }

  .save-actions .buttons {
    margin-left: 0;
    flex-direction: column;
  }

  .providers-workspace-header {
    display: none;
  }

  .providers-workspace-body {
    grid-template-columns: 1fr;
  }

  .provider-list-panel {
    border-right: none;
    border-bottom: 1px solid var(--surface-border);
  }

  .provider-list-panel::before,
  .provider-details::before {
    display: block;
    padding: 0 1rem 0.65rem 1rem;
    color: var(--text-color-secondary);
    font-size: 0.78rem;
    font-weight: 700;
    letter-spacing: 0.04em;
    text-transform: uppercase;
  }

  .provider-list-panel::before {
    content: 'Provider list';
  }

  .provider-details::before {
    content: 'Configure provider';
    padding-left: 0;
    padding-right: 0;
  }

  .provider-row {
    grid-template-columns: 1fr;
  }

  .provider-row-actions {
    justify-content: space-between;
  }

  .provider-details {
    max-width: none;
  }

  .provider-details-header,
  .detail-row {
    grid-template-columns: 1fr;
  }

  .provider-details-header {
    flex-direction: column;
    align-items: stretch;
  }

  .detail-control,
  .credential-control,
  .credential-actions,
  .credential-edit {
    width: 100%;
    align-items: stretch;
    justify-content: flex-start;
  }

  .routing-select,
  .delay-input,
  .setting-text-input,
  .credential-input {
    width: 100%;
  }
}
</style>
