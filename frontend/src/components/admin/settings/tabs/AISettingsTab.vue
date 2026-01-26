<template>
  <div>
    <SettingSection title="AI Assistant Configuration">
      <SettingItem
        v-for="setting in configSettings"
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
            @update:modelValue="handleUpdate(setting)"
            :min="1"
            :step="setting.key === 'ai.tool-result.max-length' ? 1000 : 1"
            style="width: 150px"
          />
        </template>
      </SettingItem>
    </SettingSection>

    <div class="settings-section">
      <h3>System Message</h3>

      <div class="ai-setting-description">
        <p class="text-muted">
          Configure the global default system message for the AI assistant. This message defines the AI's behavior and will be used for all users unless they override it with their own custom message in their profile settings.
        </p>
      </div>

      <div class="ai-message-container">
        <div class="ai-message-header">
          <label for="ai-system-message" class="ai-message-label">
            Default System Message
          </label>
          <div class="ai-message-actions">
            <Button
              label="Load Built-in Default"
              icon="pi pi-refresh"
              size="small"
              outlined
              @click="loadBuiltInDefault"
              :loading="loadingBuiltInDefault"
              v-tooltip.left="'Reset to the built-in default system message'"
            />
            <Button
              label="Save"
              icon="pi pi-save"
              size="small"
              @click="saveAISystemMessage"
              :loading="savingAIMessage"
              :disabled="!aiSystemMessageChanged"
            />
          </div>
        </div>
        <Textarea
          id="ai-system-message"
          v-model="aiSystemMessage"
          rows="15"
          class="ai-system-message-input"
          placeholder="Loading system message..."
          @input="aiSystemMessageChanged = true"
        />
        <small class="text-muted">
          This global default will be used for all users. Users can override this in their profile settings. Leave empty and save to use the built-in default.
        </small>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, computed } from 'vue'
import { useToast } from 'primevue/usetoast'
import InputSwitch from 'primevue/inputswitch'
import InputNumber from 'primevue/inputnumber'
import Button from 'primevue/button'
import Textarea from 'primevue/textarea'
import SettingSection from '../SettingSection.vue'
import SettingItem from '../SettingItem.vue'
import { useAdminSettings } from '@/composables/useAdminSettings'
import apiService from '@/utils/apiService'

const { loadSettings, updateSetting, resetSetting } = useAdminSettings()
const toast = useToast()

const aiSettings = ref([])
const aiSystemMessage = ref('')
const aiSystemMessageOriginal = ref('')
const aiSystemMessageChanged = ref(false)
const savingAIMessage = ref(false)
const loadingBuiltInDefault = ref(false)

// Filter out the system message setting from config settings
const configSettings = computed(() =>
  aiSettings.value.filter(s => s.key !== 'ai.default-system-message')
)

const loadAISettings = async () => {
  try {
    const response = await apiService.get('/ai/default-system-message')
    aiSystemMessage.value = response.message || ''
    aiSystemMessageOriginal.value = response.message || ''
    aiSystemMessageChanged.value = false
  } catch (error) {
    console.error('Failed to load AI system message:', error)
    toast.add({
      severity: 'error',
      summary: 'Error',
      detail: 'Failed to load AI system message',
      life: 3000
    })
  }
}

const loadBuiltInDefault = async () => {
  loadingBuiltInDefault.value = true
  try {
    const response = await apiService.get('/ai/builtin-system-message')
    aiSystemMessage.value = response.message || ''
    aiSystemMessageChanged.value = aiSystemMessage.value !== aiSystemMessageOriginal.value
  } catch (error) {
    console.error('Failed to load built-in default:', error)
    toast.add({
      severity: 'error',
      summary: 'Error',
      detail: 'Failed to load built-in default',
      life: 3000
    })
  } finally {
    loadingBuiltInDefault.value = false
  }
}

const saveAISystemMessage = async () => {
  savingAIMessage.value = true
  try {
    const value = aiSystemMessage.value?.trim() || ''
    await apiService.put('/admin/settings/ai.default-system-message', { value })

    aiSystemMessageOriginal.value = aiSystemMessage.value
    aiSystemMessageChanged.value = false

    toast.add({
      severity: 'success',
      summary: 'Success',
      detail: 'AI system message updated successfully',
      life: 3000
    })
  } catch (error) {
    console.error('Failed to save AI system message:', error)
    const errorMessage = error.response?.data?.message || error.message || 'Failed to save AI system message'
    toast.add({
      severity: 'error',
      summary: 'Error',
      detail: errorMessage,
      life: 3000
    })
  } finally {
    savingAIMessage.value = false
  }
}

onMounted(async () => {
  aiSettings.value = await loadSettings('ai')
  await loadAISettings()
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

/* AI Settings Specific Styles */
.ai-setting-description {
  padding: 0 1rem;
  margin-bottom: 1.5rem;
}

.ai-setting-description p {
  margin: 0;
  line-height: 1.6;
}

.ai-message-container {
  padding: 0 1rem;
}

.ai-message-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 0.75rem;
}

.ai-message-label {
  font-weight: 600;
  font-size: 1rem;
  color: var(--gp-text-primary);
}

.ai-message-actions {
  display: flex;
  gap: 0.5rem;
}

.ai-system-message-input {
  width: 100%;
}

/* Mobile Responsive */
@media (max-width: 768px) {
  .ai-message-header {
    flex-direction: column;
    align-items: flex-start;
    gap: 0.75rem;
  }

  .ai-message-actions {
    width: 100%;
  }

  .ai-message-actions :deep(.p-button) {
    flex: 1;
  }
}
</style>
