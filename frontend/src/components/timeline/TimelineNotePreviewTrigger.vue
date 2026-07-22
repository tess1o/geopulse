<template>
  <button
    v-if="hasNotes"
    type="button"
    class="note-trigger"
    :aria-label="triggerLabel"
    :title="triggerLabel"
    v-tooltip.top="triggerLabel"
    @click.stop="openNotes"
  >
    <i class="pi pi-file-edit" />
    <span>{{ notes.length }}</span>
  </button>

  <NotesViewerDialog
    v-if="hasNotes || viewerVisible"
    v-model:visible="viewerVisible"
    :notes="notes"
    :can-manage="allowManagement"
    @close="closeNotes"
    @note-updated="handleNoteChanged"
    @note-deleted="handleNoteChanged"
  />
</template>

<script setup>
import { computed, ref } from 'vue'
import NotesViewerDialog from './NotesViewerDialog.vue'

const props = defineProps({
  notes: {
    type: Array,
    default: () => []
  },
  allowManagement: {
    type: Boolean,
    default: true
  }
})

const emit = defineEmits(['note-changed'])
const viewerVisible = ref(false)
const hasNotes = computed(() => Array.isArray(props.notes) && props.notes.length > 0)
const triggerLabel = computed(() => props.notes.length === 1 ? 'Open note' : `Open ${props.notes.length} notes`)

const openNotes = () => {
  viewerVisible.value = true
}

const closeNotes = () => {
  viewerVisible.value = false
}

const handleNoteChanged = (note) => {
  emit('note-changed', note)
}

defineExpose({
  openNotes
})
</script>

<style scoped>
.note-trigger {
  border: 1px solid var(--gp-border-medium);
  border-radius: 999px;
  background: var(--gp-surface-white);
  color: var(--gp-text-primary);
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 2px 8px;
  font-size: 0.75rem;
  font-weight: 700;
  cursor: pointer;
}

.note-trigger:hover {
  background: var(--gp-surface-light);
}
</style>
