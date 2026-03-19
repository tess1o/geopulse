<template>
  <div>
    <SettingSection v-if="systemSettings.length > 0" title="System">
      <SettingItem
        v-for="setting in systemSettings"
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

    <div v-else class="empty-state">
      <div class="empty-state-icon">
        <i class="pi pi-cog" style="font-size: 2rem; color: var(--text-color-secondary);" />
      </div>
      <h3>No General System Settings</h3>
      <p class="text-muted">Notification settings were moved to the Notifications tab.</p>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import InputSwitch from 'primevue/inputswitch'
import InputNumber from 'primevue/inputnumber'
import InputText from 'primevue/inputtext'
import SettingSection from '../SettingSection.vue'
import SettingItem from '../SettingItem.vue'
import { useAdminSettings } from '@/composables/useAdminSettings'
const { loadSettings, updateSetting, resetSetting } = useAdminSettings()

const systemSettings = ref([])

const reloadSettings = async () => {
  const loaded = await loadSettings('system')
  systemSettings.value = loaded.filter(setting => !setting.key.startsWith('system.notifications.apprise.'))
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
</style>
