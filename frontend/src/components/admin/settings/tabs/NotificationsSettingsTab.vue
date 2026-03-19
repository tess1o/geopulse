<template>
  <div>
    <SettingSection v-if="appriseSettings.length" title="Apprise Notifications">
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
            :min="integerMin(setting)"
            @update:modelValue="handleUpdate(setting)"
            style="width: 180px"
          />
          <Password
            v-else-if="setting.valueType === 'ENCRYPTED'"
            v-model="setting.currentValue"
            :feedback="false"
            toggleMask
            autocomplete="new-password"
            placeholder="Enter new token to update"
            :inputProps="{
              autocomplete: 'new-password',
              name: 'apprise_api_token',
              'data-lpignore': 'true',
              'data-form-type': 'other',
              autocapitalize: 'off',
              spellcheck: 'false'
            }"
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
    </SettingSection>

    <SettingSection v-if="cleanupSettings.length" title="Geofence Event Cleanup">
      <SettingItem
        v-for="setting in cleanupSettings"
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
            :min="integerMin(setting)"
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

    <BaseCard class="test-card">
      <div class="test-card-header">
        <div>
          <h4>Connection Test</h4>
          <p class="text-muted">
            Validate API connectivity and optionally send a real test notification.
          </p>
        </div>
        <Button
          label="Run Test"
          icon="pi pi-send"
          @click="openTestDialog"
        />
      </div>
    </BaseCard>

    <Dialog
      v-model:visible="showTestDialog"
      modal
      :draggable="false"
      header="Apprise Connection Test"
      class="apprise-test-dialog"
    >
      <div class="dialog-content">
        <p class="text-muted">
          Leave destination empty to run connectivity-only test. Provide a destination URL to send a real notification.
        </p>
        <div class="field-grid">
          <label for="apprise-test-destination">Destination URL(s) (optional)</label>
          <Textarea
            id="apprise-test-destination"
            v-model="testDestination"
            rows="3"
            autoResize
            placeholder="tgram://TOKEN/CHAT_ID"
          />

          <label for="apprise-test-title">Test title</label>
          <InputText
            id="apprise-test-title"
            v-model="testTitle"
            placeholder="GeoPulse Apprise Test"
          />

          <label for="apprise-test-body">Test message</label>
          <InputText
            id="apprise-test-body"
            v-model="testBody"
            placeholder="Test notification from GeoPulse admin settings."
          />
        </div>

        <Message v-if="lastTestResult" :severity="lastTestResult.severity" :closable="false">
          <strong>{{ lastTestResult.summary }}</strong>
          <span v-if="lastTestResult.statusCode"> (HTTP {{ lastTestResult.statusCode }})</span>
          <div>{{ lastTestResult.detail }}</div>
        </Message>
      </div>

      <template #footer>
        <Button label="Close" severity="secondary" text @click="showTestDialog = false" />
        <Button
          label="Test Connection"
          icon="pi pi-send"
          :loading="testingConnection"
          @click="testAppriseConnection"
        />
      </template>
    </Dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useToast } from 'primevue/usetoast'
import InputSwitch from 'primevue/inputswitch'
import InputNumber from 'primevue/inputnumber'
import InputText from 'primevue/inputtext'
import Textarea from 'primevue/textarea'
import Password from 'primevue/password'
import Button from 'primevue/button'
import Dialog from 'primevue/dialog'
import Message from 'primevue/message'
import BaseCard from '@/components/ui/base/BaseCard.vue'
import SettingSection from '../SettingSection.vue'
import SettingItem from '../SettingItem.vue'
import { useAdminSettings } from '@/composables/useAdminSettings'
import apiService from '@/utils/apiService'

const toast = useToast()
const { loadSettings, updateSetting, resetSetting } = useAdminSettings()

const systemSettings = ref([])
const showTestDialog = ref(false)
const testingConnection = ref(false)
const lastTestResult = ref(null)

const testDestination = ref('')
const testTitle = ref('GeoPulse Apprise Test')
const testBody = ref('Test notification from GeoPulse admin settings.')

const appriseSettings = computed(() =>
  systemSettings.value.filter(setting => setting.key.startsWith('system.notifications.apprise.'))
)

const cleanupSettings = computed(() =>
  systemSettings.value.filter(setting => setting.key.startsWith('system.notifications.geofence-events.'))
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

const integerMin = (setting) => (
  setting?.key?.includes('cleanup.interval-days') || setting?.key?.includes('retention-days')
    ? 1
    : 0
)

const openTestDialog = () => {
  lastTestResult.value = null
  showTestDialog.value = true
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
    lastTestResult.value = {
      severity: 'success',
      summary: 'Apprise test succeeded',
      detail: response?.message || 'Connection test succeeded',
      statusCode: response?.statusCode || null
    }

    toast.add({
      severity: 'success',
      summary: 'Apprise Test Succeeded',
      detail: response?.message || 'Connection test succeeded',
      life: 4000
    })
  } catch (error) {
    const statusCode = error?.response?.data?.statusCode || null
    const detail = error?.response?.data?.message || error?.message || 'Apprise test failed'
    lastTestResult.value = {
      severity: 'error',
      summary: 'Apprise test failed',
      detail,
      statusCode
    }

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

.test-card {
  margin: 1rem;
  padding: 1rem;
}

.test-card-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 1rem;
  margin-bottom: 1rem;
}

.test-card-header h4 {
  margin: 0;
}

.test-card-header p {
  margin: 0.35rem 0 0 0;
}

.dialog-content {
  display: grid;
  gap: 0.75rem;
}

.field-grid {
  display: grid;
  gap: 0.5rem;
}

.field-grid label {
  font-size: 0.9rem;
  font-weight: 600;
}

.apprise-test-dialog {
  width: min(680px, 95vw);
}

@media (max-width: 768px) {
  .test-card {
    margin: 0.5rem;
    padding: 0.75rem;
  }

  .test-card-header {
    flex-direction: column;
    align-items: stretch;
  }
}
</style>
