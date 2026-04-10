<template>
  <component
    :is="activeComponent"
    ref="implRef"
    :map="map"
    :planned-items-data="plannedItemsData"
    :visible="visible"
    :marker-options="markerOptions"
    @plan-item-contextmenu="(payload) => emit('plan-item-contextmenu', payload)"
  />
</template>

<script setup>
import { computed, ref } from 'vue'
import RasterTripPlanLayer from '@/maps/raster/layers/RasterTripPlanLayer.vue'
import VectorTripPlanLayer from '@/maps/vector/layers/VectorTripPlanLayer.vue'
import { MAP_RENDER_MODES, resolveMapEngineModeFromInstance } from '@/maps/contracts/mapContracts'

const props = defineProps({
  map: {
    type: Object,
    required: true
  },
  plannedItemsData: {
    type: Array,
    default: () => []
  },
  visible: {
    type: Boolean,
    default: true
  },
  markerOptions: {
    type: Object,
    default: () => ({})
  }
})

const emit = defineEmits(['plan-item-contextmenu'])

const implRef = ref(null)
const mapMode = computed(() => resolveMapEngineModeFromInstance(props.map, MAP_RENDER_MODES.RASTER))
const activeComponent = computed(() => mapMode.value === MAP_RENDER_MODES.VECTOR ? VectorTripPlanLayer : RasterTripPlanLayer)

const clearPlanMarkers = (...args) => implRef.value?.clearPlanMarkers?.(...args)

defineExpose({
  implRef,
  clearPlanMarkers
})
</script>
