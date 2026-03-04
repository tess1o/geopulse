<template>
  <Dialog
    v-model:visible="internalVisible"
    header="Edit Movement Type"
    :modal="true"
    class="gp-dialog-sm"
    @hide="$emit('close')"
  >
    <div v-if="trip" class="movement-edit-content">
      <div class="trip-meta">
        <Tag :value="trip.movementType || 'UNKNOWN'" :severity="getTransportSeverity(trip.movementType || 'UNKNOWN')" />
        <Tag
          :value="trip.movementTypeSource || 'AUTO'"
          :severity="(trip.movementTypeSource || 'AUTO') === 'MANUAL' ? 'warn' : 'success'"
        />
        <span class="trip-time">{{ formatDateTime(trip.timestamp) }}</span>
      </div>

      <Message v-if="(trip.movementType || 'UNKNOWN') === 'UNKNOWN'" severity="warn" :closable="false">
        Algorithm did not recognize this movement type. Set it manually.
      </Message>

      <div class="movement-edit-controls">
        <Select
          v-model="selectedMovementType"
          :options="movementTypeOptions"
          optionLabel="label"
          optionValue="value"
          placeholder="Select movement type"
          class="movement-select"
          :disabled="saving"
        />

        <div class="movement-edit-actions">
          <Button
            label="Save"
            icon="pi pi-save"
            :disabled="!selectedMovementType || saving"
            :loading="saving"
            @click="save"
          />
          <Button
            label="Reset"
            icon="pi pi-refresh"
            severity="secondary"
            outlined
            :disabled="!canReset || saving"
            :loading="saving"
            @click="reset"
          />
        </div>
      </div>
    </div>

    <template #footer>
      <Button label="Close" outlined @click="internalVisible = false" />
    </template>
  </Dialog>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
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
  }
})

const emit = defineEmits(['close', 'movement-updated'])

const toast = useToast()
const timezone = useTimezone()
const timelineStore = useTimelineStore()

const selectedMovementType = ref(null)
const saving = ref(false)

const movementTypeOptions = [
  { label: 'Walk', value: 'WALK' },
  { label: 'Car', value: 'CAR' },
  { label: 'Bicycle', value: 'BICYCLE' },
  { label: 'Running', value: 'RUNNING' },
  { label: 'Train', value: 'TRAIN' },
  { label: 'Flight', value: 'FLIGHT' },
  { label: 'Unknown', value: 'UNKNOWN' }
]

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
      summary: 'Movement Updated',
      detail: `Trip set to ${updated.movementType}`,
      life: 2500
    })

    internalVisible.value = false
  } catch (error) {
    console.error('Failed to update movement type:', error)
    toast.add({
      severity: 'error',
      summary: 'Update Failed',
      detail: error.message || 'Could not update movement type',
      life: 3000
    })
  } finally {
    saving.value = false
  }
}

const reset = async () => {
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
      summary: 'Movement Reset',
      detail: `Trip reset to ${updated.movementType}`,
      life: 2500
    })

    internalVisible.value = false
  } catch (error) {
    console.error('Failed to reset movement type:', error)
    toast.add({
      severity: 'error',
      summary: 'Reset Failed',
      detail: error.message || 'Could not reset movement type',
      life: 3000
    })
  } finally {
    saving.value = false
  }
}

const getTransportSeverity = (transportMode) => {
  const severityMap = {
    CAR: 'info',
    WALK: 'success',
    BICYCLE: 'info',
    RUNNING: 'success',
    TRAIN: 'info',
    FLIGHT: 'danger',
    UNKNOWN: 'secondary'
  }
  return severityMap[transportMode?.toUpperCase()] || 'secondary'
}

const formatDateTime = (timestamp) => {
  if (!timestamp) return 'Unknown time'
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
