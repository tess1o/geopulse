<template>
  <component
    :is="activeComponent"
    :map="map"
    :location="location"
  />
</template>

<script setup>
import { computed } from 'vue'
import RasterViewerLocationMarker from '@/maps/raster/markers/RasterViewerLocationMarker.vue'
import VectorViewerLocationMarker from '@/maps/vector/markers/VectorViewerLocationMarker.vue'
import { MAP_RENDER_MODES, resolveMapEngineModeFromInstance } from '@/maps/contracts/mapContracts'

const props = defineProps({
  map: {
    type: Object,
    required: true
  },
  location: {
    type: Object,
    required: true
  }
})

const mapMode = computed(() => resolveMapEngineModeFromInstance(props.map, MAP_RENDER_MODES.RASTER))

const activeComponent = computed(() => {
  return mapMode.value === MAP_RENDER_MODES.VECTOR
    ? VectorViewerLocationMarker
    : RasterViewerLocationMarker
})
</script>
