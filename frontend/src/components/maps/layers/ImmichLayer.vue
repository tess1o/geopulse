<template>
  <component
    :is="activeComponent"
    ref="implRef"
    :map="map"
    :visible="visible"
    :marker-options="markerOptions"
    @photo-click="(payload) => emit('photo-click', payload)"
    @cluster-click="(payload) => emit('cluster-click', payload)"
    @photo-hover="(payload) => emit('photo-hover', payload)"
    @error="(payload) => emit('error', payload)"
  />
</template>

<script setup>
import { computed, ref } from 'vue'
import RasterImmichLayer from '@/maps/raster/layers/RasterImmichLayer.vue'
import VectorImmichLayer from '@/maps/vector/layers/VectorImmichLayer.vue'
import { MAP_RENDER_MODES, resolveMapEngineModeFromInstance } from '@/maps/contracts/mapContracts'

const props = defineProps({
  map: {
    type: Object,
    required: true
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

const emit = defineEmits(['photo-click', 'cluster-click', 'photo-hover', 'error'])

const implRef = ref(null)
const mapMode = computed(() => resolveMapEngineModeFromInstance(props.map, MAP_RENDER_MODES.RASTER))
const activeComponent = computed(() => mapMode.value === MAP_RENDER_MODES.VECTOR ? VectorImmichLayer : RasterImmichLayer)

const refreshPhotos = (...args) => implRef.value?.refreshPhotos?.(...args)
const clearPhotoMarkers = (...args) => implRef.value?.clearPhotoMarkers?.(...args)

defineExpose({
  implRef,
  refreshPhotos,
  clearPhotoMarkers,
  isLoading: computed(() => implRef.value?.isLoading ?? false)
})
</script>
