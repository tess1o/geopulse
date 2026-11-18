<template>
  <component
    :is="activeComponent"
    :map="map"
    :points="points"
    :range="range"
    :inspected-index="inspectedIndex"
    :hovered-index="hoveredIndex"
    :pin-labels="pinLabels"
    @inspect="index => emit('inspect', index)"
    @hover="index => emit('hover', index)"
  />
</template>

<script setup>
import { computed } from 'vue'
import RasterGpsTimePickerLayer from '@/maps/raster/layers/RasterGpsTimePickerLayer.vue'
import { getVectorEngine } from '@/maps/runtime/vectorEngineRegistry'
import { MAP_RENDER_MODES, resolveMapEngineModeFromInstance } from '@/maps/contracts/mapContracts'

// Recorded GPS samples drawn one by one in time order (no clustering), numbered, with the track
// joining them and the chosen From/To range highlighted. Used while choosing activity times.
const props = defineProps({
  map: { type: Object, required: true },
  points: { type: Array, default: () => [] },
  range: { type: Object, default: () => ({ from: null, to: null }) },
  inspectedIndex: { type: Number, default: null },
  hoveredIndex: { type: Number, default: null },
  pinLabels: { type: Object, default: () => ({ from: 'From', to: 'To' }) }
})
const emit = defineEmits(['inspect', 'hover'])

const activeComponent = computed(() => (
  resolveMapEngineModeFromInstance(props.map, MAP_RENDER_MODES.RASTER) === MAP_RENDER_MODES.VECTOR
    ? getVectorEngine().VectorGpsTimePickerLayer
    : RasterGpsTimePickerLayer
))
</script>
