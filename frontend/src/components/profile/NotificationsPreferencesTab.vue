<template>
  <Card class="profile-settings-card">
    <template #content>
      <form class="preferences-form settings-tab" @submit.prevent="save">
        <div class="settings-tab-header">
          <div class="settings-tab-icon"><i class="pi pi-bell"></i></div>
          <div class="settings-tab-info">
            <h3 class="settings-tab-title">Notifications</h3>
            <p class="settings-tab-description">Choose which events create alerts and how they are delivered.</p>
          </div>
        </div>

        <section class="settings-group" aria-labelledby="gps-health-heading">
          <div class="settings-group-header">
            <h3 id="gps-health-heading">GPS health</h3>
            <p>Get alerted when every active live GPS source stops sending data.</p>
          </div>

          <div class="settings-panel">
            <SettingCard title="Monitor GPS arrivals" description="Detect extended gaps across all active live sources." setting-id="gpsHealthEnabled">
              <template #control>
                <InputSwitch id="gps-health" v-model="form.gpsHealthEnabled" :disabled="readOnly" aria-label="Monitor GPS arrivals" />
              </template>
            </SettingCard>

            <SettingCard
              v-if="form.gpsHealthEnabled"
              title="Silence threshold"
              description="Wait this many minutes before creating the first alert."
              details="Choose a value from 1 minute to 7 days."
              setting-id="gpsSilenceMinutes"
            >
              <template #control>
                <InputNumber id="silence" v-model="form.gpsSilenceMinutes" :min="1" :max="10080" suffix=" min" :disabled="readOnly" fluid aria-label="GPS silence threshold in minutes" />
              </template>
            </SettingCard>

            <ChannelSettings v-if="form.gpsHealthEnabled" v-model="form.gpsHealth" label="GPS health delivery" :read-only="readOnly" />
          </div>
        </section>

        <section class="settings-group" aria-labelledby="rewind-heading">
          <div class="settings-group-header">
            <h3 id="rewind-heading">Monthly Rewind</h3>
            <p>Get a reminder when the previous month is ready to explore.</p>
          </div>

          <div class="settings-panel">
            <SettingCard title="Notify when Rewind is ready" description="Create an alert on the first day of each month." setting-id="rewindEnabled">
              <template #control>
                <InputSwitch id="rewind" v-model="form.rewindEnabled" :disabled="readOnly" aria-label="Notify when Rewind is ready" />
              </template>
            </SettingCard>
            <ChannelSettings v-if="form.rewindEnabled" v-model="form.rewind" label="Rewind delivery" :read-only="readOnly" />
          </div>
        </section>

        <section class="settings-group" aria-labelledby="product-updates-heading">
          <div class="settings-group-header">
            <h3 id="product-updates-heading">Product updates</h3>
            <p>Control in-app announcements about new GeoPulse features.</p>
          </div>

          <div class="settings-panel">
            <SettingCard title="Show release highlights" description="Show What’s New once after an upgrade." details="Release highlights appear only inside GeoPulse and are never sent externally." setting-id="whatsNewEnabled">
              <template #control>
                <InputSwitch id="whats-new" v-model="form.whatsNewEnabled" :disabled="readOnly" aria-label="Show release highlights" />
              </template>
            </SettingCard>
          </div>
        </section>

        <div class="settings-actions is-sticky"><Button type="submit" label="Save Changes" :loading="saving" :disabled="readOnly" /></div>
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
import SettingCard from '@/components/ui/forms/SettingCard.vue'

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
  if (props.readOnly) return
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
