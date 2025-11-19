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
          <h3>Geocoding Provider Settings</h3>
          <p class="text-muted">Configure geocoding providers (coming soon)</p>
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
import Button from 'primevue/button'
import Tag from 'primevue/tag'
import Toast from 'primevue/toast'
import { useToast } from 'primevue/usetoast'
import AppLayout from '@/components/ui/layout/AppLayout.vue'
import apiService from '@/utils/apiService'

const toast = useToast()

const authSettings = ref([])

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
  }
}

const loadSettings = async () => {
  try {
    const response = await apiService.get('/admin/settings/auth')
    authSettings.value = response.map(setting => ({
      ...setting,
      label: settingLabels[setting.key]?.label || setting.key,
      description: settingLabels[setting.key]?.description || setting.description,
      currentValue: setting.valueType === 'BOOLEAN' ? setting.value === 'true' : setting.value
    }))
  } catch (error) {
    console.error('Failed to load settings:', error)
    toast.add({
      severity: 'error',
      summary: 'Error',
      detail: 'Failed to load settings',
      life: 3000
    })
  }
}

const updateSetting = async (setting) => {
  try {
    const value = setting.valueType === 'BOOLEAN'
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
  } catch (error) {
    console.error('Failed to update setting:', error)
    toast.add({
      severity: 'error',
      summary: 'Error',
      detail: 'Failed to update setting',
      life: 3000
    })
    // Reload to get correct value
    await loadSettings()
  }
}

const resetSetting = async (setting) => {
  try {
    const response = await apiService.delete(`/admin/settings/${setting.key}`)

    setting.isDefault = true
    setting.currentValue = setting.valueType === 'BOOLEAN'
      ? response.defaultValue === 'true'
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
  loadSettings()
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
