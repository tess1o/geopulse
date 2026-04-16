<template>
  <component
    :is="activeComponent"
    ref="implRef"
    :map="map"
    :path-data="pathData"
    :visible="visible"
    :highlighted-trip="highlightedTrip"
    :path-options="pathOptions"
    :replay-state="replayState"
    @path-click="(payload) => emit('path-click', payload)"
    @path-hover="(payload) => emit('path-hover', payload)"
    @trip-marker-click="(payload) => emit('trip-marker-click', payload)"
    @highlighted-trip-click="(payload) => emit('highlighted-trip-click', payload)"
    @highlighted-trip-replay-data="(payload) => emit('highlighted-trip-replay-data', payload)"
  />
</template>

<script setup>
import { computed, ref } from 'vue'
import RasterPathLayer from '@/maps/raster/layers/RasterPathLayer.vue'
import VectorPathLayer from '@/maps/vector/layers/VectorPathLayer.vue'
import { MAP_RENDER_MODES, resolveMapEngineModeFromInstance } from '@/maps/contracts/mapContracts'

const props = defineProps({
  map: {
    type: Object,
    required: true
  },
  pathData: {
    type: Array,
    default: () => []
  },
  visible: {
    type: Boolean,
    default: true
  },
  highlightedTrip: {
    type: Object,
    default: null
  },
  pathOptions: {
    type: Object,
    default: () => ({
      color: '#007bff',
      weight: 4,
      opacity: 0.8,
      smoothFactor: 1
    })
  },
  replayState: {
    type: Object,
    default: null
  }
})

const emit = defineEmits([
  'path-click',
  'path-hover',
  'trip-marker-click',
  'highlighted-trip-click',
  'highlighted-trip-replay-data'
])

const implRef = ref(null)
const mapMode = computed(() => resolveMapEngineModeFromInstance(props.map, MAP_RENDER_MODES.RASTER))
const activeComponent = computed(() => mapMode.value === MAP_RENDER_MODES.VECTOR ? VectorPathLayer : RasterPathLayer)

defineExpose({
  implRef
})
</script>
