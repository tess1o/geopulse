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
          :disabled="isSaving"
          @click="discardChanges"
        />
        <Button
          label="Save Changes"
          icon="pi pi-save"
          :loading="isSaving"
          @click="saveAllChanges"
        />
      </div>
    </div>

    <SettingSection title="Provider">
      <SettingItem
        v-for="setting in providerSettings"
        :key="setting.key"
        :setting="setting"
        @reset="handleReset(setting)"
      >
        <template #control="{ setting }">
          <InputSwitch
            v-if="setting.valueType === 'BOOLEAN'"
            v-model="setting.currentValue"
            @change="markDirty"
          />
          <InputText
            v-else
            v-model="setting.currentValue"
            class="setting-text-input"
            :placeholder="getPlaceholder(setting)"
            @input="markDirty"
          />
        </template>
      </SettingItem>

      <div v-if="apiKeySetting" class="credential-row" :data-setting-id="apiKeySetting.key">
        <div class="setting-info">
          <label>{{ apiKeySetting.label }}</label>
          <small class="text-muted">{{ apiKeySetting.description }}</small>
        </div>
        <div class="credential-control">
          <span class="credential-state">{{ apiKeyStateText }}</span>
          <Password
            v-if="editingApiKey"
            v-model="apiKeyDraft"
            :feedback="false"
            toggleMask
            autocomplete="new-password"
            placeholder="Enter Open-Meteo API key"
            class="credential-input"
            @input="markDirty"
          />
          <div class="credential-actions">
            <Button
              :label="editingApiKey ? 'Cancel' : apiKeyStored ? 'Replace' : 'Set'"
              icon="pi pi-key"
              size="small"
              class="api-key-action-button"
              @click="toggleApiKeyEdit"
            />
            <Button
              v-if="apiKeyStored || apiKeyDraft"
              label="Clear"
              icon="pi pi-times"
              size="small"
              severity="danger"
              text
              @click="clearApiKey"
            />
          </div>
        </div>
        <div class="setting-status">
          <Tag v-if="apiKeySetting.isDefault" severity="secondary" value="Default" />
          <Button
            v-else
            label="Reset"
            icon="pi pi-refresh"
            text
            size="small"
            @click="handleReset(apiKeySetting)"
          />
        </div>
      </div>

      <div class="provider-actions">
        <Button
          label="Test Connection"
          icon="pi pi-bolt"
          class="test-connection-button"
          :loading="testingConnection"
          @click="testConnection"
        />
      </div>
    </SettingSection>

    <SettingSection title="Sampling">
      <SettingItem
        v-for="setting in samplingSettings"
        :key="setting.key"
        :setting="setting"
        @reset="handleReset(setting)"
      >
        <template #control="{ setting }">
          <InputSwitch
            v-if="setting.valueType === 'BOOLEAN'"
            v-model="setting.currentValue"
            @change="markDirty"
          />
          <InputNumber
            v-else
            v-model="setting.currentValue"
            :min="numberMin(setting)"
            :max="numberMax(setting)"
            :step="numberStep(setting)"
            class="number-input"
            @update:modelValue="markDirty"
          />
        </template>
      </SettingItem>
    </SettingSection>

    <SettingSection title="Quota & Processing">
      <SettingItem
        v-for="setting in quotaSettings"
        :key="setting.key"
        :setting="setting"
        @reset="handleReset(setting)"
      >
        <template #control="{ setting }">
          <InputNumber
            v-model="setting.currentValue"
            :min="numberMin(setting)"
            :max="numberMax(setting)"
            :step="numberStep(setting)"
            class="number-input"
            @update:modelValue="markDirty"
          />
        </template>
      </SettingItem>
    </SettingSection>

    <SettingSection title="Status">
      <div class="weather-status">
        <div class="weather-status-row">
          <span>State</span>
          <Tag :value="statusLabel" :severity="statusSeverity" />
        </div>
        <div class="weather-status-row">
          <span>Requests today</span>
          <strong>{{ status.requestsUsedToday || 0 }} / {{ status.dailyRequestLimit || 0 }}</strong>
        </div>
        <div class="weather-status-row">
          <span>Samples stored</span>
          <strong>{{ status.samples || 0 }}</strong>
        </div>
        <div class="weather-status-row">
          <span>Pending targets</span>
          <strong>{{ pendingTargets }}</strong>
        </div>
        <div class="weather-status-row">
          <span>In progress targets</span>
          <strong>{{ inProgressTargets }}</strong>
        </div>
        <div class="weather-status-row">
          <span>Completed targets</span>
          <strong>{{ completedTargets }}</strong>
        </div>
        <div class="weather-status-row">
          <span>Skipped targets</span>
          <strong>{{ skippedTargets }}</strong>
        </div>
        <div class="weather-status-row">
          <span>Failed targets</span>
          <strong>{{ failedTargets }}</strong>
        </div>
        <div class="weather-status-row">
          <span>Status refreshed</span>
          <strong>{{ statusRefreshedText }}</strong>
        </div>
      </div>
    </SettingSection>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useToast } from 'primevue/usetoast'
import Button from 'primevue/button'
import InputNumber from 'primevue/inputnumber'
import InputSwitch from 'primevue/inputswitch'
import InputText from 'primevue/inputtext'
import Message from 'primevue/message'
import Password from 'primevue/password'
import Tag from 'primevue/tag'
import SettingSection from '../SettingSection.vue'
import SettingItem from '../SettingItem.vue'
import { useAdminSettings } from '@/composables/useAdminSettings'
import apiService from '@/utils/apiService'
import { getPlaceholder as getPlaceholderHelper, parseSettingValue } from '@/utils/settingHelpers'

const toast = useToast()
const { loadSettings, resetSetting } = useAdminSettings()

const settings = ref([])
const originalSettings = ref([])
const status = ref({})
const hasUnsavedChanges = ref(false)
const isSaving = ref(false)
const testingConnection = ref(false)
const editingApiKey = ref(false)
const apiKeyDraft = ref('')
const apiKeyCleared = ref(false)
const statusRefreshedAt = ref(null)
let statusRefreshTimer = null

const providerKeys = [
  'weather.enabled',
  'weather.open-meteo.forecast-url',
  'weather.open-meteo.archive-url'
]
const samplingKeys = [
  'weather.ongoing.enabled',
  'weather.ongoing.interval-minutes',
  'weather.backfill.enabled',
  'weather.coordinate-precision',
  'weather.failed-target-retry.enabled',
  'weather.failed-target-retry.cooldown-hours'
]
const quotaKeys = [
  'weather.quota.daily-request-limit',
  'weather.quota.ongoing-reserve'
]

const getSetting = (key) => settings.value.find(setting => setting.key === key)
const getOriginalSetting = (key) => originalSettings.value.find(setting => setting.key === key)
const providerSettings = computed(() => providerKeys.map(getSetting).filter(Boolean))
const samplingSettings = computed(() => samplingKeys.map(getSetting).filter(Boolean))
const quotaSettings = computed(() => quotaKeys.map(getSetting).filter(Boolean))
const apiKeySetting = computed(() => getSetting('weather.open-meteo.api-key'))
const apiKeyStored = computed(() => !apiKeyCleared.value && apiKeySetting.value?.currentValue && apiKeySetting.value.currentValue !== '')
const apiKeyStateText = computed(() => {
  if (apiKeyDraft.value) return 'New value ready to save'
  if (apiKeyCleared.value) return 'Will be cleared on save'
  if (apiKeyStored.value) return 'Saved'
  return 'Not set'
})
const pendingTargets = computed(() => status.value?.targetsByStatus?.PENDING || 0)
const inProgressTargets = computed(() => status.value?.targetsByStatus?.IN_PROGRESS || 0)
const completedTargets = computed(() => status.value?.targetsByStatus?.COMPLETED || 0)
const skippedTargets = computed(() => status.value?.targetsByStatus?.SKIPPED || 0)
const failedTargets = computed(() => status.value?.targetsByStatus?.FAILED || 0)
const statusRefreshedText = computed(() => {
  if (!statusRefreshedAt.value) return 'Never'
  return statusRefreshedAt.value.toLocaleTimeString()
})
const statusLabel = computed(() => {
  if (!status.value?.enabled) return 'Disabled'
  if (!status.value?.configured) return 'Not configured'
  return 'Enabled'
})
const statusSeverity = computed(() => {
  if (!status.value?.enabled) return 'secondary'
  if (!status.value?.configured) return 'warn'
  return 'success'
})

const reloadSettings = async () => {
  settings.value = await loadSettings('weather')
  originalSettings.value = JSON.parse(JSON.stringify(settings.value))
  apiKeyDraft.value = ''
  apiKeyCleared.value = false
  editingApiKey.value = false
  hasUnsavedChanges.value = false
}

const loadStatus = async () => {
  try {
    const response = await apiService.get('/admin/weather/status')
    status.value = response?.data || response || {}
    statusRefreshedAt.value = new Date()
  } catch (error) {
    console.warn('Failed to load weather status:', error)
  }
}

onMounted(async () => {
  await reloadSettings()
  await loadStatus()
  statusRefreshTimer = window.setInterval(loadStatus, 10000)
})

onBeforeUnmount(() => {
  if (statusRefreshTimer) {
    window.clearInterval(statusRefreshTimer)
    statusRefreshTimer = null
  }
})

const getPlaceholder = (setting) => getPlaceholderHelper(setting)

const markDirty = () => {
  hasUnsavedChanges.value = true
}

const toggleApiKeyEdit = () => {
  if (editingApiKey.value) {
    apiKeyDraft.value = ''
    editingApiKey.value = false
    return
  }
  editingApiKey.value = true
}

const clearApiKey = () => {
  apiKeyDraft.value = ''
  apiKeyCleared.value = true
  editingApiKey.value = false
  markDirty()
}

const discardChanges = async () => {
  await reloadSettings()
}

const handleReset = async (setting) => {
  await resetSetting(setting)
  await reloadSettings()
  await loadStatus()
}

const buildChangedSettings = () => {
  const changed = []
  settings.value.forEach(setting => {
    if (setting.valueType === 'ENCRYPTED') {
      return
    }

    const original = getOriginalSetting(setting.key)
    if (!original || setting.currentValue !== original.currentValue) {
      changed.push({
        key: setting.key,
        value: parseSettingValue(setting)
      })
    }
  })

  if (apiKeySetting.value && apiKeyDraft.value) {
    changed.push({
      key: apiKeySetting.value.key,
      value: apiKeyDraft.value
    })
  } else if (apiKeySetting.value && apiKeyCleared.value) {
    changed.push({
      key: apiKeySetting.value.key,
      value: ''
    })
  }

  return changed
}

const validateChanges = () => {
  const ongoingInterval = Number(getSetting('weather.ongoing.interval-minutes')?.currentValue)
  if (Number.isFinite(ongoingInterval) && ongoingInterval < 30) {
    return 'Ongoing interval must be at least 30 minutes'
  }

  const precision = Number(getSetting('weather.coordinate-precision')?.currentValue)
  if (Number.isFinite(precision) && (precision < 0 || precision > 5)) {
    return 'Coordinate precision must be between 0 and 5'
  }

  return null
}

const saveAllChanges = async () => {
  const validationError = validateChanges()
  if (validationError) {
    toast.add({
      severity: 'error',
      summary: 'Validation Error',
      detail: validationError,
      life: 4000
    })
    return
  }

  const changed = buildChangedSettings()
  if (changed.length === 0) {
    hasUnsavedChanges.value = false
    return
  }

  isSaving.value = true
  try {
    await apiService.post('/admin/settings/bulk', { settings: changed })
    toast.add({
      severity: 'success',
      summary: 'Settings Saved',
      detail: `Updated ${changed.length} weather setting${changed.length === 1 ? '' : 's'}`,
      life: 3000
    })
    await reloadSettings()
    await loadStatus()
  } catch (error) {
    const detail = error.response?.data?.message || error.message || 'Failed to save weather settings'
    toast.add({
      severity: 'error',
      summary: 'Save Failed',
      detail,
      life: 5000
    })
  } finally {
    isSaving.value = false
  }
}

const testConnection = async () => {
  testingConnection.value = true
  try {
    const response = await apiService.post('/admin/settings/weather/test')
    toast.add({
      severity: 'success',
      summary: 'Connection OK',
      detail: response.message || 'Open-Meteo endpoint is reachable',
      life: 3500
    })
  } catch (error) {
    const detail = error.response?.data?.message || error.message || 'Open-Meteo test failed'
    toast.add({
      severity: 'error',
      summary: 'Connection Failed',
      detail,
      life: 5000
    })
  } finally {
    testingConnection.value = false
  }
}

const numberMin = (setting) => {
  if (setting.key === 'weather.ongoing.interval-minutes') return 30
  if (setting.key === 'weather.coordinate-precision') return 0
  return 0
}

const numberMax = (setting) => {
  if (setting.key === 'weather.coordinate-precision') return 5
  return null
}

const numberStep = (setting) => {
  if (setting.key.includes('quota')) return 100
  return 1
}
</script>

<style scoped>
@import '../admin-settings-common.css';

.save-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 1rem;
  margin-bottom: 1rem;
}

.buttons,
.provider-actions,
.credential-actions {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.credential-row {
  display: grid;
  grid-template-columns: minmax(240px, 1fr) minmax(360px, 520px) 80px;
  gap: 1rem;
  align-items: center;
  padding: 1rem;
  border-bottom: 1px solid var(--surface-border);
}

.credential-control {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  width: 100%;
}

.credential-state {
  color: var(--gp-text-secondary);
  font-size: 0.86rem;
  font-weight: 700;
}

.credential-input {
  width: 100%;
}

.credential-input :deep(.p-password-input),
.credential-input :deep(input) {
  width: 100%;
}

:deep(.api-key-action-button.p-button) {
  color: #ffffff;
  background: #15803d;
  border-color: #15803d;
  font-weight: 600;
}

:deep(.api-key-action-button.p-button:not(:disabled):hover) {
  color: #ffffff;
  background: #166534;
  border-color: #166534;
}

:deep(.api-key-action-button.p-button .p-button-icon),
:deep(.api-key-action-button.p-button .p-button-label) {
  color: #ffffff;
}

.provider-actions {
  margin-top: 1rem;
  padding-left: 1rem;
}

:deep(.test-connection-button.p-button) {
  color: #ffffff;
  background: #2563eb;
  border-color: #2563eb;
  font-weight: 600;
}

:deep(.test-connection-button.p-button:not(:disabled):hover) {
  color: #ffffff;
  background: #1d4ed8;
  border-color: #1d4ed8;
}

:deep(.test-connection-button.p-button .p-button-icon),
:deep(.test-connection-button.p-button .p-button-label) {
  color: #ffffff;
}

.weather-status {
  max-width: 520px;
  margin-left: 1rem;
  border: 1px solid var(--gp-border-light);
  border-radius: 6px;
  overflow: hidden;
}

.weather-status-row {
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 1rem;
  align-items: center;
  padding: 0.75rem 1rem;
  border-bottom: 1px solid var(--gp-border-light);
}

.weather-status-row:last-child {
  border-bottom: none;
}

.weather-status-row span {
  color: var(--gp-text-secondary);
}

@media (max-width: 768px) {
  .save-actions,
  .credential-row {
    grid-template-columns: 1fr;
    flex-direction: column;
    align-items: stretch;
  }

  .provider-actions {
    padding-left: 0.5rem;
  }

  .weather-status {
    margin-left: 0.5rem;
    margin-right: 0.5rem;
  }
}
</style>
