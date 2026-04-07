<template>
  <component
    :is="activeHostComponent"
    v-if="activeHostComponent"
    :key="hostKey"
    ref="engineHostRef"
    :map-id="mapId"
    :center="center"
    :zoom="zoom"
    :options="options"
    :height="height"
    :width="width"
    :custom-tile-url="customTileUrl"
    :custom-style-url="customStyleUrl"
    :map-render-mode="mapRenderMode"
    :is-shared-view="isSharedView"
    :enable-fullscreen="enableFullscreen"
    :fullscreen-options="fullscreenOptions"
    @map-ready="handleMapReady"
    @map-click="(event) => emit('map-click', event)"
    @map-contextmenu="(event) => emit('map-contextmenu', event)"
    @map-warning="handleMapWarning"
    @engine-fatal="handleEngineFatal"
  />
</template>

<script setup>
import { computed, markRaw, ref, shallowRef, watch } from 'vue'
import L from 'leaflet'
import { useAuthStore } from '@/stores/auth'
import { MAP_RENDER_MODES } from '@/maps/contracts/mapContracts'
import { resolveEffectiveMapMode } from '@/maps/runtime/mapSourceResolver'
import { resolveMapEngineComponent } from '@/maps/runtime/engineResolver'

const props = defineProps({
  mapId: {
    type: String,
    default: 'leaflet-map'
  },
  center: {
    type: Array,
    default: () => null
  },
  zoom: {
    type: Number,
    default: 13
  },
  options: {
    type: Object,
    default: () => ({})
  },
  height: {
    type: String,
    default: '100%'
  },
  width: {
    type: String,
    default: '100%'
  },
  customTileUrl: {
    type: String,
    default: null
  },
  customStyleUrl: {
    type: String,
    default: null
  },
  mapRenderMode: {
    type: String,
    default: null
  },
  isSharedView: {
    type: Boolean,
    default: false
  },
  enableFullscreen: {
    type: Boolean,
    default: true
  },
  fullscreenOptions: {
    type: Object,
    default: () => ({})
  }
})

const emit = defineEmits(['map-ready', 'map-click', 'map-contextmenu', 'map-warning'])

const authStore = useAuthStore()
const engineHostRef = ref(null)
const map = shallowRef(null)
const isReady = ref(false)
const activeHostComponent = shallowRef(null)
const activeMode = ref(MAP_RENDER_MODES.VECTOR)
const hostKey = ref('map-host-VECTOR')

let resolveRequestId = 0

const effectiveMapMode = computed(() => {
  authStore.mapRenderMode
  authStore.customMapTileUrl
  authStore.customMapStyleUrl

  return resolveEffectiveMapMode({
    isSharedView: props.isSharedView,
    overrideRenderMode: props.mapRenderMode,
    overrideTileUrl: props.customTileUrl,
    overrideStyleUrl: props.customStyleUrl
  })
})

const loadEngineForMode = async (mode) => {
  const requestId = ++resolveRequestId

  map.value = null
  isReady.value = false

  const component = await resolveMapEngineComponent(mode)
  if (requestId !== resolveRequestId) {
    return
  }

  activeMode.value = mode
  activeHostComponent.value = markRaw(component)
  hostKey.value = `map-host-${mode}-${requestId}`
}

watch(
  effectiveMapMode,
  async (mode) => {
    await loadEngineForMode(mode)
  },
  { immediate: true }
)

const handleMapReady = (mapInstance) => {
  map.value = mapInstance ? markRaw(mapInstance) : null
  isReady.value = true
  emit('map-ready', mapInstance)
}

const handleMapWarning = (warning) => {
  emit('map-warning', warning)
}

const handleEngineFatal = async (errorDetails) => {
  emit('map-warning', {
    code: errorDetails?.code || 'engine_fatal',
    message: errorDetails?.message || 'Map engine failed to initialize. Falling back to raster mode.',
    details: errorDetails
  })

  if (activeMode.value !== MAP_RENDER_MODES.RASTER) {
    await loadEngineForMode(MAP_RENDER_MODES.RASTER)
  }
}

const invalidateSize = () => {
  engineHostRef.value?.invalidateSize?.()
}

const setView = (center, zoom, options) => {
  engineHostRef.value?.setView?.(center, zoom, options)
}

const fitBounds = (bounds, options = {}) => {
  engineHostRef.value?.fitBounds?.(bounds, options)
}

defineExpose({
  map,
  isReady,
  invalidateSize,
  setView,
  fitBounds,
  L
})
</script>
