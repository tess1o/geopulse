<template>
  <Dialog
    v-model:visible="internalVisible"
    :header="t('tripDialogs.mapMatchingDetails.header')"
    :modal="true"
    :style="{ width: '32rem' }"
    @hide="$emit('close')"
  >
    <div class="map-matching-details">
      <p v-if="tripTimestamp" class="trip-when">
        {{ tripTimestamp }}
        <span v-if="tripDuration">· {{ formatDuration(tripDuration) }}</span>
      </p>

      <div class="state-heading">
        <Tag :value="statusLabel" :severity="statusSeverity" />
        <p class="state-title">{{ stateText.title }}</p>
      </div>

      <p v-if="completedLabel" class="state-when">{{ completedLabel }}</p>

      <p v-if="neverQueued" class="state-note">
        {{ t('tripDialogs.mapMatchingDetails.neverQueuedNote') }}
      </p>

      <div v-if="stateText.detail" class="state-detail">
        <span v-if="detailCaption">{{ detailCaption }}</span>
        <code>{{ stateText.detail }}</code>
      </div>

      <Button
        v-if="canOpenMapMatchingSettings"
        :label="t('tripDialogs.mapMatchingDetails.openSettings')"
        icon="pi pi-cog"
        severity="secondary"
        outlined
        @click="openMapMatchingSettings"
      />
      <p v-else-if="isProblem" class="state-hint">
        {{ t('tripDialogs.mapMatchingDetails.adminHint') }}
      </p>
    </div>

    <template #footer>
      <Button :label="t('tripDialogs.mapMatchingDetails.close')" icon="pi pi-times" text @click="internalVisible = false" />
    </template>
  </Dialog>
</template>

<script setup>
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { storeToRefs } from 'pinia'
import { useRouter } from 'vue-router'
import Button from 'primevue/button'
import Dialog from 'primevue/dialog'
import Tag from 'primevue/tag'
import { useTimezone } from '@/composables/useTimezone'
import { useAuthStore } from '@/stores/auth'
import { formatDuration } from '@/utils/calculationsHelpers'
import { describeMapMatchingState } from '@/utils/mapMatchingDetails'

const props = defineProps({
  visible: {
    type: Boolean,
    default: false
  },
  trip: {
    type: Object,
    default: null
  },
  info: {
    type: Object,
    default: null
  }
})

const emit = defineEmits(['close'])

const internalVisible = computed({
  get: () => props.visible,
  set: (value) => {
    if (!value) emit('close')
  }
})

const { t } = useI18n()
const timezone = useTimezone()
const router = useRouter()
const { canViewAdmin } = storeToRefs(useAuthStore())

const STATUS_LABELS = computed(() => ({
  COMPLETED: t('tripDialogs.mapMatchingDetails.statusLabels.completed'),
  QUEUED: t('tripDialogs.mapMatchingDetails.statusLabels.queued'),
  PROCESSING: t('tripDialogs.mapMatchingDetails.statusLabels.processing'),
  FAILED: t('tripDialogs.mapMatchingDetails.statusLabels.failed'),
  SKIPPED: t('tripDialogs.mapMatchingDetails.statusLabels.skipped')
}))
const STATUS_SEVERITIES = {
  COMPLETED: 'success',
  QUEUED: 'info',
  PROCESSING: 'info',
  SKIPPED: 'warn',
  FAILED: 'danger'
}

const status = computed(() => props.info?.status || 'FAILED')
const statusLabel = computed(() => STATUS_LABELS.value[status.value] || status.value)
const statusSeverity = computed(() => STATUS_SEVERITIES[status.value] || 'danger')
const stateText = computed(() => describeMapMatchingState(props.info))
const isProblem = computed(() => status.value === 'FAILED' || status.value === 'SKIPPED')
// Trips judged before queueing have no target row, so the state comes from the eligibility checks rather
// than from a stored attempt.
const neverQueued = computed(() => !props.info?.targetId)
const detailCaption = computed(() => (isProblem.value ? t('tripDialogs.mapMatchingDetails.detailCaption') : null))

const tripDuration = computed(() => props.trip?.tripDuration || 0)
const tripTimestamp = computed(() => {
  if (!props.trip?.timestamp) return ''
  return `${timezone.formatDateDisplay(props.trip.timestamp)} ${timezone.formatTime(props.trip.timestamp)}`
})
const completedLabel = computed(() => {
  if (!props.info?.completedAt) return null
  const when = `${timezone.formatDateDisplay(props.info.completedAt)} ${timezone.formatTime(props.info.completedAt)}`
  return isProblem.value
    ? t('tripDialogs.mapMatchingDetails.lastAttemptedOn', { when })
    : t('tripDialogs.mapMatchingDetails.refinedOn', { when })
})

const canOpenMapMatchingSettings = computed(() => isProblem.value && canViewAdmin.value)

const openMapMatchingSettings = () => {
  emit('close')
  router.push({ path: '/app/admin/settings', query: { tab: 'map-matching' } })
}
</script>

<style scoped>
.map-matching-details { display: grid; gap: 1rem; }
.trip-when { margin: 0; color: var(--text-color-secondary); font-size: 0.9rem; }
.state-heading { display: flex; align-items: flex-start; gap: 0.75rem; }
.state-title { margin: 0; line-height: 1.5; }
.state-when { margin: 0; color: var(--text-color-secondary); font-size: 0.85rem; }
.state-note { margin: 0; color: var(--text-color-secondary); line-height: 1.5; }
.state-detail { display: grid; gap: 0.35rem; }
.state-detail > span { color: var(--text-color-secondary); font-size: 0.8rem; }
.state-detail code { display: block; padding: 0.6rem; border-radius: 0.4rem; background: var(--surface-ground); font-size: 0.8rem; overflow-wrap: anywhere; }
.state-hint { margin: 0; color: var(--text-color-secondary); font-size: 0.85rem; line-height: 1.5; }
</style>
