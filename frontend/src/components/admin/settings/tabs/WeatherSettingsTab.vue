<template>
  <div>
    <div v-if="hasUnsavedChanges" class="save-actions">
      <Message severity="warn" :closable="false">{{ t('adminProviderSettings.shared.unsavedChanges') }}</Message>
      <div class="buttons">
        <Button :label="t('adminProviderSettings.shared.discardChanges')" severity="secondary" outlined :disabled="isSaving" @click="reloadSettings" />
        <Button :label="t('adminProviderSettings.shared.saveChanges')" icon="pi pi-save" :loading="isSaving" :disabled="adminReadOnly" @click="saveAllChanges" />
      </div>
    </div>

    <SettingSection :title="t('admin.settingsPage.tabs.weather')">
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
            <Button :label="credentialEditModes[setting.key] ? t('adminProviderSettings.weatherSettingsTab.cancel') : credentialStored(setting) ? t('adminProviderSettings.shared.replace') : t('adminProviderSettings.shared.set')"
              icon="pi pi-key" size="small" @click="toggleCredentialEdit(setting)" />
            <Button v-if="credentialStored(setting) || credentialDraftPresent(setting.key)" :label="t('adminProviderSettings.shared.clear')"
              icon="pi pi-times" size="small" severity="danger" text @click="clearCredential(setting)" />
          </div>
        </div>
      </div>

      <div class="section-actions">
        <Button :label="t('adminProviderSettings.shared.testConnection')" icon="pi pi-bolt" :loading="testingConnection"
          :disabled="adminReadOnly" @click="testConnection" />
      </div>
    </SettingSection>

    <SettingSection :title="t('adminProviderSettings.weatherSettingsTab.collectionSectionTitle')">
      <SettingItem v-for="setting in collectionSettings" :key="setting.key" :setting="setting" @reset="handleReset(setting)">
        <template #control="{ setting }">
          <InputSwitch v-if="setting.valueType === 'BOOLEAN'" v-model="setting.currentValue" @change="markDirty" />
          <InputNumber v-else v-model="setting.currentValue" :min="numberMin(setting)" :max="numberMax(setting)"
            :step="numberStep(setting)" class="number-input" @update:modelValue="markDirty" />
        </template>
      </SettingItem>
    </SettingSection>

    <SettingSection :title="t('adminProviderSettings.weatherSettingsTab.quotaSectionTitle')">
      <SettingItem v-for="setting in quotaSettings" :key="setting.key" :setting="setting" @reset="handleReset(setting)">
        <template #control="{ setting }">
          <InputNumber v-model="setting.currentValue" :min="0" :step="100" class="number-input" @update:modelValue="markDirty" />
        </template>
      </SettingItem>
    </SettingSection>

    <details class="advanced-settings">
      <summary>{{ t('adminProviderSettings.weatherSettingsTab.advancedSettingsSummary') }}</summary>
      <SettingSection :title="t('adminProviderSettings.weatherSettingsTab.providerUrlsSectionTitle')">
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

    <SettingSection :title="t('adminProviderSettings.weatherSettingsTab.processingStatusSectionTitle')">
      <div class="status-card">
        <div class="status-header">
          <div>
            <Tag :value="workerState" :severity="workerSeverity" />
            <h3>{{ statusSummary }}</h3>
          </div>
          <div class="buttons">
            <Button :label="t('adminProviderSettings.shared.refresh')" icon="pi pi-refresh" severity="secondary" outlined :loading="loadingStatus" @click="loadStatus" />
            <Button v-if="canResumeProcessing" :label="t('adminProviderSettings.weatherSettingsTab.resumeProcessing')" icon="pi pi-play" :loading="processingWeatherNow"
              :disabled="adminReadOnly" @click="processWeatherNow" />
          </div>
        </div>

        <dl class="status-grid">
          <div><dt>{{ t('adminProviderSettings.weatherSettingsTab.currentPhase') }}</dt><dd>{{ phaseText }}</dd></div>
          <div><dt>{{ t('adminProviderSettings.weatherSettingsTab.historicalUserRanges') }}</dt><dd>{{ reconciliation.pendingUserRanges || 0 }}</dd></div>
          <div><dt>{{ t('adminProviderSettings.weatherSettingsTab.pendingTargets') }}</dt><dd>{{ pendingTargets }}</dd></div>
          <div><dt>{{ t('adminProviderSettings.weatherSettingsTab.readyTargets') }}</dt><dd>{{ status.claimablePendingTargets || 0 }}</dd></div>
          <div><dt>{{ t('adminProviderSettings.weatherSettingsTab.providerCallsToday') }}</dt><dd>{{ status.requestsUsedToday || 0 }} / {{ status.dailyRequestLimit || 0 }}</dd></div>
          <div><dt>{{ t('adminProviderSettings.weatherSettingsTab.ongoingReserve') }}</dt><dd>{{ status.ongoingReserve || 0 }}</dd></div>
          <div><dt>{{ t('adminProviderSettings.weatherSettingsTab.lastCompleted') }}</dt><dd>{{ formatDateTime(status.lastCompletedAt) }}</dd></div>
          <div><dt>{{ t('adminProviderSettings.weatherSettingsTab.statusRefreshed') }}</dt><dd>{{ statusRefreshedText }}</dd></div>
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
import { useI18n } from 'vue-i18n'
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
import { useAdminStore } from '@/stores/admin'
import { showDemoReadOnlyToast } from '@/utils/demoMode'
import { parseSettingValue } from '@/utils/settingHelpers'

const { t } = useI18n()
const toast = useToast()
const { loadSettings, resetSetting } = useAdminSettings()
const { adminReadOnly } = storeToRefs(useAuthStore())
const adminStore = useAdminStore()
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
  'weather.failed-target-retry.enabled', 'weather.failed-target-retry.cooldown-hours',
  'weather.open-meteo.connect-timeout-seconds', 'weather.open-meteo.read-timeout-seconds',
  'weather.pirate.connect-timeout-seconds', 'weather.pirate.read-timeout-seconds',
  'weather.targets.completed-retention-days', 'weather.targets.failed-retention-days',
  'weather.targets.in-progress-timeout-minutes']

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
const secondaryProviderOptions = computed(() => [{ label: t('adminProviderSettings.shared.none'), value: '' }, ...primaryProviderOptions.value])
const reconciliation = computed(() => status.value.reconciliation || {})
const processing = computed(() => status.value.processing || {})
const pendingTargets = computed(() => status.value.targetsByStatus?.PENDING || 0)
const hasQueuedWork = computed(() => (reconciliation.value.pendingUserRanges || 0) > 0 || pendingTargets.value > 0)
const canResumeProcessing = computed(() => status.value.enabled && status.value.configured
  && !processing.value.running && hasQueuedWork.value)
const workerState = computed(() => processing.value.running ? 'RUNNING' : status.value.fetchBlockedReason ? 'BLOCKED' : 'IDLE')
const workerSeverity = computed(() => workerState.value === 'RUNNING' ? 'info' : workerState.value === 'BLOCKED' ? 'warn' : 'success')
const phaseText = computed(() => ({
  FETCHING: t('adminProviderSettings.weatherSettingsTab.phaseText.fetching'),
  DISCOVERING: t('adminProviderSettings.weatherSettingsTab.phaseText.discovering'),
  DISCOVERING_ONGOING: t('adminProviderSettings.weatherSettingsTab.phaseText.discoveringOngoing'),
  BLOCKED: t('adminProviderSettings.weatherSettingsTab.phaseText.blocked'),
  IDLE: t('adminProviderSettings.weatherSettingsTab.phaseText.idle')
})[processing.value.phase] || processing.value.phase || t('adminProviderSettings.weatherSettingsTab.phaseText.idle'))
const statusSummary = computed(() => {
  if (!status.value.enabled) return t('adminProviderSettings.weatherSettingsTab.statusSummary.disabled')
  if (!status.value.configured) return t('adminProviderSettings.weatherSettingsTab.statusSummary.notConfigured')
  if (processing.value.running) return phaseText.value
  if (status.value.fetchBlockedReason) return t('adminProviderSettings.weatherSettingsTab.statusSummary.waitingExternal')
  if ((reconciliation.value.pendingUserRanges || 0) > 0 || pendingTargets.value > 0) return t('adminProviderSettings.weatherSettingsTab.statusSummary.workQueued')
  return t('adminProviderSettings.weatherSettingsTab.statusSummary.caughtUp')
})
const statusRefreshedText = computed(() => statusRefreshedAt.value?.toLocaleTimeString() || t('adminProviderSettings.weatherSettingsTab.never'))

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
    status.value = await adminStore.getWeatherStatus() || {}
    statusRefreshedAt.value = new Date()
  } catch (error) {
    console.warn('Failed to load weather status:', error)
  } finally { loadingStatus.value = false }
}
const processWeatherNow = async () => {
  if (adminReadOnly.value) return showDemoReadOnlyToast(toast)
  processingWeatherNow.value = true
  try {
    const result = await adminStore.processWeatherNow() || {}
    toast.add({ severity: 'info',
      summary: result.alreadyRunning ? t('adminProviderSettings.weatherSettingsTab.toasts.alreadyRunning') : t('adminProviderSettings.weatherSettingsTab.toasts.processingRequested'),
      detail: result.alreadyRunning ? t('adminProviderSettings.weatherSettingsTab.toasts.alreadyRunningDetail') : t('adminProviderSettings.weatherSettingsTab.toasts.processingRequestedDetail'), life: 4000 })
    await loadStatus()
  } catch (error) {
    toast.add({ severity: 'error', summary: t('adminProviderSettings.weatherSettingsTab.toasts.unableToStart'), detail: error.message, life: 5000 })
  } finally { processingWeatherNow.value = false }
}

const markDirty = () => { hasUnsavedChanges.value = true }
const credentialDraftPresent = key => String(credentialDrafts.value[key] || '').trim() !== ''
const credentialStored = setting => !credentialCleared.value[setting.key] && String(setting.currentValue || '').trim() !== ''
const credentialStateText = setting => credentialDraftPresent(setting.key) ? t('adminProviderSettings.shared.credentialState.newValueReady')
  : credentialCleared.value[setting.key] ? t('adminProviderSettings.shared.credentialState.willBeCleared') : credentialStored(setting) ? t('adminProviderSettings.shared.credentialState.saved') : t('adminProviderSettings.shared.credentialState.notSet')
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
  if (Number.isFinite(interval) && interval < 30) return t('adminProviderSettings.weatherSettingsTab.validation.intervalTooShort')
  const precision = Number(getSetting('weather.coordinate-precision')?.currentValue)
  if (Number.isFinite(precision) && (precision < 0 || precision > 5)) return t('adminProviderSettings.weatherSettingsTab.validation.precisionOutOfRange')
  const primary = getSetting('weather.primary-provider')?.currentValue
  const secondary = getSetting('weather.secondary-provider')?.currentValue
  if (!primary || !enabledProviderValues.value.includes(primary)) return t('adminProviderSettings.weatherSettingsTab.validation.primaryMustBeEnabled')
  if (secondary && (secondary === primary || !enabledProviderValues.value.includes(secondary))) return t('adminProviderSettings.weatherSettingsTab.validation.fallbackMustBeEnabledAndDifferent')
  const pirateKey = getSetting('weather.pirate.api-key')
  if (getSetting('weather.pirate.enabled')?.currentValue === true && pirateKey && !credentialStored(pirateKey) && !credentialDraftPresent(pirateKey.key)) return t('adminProviderSettings.weatherSettingsTab.validation.pirateRequiresApiKey')
  return null
}
const saveAllChanges = async () => {
  if (adminReadOnly.value) return showDemoReadOnlyToast(toast)
  const validationError = validateChanges()
  if (validationError) return toast.add({ severity: 'error', summary: t('adminProviderSettings.shared.validationError'), detail: validationError, life: 4000 })
  const changed = buildChangedSettings()
  if (!changed.length) return (hasUnsavedChanges.value = false)
  isSaving.value = true
  try {
    await adminStore.bulkUpdateSettings(changed)
    toast.add({ severity: 'success', summary: t('adminProviderSettings.shared.settingsSaved'), detail: t('adminProviderSettings.shared.settingsUpdatedDetail', { count: changed.length }, changed.length), life: 3000 })
    await reloadSettings()
    await loadStatus()
  } catch (error) {
    toast.add({ severity: 'error', summary: t('adminProviderSettings.shared.saveFailed'), detail: error.message, life: 5000 })
  } finally { isSaving.value = false }
}
const testConnection = async () => {
  if (adminReadOnly.value) return showDemoReadOnlyToast(toast)
  testingConnection.value = true
  try {
    const response = await adminStore.testWeatherConnection()
    toast.add({
      severity: response.success ? 'success' : 'error',
      summary: response.success ? t('adminProviderSettings.shared.connectionOk') : t('adminProviderSettings.shared.connectionFailed'),
      detail: response.message || (response.success ? t('adminProviderSettings.weatherSettingsTab.toasts.weatherReachable') : t('adminProviderSettings.weatherSettingsTab.toasts.weatherUnreachable')),
      life: 3500
    })
    await loadStatus()
  } catch (error) {
    toast.add({ severity: 'error', summary: t('adminProviderSettings.shared.connectionFailed'), detail: error.message, life: 5000 })
  } finally { testingConnection.value = false }
}
const numberMin = setting => setting.key === 'weather.ongoing.interval-minutes' ? 30 : 0
const numberMax = setting => setting.key === 'weather.coordinate-precision' ? 5 : null
const numberStep = setting => setting.key.includes('quota') ? 100 : 1
const formatDateTime = value => {
  if (!value) return t('adminProviderSettings.shared.none')
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
