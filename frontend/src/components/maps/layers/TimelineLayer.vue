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
    @marker-contextmenu="(payload) => emit('marker-contextmenu', payload)"
    v-bind="vectorOnlyBindings"
  />
</template>

<script setup>
import { computed, ref } from 'vue'
import RasterTimelineLayer from '@/maps/raster/layers/RasterTimelineLayer.vue'
import { getVectorEngine } from '@/maps/runtime/vectorEngineRegistry'
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
  },
  // Stay weather (Map<timelineIndex, samples[]>); only the vector layer renders it.
  itemWeather: {
    type: Map,
    default: null
  }
})

const emit = defineEmits(['marker-click', 'marker-hover', 'marker-contextmenu', 'groups-change'])

const implRef = ref(null)
const mapMode = computed(() => resolveMapEngineModeFromInstance(props.map, MAP_RENDER_MODES.RASTER))
const vectorOnlyBindings = computed(() => (
  mapMode.value === MAP_RENDER_MODES.VECTOR
    ? { itemWeather: props.itemWeather, onGroupsChange: () => emit('groups-change') }
    : {}
))
const activeComponent = computed(() => mapMode.value === MAP_RENDER_MODES.VECTOR ? getVectorEngine().VectorTimelineLayer : RasterTimelineLayer)

const getCurrentGroups = () => implRef.value?.getCurrentGroups?.() ?? []
const getRenderedEntities = () => implRef.value?.getRenderedEntities?.() ?? []
const setExcludedGroupIndices = (indices) => implRef.value?.setExcludedGroupIndices?.(indices)

defineExpose({
  implRef,
  getCurrentGroups,
  getRenderedEntities,
  setExcludedGroupIndices
})
</script>
