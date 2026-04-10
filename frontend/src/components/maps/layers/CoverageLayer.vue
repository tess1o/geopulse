<template>
  <component
    :is="activeComponent"
    :map="map"
    :cells="cells"
    :grid-meters="gridMeters"
    :visible="visible"
    :fill-color="fillColor"
    :min-opacity="minOpacity"
    :max-opacity="maxOpacity"
    :max-cells-to-render="maxCellsToRender"
  />
</template>

<script setup>
import { computed } from 'vue'
import RasterCoverageLayer from '@/maps/raster/layers/RasterCoverageLayer.vue'
import VectorCoverageLayer from '@/maps/vector/layers/VectorCoverageLayer.vue'
import { MAP_RENDER_MODES, resolveMapEngineModeFromInstance } from '@/maps/contracts/mapContracts'

const props = defineProps({
  map: {
    type: Object,
    default: null
  },
  cells: {
    type: Array,
    default: () => []
  },
  gridMeters: {
    type: Number,
    default: 50
  },
  visible: {
    type: Boolean,
    default: true
  },
  fillColor: {
    type: String,
    default: '#1d4ed8'
  },
  minOpacity: {
    type: Number,
    default: 0.15
  },
  maxOpacity: {
    type: Number,
    default: 0.85
  },
  maxCellsToRender: {
    type: Number,
    default: 12000
  }
})

const mapMode = computed(() => resolveMapEngineModeFromInstance(props.map, MAP_RENDER_MODES.RASTER))
const activeComponent = computed(() => mapMode.value === MAP_RENDER_MODES.VECTOR ? VectorCoverageLayer : RasterCoverageLayer)
</script>
