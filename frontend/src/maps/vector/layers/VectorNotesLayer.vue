<template>
  <NotesViewerDialog
    v-model:visible="notesViewerVisible"
    :notes="selectedNotes"
    :can-manage="canManageNotes"
    @close="handleNotesViewerClose"
    @note-updated="handleNoteChanged"
    @note-deleted="handleNoteChanged"
  />
</template>

<script setup>
import { computed, onMounted, onUnmounted, readonly, ref, watch } from 'vue'
import maplibregl from 'maplibre-gl'
import NotesViewerDialog from '@/components/timeline/NotesViewerDialog.vue'
import { useDateRangeStore } from '@/stores/dateRange'
import { useNotesStore } from '@/stores/notes'
import { isMapLibreMap } from '@/maps/vector/utils/maplibreLayerUtils'
import { createNoteMarkerHtml, getNoteIdentityKey, groupNotesByCoordinate } from '@/maps/shared/noteMapMarkers'
import '@/styles/note-map-markers.css'

const props = defineProps({
  map: {
    type: Object,
    required: true
  },
  visible: {
    type: Boolean,
    default: true
  },
  notes: {
    type: Array,
    default: null
  },
  loadNotes: {
    type: Boolean,
    default: true
  },
  canManageNotes: {
    type: Boolean,
    default: true
  }
})

const emit = defineEmits(['error'])

const notesStore = useNotesStore()
const dateRangeStore = useDateRangeStore()
const loading = ref(false)
const selectedNotes = ref([])
const notesViewerVisible = ref(false)
const markers = []

const effectiveNotes = computed(() => (
  Array.isArray(props.notes) ? props.notes : notesStore.notes
))

const canFetchNotes = computed(() => props.loadNotes && !Array.isArray(props.notes))

const clearNoteMarkers = () => {
  while (markers.length > 0) {
    const marker = markers.pop()
    marker?.remove?.()
  }
}

const openNotesViewer = (notes) => {
  selectedNotes.value = Array.isArray(notes) ? notes.slice() : []
  notesViewerVisible.value = selectedNotes.value.length > 0
}

const handleNotesViewerClose = () => {
  selectedNotes.value = []
}

const syncSelectedNotes = (selectedKeys = selectedNotes.value.map(getNoteIdentityKey)) => {
  if (!notesViewerVisible.value) {
    return
  }

  const keySet = new Set(selectedKeys)
  selectedNotes.value = effectiveNotes.value.filter((note) => keySet.has(getNoteIdentityKey(note)))

  if (selectedNotes.value.length === 0) {
    notesViewerVisible.value = false
  }
}

const renderNoteMarkers = () => {
  if (!isMapLibreMap(props.map) || !props.visible) {
    return
  }

  clearNoteMarkers()

  groupNotesByCoordinate(effectiveNotes.value).forEach((group) => {
    const element = document.createElement('div')
    element.className = 'gp-note-marker-wrapper'
    element.innerHTML = createNoteMarkerHtml(group.notes.length)
    element.addEventListener('click', (event) => {
      event.stopPropagation()
      openNotesViewer(group.notes)
    })

    const marker = new maplibregl.Marker({
      element,
      anchor: 'center'
    })
      .setLngLat([group.longitude, group.latitude])
      .addTo(props.map)

    markers.push(marker)
  })
}

const fetchAndRenderNotes = async (forceRefresh = false) => {
  if (!isMapLibreMap(props.map)) {
    return
  }

  if (!canFetchNotes.value) {
    renderNoteMarkers()
    return
  }

  if (!dateRangeStore.hasDateRange) {
    renderNoteMarkers()
    return
  }

  try {
    loading.value = true
    await notesStore.fetchNotes(null, null, {
      includeExternal: true,
      forceRefresh
    })
    renderNoteMarkers()
  } catch (error) {
    emit('error', {
      type: forceRefresh ? 'refresh' : 'fetch',
      message: error.userMessage || error.message || 'Failed to load notes',
      error
    })
  } finally {
    loading.value = false
  }
}

const handleNoteChanged = async () => {
  const selectedKeys = selectedNotes.value.map(getNoteIdentityKey)

  if (canFetchNotes.value) {
    await fetchAndRenderNotes(true)
  } else {
    renderNoteMarkers()
  }

  syncSelectedNotes(selectedKeys)
}

watch(
  effectiveNotes,
  () => {
    renderNoteMarkers()
    syncSelectedNotes()
  },
  { deep: false }
)

watch(
  () => dateRangeStore.getCurrentDateRange,
  async (newRange) => {
    if (newRange && props.visible) {
      await fetchAndRenderNotes()
    }
  },
  { deep: true }
)

watch(
  () => props.visible,
  async (newVisible) => {
    if (!newVisible) {
      clearNoteMarkers()
      return
    }
    await fetchAndRenderNotes()
  }
)

watch(
  () => props.map,
  () => {
    if (props.visible) {
      renderNoteMarkers()
    }
  }
)

onMounted(async () => {
  if (props.visible) {
    await fetchAndRenderNotes()
  }
})

onUnmounted(() => {
  clearNoteMarkers()
})

defineExpose({
  refreshNotes: () => fetchAndRenderNotes(true),
  clearNoteMarkers,
  isLoading: readonly(loading)
})
</script>
