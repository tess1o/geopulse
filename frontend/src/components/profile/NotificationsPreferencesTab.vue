<template>
  <Card>
    <template #content>
      <form class="preferences-form" @submit.prevent="save">
        <section>
          <h2>GPS health</h2>
          <p>Alert only when GeoPulse stops receiving data from every active live GPS source.</p>
          <div class="row"><label for="gps-health">Monitor GPS arrivals</label><InputSwitch id="gps-health" v-model="form.gpsHealthEnabled" :disabled="readOnly" /></div>
          <div v-if="form.gpsHealthEnabled" class="field">
            <label for="silence">Silence threshold (minutes)</label>
            <InputNumber id="silence" v-model="form.gpsSilenceMinutes" :min="1" :max="10080" :disabled="readOnly" />
            <small>The first alert waits for this period after monitoring is enabled.</small>
          </div>
          <ChannelSettings v-if="form.gpsHealthEnabled" v-model="form.gpsHealth" label="GPS health delivery" :read-only="readOnly" />
        </section>

        <section>
          <h2>Monthly Rewind</h2>
          <p>On the first day of a month, GeoPulse can remind you to explore the completed month.</p>
          <div class="row"><label for="rewind">Notify when Rewind is ready</label><InputSwitch id="rewind" v-model="form.rewindEnabled" :disabled="readOnly" /></div>
          <ChannelSettings v-if="form.rewindEnabled" v-model="form.rewind" label="Rewind delivery" :read-only="readOnly" />
        </section>

        <section class="release-note">
          <h2>What’s new</h2>
          <p>Release highlights appear once in GeoPulse after your next upgrade. They are never sent externally.</p>
          <div class="row"><label for="whats-new">Show release highlights</label><InputSwitch id="whats-new" v-model="form.whatsNewEnabled" :disabled="readOnly" /></div>
        </section>

        <div class="actions"><Button type="submit" label="Save notification preferences" :loading="saving" :disabled="readOnly" /></div>
      </form>
    </template>
  </Card>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import Card from 'primevue/card'
import InputSwitch from 'primevue/inputswitch'
import InputNumber from 'primevue/inputnumber'
import Button from 'primevue/button'
import apiService from '@/utils/apiService'
import ChannelSettings from './NotificationChannelSettings.vue'

const props = defineProps({ readOnly: Boolean })
const emit = defineEmits(['saved'])
const saving = ref(false)
const channel = () => ({ inAppEnabled: true, appriseEnabled: false, routingMode: 'URLS', destination: '', appriseConfigKey: '', appriseTag: '' })
const form = reactive({ gpsHealthEnabled: false, gpsSilenceMinutes: 60, gpsHealth: channel(), rewindEnabled: false, rewind: channel(), whatsNewEnabled: true })

const assign = (value = {}) => Object.assign(form, {
  gpsHealthEnabled: value.gpsHealthEnabled === true,
  gpsSilenceMinutes: value.gpsSilenceMinutes || 60,
  gpsHealth: { ...channel(), ...(value.gpsHealth || {}) },
  rewindEnabled: value.rewindEnabled === true,
  rewind: { ...channel(), ...(value.rewind || {}) },
  whatsNewEnabled: value.whatsNewEnabled !== false
})

onMounted(async () => {
  const response = await apiService.get('/notifications/preferences')
  assign(response?.data || response)
})

const save = async () => {
  saving.value = true
  try {
    const response = await apiService.put('/notifications/preferences', form)
    assign(response?.data || response)
    emit('saved')
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.preferences-form, section, .field { display: grid; gap: .75rem; }
.preferences-form { gap: 1.75rem; }
section h2 { margin: 0; font-size: 1.15rem; }
section p, small { color: var(--gp-text-secondary); margin: 0; }
.row { display: flex; align-items: center; justify-content: space-between; gap: 1rem; }
.field label { font-weight: 600; }
.release-note { border-top: 1px solid var(--gp-border-light); padding-top: 1.25rem; }
.actions { display: flex; justify-content: flex-end; }
</style>
