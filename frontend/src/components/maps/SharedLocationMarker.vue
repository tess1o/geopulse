<template>
  <component
    :is="activeComponent"
    ref="implRef"
    :map="map"
    :latitude="latitude"
    :longitude="longitude"
    :share-data="shareData"
    :avatar-url="avatarUrl"
    :open-popup="openPopup"
  />
</template>

<script setup>
import { computed, ref } from 'vue'
import RasterSharedLocationMarker from '@/maps/raster/markers/RasterSharedLocationMarker.vue'
import VectorSharedLocationMarker from '@/maps/vector/markers/VectorSharedLocationMarker.vue'
import { MAP_RENDER_MODES, resolveMapEngineModeFromInstance } from '@/maps/contracts/mapContracts'

const props = defineProps({
  map: {
    type: Object,
    required: true
  },
  latitude: {
    type: Number,
    required: true
  },
  longitude: {
    type: Number,
    required: true
  },
  shareData: {
    type: Object,
    default: () => ({})
  },
  avatarUrl: {
    type: String,
    default: null
  },
  openPopup: {
    type: Boolean,
    default: true
  }
})

const implRef = ref(null)

const mapMode = computed(() => resolveMapEngineModeFromInstance(props.map, MAP_RENDER_MODES.RASTER))

const activeComponent = computed(() => {
  return mapMode.value === MAP_RENDER_MODES.VECTOR
    ? VectorSharedLocationMarker
    : RasterSharedLocationMarker
})

defineExpose({
  implRef
})
</script>
