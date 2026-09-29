<template>
  <component
    :is="activeComponent"
    ref="implRef"
    :map="map"
    :samples="samples"
    :visible="visible"
    :highlighted-item="highlightedItem"
    v-bind="vectorOnlyBindings"
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
  },
  // Vector only: let the cross-type pass place (group/hide) weather markers.
  managed: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['groups-change'])

const implRef = ref(null)
const mapMode = computed(() => resolveMapEngineModeFromInstance(props.map, MAP_RENDER_MODES.RASTER))
const activeComponent = computed(() => mapMode.value === MAP_RENDER_MODES.VECTOR ? VectorWeatherLayer : RasterWeatherLayer)
const vectorOnlyBindings = computed(() => (
  mapMode.value === MAP_RENDER_MODES.VECTOR
    ? { managed: props.managed, onGroupsChange: () => emit('groups-change') }
    : {}
))

const getPlacementInput = () => implRef.value?.getPlacementInput?.() ?? { samples: [], highlightedIndices: new Set() }
const setPlacedGroups = (groups) => implRef.value?.setPlacedGroups?.(groups)

defineExpose({
  implRef,
  getPlacementInput,
  setPlacedGroups
})
</script>
