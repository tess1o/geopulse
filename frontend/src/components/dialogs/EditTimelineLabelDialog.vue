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
      <strong>{{ t('miscDialogs.editTimelineLabel.activeOwnTracksTitle') }}</strong>
      <p style="margin: var(--gp-spacing-xs) 0 0 0">
        {{ t('miscDialogs.editTimelineLabel.activeOwnTracksMessage') }}
      </p>
    </Message>

    <div v-if="form" class="grid">
      <!-- Label Name -->
      <div class="col-12">
        <label for="labelName" class="gp-text-secondary" style="display: block; margin-bottom: var(--gp-spacing-xs)">
          {{ t('miscDialogs.timelineLabelForm.nameLabel') }}
        </label>
        <InputText
          id="labelName"
          v-model="form.name"
          :placeholder="t('miscDialogs.timelineLabelForm.namePlaceholder')"
          class="w-full gp-input"
          :class="{ 'p-invalid': errors.labelName }"
          :disabled="isActiveOwnTracksTag"
        />
        <small v-if="errors.labelName" class="p-error">{{ errors.labelName }}</small>
      </div>

      <!-- Date Range -->
      <div class="col-12">
        <label for="dateRange" class="gp-text-secondary" style="display: block; margin-bottom: var(--gp-spacing-xs)">
          {{ t('miscDialogs.timelineLabelForm.dateRangeLabel') }}
        </label>
        <DatePicker
          id="dateRange"
          v-model="dateRange"
          selectionMode="range"
          :manualInput="false"
          dateFormat="M d, yy"
          :placeholder="t('miscDialogs.timelineLabelForm.dateRangePlaceholder')"
          class="w-full"
          :class="{ 'p-invalid': errors.dateRange }"
          :disabled="isActiveOwnTracksTag"
        />
        <small v-if="errors.dateRange" class="p-error">{{ errors.dateRange }}</small>
      </div>

      <!-- Color Picker -->
      <div class="col-12">
        <label class="gp-text-secondary" style="display: block; margin-bottom: var(--gp-spacing-xs)">
          {{ t('miscDialogs.timelineLabelForm.colorLabel') }}
        </label>
        <div style="display: flex; align-items: center; gap: var(--gp-spacing-sm)">
          <ColorPicker v-model="form.color" format="hex" :disabled="isActiveOwnTracksTag" />
          <div
            class="gp-period-badge"
            :style="{ backgroundColor: displayColor }"
            style="font-size: 0.75rem"
          >
            {{ form.name || t('miscDialogs.timelineLabelForm.colorPreview') }}
          </div>
          <Button
            :label="t('miscDialogs.timelineLabelForm.randomButton')"
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
            {{ t('miscDialogs.timelineLabelForm.showAsPresetLabel') }}
          </label>
        </div>
        <small class="gp-text-secondary">
          {{ t('miscDialogs.timelineLabelForm.showAsPresetHint') }}
        </small>
      </div>
    </div>

    <template #footer>
      <Button
        :label="t('miscDialogs.timelineLabelForm.cancel')"
        icon="pi pi-times"
        @click="dialogVisible = false"
        outlined
      />
      <Button
        :label="t('miscDialogs.editTimelineLabel.updateButton')"
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
import { useI18n } from 'vue-i18n'
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

const { t } = useI18n()

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
    return t('miscDialogs.editTimelineLabel.readOnlyHeader')
  }
  return t('miscDialogs.editTimelineLabel.header')
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
      summary: t('miscDialogs.editTimelineLabel.toasts.updatedSummary'),
      detail: t('miscDialogs.editTimelineLabel.toasts.updatedDetail'),
      life: 3000
    })

    emit('updated')
  } catch (error) {
    toast.add({
      severity: 'error',
      summary: t('common.error'),
      detail: error.message || t('miscDialogs.editTimelineLabel.toasts.updateFailedFallback'),
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
