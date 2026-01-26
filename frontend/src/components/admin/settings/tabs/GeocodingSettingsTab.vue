<template>
  <div>
    <SettingSection title="General Settings">
      <SettingItem
        v-for="setting in generalSettings"
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
            @change="handleUpdate(setting)"
            placeholder="Select primary provider"
            style="width: 200px"
          />
          <Select
            v-else-if="setting.key === 'geocoding.fallback-provider'"
            v-model="setting.currentValue"
            :options="fallbackProviderOptions"
            optionLabel="label"
            optionValue="value"
            @change="handleUpdate(setting)"
            placeholder="Select fallback provider"
            style="width: 200px"
          />
          <InputNumber
            v-else-if="setting.valueType === 'INTEGER'"
            v-model="setting.currentValue"
            @update:modelValue="handleUpdate(setting)"
            :min="0"
            :step="100"
            style="width: 150px"
          />
        </template>
      </SettingItem>
    </SettingSection>

    <SettingSection title="Provider Availability">
      <SettingItem
        v-for="setting in providerAvailabilitySettings"
        :key="setting.key"
        :setting="setting"
        @reset="handleReset(setting)"
      >
        <template #control="{ setting }">
          <InputSwitch
            v-model="setting.currentValue"
            @change="handleUpdate(setting)"
          />
        </template>
      </SettingItem>
    </SettingSection>

    <SettingSection title="Provider Configuration">
      <SettingItem
        v-for="setting in providerConfigSettings"
        :key="setting.key"
        :setting="setting"
        @reset="handleReset(setting)"
      >
        <template #control="{ setting }">
          <Password
            v-if="setting.valueType === 'ENCRYPTED'"
            v-model="setting.currentValue"
            :feedback="false"
            toggleMask
            placeholder="Enter new value to update"
            @blur="handleUpdate(setting)"
            style="width: 300px"
          />
          <InputText
            v-else
            v-model="setting.currentValue"
            @blur="handleUpdate(setting)"
            :placeholder="getPlaceholder(setting)"
            style="width: 300px"
          />
        </template>
      </SettingItem>
    </SettingSection>
  </div>
</template>

<script setup>
import { ref, onMounted, computed } from 'vue'
import InputSwitch from 'primevue/inputswitch'
import InputNumber from 'primevue/inputnumber'
import InputText from 'primevue/inputtext'
import Password from 'primevue/password'
import Select from 'primevue/select'
import SettingSection from '../SettingSection.vue'
import SettingItem from '../SettingItem.vue'
import { useAdminSettings } from '@/composables/useAdminSettings'
import { GEOCODING_PROVIDER_OPTIONS } from '@/constants/adminSettingsMetadata'
import { getPlaceholder as getPlaceholderHelper } from '@/utils/settingHelpers'

const { loadSettings, updateSetting, resetSetting } = useAdminSettings()
const geocodingSettings = ref([])

const generalSettings = computed(() =>
  geocodingSettings.value.filter(s =>
    ['geocoding.primary-provider', 'geocoding.fallback-provider', 'geocoding.delay-ms'].includes(s.key)
  )
)

const providerAvailabilitySettings = computed(() =>
  geocodingSettings.value.filter(s => s.key.includes('.enabled'))
)

const providerConfigSettings = computed(() =>
  geocodingSettings.value.filter(s =>
    s.key.includes('.url') ||
    s.key.includes('.language') ||
    s.key.includes('.api-key') ||
    s.key.includes('.access-token')
  )
)

// Compute enabled providers
const enabledProviders = computed(() => {
  const enabled = []
  const nominatim = geocodingSettings.value.find(s => s.key === 'geocoding.nominatim.enabled')
  const photon = geocodingSettings.value.find(s => s.key === 'geocoding.photon.enabled')
  const googlemaps = geocodingSettings.value.find(s => s.key === 'geocoding.googlemaps.enabled')
  const mapbox = geocodingSettings.value.find(s => s.key === 'geocoding.mapbox.enabled')

  if (nominatim?.currentValue === true) enabled.push('nominatim')
  if (photon?.currentValue === true) enabled.push('photon')
  if (googlemaps?.currentValue === true) enabled.push('googlemaps')
  if (mapbox?.currentValue === true) enabled.push('mapbox')

  return enabled
})

// Provider options for primary (no "None" option)
const providerOptions = computed(() => {
  return GEOCODING_PROVIDER_OPTIONS.filter(opt => enabledProviders.value.includes(opt.value))
})

// Provider options for fallback (includes "None" option)
const fallbackProviderOptions = computed(() => {
  return [
    { label: 'None', value: '' },
    ...GEOCODING_PROVIDER_OPTIONS.filter(opt => enabledProviders.value.includes(opt.value))
  ]
})

const getPlaceholder = (setting) => {
  return getPlaceholderHelper(setting)
}

const validateGeocoding = async (setting) => {
  if (setting.key === 'geocoding.primary-provider') {
    if (!setting.currentValue || setting.currentValue === '') {
      return 'Primary provider cannot be empty'
    }
    if (!enabledProviders.value.includes(setting.currentValue)) {
      return 'Primary provider must be enabled'
    }
  }

  if (setting.key === 'geocoding.fallback-provider') {
    const primaryProvider = geocodingSettings.value.find(s => s.key === 'geocoding.primary-provider')
    if (setting.currentValue && setting.currentValue !== '' && setting.currentValue === primaryProvider?.currentValue) {
      return 'Fallback provider must be different from primary provider'
    }
  }

  return null
}

const reloadGeocodingSettings = async () => {
  geocodingSettings.value = await loadSettings('geocoding')
}

onMounted(async () => {
  await reloadGeocodingSettings()
})

const handleUpdate = async (setting) => {
  await updateSetting(setting, validateGeocoding, reloadGeocodingSettings)
}

const handleReset = async (setting) => {
  await resetSetting(setting)
}
</script>
