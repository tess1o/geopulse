<template>
  <Dialog
    v-model:visible="internalVisible"
    header="Notes"
    modal
    class="notes-viewer-dialog"
    :close-on-escape="!editorVisible"
    @hide="handleHide"
  >
    <div v-if="notes.length === 0" class="notes-empty">
      No notes for this timeline item.
    </div>

    <div v-else class="notes-list">
      <article
        v-for="note in notes"
        :key="noteKey(note)"
        class="note-card"
      >
        <header class="note-header">
          <div class="note-title-block">
            <div v-if="displayTitle(note)" class="note-title">{{ displayTitle(note) }}</div>
            <div class="note-meta">
              <span class="note-source-badge" :class="sourceClass(note)">{{ sourceLabel(note) }}</span>
              <span>{{ formatDateTime(note.eventTime) }}</span>
              <span v-if="note.locationSource && note.locationSource !== 'NONE'" class="note-location-source">
                {{ formatLocationSource(note.locationSource) }}
              </span>
            </div>
          </div>
          <div class="note-actions">
            <Button
              v-if="canManageNote(note)"
              icon="pi pi-pencil"
              text
              rounded
              aria-label="Edit note"
              v-tooltip.left="'Edit note'"
              @click="editNote(note)"
            />
            <Button
              v-if="canManageNote(note)"
              icon="pi pi-trash"
              text
              rounded
              severity="danger"
              aria-label="Delete note"
              v-tooltip.left="'Delete note'"
              @click="confirmDeleteNote(note)"
            />
            <Button
              v-if="note.externalUrl"
              icon="pi pi-external-link"
              text
              rounded
              aria-label="Open in Memos"
              v-tooltip.left="'Open in Memos'"
              @click="openExternal(note.externalUrl)"
            />
          </div>
        </header>

        <div class="note-markdown" v-html="renderSafeMarkdown(noteBody(note))"></div>
        <Message v-if="note.truncated" severity="warn" :closable="false" class="note-truncated">
          This Memos note is large, so GeoPulse shows a truncated preview.
        </Message>
        <div v-if="isDeletePending(note)" class="note-delete-confirm">
          <div class="note-delete-confirm-message">
            <i class="pi pi-exclamation-triangle" aria-hidden="true" />
            <span>Delete this GeoPulse note?</span>
          </div>
          <div class="note-delete-confirm-actions">
            <Button label="Cancel" size="small" outlined :disabled="deleting" @click="cancelDelete" />
            <Button label="Delete" size="small" icon="pi pi-trash" severity="danger" :loading="deleting" @click="deleteSelectedNote" />
          </div>
        </div>
      </article>
    </div>

    <template #footer>
      <Button label="Close" outlined @click="internalVisible = false" />
    </template>
  </Dialog>

  <NoteEditorDialog
    v-model:visible="editorVisible"
    :note="selectedNote"
    @saved="handleNoteUpdated"
    @close="selectedNote = null"
  />
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import Dialog from 'primevue/dialog'
import Button from 'primevue/button'
import Message from 'primevue/message'
import { useToast } from 'primevue/usetoast'
import { useTimezone } from '@/composables/useTimezone'
import { renderSafeMarkdown } from '@/utils/safeMarkdown'
import { useNotesStore } from '@/stores/notes'
import NoteEditorDialog from './NoteEditorDialog.vue'

const props = defineProps({
  visible: {
    type: Boolean,
    default: false
  },
  notes: {
    type: Array,
    default: () => []
  },
  canManage: {
    type: Boolean,
    default: true
  }
})

const emit = defineEmits(['update:visible', 'close', 'note-updated', 'note-deleted'])
const timezone = useTimezone()
const notesStore = useNotesStore()
const toast = useToast()
const editorVisible = ref(false)
const selectedNote = ref(null)
const notePendingDelete = ref(null)
const deleting = ref(false)

function resetTransientState() {
  if (deleting.value) return
  notePendingDelete.value = null
  selectedNote.value = null
  editorVisible.value = false
}

const internalVisible = computed({
  get: () => props.visible,
  set: (value) => {
    if (!value) {
      resetTransientState()
    }
    emit('update:visible', value)
    if (!value) emit('close')
  }
})

const handleHide = () => {
  resetTransientState()
  emit('update:visible', false)
  emit('close')
}

const noteKey = (note) => {
  const identity = note?.id ?? note?.externalId ?? note?.eventTime ?? note?.createdAt ?? note?.updatedAt ?? ''
  return `${note?.source || 'note'}-${identity}`
}
const sourceLabel = (note) => note.source === 'MEMOS' ? 'Memos' : 'GeoPulse'
const sourceClass = (note) => note.source === 'MEMOS' ? 'note-source-memos' : 'note-source-geopulse'
const canManageNote = (note) => props.canManage && note?.source === 'GEOPULSE' && note?.editable !== false && note?.id != null
const formatDateTime = (value) => value ? `${timezone.formatDateDisplay(value)} ${timezone.formatTime(value)}` : ''
const formatLocationSource = (source) => {
  const labels = {
    EXPLICIT: 'Geotagged',
    DERIVED_STAY: 'Stay location',
    DERIVED_TRIP_GPS: 'Trip GPS',
    DERIVED_TRIP_INTERPOLATED: 'Trip estimate'
  }
  return labels[source] || source
}

const normalizeText = (value) => String(value || '')
  .replace(/[#*_`>\[\]()]/g, ' ')
  .replace(/\s+/g, ' ')
  .trim()

const noteBody = (note) => note.contentMarkdown || note.snippet || ''

const displayTitle = (note) => {
  const title = String(note.title || '').trim()
  if (!title) {
    return ''
  }

  const normalizedTitle = normalizeText(title)
  const normalizedBody = normalizeText(noteBody(note))

  if (!normalizedTitle || normalizedTitle === normalizedBody) {
    return ''
  }

  if (note.source === 'MEMOS' && normalizedBody.startsWith(normalizedTitle)) {
    return ''
  }

  return title
}

const openExternal = (url) => {
  window.open(url, '_blank', 'noopener,noreferrer')
}

const editNote = (note) => {
  if (!canManageNote(note)) return
  selectedNote.value = note
  editorVisible.value = true
}

const handleNoteUpdated = (note) => {
  editorVisible.value = false
  selectedNote.value = null
  emit('note-updated', note)
}

const confirmDeleteNote = (note) => {
  if (!canManageNote(note)) return

  notePendingDelete.value = note
}

const cancelDelete = () => {
  if (deleting.value) return
  notePendingDelete.value = null
}

const isDeletePending = (note) => (
  canManageNote(note)
    && notePendingDelete.value
    && noteKey(notePendingDelete.value) === noteKey(note)
)

const deleteSelectedNote = async () => {
  const note = notePendingDelete.value
  if (!canManageNote(note)) return

  deleting.value = true
  try {
    await notesStore.deleteNote(note.id)
    toast.add({
      severity: 'success',
      summary: 'Note deleted',
      detail: 'GeoPulse note was deleted',
      life: 3000
    })
    notePendingDelete.value = null
    emit('note-deleted', note)
  } catch (error) {
    toast.add({
      severity: 'error',
      summary: 'Delete failed',
      detail: error.userMessage || error.message || 'Failed to delete note',
      life: 5000
    })
  } finally {
    deleting.value = false
  }
}

watch(
  () => props.visible,
  (visible) => {
    if (!visible) {
      resetTransientState()
    }
  }
)

watch(
  () => props.notes,
  () => {
    if (!notePendingDelete.value) return

    const pendingKey = noteKey(notePendingDelete.value)
    const stillVisible = Array.isArray(props.notes)
      && props.notes.some((note) => canManageNote(note) && noteKey(note) === pendingKey)
    if (!stillVisible) {
      notePendingDelete.value = null
    }
  },
  { deep: false }
)
</script>

<style scoped>
.notes-viewer-dialog {
  width: min(720px, 94vw);
}

.notes-empty {
  color: var(--gp-text-secondary);
  padding: var(--gp-spacing-md) 0;
}

.notes-list {
  display: flex;
  flex-direction: column;
  gap: var(--gp-spacing-md);
  max-height: min(68vh, 720px);
  overflow: auto;
}

.note-card {
  border: 1px solid var(--gp-border-light);
  border-radius: var(--gp-radius-small);
  padding: var(--gp-spacing-md);
  background: var(--gp-surface-white);
}

.note-header {
  display: flex;
  justify-content: space-between;
  gap: var(--gp-spacing-sm);
  align-items: flex-start;
}

.note-actions {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  flex-shrink: 0;
}

.note-actions :deep(.p-button) {
  width: 2rem;
  height: 2rem;
}

.note-title-block {
  min-width: 0;
}

.note-title {
  color: var(--gp-text-primary);
  font-weight: 700;
  font-size: 1rem;
  line-height: 1.35;
  overflow-wrap: anywhere;
}

.note-meta {
  display: flex;
  align-items: center;
  gap: var(--gp-spacing-xs);
  flex-wrap: wrap;
  color: var(--gp-text-secondary);
  font-size: 0.85rem;
  line-height: 1.35;
}

.note-title + .note-meta {
  margin-top: 6px;
}

.note-source-badge {
  display: inline-flex;
  align-items: center;
  border-radius: 999px;
  padding: 2px 8px;
  font-size: 0.78rem;
  font-weight: 700;
  line-height: 1.25;
}

.note-source-memos {
  color: #0f5f8f;
  background: #e0f2fe;
}

.note-source-geopulse {
  color: #166534;
  background: #dcfce7;
}

.note-markdown {
  margin-top: var(--gp-spacing-sm);
  color: var(--gp-text-primary);
  font-size: 0.95rem;
  line-height: 1.6;
  overflow-wrap: anywhere;
}

.note-markdown :deep(p) {
  margin: 0 0 0.65rem;
}

.note-markdown :deep(h1),
.note-markdown :deep(h2),
.note-markdown :deep(h3) {
  margin: 0.85rem 0 0.45rem;
  font-size: 1.02rem;
  line-height: 1.35;
}

.note-markdown :deep(pre) {
  overflow: auto;
  background: var(--gp-surface-light);
  border-radius: var(--gp-radius-small);
  padding: var(--gp-spacing-sm);
}

.note-delete-confirm {
  margin-top: var(--gp-spacing-sm);
  border: 1px solid #ef4444;
  border-radius: var(--gp-radius-small);
  background: #fef2f2;
  padding: var(--gp-spacing-sm);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--gp-spacing-sm);
  color: #7f1d1d;
  line-height: 1.45;
}

.note-delete-confirm-message {
  display: inline-flex;
  align-items: center;
  gap: var(--gp-spacing-xs);
  font-weight: 600;
}

.note-delete-confirm-message i {
  color: #dc2626;
  flex-shrink: 0;
}

.note-delete-confirm-actions {
  display: inline-flex;
  align-items: center;
  gap: var(--gp-spacing-xs);
  flex-wrap: wrap;
  justify-content: flex-end;
}

.note-truncated {
  margin-top: var(--gp-spacing-sm);
}

:global(.p-dark) .note-delete-confirm {
  border-color: rgba(239, 68, 68, 0.55);
  background: rgba(127, 29, 29, 0.35);
  color: #fee2e2;
}

:global(.p-dark) .note-delete-confirm-message i {
  color: #fca5a5;
}

:global(.p-dark) .note-source-memos {
  color: #bae6fd;
  background: rgba(14, 116, 144, 0.35);
}

:global(.p-dark) .note-source-geopulse {
  color: #bbf7d0;
  background: rgba(22, 101, 52, 0.35);
}

@media (max-width: 640px) {
  .note-delete-confirm {
    align-items: flex-start;
    flex-direction: column;
  }

  .note-delete-confirm-actions {
    width: 100%;
    justify-content: flex-end;
  }
}
</style>
