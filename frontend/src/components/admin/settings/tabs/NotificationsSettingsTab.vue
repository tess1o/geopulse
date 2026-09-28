<template>
  <div>
    <SettingSection v-if="appriseSettings.length" :title="t('adminProviderSettings.notificationsSettingsTab.appriseSectionTitle')">
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
            :placeholder="t('adminProviderSettings.notificationsSettingsTab.enterNewTokenPlaceholder')"
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

    <SettingSection v-if="cleanupSettings.length" :title="t('adminProviderSettings.notificationsSettingsTab.cleanupSectionTitle')">
      <i18n-t keypath="adminProviderSettings.notificationsSettingsTab.cleanupNote" tag="p" class="text-muted cleanup-note">
        <template #code1><code>geopulse.notifications.geofence-events.cleanup.scheduler-cadence</code></template>
        <template #code2><code>geopulse.notifications.user-notifications.cleanup.scheduler-cadence</code></template>
      </i18n-t>
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
          <h4>{{ t('adminProviderSettings.notificationsSettingsTab.connectionTestTitle') }}</h4>
          <p class="text-muted">
            {{ t('adminProviderSettings.notificationsSettingsTab.connectionTestDescription') }}
          </p>
        </div>
        <Button
          :label="t('adminProviderSettings.notificationsSettingsTab.runTest')"
          icon="pi pi-send"
          @click="openTestDialog"
          :disabled="adminReadOnly"
        />
      </div>
    </BaseCard>

    <Dialog
      v-model:visible="showTestDialog"
      modal
      :draggable="false"
      :header="t('adminProviderSettings.notificationsSettingsTab.dialogHeader')"
      class="apprise-test-dialog"
    >
      <div class="dialog-content">
        <p class="text-muted">
          {{ t('adminProviderSettings.notificationsSettingsTab.dialogDescription') }}
        </p>
        <div class="field-grid">
          <label for="apprise-test-destination">{{ t('adminProviderSettings.notificationsSettingsTab.destinationLabel') }}</label>
          <Textarea
            id="apprise-test-destination"
            v-model="testDestination"
            rows="3"
            autoResize
            :placeholder="t('adminProviderSettings.notificationsSettingsTab.destinationPlaceholder')"
          />

          <label for="apprise-test-title">{{ t('adminProviderSettings.notificationsSettingsTab.testTitleLabel') }}</label>
          <InputText
            id="apprise-test-title"
            v-model="testTitle"
            :placeholder="t('adminProviderSettings.notificationsSettingsTab.testTitleDefault')"
          />

          <label for="apprise-test-body">{{ t('adminProviderSettings.notificationsSettingsTab.testBodyLabel') }}</label>
          <InputText
            id="apprise-test-body"
            v-model="testBody"
            :placeholder="t('adminProviderSettings.notificationsSettingsTab.testBodyDefault')"
          />
        </div>

        <Message v-if="lastTestResult" :severity="lastTestResult.severity" :closable="false">
          <strong>{{ lastTestResult.summary }}</strong>
          <span v-if="lastTestResult.statusCode"> (HTTP {{ lastTestResult.statusCode }})</span>
          <div>{{ lastTestResult.detail }}</div>
        </Message>
      </div>

      <template #footer>
        <Button :label="t('adminProviderSettings.notificationsSettingsTab.close')" severity="secondary" text @click="showTestDialog = false" />
        <Button
          :label="t('adminProviderSettings.shared.testConnection')"
          icon="pi pi-send"
          :loading="testingConnection"
          :disabled="adminReadOnly"
          @click="testAppriseConnection"
        />
      </template>
    </Dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useToast } from 'primevue/usetoast'
import InputSwitch from 'primevue/inputswitch'
import InputNumber from 'primevue/inputnumber'
import InputText from 'primevue/inputtext'
import Textarea from 'primevue/textarea'
import Password from 'primevue/password'
import Button from 'primevue/button'
import Dialog from 'primevue/dialog'
import Message from 'primevue/message'
import { storeToRefs } from 'pinia'
import BaseCard from '@/components/ui/base/BaseCard.vue'
import SettingSection from '../SettingSection.vue'
import SettingItem from '../SettingItem.vue'
import { useAdminSettings } from '@/composables/useAdminSettings'
import { useAuthStore } from '@/stores/auth'
import { useAdminStore } from '@/stores/admin'
import { showDemoReadOnlyToast } from '@/utils/demoMode'
import { formatApiErrorDetail } from '@/utils/apiErrorDetail'

const { t } = useI18n()
const toast = useToast()
const { loadSettings, updateSetting, resetSetting } = useAdminSettings()
const authStore = useAuthStore()
const adminStore = useAdminStore()
const { adminReadOnly } = storeToRefs(authStore)

const systemSettings = ref([])
const showTestDialog = ref(false)
const testingConnection = ref(false)
const lastTestResult = ref(null)

const testDestination = ref('')
const testTitle = ref(t('adminProviderSettings.notificationsSettingsTab.testTitleDefault'))
const testBody = ref(t('adminProviderSettings.notificationsSettingsTab.testBodyDefault'))

const appriseSettings = computed(() =>
  systemSettings.value.filter(setting => setting.key.startsWith('system.notifications.apprise.'))
)

const cleanupSettings = computed(() =>
  systemSettings.value.filter(setting =>
    setting.key.startsWith('system.notifications.geofence-events.')
    || setting.key.startsWith('system.notifications.user-notifications.')
  )
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
  setting?.key?.includes('retention-days')
    ? 1
    : 0
)

const openTestDialog = () => {
  lastTestResult.value = null
  showTestDialog.value = true
}

const testAppriseConnection = async () => {
  if (adminReadOnly.value) {
    showDemoReadOnlyToast(toast)
    return
  }

  testingConnection.value = true
  try {
    const payload = {
      destination: testDestination.value?.trim() || null,
      title: testTitle.value?.trim() || null,
      body: testBody.value?.trim() || null
    }

    const response = await adminStore.testAppriseConnection(payload)
    lastTestResult.value = {
      severity: response.success ? 'success' : 'error',
      summary: response.success
        ? t('adminProviderSettings.notificationsSettingsTab.toasts.testSucceededMessage')
        : t('adminProviderSettings.notificationsSettingsTab.toasts.testFailedMessage'),
      detail: response?.detail || (response.success
        ? t('adminProviderSettings.notificationsSettingsTab.toasts.connectionTestSucceeded')
        : t('adminProviderSettings.notificationsSettingsTab.toasts.connectionTestFailed')),
      statusCode: response?.statusCode || null
    }

    toast.add({
      severity: response.success ? 'success' : 'error',
      summary: response.success
        ? t('adminProviderSettings.notificationsSettingsTab.toasts.testSucceeded')
        : t('adminProviderSettings.notificationsSettingsTab.toasts.testFailed'),
      detail: response?.detail || (response.success
        ? t('adminProviderSettings.notificationsSettingsTab.toasts.connectionTestSucceeded')
        : t('adminProviderSettings.notificationsSettingsTab.toasts.connectionTestFailed')),
      life: 4000
    })
  } catch (error) {
    const statusCode = error?.status || null
    const detail = formatApiErrorDetail(error, t('adminProviderSettings.notificationsSettingsTab.toasts.testFailedMessage'))
    lastTestResult.value = {
      severity: 'error',
      summary: t('adminProviderSettings.notificationsSettingsTab.toasts.testFailedMessage'),
      detail,
      statusCode
    }

    toast.add({
      severity: 'error',
      summary: t('adminProviderSettings.notificationsSettingsTab.toasts.testFailed'),
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

.cleanup-note {
  margin: 0 1rem 0.75rem 1rem;
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
