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
        <label for="note-title">Title</label>
        <InputText id="note-title" v-model="form.title" placeholder="Optional title" :disabled="saving" />
      </div>

      <div v-if="!isEditing && memosConfigured" class="form-field">
        <label for="note-destination">Save to</label>
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
        <label for="note-visibility">Memos visibility</label>
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
        <label for="note-content">Note</label>
        <Textarea
          id="note-content"
          v-model="form.contentMarkdown"
          rows="8"
          autoResize
          placeholder="Write a note..."
          :disabled="saving"
          :invalid="submitted && !form.contentMarkdown.trim()"
        />
        <small v-if="submitted && !form.contentMarkdown.trim()" class="error-message">Note content is required</small>
      </div>
    </form>

    <template #footer>
      <Button label="Cancel" outlined :disabled="saving" @click="internalVisible = false" />
      <Button :label="saveLabel" icon="pi pi-save" :loading="saving" @click="save" />
    </template>
  </Dialog>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import Dialog from 'primevue/dialog'
import Button from 'primevue/button'
import InputText from 'primevue/inputtext'
import Select from 'primevue/select'
import Textarea from 'primevue/textarea'
import { useToast } from 'primevue/usetoast'
import { useNotesStore } from '@/stores/notes'

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

const emit = defineEmits(['update:visible', 'close', 'saved'])
const toast = useToast()
const notesStore = useNotesStore()
const saving = ref(false)
const submitted = ref(false)
const dialogStyle = {
  width: 'min(760px, calc(100vw - 32px))'
}
const isEditing = computed(() => props.note?.source === 'GEOPULSE' && props.note?.id != null)
const dialogHeader = computed(() => isEditing.value ? 'Edit Note' : 'Add Note')
const saveLabel = computed(() => isEditing.value ? 'Update' : 'Save')

const destinationOptions = [
  { label: 'GeoPulse', value: 'GEOPULSE' },
  { label: 'Memos', value: 'MEMOS' }
]

const visibilityOptions = [
  { label: 'Private', value: 'PRIVATE' },
  { label: 'Protected', value: 'PROTECTED' },
  { label: 'Public', value: 'PUBLIC' }
]

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
      summary: isEditing.value ? 'Note updated' : 'Note saved',
      detail: isEditing.value
        ? 'GeoPulse note was updated'
        : form.value.destination === 'MEMOS' ? 'Note was created in Memos' : 'Note was saved in GeoPulse',
      life: 3000
    })
    emit('saved', saved)
    internalVisible.value = false
  } catch (error) {
    toast.add({
      severity: 'error',
      summary: 'Save failed',
      detail: error.userMessage || error.message || 'Failed to save note',
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
