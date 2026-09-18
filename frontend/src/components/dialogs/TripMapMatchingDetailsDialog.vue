<template>
  <Dialog
    v-model:visible="internalVisible"
    header="Map Matching Details"
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
        This trip was skipped before it reached the map-matching queue, so there is no stored result to retry.
      </p>

      <div v-if="stateText.detail" class="state-detail">
        <span v-if="detailCaption">{{ detailCaption }}</span>
        <code>{{ stateText.detail }}</code>
      </div>

      <Button
        v-if="canOpenMapMatchingSettings"
        label="Open map matching settings"
        icon="pi pi-cog"
        severity="secondary"
        outlined
        @click="openMapMatchingSettings"
      />
      <p v-else-if="isProblem" class="state-hint">
        An administrator can re-run map matching from Admin → Settings → Map Matching.
      </p>
    </div>

    <template #footer>
      <Button label="Close" icon="pi pi-times" text @click="internalVisible = false" />
    </template>
  </Dialog>
</template>

<script setup>
import { computed } from 'vue'
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

const timezone = useTimezone()
const router = useRouter()
const { canViewAdmin } = storeToRefs(useAuthStore())

const STATUS_LABELS = {
  COMPLETED: 'MATCHED',
  QUEUED: 'QUEUED',
  PROCESSING: 'MATCHING',
  FAILED: 'FAILED',
  SKIPPED: 'SKIPPED'
}
const STATUS_SEVERITIES = {
  COMPLETED: 'success',
  QUEUED: 'info',
  PROCESSING: 'info',
  SKIPPED: 'warn',
  FAILED: 'danger'
}

const status = computed(() => props.info?.status || 'FAILED')
const statusLabel = computed(() => STATUS_LABELS[status.value] || status.value)
const statusSeverity = computed(() => STATUS_SEVERITIES[status.value] || 'danger')
const stateText = computed(() => describeMapMatchingState(props.info))
const isProblem = computed(() => status.value === 'FAILED' || status.value === 'SKIPPED')
// Trips judged before queueing have no target row, so the state comes from the eligibility checks rather
// than from a stored attempt.
const neverQueued = computed(() => !props.info?.targetId)
const detailCaption = computed(() => (isProblem.value ? 'Reported by the routing engine' : null))

const tripDuration = computed(() => props.trip?.tripDuration || 0)
const tripTimestamp = computed(() => {
  if (!props.trip?.timestamp) return ''
  return `${timezone.formatDateDisplay(props.trip.timestamp)} ${timezone.formatTime(props.trip.timestamp)}`
})
const completedLabel = computed(() => {
  if (!props.info?.completedAt) return null
  const when = `${timezone.formatDateDisplay(props.info.completedAt)} ${timezone.formatTime(props.info.completedAt)}`
  return isProblem.value ? `Last attempted on ${when}` : `Refined on ${when}`
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
