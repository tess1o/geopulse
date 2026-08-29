<template>
  <div>
    <SettingSection title="Authentication Settings">
      <SettingItem
        v-for="setting in primaryAuthSettings"
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

    <details class="advanced-settings">
      <summary>OIDC Advanced</summary>
      <SettingSection title="OIDC Advanced">
        <SettingItem
          v-for="setting in oidcAdvancedSettings"
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
              style="width: 150px"
              @update:modelValue="handleUpdate(setting)"
            />
            <InputText
              v-else
              v-model="setting.currentValue"
              style="width: 360px"
              @change="handleUpdate(setting)"
            />
          </template>
        </SettingItem>
      </SettingSection>
    </details>
  </div>
</template>

<script setup>
import { computed, ref, onMounted } from 'vue'
import InputNumber from 'primevue/inputnumber'
import InputSwitch from 'primevue/inputswitch'
import InputText from 'primevue/inputtext'
import SettingSection from '../SettingSection.vue'
import SettingItem from '../SettingItem.vue'
import { useAdminSettings } from '@/composables/useAdminSettings'

const { loadSettings, updateSetting, resetSetting } = useAdminSettings()
const authSettings = ref([])
const oidcAdvancedKeys = [
  'auth.oidc.callback-base-url',
  'auth.oidc.jwks-cache.ttl-hours',
  'auth.oidc.cleanup.session-states.enabled'
]
const primaryAuthSettings = computed(() => authSettings.value.filter(setting => !oidcAdvancedKeys.includes(setting.key)))
const oidcAdvancedSettings = computed(() => authSettings.value.filter(setting => oidcAdvancedKeys.includes(setting.key)))

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

<style scoped>
@import '../admin-settings-common.css';
</style>
