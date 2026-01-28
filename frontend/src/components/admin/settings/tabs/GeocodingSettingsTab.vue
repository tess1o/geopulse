<template>
  <div>
    <!-- Save/Discard buttons (only shown when dirty) -->
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
        />
      </div>
    </div>

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
            @change="markDirty"
            placeholder="Select primary provider"
            style="width: 200px"
          />
          <Select
            v-else-if="setting.key === 'geocoding.fallback-provider'"
            v-model="setting.currentValue"
            :options="fallbackProviderOptions"
            optionLabel="label"
            optionValue="value"
            @change="markDirty"
            placeholder="Select fallback provider"
            style="width: 200px"
          />
          <InputNumber
            v-else-if="setting.valueType === 'INTEGER'"
            v-model="setting.currentValue"
            @input="markDirty"
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
          <div style="display: flex; align-items: center; gap: 0.5rem;">
            <InputSwitch
              v-model="setting.currentValue"
              @input="markDirty"
            />

            <!-- Warning for Google Maps -->
            <span
              v-if="setting.key === 'geocoding.googlemaps.enabled' &&
                    setting.currentValue &&
                    !hasGoogleMapsCredentials"
              class="warning-text"
            >
              ⚠️ Requires API key
            </span>

            <!-- Warning for Mapbox -->
            <span
              v-if="setting.key === 'geocoding.mapbox.enabled' &&
                    setting.currentValue &&
                    !hasMapboxCredentials"
              class="warning-text"
            >
              ⚠️ Requires access token
            </span>
          </div>
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
            @input="markDirty"
            style="width: 300px"
          />
          <InputText
            v-else
            v-model="setting.currentValue"
            @input="markDirty"
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
import { useToast } from 'primevue/usetoast'
import InputSwitch from 'primevue/inputswitch'
import InputNumber from 'primevue/inputnumber'
import InputText from 'primevue/inputtext'
import Password from 'primevue/password'
import Select from 'primevue/select'
import Button from 'primevue/button'
import Message from 'primevue/message'
import SettingSection from '../SettingSection.vue'
import SettingItem from '../SettingItem.vue'
import { useAdminSettings } from '@/composables/useAdminSettings'
import { GEOCODING_PROVIDER_OPTIONS } from '@/constants/adminSettingsMetadata'
import { getPlaceholder as getPlaceholderHelper, parseSettingValue } from '@/utils/settingHelpers'
import apiService from '@/utils/apiService'

const { loadSettings, updateSetting, resetSetting } = useAdminSettings()
const toast = useToast()
const geocodingSettings = ref([])

// Dirty state tracking
const hasUnsavedChanges = ref(false)
const originalSettings = ref([])
const isSaving = ref(false)

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

// Check if credentials are present for paid providers
// Note: '********' means credentials exist on server (masked for security)
const hasGoogleMapsCredentials = computed(() => {
  const apiKey = geocodingSettings.value.find(s => s.key === 'geocoding.googlemaps.api-key')?.currentValue
  return apiKey && apiKey !== ''
})

const hasMapboxCredentials = computed(() => {
  const token = geocodingSettings.value.find(s => s.key === 'geocoding.mapbox.access-token')?.currentValue
  return token && token !== ''
})

const getPlaceholder = (setting) => {
  return getPlaceholderHelper(setting)
}

const reloadGeocodingSettings = async () => {
  geocodingSettings.value = await loadSettings('geocoding')
}

// Mark dirty when any input changes
const markDirty = () => {
  hasUnsavedChanges.value = true
}

onMounted(async () => {
  await reloadGeocodingSettings()
  originalSettings.value = JSON.parse(JSON.stringify(geocodingSettings.value))
})

// Frontend validation (mirrors backend logic for better UX)
const validateAllSettings = () => {
  // Get current values
  const nominatimEnabled = geocodingSettings.value.find(s => s.key === 'geocoding.nominatim.enabled')?.currentValue
  const photonEnabled = geocodingSettings.value.find(s => s.key === 'geocoding.photon.enabled')?.currentValue
  const googlemapsEnabled = geocodingSettings.value.find(s => s.key === 'geocoding.googlemaps.enabled')?.currentValue
  const mapboxEnabled = geocodingSettings.value.find(s => s.key === 'geocoding.mapbox.enabled')?.currentValue

  const googlemapsApiKey = geocodingSettings.value.find(s => s.key === 'geocoding.googlemaps.api-key')?.currentValue
  const mapboxToken = geocodingSettings.value.find(s => s.key === 'geocoding.mapbox.access-token')?.currentValue

  const primaryProvider = geocodingSettings.value.find(s => s.key === 'geocoding.primary-provider')?.currentValue
  const fallbackProvider = geocodingSettings.value.find(s => s.key === 'geocoding.fallback-provider')?.currentValue

  // 1. At least one provider must be enabled
  if (!nominatimEnabled && !photonEnabled && !googlemapsEnabled && !mapboxEnabled) {
    return 'At least one geocoding provider must be enabled'
  }

  // 2. Cannot enable Google Maps without API key
  // Note: '********' means credentials exist on server (masked for security)
  if (googlemapsEnabled && (!googlemapsApiKey || googlemapsApiKey === '')) {
    return 'Cannot enable Google Maps without providing an API key'
  }

  // 3. Cannot enable Mapbox without access token
  // Note: '********' means credentials exist on server (masked for security)
  if (mapboxEnabled && (!mapboxToken || mapboxToken === '')) {
    return 'Cannot enable Mapbox without providing an access token'
  }

  // 4. Primary provider must be enabled
  const enabledProvidersList = []
  if (nominatimEnabled) enabledProvidersList.push('nominatim')
  if (photonEnabled) enabledProvidersList.push('photon')
  if (googlemapsEnabled) enabledProvidersList.push('googlemaps')
  if (mapboxEnabled) enabledProvidersList.push('mapbox')

  if (primaryProvider && !enabledProvidersList.includes(primaryProvider)) {
    return `Primary provider "${primaryProvider}" is not enabled. Please enable it first or choose a different provider.`
  }

  // 5. Fallback provider must be enabled and different from primary
  if (fallbackProvider && fallbackProvider !== '') {
    if (!enabledProvidersList.includes(fallbackProvider)) {
      return `Fallback provider "${fallbackProvider}" is not enabled. Please enable it first or choose a different provider.`
    }
    if (fallbackProvider === primaryProvider) {
      return 'Fallback provider cannot be the same as primary provider'
    }
  }

  return null // No errors
}

// Save all changes using batch endpoint
const saveAllChanges = async () => {
  // 1. Frontend validation
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
    // 2. Get all changed settings
    const changedSettings = geocodingSettings.value.filter(setting => {
      const original = originalSettings.value.find(s => s.key === setting.key)
      return JSON.stringify(setting.currentValue) !== JSON.stringify(original.currentValue)
    })

    if (changedSettings.length === 0) {
      hasUnsavedChanges.value = false
      return
    }

    // 3. Format for bulk update API
    const bulkUpdatePayload = {
      settings: changedSettings.map(setting => ({
        key: setting.key,
        value: parseSettingValue(setting)
      }))
    }

    // 4. Call batch update endpoint (ATOMIC - all or nothing)
    const response = await apiService.post('/admin/settings/bulk', bulkUpdatePayload)

    // 5. Reload to get fresh data from server
    await reloadGeocodingSettings()
    originalSettings.value = JSON.parse(JSON.stringify(geocodingSettings.value))
    hasUnsavedChanges.value = false

    toast.add({
      severity: 'success',
      summary: 'Settings Saved',
      detail: `Successfully saved ${response.updated} settings`,
      life: 3000
    })
  } catch (error) {
    console.error('Failed to save settings:', error)
    const errorDetail = error.response?.data?.message || error.message || 'Failed to save settings'
    const errorKey = error.response?.data?.key

    toast.add({
      severity: 'error',
      summary: 'Save Failed',
      detail: errorKey ? `${errorKey}: ${errorDetail}` : errorDetail,
      life: 5000
    })

    // Reload to reset to server state (transaction rolled back)
    await reloadGeocodingSettings()
    originalSettings.value = JSON.parse(JSON.stringify(geocodingSettings.value))
    hasUnsavedChanges.value = false
  } finally {
    isSaving.value = false
  }
}

// Discard all changes
const discardChanges = () => {
  // Restore original values
  geocodingSettings.value = JSON.parse(JSON.stringify(originalSettings.value))
  hasUnsavedChanges.value = false

  toast.add({
    severity: 'info',
    summary: 'Changes Discarded',
    detail: 'All unsaved changes have been discarded',
    life: 3000
  })
}

// Handle individual reset - just changes UI to default value, requires Save Changes
const handleReset = (setting) => {
  // Reset to default value in UI only (not saved until user clicks Save Changes)
  setting.currentValue = setting.defaultValue
  markDirty()
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

.warning-text {
  color: var(--orange-500);
  font-size: 0.875rem;
  font-weight: 500;
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
}
</style>
