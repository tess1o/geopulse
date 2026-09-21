<template>
  <Dialog
    v-model:visible="dialogVisible"
    modal
    header="Create Timeline Label"
    class="gp-dialog-md"
    @hide="resetForm"
  >
    <div class="grid">
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
        />
        <small v-if="errors.dateRange" class="p-error">{{ errors.dateRange }}</small>
      </div>

      <!-- Color Picker -->
      <div class="col-12">
        <label class="gp-text-secondary" style="display: block; margin-bottom: var(--gp-spacing-xs)">
          Color
        </label>
        <div style="display: flex; align-items: center; gap: var(--gp-spacing-sm)">
          <ColorPicker v-model="form.color" format="hex" />
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
            text
          />
        </div>
      </div>

      <div class="col-12">
        <div class="preset-toggle">
          <Checkbox
            id="showAsPreset"
            v-model="form.showAsPreset"
            :binary="true"
          />
          <label for="showAsPreset" class="gp-text-secondary">
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
        label="Create"
        icon="pi pi-check"
        @click="createTimelineLabel"
        :loading="isLoading"
      />
    </template>
  </Dialog>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { useToast } from 'primevue/usetoast'
import { useConfirm } from 'primevue/useconfirm'
import { useTimelineLabelsStore } from '@/stores/timelineLabels'
import { useTimelineLabel } from '@/composables/useTimelineLabel'
import Dialog from 'primevue/dialog'
import Button from 'primevue/button'
import InputText from 'primevue/inputtext'
import DatePicker from 'primevue/datepicker'
import ColorPicker from 'primevue/colorpicker'
import Checkbox from 'primevue/checkbox'

const props = defineProps({
  visible: Boolean
})

const emit = defineEmits(['update:visible', 'created'])

const toast = useToast()
const confirm = useConfirm()
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

const form = ref({
  name: '',
  color: getRandomColor(),
  showAsPreset: true
})

const dateRange = ref(null)
const isLoading = ref(false)
const errors = ref({})

// Computed
const displayColor = createDisplayColor(computed(() => form.value.color))

// Methods
const validateForm = () => {
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

const createTimelineLabel = async () => {
  if (!validateForm()) {
    return
  }

  isLoading.value = true

  try {
    const normalizedRange = normalizeDateRangeForPayload(dateRange.value)

    // Check for overlaps first
    const overlappingLabels = await store.checkOverlaps(
      normalizedRange.start,
      normalizedRange.end
    )

    // If overlaps found, show confirmation dialog
    if (overlappingLabels && overlappingLabels.length > 0) {
      const overlappingNames = overlappingLabels.map(t => t.name).join(', ')

      isLoading.value = false

      confirm.require({
        message: `This label overlaps with: ${overlappingNames}. Do you want to create it anyway?`,
        header: 'Overlapping Labels Detected',
        icon: 'pi pi-exclamation-triangle',
        acceptLabel: 'Create Anyway',
        rejectLabel: 'Cancel',
        accept: async () => {
          await performCreate(normalizedRange)
        }
      })
      return
    }

    // No overlaps, proceed with creation
    await performCreate(normalizedRange)
  } catch (error) {
    toast.add({
      severity: 'error',
      summary: 'Error',
      detail: error.response?.data?.message || 'Failed to check overlaps',
      life: 3000
    })
    isLoading.value = false
  }
}

const performCreate = async (normalizedRange) => {
  isLoading.value = true

  try {
    const payload = {
      name: form.value.name.trim(),
      startTime: normalizedRange.start,
      endTime: normalizedRange.end,
      color: formatColorWithHash(form.value.color),
      showAsPreset: form.value.showAsPreset !== false
    }

    await store.createTimelineLabel(payload)

    toast.add({
      severity: 'success',
      summary: 'Created',
      detail: 'Timeline label created successfully',
      life: 3000
    })

    dialogVisible.value = false
    emit('created')
  } catch (error) {
    toast.add({
      severity: 'error',
      summary: 'Error',
      detail: error.response?.data?.message || 'Failed to create timeline label',
      life: 3000
    })
  } finally {
    isLoading.value = false
  }
}

const resetForm = () => {
  form.value = {
    name: '',
    color: getRandomColor(),
    showAsPreset: true
  }
  dateRange.value = null
  errors.value = {}
}

</script>

<style scoped>
/* Dialog uses global styles from /frontend/src/style.css */
.preset-toggle {
  display: flex;
  align-items: center;
  gap: var(--gp-spacing-sm);
}
</style>
