<template>
  <Dialog
    v-model:visible="internalVisible"
    :header="dialogHeader"
    modal
    class="note-editor-dialog"
    :style="dialogStyle"
    @hide="handleHide"
  >
    <form class="note-form" @submit.prevent="save">
      <div class="form-field">
        <label for="note-title">{{ t('timeline.noteEditor.titleLabel') }}</label>
        <InputText id="note-title" v-model="form.title" :placeholder="t('timeline.noteEditor.titlePlaceholder')" :disabled="saving" />
      </div>

      <div v-if="!isEditing && memosConfigured" class="form-field">
        <label for="note-destination">{{ t('timeline.noteEditor.saveToLabel') }}</label>
        <Select
          id="note-destination"
          v-model="form.destination"
          :options="destinationOptions"
          optionLabel="label"
          optionValue="value"
          :disabled="saving"
        />
      </div>

      <div v-if="!isEditing && form.destination === 'MEMOS'" class="form-field">
        <label for="note-visibility">{{ t('timeline.noteEditor.visibilityLabel') }}</label>
        <Select
          id="note-visibility"
          v-model="form.visibility"
          :options="visibilityOptions"
          optionLabel="label"
          optionValue="value"
          :disabled="saving"
        />
      </div>

      <div class="form-field">
        <label for="note-content">{{ t('timeline.noteEditor.noteLabel') }}</label>
        <Textarea
          id="note-content"
          v-model="form.contentMarkdown"
          rows="8"
          autoResize
          :placeholder="t('timeline.noteEditor.contentPlaceholder')"
          :disabled="saving"
          :invalid="submitted && !form.contentMarkdown.trim()"
        />
        <small v-if="submitted && !form.contentMarkdown.trim()" class="error-message">{{ t('timeline.noteEditor.contentRequired') }}</small>
      </div>
    </form>

    <template #footer>
      <Button :label="t('timeline.noteEditor.cancel')" outlined :disabled="saving" @click="internalVisible = false" />
      <Button :label="saveLabel" icon="pi pi-save" :loading="saving" @click="save" />
    </template>
  </Dialog>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import Dialog from 'primevue/dialog'
import Button from 'primevue/button'
import InputText from 'primevue/inputtext'
import Select from 'primevue/select'
import Textarea from 'primevue/textarea'
import { useToast } from 'primevue/usetoast'
import { useNotesStore } from '@/stores/notes'
import { formatApiErrorDetail } from '@/utils/apiErrorDetail'

const props = defineProps({
  visible: {
    type: Boolean,
    default: false
  },
  anchorType: {
    type: String,
    default: 'TIMESTAMP'
  },
  anchorId: {
    type: [Number, String],
    default: null
  },
  eventTime: {
    type: String,
    default: null
  },
  latitude: {
    type: Number,
    default: null
  },
  longitude: {
    type: Number,
    default: null
  },
  memosConfigured: {
    type: Boolean,
    default: false
  },
  defaultDestination: {
    type: String,
    default: 'GEOPULSE'
  },
  defaultVisibility: {
    type: String,
    default: 'PRIVATE'
  },
  note: {
    type: Object,
    default: null
  }
})

const { t } = useI18n()
const emit = defineEmits(['update:visible', 'close', 'saved'])
const toast = useToast()
const notesStore = useNotesStore()
const saving = ref(false)
const submitted = ref(false)
const dialogStyle = {
  width: 'min(760px, calc(100vw - 32px))'
}
const isEditing = computed(() => props.note?.source === 'GEOPULSE' && props.note?.id != null)
const dialogHeader = computed(() => isEditing.value ? t('timeline.noteEditor.editHeader') : t('timeline.noteEditor.addHeader'))
const saveLabel = computed(() => isEditing.value ? t('timeline.noteEditor.update') : t('timeline.noteEditor.save'))

const destinationOptions = computed(() => [
  { label: t('timeline.noteEditor.destinations.geopulse'), value: 'GEOPULSE' },
  { label: t('timeline.noteEditor.destinations.memos'), value: 'MEMOS' }
])

const visibilityOptions = computed(() => [
  { label: t('timeline.noteEditor.visibilityOptions.private'), value: 'PRIVATE' },
  { label: t('timeline.noteEditor.visibilityOptions.protected'), value: 'PROTECTED' },
  { label: t('timeline.noteEditor.visibilityOptions.public'), value: 'PUBLIC' }
])

const form = ref({
  title: '',
  contentMarkdown: '',
  destination: 'GEOPULSE',
  visibility: 'PRIVATE'
})

const internalVisible = computed({
  get: () => props.visible,
  set: (value) => {
    emit('update:visible', value)
    if (!value) emit('close')
  }
})

const handleHide = () => {
  emit('update:visible', false)
  emit('close')
}

watch(
  () => props.visible,
  (visible) => {
    if (visible) {
      form.value = {
        title: isEditing.value ? props.note?.title || '' : '',
        contentMarkdown: isEditing.value ? props.note?.contentMarkdown || '' : '',
        destination: isEditing.value ? 'GEOPULSE' : props.memosConfigured ? props.defaultDestination || 'GEOPULSE' : 'GEOPULSE',
        visibility: props.defaultVisibility || 'PRIVATE'
      }
      submitted.value = false
    }
  },
  { immediate: true }
)

const save = async () => {
  submitted.value = true
  if (!form.value.contentMarkdown.trim()) {
    return
  }

  saving.value = true
  try {
    const payload = {
      title: form.value.title?.trim() || null,
      contentMarkdown: form.value.contentMarkdown.trim()
    }

    const saved = isEditing.value
      ? await notesStore.updateNote(props.note.id, payload)
      : await notesStore.createNote({
          ...payload,
          destination: props.memosConfigured ? form.value.destination : 'GEOPULSE',
          visibility: form.value.destination === 'MEMOS' ? form.value.visibility : null,
          anchorType: props.anchorType,
          anchorId: props.anchorId ? Number(props.anchorId) : null,
          eventTime: props.eventTime,
          latitude: props.latitude,
          longitude: props.longitude
        })

    toast.add({
      severity: 'success',
      summary: isEditing.value ? t('timeline.noteEditor.toasts.updatedTitle') : t('timeline.noteEditor.toasts.savedTitle'),
      detail: isEditing.value
        ? t('timeline.noteEditor.toasts.updatedDetail')
        : form.value.destination === 'MEMOS' ? t('timeline.noteEditor.toasts.savedInMemos') : t('timeline.noteEditor.toasts.savedInGeoPulse'),
      life: 3000
    })
    emit('saved', saved)
    internalVisible.value = false
  } catch (error) {
    toast.add({
      severity: 'error',
      summary: t('timeline.noteEditor.toasts.saveFailedTitle'),
      detail: formatApiErrorDetail(error, t('timeline.noteEditor.toasts.saveFailedDetail')),
      life: 5000
    })
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
:global(.note-editor-dialog.p-dialog) {
  width: min(760px, calc(100vw - 32px));
}

.note-form {
  display: flex;
  flex-direction: column;
  gap: var(--gp-spacing-md);
}

.form-field {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.form-field label {
  color: var(--gp-text-primary);
  font-weight: 600;
}

.form-field :deep(.p-inputtext),
.form-field :deep(.p-select),
.form-field :deep(.p-textarea) {
  width: 100%;
}

.form-field :deep(.p-textarea) {
  min-height: 220px;
  resize: vertical;
}

.error-message {
  color: var(--gp-danger);
}

@media (max-width: 640px) {
  :global(.note-editor-dialog.p-dialog) {
    width: calc(100vw - 24px);
  }

  .form-field :deep(.p-textarea) {
    min-height: 180px;
  }
}
</style>
