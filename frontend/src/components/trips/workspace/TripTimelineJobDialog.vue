<template>
  <Dialog
    v-model:visible="dialogVisible"
    modal
    :header="t('trips.workspacePage.timelineGenerationDialog.header')"
    class="gp-dialog-md timeline-generation-dialog"
    :closable="timelineJobCanClose"
    :dismissableMask="timelineJobCanClose"
    @hide="emit('hide')"
  >
    <div class="timeline-generation-content">
      <div class="timeline-generation-status-row">
        <Tag :value="timelineJobStatusLabel" :severity="timelineJobStatusSeverity" />
        <small v-if="jobId" class="timeline-generation-job-id">
          {{ t('trips.workspacePage.timelineGenerationDialog.jobId', { id: jobId }) }}
        </small>
      </div>

      <p class="timeline-generation-step">
        {{ timelineJobCurrentStep }}
      </p>

      <ProgressBar
        :value="timelineJobProgressValue"
        :showValue="false"
      />

      <div class="timeline-generation-meta">
        <span>{{ timelineJobProgressValue }}%</span>
        <span>{{ timelineJobDurationLabel }}</span>
      </div>

      <Message
        v-if="progress?.errorMessage || error"
        severity="error"
        :closable="false"
      >
        {{ progress?.errorMessage || error }}
      </Message>
      <Message
        v-else-if="refreshing"
        severity="info"
        :closable="false"
      >
        {{ t('trips.workspacePage.timelineGenerationDialog.refreshingWorkspace') }}
      </Message>
    </div>

    <template #footer>
      <Button
        v-if="timelineJobStatus === 'FAILED'"
        :label="t('trips.workspacePage.timelineGenerationDialog.retryStatus')"
        icon="pi pi-refresh"
        outlined
        :disabled="!jobId"
        @click="emit('retry')"
      />
      <Button
        :label="t('trips.workspacePage.timelineGenerationDialog.close')"
        icon="pi pi-times"
        :disabled="!timelineJobCanClose"
        @click="dialogVisible = false"
      />
    </template>
  </Dialog>
</template>

<script setup>
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import Button from 'primevue/button'
import Dialog from 'primevue/dialog'
import Message from 'primevue/message'
import ProgressBar from 'primevue/progressbar'
import Tag from 'primevue/tag'
import { formatMessageDescriptor } from '@/utils/messageDescriptor'
import { isTimelineJobDialogClosable, normalizeTimelineJobStatus } from './timelineJobDialogState'

// Progress of the timeline generation job a trip action started. It can't be closed while the job still runs.
const props = defineProps({
  visible: { type: Boolean, default: false },
  jobId: { type: [String, Number], default: null },
  progress: { type: Object, default: null },
  error: { type: String, default: null },
  refreshing: { type: Boolean, default: false }
})
const emit = defineEmits(['update:visible', 'retry', 'hide'])

const { t } = useI18n()

const dialogVisible = computed({
  get: () => props.visible,
  set: (value) => emit('update:visible', value)
})

const timelineJobStatus = computed(() => normalizeTimelineJobStatus(props.progress))
const timelineJobCanClose = computed(() => isTimelineJobDialogClosable(props))
const timelineJobStatusLabel = computed(() => {
  switch (timelineJobStatus.value) {
    case 'QUEUED':
      return t('trips.workspacePage.timelineGenerationDialog.statusQueued')
    case 'RUNNING':
      return t('trips.workspacePage.timelineGenerationDialog.statusRunning')
    case 'COMPLETED':
      return t('trips.workspacePage.timelineGenerationDialog.statusCompleted')
    case 'FAILED':
      return t('trips.workspacePage.timelineGenerationDialog.statusFailed')
    default:
      return t('trips.workspacePage.timelineGenerationDialog.statusStarting')
  }
})
const timelineJobStatusSeverity = computed(() => {
  switch (timelineJobStatus.value) {
    case 'QUEUED':
      return 'info'
    case 'RUNNING':
      return 'warn'
    case 'COMPLETED':
      return 'success'
    case 'FAILED':
      return 'danger'
    default:
      return 'secondary'
  }
})
const timelineJobCurrentStep = computed(() => (
  formatMessageDescriptor(props.progress?.currentStep)
  || (timelineJobStatus.value === 'FAILED'
    ? t('trips.workspacePage.timelineGenerationDialog.stepFailed')
    : t('trips.workspacePage.timelineGenerationDialog.stepPreparing'))
))
const timelineJobProgressValue = computed(() => {
  const raw = Number(props.progress?.progressPercentage)
  if (!Number.isFinite(raw)) return 0
  return Math.min(100, Math.max(0, Math.round(raw)))
})
const timelineJobDurationLabel = computed(() => {
  const durationMs = Number(props.progress?.durationMs)
  if (!Number.isFinite(durationMs) || durationMs < 0) {
    return t('trips.workspacePage.timelineGenerationDialog.durationDash')
  }
  return t('trips.workspacePage.timelineGenerationDialog.durationValue', { duration: formatJobDuration(durationMs) })
})

const formatJobDuration = (durationMs) => {
  const totalSeconds = Math.floor(durationMs / 1000)
  if (totalSeconds < 60) {
    return t('trips.workspacePage.timelineGenerationDialog.secondsShort', { count: totalSeconds })
  }
  const minutes = Math.floor(totalSeconds / 60)
  const seconds = totalSeconds % 60
  if (minutes < 60) {
    return seconds > 0
      ? t('trips.workspacePage.timelineGenerationDialog.minutesSecondsShort', { minutes, seconds })
      : t('trips.workspacePage.timelineGenerationDialog.minutesShort', { count: minutes })
  }
  const hours = Math.floor(minutes / 60)
  const minutesRemainder = minutes % 60
  return minutesRemainder > 0
    ? t('trips.workspacePage.timelineGenerationDialog.hoursMinutesShort', { hours, minutes: minutesRemainder })
    : t('trips.workspacePage.timelineGenerationDialog.hoursShort', { count: hours })
}
</script>

<style scoped>
.timeline-generation-content {
  display: flex;
  flex-direction: column;
  gap: var(--gp-spacing-sm);
}

.timeline-generation-status-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--gp-spacing-sm);
}

.timeline-generation-job-id {
  color: var(--gp-text-secondary);
}

.timeline-generation-step {
  margin: 0;
  color: var(--gp-text-primary);
  font-weight: 500;
}

.timeline-generation-meta {
  display: flex;
  justify-content: space-between;
  align-items: center;
  color: var(--gp-text-secondary);
  font-size: 0.85rem;
}
</style>
