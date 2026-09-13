<template>
  <div class="channel-settings" role="group" :aria-label="label">
    <SettingCard title="Show in inbox" description="Create an alert in the GeoPulse notification inbox.">
      <template #control>
        <InputSwitch :modelValue="modelValue.inAppEnabled" :disabled="readOnly" aria-label="Show in inbox" @update:modelValue="update('inAppEnabled', $event)" />
      </template>
    </SettingCard>

    <SettingCard title="Send through Apprise" description="Forward this alert to configured external destinations.">
      <template #control>
        <InputSwitch :modelValue="modelValue.appriseEnabled" :disabled="readOnly" aria-label="Send through Apprise" @update:modelValue="update('appriseEnabled', $event)" />
      </template>
    </SettingCard>

    <template v-if="modelValue.appriseEnabled">
      <SettingCard title="Apprise routing" description="Choose how GeoPulse addresses the destination.">
        <template #control>
          <Dropdown class="w-full" :modelValue="modelValue.routingMode" :options="routingModes" optionLabel="label" optionValue="value" :disabled="readOnly" aria-label="Apprise routing" @update:modelValue="update('routingMode', $event)" />
        </template>
      </SettingCard>

      <SettingCard v-if="modelValue.routingMode === 'URLS'" title="Destination URLs" description="Enter one or more Apprise destination URLs.">
        <template #control>
          <Textarea class="w-full" :modelValue="modelValue.destination" rows="2" autoResize placeholder="tgram://TOKEN/CHAT_ID" :disabled="readOnly" aria-label="Apprise destination URLs" @update:modelValue="update('destination', $event)" />
        </template>
      </SettingCard>

      <template v-else>
        <SettingCard title="Configuration key" description="Name of the stored Apprise configuration.">
          <template #control>
            <InputText class="w-full" :modelValue="modelValue.appriseConfigKey" placeholder="Apprise config key" :disabled="readOnly" aria-label="Apprise configuration key" @update:modelValue="update('appriseConfigKey', $event)" />
          </template>
        </SettingCard>
        <SettingCard title="Configuration tag" description="Optional tag used to select configured destinations.">
          <template #control>
            <InputText class="w-full" :modelValue="modelValue.appriseTag" placeholder="Optional Apprise tag" :disabled="readOnly" aria-label="Apprise configuration tag" @update:modelValue="update('appriseTag', $event)" />
          </template>
        </SettingCard>
      </template>
    </template>
  </div>
</template>

<script setup>
import InputSwitch from 'primevue/inputswitch'
import Dropdown from 'primevue/dropdown'
import Textarea from 'primevue/textarea'
import InputText from 'primevue/inputtext'
import SettingCard from '@/components/ui/forms/SettingCard.vue'
const props = defineProps({ modelValue: { type: Object, required: true }, label: String, readOnly: Boolean })
const emit = defineEmits(['update:modelValue'])
const routingModes = [{ label: 'Destination URL(s)', value: 'URLS' }, { label: 'Apprise config key and tag', value: 'KEY_TAG' }]
const update = (key, value) => emit('update:modelValue', { ...props.modelValue, [key]: value })
</script>
