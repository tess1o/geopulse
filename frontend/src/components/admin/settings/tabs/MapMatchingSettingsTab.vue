<template>
  <div>
    <div v-if="hasUnsavedChanges" class="save-actions">
      <Message severity="warn" :closable="false">You have unsaved changes</Message>
      <div class="buttons">
        <Button label="Discard Changes" severity="secondary" outlined :disabled="isSaving" @click="reloadSettings" />
        <Button label="Save Changes" icon="pi pi-save" :loading="isSaving" :disabled="adminReadOnly" @click="saveAllChanges" />
      </div>
    </div>

    <SettingSection title="Map Matching">
      <SettingItem v-for="setting in coreSettings" :key="setting.key" :setting="setting" @reset="handleReset(setting)">
        <template #control="{ setting }">
          <InputSwitch v-if="setting.valueType === 'BOOLEAN'" v-model="setting.currentValue" @change="markDirty" />
          <Select
            v-else-if="setting.key === 'map-matching.provider'"
            v-model="setting.currentValue"
            :options="providerOptions"
            optionLabel="label"
            optionValue="value"
            class="provider-select"
            @change="markDirty"
          />
        </template>
      </SettingItem>
    </SettingSection>

    <SettingSection title="Valhalla">
      <SettingItem v-for="setting in valhallaSettings" :key="setting.key" :setting="setting" @reset="handleReset(setting)">
        <template #control="{ setting }">
          <InputText
            v-if="setting.valueType === 'STRING'"
            v-model="setting.currentValue"
            class="url-input"
            placeholder="http://valhalla:8002"
            @input="markDirty"
          />
          <InputNumber
            v-else
            v-model="setting.currentValue"
            :min="1"
            :step="1"
            class="number-input"
            @update:modelValue="markDirty"
          />
        </template>
      </SettingItem>

      <div class="section-actions">
        <Button
          label="Test Valhalla Connection"
          icon="pi pi-bolt"
          :loading="testingConnection"
          :disabled="adminReadOnly"
          @click="testConnection"
        />
      </div>
    </SettingSection>

    <SettingSection title="Processing Limits">
      <SettingItem v-for="setting in limitSettings" :key="setting.key" :setting="setting" @reset="handleReset(setting)">
        <template #control="{ setting }">
          <InputNumber
            v-model="setting.currentValue"
            :min="1"
            :step="1"
            class="number-input"
            @update:modelValue="markDirty"
          />
        </template>
      </SettingItem>
    </SettingSection>

    <details class="advanced-settings">
      <summary>Advanced configuration</summary>
      <SettingSection title="Match Quality">
        <SettingItem v-for="setting in advancedSettings" :key="setting.key" :setting="setting" @reset="handleReset(setting)">
          <template #control="{ setting }">
            <InputNumber
              v-model="setting.currentValue"
              :min="1"
              :max="percentSettingKeys.has(setting.key) ? 100 : undefined"
              :step="1"
              class="number-input"
              @update:modelValue="markDirty"
            />
          </template>
        </SettingItem>
      </SettingSection>
    </details>

    <SettingSection title="Processing Status">
      <div class="status-card">
        <div class="status-header">
          <div>
            <Tag :value="workerState" :severity="workerSeverity" />
            <h3>{{ statusSummary }}</h3>
          </div>
          <div class="buttons">
            <Button label="Refresh" icon="pi pi-refresh" severity="secondary" outlined
              :loading="loadingStatus" @click="loadStatus" />
          </div>
        </div>

        <div v-if="showBackfillProgress" class="backfill-progress">
          <div class="progress-heading">
            <div>
              <strong>Historical backfill</strong>
              <span>{{ formatNumber(backfill.scannedTrips) }} / {{ formatNumber(backfill.totalTrips) }} trips inspected</span>
            </div>
            <strong>{{ formatPercent(backfill.percent) }}</strong>
          </div>
          <ProgressBar :value="backfillProgressValue" :showValue="false" />
          <div class="progress-caption">
            <span>{{ formatNumber(backfill.remainingTrips) }} trips remaining</span>
            <span>{{ formatNumber(backfill.completedUsers) }} / {{ formatNumber(backfill.totalUsers) }} users complete</span>
          </div>
        </div>

        <dl class="status-grid status-grid-primary">
          <div><dt>Queued</dt><dd>{{ formatNumber(queue.queued) }}</dd></div>
          <div><dt>Processing</dt><dd>{{ formatNumber(queue.processing) }}</dd></div>
          <div><dt>Last activity</dt><dd>{{ formatDateTime(worker.lastActivityAt) }}</dd></div>
        </dl>

        <Message v-if="worker.lastError" severity="warn" :closable="false">{{ worker.lastError }}</Message>

        <details class="status-diagnostics">
          <summary>Diagnostics</summary>
          <dl class="status-grid">
            <div><dt>Phase</dt><dd>{{ worker.phase || 'IDLE' }}</dd></div>
            <div><dt>Trigger</dt><dd>{{ worker.trigger || '—' }}</dd></div>
            <div><dt>Worker started</dt><dd>{{ formatDateTime(worker.startedAt) }}</dd></div>
            <div><dt>Last worker cycle</dt><dd>{{ formatDateTime(diagnostics.lastWorkerCycleCompletedAt) }}</dd></div>
            <div><dt>User histories remaining</dt><dd>{{ formatNumber(backfill.remainingUsers) }} / {{ formatNumber(backfill.totalUsers) }}</dd></div>
            <div><dt>Oldest queued target</dt><dd>{{ formatDateTime(queue.oldestQueuedAt) }}</dd></div>
          </dl>

          <div class="diagnostic-outcomes">
            <div class="diagnostic-outcomes-header">
              <h4>Stored cache records</h4>
              <span>All cache versions</span>
            </div>

            <div class="diagnostic-outcome-groups">
              <section class="outcome-group">
                <h5>By status</h5>
                <dl class="outcome-list">
                  <div v-for="(count, name) in diagnostics.targetsByStatus" :key="name" class="outcome-item">
                    <dt>{{ formatStatusName(name) }}</dt>
                    <dd>{{ formatNumber(count) }}</dd>
                  </div>
                </dl>
              </section>

              <section class="outcome-group">
                <h5>By source</h5>
                <dl class="outcome-list">
                  <div v-for="(count, name) in diagnostics.targetsBySource" :key="name" class="outcome-item">
                    <dt>{{ formatStatusName(name) }}</dt>
                    <dd>{{ formatNumber(count) }}</dd>
                  </div>
                </dl>
              </section>
            </div>
          </div>
        </details>
      </div>
    </SettingSection>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { storeToRefs } from 'pinia'
import { useToast } from 'primevue/usetoast'
import Button from 'primevue/button'
import InputNumber from 'primevue/inputnumber'
import InputSwitch from 'primevue/inputswitch'
import InputText from 'primevue/inputtext'
import Message from 'primevue/message'
import ProgressBar from 'primevue/progressbar'
import Tag from 'primevue/tag'
import Select from 'primevue/select'
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
const hasUnsavedChanges = ref(false)
const isSaving = ref(false)
const testingConnection = ref(false)
const loadingStatus = ref(false)
const status = ref({})
let statusRefreshTimer = null
let statusRequestInFlight = false

const providerOptions = [
  { label: 'Valhalla', value: 'valhalla' }
]

const coreKeys = [
  'map-matching.enabled',
  'map-matching.automatic.enabled',
  'map-matching.backfill.enabled',
  'map-matching.provider'
]
const valhallaKeys = [
  'map-matching.valhalla.base-url',
  'map-matching.valhalla.connect-timeout-seconds',
  'map-matching.valhalla.read-timeout-seconds'
]
const limitKeys = [
  'map-matching.automatic.quiet-period-minutes',
  'map-matching.max-input-points',
  'map-matching.max-trip-duration-hours',
  'map-matching.worker.batch-size',
  'map-matching.max-attempts'
]
const advancedKeys = [
  'map-matching.quality.min-raw-distance-meters',
  'map-matching.quality.min-distance-coverage-percent',
  'map-matching.quality.max-discontinuity-percent',
  'map-matching.quality.max-short-discontinuity-meters'
]
const percentSettingKeys = new Set([
  'map-matching.quality.min-distance-coverage-percent',
  'map-matching.quality.max-discontinuity-percent'
])

const getSetting = key => settings.value.find(setting => setting.key === key)
const getOriginalSetting = key => originalSettings.value.find(setting => setting.key === key)
const mapSettings = keys => keys.map(getSetting).filter(Boolean)
const coreSettings = computed(() => mapSettings(coreKeys))
const valhallaSettings = computed(() => mapSettings(valhallaKeys))
const limitSettings = computed(() => mapSettings(limitKeys))
const advancedSettings = computed(() => mapSettings(advancedKeys))
const worker = computed(() => status.value.worker || {})
const backfill = computed(() => status.value.backfill || {})
const queue = computed(() => status.value.queue || {})
const diagnostics = computed(() => status.value.diagnostics || {})
const workerState = computed(() => worker.value.running ? 'RUNNING' : worker.value.lastError ? 'BLOCKED' : 'IDLE')
const workerSeverity = computed(() => workerState.value === 'RUNNING' ? 'info' : workerState.value === 'BLOCKED' ? 'warn' : 'success')
const backfillProgressValue = computed(() => Math.min(100, Math.max(0, Number(backfill.value.percent) || 0)))
const showBackfillProgress = computed(() => backfill.value.enabled || Number(backfill.value.totalTrips) > 0)
const statusSummary = computed(() => {
  if (!status.value.enabled) return 'Map matching is disabled'
  if (!status.value.configured) return 'Valhalla is not configured'
  if (worker.value.running) return worker.value.phase === 'DISCOVERING' ? 'Discovering eligible trips' : 'Matching queued trips'
  if (worker.value.lastError) return 'Processing is blocked'
  if (!backfill.value.enabled && Number(backfill.value.remainingTrips) > 0) return 'Historical backfill is paused'
  if (Number(backfill.value.remainingTrips) > 0 || Number(queue.value.queued) > 0 || Number(queue.value.processing) > 0) return 'Work is queued'
  return 'Map matching is caught up'
})

const reloadSettings = async () => {
  settings.value = await loadSettings('map-matching')
  originalSettings.value = JSON.parse(JSON.stringify(settings.value))
  hasUnsavedChanges.value = false
}

const loadStatus = async (showLoading = true) => {
  if (statusRequestInFlight) return
  statusRequestInFlight = true
  if (showLoading) loadingStatus.value = true
  try {
    const response = await apiService.get('/admin/settings/map-matching/status')
    status.value = response?.data || response || {}
  } catch (error) {
    console.warn('Failed to load map-matching status:', error)
  } finally {
    statusRequestInFlight = false
    if (showLoading) loadingStatus.value = false
  }
}

const clearStatusRefresh = () => {
  if (statusRefreshTimer) {
    clearTimeout(statusRefreshTimer)
    statusRefreshTimer = null
  }
}

const scheduleStatusRefresh = () => {
  clearStatusRefresh()
  statusRefreshTimer = setTimeout(async () => {
    await loadStatus(false)
    scheduleStatusRefresh()
  }, worker.value.running ? 3000 : 15000)
}

const formatDateTime = value => value ? new Date(value).toLocaleString() : '—'
const formatNumber = value => new Intl.NumberFormat().format(Number(value) || 0)
const formatPercent = value => `${(Number(value) || 0).toFixed(1)}%`
const formatStatusName = value => String(value || '')
  .replaceAll('_', ' ')
  .toLowerCase()
  .replace(/\b\w/g, letter => letter.toUpperCase())

const markDirty = () => {
  hasUnsavedChanges.value = true
}

const buildChangedSettings = () => {
  const changed = []
  for (const setting of settings.value) {
    const original = getOriginalSetting(setting.key)
    if (!original || setting.currentValue !== original.currentValue) {
      changed.push({ key: setting.key, value: parseSettingValue(setting) })
    }
  }
  return changed
}

const validateChanges = () => {
  const provider = getSetting('map-matching.provider')?.currentValue
  if (provider !== 'valhalla') {
    return 'Map matching provider must be Valhalla'
  }

  const baseUrl = String(getSetting('map-matching.valhalla.base-url')?.currentValue || '').trim()
  if (baseUrl && !baseUrl.startsWith('http://') && !baseUrl.startsWith('https://')) {
    return 'Valhalla base URL must start with http:// or https://'
  }

  for (const setting of [...valhallaSettings.value, ...limitSettings.value, ...advancedSettings.value]) {
    if (setting.valueType === 'INTEGER' && Number(setting.currentValue) < 1) {
      return `${setting.label} must be at least 1`
    }
    if (percentSettingKeys.has(setting.key) && Number(setting.currentValue) > 100) {
      return `${setting.label} must be at most 100`
    }
  }
  return null
}

const saveAllChanges = async () => {
  if (adminReadOnly.value) return showDemoReadOnlyToast(toast)

  const validationError = validateChanges()
  if (validationError) {
    return toast.add({ severity: 'error', summary: 'Validation Error', detail: validationError, life: 4000 })
  }

  const changed = buildChangedSettings()
  if (!changed.length) {
    hasUnsavedChanges.value = false
    return
  }

  isSaving.value = true
  try {
    await apiService.post('/admin/settings/bulk', { settings: changed })
    toast.add({
      severity: 'success',
      summary: 'Settings Saved',
      detail: `Updated ${changed.length} setting${changed.length === 1 ? '' : 's'}`,
      life: 3000
    })
    await reloadSettings()
    await loadStatus()
  } catch (error) {
    toast.add({
      severity: 'error',
      summary: 'Save Failed',
      detail: error.response?.data?.message || error.message || 'Failed to save map matching settings',
      life: 5000
    })
  } finally {
    isSaving.value = false
  }
}

const handleReset = async setting => {
  await resetSetting(setting)
  await reloadSettings()
}

const testConnection = async () => {
  if (adminReadOnly.value) return showDemoReadOnlyToast(toast)
  testingConnection.value = true
  try {
    const response = await apiService.post('/admin/settings/map-matching/valhalla/test')
    toast.add({
      severity: 'success',
      summary: 'Connection OK',
      detail: response.message || 'Valhalla endpoint is reachable',
      life: 3500
    })
  } catch (error) {
    toast.add({
      severity: 'error',
      summary: 'Connection Failed',
      detail: error.response?.data?.message || error.message || 'Unable to reach Valhalla',
      life: 5000
    })
  } finally {
    testingConnection.value = false
  }
}

onMounted(async () => {
  await Promise.all([reloadSettings(), loadStatus()])
  scheduleStatusRefresh()
})

onBeforeUnmount(clearStatusRefresh)
</script>

<style scoped>
@import '../admin-settings-common.css';
.save-actions, .buttons, .section-actions { display: flex; align-items: center; gap: 0.75rem; }
.save-actions { justify-content: space-between; }
.section-actions { margin: 1rem; }
.advanced-settings { margin: 1rem 0; border: 1px solid var(--gp-border-light); border-radius: 6px; }
.advanced-settings summary { padding: 1rem; cursor: pointer; font-weight: 800; }
.provider-select { width: 220px; }
.number-input { width: 180px; }
.url-input { width: min(56vw, 720px); min-width: 420px; }
.status-card { margin: 0 1rem; padding: 1rem; display: grid; gap: 1rem; border: 1px solid var(--gp-border-light); border-radius: 6px; background: color-mix(in srgb, var(--surface-ground) 70%, transparent); }
.status-header { display: flex; align-items: center; justify-content: space-between; gap: 1rem; }
.status-header h3 { margin: 0.5rem 0 0; }
.backfill-progress { display: grid; gap: 0.65rem; padding: 1rem; border: 1px solid var(--surface-border); border-radius: 0.75rem; }
.progress-heading, .progress-caption { display: flex; justify-content: space-between; gap: 1rem; }
.progress-heading > div { display: grid; gap: 0.2rem; }
.progress-heading span, .progress-caption { color: var(--text-color-secondary); font-size: 0.85rem; }
.status-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(160px, 1fr)); gap: 1rem; margin: 0; }
.status-grid div { padding: 0.75rem; border-radius: 0.5rem; background: var(--surface-ground); }
.status-grid dt { color: var(--text-color-secondary); font-size: 0.8rem; }
.status-grid dd { margin: 0.3rem 0 0; font-weight: 600; }
.status-grid-primary { grid-template-columns: repeat(3, minmax(0, 1fr)); }
.status-diagnostics { border-top: 1px solid var(--surface-border); padding-top: 0.75rem; }
.status-diagnostics summary { cursor: pointer; color: var(--primary-color); font-weight: 600; }
.status-diagnostics[open] summary { margin-bottom: 1rem; }
.diagnostic-outcomes { margin-top: 1rem; padding-top: 1rem; border-top: 1px solid var(--surface-border); }
.diagnostic-outcomes-header { display: flex; align-items: baseline; justify-content: space-between; gap: 1rem; margin-bottom: 0.75rem; }
.diagnostic-outcomes-header h4 { margin: 0; }
.diagnostic-outcomes-header span { color: var(--text-color-secondary); font-size: 0.8rem; }
.diagnostic-outcome-groups { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 1rem; }
.outcome-group { min-width: 0; padding: 0.75rem; border: 1px solid var(--surface-border); border-radius: 0.5rem; background: var(--surface-ground); }
.outcome-group h5 { margin: 0 0 0.65rem; color: var(--text-color-secondary); font-size: 0.78rem; font-weight: 600; text-transform: uppercase; letter-spacing: 0.04em; }
.outcome-list { display: grid; grid-template-columns: repeat(auto-fit, minmax(120px, 1fr)); gap: 0.5rem; margin: 0; }
.outcome-item { display: flex; align-items: center; justify-content: space-between; gap: 0.75rem; min-width: 0; padding: 0.5rem 0.65rem; border: 1px solid var(--surface-border); border-radius: 999px; background: var(--surface-card); }
.outcome-item dt { overflow: hidden; color: var(--text-color-secondary); font-size: 0.8rem; text-overflow: ellipsis; white-space: nowrap; }
.outcome-item dd { margin: 0; color: var(--text-color); font-weight: 700; }
@media (max-width: 768px) {
  .save-actions { flex-direction: column; align-items: stretch; }
  .url-input { width: 100%; min-width: 0; }
  .status-header, .progress-heading, .progress-caption, .diagnostic-outcomes-header { align-items: flex-start; flex-direction: column; }
  .status-grid-primary { grid-template-columns: 1fr; }
  .diagnostic-outcome-groups { grid-template-columns: 1fr; }
}
</style>
