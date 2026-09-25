<template>
  <div>
    <SettingSection title="Place discovery">
      <SettingItem v-for="setting in basicSettings" :key="setting.key" :setting="setting" @reset="resetSetting(setting)">
        <template #control="{ setting }">
          <InputSwitch
            v-if="setting.valueType === 'BOOLEAN'"
            v-model="setting.currentValue"
            @change="updateSetting(setting)"
          />
          <InputText
            v-else
            v-model="setting.currentValue"
            class="endpoint-input"
            @change="updateSetting(setting)"
          />
        </template>
      </SettingItem>

      <div class="section-actions">
        <Button label="Test endpoints" icon="pi pi-check" :loading="testing" @click="testEndpoints" />
      </div>

      <Message v-if="testMessage" :severity="testSuccess ? 'success' : 'error'">
        {{ testMessage }}
      </Message>
    </SettingSection>

    <details class="advanced-settings">
      <summary>Advanced settings</summary>
      <SettingSection title="Endpoints, limits and caching">
        <p class="advanced-hint">
          Endpoints can be pointed at self-hosted instances. Longer cache lifetimes mean fewer
          requests to shared public infrastructure.
        </p>
        <SettingItem v-for="setting in advancedSettings" :key="setting.key" :setting="setting" @reset="resetSetting(setting)">
          <template #control="{ setting }">
            <InputSwitch
              v-if="setting.valueType === 'BOOLEAN'"
              v-model="setting.currentValue"
              @change="updateSetting(setting)"
            />
            <InputNumber
              v-else-if="setting.valueType === 'INTEGER'"
              v-model="setting.currentValue"
              :min="1"
              class="number-input"
              @change="updateSetting(setting)"
            />
            <InputText
              v-else
              v-model="setting.currentValue"
              class="endpoint-input"
              @change="updateSetting(setting)"
            />
          </template>
        </SettingItem>
      </SettingSection>
    </details>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import Button from 'primevue/button'
import InputNumber from 'primevue/inputnumber'
import InputSwitch from 'primevue/inputswitch'
import InputText from 'primevue/inputtext'
import Message from 'primevue/message'
import SettingSection from '../SettingSection.vue'
import SettingItem from '../SettingItem.vue'
import { useAdminSettings } from '@/composables/useAdminSettings'
import { useAdminStore } from '@/stores/admin'
import { formatApiErrorDetail } from '@/utils/apiErrorDetail'

const { loadSettings, updateSetting, resetSetting } = useAdminSettings()
const adminStore = useAdminStore()

const settings = ref([])
const testing = ref(false)
const testSuccess = ref(false)
const testMessage = ref('')

// The user-agent sits with the basics on purpose: it is the setting most likely to get a
// self-hoster's instance rate-limited, so it should not be buried.
const basicKeys = ['poi.enabled', 'poi.user-agent', 'poi.language', 'poi.attribution.enabled']
const advancedKeys = [
  'poi.wikidata.endpoint',
  'poi.commons.endpoint',
  'poi.max-results',
  'poi.commons.thumb-width',
  'poi.cache.ttl-days',
  'poi.cache.image-ttl-days'
]

const getSetting = (key) => settings.value.find((setting) => setting.key === key)
const mapSettings = (keys) => keys.map(getSetting).filter(Boolean)

const basicSettings = computed(() => mapSettings(basicKeys))
const advancedSettings = computed(() => mapSettings(advancedKeys))

onMounted(async () => {
  settings.value = await loadSettings('poi')
})

const testEndpoints = async () => {
  testing.value = true
  try {
    const response = await adminStore.testPoiConnection()
    testSuccess.value = response?.success ?? false
    // Report each endpoint separately: Wikidata can be down while Commons is fine, which
    // would mean places load without photos.
    const parts = []
    parts.push(response.wikidataSuccess ? 'Wikidata OK' : `Wikidata failed: ${response.wikidataDetail}`)
    parts.push(response.commonsSuccess ? 'Commons OK' : `Commons failed: ${response.commonsDetail}`)
    testMessage.value = parts.join(' · ')
  } catch (error) {
    testSuccess.value = false
    testMessage.value = formatApiErrorDetail(error, 'Endpoint test failed')
  } finally {
    testing.value = false
  }
}
</script>

<style scoped>
@import '../admin-settings-common.css';

.section-actions {
  margin-top: 1rem;
}

.endpoint-input {
  width: 100%;
  min-width: 20rem;
}

.number-input {
  width: 8rem;
}

/* Width on the InputNumber wrapper does not reach the inner input: as a flex item its
   min-width: auto keeps the intrinsic input width, which would overlap the reset tag. */
.number-input :deep(.p-inputnumber-input) {
  width: 100%;
}

.advanced-settings {
  margin: 1rem 0;
  border: 1px solid var(--gp-border-light);
  border-radius: 6px;
}

.advanced-settings summary {
  padding: 1rem;
  cursor: pointer;
  font-weight: 800;
}

.advanced-hint {
  margin: 0 0 0.25rem;
  padding: 0 1rem;
  color: var(--gp-text-secondary);
  font-size: 0.9rem;
}

@media (max-width: 768px) {
  .advanced-hint {
    padding: 0 0.5rem;
  }
}

.p-dark .advanced-settings {
  border-color: var(--gp-border-dark);
}
</style>
