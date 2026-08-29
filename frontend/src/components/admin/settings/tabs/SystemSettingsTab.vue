<template>
  <div>
    <SettingSection v-if="baseSystemSettings.length > 0" title="System">
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
            placeholder="Select default unit"
            @change="handleUpdate(setting)"
            style="width: 260px"
          />
          <Select
            v-else-if="setting.key === 'system.user.default-temperature-unit'"
            v-model="setting.currentValue"
            :options="temperatureUnitOptions"
            optionLabel="label"
            optionValue="value"
            placeholder="Select default unit"
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

    <details v-if="updateCheckSettings.length > 0" class="advanced-settings">
      <summary>Update Check</summary>
      <SettingSection title="Release Metadata">
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
      <summary>Water Dataset</summary>
      <SettingSection title="Dataset Source">
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
      <h3>No General System Settings</h3>
      <p class="text-muted">Notification settings were moved to the Notifications tab.</p>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import InputSwitch from 'primevue/inputswitch'
import InputNumber from 'primevue/inputnumber'
import InputText from 'primevue/inputtext'
import Select from 'primevue/select'
import SettingSection from '../SettingSection.vue'
import SettingItem from '../SettingItem.vue'
import { useAdminSettings } from '@/composables/useAdminSettings'
import { DISTANCE_UNIT_OPTIONS, TEMPERATURE_UNIT_OPTIONS } from '@/constants/adminSettingsMetadata'
const { loadSettings, updateSetting, resetSetting } = useAdminSettings()

const systemSettings = ref([])
const distanceUnitOptions = DISTANCE_UNIT_OPTIONS
const temperatureUnitOptions = TEMPERATURE_UNIT_OPTIONS

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
    !waterDatasetKeys.includes(setting.key)
  )
)
const updateCheckSettings = computed(() =>
  updateCheckKeys.map(key => systemSettings.value.find(setting => setting.key === key)).filter(Boolean)
)
const waterDatasetSettings = computed(() =>
  waterDatasetKeys.map(key => systemSettings.value.find(setting => setting.key === key)).filter(Boolean)
)

const reloadSettings = async () => {
  const loaded = await loadSettings('system')
  systemSettings.value = loaded.filter(setting => !setting.key.startsWith('system.notifications.'))
}

onMounted(async () => {
  await reloadSettings()
})

const handleUpdate = async (setting) => {
  await updateSetting(setting, null, reloadSettings)
}

const handleReset = async (setting) => {
  await resetSetting(setting)
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

@media (max-width: 768px) {
  .url-input {
    width: 100%;
    min-width: 0;
  }
}
</style>
