<template>
  <div class="channel">
    <h3>{{ label }}</h3>
    <div class="row"><label>Show in inbox</label><InputSwitch :modelValue="modelValue.inAppEnabled" :disabled="readOnly" @update:modelValue="update('inAppEnabled', $event)" /></div>
    <div class="row"><label>Send through Apprise</label><InputSwitch :modelValue="modelValue.appriseEnabled" :disabled="readOnly" @update:modelValue="update('appriseEnabled', $event)" /></div>
    <template v-if="modelValue.appriseEnabled">
      <Dropdown :modelValue="modelValue.routingMode" :options="routingModes" optionLabel="label" optionValue="value" :disabled="readOnly" @update:modelValue="update('routingMode', $event)" />
      <Textarea v-if="modelValue.routingMode === 'URLS'" :modelValue="modelValue.destination" rows="2" autoResize placeholder="tgram://TOKEN/CHAT_ID" :disabled="readOnly" @update:modelValue="update('destination', $event)" />
      <template v-else>
        <InputText :modelValue="modelValue.appriseConfigKey" placeholder="Apprise config key" :disabled="readOnly" @update:modelValue="update('appriseConfigKey', $event)" />
        <InputText :modelValue="modelValue.appriseTag" placeholder="Optional Apprise tag" :disabled="readOnly" @update:modelValue="update('appriseTag', $event)" />
      </template>
    </template>
  </div>
</template>

<script setup>
import InputSwitch from 'primevue/inputswitch'
import Dropdown from 'primevue/dropdown'
import Textarea from 'primevue/textarea'
import InputText from 'primevue/inputtext'
const props = defineProps({ modelValue: { type: Object, required: true }, label: String, readOnly: Boolean })
const emit = defineEmits(['update:modelValue'])
const routingModes = [{ label: 'Destination URL(s)', value: 'URLS' }, { label: 'Apprise config key and tag', value: 'KEY_TAG' }]
const update = (key, value) => emit('update:modelValue', { ...props.modelValue, [key]: value })
</script>

<style scoped>
.channel { display: grid; gap: .7rem; padding: 1rem; border: 1px solid var(--gp-border-light); border-radius: .5rem; }
h3 { margin: 0; font-size: 1rem; }.row { display: flex; justify-content: space-between; gap: 1rem; align-items: center; }
</style>
