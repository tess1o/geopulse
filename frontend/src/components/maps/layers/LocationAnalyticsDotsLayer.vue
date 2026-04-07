<template>
  <component
    :is="activeComponent"
    ref="implRef"
    :map="map"
    :places="places"
    :visible="visible"
    :selected-place-key="selectedPlaceKey"
    :hovered-place-key="hoveredPlaceKey"
    @marker-click="(payload) => emit('marker-click', payload)"
    @open-place-details="(payload) => emit('open-place-details', payload)"
  />
</template>

<script setup>
import { computed, ref } from 'vue'
import RasterLocationAnalyticsDotsLayer from '@/maps/raster/layers/RasterLocationAnalyticsDotsLayer.vue'
import VectorLocationAnalyticsDotsLayer from '@/maps/vector/layers/VectorLocationAnalyticsDotsLayer.vue'
import { MAP_RENDER_MODES, resolveMapEngineModeFromInstance } from '@/maps/contracts/mapContracts'

const props = defineProps({
  map: {
    type: Object,
    required: true
  },
  places: {
    type: Array,
    default: () => []
  },
  visible: {
    type: Boolean,
    default: true
  },
  selectedPlaceKey: {
    type: String,
    default: null
  },
  hoveredPlaceKey: {
    type: String,
    default: null
  }
})

const emit = defineEmits(['marker-click', 'open-place-details'])

const implRef = ref(null)
const mapMode = computed(() => resolveMapEngineModeFromInstance(props.map, MAP_RENDER_MODES.RASTER))
const activeComponent = computed(() => mapMode.value === MAP_RENDER_MODES.VECTOR ? VectorLocationAnalyticsDotsLayer : RasterLocationAnalyticsDotsLayer)

defineExpose({
  implRef
})
</script>
