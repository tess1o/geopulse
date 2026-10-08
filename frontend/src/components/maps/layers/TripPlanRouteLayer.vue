<template>
  <component
    :is="activeComponent"
    :map="map"
    :legs="legs"
    :visible="visible"
    :color="appearance.defaultPathColor"
    :width="appearance.pathWidth"
    :outline="appearance.outlineEnabled"
  />
</template>

<script setup>
import { computed } from 'vue'
import RasterTripPlanRouteLayer from '@/maps/raster/layers/RasterTripPlanRouteLayer.vue'
import { getVectorEngine } from '@/maps/runtime/vectorEngineRegistry'
import { MAP_RENDER_MODES, resolveMapEngineModeFromInstance } from '@/maps/contracts/mapContracts'
import { useMapAppearance } from '@/composables/useMapAppearance'

/**
 * The line through a trip's planned stops, in plan order. Each leg is either routed along roads
 * (solid) or a straight connection (dashed) - the fallback when routing is unavailable or failed,
 * and the placeholder while routed legs load.
 *
 * Styled like the timeline path (colour, width and outline from the viewer's map appearance
 * preferences, see PathLayer), so a plan reads the same as a recorded trip.
 */
const props = defineProps({
  map: {
    type: Object,
    required: true
  },
  /** [{ fromItemId, toItemId, routed, coordinates: [[lat, lon], ...] }] */
  legs: {
    type: Array,
    default: () => []
  },
  visible: {
    type: Boolean,
    default: true
  }
})

const appearance = useMapAppearance()
const mapMode = computed(() => resolveMapEngineModeFromInstance(props.map, MAP_RENDER_MODES.RASTER))
const activeComponent = computed(() => mapMode.value === MAP_RENDER_MODES.VECTOR ? getVectorEngine().VectorTripPlanRouteLayer : RasterTripPlanRouteLayer)
</script>
