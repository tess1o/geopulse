<template>
  <BaseCard v-if="showSection" class="place-notes-card">
    <div class="place-notes-header">
      <h3 class="section-title">{{ title }}</h3>
      <div class="place-notes-header-right">
        <span v-if="notes.length > 0" class="place-notes-count">
          {{ notes.length }}
        </span>
        <Button
          v-if="notes.length > 0"
          label="View all"
          icon="pi pi-list"
          size="small"
          text
          @click="viewerVisible = true"
        />
        <Button
          v-if="notesError"
          icon="pi pi-refresh"
          aria-label="Reload notes"
          v-tooltip.left="'Reload notes'"
          size="small"
          text
          :loading="notesLoading"
          @click="refreshNotes"
        />
      </div>
    </div>

    <div v-if="notesLoading && notes.length === 0" class="place-notes-loading">
      <ProgressSpinner />
    </div>
    <div v-else-if="notes.length === 0 && !notesError" class="place-notes-empty">
      <i class="pi pi-file-edit" aria-hidden="true"></i>
      <span>{{ emptyMessage }}</span>
    </div>
    <div v-else-if="notes.length > 0" class="place-notes-list">
      <article
        v-for="note in notes"
        :key="noteKey(note)"
        class="place-note-item"
      >
        <header class="place-note-header">
          <div class="place-note-title-block">
            <div v-if="displayTitle(note)" class="place-note-title">{{ displayTitle(note) }}</div>
            <div class="place-note-meta">
              <span class="place-note-source-badge" :class="sourceClass(note)">{{ sourceLabel(note) }}</span>
              <span>{{ formatDateTime(note.eventTime) }}</span>
              <span v-if="note.locationSource && note.locationSource !== 'NONE'" class="place-note-location-source">
                {{ formatLocationSource(note.locationSource) }}
              </span>
            </div>
          </div>
          <Button
            v-if="note.externalUrl"
            icon="pi pi-external-link"
            text
            rounded
            aria-label="Open in Memos"
            v-tooltip.left="'Open in Memos'"
            @click="openExternal(note.externalUrl)"
          />
        </header>

        <div class="place-note-markdown" v-html="renderSafeMarkdown(noteBody(note))"></div>
        <div v-if="note.truncated" class="place-note-truncated">
          This Memos note is large, so GeoPulse shows a truncated preview.
        </div>
      </article>
    </div>
    <div v-if="notesLoading && notes.length > 0" class="place-notes-loading-inline">
      <ProgressSpinner stroke-width="6" />
      <span>Loading notes...</span>
    </div>
    <div v-if="notesError" class="place-notes-error">
      {{ notesError }}
    </div>
  </BaseCard>

  <NotesViewerDialog
    v-model:visible="viewerVisible"
    :notes="notes"
    :can-manage="true"
    @note-updated="handleNoteChanged"
    @note-deleted="handleNoteChanged"
  />
</template>

<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import Button from 'primevue/button'
import ProgressSpinner from 'primevue/progressspinner'
import BaseCard from '@/components/ui/base/BaseCard.vue'
import NotesViewerDialog from '@/components/timeline/NotesViewerDialog.vue'
import { useTimezone } from '@/composables/useTimezone'
import apiService from '@/utils/apiService'
import { renderSafeMarkdown } from '@/utils/safeMarkdown'

const props = defineProps({
  title: {
    type: String,
    default: 'Notes'
  },
  searchParams: {
    type: Object,
    default: null
  },
  inMemoryFilter: {
    type: Function,
    default: null
  },
  inMemoryFilterCacheKey: {
    type: String,
    default: ''
  },
  emptyMessage: {
    type: String,
    default: 'No notes found for this place.'
  }
})

const emit = defineEmits(['notes-change'])
const timezone = useTimezone()

const notes = ref([])
const notesLoading = ref(false)
const notesError = ref(null)
const viewerVisible = ref(false)
let requestSequence = 0

const unwrapApiData = (response) => {
  if (response && typeof response === 'object' && Object.prototype.hasOwnProperty.call(response, 'data')) {
    return response.data
  }
  return response
}

const toFiniteNumber = (value) => {
  const numberValue = Number(value)
  return Number.isFinite(numberValue) ? numberValue : null
}

const normalizedSearchParams = computed(() => {
  if (!props.searchParams || typeof props.searchParams !== 'object') {
    return null
  }

  const params = Object.entries(props.searchParams).reduce((acc, [key, value]) => {
    if (value !== undefined && value !== null && value !== '') {
      acc[key] = value
    }
    return acc
  }, {})

  if (!params.startTime || !params.endTime) {
    return null
  }

  return params
})

const searchSignature = computed(() => {
  if (!normalizedSearchParams.value) {
    return ''
  }
  return JSON.stringify(Object.keys(normalizedSearchParams.value).sort().reduce((acc, key) => {
    acc[key] = normalizedSearchParams.value[key]
    return acc
  }, {}))
})

const normalizedInMemoryFilterCacheKey = computed(() => String(props.inMemoryFilterCacheKey || ''))
const showSection = computed(() => !!normalizedSearchParams.value)

const noteKey = (note) => {
  const identity = note?.id ?? note?.externalId ?? note?.eventTime ?? note?.createdAt ?? note?.updatedAt ?? ''
  return `${note?.source || 'note'}-${identity}`
}

const sourceLabel = (note) => note.source === 'MEMOS' ? 'Memos' : 'GeoPulse'
const sourceClass = (note) => note.source === 'MEMOS' ? 'place-note-source-memos' : 'place-note-source-geopulse'
const formatDateTime = (value) => value ? timezone.formatDateTimeDisplay(value) : ''
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

const hasCoordinates = (note) => (
  toFiniteNumber(note?.latitude) !== null &&
  toFiniteNumber(note?.longitude) !== null
)

const sortNotesByEventTimeDesc = (items) => [...items].sort((a, b) => {
  const timeA = Date.parse(a?.eventTime || '')
  const timeB = Date.parse(b?.eventTime || '')
  if (Number.isFinite(timeA) && Number.isFinite(timeB) && timeA !== timeB) {
    return timeB - timeA
  }
  if (Number.isFinite(timeA) && !Number.isFinite(timeB)) {
    return -1
  }
  if (!Number.isFinite(timeA) && Number.isFinite(timeB)) {
    return 1
  }
  return noteKey(a).localeCompare(noteKey(b))
})

const applyLocalFilters = (items) => {
  const geotagged = items.filter(hasCoordinates)
  if (typeof props.inMemoryFilter !== 'function') {
    return geotagged
  }
  return geotagged.filter((note) => props.inMemoryFilter(note))
}

const setNotes = (nextNotes) => {
  notes.value = sortNotesByEventTimeDesc(nextNotes)
  emit('notes-change', notes.value)
}

const refreshNotes = async () => {
  const params = normalizedSearchParams.value
  if (!params) {
    notesError.value = null
    setNotes([])
    return []
  }

  const requestId = ++requestSequence
  notesLoading.value = true
  notesError.value = null

  try {
    const response = await apiService.get('/notes/search', params)
    if (requestId !== requestSequence) {
      return notes.value
    }

    const payload = unwrapApiData(response)
    const fetchedNotes = Array.isArray(payload?.notes) ? payload.notes : []
    setNotes(applyLocalFilters(fetchedNotes))
    return notes.value
  } catch (error) {
    if (requestId !== requestSequence) {
      return notes.value
    }

    setNotes([])
    notesError.value = error.userMessage || error.message || 'Failed to load notes'
    return []
  } finally {
    if (requestId === requestSequence) {
      notesLoading.value = false
    }
  }
}

const handleNoteChanged = () => {
  refreshNotes()
}

const openExternal = (url) => {
  window.open(url, '_blank', 'noopener,noreferrer')
}

watch(
  [searchSignature, normalizedInMemoryFilterCacheKey],
  () => {
    refreshNotes()
  },
  { immediate: true }
)

onBeforeUnmount(() => {
  requestSequence += 1
})

defineExpose({
  notes,
  notesLoading,
  notesError,
  refreshNotes
})
</script>

<style scoped>
.place-notes-card {
  max-width: 100%;
}

.place-notes-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--gp-spacing-md);
  margin-bottom: var(--gp-spacing-md);
}

.section-title {
  margin: 0;
  color: var(--gp-text-primary);
  font-size: 1.125rem;
  font-weight: 700;
  line-height: 1.35;
}

.place-notes-header-right {
  display: inline-flex;
  align-items: center;
  gap: var(--gp-spacing-xs);
  flex-shrink: 0;
}

.place-notes-count {
  border-radius: 999px;
  background: color-mix(in srgb, var(--gp-primary) 11%, var(--gp-surface-white));
  border: 1px solid color-mix(in srgb, var(--gp-primary) 24%, var(--gp-border-light));
  color: var(--gp-primary);
  font-size: 0.8rem;
  font-weight: 700;
  line-height: 1;
  padding: 6px 10px;
}

.place-notes-loading,
.place-notes-empty {
  min-height: 120px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--gp-spacing-sm);
  color: var(--gp-text-secondary);
  text-align: center;
}

.place-notes-empty i {
  color: var(--gp-text-muted);
  font-size: 1.4rem;
}

.place-notes-list {
  display: grid;
  gap: var(--gp-spacing-md);
  max-height: min(560px, 62vh);
  overflow: auto;
  padding-right: 2px;
}

.place-note-item {
  border: 1px solid var(--gp-border-light);
  border-radius: var(--gp-radius-medium);
  background: var(--gp-surface-white);
  padding: var(--gp-spacing-md);
}

.place-note-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--gp-spacing-sm);
}

.place-note-title-block {
  min-width: 0;
}

.place-note-title {
  color: var(--gp-text-primary);
  font-size: 1rem;
  font-weight: 700;
  line-height: 1.35;
  overflow-wrap: anywhere;
}

.place-note-meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--gp-spacing-xs);
  color: var(--gp-text-secondary);
  font-size: 0.85rem;
  line-height: 1.35;
}

.place-note-title + .place-note-meta {
  margin-top: 6px;
}

.place-note-source-badge {
  display: inline-flex;
  align-items: center;
  border-radius: 999px;
  font-size: 0.78rem;
  font-weight: 700;
  line-height: 1.25;
  padding: 2px 8px;
}

.place-note-source-memos {
  background: #e0f2fe;
  color: #0f5f8f;
}

.place-note-source-geopulse {
  background: #dcfce7;
  color: #166534;
}

.place-note-markdown {
  margin-top: var(--gp-spacing-sm);
  color: var(--gp-text-primary);
  font-size: 0.95rem;
  line-height: 1.6;
  overflow-wrap: anywhere;
}

.place-note-markdown :deep(p) {
  margin: 0 0 0.65rem;
}

.place-note-markdown :deep(p:last-child) {
  margin-bottom: 0;
}

.place-note-markdown :deep(h1),
.place-note-markdown :deep(h2),
.place-note-markdown :deep(h3) {
  margin: 0.85rem 0 0.45rem;
  color: var(--gp-text-primary);
  font-size: 1.02rem;
  line-height: 1.35;
}

.place-note-markdown :deep(pre) {
  overflow: auto;
  background: var(--gp-surface-light);
  border-radius: var(--gp-radius-small);
  padding: var(--gp-spacing-sm);
}

.place-note-truncated {
  margin-top: var(--gp-spacing-sm);
  border: 1px solid color-mix(in srgb, #f59e0b 45%, var(--gp-border-light));
  border-radius: var(--gp-radius-small);
  background: color-mix(in srgb, #f59e0b 13%, var(--gp-surface-white));
  color: #92400e;
  font-size: 0.86rem;
  line-height: 1.45;
  padding: var(--gp-spacing-sm);
}

.place-notes-loading-inline {
  margin-top: var(--gp-spacing-md);
  display: inline-flex;
  align-items: center;
  gap: var(--gp-spacing-sm);
  color: var(--gp-text-secondary);
  font-size: 0.9rem;
}

.place-notes-loading-inline :deep(.p-progressspinner) {
  width: 1.25rem;
  height: 1.25rem;
}

.place-notes-error {
  margin-top: var(--gp-spacing-md);
  border: 1px solid color-mix(in srgb, var(--gp-error) 36%, var(--gp-border-light));
  border-radius: var(--gp-radius-small);
  background: color-mix(in srgb, var(--gp-error) 9%, var(--gp-surface-white));
  color: var(--gp-error);
  font-size: 0.9rem;
  line-height: 1.45;
  padding: var(--gp-spacing-sm);
}

.p-dark .place-note-item {
  background: var(--gp-surface-darker);
  border-color: var(--gp-border-dark);
}

.p-dark .place-note-source-memos {
  background: color-mix(in srgb, #38bdf8 22%, var(--gp-surface-darker));
  color: #bae6fd;
}

.p-dark .place-note-source-geopulse {
  background: color-mix(in srgb, #22c55e 22%, var(--gp-surface-darker));
  color: #bbf7d0;
}

.p-dark .place-notes-count {
  background: color-mix(in srgb, var(--gp-primary) 20%, var(--gp-surface-darker));
  border-color: color-mix(in srgb, var(--gp-primary) 36%, var(--gp-border-dark));
}

.p-dark .place-note-truncated {
  background: color-mix(in srgb, #f59e0b 18%, var(--gp-surface-darker));
  color: #fcd34d;
}

.p-dark .place-notes-error {
  background: color-mix(in srgb, var(--gp-error) 16%, var(--gp-surface-darker));
}

@media (max-width: 640px) {
  .place-notes-header {
    align-items: flex-start;
    flex-direction: column;
  }

  .place-notes-header-right {
    width: 100%;
    justify-content: flex-start;
    flex-wrap: wrap;
  }
}
</style>
