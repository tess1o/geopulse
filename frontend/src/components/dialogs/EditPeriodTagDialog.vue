<template>
  <Dialog
    v-model:visible="dialogVisible"
    modal
    :header="dialogHeader"
    :style="{ width: '600px' }"
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
      <!-- Tag Name -->
      <div class="col-12">
        <label for="tagName" class="gp-text-secondary" style="display: block; margin-bottom: var(--gp-spacing-xs)">
          Tag Name *
        </label>
        <InputText
          id="tagName"
          v-model="form.tagName"
          placeholder="e.g., Spain Vacation, Work Trip to NYC"
          class="w-full gp-input"
          :class="{ 'p-invalid': errors.tagName }"
          :disabled="isActiveOwnTracksTag"
        />
        <small v-if="errors.tagName" class="p-error">{{ errors.tagName }}</small>
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
            {{ form.tagName || 'Preview' }}
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
        @click="updatePeriodTag"
        :loading="isLoading"
        :disabled="isActiveOwnTracksTag"
      />
    </template>
  </Dialog>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { useToast } from 'primevue/usetoast'
import { usePeriodTagsStore } from '@/stores/periodTags'
import { usePeriodTag } from '@/composables/usePeriodTag'
import Dialog from 'primevue/dialog'
import Button from 'primevue/button'
import InputText from 'primevue/inputtext'
import DatePicker from 'primevue/datepicker'
import ColorPicker from 'primevue/colorpicker'
import Message from 'primevue/message'

const props = defineProps({
  visible: Boolean,
  periodTag: {
    type: Object,
    default: null
  }
})

const emit = defineEmits(['update:visible', 'updated'])

const toast = useToast()
const store = usePeriodTagsStore()

// Use period tag composable
const {
  getRandomColor,
  formatColorWithHash,
  createDisplayColor,
  validateTagName,
  validateDateRange
} = usePeriodTag()

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
  return props.periodTag?.source === 'owntracks' && props.periodTag?.isActive === true
})

const dialogHeader = computed(() => {
  if (isActiveOwnTracksTag.value) {
    return 'View Period Tag (Read-Only)'
  }
  return 'Edit Period Tag'
})

const displayColor = createDisplayColor(computed(() => form.value?.color))

// Methods
const loadForm = () => {
  if (!props.periodTag) return

  // Load form data
  form.value = {
    tagName: props.periodTag.tagName,
    color: props.periodTag.color || '#FF6B6B'
  }

  // Set date range
  if (props.periodTag.startTime && props.periodTag.endTime) {
    dateRange.value = [
      new Date(props.periodTag.startTime),
      new Date(props.periodTag.endTime)
    ]
  }
}

const validate = () => {
  errors.value = {}

  const tagNameError = validateTagName(form.value.tagName)
  if (tagNameError) {
    errors.value.tagName = tagNameError
  }

  const dateRangeError = validateDateRange(dateRange.value)
  if (dateRangeError) {
    errors.value.dateRange = dateRangeError
  }

  return Object.keys(errors.value).length === 0
}

const updatePeriodTag = async () => {
  if (!validate()) return

  isLoading.value = true

  try {
    const data = {
      tagName: form.value.tagName.trim(),
      startTime: dateRange.value[0].toISOString(),
      endTime: dateRange.value[1].toISOString(),
      color: formatColorWithHash(form.value.color)
    }

    await store.updatePeriodTag(props.periodTag.id, data)

    toast.add({
      severity: 'success',
      summary: 'Updated',
      detail: 'Period tag updated successfully',
      life: 3000
    })

    emit('updated')
  } catch (error) {
    toast.add({
      severity: 'error',
      summary: 'Error',
      detail: error.message || 'Failed to update period tag',
      life: 3000
    })
  } finally {
    isLoading.value = false
  }
}

// Watch for periodTag changes
watch(() => props.periodTag, (newVal) => {
  if (newVal && props.visible) {
    loadForm()
  }
}, { deep: true })
</script>

<style scoped>
/* Dialog uses global styles */
</style>
