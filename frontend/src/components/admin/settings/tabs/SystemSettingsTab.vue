<template>
  <div>
    <SettingSection v-if="baseSystemSettings.length > 0" :title="t('admin.settingsPage.tabs.system')">
      <SettingItem
        v-for="setting in baseSystemSettings"
        :key="setting.key"
        :setting="setting"
        @reset="handleReset(setting)"
      >
        <template #control="{ setting }">
          <Select
            v-if="setting.key === 'system.user.default-distance-unit'"
            v-model="setting.currentValue"
            :options="distanceUnitOptions"
            optionLabel="label"
            optionValue="value"
            :placeholder="t('adminSettings.systemTab.selectDefaultUnit')"
            @change="handleUpdate(setting)"
            style="width: 260px"
          />
          <Select
            v-else-if="setting.key === 'system.user.default-temperature-unit'"
            v-model="setting.currentValue"
            :options="temperatureUnitOptions"
            optionLabel="label"
            optionValue="value"
            :placeholder="t('adminSettings.systemTab.selectDefaultUnit')"
            @change="handleUpdate(setting)"
            style="width: 260px"
          />
          <InputSwitch
            v-else-if="setting.valueType === 'BOOLEAN'"
            v-model="setting.currentValue"
            @change="handleUpdate(setting)"
          />
          <InputNumber
            v-else-if="setting.valueType === 'INTEGER'"
            v-model="setting.currentValue"
            :min="0"
            @update:modelValue="handleUpdate(setting)"
            style="width: 180px"
          />
          <InputText
            v-else
            v-model="setting.currentValue"
            @change="handleUpdate(setting)"
            style="width: 300px"
          />
        </template>
      </SettingItem>
    </SettingSection>

    <details v-if="loggingSetting" class="advanced-settings" open>
      <summary>{{ t('adminSettings.systemTab.observability') }}</summary>
      <SettingSection :title="t('adminSettings.systemTab.applicationLogging')">
        <SettingItem
          :setting="loggingSetting"
          :reset-label="t('adminSettings.systemTab.useEnvironmentDefault')"
          @reset="handleLoggingReset"
        >
          <template #control="{ setting }">
            <Select
              v-model="setting.currentValue"
              :options="logLevelOptions"
              :placeholder="t('adminSettings.systemTab.selectLogLevel')"
              @change="handleLoggingUpdate(setting)"
              style="width: 220px"
            />
          </template>
        </SettingItem>
        <div v-if="loggingStatus" class="logging-status">
          {{ t('adminSettings.systemTab.configuredLabel') }} <strong>{{ loggingStatus.configuredLevel }}</strong> ·
          {{ t('adminSettings.systemTab.effectiveLabel') }} <strong>{{ loggingStatus.effectiveLevel }}</strong> ·
          {{ t('adminSettings.systemTab.sourceLabel') }} <strong>{{ loggingStatus.source }}</strong>
        </div>
        <Message v-if="loggingStatus?.effectiveLevel === 'DEBUG'" severity="warn" :closable="false">
          {{ t('adminSettings.systemTab.debugLoggingWarning') }}
        </Message>
      </SettingSection>
    </details>

    <details v-if="updateCheckSettings.length > 0" class="advanced-settings">
      <summary>{{ t('adminSettings.systemTab.updateCheck') }}</summary>
      <SettingSection :title="t('adminSettings.systemTab.releaseMetadata')">
        <SettingItem
          v-for="setting in updateCheckSettings"
          :key="setting.key"
          :setting="setting"
          @reset="handleReset(setting)"
        >
          <template #control="{ setting }">
            <InputSwitch
              v-if="setting.valueType === 'BOOLEAN'"
              v-model="setting.currentValue"
              @change="handleUpdate(setting)"
            />
            <InputNumber
              v-else-if="setting.valueType === 'INTEGER'"
              v-model="setting.currentValue"
              :min="1"
              @update:modelValue="handleUpdate(setting)"
              style="width: 180px"
            />
            <InputText
              v-else
              v-model="setting.currentValue"
              @change="handleUpdate(setting)"
              class="url-input"
            />
          </template>
        </SettingItem>
      </SettingSection>
    </details>

    <details v-if="waterDatasetSettings.length > 0" class="advanced-settings">
      <summary>{{ t('adminSettings.systemTab.waterDataset') }}</summary>
      <SettingSection :title="t('adminSettings.systemTab.datasetSource')">
        <SettingItem
          v-for="setting in waterDatasetSettings"
          :key="setting.key"
          :setting="setting"
          @reset="handleReset(setting)"
        >
          <template #control="{ setting }">
            <InputSwitch
              v-if="setting.valueType === 'BOOLEAN'"
              v-model="setting.currentValue"
              @change="handleUpdate(setting)"
            />
            <InputNumber
              v-else-if="setting.valueType === 'INTEGER'"
              v-model="setting.currentValue"
              :min="1"
              @update:modelValue="handleUpdate(setting)"
              style="width: 180px"
            />
            <InputText
              v-else
              v-model="setting.currentValue"
              @change="handleUpdate(setting)"
              class="url-input"
            />
          </template>
        </SettingItem>
      </SettingSection>
    </details>

    <div v-if="systemSettings.length === 0" class="empty-state">
      <div class="empty-state-icon">
        <i class="pi pi-cog" style="font-size: 2rem; color: var(--text-color-secondary);" />
      </div>
      <h3>{{ t('adminSettings.systemTab.noSettingsTitle') }}</h3>
      <p class="text-muted">{{ t('adminSettings.systemTab.noSettingsDescription') }}</p>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import InputSwitch from 'primevue/inputswitch'
import InputNumber from 'primevue/inputnumber'
import InputText from 'primevue/inputtext'
import Select from 'primevue/select'
import Message from 'primevue/message'
import SettingSection from '../SettingSection.vue'
import SettingItem from '../SettingItem.vue'
import { useAdminSettings } from '@/composables/useAdminSettings'
import { useAdminStore } from '@/stores/admin'
const { t } = useI18n()
const { loadSettings, updateSetting, resetSetting } = useAdminSettings()
const adminStore = useAdminStore()

const systemSettings = ref([])
const distanceUnitOptions = computed(() => [
  { label: t('adminSettings.unitOptions.distanceKilometers'), value: 'KILOMETERS' },
  { label: t('adminSettings.unitOptions.distanceMiles'), value: 'MILES' }
])
const temperatureUnitOptions = computed(() => [
  { label: t('adminSettings.unitOptions.temperatureCelsius'), value: 'CELSIUS' },
  { label: t('adminSettings.unitOptions.temperatureFahrenheit'), value: 'FAHRENHEIT' }
])
const logLevelOptions = ['ERROR', 'WARN', 'INFO', 'DEBUG']
const loggingStatus = ref(null)

const updateCheckKeys = [
  'system.version-check.github-api-url',
  'system.version-check.release-url',
  'system.version-check.cache-ttl-minutes',
  'system.version-check.connect-timeout-seconds',
  'system.version-check.read-timeout-seconds'
]
const waterDatasetKeys = [
  'system.water-dataset.url',
  'system.water-dataset.sha256',
  'system.water-dataset.auto-import',
  'system.water-dataset.connect-timeout-seconds',
  'system.water-dataset.download-timeout-hours',
  'system.water-dataset.download-stall-timeout-seconds',
  'system.water-dataset.setup-start-timeout-minutes'
]

const baseSystemSettings = computed(() =>
  systemSettings.value.filter(setting =>
    !updateCheckKeys.includes(setting.key) &&
    !waterDatasetKeys.includes(setting.key) &&
    setting.key !== 'system.logging.application-level'
  )
)
const updateCheckSettings = computed(() =>
  updateCheckKeys.map(key => systemSettings.value.find(setting => setting.key === key)).filter(Boolean)
)
const waterDatasetSettings = computed(() =>
  waterDatasetKeys.map(key => systemSettings.value.find(setting => setting.key === key)).filter(Boolean)
)
const loggingSetting = computed(() =>
  systemSettings.value.find(setting => setting.key === 'system.logging.application-level')
)

const reloadLoggingStatus = async () => {
  loggingStatus.value = await adminStore.getLoggingStatus()
}

const reloadSettings = async () => {
  const loaded = await loadSettings('system')
  systemSettings.value = loaded.filter(setting => !setting.key.startsWith('system.notifications.'))
}

onMounted(async () => {
  await Promise.all([reloadSettings(), reloadLoggingStatus()])
})

const handleUpdate = async (setting) => {
  await updateSetting(setting, null, reloadSettings)
}

const handleReset = async (setting) => {
  await resetSetting(setting)
}

const handleLoggingUpdate = async (setting) => {
  await updateSetting(setting, null, reloadSettings)
  await reloadLoggingStatus()
}

const handleLoggingReset = async () => {
  await resetSetting(loggingSetting.value)
  await Promise.all([reloadSettings(), reloadLoggingStatus()])
}
</script>

<style scoped>
@import '../admin-settings-common.css';

.advanced-settings {
  margin: 1rem 0;
  border: 1px solid var(--gp-border-light);
  border-radius: 6px;
}

.advanced-settings summary {
  padding: 1rem;
  cursor: pointer;
  font-weight: 800;
}

.url-input {
  width: min(56vw, 720px);
  min-width: 420px;
}

.logging-status {
  margin: 0.75rem 0 1rem;
  padding: 0 1rem;
  color: var(--text-color-secondary);
}

@media (max-width: 768px) {
  .url-input {
    width: 100%;
    min-width: 0;
  }
}
</style>
