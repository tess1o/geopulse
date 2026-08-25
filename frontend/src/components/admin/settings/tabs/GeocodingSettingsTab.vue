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
          :disabled="adminReadOnly"
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

    <SettingSection title="Custom Providers">
      <div class="custom-providers-layout">
        <div class="custom-provider-list">
          <div
            v-for="provider in customProviders"
            :key="provider.name"
            class="custom-provider-row"
          >
            <div>
              <div class="provider-name">{{ provider.displayName }}</div>
              <small class="text-muted">{{ provider.name }} &middot; {{ provider.type }} &middot; {{ provider.url }}</small>
              <div class="provider-chips">
                <Tag v-if="provider.enabled" value="Enabled" severity="success" />
                <Tag v-else value="Disabled" severity="secondary" />
                <Tag v-if="isPrimaryProviderName(provider.name)" value="Primary" severity="info" />
                <Tag v-if="isFallbackProviderName(provider.name)" value="Fallback" severity="warning" />
              </div>
            </div>
            <div class="custom-provider-actions">
              <Button label="Edit" size="small" severity="secondary" text @click="editCustomProvider(provider)" />
              <Button
                label="Delete"
                size="small"
                severity="danger"
                text
                :disabled="adminReadOnly || isCustomProviderDeleteBlocked(provider)"
                v-tooltip.bottom="customProviderDeleteBlockReason(provider) || 'Delete custom provider'"
                @click="deleteCustomProvider(provider)"
              />
            </div>
          </div>

          <div v-if="customProviders.length === 0" class="empty-provider-settings">
            No custom geocoding providers configured.
          </div>
        </div>

        <div class="custom-provider-form">
          <div class="provider-details-header">
            <div>
              <h4>{{ editingCustomProviderName ? 'Edit custom provider' : 'Add custom provider' }}</h4>
            </div>
            <Button
              v-if="editingCustomProviderName"
              label="New"
              icon="pi pi-plus"
              size="small"
              text
              class="new-custom-provider-button"
              @click="resetCustomProviderForm"
            />
          </div>

          <div class="detail-row">
            <div class="detail-label">
              <label>Name</label>
              <small class="text-muted">Stable routing key, lowercase with hyphens.</small>
            </div>
            <div class="custom-provider-control">
              <InputText
                v-model="customProviderForm.name"
                :disabled="!!editingCustomProviderName"
                class="setting-text-input"
                :class="{ 'p-invalid': !!customProviderFormErrors.name }"
                @input="handleCustomProviderNameInput"
              />
              <small v-if="customProviderFormErrors.name" class="field-error">{{ customProviderFormErrors.name }}</small>
            </div>
          </div>

          <div class="detail-row">
            <div class="detail-label">
              <label>Display Name</label>
              <small class="text-muted">Shown in provider lists and cached records.</small>
            </div>
            <div class="custom-provider-control">
              <InputText
                v-model="customProviderForm.displayName"
                class="setting-text-input"
                :class="{ 'p-invalid': !!customProviderFormErrors.displayName }"
                @input="clearCustomProviderFieldError('displayName')"
              />
              <small v-if="customProviderFormErrors.displayName" class="field-error">{{ customProviderFormErrors.displayName }}</small>
            </div>
          </div>

          <div class="detail-row">
            <div class="detail-label">
              <label>Type</label>
              <small class="text-muted">Response format to use for this endpoint.</small>
            </div>
            <div class="custom-provider-control">
              <Select
                v-model="customProviderForm.type"
                :options="customProviderTypeOptions"
                optionLabel="label"
                optionValue="value"
                class="routing-select"
                :class="{ 'p-invalid': !!customProviderFormErrors.type }"
                @update:modelValue="clearCustomProviderFieldError('type')"
              />
              <small v-if="customProviderFormErrors.type" class="field-error">{{ customProviderFormErrors.type }}</small>
            </div>
          </div>

          <div class="detail-row">
            <div class="detail-label">
              <label>URL</label>
              <small class="text-muted">Base URL, for example https://photon.komoot.io.</small>
            </div>
            <div class="custom-provider-control">
              <InputText
                v-model="customProviderForm.url"
                class="setting-text-input"
                :class="{ 'p-invalid': !!customProviderFormErrors.url }"
                @input="clearCustomProviderFieldError('url')"
              />
              <small v-if="customProviderFormErrors.url" class="field-error">{{ customProviderFormErrors.url }}</small>
            </div>
          </div>

          <div class="detail-row">
            <div class="detail-label">
              <label>Enabled</label>
              <small class="text-muted">Enabled providers can be selected for routing.</small>
            </div>
            <InputSwitch v-model="customProviderForm.enabled" />
          </div>

          <div class="detail-row">
            <div class="detail-label">
              <label>Language</label>
              <small class="text-muted">Optional language header/query value.</small>
            </div>
            <div class="custom-provider-control">
              <InputText
                v-model="customProviderForm.language"
                class="setting-text-input"
                :class="{ 'p-invalid': !!customProviderFormErrors.language }"
                @input="clearCustomProviderFieldError('language')"
              />
              <small v-if="customProviderFormErrors.language" class="field-error">{{ customProviderFormErrors.language }}</small>
            </div>
          </div>

          <div class="detail-row">
            <div class="detail-label">
              <label>Delay</label>
              <small class="text-muted">Optional request delay in milliseconds.</small>
            </div>
            <div class="custom-provider-control">
              <InputNumber
                v-model="customProviderForm.delayMs"
                :min="0"
                :step="100"
                class="delay-input"
                :class="{ 'p-invalid': !!customProviderFormErrors.delayMs }"
                @update:modelValue="clearCustomProviderFieldError('delayMs')"
              />
              <small v-if="customProviderFormErrors.delayMs" class="field-error">{{ customProviderFormErrors.delayMs }}</small>
            </div>
          </div>

          <div class="detail-row">
            <div class="detail-label">
              <label>Headers JSON</label>
              <small class="text-muted">Optional headers, encrypted at rest. Example: {"X-Api-Key":"secret"}</small>
            </div>
            <div class="custom-provider-control">
              <Textarea
                v-model="customHeadersText"
                rows="4"
                class="headers-input"
                :class="{ 'p-invalid': !!customProviderFormErrors.headers }"
                @input="clearCustomProviderFieldError('headers')"
              />
              <small v-if="customProviderFormErrors.headers" class="field-error">{{ customProviderFormErrors.headers }}</small>
            </div>
          </div>

          <div class="custom-provider-save-row">
            <Button
              :label="editingCustomProviderName ? 'Update Provider' : 'Create Provider'"
              icon="pi pi-save"
              :loading="isSavingCustomProvider"
              :disabled="adminReadOnly"
              @click="saveCustomProvider"
            />
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
import Textarea from 'primevue/textarea'
import Button from 'primevue/button'
import Message from 'primevue/message'
import Tag from 'primevue/tag'
import { storeToRefs } from 'pinia'
import SettingSection from '../SettingSection.vue'
import SettingItem from '../SettingItem.vue'
import { useAdminSettings } from '@/composables/useAdminSettings'
import { useAuthStore } from '@/stores/auth'
import { GEOCODING_PROVIDER_OPTIONS } from '@/constants/adminSettingsMetadata'
import { getPlaceholder as getPlaceholderHelper, parseSettingValue } from '@/utils/settingHelpers'
import apiService from '@/utils/apiService'
import adminService from '@/utils/adminService'
import { extractApiErrorDetail } from '@/utils/apiErrorDetail'
import { showDemoReadOnlyToast } from '@/utils/demoMode'

const { loadSettings } = useAdminSettings()
const toast = useToast()
const authStore = useAuthStore()
const { adminReadOnly } = storeToRefs(authStore)
const geocodingSettings = ref([])
const ALLOWED_PHOTON_LANGUAGES = ['de', 'pl', 'el', 'en', 'es', 'fa', 'fr', 'it', 'ja', 'ko']
const ALLOWED_PHOTON_LANGUAGE_SET = new Set(ALLOWED_PHOTON_LANGUAGES)
const PHOTON_LANGUAGE_EXAMPLES = ALLOWED_PHOTON_LANGUAGES.join(', ')
const CUSTOM_PROVIDER_NAME_PATTERN = /^[a-z0-9][a-z0-9-]*$/
const CUSTOM_PROVIDER_TYPES = new Set(['photon', 'nominatim'])

const hasUnsavedChanges = ref(false)
const originalSettings = ref([])
const isSaving = ref(false)
const selectedProviderId = ref(null)
const credentialDrafts = ref({})
const credentialEditModes = ref({})
const credentialCleared = ref({})
const customProviders = ref([])
const editingCustomProviderName = ref(null)
const isSavingCustomProvider = ref(false)
const customHeadersText = ref('')
const customProviderFormErrors = ref({})
const customProviderForm = ref({
  name: '',
  displayName: '',
  type: 'photon',
  url: '',
  enabled: true,
  language: '',
  delayMs: null
})

const customProviderTypeOptions = [
  { label: 'Photon compatible', value: 'photon' },
  { label: 'Nominatim compatible', value: 'nominatim' }
]

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
  [
    ...GEOCODING_PROVIDER_OPTIONS.filter(opt => enabledProviders.value.includes(opt.value)),
    ...customProviderOptions.value
  ]
)

const fallbackProviderOptions = computed(() => [
  { label: 'None', value: '' },
  ...providerOptions.value
])

const customProviderOptions = computed(() =>
  customProviders.value
    .filter(provider => provider.enabled)
    .map(provider => ({
      label: provider.displayName,
      value: provider.name
    }))
)

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

const isPrimaryProviderName = (providerName) => getSettingValue('geocoding.primary-provider') === providerName

const isFallbackProviderName = (providerName) => getSettingValue('geocoding.fallback-provider') === providerName

const customProviderDeleteBlockReason = (provider) => {
  if (!provider?.name) {
    return null
  }
  if (isPrimaryProviderName(provider.name)) {
    return 'Cannot delete a provider while it is selected as primary'
  }
  if (isFallbackProviderName(provider.name)) {
    return 'Cannot delete a provider while it is selected as fallback'
  }
  return null
}

const isCustomProviderDeleteBlocked = (provider) => !!customProviderDeleteBlockReason(provider)

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

const normalizeCustomProviderName = (value) =>
  String(value || '')
    .trim()
    .toLowerCase()
    .replace(/[\s_]+/g, '-')

const handleCustomProviderNameInput = () => {
  customProviderForm.value.name = normalizeCustomProviderName(customProviderForm.value.name)
  if (customProviderFormErrors.value.name) {
    validateCustomProviderForm()
  }
}

const clearCustomProviderFieldError = (field) => {
  if (!customProviderFormErrors.value[field]) {
    return
  }

  const nextErrors = { ...customProviderFormErrors.value }
  delete nextErrors[field]
  customProviderFormErrors.value = nextErrors
}

const reloadGeocodingSettings = async () => {
  geocodingSettings.value = await loadSettings('geocoding')
}

const reloadCustomProviders = async () => {
  customProviders.value = await adminService.getCustomGeocodingProviders()
}

onMounted(async () => {
  await Promise.all([reloadGeocodingSettings(), reloadCustomProviders()])
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
  const enabledCustomProviderNames = customProviders.value
    .filter(provider => provider.enabled)
    .map(provider => provider.name)
  const allEnabledProviderNames = [...enabledProviders.value, ...enabledCustomProviderNames]

  if (primaryProvider && !allEnabledProviderNames.includes(primaryProvider)) {
    return `Primary provider "${primaryProvider}" is not enabled. Please enable it first or choose a different provider.`
  }

  if (fallbackProvider && fallbackProvider !== '') {
    if (!allEnabledProviderNames.includes(fallbackProvider)) {
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
  if (adminReadOnly.value) {
    showDemoReadOnlyToast(toast)
    return
  }

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
    const errorDetail = extractApiErrorDetail(error, 'Failed to save settings')
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

const resetCustomProviderForm = () => {
  editingCustomProviderName.value = null
  customProviderFormErrors.value = {}
  customProviderForm.value = {
    name: '',
    displayName: '',
    type: 'photon',
    url: '',
    enabled: true,
    language: '',
    delayMs: null
  }
  customHeadersText.value = ''
}

const editCustomProvider = (provider) => {
  editingCustomProviderName.value = provider.name
  customProviderFormErrors.value = {}
  customProviderForm.value = {
    name: provider.name,
    displayName: provider.displayName,
    type: provider.type,
    url: provider.url,
    enabled: provider.enabled,
    language: provider.language || '',
    delayMs: provider.delayMs ?? null
  }
  customHeadersText.value = ''
}

const parseHeaders = () => {
  const text = customHeadersText.value?.trim()
  if (!text) {
    return {}
  }
  const parsed = JSON.parse(text)
  if (!parsed || Array.isArray(parsed) || typeof parsed !== 'object') {
    throw new Error('Headers JSON must be an object')
  }
  for (const [key, value] of Object.entries(parsed)) {
    if (!String(key).trim()) {
      throw new Error('Header names cannot be empty')
    }
    if (typeof value !== 'string') {
      throw new Error(`Header "${key}" value must be a string`)
    }
  }
  return parsed
}

const validateCustomProviderForm = () => {
  const errors = {}
  const name = normalizeCustomProviderName(customProviderForm.value.name)
  const displayName = customProviderForm.value.displayName?.trim() || ''
  const type = customProviderForm.value.type
  const url = customProviderForm.value.url?.trim() || ''
  const language = customProviderForm.value.language?.trim() || ''
  const delayMs = customProviderForm.value.delayMs

  customProviderForm.value.name = name

  if (!name) {
    errors.name = 'Provider name is required'
  } else if (!CUSTOM_PROVIDER_NAME_PATTERN.test(name)) {
    errors.name = 'Use lowercase letters, numbers, and hyphens. Start with a letter or number.'
  } else if (name.length > 50) {
    errors.name = 'Provider name must be 50 characters or fewer'
  } else if (!editingCustomProviderName.value && providerDefinitions.some(provider => provider.id === name)) {
    errors.name = 'Provider name is reserved for a built-in provider'
  } else if (!editingCustomProviderName.value && customProviders.value.some(provider => provider.name === name)) {
    errors.name = 'A custom provider with this name already exists'
  }

  if (!displayName) {
    errors.displayName = 'Display name is required'
  } else if (displayName.length > 50) {
    errors.displayName = 'Display name must be 50 characters or fewer'
  }

  if (!CUSTOM_PROVIDER_TYPES.has(type)) {
    errors.type = 'Choose Photon compatible or Nominatim compatible'
  }

  if (!url) {
    errors.url = 'Base URL is required'
  } else {
    try {
      const parsedUrl = new URL(url)
      if (!['http:', 'https:'].includes(parsedUrl.protocol)) {
        errors.url = 'Base URL must start with http:// or https://'
      }
    } catch {
      errors.url = 'Enter a valid base URL'
    }
  }

  if (type === 'photon') {
    const languageError = validatePhotonLanguage(language)
    if (languageError) {
      errors.language = languageError
    }
  }

  if (delayMs != null && delayMs !== '' && (!Number.isInteger(delayMs) || delayMs < 0)) {
    errors.delayMs = 'Delay must be a whole number greater than or equal to 0'
  }

  try {
    parseHeaders()
  } catch (error) {
    errors.headers = error.message
  }

  customProviderFormErrors.value = errors
  const firstError = Object.values(errors)[0]
  return firstError || null
}

const buildCustomProviderPayload = () => {
  const payload = {
    name: normalizeCustomProviderName(customProviderForm.value.name),
    displayName: customProviderForm.value.displayName.trim(),
    type: customProviderForm.value.type,
    url: customProviderForm.value.url.trim(),
    enabled: customProviderForm.value.enabled,
    language: customProviderForm.value.language?.trim() || null,
    delayMs: customProviderForm.value.delayMs ?? null
  }

  if (!editingCustomProviderName.value || customHeadersText.value?.trim()) {
    payload.headers = parseHeaders()
  }

  return payload
}

const saveCustomProvider = async () => {
  if (adminReadOnly.value) {
    showDemoReadOnlyToast(toast)
    return
  }

  const validationError = validateCustomProviderForm()
  if (validationError) {
    toast.add({ severity: 'error', summary: 'Validation Error', detail: validationError, life: 5000 })
    return
  }

  isSavingCustomProvider.value = true
  try {
    const payload = buildCustomProviderPayload()
    if (editingCustomProviderName.value) {
      await adminService.updateCustomGeocodingProvider(editingCustomProviderName.value, payload)
    } else {
      await adminService.createCustomGeocodingProvider(payload)
    }
    await reloadCustomProviders()
    resetCustomProviderForm()
    toast.add({ severity: 'success', summary: 'Provider Saved', detail: 'Custom geocoding provider saved', life: 3000 })
  } catch (error) {
    const detail = extractApiErrorDetail(error, 'Failed to save custom provider')
    toast.add({ severity: 'error', summary: 'Save Failed', detail, life: 5000 })
  } finally {
    isSavingCustomProvider.value = false
  }
}

const deleteCustomProvider = async (provider) => {
  if (adminReadOnly.value) {
    showDemoReadOnlyToast(toast)
    return
  }
  try {
    await adminService.deleteCustomGeocodingProvider(provider.name)
    await reloadCustomProviders()
    if (editingCustomProviderName.value === provider.name) {
      resetCustomProviderForm()
    }
    toast.add({ severity: 'success', summary: 'Provider Deleted', detail: 'Custom geocoding provider deleted', life: 3000 })
  } catch (error) {
    const detail = extractApiErrorDetail(error, 'Failed to delete custom provider')
    toast.add({ severity: 'error', summary: 'Delete Failed', detail, life: 5000 })
  }
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

.custom-providers-layout {
  width: 100%;
  max-width: 1240px;
  display: grid;
  grid-template-columns: minmax(360px, 480px) minmax(420px, 760px);
  border: 1px solid var(--surface-border);
  border-radius: var(--gp-radius-medium);
  background: var(--surface-card);
  overflow: hidden;
}

.custom-provider-list,
.custom-provider-form {
  padding: 1rem;
}

.custom-provider-list {
  border-right: 1px solid var(--surface-border);
}

.custom-provider-row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 1rem;
  padding: 0.85rem 0;
  border-bottom: 1px solid var(--surface-border);
}

.custom-provider-row:last-child {
  border-bottom: none;
}

.custom-provider-actions {
  display: flex;
  align-items: center;
  gap: 0.25rem;
  flex: 0 0 auto;
}

.new-custom-provider-button {
  color: var(--gp-primary);
}

.custom-provider-control {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 0.35rem;
}

.field-error {
  color: var(--red-500);
  line-height: 1.35;
}

.headers-input {
  width: min(100%, 520px);
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", monospace;
}

.custom-provider-save-row {
  display: flex;
  justify-content: flex-end;
  padding-top: 1rem;
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

  .custom-providers-layout {
    grid-template-columns: 1fr;
  }

  .custom-provider-list {
    border-right: none;
    border-bottom: 1px solid var(--surface-border);
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
  .custom-provider-control,
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
  .headers-input,
  .credential-input {
    width: 100%;
  }
}
</style>
