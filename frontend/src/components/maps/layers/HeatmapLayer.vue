<template>
  <component
    :is="activeComponent"
    :map="map"
    :points="points"
    :value-key="valueKey"
    :min-weight="minWeight"
    :gamma="gamma"
    :radius="radius"
    :blur="blur"
    :min-opacity="minOpacity"
    :max="max"
    :gradient="gradient"
    :lock-max-zoom="lockMaxZoom"
    :enabled="enabled"
  />
</template>

<script setup>
import { computed } from 'vue'
import RasterHeatmapLayer from '@/maps/raster/layers/RasterHeatmapLayer.vue'
import VectorHeatmapLayer from '@/maps/vector/layers/VectorHeatmapLayer.vue'
import { MAP_RENDER_MODES, resolveMapEngineModeFromInstance } from '@/maps/contracts/mapContracts'

const props = defineProps({
  map: {
    type: Object,
    default: null
  },
  points: {
    type: Array,
    default: () => []
  },
  valueKey: {
    type: [String, Function],
    default: 'durationSeconds'
  },
  minWeight: {
    type: Number,
    default: 0.05
  },
  gamma: {
    type: Number,
    default: 0.6
  },
  radius: {
    type: Number,
    default: 32
  },
  blur: {
    type: Number,
    default: 24
  },
  minOpacity: {
    type: Number,
    default: 0.3
  },
  max: {
    type: Number,
    default: 1.0
  },
  gradient: {
    type: Object,
    default: () => ({
      0.0: '#2563eb',
      0.35: '#22c55e',
      0.6: '#eab308',
      0.8: '#f97316',
      1.0: '#dc2626'
    })
  },
  lockMaxZoom: {
    type: Boolean,
    default: true
  },
  enabled: {
    type: Boolean,
    default: true
  }
})

const mapMode = computed(() => resolveMapEngineModeFromInstance(props.map, MAP_RENDER_MODES.RASTER))
const activeComponent = computed(() => mapMode.value === MAP_RENDER_MODES.VECTOR ? VectorHeatmapLayer : RasterHeatmapLayer)
</script>
