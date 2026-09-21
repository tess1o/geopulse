<template>
  <Dialog
    v-model:visible="dialogVisible"
    modal
    :header="dialogHeader"
    class="gp-dialog-md"
    @show="loadForm"
  >
    <!-- OwnTracks Tag Warning -->
    <Message v-if="isActiveOwnTracksTag" severity="warn" :closable="false" style="margin-bottom: var(--gp-spacing-md)">
      <strong>Active OwnTracks Tag</strong>
      <p style="margin: var(--gp-spacing-xs) 0 0 0">
        This tag is currently being managed by OwnTracks and cannot be edited while active.
        You can edit it after it's completed (when you change tags in OwnTracks).
      </p>
    </Message>

    <div v-if="form" class="grid">
      <!-- Label Name -->
      <div class="col-12">
        <label for="labelName" class="gp-text-secondary" style="display: block; margin-bottom: var(--gp-spacing-xs)">
          Label Name *
        </label>
        <InputText
          id="labelName"
          v-model="form.name"
          placeholder="e.g., Spain Vacation, Work Trip to NYC"
          class="w-full gp-input"
          :class="{ 'p-invalid': errors.labelName }"
          :disabled="isActiveOwnTracksTag"
        />
        <small v-if="errors.labelName" class="p-error">{{ errors.labelName }}</small>
      </div>

      <!-- Date Range -->
      <div class="col-12">
        <label for="dateRange" class="gp-text-secondary" style="display: block; margin-bottom: var(--gp-spacing-xs)">
          Date Range *
        </label>
        <DatePicker
          id="dateRange"
          v-model="dateRange"
          selectionMode="range"
          :manualInput="false"
          dateFormat="M d, yy"
          placeholder="Select start and end dates"
          class="w-full"
          :class="{ 'p-invalid': errors.dateRange }"
          :disabled="isActiveOwnTracksTag"
        />
        <small v-if="errors.dateRange" class="p-error">{{ errors.dateRange }}</small>
      </div>

      <!-- Color Picker -->
      <div class="col-12">
        <label class="gp-text-secondary" style="display: block; margin-bottom: var(--gp-spacing-xs)">
          Color
        </label>
        <div style="display: flex; align-items: center; gap: var(--gp-spacing-sm)">
          <ColorPicker v-model="form.color" format="hex" :disabled="isActiveOwnTracksTag" />
          <div
            class="gp-period-badge"
            :style="{ backgroundColor: displayColor }"
            style="font-size: 0.75rem"
          >
            {{ form.name || 'Preview' }}
          </div>
          <Button
            label="Random"
            icon="pi pi-refresh"
            size="small"
            @click="form.color = getRandomColor()"
            :disabled="isActiveOwnTracksTag"
            text
          />
        </div>
      </div>

      <div class="col-12">
        <div class="preset-toggle">
          <Checkbox
            id="editShowAsPreset"
            v-model="form.showAsPreset"
            :binary="true"
            :disabled="isActiveOwnTracksTag"
          />
          <label for="editShowAsPreset" class="gp-text-secondary">
            Show as date preset
          </label>
        </div>
        <small class="gp-text-secondary">
          When enabled, this label appears in DatePicker preset dropdowns.
        </small>
      </div>
    </div>

    <template #footer>
      <Button
        label="Cancel"
        icon="pi pi-times"
        @click="dialogVisible = false"
        outlined
      />
      <Button
        label="Update"
        icon="pi pi-check"
        @click="updateTimelineLabel"
        :loading="isLoading"
        :disabled="isActiveOwnTracksTag"
      />
    </template>
  </Dialog>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { useToast } from 'primevue/usetoast'
import { useTimelineLabelsStore } from '@/stores/timelineLabels'
import { useTimelineLabel } from '@/composables/useTimelineLabel'
import Dialog from 'primevue/dialog'
import Button from 'primevue/button'
import InputText from 'primevue/inputtext'
import DatePicker from 'primevue/datepicker'
import ColorPicker from 'primevue/colorpicker'
import Message from 'primevue/message'
import Checkbox from 'primevue/checkbox'

const props = defineProps({
  visible: Boolean,
  timelineLabel: {
    type: Object,
    default: null
  }
})

const emit = defineEmits(['update:visible', 'updated'])

const toast = useToast()
const store = useTimelineLabelsStore()

// Use timeline label composable
const {
  getRandomColor,
  formatColorWithHash,
  createDisplayColor,
  validateLabelName,
  validateDateRange,
  normalizeDateRangeForPayload
} = useTimelineLabel()

// State
const dialogVisible = computed({
  get: () => props.visible,
  set: (value) => emit('update:visible', value)
})

const form = ref(null)
const dateRange = ref(null)
const isLoading = ref(false)
const errors = ref({})

// Computed
const isActiveOwnTracksTag = computed(() => {
  return props.timelineLabel?.source === 'owntracks' && props.timelineLabel?.isActive === true
})

const dialogHeader = computed(() => {
  if (isActiveOwnTracksTag.value) {
    return 'View Timeline Label (Read-Only)'
  }
  return 'Edit Timeline Label'
})

const displayColor = createDisplayColor(computed(() => form.value?.color))

// Methods
const loadForm = () => {
  if (!props.timelineLabel) return

  // Load form data
  form.value = {
    name: props.timelineLabel.name,
    color: props.timelineLabel.color || '#FF6B6B',
    showAsPreset: props.timelineLabel.showAsPreset !== false
  }

  // Set date range
  if (props.timelineLabel.startTime && props.timelineLabel.endTime) {
    dateRange.value = [
      new Date(props.timelineLabel.startTime),
      new Date(props.timelineLabel.endTime)
    ]
  }
}

const validate = () => {
  errors.value = {}

  const nameError = validateLabelName(form.value.name)
  if (nameError) {
    errors.value.labelName = nameError
  }

  const dateRangeError = validateDateRange(dateRange.value)
  if (dateRangeError) {
    errors.value.dateRange = dateRangeError
  }

  return Object.keys(errors.value).length === 0
}

const updateTimelineLabel = async () => {
  if (!validate()) return

  isLoading.value = true

  try {
    const normalizedRange = normalizeDateRangeForPayload(dateRange.value)
    const data = {
      name: form.value.name.trim(),
      startTime: normalizedRange.start,
      endTime: normalizedRange.end,
      color: formatColorWithHash(form.value.color),
      showAsPreset: form.value.showAsPreset !== false
    }

    await store.updateTimelineLabel(props.timelineLabel.id, data)

    toast.add({
      severity: 'success',
      summary: 'Updated',
      detail: 'Timeline label updated successfully',
      life: 3000
    })

    emit('updated')
  } catch (error) {
    toast.add({
      severity: 'error',
      summary: 'Error',
      detail: error.message || 'Failed to update timeline label',
      life: 3000
    })
  } finally {
    isLoading.value = false
  }
}

// Watch for timelineLabel changes
watch(() => props.timelineLabel, (newVal) => {
  if (newVal && props.visible) {
    loadForm()
  }
}, { deep: true })
</script>

<style scoped>
/* Dialog uses global styles */
.preset-toggle {
  display: flex;
  align-items: center;
  gap: var(--gp-spacing-sm);
}
</style>
