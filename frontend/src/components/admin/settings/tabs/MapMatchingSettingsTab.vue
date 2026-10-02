<template>
  <div>
    <div v-if="hasUnsavedChanges" class="save-actions">
      <Message severity="warn" :closable="false">{{ t('adminProviderSettings.shared.unsavedChanges') }}</Message>
      <div class="buttons">
        <Button :label="t('adminProviderSettings.shared.discardChanges')" severity="secondary" outlined :disabled="isSaving" @click="reloadSettings" />
        <Button :label="t('adminProviderSettings.shared.saveChanges')" icon="pi pi-save" :loading="isSaving" :disabled="adminReadOnly" @click="saveAllChanges" />
      </div>
    </div>

    <SettingSection :title="t('admin.settingsPage.tabs.mapMatching')">
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
          :label="t('adminProviderSettings.mapMatchingSettingsTab.testValhallaConnection')"
          icon="pi pi-bolt"
          :loading="testingConnection"
          :disabled="adminReadOnly"
          @click="testConnection"
        />
      </div>
    </SettingSection>

    <SettingSection :title="t('adminProviderSettings.mapMatchingSettingsTab.processingLimitsSectionTitle')">
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
      <summary>{{ t('adminProviderSettings.mapMatchingSettingsTab.advancedConfigurationSummary') }}</summary>
      <SettingSection :title="t('adminProviderSettings.mapMatchingSettingsTab.matchQualitySectionTitle')">
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

    <SettingSection :title="t('adminProviderSettings.mapMatchingSettingsTab.processingStatusSectionTitle')">
      <div class="status-card">
        <div class="status-header">
          <div>
            <Tag :value="workerState" :severity="workerSeverity" />
            <h3>{{ statusSummary }}</h3>
          </div>
          <div class="buttons">
            <Button
              :label="t('adminProviderSettings.mapMatchingSettingsTab.rerunMapMatching')"
              icon="pi pi-refresh"
              severity="secondary"
              outlined
              :loading="rerunningMapMatching"
              :disabled="rerunMapMatchingDisabled"
              @click="openRerunMapMatching()"
            />
            <Button :label="t('adminProviderSettings.shared.refresh')" icon="pi pi-refresh" severity="secondary" outlined
              :loading="loadingStatus" @click="loadStatus" />
          </div>
        </div>

        <div v-if="showBackfillProgress" class="backfill-progress">
          <div class="progress-heading">
            <div>
              <strong>{{ t('adminProviderSettings.mapMatchingSettingsTab.historicalBackfill') }}</strong>
              <span>{{ t('adminProviderSettings.mapMatchingSettingsTab.tripsInspected', { scanned: formatNumber(backfill.scannedTrips), total: formatNumber(backfill.totalTrips) }) }}</span>
            </div>
            <strong>{{ formatPercent(backfill.percent) }}</strong>
          </div>
          <ProgressBar :value="backfillProgressValue" :showValue="false" />
          <div class="progress-caption">
            <span>{{ t('adminProviderSettings.mapMatchingSettingsTab.tripsRemaining', { count: formatNumber(backfill.remainingTrips) }) }}</span>
            <span>{{ t('adminProviderSettings.mapMatchingSettingsTab.usersComplete', { completed: formatNumber(backfill.completedUsers), total: formatNumber(backfill.totalUsers) }) }}</span>
          </div>
        </div>

        <dl class="status-grid status-grid-primary">
          <div><dt>{{ t('adminProviderSettings.mapMatchingSettingsTab.queued') }}</dt><dd>{{ formatNumber(queue.queued) }}</dd></div>
          <div><dt>{{ t('adminProviderSettings.mapMatchingSettingsTab.processing') }}</dt><dd>{{ formatNumber(queue.processing) }}</dd></div>
          <div><dt>{{ t('adminProviderSettings.mapMatchingSettingsTab.scheduledRanges') }}</dt><dd>{{ formatNumber(pendingReconciliationCount) }}</dd></div>
          <div><dt>{{ t('adminProviderSettings.mapMatchingSettingsTab.nextScan') }}</dt><dd>{{ formatDateTime(diagnostics.nextReconciliationEligibleAt) }}</dd></div>
          <div><dt>{{ t('adminProviderSettings.mapMatchingSettingsTab.lastActivity') }}</dt><dd>{{ formatDateTime(worker.lastActivityAt) }}</dd></div>
        </dl>

        <Message v-if="worker.lastError" severity="warn" :closable="false">{{ worker.lastError }}</Message>

        <Message v-if="pendingRerunHint" severity="info" :closable="false">
          {{ t('adminProviderSettings.mapMatchingSettingsTab.pendingRerunHint') }}
        </Message>

        <details class="status-diagnostics">
          <summary>{{ t('adminProviderSettings.mapMatchingSettingsTab.diagnosticsSummary') }}</summary>
          <dl class="status-grid">
            <div><dt>{{ t('adminProviderSettings.mapMatchingSettingsTab.phase') }}</dt><dd>{{ worker.phase || 'IDLE' }}</dd></div>
            <div><dt>{{ t('adminProviderSettings.mapMatchingSettingsTab.trigger') }}</dt><dd>{{ worker.trigger || '—' }}</dd></div>
            <div><dt>{{ t('adminProviderSettings.mapMatchingSettingsTab.workerStarted') }}</dt><dd>{{ formatDateTime(worker.startedAt) }}</dd></div>
            <div><dt>{{ t('adminProviderSettings.mapMatchingSettingsTab.lastWorkerCycle') }}</dt><dd>{{ formatDateTime(diagnostics.lastWorkerCycleCompletedAt) }}</dd></div>
            <div><dt>{{ t('adminProviderSettings.mapMatchingSettingsTab.userHistoriesRemaining') }}</dt><dd>{{ formatNumber(backfill.remainingUsers) }} / {{ formatNumber(backfill.totalUsers) }}</dd></div>
            <div><dt>{{ t('adminProviderSettings.mapMatchingSettingsTab.pendingReconciliations') }}</dt><dd>{{ formatNumber(pendingReconciliationCount) }}</dd></div>
            <div><dt>{{ t('adminProviderSettings.mapMatchingSettingsTab.oldestQueuedTarget') }}</dt><dd>{{ formatDateTime(queue.oldestQueuedAt) }}</dd></div>
          </dl>

          <div class="diagnostic-outcomes">
            <div class="diagnostic-outcomes-header">
              <h4>{{ t('adminProviderSettings.mapMatchingSettingsTab.storedCacheRecords') }}</h4>
              <span>{{ t('adminProviderSettings.mapMatchingSettingsTab.allCacheVersions') }}</span>
            </div>

            <div class="diagnostic-outcome-groups">
              <section class="outcome-group">
                <h5>{{ t('adminProviderSettings.mapMatchingSettingsTab.byStatus') }}</h5>
                <dl class="outcome-list">
                  <div v-for="(count, name) in diagnostics.targetsByStatus" :key="name" class="outcome-item">
                    <dt>{{ formatStatusName(name) }}</dt>
                    <dd>{{ formatNumber(count) }}</dd>
                  </div>
                </dl>
              </section>

              <section class="outcome-group">
                <h5>{{ t('adminProviderSettings.mapMatchingSettingsTab.bySource') }}</h5>
                <dl class="outcome-list">
                  <div v-for="(count, name) in diagnostics.targetsBySource" :key="name" class="outcome-item">
                    <dt>{{ formatStatusName(name) }}</dt>
                    <dd>{{ formatNumber(count) }}</dd>
                  </div>
                </dl>
              </section>

              <section class="outcome-group">
                <h5>{{ t('adminProviderSettings.mapMatchingSettingsTab.pendingRangesGroup') }}</h5>
                <dl class="outcome-list">
                  <div v-for="(count, name) in diagnostics.pendingReconciliationsBySource" :key="name" class="outcome-item">
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

    <Dialog
      v-model:visible="rerunPromptVisible"
      :header="t('adminProviderSettings.mapMatchingSettingsTab.rerunPromptHeader')"
      :modal="true"
      :style="{ width: '32rem' }"
    >
      <div class="rerun-prompt">
        <i class="pi pi-info-circle"></i>
        <p>
          {{ t('adminProviderSettings.mapMatchingSettingsTab.rerunPromptText') }}
        </p>
      </div>
      <template #footer>
        <Button :label="t('adminProviderSettings.mapMatchingSettingsTab.later')" severity="secondary" text @click="rerunPromptVisible = false" />
        <Button :label="t('adminProviderSettings.mapMatchingSettingsTab.rerunNow')" icon="pi pi-refresh" @click="openRerunFromPrompt" />
      </template>
    </Dialog>

    <Dialog
      v-model:visible="rerunMapMatchingDialogVisible"
      :header="t('adminProviderSettings.mapMatchingSettingsTab.rerunDialogHeader')"
      :modal="true"
      :style="{ width: '34rem' }"
    >
      <div class="rerun-options">
        <div
          v-for="option in rerunModeOptions"
          :key="option.value"
          class="rerun-option"
          :class="{ selected: rerunMode === option.value }"
        >
          <RadioButton
            v-model="rerunMode"
            :inputId="`rerun-${option.value}`"
            :value="option.value"
            class="rerun-radio"
          />
          <label :for="`rerun-${option.value}`" class="rerun-info">
            <span class="rerun-label">{{ option.label }}</span>
            <p class="rerun-description">{{ option.description }}</p>
          </label>
        </div>
      </div>

      <Message v-if="rerunMode === 'ALL'" severity="warn" :closable="false">
        {{ t('adminProviderSettings.mapMatchingSettingsTab.rerunAllWarning') }}
      </Message>
      <template #footer>
        <Button :label="t('common.cancel')" icon="pi pi-times" text :disabled="rerunningMapMatching"
          @click="rerunMapMatchingDialogVisible = false" />
        <Button
          :label="t('adminProviderSettings.mapMatchingSettingsTab.rerun')"
          icon="pi pi-refresh"
          :severity="rerunMode === 'ALL' ? 'danger' : 'primary'"
          :loading="rerunningMapMatching"
          @click="rerunMapMatching"
        />
      </template>
    </Dialog>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { storeToRefs } from 'pinia'
import { useToast } from 'primevue/usetoast'
import Button from 'primevue/button'
import Dialog from 'primevue/dialog'
import InputNumber from 'primevue/inputnumber'
import InputSwitch from 'primevue/inputswitch'
import InputText from 'primevue/inputtext'
import Message from 'primevue/message'
import ProgressBar from 'primevue/progressbar'
import RadioButton from 'primevue/radiobutton'
import Tag from 'primevue/tag'
import Select from 'primevue/select'
import SettingSection from '../SettingSection.vue'
import SettingItem from '../SettingItem.vue'
import { useAdminSettings } from '@/composables/useAdminSettings'
import { useAuthStore } from '@/stores/auth'
import { useAdminStore } from '@/stores/admin'
import { showDemoReadOnlyToast } from '@/utils/demoMode'
import { parseSettingValue } from '@/utils/settingHelpers'
import { affectsMapMatchingCache } from '@/utils/mapMatchingSettings'
import { formatApiErrorDetail } from '@/utils/apiErrorDetail'

const { t } = useI18n()
const toast = useToast()
const { loadSettings, resetSetting } = useAdminSettings()
const { adminReadOnly } = storeToRefs(useAuthStore())
const adminStore = useAdminStore()

const settings = ref([])
const originalSettings = ref([])
const hasUnsavedChanges = ref(false)
const isSaving = ref(false)
const testingConnection = ref(false)
const loadingStatus = ref(false)
const rerunningMapMatching = ref(false)
const rerunMapMatchingDialogVisible = ref(false)
const rerunPromptVisible = ref(false)
const rerunMode = ref('UNSUCCESSFUL')
const pendingRerunHint = ref(false)
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
const pendingReconciliationCount = computed(() => Number(diagnostics.value.pendingReconciliations) || 0)
const hasPendingReconciliations = computed(() => pendingReconciliationCount.value > 0)
const canRerunMapMatching = computed(() =>
  status.value.enabled && status.value.configured && backfill.value.enabled
)
const rerunMapMatchingDisabled = computed(() =>
  adminReadOnly.value || rerunningMapMatching.value || worker.value.running || !canRerunMapMatching.value
)
const rerunModeOptions = computed(() => [
  {
    value: 'UNSUCCESSFUL',
    label: t('adminProviderSettings.mapMatchingSettingsTab.rerunModeOptions.unsuccessfulLabel'),
    description: t('adminProviderSettings.mapMatchingSettingsTab.rerunModeOptions.unsuccessfulDescription')
  },
  {
    value: 'ALL',
    label: t('adminProviderSettings.mapMatchingSettingsTab.rerunModeOptions.allLabel', { count: formatNumber(backfill.value.totalTrips) }),
    description: t('adminProviderSettings.mapMatchingSettingsTab.rerunModeOptions.allDescription')
  }
])
const waitingForQuietPeriod = computed(() => {
  if (!hasPendingReconciliations.value || !diagnostics.value.nextReconciliationEligibleAt) return false
  return new Date(diagnostics.value.nextReconciliationEligibleAt).getTime() > Date.now()
})
const workerState = computed(() => {
  if (worker.value.running) return 'RUNNING'
  if (worker.value.lastError) return 'BLOCKED'
  if (hasPendingReconciliations.value) return waitingForQuietPeriod.value ? 'SCHEDULED' : 'QUEUED'
  return 'IDLE'
})
const workerSeverity = computed(() => {
  if (workerState.value === 'RUNNING' || workerState.value === 'SCHEDULED' || workerState.value === 'QUEUED') return 'info'
  if (workerState.value === 'BLOCKED') return 'warn'
  return 'success'
})
const backfillProgressValue = computed(() => Math.min(100, Math.max(0, Number(backfill.value.percent) || 0)))
const showBackfillProgress = computed(() => backfill.value.enabled || Number(backfill.value.totalTrips) > 0)
const statusSummary = computed(() => {
  if (!status.value.enabled) return t('adminProviderSettings.mapMatchingSettingsTab.statusSummary.disabled')
  if (!status.value.configured) return t('adminProviderSettings.mapMatchingSettingsTab.statusSummary.notConfigured')
  if (worker.value.running) return worker.value.phase === 'DISCOVERING' ? t('adminProviderSettings.mapMatchingSettingsTab.statusSummary.discoveringEligible') : t('adminProviderSettings.mapMatchingSettingsTab.statusSummary.matchingQueued')
  if (worker.value.lastError) return t('adminProviderSettings.mapMatchingSettingsTab.statusSummary.blocked')
  if (!backfill.value.enabled && Number(backfill.value.remainingTrips) > 0) return t('adminProviderSettings.mapMatchingSettingsTab.statusSummary.backfillPaused')
  if (waitingForQuietPeriod.value) return t('adminProviderSettings.mapMatchingSettingsTab.statusSummary.workScheduled')
  if (hasPendingReconciliations.value) return t('adminProviderSettings.mapMatchingSettingsTab.statusSummary.discoveringEligible')
  if (Number(backfill.value.remainingTrips) > 0 || Number(queue.value.queued) > 0 || Number(queue.value.processing) > 0) return t('adminProviderSettings.mapMatchingSettingsTab.statusSummary.workQueued')
  return t('adminProviderSettings.mapMatchingSettingsTab.statusSummary.caughtUp')
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
    status.value = await adminStore.getMapMatchingStatus() || {}
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
    return t('adminProviderSettings.mapMatchingSettingsTab.validation.providerMustBeValhalla')
  }

  const baseUrl = String(getSetting('map-matching.valhalla.base-url')?.currentValue || '').trim()
  if (baseUrl && !baseUrl.startsWith('http://') && !baseUrl.startsWith('https://')) {
    return t('adminProviderSettings.mapMatchingSettingsTab.validation.baseUrlProtocol')
  }

  for (const setting of [...valhallaSettings.value, ...limitSettings.value, ...advancedSettings.value]) {
    if (setting.valueType === 'INTEGER' && Number(setting.currentValue) < 1) {
      return t('adminProviderSettings.mapMatchingSettingsTab.validation.atLeastOne', { label: setting.label })
    }
    if (percentSettingKeys.has(setting.key) && Number(setting.currentValue) > 100) {
      return t('adminProviderSettings.mapMatchingSettingsTab.validation.atMost100', { label: setting.label })
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
    await adminStore.bulkUpdateSettings(changed)
    toast.add({
      severity: 'success',
      summary: 'Settings Saved',
      detail: `Updated ${changed.length} setting${changed.length === 1 ? '' : 's'}`,
      life: 3000
    })
    if (affectsMapMatchingCache(changed.map(setting => setting.key))) {
      promptForRerun()
    }
    await reloadSettings()
    await loadStatus()
  } catch (error) {
    toast.add({
      severity: 'error',
      summary: 'Save Failed',
      detail: formatApiErrorDetail(error, 'Failed to save map matching settings'),
      life: 5000
    })
  } finally {
    isSaving.value = false
  }
}

const handleReset = async setting => {
  await resetSetting(setting)
  if (affectsMapMatchingCache([setting.key])) {
    promptForRerun()
  }
  await reloadSettings()
}

const testConnection = async () => {
  if (adminReadOnly.value) return showDemoReadOnlyToast(toast)
  testingConnection.value = true
  try {
    const response = await adminStore.testValhallaConnection()
    toast.add({
      severity: 'success',
      summary: 'Connection OK',
      detail: response.success ? 'Valhalla endpoint is reachable' : (response.detail || 'Unable to reach Valhalla'),
      life: 3500
    })
  } catch (error) {
    toast.add({
      severity: 'error',
      summary: 'Connection Failed',
      detail: formatApiErrorDetail(error, 'Unable to reach Valhalla'),
      life: 5000
    })
  } finally {
    testingConnection.value = false
  }
}

// Settings that change the cache key do not re-match history on their own; ask the admin instead of
// silently queueing the whole history from a form save. Declining keeps the reminder in the status card.
const promptForRerun = () => {
  pendingRerunHint.value = true
  rerunPromptVisible.value = true
}

// Applying new settings to trips that are already matched needs the ALL mode, so preselect it here; the
// admin can still switch to retrying failures only.
const openRerunFromPrompt = () => {
  rerunPromptVisible.value = false
  openRerunMapMatching('ALL')
}

const openRerunMapMatching = (mode = 'UNSUCCESSFUL') => {
  if (adminReadOnly.value) return showDemoReadOnlyToast(toast)
  rerunMode.value = mode
  rerunMapMatchingDialogVisible.value = true
}

const rerunMapMatching = async () => {
  if (adminReadOnly.value) return showDemoReadOnlyToast(toast)
  rerunningMapMatching.value = true
  const mode = rerunMode.value
  try {
    const data = await adminStore.rebuildMapMatching(mode)
    rerunMapMatchingDialogVisible.value = false
    pendingRerunHint.value = false
    toast.add({
      severity: 'success',
      summary: 'Map Matching Re-run',
      detail: mode === 'ALL'
        ? `Cleared ${formatNumber(data.affectedTargets)} cached matches; re-scanning `
          + `${formatNumber(data.queuedUsers)} user histories`
        : `Re-queued ${formatNumber(data.affectedTargets)} failed or skipped trips across `
          + `${formatNumber(data.queuedUsers)} user histories`,
      life: 4000
    })
    await loadStatus()
  } catch (error) {
    toast.add({
      severity: 'error',
      summary: 'Re-run Failed',
      detail: formatApiErrorDetail(error, 'Failed to re-run map matching'),
      life: 5000
    })
  } finally {
    rerunningMapMatching.value = false
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
.advanced-settings { margin: 1rem 0; border: 1px solid var(--gp-border); border-radius: 6px; }
.advanced-settings summary { padding: 1rem; cursor: pointer; font-weight: 800; }
.provider-select { width: 220px; }
.number-input { width: 180px; }
.url-input { width: min(56vw, 720px); min-width: 420px; }
.status-card { margin: 0 1rem; padding: 1rem; display: grid; gap: 1rem; border: 1px solid var(--gp-border); border-radius: 6px; background: color-mix(in srgb, var(--gp-surface-ground) 70%, transparent); }
.status-header { display: flex; align-items: center; justify-content: space-between; gap: 1rem; }
.status-header h3 { margin: 0.5rem 0 0; }
.backfill-progress { display: grid; gap: 0.65rem; padding: 1rem; border: 1px solid var(--gp-border); border-radius: 0.75rem; }
.progress-heading, .progress-caption { display: flex; justify-content: space-between; gap: 1rem; }
.progress-heading > div { display: grid; gap: 0.2rem; }
.progress-heading span, .progress-caption { color: var(--gp-text-secondary); font-size: 0.85rem; }
.status-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(160px, 1fr)); gap: 1rem; margin: 0; }
.status-grid div { padding: 0.75rem; border-radius: 0.5rem; background: var(--gp-surface-ground); }
.status-grid dt { color: var(--gp-text-secondary); font-size: 0.8rem; }
.status-grid dd { margin: 0.3rem 0 0; font-weight: 600; }
.status-grid-primary { grid-template-columns: repeat(3, minmax(0, 1fr)); }
.status-diagnostics { border-top: 1px solid var(--gp-border); padding-top: 0.75rem; }
.status-diagnostics summary { cursor: pointer; color: var(--gp-primary); font-weight: 600; }
.status-diagnostics[open] summary { margin-bottom: 1rem; }
.diagnostic-outcomes { margin-top: 1rem; padding-top: 1rem; border-top: 1px solid var(--gp-border); }
.diagnostic-outcomes-header { display: flex; align-items: baseline; justify-content: space-between; gap: 1rem; margin-bottom: 0.75rem; }
.diagnostic-outcomes-header h4 { margin: 0; }
.diagnostic-outcomes-header span { color: var(--gp-text-secondary); font-size: 0.8rem; }
.diagnostic-outcome-groups { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 1rem; }
.outcome-group { min-width: 0; padding: 0.75rem; border: 1px solid var(--gp-border); border-radius: 0.5rem; background: var(--gp-surface-ground); }
.outcome-group h5 { margin: 0 0 0.65rem; color: var(--gp-text-secondary); font-size: 0.78rem; font-weight: 600; text-transform: uppercase; letter-spacing: 0.04em; }
.outcome-list { display: grid; grid-template-columns: repeat(auto-fit, minmax(120px, 1fr)); gap: 0.5rem; margin: 0; }
.outcome-item { display: flex; align-items: center; justify-content: space-between; gap: 0.75rem; min-width: 0; padding: 0.5rem 0.65rem; border: 1px solid var(--gp-border); border-radius: 999px; background: var(--gp-surface-card); }
.outcome-item dt { overflow: hidden; color: var(--gp-text-secondary); font-size: 0.8rem; text-overflow: ellipsis; white-space: nowrap; }
.outcome-item dd { margin: 0; color: var(--gp-text-primary); font-weight: 700; }
.rerun-prompt { display: flex; gap: 0.75rem; align-items: flex-start; }
.rerun-prompt > i { color: var(--gp-primary); font-size: 1.25rem; }
.rerun-prompt p { margin: 0; line-height: 1.5; }
.rerun-options { display: grid; gap: 0.75rem; margin-bottom: 1rem; }
.rerun-option { display: flex; gap: 0.75rem; align-items: flex-start; padding: 0.85rem; border: 1px solid var(--gp-border); border-radius: 0.5rem; cursor: pointer; }
.rerun-option.selected { border-color: var(--gp-primary); background: color-mix(in srgb, var(--gp-primary) 6%, transparent); }
.rerun-radio { margin-top: 0.15rem; }
.rerun-info { display: grid; gap: 0.2rem; cursor: pointer; }
.rerun-label { font-weight: 600; }
.rerun-description { margin: 0; color: var(--gp-text-secondary); font-size: 0.85rem; line-height: 1.4; }
@media (max-width: 768px) {
  .save-actions { flex-direction: column; align-items: stretch; }
  .url-input { width: 100%; min-width: 0; }
  .status-header, .progress-heading, .progress-caption, .diagnostic-outcomes-header { align-items: flex-start; flex-direction: column; }
  .status-grid-primary { grid-template-columns: 1fr; }
  .diagnostic-outcome-groups { grid-template-columns: 1fr; }
}
</style>
