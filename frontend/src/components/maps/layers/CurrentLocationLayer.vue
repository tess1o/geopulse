<template>
  <component
    :is="activeComponent"
    :map="map"
    :location="location"
  />
</template>

<script setup>
import { computed } from 'vue'
import RasterCurrentLocationLayer from '@/maps/raster/layers/RasterCurrentLocationLayer.vue'
import VectorCurrentLocationLayer from '@/maps/vector/layers/VectorCurrentLocationLayer.vue'
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
const activeComponent = computed(() => mapMode.value === MAP_RENDER_MODES.VECTOR ? VectorCurrentLocationLayer : RasterCurrentLocationLayer)
</script>
