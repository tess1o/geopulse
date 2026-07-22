<template>
  <component
    :is="activeComponent"
    ref="implRef"
    :map="map"
    :visible="visible"
    :notes="notes"
    :load-notes="loadNotes"
    :can-manage-notes="canManageNotes"
    @error="(payload) => emit('error', payload)"
  />
</template>

<script setup>
import { computed, ref } from 'vue'
import RasterNotesLayer from '@/maps/raster/layers/RasterNotesLayer.vue'
import VectorNotesLayer from '@/maps/vector/layers/VectorNotesLayer.vue'
import { MAP_RENDER_MODES, resolveMapEngineModeFromInstance } from '@/maps/contracts/mapContracts'

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

const implRef = ref(null)
const mapMode = computed(() => resolveMapEngineModeFromInstance(props.map, MAP_RENDER_MODES.RASTER))
const activeComponent = computed(() => mapMode.value === MAP_RENDER_MODES.VECTOR ? VectorNotesLayer : RasterNotesLayer)

const refreshNotes = (...args) => implRef.value?.refreshNotes?.(...args)
const clearNoteMarkers = (...args) => implRef.value?.clearNoteMarkers?.(...args)

defineExpose({
  implRef,
  refreshNotes,
  clearNoteMarkers,
  isLoading: computed(() => implRef.value?.isLoading ?? false)
})
</script>
