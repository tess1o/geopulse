<template>
  <Dialog
    v-model:visible="internalVisible"
    :header="t('tripDialogs.movementTypeQuickEdit.header')"
    :modal="true"
    class="gp-dialog-sm"
    @hide="$emit('close')"
  >
    <div v-if="trip" class="movement-edit-content">
      <div class="trip-meta">
        <Tag :value="translateMovementType(trip.movementType || 'UNKNOWN')" :severity="getTransportSeverity(trip.movementType || 'UNKNOWN')" />
        <Tag
          :value="trip.movementTypeSource || 'AUTO'"
          :severity="(trip.movementTypeSource || 'AUTO') === 'MANUAL' ? 'warn' : 'success'"
        />
        <span class="trip-time">{{ formatDateTime(trip.timestamp) }}</span>
      </div>

      <Message v-if="(trip.movementType || 'UNKNOWN') === 'UNKNOWN'" severity="warn" :closable="false">
        {{ t('tripDialogs.movementTypeQuickEdit.unknownAlgorithmWarning') }}
      </Message>
      <Message v-if="readOnly" severity="error" :closable="false">
        {{ t('tripDialogs.movementTypeQuickEdit.readOnlyWarning') }}
      </Message>

      <div class="movement-edit-controls">
        <Select
          v-model="selectedMovementType"
          :options="movementTypeOptions"
          optionLabel="label"
          optionValue="value"
          :placeholder="t('tripDialogs.movementTypeQuickEdit.selectPlaceholder')"
          class="movement-select"
          :disabled="saving || readOnly"
        />

        <div class="movement-edit-actions">
          <Button
            :label="t('tripDialogs.movementTypeQuickEdit.save')"
            icon="pi pi-save"
            :disabled="readOnly || !selectedMovementType || saving"
            :loading="saving"
            @click="save"
          />
          <Button
            :label="t('tripDialogs.movementTypeQuickEdit.reset')"
            icon="pi pi-refresh"
            severity="secondary"
            outlined
            :disabled="readOnly || !canReset || saving"
            :loading="saving"
            @click="reset"
          />
        </div>
      </div>
    </div>

    <template #footer>
      <Button :label="t('tripDialogs.movementTypeQuickEdit.close')" outlined @click="internalVisible = false" />
    </template>
  </Dialog>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import Dialog from 'primevue/dialog'
import Button from 'primevue/button'
import Tag from 'primevue/tag'
import Select from 'primevue/select'
import Message from 'primevue/message'
import { useToast } from 'primevue/usetoast'
import { useTimelineStore } from '@/stores/timeline'
import { useTimezone } from '@/composables/useTimezone'

const props = defineProps({
  visible: {
    type: Boolean,
    default: false
  },
  trip: {
    type: Object,
    default: null
  },
  readOnly: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['close', 'movement-updated'])

const { t, te } = useI18n()
const toast = useToast()
const timezone = useTimezone()
const timelineStore = useTimelineStore()

const selectedMovementType = ref(null)
const saving = ref(false)

const translateMovementType = (type) => {
  if (!type) return t('common.unknown')
  return te(`movementTypes.${type}`) ? t(`movementTypes.${type}`) : type
}

const movementTypeOptions = computed(() => ([
  { label: t('movementTypes.WALK'), value: 'WALK' },
  { label: t('movementTypes.CAR'), value: 'CAR' },
  { label: t('movementTypes.MOTORCYCLE'), value: 'MOTORCYCLE' },
  { label: t('movementTypes.PUBLIC_TRANSPORT'), value: 'PUBLIC_TRANSPORT' },
  { label: t('movementTypes.BICYCLE'), value: 'BICYCLE' },
  { label: t('movementTypes.RUNNING'), value: 'RUNNING' },
  { label: t('movementTypes.TRAIN'), value: 'TRAIN' },
  { label: t('movementTypes.FLIGHT'), value: 'FLIGHT' },
  { label: t('movementTypes.BOAT'), value: 'BOAT' },
  { label: t('movementTypes.UNKNOWN'), value: 'UNKNOWN' }
]))

const internalVisible = computed({
  get: () => props.visible,
  set: (value) => {
    if (!value) emit('close')
  }
})

const canReset = computed(() => (props.trip?.movementTypeSource || 'AUTO') === 'MANUAL')

watch(
  () => [props.trip?.id, props.visible],
  () => {
    if (props.visible && props.trip) {
      selectedMovementType.value = props.trip.movementType || 'UNKNOWN'
    }
  },
  { immediate: true }
)

const save = async () => {
  if (props.readOnly) return
  if (!props.trip?.id || !selectedMovementType.value) return

  saving.value = true
  try {
    const updated = await timelineStore.updateTripMovementType(props.trip.id, selectedMovementType.value)
    if (!updated) return

    props.trip.movementType = updated.movementType
    props.trip.movementTypeSource = updated.movementTypeSource
    emit('movement-updated', updated)

    toast.add({
      severity: 'success',
      summary: t('tripDialogs.movementTypeQuickEdit.toasts.updatedSummary'),
      detail: t('tripDialogs.movementTypeQuickEdit.toasts.updatedDetail', { type: translateMovementType(updated.movementType) }),
      life: 2500
    })

    internalVisible.value = false
  } catch (error) {
    console.error('Failed to update movement type:', error)
    toast.add({
      severity: 'error',
      summary: t('tripDialogs.movementTypeQuickEdit.toasts.updateFailedSummary'),
      detail: error.message || t('tripDialogs.movementTypeQuickEdit.toasts.updateFailedFallback'),
      life: 3000
    })
  } finally {
    saving.value = false
  }
}

const reset = async () => {
  if (props.readOnly) return
  if (!props.trip?.id) return

  saving.value = true
  try {
    const updated = await timelineStore.resetTripMovementType(props.trip.id)
    if (!updated) return

    props.trip.movementType = updated.movementType
    props.trip.movementTypeSource = updated.movementTypeSource
    emit('movement-updated', updated)

    toast.add({
      severity: 'success',
      summary: t('tripDialogs.movementTypeQuickEdit.toasts.resetSummary'),
      detail: t('tripDialogs.movementTypeQuickEdit.toasts.resetDetail', { type: translateMovementType(updated.movementType) }),
      life: 2500
    })

    internalVisible.value = false
  } catch (error) {
    console.error('Failed to reset movement type:', error)
    toast.add({
      severity: 'error',
      summary: t('tripDialogs.movementTypeQuickEdit.toasts.resetFailedSummary'),
      detail: error.message || t('tripDialogs.movementTypeQuickEdit.toasts.resetFailedFallback'),
      life: 3000
    })
  } finally {
    saving.value = false
  }
}

const getTransportSeverity = (transportMode) => {
  const severityMap = {
    CAR: 'info',
    MOTORCYCLE: 'info',
    PUBLIC_TRANSPORT: 'info',
    WALK: 'success',
    BICYCLE: 'info',
    RUNNING: 'success',
    TRAIN: 'info',
    FLIGHT: 'danger',
    BOAT: 'info',
    UNKNOWN: 'secondary'
  }
  return severityMap[transportMode?.toUpperCase()] || 'secondary'
}

const formatDateTime = (timestamp) => {
  if (!timestamp) return t('tripDialogs.movementTypeQuickEdit.unknownTime')
  return `${timezone.formatDateDisplay(timestamp)} ${timezone.formatTime(timestamp)}`
}
</script>

<style scoped>
.movement-edit-content {
  display: flex;
  flex-direction: column;
  gap: var(--gp-spacing-md);
}

.trip-meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--gp-spacing-xs);
}

.trip-time {
  color: var(--gp-text-secondary);
  font-size: 0.85rem;
  margin-left: 4px;
}

.movement-edit-controls {
  display: flex;
  flex-direction: column;
  gap: var(--gp-spacing-sm);
}

.movement-select {
  width: 100%;
}

.movement-edit-actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--gp-spacing-sm);
}

@media (max-width: 768px) {
  .movement-edit-actions {
    flex-direction: column;
  }
}
</style>
