<template>
  <component
    :is="activeComponent"
    ref="implRef"
    :map="map"
    :visible="visible"
    :marker-options="markerOptions"
    :photos="photos"
    :auth-token="authToken"
    @photo-click="(payload) => emit('photo-click', payload)"
    @cluster-click="(payload) => emit('cluster-click', payload)"
    @photo-hover="(payload) => emit('photo-hover', payload)"
    @error="(payload) => emit('error', payload)"
    v-bind="groupsChangeListener"
  />
</template>

<script setup>
import { computed, ref } from 'vue'
import RasterImmichLayer from '@/maps/raster/layers/RasterImmichLayer.vue'
import { getVectorEngine } from '@/maps/runtime/vectorEngineRegistry'
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
  },
  photos: {
    type: Array,
    default: null
  },
  authToken: {
    type: String,
    default: null
  }
})

const emit = defineEmits(['photo-click', 'cluster-click', 'photo-hover', 'error', 'groups-change'])

const implRef = ref(null)
const mapMode = computed(() => resolveMapEngineModeFromInstance(props.map, MAP_RENDER_MODES.RASTER))
const groupsChangeListener = computed(() => (
  mapMode.value === MAP_RENDER_MODES.VECTOR ? { onGroupsChange: () => emit('groups-change') } : {}
))
const activeComponent = computed(() => mapMode.value === MAP_RENDER_MODES.VECTOR ? getVectorEngine().VectorImmichLayer : RasterImmichLayer)

const refreshPhotos = (...args) => implRef.value?.refreshPhotos?.(...args)
const clearPhotoMarkers = (...args) => implRef.value?.clearPhotoMarkers?.(...args)

const getCurrentGroups = () => implRef.value?.getCurrentGroups?.() ?? []
const getRenderedEntities = () => implRef.value?.getRenderedEntities?.() ?? []
const setExcludedGroupIndices = (indices) => implRef.value?.setExcludedGroupIndices?.(indices)

defineExpose({
  implRef,
  getCurrentGroups,
  getRenderedEntities,
  setExcludedGroupIndices,
  refreshPhotos,
  clearPhotoMarkers,
  isLoading: computed(() => implRef.value?.isLoading ?? false)
})
</script>
