<template>
  <div class="channel-settings" role="group" :aria-label="label">
    <SettingCard :title="t('profile.notifications.channels.inApp.title')" :description="t('profile.notifications.channels.inApp.description')">
      <template #control>
        <InputSwitch :modelValue="modelValue.inAppEnabled" :disabled="readOnly" :aria-label="t('profile.notifications.channels.inApp.title')" @update:modelValue="update('inAppEnabled', $event)" />
      </template>
    </SettingCard>

    <SettingCard :title="t('profile.notifications.channels.apprise.title')" :description="t('profile.notifications.channels.apprise.description')">
      <template #control>
        <InputSwitch :modelValue="modelValue.appriseEnabled" :disabled="readOnly" :aria-label="t('profile.notifications.channels.apprise.title')" @update:modelValue="update('appriseEnabled', $event)" />
      </template>
    </SettingCard>

    <template v-if="modelValue.appriseEnabled">
      <SettingCard :title="t('profile.notifications.channels.routing.title')" :description="t('profile.notifications.channels.routing.description')">
        <template #control>
          <Dropdown class="w-full" :modelValue="modelValue.routingMode" :options="routingModes" optionLabel="label" optionValue="value" :disabled="readOnly" :aria-label="t('profile.notifications.channels.routing.title')" @update:modelValue="update('routingMode', $event)" />
        </template>
      </SettingCard>

      <SettingCard v-if="modelValue.routingMode === 'URLS'" :title="t('profile.notifications.channels.destinationUrls.title')" :description="t('profile.notifications.channels.destinationUrls.description')">
        <template #control>
          <Textarea class="w-full" :modelValue="modelValue.destination" rows="2" autoResize placeholder="tgram://TOKEN/CHAT_ID" :disabled="readOnly" :aria-label="t('profile.notifications.channels.destinationUrls.ariaLabel')" @update:modelValue="update('destination', $event)" />
        </template>
      </SettingCard>

      <template v-else>
        <SettingCard :title="t('profile.notifications.channels.configKey.title')" :description="t('profile.notifications.channels.configKey.description')">
          <template #control>
            <InputText class="w-full" :modelValue="modelValue.appriseConfigKey" :placeholder="t('profile.notifications.channels.configKey.placeholder')" :disabled="readOnly" :aria-label="t('profile.notifications.channels.configKey.ariaLabel')" @update:modelValue="update('appriseConfigKey', $event)" />
          </template>
        </SettingCard>
        <SettingCard :title="t('profile.notifications.channels.configTag.title')" :description="t('profile.notifications.channels.configTag.description')">
          <template #control>
            <InputText class="w-full" :modelValue="modelValue.appriseTag" :placeholder="t('profile.notifications.channels.configTag.placeholder')" :disabled="readOnly" :aria-label="t('profile.notifications.channels.configTag.ariaLabel')" @update:modelValue="update('appriseTag', $event)" />
          </template>
        </SettingCard>
      </template>
    </template>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import InputSwitch from 'primevue/inputswitch'
import Dropdown from 'primevue/dropdown'
import Textarea from 'primevue/textarea'
import InputText from 'primevue/inputtext'
import SettingCard from '@/components/ui/forms/SettingCard.vue'
const { t } = useI18n()
const props = defineProps({ modelValue: { type: Object, required: true }, label: String, readOnly: Boolean })
const emit = defineEmits(['update:modelValue'])
// Keys, not text: PrimeVue's optionLabel reads a field, so labels resolve here.
const routingModes = computed(() => [
  { label: t('profile.notifications.channels.routing.urls'), value: 'URLS' },
  { label: t('profile.notifications.channels.routing.keyTag'), value: 'KEY_TAG' }
])
const update = (key, value) => emit('update:modelValue', { ...props.modelValue, [key]: value })
</script>
