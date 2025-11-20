<template>
  <AppLayout>
    <div class="admin-settings">
      <div class="page-header">
        <h1>System Settings</h1>
        <p class="text-muted">Configure system-wide settings</p>
      </div>

    <TabView>
      <!-- Authentication Tab -->
      <TabPanel header="Authentication">
        <div class="settings-section">
          <h3>Registration Settings</h3>

          <div class="setting-item" v-for="setting in authSettings" :key="setting.key">
            <div class="setting-info">
              <label>{{ setting.label }}</label>
              <small class="text-muted">{{ setting.description }}</small>
            </div>
            <div class="setting-control">
              <InputSwitch
                v-if="setting.valueType === 'BOOLEAN'"
                v-model="setting.currentValue"
                @change="updateSetting(setting)"
              />
              <div class="setting-status">
                <Tag v-if="setting.isDefault" severity="secondary" value="Default" />
                <Button
                  v-else
                  label="Reset"
                  icon="pi pi-refresh"
                  text
                  size="small"
                  @click="resetSetting(setting)"
                />
              </div>
            </div>
          </div>
        </div>
      </TabPanel>

      <!-- Geocoding Tab -->
      <TabPanel header="Geocoding">
        <div class="settings-section">
          <h3>General Settings</h3>

          <div class="setting-item" v-for="setting in geocodingSettings.filter(s => ['geocoding.primary-provider', 'geocoding.fallback-provider', 'geocoding.delay-ms'].includes(s.key))" :key="setting.key">
            <div class="setting-info">
              <label>{{ setting.label }}</label>
              <small class="text-muted">{{ setting.description }}</small>
            </div>
            <div class="setting-control">
              <Select
                v-if="setting.key === 'geocoding.primary-provider'"
                v-model="setting.currentValue"
                :options="providerOptions"
                optionLabel="label"
                optionValue="value"
                @change="updateSetting(setting)"
                placeholder="Select primary provider"
                style="width: 200px"
              />
              <Select
                v-else-if="setting.key === 'geocoding.fallback-provider'"
                v-model="setting.currentValue"
                :options="fallbackProviderOptions"
                optionLabel="label"
                optionValue="value"
                @change="updateSetting(setting)"
                placeholder="Select fallback provider"
                style="width: 200px"
              />
              <InputNumber
                v-else-if="setting.valueType === 'INTEGER'"
                v-model="setting.currentValue"
                @update:modelValue="updateSetting(setting)"
                :min="0"
                :step="100"
                style="width: 150px"
              />
              <div class="setting-status">
                <Tag v-if="setting.isDefault" severity="secondary" value="Default" />
                <Button
                  v-else
                  label="Reset"
                  icon="pi pi-refresh"
                  text
                  size="small"
                  @click="resetSetting(setting)"
                />
              </div>
            </div>
          </div>
        </div>

        <div class="settings-section">
          <h3>Provider Availability</h3>

          <div class="setting-item" v-for="setting in geocodingSettings.filter(s => s.key.includes('.enabled'))" :key="setting.key">
            <div class="setting-info">
              <label>{{ setting.label }}</label>
              <small class="text-muted">{{ setting.description }}</small>
            </div>
            <div class="setting-control">
              <InputSwitch
                v-model="setting.currentValue"
                @change="updateSetting(setting)"
              />
              <div class="setting-status">
                <Tag v-if="setting.isDefault" severity="secondary" value="Default" />
                <Button
                  v-else
                  label="Reset"
                  icon="pi pi-refresh"
                  text
                  size="small"
                  @click="resetSetting(setting)"
                />
              </div>
            </div>
          </div>
        </div>

        <div class="settings-section">
          <h3>Provider Configuration</h3>

          <div class="setting-item" v-for="setting in geocodingSettings.filter(s => s.key.includes('.url') || s.key.includes('.api-key') || s.key.includes('.access-token'))" :key="setting.key">
            <div class="setting-info">
              <label>{{ setting.label }}</label>
              <small class="text-muted">{{ setting.description }}</small>
            </div>
            <div class="setting-control">
              <Password
                v-if="setting.valueType === 'ENCRYPTED'"
                v-model="setting.currentValue"
                :feedback="false"
                toggleMask
                placeholder="Enter new value to update"
                @blur="updateSetting(setting)"
                style="width: 300px"
              />
              <InputText
                v-else
                v-model="setting.currentValue"
                @blur="updateSetting(setting)"
                placeholder="Optional custom URL"
                style="width: 300px"
              />
              <div class="setting-status">
                <Tag v-if="setting.isDefault" severity="secondary" value="Default" />
                <Button
                  v-else
                  label="Reset"
                  icon="pi pi-refresh"
                  text
                  size="small"
                  @click="resetSetting(setting)"
                />
              </div>
            </div>
          </div>
        </div>
      </TabPanel>

      <!-- GPS Tab -->
      <TabPanel header="GPS Processing">
        <div class="settings-section">
          <h3>GPS Processing Defaults</h3>
          <p class="text-muted">Default GPS filtering settings (coming soon)</p>
        </div>
      </TabPanel>

      <!-- Import Tab -->
      <TabPanel header="Import">
        <div class="settings-section">
          <h3>Import Settings</h3>
          <p class="text-muted">Import batch and file settings (coming soon)</p>
        </div>
      </TabPanel>

      <!-- System Tab -->
      <TabPanel header="System">
        <div class="settings-section">
          <h3>System Performance</h3>
          <p class="text-muted">Timeline processing settings (coming soon)</p>
        </div>
      </TabPanel>
    </TabView>

    <Toast />
    </div>
  </AppLayout>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import TabView from 'primevue/tabview'
import TabPanel from 'primevue/tabpanel'
import InputSwitch from 'primevue/inputswitch'
import InputNumber from 'primevue/inputnumber'
import InputText from 'primevue/inputtext'
import Password from 'primevue/password'
import Select from 'primevue/select'
import Button from 'primevue/button'
import Tag from 'primevue/tag'
import Toast from 'primevue/toast'
import { useToast } from 'primevue/usetoast'
import { computed } from 'vue'
import AppLayout from '@/components/ui/layout/AppLayout.vue'
import apiService from '@/utils/apiService'

const toast = useToast()

const authSettings = ref([])
const geocodingSettings = ref([])

const settingLabels = {
  'auth.registration.enabled': {
    label: 'Registration Enabled',
    description: 'Allow new users to register'
  },
  'auth.password-registration.enabled': {
    label: 'Password Registration',
    description: 'Allow registration with email/password'
  },
  'auth.oidc.registration.enabled': {
    label: 'OIDC Registration',
    description: 'Allow registration via OIDC providers'
  },
  'auth.oidc.auto-link-accounts': {
    label: 'Auto-Link OIDC Accounts',
    description: 'Automatically link OIDC accounts by email (security risk)'
  },
  'geocoding.primary-provider': {
    label: 'Primary Provider',
    description: 'Primary geocoding service'
  },
  'geocoding.fallback-provider': {
    label: 'Fallback Provider',
    description: 'Fallback geocoding service (optional)'
  },
  'geocoding.delay-ms': {
    label: 'Request Delay',
    description: 'Delay between geocoding requests (milliseconds)'
  },
  'geocoding.nominatim.enabled': {
    label: 'Nominatim',
    description: 'Enable Nominatim geocoding provider'
  },
  'geocoding.photon.enabled': {
    label: 'Photon',
    description: 'Enable Photon geocoding provider'
  },
  'geocoding.googlemaps.enabled': {
    label: 'Google Maps',
    description: 'Enable Google Maps geocoding provider'
  },
  'geocoding.mapbox.enabled': {
    label: 'Mapbox',
    description: 'Enable Mapbox geocoding provider'
  },
  'geocoding.nominatim.url': {
    label: 'Nominatim URL',
    description: 'Custom Nominatim server URL (optional)'
  },
  'geocoding.photon.url': {
    label: 'Photon URL',
    description: 'Custom Photon server URL (optional)'
  },
  'geocoding.googlemaps.api-key': {
    label: 'Google Maps API Key',
    description: 'API key for Google Maps (encrypted, enter to update)'
  },
  'geocoding.mapbox.access-token': {
    label: 'Mapbox Access Token',
    description: 'Access token for Mapbox (encrypted, enter to update)'
  }
}

const allProviderOptions = [
  { label: 'Nominatim', value: 'nominatim' },
  { label: 'Photon', value: 'photon' },
  { label: 'Google Maps', value: 'googlemaps' },
  { label: 'Mapbox', value: 'mapbox' }
]

// Computed property to get only enabled providers
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
  return allProviderOptions.filter(opt => enabledProviders.value.includes(opt.value))
})

// Provider options for fallback (includes "None" option)
const fallbackProviderOptions = computed(() => {
  return [
    { label: 'None', value: '' },
    ...allProviderOptions.filter(opt => enabledProviders.value.includes(opt.value))
  ]
})

const loadAuthSettings = async () => {
  try {
    const response = await apiService.get('/admin/settings/auth')
    authSettings.value = response.map(setting => ({
      ...setting,
      label: settingLabels[setting.key]?.label || setting.key,
      description: settingLabels[setting.key]?.description || setting.description,
      currentValue: setting.valueType === 'BOOLEAN' ? setting.value === 'true' : setting.value
    }))
  } catch (error) {
    console.error('Failed to load auth settings:', error)
    toast.add({
      severity: 'error',
      summary: 'Error',
      detail: 'Failed to load auth settings',
      life: 3000
    })
  }
}

const loadGeocodingSettings = async () => {
  try {
    const response = await apiService.get('/admin/settings/geocoding')
    geocodingSettings.value = response.map(setting => ({
      ...setting,
      label: settingLabels[setting.key]?.label || setting.key,
      description: settingLabels[setting.key]?.description || setting.description,
      currentValue: setting.valueType === 'BOOLEAN'
        ? setting.value === 'true'
        : setting.valueType === 'INTEGER'
        ? parseInt(setting.value)
        : setting.value
    }))
  } catch (error) {
    console.error('Failed to load geocoding settings:', error)
    toast.add({
      severity: 'error',
      summary: 'Error',
      detail: 'Failed to load geocoding settings',
      life: 3000
    })
  }
}

const updateSetting = async (setting) => {
  try {
    // Validation for geocoding settings
    if (setting.key === 'geocoding.primary-provider') {
      if (!setting.currentValue || setting.currentValue === '') {
        toast.add({
          severity: 'error',
          summary: 'Validation Error',
          detail: 'Primary provider cannot be empty',
          life: 3000
        })
        await loadGeocodingSettings()
        return
      }
      if (!enabledProviders.value.includes(setting.currentValue)) {
        toast.add({
          severity: 'error',
          summary: 'Validation Error',
          detail: 'Primary provider must be enabled',
          life: 3000
        })
        await loadGeocodingSettings()
        return
      }
    }

    if (setting.key === 'geocoding.fallback-provider') {
      const primaryProvider = geocodingSettings.value.find(s => s.key === 'geocoding.primary-provider')
      if (setting.currentValue && setting.currentValue !== '' && setting.currentValue === primaryProvider?.currentValue) {
        toast.add({
          severity: 'error',
          summary: 'Validation Error',
          detail: 'Fallback provider must be different from primary provider',
          life: 3000
        })
        await loadGeocodingSettings()
        return
      }
    }

    // For encrypted fields, skip update if value is ******** (not changed)
    if (setting.valueType === 'ENCRYPTED' && (setting.currentValue === '********' || setting.currentValue === '')) {
      return
    }

    const value = setting.valueType === 'BOOLEAN'
      ? setting.currentValue.toString()
      : setting.valueType === 'INTEGER'
      ? setting.currentValue.toString()
      : setting.currentValue

    await apiService.put(`/admin/settings/${setting.key}`, { value })

    setting.isDefault = false

    toast.add({
      severity: 'success',
      summary: 'Success',
      detail: `${setting.label} updated`,
      life: 3000
    })

    // Reload settings to refresh masked encrypted values
    if (setting.valueType === 'ENCRYPTED') {
      await loadGeocodingSettings()
    }
  } catch (error) {
    console.error('Failed to update setting:', error)
    const errorMessage = error.response?.data?.message || error.message || 'Failed to update setting'
    toast.add({
      severity: 'error',
      summary: 'Error',
      detail: errorMessage,
      life: 3000
    })
    // Reload to get correct value
    await loadAuthSettings()
    await loadGeocodingSettings()
  }
}

const resetSetting = async (setting) => {
  try {
    const response = await apiService.delete(`/admin/settings/${setting.key}`)

    setting.isDefault = true
    setting.currentValue = setting.valueType === 'BOOLEAN'
      ? response.defaultValue === 'true'
      : setting.valueType === 'INTEGER'
      ? parseInt(response.defaultValue)
      : response.defaultValue

    toast.add({
      severity: 'success',
      summary: 'Success',
      detail: `${setting.label} reset to default`,
      life: 3000
    })
  } catch (error) {
    console.error('Failed to reset setting:', error)
    toast.add({
      severity: 'error',
      summary: 'Error',
      detail: 'Failed to reset setting',
      life: 3000
    })
  }
}

onMounted(() => {
  loadAuthSettings()
  loadGeocodingSettings()
})
</script>

<style scoped>
.admin-settings {
  padding: 1.5rem;
}

.page-header {
  margin-bottom: 1.5rem;
}

.page-header h1 {
  margin: 0;
  font-size: 1.75rem;
}

.settings-section {
  padding: 1rem 0;
}

.settings-section h3 {
  margin-top: 0;
  margin-bottom: 1rem;
}

.setting-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 1rem;
  border-bottom: 1px solid var(--surface-border);
}

.setting-item:last-child {
  border-bottom: none;
}

.setting-info {
  flex: 1;
}

.setting-info label {
  display: block;
  font-weight: 500;
  margin-bottom: 0.25rem;
}

.setting-info small {
  display: block;
}

.setting-control {
  display: flex;
  align-items: center;
  gap: 1rem;
}

.setting-status {
  min-width: 80px;
  text-align: right;
}

.text-muted {
  color: var(--text-color-secondary);
}
</style>
