<template>
  <div>
    <SettingSection title="Apprise Notifications">
      <SettingItem
        v-for="setting in appriseSettings"
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
            :min="0"
            @update:modelValue="handleUpdate(setting)"
            style="width: 180px"
          />
          <Password
            v-else-if="setting.valueType === 'ENCRYPTED'"
            v-model="setting.currentValue"
            :feedback="false"
            toggleMask
            placeholder="Enter new token to update"
            @change="handleUpdate(setting)"
            style="width: 280px"
          />
          <InputText
            v-else
            v-model="setting.currentValue"
            @change="handleUpdate(setting)"
            style="width: 300px"
          />
        </template>
      </SettingItem>

      <div class="apprise-test-panel">
        <h4>Connection Test</h4>
        <p class="text-muted">
          Test Apprise connectivity using the configured API URL and token. Optionally provide a destination to send a real test notification.
        </p>

        <div class="test-fields">
          <InputText v-model="testDestination" placeholder="Optional destination URL(s)" class="test-input" />
          <InputText v-model="testTitle" placeholder="Test title" class="test-input" />
          <InputText v-model="testBody" placeholder="Test message" class="test-input" />
        </div>

        <Button
          label="Test Apprise"
          icon="pi pi-send"
          :loading="testingConnection"
          @click="testAppriseConnection"
        />
      </div>
    </SettingSection>

    <SettingSection v-if="otherSystemSettings.length > 0" title="System">
      <SettingItem
        v-for="setting in otherSystemSettings"
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
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useToast } from 'primevue/usetoast'
import InputSwitch from 'primevue/inputswitch'
import InputNumber from 'primevue/inputnumber'
import InputText from 'primevue/inputtext'
import Password from 'primevue/password'
import Button from 'primevue/button'
import SettingSection from '../SettingSection.vue'
import SettingItem from '../SettingItem.vue'
import { useAdminSettings } from '@/composables/useAdminSettings'
import apiService from '@/utils/apiService'

const toast = useToast()
const { loadSettings, updateSetting, resetSetting } = useAdminSettings()

const systemSettings = ref([])
const testingConnection = ref(false)

const testDestination = ref('')
const testTitle = ref('GeoPulse Apprise Test')
const testBody = ref('Test notification from GeoPulse admin settings.')

const appriseSettings = computed(() =>
  systemSettings.value.filter(s => s.key.startsWith('system.notifications.apprise.'))
)

const otherSystemSettings = computed(() =>
  systemSettings.value.filter(s => !s.key.startsWith('system.notifications.apprise.'))
)

const reloadSettings = async () => {
  systemSettings.value = await loadSettings('system')
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

const testAppriseConnection = async () => {
  testingConnection.value = true
  try {
    const payload = {
      destination: testDestination.value?.trim() || null,
      title: testTitle.value?.trim() || null,
      body: testBody.value?.trim() || null
    }

    const response = await apiService.post('/admin/settings/system/notifications/apprise/test', payload)

    toast.add({
      severity: 'success',
      summary: 'Apprise Test Succeeded',
      detail: response?.message || 'Connection test succeeded',
      life: 4000
    })
  } catch (error) {
    const detail = error?.response?.data?.message || error?.message || 'Apprise test failed'
    toast.add({
      severity: 'error',
      summary: 'Apprise Test Failed',
      detail,
      life: 5000
    })
  } finally {
    testingConnection.value = false
  }
}
</script>

<style scoped>
@import '../admin-settings-common.css';

.apprise-test-panel {
  margin: 1rem;
  padding: 1rem;
  border: 1px solid var(--surface-border);
  border-radius: 8px;
  background: var(--surface-card);
}

.apprise-test-panel h4 {
  margin-top: 0;
  margin-bottom: 0.5rem;
}

.test-fields {
  display: grid;
  gap: 0.5rem;
  margin-bottom: 0.75rem;
}

.test-input {
  width: 100%;
}

@media (max-width: 768px) {
  .apprise-test-panel {
    margin: 0.5rem;
    padding: 0.75rem;
  }
}
</style>
