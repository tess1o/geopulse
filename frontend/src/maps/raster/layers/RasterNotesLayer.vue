<template>
  <BaseLayer
    ref="baseLayerRef"
    :map="map"
    :visible="visible"
    @layer-ready="handleLayerReady"
  />
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
import L from 'leaflet'
import BaseLayer from '@/components/maps/layers/BaseLayer.vue'
import NotesViewerDialog from '@/components/timeline/NotesViewerDialog.vue'
import { useDateRangeStore } from '@/stores/dateRange'
import { useNotesStore } from '@/stores/notes'
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
const baseLayerRef = ref(null)
const loading = ref(false)
const selectedNotes = ref([])
const notesViewerVisible = ref(false)

const effectiveNotes = computed(() => (
  Array.isArray(props.notes) ? props.notes : notesStore.notes
))

const canFetchNotes = computed(() => props.loadNotes && !Array.isArray(props.notes))

const clearNoteMarkers = () => {
  baseLayerRef.value?.clearLayer?.()
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
  if (!baseLayerRef.value?.isReady || !props.visible) {
    return
  }

  clearNoteMarkers()

  groupNotesByCoordinate(effectiveNotes.value).forEach((group) => {
    const icon = L.divIcon({
      html: createNoteMarkerHtml(group.notes.length),
      className: 'gp-note-marker-wrapper',
      iconSize: L.point(32, 32),
      iconAnchor: L.point(16, 16),
      popupAnchor: L.point(0, -16)
    })

    const marker = L.marker([group.latitude, group.longitude], { icon })
    marker.on('click', (event) => {
      event.originalEvent?.stopPropagation?.()
      openNotesViewer(group.notes)
    })

    baseLayerRef.value?.addToLayer?.(marker)
  })
}

const fetchAndRenderNotes = async (forceRefresh = false) => {
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

const handleLayerReady = async () => {
  if (props.visible) {
    await fetchAndRenderNotes()
  }
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

onMounted(async () => {
  if (props.visible) {
    await fetchAndRenderNotes()
  }
})

onUnmounted(() => {
  clearNoteMarkers()
})

defineExpose({
  baseLayerRef,
  refreshNotes: () => fetchAndRenderNotes(true),
  clearNoteMarkers,
  isLoading: readonly(loading)
})
</script>
