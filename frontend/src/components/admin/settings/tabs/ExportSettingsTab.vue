<template>
  <div>
    <SettingSection title="Job Management">
      <SettingItem
        v-for="setting in jobSettings"
        :key="setting.key"
        :setting="setting"
        @reset="handleReset(setting)"
      >
        <template #control="{ setting }">
          <InputNumber
            v-model="setting.currentValue"
            @update:modelValue="handleUpdate(setting)"
            :min="1"
            style="width: 150px"
          />
        </template>
      </SettingItem>
    </SettingSection>

    <SettingSection title="Batch Processing">
      <SettingItem
        v-for="setting in batchSettings"
        :key="setting.key"
        :setting="setting"
        @reset="handleReset(setting)"
      >
        <template #control="{ setting }">
          <InputNumber
            v-model="setting.currentValue"
            @update:modelValue="handleUpdate(setting)"
            :min="1"
            :step="100"
            style="width: 150px"
          />
        </template>
      </SettingItem>
    </SettingSection>

    <SettingSection title="Temporary File Storage">
      <SettingItem
        v-for="setting in tempFileSettings"
        :key="setting.key"
        :setting="setting"
        @reset="handleReset(setting)"
      >
        <template #control="{ setting }">
          <InputNumber
            v-model="setting.currentValue"
            @update:modelValue="handleUpdate(setting)"
            :min="1"
            style="width: 150px"
          />
        </template>
      </SettingItem>
    </SettingSection>
  </div>
</template>

<script setup>
import { ref, onMounted, computed } from 'vue'
import InputNumber from 'primevue/inputnumber'
import SettingSection from '../SettingSection.vue'
import SettingItem from '../SettingItem.vue'
import { useAdminSettings } from '@/composables/useAdminSettings'

const { loadSettings, updateSetting, resetSetting } = useAdminSettings()
const exportSettings = ref([])

const jobSettings = computed(() =>
  exportSettings.value.filter(s =>
    ['export.max-jobs-per-user', 'export.job-expiry-hours', 'export.concurrent-jobs-limit'].includes(s.key)
  )
)

const batchSettings = computed(() =>
  exportSettings.value.filter(s =>
    ['export.batch-size', 'export.trip-point-limit'].includes(s.key)
  )
)

const tempFileSettings = computed(() =>
  exportSettings.value.filter(s =>
    ['export.temp-file-retention-hours'].includes(s.key)
  )
)

onMounted(async () => {
  exportSettings.value = await loadSettings('export')
})

const handleUpdate = async (setting) => {
  await updateSetting(setting)
}

const handleReset = async (setting) => {
  await resetSetting(setting)
}
</script>
