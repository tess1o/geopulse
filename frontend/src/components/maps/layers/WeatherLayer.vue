<template>
  <component
    :is="activeComponent"
    ref="implRef"
    :map="map"
    :samples="samples"
    :visible="visible"
    :highlighted-item="highlightedItem"
  />
</template>

<script setup>
import { computed, ref } from 'vue'
import RasterWeatherLayer from '@/maps/raster/layers/RasterWeatherLayer.vue'
import VectorWeatherLayer from '@/maps/vector/layers/VectorWeatherLayer.vue'
import { MAP_RENDER_MODES, resolveMapEngineModeFromInstance } from '@/maps/contracts/mapContracts'

const props = defineProps({
  map: {
    type: Object,
    required: true
  },
  samples: {
    type: Array,
    default: () => []
  },
  visible: {
    type: Boolean,
    default: false
  },
  highlightedItem: {
    type: Object,
    default: null
  }
})

const implRef = ref(null)
const mapMode = computed(() => resolveMapEngineModeFromInstance(props.map, MAP_RENDER_MODES.RASTER))
const activeComponent = computed(() => mapMode.value === MAP_RENDER_MODES.VECTOR ? VectorWeatherLayer : RasterWeatherLayer)

defineExpose({
  implRef
})
</script>
