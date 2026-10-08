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
    :profile="profile"
    :lock-max-zoom="lockMaxZoom"
    :enabled="enabled"
  />
</template>

<script setup>
import { computed } from 'vue'
import RasterHeatmapLayer from '@/maps/raster/layers/RasterHeatmapLayer.vue'
import { getVectorEngine } from '@/maps/runtime/vectorEngineRegistry'
import { MAP_RENDER_MODES, resolveMapEngineModeFromInstance } from '@/maps/contracts/mapContracts'
import { HEATMAP_GRADIENTS } from '@/maps/shared/mapAppearance'

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
    default: () => ({ ...HEATMAP_GRADIENTS.CLASSIC })
  },
  profile: {
    type: String,
    default: 'default'
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
const activeComponent = computed(() => mapMode.value === MAP_RENDER_MODES.VECTOR ? getVectorEngine().VectorHeatmapLayer : RasterHeatmapLayer)
</script>
