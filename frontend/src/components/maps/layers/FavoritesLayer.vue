<template>
  <component
    :is="activeComponent"
    ref="implRef"
    :map="map"
    :favorites-data="favoritesData"
    :visible="visible"
    :marker-options="markerOptions"
    @favorite-click="(payload) => emit('favorite-click', payload)"
    @favorite-hover="(payload) => emit('favorite-hover', payload)"
    @favorite-edit="(payload) => emit('favorite-edit', payload)"
    @favorite-delete="(payload) => emit('favorite-delete', payload)"
    @favorite-contextmenu="(payload) => emit('favorite-contextmenu', payload)"
  />
</template>

<script setup>
import { computed, ref } from 'vue'
import RasterFavoritesLayer from '@/maps/raster/layers/RasterFavoritesLayer.vue'
import VectorFavoritesLayer from '@/maps/vector/layers/VectorFavoritesLayer.vue'
import { MAP_RENDER_MODES, resolveMapEngineModeFromInstance } from '@/maps/contracts/mapContracts'

const props = defineProps({
  map: {
    type: Object,
    required: true
  },
  favoritesData: {
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

const emit = defineEmits(['favorite-click', 'favorite-hover', 'favorite-edit', 'favorite-delete', 'favorite-contextmenu'])

const implRef = ref(null)
const mapMode = computed(() => resolveMapEngineModeFromInstance(props.map, MAP_RENDER_MODES.RASTER))
const activeComponent = computed(() => mapMode.value === MAP_RENDER_MODES.VECTOR ? VectorFavoritesLayer : RasterFavoritesLayer)

const getMarkerByFavorite = (...args) => implRef.value?.getMarkerByFavorite?.(...args)
const focusOnFavorite = (...args) => implRef.value?.focusOnFavorite?.(...args)
const updateFavorite = (...args) => implRef.value?.updateFavorite?.(...args)
const removeFavorite = (...args) => implRef.value?.removeFavorite?.(...args)
const clearFavoriteMarkers = (...args) => implRef.value?.clearFavoriteMarkers?.(...args)

defineExpose({
  implRef,
  getMarkerByFavorite,
  focusOnFavorite,
  updateFavorite,
  removeFavorite,
  clearFavoriteMarkers
})
</script>
