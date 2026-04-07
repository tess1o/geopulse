<template>
  <component
    :is="activeComponent"
    ref="implRef"
    :map="map"
    :timeline-data="timelineData"
    :visible="visible"
    :highlighted-item="highlightedItem"
    :marker-options="markerOptions"
    @marker-click="(payload) => emit('marker-click', payload)"
    @marker-hover="(payload) => emit('marker-hover', payload)"
  />
</template>

<script setup>
import { computed, ref } from 'vue'
import RasterTimelineLayer from '@/maps/raster/layers/RasterTimelineLayer.vue'
import VectorTimelineLayer from '@/maps/vector/layers/VectorTimelineLayer.vue'
import { MAP_RENDER_MODES, resolveMapEngineModeFromInstance } from '@/maps/contracts/mapContracts'

const props = defineProps({
  map: {
    type: Object,
    required: true
  },
  timelineData: {
    type: Array,
    default: () => []
  },
  visible: {
    type: Boolean,
    default: true
  },
  highlightedItem: {
    type: Object,
    default: null
  },
  markerOptions: {
    type: Object,
    default: () => ({})
  }
})

const emit = defineEmits(['marker-click', 'marker-hover'])

const implRef = ref(null)
const mapMode = computed(() => resolveMapEngineModeFromInstance(props.map, MAP_RENDER_MODES.RASTER))
const activeComponent = computed(() => mapMode.value === MAP_RENDER_MODES.VECTOR ? VectorTimelineLayer : RasterTimelineLayer)

defineExpose({
  implRef
})
</script>
