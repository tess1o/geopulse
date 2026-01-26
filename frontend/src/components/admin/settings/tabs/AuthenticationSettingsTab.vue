<template>
  <div>
    <SettingSection title="Registration Settings">
      <SettingItem
        v-for="setting in authSettings"
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
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import InputSwitch from 'primevue/inputswitch'
import SettingSection from '../SettingSection.vue'
import SettingItem from '../SettingItem.vue'
import { useAdminSettings } from '@/composables/useAdminSettings'

const { loadSettings, updateSetting, resetSetting } = useAdminSettings()
const authSettings = ref([])

onMounted(async () => {
  authSettings.value = await loadSettings('auth')
})

const handleUpdate = async (setting) => {
  await updateSetting(setting)
}

const handleReset = async (setting) => {
  await resetSetting(setting)
}
</script>
