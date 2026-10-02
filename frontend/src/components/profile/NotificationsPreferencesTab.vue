<template>
  <Card class="profile-settings-card">
    <template #content>
      <form class="preferences-form settings-tab" @submit.prevent="save">
        <div class="settings-tab-header">
          <div class="settings-tab-icon"><i class="pi pi-bell"></i></div>
          <div class="settings-tab-info">
            <h3 class="settings-tab-title">{{ t('profile.notifications.title') }}</h3>
            <p class="settings-tab-description">{{ t('profile.notifications.description') }}</p>
          </div>
        </div>

        <section class="settings-group" aria-labelledby="gps-health-heading">
          <div class="settings-group-header">
            <h3 id="gps-health-heading">{{ t('profile.notifications.gpsHealth.heading') }}</h3>
            <p>{{ t('profile.notifications.gpsHealth.description') }}</p>
          </div>

          <div class="settings-panel">
            <SettingCard :title="t('profile.notifications.gpsHealth.monitor.title')" :description="t('profile.notifications.gpsHealth.monitor.description')" setting-id="gpsHealthEnabled">
              <template #control>
                <InputSwitch id="gps-health" v-model="form.gpsHealthEnabled" :disabled="readOnly" :aria-label="t('profile.notifications.gpsHealth.monitor.title')" />
              </template>
            </SettingCard>

            <SettingCard
              v-if="form.gpsHealthEnabled"
              :title="t('profile.notifications.gpsHealth.silence.title')"
              :description="t('profile.notifications.gpsHealth.silence.description')"
              :details="t('profile.notifications.gpsHealth.silence.details')"
              setting-id="gpsSilenceMinutes"
            >
              <template #control>
                <InputNumber id="silence" v-model="form.gpsSilenceMinutes" :min="1" :max="10080" :suffix="t('profile.notifications.silenceSuffix')" :disabled="readOnly" fluid :aria-label="t('profile.notifications.gpsHealth.silence.ariaLabel')" />
              </template>
            </SettingCard>

            <ChannelSettings v-if="form.gpsHealthEnabled" v-model="form.gpsHealth" :label="t('profile.notifications.gpsHealth.channelLabel')" :read-only="readOnly" />
          </div>
        </section>

        <section class="settings-group" aria-labelledby="rewind-heading">
          <div class="settings-group-header">
            <h3 id="rewind-heading">{{ t('profile.notifications.rewind.heading') }}</h3>
            <p>{{ t('profile.notifications.rewind.description') }}</p>
          </div>

          <div class="settings-panel">
            <SettingCard :title="t('profile.notifications.rewind.notify.title')" :description="t('profile.notifications.rewind.notify.description')" setting-id="rewindEnabled">
              <template #control>
                <InputSwitch id="rewind" v-model="form.rewindEnabled" :disabled="readOnly" :aria-label="t('profile.notifications.rewind.notify.title')" />
              </template>
            </SettingCard>
            <ChannelSettings v-if="form.rewindEnabled" v-model="form.rewind" :label="t('profile.notifications.rewind.channelLabel')" :read-only="readOnly" />
          </div>
        </section>

        <section class="settings-group" aria-labelledby="product-updates-heading">
          <div class="settings-group-header">
            <h3 id="product-updates-heading">{{ t('profile.notifications.productUpdates.heading') }}</h3>
            <p>{{ t('profile.notifications.productUpdates.description') }}</p>
          </div>

          <div class="settings-panel">
            <SettingCard :title="t('profile.notifications.productUpdates.whatsNew.title')" :description="t('profile.notifications.productUpdates.whatsNew.description')" :details="t('profile.notifications.productUpdates.whatsNew.details')" setting-id="whatsNewEnabled">
              <template #control>
                <InputSwitch id="whats-new" v-model="form.whatsNewEnabled" :disabled="readOnly" :aria-label="t('profile.notifications.productUpdates.whatsNew.title')" />
              </template>
            </SettingCard>
          </div>
        </section>

        <div class="settings-actions is-sticky"><Button type="submit" :label="t('profile.notifications.saveChanges')" :loading="saving" :disabled="readOnly" /></div>
      </form>
    </template>
  </Card>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import Card from 'primevue/card'
import InputSwitch from 'primevue/inputswitch'
import InputNumber from 'primevue/inputnumber'
import Button from 'primevue/button'
import { useNotificationsStore } from '@/stores/notifications'
import ChannelSettings from './NotificationChannelSettings.vue'
import SettingCard from '@/components/ui/forms/SettingCard.vue'

const { t } = useI18n()
const props = defineProps({ readOnly: Boolean })
const emit = defineEmits(['saved'])
const saving = ref(false)
const notificationsStore = useNotificationsStore()
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
  assign(await notificationsStore.fetchPreferences())
})

const save = async () => {
  if (props.readOnly) return
  saving.value = true
  try {
    assign(await notificationsStore.updatePreferences(form))
    emit('saved')
  } finally {
    saving.value = false
  }
}
</script>
