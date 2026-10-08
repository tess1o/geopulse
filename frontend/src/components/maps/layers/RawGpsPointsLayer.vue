<template>
  <component
    :is="activeComponent"
    ref="implRef"
    :map="map"
    :points="points"
    :visible="visible"
    :resolve-location="resolveLocation"
  />
</template>

<script setup>
import { computed, ref } from 'vue'
import RasterRawGpsPointsLayer from '@/maps/raster/layers/RasterRawGpsPointsLayer.vue'
import { getVectorEngine } from '@/maps/runtime/vectorEngineRegistry'
import { MAP_RENDER_MODES, resolveMapEngineModeFromInstance } from '@/maps/contracts/mapContracts'

const props = defineProps({
  map: {
    type: Object,
    required: true
  },
  points: {
    type: Array,
    default: () => []
  },
  visible: {
    type: Boolean,
    default: true
  },
  resolveLocation: {
    type: Function,
    default: null
  }
})

const implRef = ref(null)
const mapMode = computed(() => resolveMapEngineModeFromInstance(props.map, MAP_RENDER_MODES.RASTER))
const activeComponent = computed(() => (
  mapMode.value === MAP_RENDER_MODES.VECTOR ? getVectorEngine().VectorRawGpsPointsLayer : RasterRawGpsPointsLayer
))

defineExpose({
  implRef
})
</script>
