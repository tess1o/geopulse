<template>
  <div>
    <SettingSection title="Chunked Upload Settings">
      <SettingItem
        v-for="setting in uploadSettings"
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

    <SettingSection title="Drop Folder Import">
      <SettingItem
        v-for="setting in dropFolderSettings"
        :key="setting.key"
        :setting="setting"
        @reset="handleReset(setting)"
      >
        <template #control="{ setting }">
          <InputSwitch
            v-if="setting.valueType === 'BOOLEAN'"
            v-model="setting.currentValue"
            @change="handleUpdate(setting)"
            :disabled="setting.readOnly"
          />
          <InputText
            v-else-if="setting.valueType === 'STRING'"
            v-model="setting.currentValue"
            @change="handleUpdate(setting)"
            style="width: 300px"
            :disabled="setting.readOnly"
          />
          <InputNumber
            v-else
            v-model="setting.currentValue"
            @update:modelValue="handleUpdate(setting)"
            :min="1"
            style="width: 150px"
            :disabled="setting.readOnly"
          />
        </template>
      </SettingItem>
    </SettingSection>

    <SettingSection title="Streaming Parser Batch Sizes">
      <SettingItem
        v-for="setting in streamingSettings"
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
  </div>
</template>

<script setup>
import { ref, onMounted, computed } from 'vue'
import InputNumber from 'primevue/inputnumber'
import InputSwitch from 'primevue/inputswitch'
import InputText from 'primevue/inputtext'
import SettingSection from '../SettingSection.vue'
import SettingItem from '../SettingItem.vue'
import { useAdminSettings } from '@/composables/useAdminSettings'

const { loadSettings, updateSetting, resetSetting } = useAdminSettings()
const importSettings = ref([])

const uploadSettings = computed(() =>
  importSettings.value.filter(s =>
    ['import.chunk-size-mb', 'import.max-file-size-gb', 'import.upload-timeout-hours'].includes(s.key)
  )
)

const batchSettings = computed(() =>
  importSettings.value.filter(s =>
    ['import.bulk-insert-batch-size', 'import.merge-batch-size'].includes(s.key)
  )
)

const tempFileSettings = computed(() =>
  importSettings.value.filter(s =>
    ['import.large-file-threshold-mb', 'import.temp-file-retention-hours'].includes(s.key)
  )
)

const dropFolderSettings = computed(() =>
  importSettings.value.filter(s =>
    [
      'import.drop-folder.enabled',
      'import.drop-folder.path',
      'import.drop-folder.poll-interval-seconds',
      'import.drop-folder.stable-age-seconds',
      'import.drop-folder.geopulse-max-size-mb',
      'import.drop-folder.runtime-identity'
    ].includes(s.key)
  )
)

const streamingSettings = computed(() =>
  importSettings.value.filter(s => s.key.includes('streaming-batch-size'))
)

onMounted(async () => {
  importSettings.value = await loadSettings('import')
})

const handleUpdate = async (setting) => {
  if (setting.readOnly) {
    return
  }
  await updateSetting(setting)
}

const handleReset = async (setting) => {
  await resetSetting(setting)
}
</script>
