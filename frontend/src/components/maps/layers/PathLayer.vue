<template>
  <component
    :is="activeComponent"
    ref="implRef"
    :map="map"
    :path-data="pathData"
    :visible="visible"
    :highlighted-trip="highlightedTrip"
    :path-options="resolvedPathOptions"
    :highlighted-path-color="resolvedHighlightedPathColor"
    :speed-band-colors="appearance.speedBandColors"
    :highlighted-path-width="appearance.highlightedPathWidth"
    :outline="resolvedOutline"
    :replay-state="replayState"
    :focus-highlighted-trip="focusHighlightedTrip"
    :inspection-enabled="inspectionEnabled"
    :allow-path-data-trip-fallback="allowPathDataTripFallback"
    :show-highlighted-trip-popup="showHighlightedTripPopup"
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
import { useMapAppearance } from '@/composables/useMapAppearance'

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
  // Colors, width and outline default to the viewer's map appearance preferences; pass these only to override.
  pathOptions: {
    type: Object,
    default: null
  },
  highlightedPathColor: {
    type: String,
    default: null
  },
  outline: {
    type: Boolean,
    default: null
  },
  replayState: {
    type: Object,
    default: null
  },
  focusHighlightedTrip: {
    type: Boolean,
    default: true
  },
  inspectionEnabled: {
    type: Boolean,
    default: true
  },
  allowPathDataTripFallback: {
    type: Boolean,
    default: false
  },
  showHighlightedTripPopup: {
    type: Boolean,
    default: true
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
const appearance = useMapAppearance()
const resolvedPathOptions = computed(() => props.pathOptions || {
  color: appearance.value.defaultPathColor,
  weight: appearance.value.pathWidth,
  opacity: 0.8,
  smoothFactor: 1
})
const resolvedHighlightedPathColor = computed(() => props.highlightedPathColor || appearance.value.activePathColor)
const resolvedOutline = computed(() => props.outline ?? appearance.value.outlineEnabled)
const mapMode = computed(() => resolveMapEngineModeFromInstance(props.map, MAP_RENDER_MODES.RASTER))
const activeComponent = computed(() => mapMode.value === MAP_RENDER_MODES.VECTOR ? VectorPathLayer : RasterPathLayer)

const getHighlightedEndpointObstacles = () => implRef.value?.getHighlightedEndpointObstacles?.() ?? []

defineExpose({
  implRef,
  getHighlightedEndpointObstacles
})
</script>
