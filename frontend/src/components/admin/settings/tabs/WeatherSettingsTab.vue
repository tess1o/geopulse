<template>
  <div>
    <div v-if="hasUnsavedChanges" class="save-actions">
      <Message severity="warn" :closable="false">You have unsaved changes</Message>
      <div class="buttons">
        <Button label="Discard Changes" severity="secondary" outlined :disabled="isSaving" @click="reloadSettings" />
        <Button label="Save Changes" icon="pi pi-save" :loading="isSaving" :disabled="adminReadOnly" @click="saveAllChanges" />
      </div>
    </div>

    <SettingSection title="Weather">
      <SettingItem v-for="setting in basicProviderSettings" :key="setting.key" :setting="setting" @reset="handleReset(setting)">
        <template #control="{ setting }">
          <InputSwitch v-if="setting.valueType === 'BOOLEAN'" v-model="setting.currentValue" @change="markDirty" />
          <Select v-else-if="setting.key === 'weather.primary-provider'" v-model="setting.currentValue"
            :options="primaryProviderOptions" optionLabel="label" optionValue="value" class="provider-select" @change="markDirty" />
          <Select v-else-if="setting.key === 'weather.secondary-provider'" v-model="setting.currentValue"
            :options="secondaryProviderOptions" optionLabel="label" optionValue="value" class="provider-select" @change="markDirty" />
        </template>
      </SettingItem>

      <div v-for="setting in credentialSettings" :key="setting.key" class="credential-row">
        <div class="setting-info">
          <label>{{ setting.label }}</label>
          <small class="text-muted">{{ setting.description }}</small>
        </div>
        <div class="credential-control">
          <span class="credential-state">{{ credentialStateText(setting) }}</span>
          <Password v-if="credentialEditModes[setting.key]" v-model="credentialDrafts[setting.key]"
            :feedback="false" toggleMask autocomplete="new-password" class="credential-input" @input="markDirty" />
          <div class="buttons">
            <Button :label="credentialEditModes[setting.key] ? 'Cancel' : credentialStored(setting) ? 'Replace' : 'Set'"
              icon="pi pi-key" size="small" @click="toggleCredentialEdit(setting)" />
            <Button v-if="credentialStored(setting) || credentialDraftPresent(setting.key)" label="Clear"
              icon="pi pi-times" size="small" severity="danger" text @click="clearCredential(setting)" />
          </div>
        </div>
      </div>

      <div class="section-actions">
        <Button label="Test Connection" icon="pi pi-bolt" :loading="testingConnection"
          :disabled="adminReadOnly" @click="testConnection" />
      </div>
    </SettingSection>

    <SettingSection title="Collection">
      <SettingItem v-for="setting in collectionSettings" :key="setting.key" :setting="setting" @reset="handleReset(setting)">
        <template #control="{ setting }">
          <InputSwitch v-if="setting.valueType === 'BOOLEAN'" v-model="setting.currentValue" @change="markDirty" />
          <InputNumber v-else v-model="setting.currentValue" :min="numberMin(setting)" :max="numberMax(setting)"
            :step="numberStep(setting)" class="number-input" @update:modelValue="markDirty" />
        </template>
      </SettingItem>
    </SettingSection>

    <SettingSection title="Quota">
      <SettingItem v-for="setting in quotaSettings" :key="setting.key" :setting="setting" @reset="handleReset(setting)">
        <template #control="{ setting }">
          <InputNumber v-model="setting.currentValue" :min="0" :step="100" class="number-input" @update:modelValue="markDirty" />
        </template>
      </SettingItem>
    </SettingSection>

    <details class="advanced-settings">
      <summary>Advanced settings</summary>
      <SettingSection title="Provider URLs and retry policy">
        <SettingItem v-for="setting in advancedSettings" :key="setting.key" :setting="setting" @reset="handleReset(setting)">
          <template #control="{ setting }">
            <InputSwitch v-if="setting.valueType === 'BOOLEAN'" v-model="setting.currentValue" @change="markDirty" />
            <InputNumber v-else-if="setting.valueType === 'INTEGER'" v-model="setting.currentValue"
              :min="numberMin(setting)" :max="numberMax(setting)" :step="numberStep(setting)"
              class="number-input" @update:modelValue="markDirty" />
            <InputText v-else v-model="setting.currentValue" class="url-input" @input="markDirty" />
          </template>
        </SettingItem>
      </SettingSection>
    </details>

    <SettingSection title="Processing status">
      <div class="status-card">
        <div class="status-header">
          <div>
            <Tag :value="workerState" :severity="workerSeverity" />
            <h3>{{ statusSummary }}</h3>
          </div>
          <div class="buttons">
            <Button label="Refresh" icon="pi pi-refresh" severity="secondary" outlined :loading="loadingStatus" @click="loadStatus" />
            <Button v-if="canResumeProcessing" label="Resume Processing" icon="pi pi-play" :loading="processingWeatherNow"
              :disabled="adminReadOnly" @click="processWeatherNow" />
          </div>
        </div>

        <dl class="status-grid">
          <div><dt>Current phase</dt><dd>{{ phaseText }}</dd></div>
          <div><dt>Historical user ranges</dt><dd>{{ reconciliation.pendingUserRanges || 0 }}</dd></div>
          <div><dt>Pending targets</dt><dd>{{ pendingTargets }}</dd></div>
          <div><dt>Ready targets</dt><dd>{{ status.claimablePendingTargets || 0 }}</dd></div>
          <div><dt>Provider calls today</dt><dd>{{ status.requestsUsedToday || 0 }} / {{ status.dailyRequestLimit || 0 }}</dd></div>
          <div><dt>Ongoing reserve</dt><dd>{{ status.ongoingReserve || 0 }}</dd></div>
          <div><dt>Last completed</dt><dd>{{ formatDateTime(status.lastCompletedAt) }}</dd></div>
          <div><dt>Status refreshed</dt><dd>{{ statusRefreshedText }}</dd></div>
        </dl>

        <Message v-if="status.fetchBlockedReason" severity="warn" :closable="false" class="block-reason">
          {{ status.fetchBlockedReason }}
        </Message>
      </div>
    </SettingSection>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { storeToRefs } from 'pinia'
import { useToast } from 'primevue/usetoast'
import Button from 'primevue/button'
import InputNumber from 'primevue/inputnumber'
import InputSwitch from 'primevue/inputswitch'
import InputText from 'primevue/inputtext'
import Message from 'primevue/message'
import Password from 'primevue/password'
import Select from 'primevue/select'
import Tag from 'primevue/tag'
import SettingSection from '../SettingSection.vue'
import SettingItem from '../SettingItem.vue'
import { useAdminSettings } from '@/composables/useAdminSettings'
import { useAuthStore } from '@/stores/auth'
import apiService from '@/utils/apiService'
import { showDemoReadOnlyToast } from '@/utils/demoMode'
import { parseSettingValue } from '@/utils/settingHelpers'

const toast = useToast()
const { loadSettings, resetSetting } = useAdminSettings()
const { adminReadOnly } = storeToRefs(useAuthStore())
const settings = ref([])
const originalSettings = ref([])
const status = ref({})
const statusRefreshedAt = ref(null)
const hasUnsavedChanges = ref(false)
const isSaving = ref(false)
const testingConnection = ref(false)
const processingWeatherNow = ref(false)
const loadingStatus = ref(false)
const credentialDrafts = ref({})
const credentialEditModes = ref({})
const credentialCleared = ref({})

const providerOptions = [
  { label: 'Open-Meteo', value: 'OPEN_METEO', enabledKey: 'weather.open-meteo.enabled' },
  { label: 'Pirate Weather', value: 'PIRATE_WEATHER', enabledKey: 'weather.pirate.enabled' }
]
const basicProviderKeys = ['weather.enabled', 'weather.primary-provider', 'weather.secondary-provider',
  'weather.open-meteo.enabled', 'weather.pirate.enabled']
const credentialKeys = ['weather.open-meteo.api-key', 'weather.pirate.api-key']
const collectionKeys = ['weather.ongoing.enabled', 'weather.ongoing.interval-minutes', 'weather.backfill.enabled']
const quotaKeys = ['weather.quota.daily-request-limit', 'weather.quota.ongoing-reserve']
const advancedKeys = ['weather.open-meteo.forecast-url', 'weather.open-meteo.archive-url',
  'weather.pirate.base-url', 'weather.pirate.time-machine-url', 'weather.coordinate-precision',
  'weather.failed-target-retry.enabled', 'weather.failed-target-retry.cooldown-hours']

const getSetting = key => settings.value.find(setting => setting.key === key)
const getOriginalSetting = key => originalSettings.value.find(setting => setting.key === key)
const mapSettings = keys => keys.map(getSetting).filter(Boolean)
const basicProviderSettings = computed(() => mapSettings(basicProviderKeys))
const credentialSettings = computed(() => mapSettings(credentialKeys))
const collectionSettings = computed(() => mapSettings(collectionKeys))
const quotaSettings = computed(() => mapSettings(quotaKeys))
const advancedSettings = computed(() => mapSettings(advancedKeys))
const enabledProviderValues = computed(() => providerOptions.filter(provider => getSetting(provider.enabledKey)?.currentValue === true).map(provider => provider.value))
const primaryProviderOptions = computed(() => providerOptions.filter(provider => enabledProviderValues.value.includes(provider.value)))
const secondaryProviderOptions = computed(() => [{ label: 'None', value: '' }, ...primaryProviderOptions.value])
const reconciliation = computed(() => status.value.reconciliation || {})
const processing = computed(() => status.value.processing || {})
const pendingTargets = computed(() => status.value.targetsByStatus?.PENDING || 0)
const hasQueuedWork = computed(() => (reconciliation.value.pendingUserRanges || 0) > 0 || pendingTargets.value > 0)
const canResumeProcessing = computed(() => status.value.enabled && status.value.configured
  && !processing.value.running && hasQueuedWork.value)
const workerState = computed(() => processing.value.running ? 'RUNNING' : status.value.fetchBlockedReason ? 'BLOCKED' : 'IDLE')
const workerSeverity = computed(() => workerState.value === 'RUNNING' ? 'info' : workerState.value === 'BLOCKED' ? 'warn' : 'success')
const phaseText = computed(() => ({ FETCHING: 'Fetching queued weather', DISCOVERING: 'Discovering historical targets',
  DISCOVERING_ONGOING: 'Discovering ongoing targets', BLOCKED: 'Blocked', IDLE: 'Idle' })[processing.value.phase] || processing.value.phase || 'Idle')
const statusSummary = computed(() => {
  if (!status.value.enabled) return 'Weather is disabled'
  if (!status.value.configured) return 'The primary provider is not configured'
  if (processing.value.running) return phaseText.value
  if (status.value.fetchBlockedReason) return 'Processing is waiting for an external condition'
  if ((reconciliation.value.pendingUserRanges || 0) > 0 || pendingTargets.value > 0) return 'Work is queued'
  return 'Weather processing is caught up'
})
const statusRefreshedText = computed(() => statusRefreshedAt.value?.toLocaleTimeString() || 'Never')

const reloadSettings = async () => {
  settings.value = await loadSettings('weather')
  originalSettings.value = JSON.parse(JSON.stringify(settings.value))
  credentialDrafts.value = {}
  credentialEditModes.value = {}
  credentialCleared.value = {}
  hasUnsavedChanges.value = false
}
const loadStatus = async () => {
  loadingStatus.value = true
  try {
    const response = await apiService.get('/admin/weather/status')
    status.value = response?.data || response || {}
    statusRefreshedAt.value = new Date()
  } catch (error) {
    console.warn('Failed to load weather status:', error)
  } finally { loadingStatus.value = false }
}
const processWeatherNow = async () => {
  if (adminReadOnly.value) return showDemoReadOnlyToast(toast)
  processingWeatherNow.value = true
  try {
    const response = await apiService.post('/admin/weather/process-now')
    const result = response?.data || response || {}
    toast.add({ severity: 'info',
      summary: result.alreadyRunning ? 'Already Running' : 'Processing Requested',
      detail: result.message || 'The weather worker was notified', life: 4000 })
    await loadStatus()
  } catch (error) {
    toast.add({ severity: 'error', summary: 'Unable to Start Processing', detail: error.message, life: 5000 })
  } finally { processingWeatherNow.value = false }
}

const markDirty = () => { hasUnsavedChanges.value = true }
const credentialDraftPresent = key => String(credentialDrafts.value[key] || '').trim() !== ''
const credentialStored = setting => !credentialCleared.value[setting.key] && String(setting.currentValue || '').trim() !== ''
const credentialStateText = setting => credentialDraftPresent(setting.key) ? 'New value ready to save'
  : credentialCleared.value[setting.key] ? 'Will be cleared on save' : credentialStored(setting) ? 'Saved' : 'Not set'
const toggleCredentialEdit = setting => {
  if (credentialEditModes.value[setting.key]) {
    delete credentialDrafts.value[setting.key]
    delete credentialEditModes.value[setting.key]
  } else {
    credentialDrafts.value[setting.key] = ''
    credentialEditModes.value[setting.key] = true
  }
}
const clearCredential = setting => {
  delete credentialDrafts.value[setting.key]
  delete credentialEditModes.value[setting.key]
  credentialCleared.value[setting.key] = true
  setting.currentValue = ''
  markDirty()
}
const handleReset = async setting => {
  await resetSetting(setting)
  await reloadSettings()
  await loadStatus()
}
const buildChangedSettings = () => {
  const changed = []
  for (const setting of settings.value) {
    if (setting.valueType === 'ENCRYPTED') {
      if (credentialDraftPresent(setting.key)) changed.push({ key: setting.key, value: credentialDrafts.value[setting.key] })
      else if (credentialCleared.value[setting.key] && getOriginalSetting(setting.key)?.currentValue) changed.push({ key: setting.key, value: '' })
      continue
    }
    const original = getOriginalSetting(setting.key)
    if (!original || setting.currentValue !== original.currentValue) changed.push({ key: setting.key, value: parseSettingValue(setting) })
  }
  return changed
}
const validateChanges = () => {
  const interval = Number(getSetting('weather.ongoing.interval-minutes')?.currentValue)
  if (Number.isFinite(interval) && interval < 30) return 'Ongoing interval must be at least 30 minutes'
  const precision = Number(getSetting('weather.coordinate-precision')?.currentValue)
  if (Number.isFinite(precision) && (precision < 0 || precision > 5)) return 'Coordinate precision must be between 0 and 5'
  const primary = getSetting('weather.primary-provider')?.currentValue
  const secondary = getSetting('weather.secondary-provider')?.currentValue
  if (!primary || !enabledProviderValues.value.includes(primary)) return 'Primary provider must be enabled'
  if (secondary && (secondary === primary || !enabledProviderValues.value.includes(secondary))) return 'Fallback provider must be enabled and different from the primary provider'
  const pirateKey = getSetting('weather.pirate.api-key')
  if (getSetting('weather.pirate.enabled')?.currentValue === true && pirateKey && !credentialStored(pirateKey) && !credentialDraftPresent(pirateKey.key)) return 'Pirate Weather requires an API key'
  return null
}
const saveAllChanges = async () => {
  if (adminReadOnly.value) return showDemoReadOnlyToast(toast)
  const validationError = validateChanges()
  if (validationError) return toast.add({ severity: 'error', summary: 'Validation Error', detail: validationError, life: 4000 })
  const changed = buildChangedSettings()
  if (!changed.length) return (hasUnsavedChanges.value = false)
  isSaving.value = true
  try {
    await apiService.post('/admin/settings/bulk', { settings: changed })
    toast.add({ severity: 'success', summary: 'Settings Saved', detail: `Updated ${changed.length} setting${changed.length === 1 ? '' : 's'}`, life: 3000 })
    await reloadSettings()
    await loadStatus()
  } catch (error) {
    toast.add({ severity: 'error', summary: 'Save Failed', detail: error.message, life: 5000 })
  } finally { isSaving.value = false }
}
const testConnection = async () => {
  if (adminReadOnly.value) return showDemoReadOnlyToast(toast)
  testingConnection.value = true
  try {
    const response = await apiService.post('/admin/settings/weather/test')
    toast.add({ severity: 'success', summary: 'Connection OK', detail: response.message || 'Weather provider is reachable', life: 3500 })
    await loadStatus()
  } catch (error) {
    toast.add({ severity: 'error', summary: 'Connection Failed', detail: error.message, life: 5000 })
  } finally { testingConnection.value = false }
}
const numberMin = setting => setting.key === 'weather.ongoing.interval-minutes' ? 30 : 0
const numberMax = setting => setting.key === 'weather.coordinate-precision' ? 5 : null
const numberStep = setting => setting.key.includes('quota') ? 100 : 1
const formatDateTime = value => {
  if (!value) return 'None'
  try { return new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value)) } catch { return String(value) }
}
onMounted(async () => { await reloadSettings(); await loadStatus() })
</script>

<style scoped>
@import '../admin-settings-common.css';
.save-actions, .status-header, .buttons, .section-actions { display: flex; align-items: center; gap: 0.75rem; }
.save-actions, .status-header { justify-content: space-between; }
.credential-row { display: grid; grid-template-columns: minmax(240px, 1fr) minmax(300px, 520px); gap: 1rem; align-items: center; padding: 1rem; border-bottom: 1px solid var(--surface-border); }
.credential-control { display: grid; gap: 0.5rem; }
.credential-state { color: var(--gp-text-secondary); font-size: 0.86rem; font-weight: 700; }
.credential-input, .credential-input :deep(input) { width: 100%; }
.section-actions { margin: 1rem; }
.advanced-settings { margin: 1rem 0; border: 1px solid var(--gp-border-light); border-radius: 6px; }
.advanced-settings summary { padding: 1rem; cursor: pointer; font-weight: 800; }
.status-card { margin: 0 1rem; padding: 1rem; border: 1px solid var(--gp-border-light); border-radius: 6px; background: color-mix(in srgb, var(--surface-ground) 70%, transparent); }
.status-header h3 { margin: 0.6rem 0 0; }
.status-grid { display: grid; grid-template-columns: repeat(2, minmax(260px, 1fr)); gap: 0.8rem 2rem; margin: 1.25rem 0 0; }
.status-grid div { display: flex; justify-content: space-between; gap: 1rem; }
.status-grid dt { color: var(--gp-text-secondary); }
.status-grid dd { margin: 0; font-weight: 700; text-align: right; }
.block-reason { margin-top: 1rem; }
.provider-select { width: 220px; }
.url-input { width: min(56vw, 720px); min-width: 420px; }
@media (max-width: 768px) {
  .save-actions, .status-header, .credential-row, .status-grid { grid-template-columns: 1fr; flex-direction: column; align-items: stretch; }
  .status-grid div { display: grid; }
  .status-grid dd { text-align: left; }
  .url-input { width: 100%; min-width: 0; }
}
</style>
