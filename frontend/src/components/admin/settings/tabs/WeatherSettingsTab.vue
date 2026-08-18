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
          :disabled="adminReadOnly"
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
          <Select
            v-else-if="setting.key === 'weather.primary-provider'"
            v-model="setting.currentValue"
            :options="primaryProviderOptions"
            optionLabel="label"
            optionValue="value"
            class="provider-select"
            placeholder="Select primary provider"
            @change="markDirty"
          />
          <Select
            v-else-if="setting.key === 'weather.secondary-provider'"
            v-model="setting.currentValue"
            :options="secondaryProviderOptions"
            optionLabel="label"
            optionValue="value"
            class="provider-select"
            placeholder="Select fallback provider"
            @change="markDirty"
          />
          <InputText
            v-else
            v-model="setting.currentValue"
            :class="['setting-text-input', { 'url-input': isUrlSetting(setting) }]"
            :placeholder="getPlaceholder(setting)"
            @input="markDirty"
          />
        </template>
      </SettingItem>

      <div
        v-for="credentialSetting in credentialSettings"
        :key="credentialSetting.key"
        class="credential-row"
        :data-setting-id="credentialSetting.key"
      >
        <div class="setting-info">
          <label>{{ credentialSetting.label }}</label>
          <small class="text-muted">{{ credentialSetting.description }}</small>
        </div>
        <div class="credential-control">
          <span class="credential-state">{{ credentialStateText(credentialSetting) }}</span>
          <Password
            v-if="credentialEditModes[credentialSetting.key]"
            v-model="credentialDrafts[credentialSetting.key]"
            :feedback="false"
            toggleMask
            autocomplete="new-password"
            :placeholder="`Enter ${credentialLabel(credentialSetting).toLowerCase()}`"
            class="credential-input"
            @input="markDirty"
          />
          <div class="credential-actions">
            <Button
              :label="credentialEditModes[credentialSetting.key] ? 'Cancel' : credentialStored(credentialSetting) ? 'Replace' : 'Set'"
              icon="pi pi-key"
              size="small"
              class="api-key-action-button"
              @click="toggleCredentialEdit(credentialSetting)"
            />
            <Button
              v-if="credentialStored(credentialSetting) || credentialDraftPresent(credentialSetting.key)"
              label="Clear"
              icon="pi pi-times"
              size="small"
              severity="danger"
              text
              @click="clearCredential(credentialSetting)"
            />
          </div>
        </div>
        <div class="setting-status">
          <Tag v-if="credentialSetting.isDefault" severity="secondary" value="Default" />
          <Button
            v-else
            label="Reset"
            icon="pi pi-refresh"
            text
            size="small"
            @click="handleReset(credentialSetting)"
          />
        </div>
      </div>

      <div class="provider-actions">
        <Button
          label="Test Connection"
          icon="pi pi-bolt"
          class="test-connection-button"
          :loading="testingConnection"
          :disabled="adminReadOnly"
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
        v-for="setting in quotaAndProcessingSettings"
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
      <div class="weather-status-panel">
        <div class="weather-status-header">
          <Message :severity="weatherSummarySeverity" :closable="false" class="weather-summary-message">
            {{ weatherSummaryText }}
          </Message>
          <Button
            label="Run Weather Now"
            icon="pi pi-play"
            class="run-weather-button"
            :loading="processingWeatherNow"
            :disabled="adminReadOnly || processingWeatherNow"
            @click="processWeatherNow"
          />
        </div>

        <div class="weather-pipeline">
          <div class="weather-stage">
            <div class="stage-title">
              <i class="pi pi-cog"></i>
              <span>Configuration</span>
            </div>
            <Tag :value="statusLabel" :severity="statusSeverity" />
            <dl>
              <div>
                <dt>Provider</dt>
                <dd>{{ providerLabel(status.provider) }}</dd>
              </div>
              <div>
                <dt>Requests today</dt>
                <dd>{{ status.requestsUsedToday || 0 }} / {{ status.dailyRequestLimit || 0 }}</dd>
              </div>
            </dl>
          </div>

          <div class="weather-stage">
            <div class="stage-title">
              <i class="pi pi-list-check"></i>
              <span>Historical range discovery</span>
            </div>
            <Tag :value="reconciliationBadge" :severity="reconciliationSeverity" />
            <dl>
              <div>
                <dt>Users/ranges waiting</dt>
                <dd>{{ reconciliation.pendingUserRanges || 0 }}</dd>
              </div>
              <div>
                <dt>Ready to inspect</dt>
                <dd>{{ reconciliation.eligibleUserRanges || 0 }}</dd>
              </div>
              <div>
                <dt>Oldest cursor</dt>
                <dd>{{ formatReconciliationDate(reconciliation.oldestCursorAt) }}</dd>
              </div>
              <div>
                <dt>Newest range end</dt>
                <dd>{{ formatReconciliationDate(reconciliation.newestRangeEnd) }}</dd>
              </div>
            </dl>
          </div>

          <div class="weather-stage">
            <div class="stage-title">
              <i class="pi pi-map-marker"></i>
              <span>Target discovery</span>
            </div>
            <Tag :value="discoveryBadge" :severity="discoverySeverity" />
            <dl>
              <div>
                <dt>Last trigger</dt>
                <dd>{{ lastDiscoveryRun?.trigger || 'None yet' }}</dd>
              </div>
              <div>
                <dt>Created / known / skipped</dt>
                <dd>{{ discoveryCountsText }}</dd>
              </div>
              <div>
                <dt>Last completed</dt>
                <dd>{{ formatDateTime(lastDiscoveryRun?.completedAt) }}</dd>
              </div>
            </dl>
          </div>

          <div class="weather-stage">
            <div class="stage-title">
              <i class="pi pi-cloud-download"></i>
              <span>Provider fetch queue</span>
            </div>
            <Tag :value="fetchBadge" :severity="fetchSeverity" />
            <dl>
              <div>
                <dt>Weather targets waiting</dt>
                <dd>{{ pendingTargets }}</dd>
              </div>
              <div>
                <dt>Ready to fetch</dt>
                <dd>{{ claimablePendingTargets }}</dd>
              </div>
              <div>
                <dt>Target time window</dt>
                <dd>{{ pendingTargetWindowText }}</dd>
              </div>
              <div>
                <dt>Last fetched</dt>
                <dd>{{ lastFetchRun?.fetchedTargets ?? 0 }}</dd>
              </div>
            </dl>
          </div>

          <div class="weather-stage weather-stage-wide">
            <div class="stage-title">
              <i class="pi pi-check-circle"></i>
              <span>Completed / deferred</span>
            </div>
            <Tag :value="providerHealthStatus" :severity="providerHealthSeverity" />
            <dl>
              <div>
                <dt>Fetch status</dt>
                <dd>{{ fetchStatusText }}</dd>
              </div>
              <div>
                <dt>Processing</dt>
                <dd>{{ processingStatusText }}</dd>
              </div>
              <div>
                <dt>Stored samples</dt>
                <dd>{{ status.samples || 0 }}</dd>
              </div>
              <div>
                <dt>Target outcomes</dt>
                <dd>{{ completedTargets }} completed, {{ skippedTargets }} skipped, {{ failedTargets }} failed</dd>
              </div>
              <div>
                <dt>Status refreshed</dt>
                <dd>{{ statusRefreshedText }}</dd>
              </div>
            </dl>
          </div>
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
import Select from 'primevue/select'
import Tag from 'primevue/tag'
import { storeToRefs } from 'pinia'
import SettingSection from '../SettingSection.vue'
import SettingItem from '../SettingItem.vue'
import { useAdminSettings } from '@/composables/useAdminSettings'
import { useAuthStore } from '@/stores/auth'
import apiService from '@/utils/apiService'
import { showDemoReadOnlyToast } from '@/utils/demoMode'
import { getPlaceholder as getPlaceholderHelper, parseSettingValue } from '@/utils/settingHelpers'

const toast = useToast()
const { loadSettings, resetSetting } = useAdminSettings()
const authStore = useAuthStore()
const { adminReadOnly } = storeToRefs(authStore)

const settings = ref([])
const originalSettings = ref([])
const status = ref({})
const hasUnsavedChanges = ref(false)
const isSaving = ref(false)
const testingConnection = ref(false)
const processingWeatherNow = ref(false)
const credentialDrafts = ref({})
const credentialEditModes = ref({})
const credentialCleared = ref({})
const statusRefreshedAt = ref(null)
let statusRefreshTimer = null

const providerOptions = [
  { label: 'Open-Meteo', value: 'OPEN_METEO', enabledKey: 'weather.open-meteo.enabled' },
  { label: 'Pirate Weather', value: 'PIRATE_WEATHER', enabledKey: 'weather.pirate.enabled' }
]
const providerKeys = [
  'weather.enabled',
  'weather.primary-provider',
  'weather.secondary-provider',
  'weather.open-meteo.enabled',
  'weather.open-meteo.forecast-url',
  'weather.open-meteo.archive-url',
  'weather.pirate.enabled',
  'weather.pirate.base-url',
  'weather.pirate.time-machine-url'
]
const credentialKeys = [
  'weather.open-meteo.api-key',
  'weather.pirate.api-key'
]
const samplingKeys = [
  'weather.ongoing.enabled',
  'weather.ongoing.interval-minutes',
  'weather.backfill.enabled',
  'weather.coordinate-precision',
  'weather.failed-target-retry.enabled',
  'weather.failed-target-retry.cooldown-hours'
]
const quotaAndProcessingKeys = [
  'weather.quota.daily-request-limit',
  'weather.quota.ongoing-reserve',
  'weather.backfill.discovery.chunks-per-run'
]

const getSetting = (key) => settings.value.find(setting => setting.key === key)
const getOriginalSetting = (key) => originalSettings.value.find(setting => setting.key === key)
const providerSettings = computed(() => providerKeys.map(getSetting).filter(Boolean))
const samplingSettings = computed(() => samplingKeys.map(getSetting).filter(Boolean))
const quotaAndProcessingSettings = computed(() => quotaAndProcessingKeys.map(getSetting).filter(Boolean))
const credentialSettings = computed(() => credentialKeys.map(getSetting).filter(Boolean))
const enabledProviderValues = computed(() =>
  providerOptions
    .filter(provider => getSetting(provider.enabledKey)?.currentValue === true)
    .map(provider => provider.value)
)
const primaryProviderOptions = computed(() =>
  providerOptions.filter(provider => enabledProviderValues.value.includes(provider.value))
)
const secondaryProviderOptions = computed(() => [
  { label: 'None', value: '' },
  ...primaryProviderOptions.value
])
const pendingTargets = computed(() => status.value?.targetsByStatus?.PENDING || 0)
const claimablePendingTargets = computed(() => status.value?.claimablePendingTargets || 0)
const fetchStatusText = computed(() => status.value?.fetchBlockedReason || 'Ready')
const reconciliation = computed(() => status.value?.reconciliation || {})
const processing = computed(() => status.value?.processing || {})
const lastDiscoveryRun = computed(() => status.value?.lastDiscoveryRun || null)
const lastFetchRun = computed(() => status.value?.lastFetchRun || null)
const providerHealth = computed(() => status.value?.providerHealth || null)
const providerHealthStatus = computed(() => providerHealth.value?.status || 'UNKNOWN')
const providerHealthSeverity = computed(() => {
  const healthStatus = providerHealth.value?.status
  if (healthStatus === 'HEALTHY') return 'success'
  if (healthStatus === 'PROVIDER_QUOTA_EXCEEDED' || healthStatus === 'INTERNAL_QUOTA_EXCEEDED') return 'warn'
  if (healthStatus === 'PROVIDER_UNAVAILABLE' || healthStatus === 'CONFIG_ERROR') return 'danger'
  return 'secondary'
})
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
const reconciliationBadge = computed(() => {
  if ((reconciliation.value.pendingUserRanges || 0) <= 0) return 'Clear'
  return `${reconciliation.value.pendingUserRanges} range${reconciliation.value.pendingUserRanges === 1 ? '' : 's'}`
})
const reconciliationSeverity = computed(() =>
  (reconciliation.value.pendingUserRanges || 0) > 0 ? 'warn' : 'success'
)
const discoveryBadge = computed(() => {
  if (processing.value?.running && String(processing.value.phase || '').includes('discover')) return 'Running'
  if (!lastDiscoveryRun.value) return 'No run'
  return lastDiscoveryRun.value.result || 'Done'
})
const discoverySeverity = computed(() => {
  if (processing.value?.running && String(processing.value.phase || '').includes('discover')) return 'info'
  if (!lastDiscoveryRun.value) return 'secondary'
  return lastDiscoveryRun.value.result === 'error' ? 'danger' : 'success'
})
const fetchBadge = computed(() => {
  if (processing.value?.running && String(processing.value.phase || '').includes('fetch')) return 'Running'
  if (claimablePendingTargets.value > 0) return 'Ready'
  if (pendingTargets.value > 0) return 'Deferred'
  return 'Idle'
})
const fetchSeverity = computed(() => {
  if (processing.value?.running && String(processing.value.phase || '').includes('fetch')) return 'info'
  if (claimablePendingTargets.value > 0) return 'warn'
  if (pendingTargets.value > 0) return 'warn'
  return 'success'
})
const pendingTargetWindowText = computed(() => {
  if (!status.value?.oldestPendingTargetAt || !status.value?.newestPendingTargetAt) return 'No pending targets'
  return `${formatDateTime(status.value.oldestPendingTargetAt)} - ${formatDateTime(status.value.newestPendingTargetAt)}`
})
const discoveryCountsText = computed(() => {
  const run = lastDiscoveryRun.value
  if (!run) return '0 / 0 / 0'
  return `${run.targetsCreated || 0} / ${run.targetsAlreadyKnown || 0} / ${run.targetsSkipped || 0}`
})
const processingStatusText = computed(() => {
  if (!processing.value?.running) {
    const waiting = processing.value?.waitingWorkers || 0
    return waiting > 0 ? `${waiting} waiting` : 'Idle'
  }
  const phase = processingPhaseText(processing.value.phase)
  const range = processing.value.rangeStart && processing.value.rangeEnd
    ? ` (${formatDateTime(processing.value.rangeStart)} - ${formatDateTime(processing.value.rangeEnd)})`
    : ''
  return `${phase}${range}`
})
const weatherSummarySeverity = computed(() => {
  if (!status.value?.enabled) return 'secondary'
  if (!status.value?.configured) return 'warn'
  if (processing.value?.running) return 'info'
  if (fetchStatusText.value !== 'Ready' && fetchStatusText.value !== 'No claimable pending weather targets') return 'warn'
  if ((reconciliation.value.pendingUserRanges || 0) > 0 || pendingTargets.value > 0) return 'info'
  return 'success'
})
const weatherSummaryText = computed(() => {
  if (!status.value?.enabled) return 'Weather integration is disabled.'
  if (!status.value?.configured) return 'Weather is enabled but the primary provider is not configured.'
  if (processing.value?.running) return processingStatusText.value
  if (claimablePendingTargets.value > 0) {
    return `${claimablePendingTargets.value} claimable weather target${claimablePendingTargets.value === 1 ? '' : 's'} ${pendingTargetWindowText.value === 'No pending targets' ? 'are ready to fetch' : `are ready to fetch for ${pendingTargetWindowText.value}`}.`
  }
  if ((reconciliation.value.pendingUserRanges || 0) > 0) {
    return `${reconciliation.value.pendingUserRanges} historical range${reconciliation.value.pendingUserRanges === 1 ? '' : 's'} waiting for target discovery. These are not provider requests yet.`
  }
  return 'Weather enrichment is caught up.'
})

const reloadSettings = async () => {
  settings.value = await loadSettings('weather')
  originalSettings.value = JSON.parse(JSON.stringify(settings.value))
  credentialDrafts.value = {}
  credentialCleared.value = {}
  credentialEditModes.value = {}
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

const processWeatherNow = async () => {
  if (adminReadOnly.value) {
    showDemoReadOnlyToast(toast)
    return
  }

  processingWeatherNow.value = true
  try {
    const response = await apiService.post('/admin/weather/process-now')
    const result = response?.data || response || {}
    toast.add({
      severity: result.result === 'already_running' ? 'warn' : result.result === 'error' ? 'error' : 'success',
      summary: result.result === 'already_running' ? 'Weather Already Running' : 'Weather Processed',
      detail: result.message || `Fetched ${result.fetchedTargets || 0} weather sample${result.fetchedTargets === 1 ? '' : 's'}`,
      life: 5000
    })
    await loadStatus()
  } catch (error) {
    const detail = error.response?.data?.message || error.message || 'Failed to process weather now'
    toast.add({
      severity: 'error',
      summary: 'Weather Processing Failed',
      detail,
      life: 5000
    })
  } finally {
    processingWeatherNow.value = false
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

const isUrlSetting = (setting) => setting.key.endsWith('.url') || setting.key.endsWith('-url')

const markDirty = () => {
  hasUnsavedChanges.value = true
}

const credentialLabel = (setting) => setting.key.includes('pirate') ? 'Pirate Weather API key' : 'Open-Meteo API key'

const credentialStored = (setting) =>
  !credentialCleared.value[setting.key] &&
  setting.currentValue != null &&
  String(setting.currentValue).trim() !== ''

const credentialDraftPresent = (key) =>
  credentialDrafts.value[key] != null &&
  String(credentialDrafts.value[key]).trim() !== ''

const credentialStateText = (setting) => {
  if (credentialDraftPresent(setting.key)) return 'New value ready to save'
  if (credentialCleared.value[setting.key]) return 'Will be cleared on save'
  if (credentialStored(setting)) return 'Saved'
  return 'Not set'
}

const toggleCredentialEdit = (setting) => {
  if (credentialEditModes.value[setting.key]) {
    const nextDrafts = { ...credentialDrafts.value }
    const nextModes = { ...credentialEditModes.value }
    delete nextDrafts[setting.key]
    delete nextModes[setting.key]
    credentialDrafts.value = nextDrafts
    credentialEditModes.value = nextModes
    return
  }
  credentialDrafts.value = { ...credentialDrafts.value, [setting.key]: '' }
  credentialEditModes.value = { ...credentialEditModes.value, [setting.key]: true }
}

const clearCredential = (setting) => {
  const nextDrafts = { ...credentialDrafts.value }
  const nextModes = { ...credentialEditModes.value }
  delete nextDrafts[setting.key]
  delete nextModes[setting.key]
  credentialDrafts.value = nextDrafts
  credentialEditModes.value = nextModes
  credentialCleared.value = { ...credentialCleared.value, [setting.key]: true }
  setting.currentValue = ''
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
      if (credentialDraftPresent(setting.key)) {
        changed.push({
          key: setting.key,
          value: credentialDrafts.value[setting.key]
        })
      } else if (credentialCleared.value[setting.key]) {
        const original = getOriginalSetting(setting.key)
        if (original?.currentValue) {
          changed.push({
            key: setting.key,
            value: ''
          })
        }
      }
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

  const chunksPerRun = Number(getSetting('weather.backfill.discovery.chunks-per-run')?.currentValue)
  if (Number.isFinite(chunksPerRun) && chunksPerRun < 1) {
    return 'Backfill chunks per run must be at least 1'
  }

  const primaryProvider = getSetting('weather.primary-provider')?.currentValue
  const secondaryProvider = getSetting('weather.secondary-provider')?.currentValue
  if (!primaryProvider) {
    return 'Primary provider is required'
  }
  if (!enabledProviderValues.value.includes(primaryProvider)) {
    return 'Primary provider must be enabled'
  }
  if (secondaryProvider) {
    if (secondaryProvider === primaryProvider) {
      return 'Secondary provider cannot match primary provider'
    }
    if (!enabledProviderValues.value.includes(secondaryProvider)) {
      return 'Secondary provider must be enabled'
    }
  }

  const pirateEnabled = getSetting('weather.pirate.enabled')?.currentValue === true
  const pirateKey = getSetting('weather.pirate.api-key')
  if (pirateEnabled && pirateKey && !credentialStored(pirateKey) && !credentialDraftPresent(pirateKey.key)) {
    return 'Pirate Weather requires an API key before it can be enabled'
  }

  return null
}

const saveAllChanges = async () => {
  if (adminReadOnly.value) {
    showDemoReadOnlyToast(toast)
    return
  }

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
  if (adminReadOnly.value) {
    showDemoReadOnlyToast(toast)
    return
  }

  testingConnection.value = true
  try {
    const response = await apiService.post('/admin/settings/weather/test')
    toast.add({
      severity: 'success',
      summary: 'Connection OK',
      detail: response.message || 'Weather provider endpoint is reachable',
      life: 3500
    })
  } catch (error) {
    const detail = error.response?.data?.message || error.message || 'Weather provider test failed'
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
  if (setting.key === 'weather.backfill.discovery.chunks-per-run') return 1
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

const providerLabel = (value) =>
  providerOptions.find(provider => provider.value === value)?.label || value || 'Unknown'

const processingPhaseText = (phase) => {
  switch (phase) {
    case 'discovering_import_targets':
      return 'Finding weather targets for the imported timeline'
    case 'fetching_import_weather':
      return 'Fetching weather for the imported timeline'
    case 'discovering_historical_targets':
      return 'Checking historical timeline ranges for missing weather'
    case 'fetching_discovered_weather':
      return 'Fetching newly discovered weather samples'
    case 'discovering_ongoing_targets':
      return 'Finding current weather targets'
    case 'fetching_weather':
      return 'Fetching weather samples'
    case 'probing_provider_health':
      return 'Checking weather provider health'
    case 'processing_admin_request':
      return 'Running weather processing now'
    default:
      return 'Weather processing is running'
  }
}

const formatDateTime = (value) => {
  if (!value) return 'None'
  try {
    return new Intl.DateTimeFormat(undefined, {
      dateStyle: 'medium',
      timeStyle: 'short'
    }).format(new Date(value))
  } catch (error) {
    return String(value)
  }
}

const formatReconciliationDate = (value) => {
  if (!value) return 'None'
  const date = new Date(value)
  if (Number.isFinite(date.getTime()) && date.getUTCFullYear() <= 1971) {
    return 'Before first timeline point'
  }
  return formatDateTime(value)
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

.weather-status-panel {
  margin: 0 1rem;
}

.weather-status-header {
  display: flex;
  align-items: stretch;
  justify-content: space-between;
  gap: 1rem;
  margin-bottom: 1rem;
}

.weather-summary-message {
  flex: 1;
  margin: 0;
}

:deep(.run-weather-button.p-button) {
  color: #ffffff;
  background: #2563eb;
  border-color: #2563eb;
  font-weight: 600;
  white-space: nowrap;
}

.weather-pipeline {
  display: grid;
  grid-template-columns: repeat(2, minmax(260px, 1fr));
  gap: 1rem;
}

.weather-stage {
  border: 1px solid var(--gp-border-light);
  border-radius: 6px;
  padding: 1rem;
  background: color-mix(in srgb, var(--surface-ground) 70%, transparent);
}

.weather-stage-wide {
  grid-column: 1 / -1;
}

.stage-title {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  margin-bottom: 0.75rem;
  font-weight: 800;
  color: var(--gp-text-primary);
}

.stage-title i {
  color: #2563eb;
}

.weather-stage dl {
  display: grid;
  gap: 0.65rem;
  margin: 0.85rem 0 0;
}

.weather-stage dl div {
  display: grid;
  grid-template-columns: minmax(130px, 1fr) minmax(0, 1.3fr);
  gap: 1rem;
}

.weather-stage dt {
  color: var(--gp-text-secondary);
}

.weather-stage dd {
  margin: 0;
  color: var(--gp-text-primary);
  font-weight: 700;
  text-align: right;
  overflow-wrap: anywhere;
}

.provider-select {
  width: 220px;
}

.url-input {
  width: min(56vw, 720px);
  min-width: 420px;
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

  .weather-status-panel {
    margin-left: 0.5rem;
    margin-right: 0.5rem;
  }

  .weather-status-header,
  .weather-pipeline {
    grid-template-columns: 1fr;
    flex-direction: column;
  }

  .weather-stage dl div {
    grid-template-columns: 1fr;
    gap: 0.25rem;
  }

  .weather-stage dd {
    text-align: left;
  }

  .url-input {
    width: 100%;
    min-width: 0;
  }
}
</style>
