<template>
  <div class="map-container-wrapper">
    <BaseMap
      ref="mapRef"
      :map-id="mapId"
      :center="center"
      :zoom="zoom"
      :options="mapOptions"
      :height="height"
      :width="width"
      :custom-tile-url="customTileUrl"
      :is-shared-view="isSharedView"
      @map-ready="handleMapReady"
      @map-click="handleMapClick"
      @map-contextmenu="handleMapContextMenu"
      @map-zoom="handleMapZoom"
      @map-move="handleMapMove"
    />

    <slot name="controls" :map="map" :isReady="isReady">
      <MapControls
        v-if="map && showControls"
        :map="map"
        class="map-controls"
        v-bind="controlsProps"
      />
    </slot>

    <slot name="overlays" :map="map" :isReady="isReady" />
    
    <slot name="dialogs" :map="map" :isReady="isReady" />
  </div>
</template>

<script setup>
import { ref, readonly } from 'vue'
import BaseMap from './BaseMap.vue'
import MapControls from './controls/MapControls.vue'

const props = defineProps({
  mapId: {
    type: String,
    default: 'map-container'
  },
  center: {
    type: Array,
    default: () => [51.505, -0.09]
  },
  zoom: {
    type: Number,
    default: 13
  },
  mapOptions: {
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
  showControls: {
    type: Boolean,
    default: true
  },
  controlsProps: {
    type: Object,
    default: () => ({})
  },
  customTileUrl: {
    type: String,
    default: null
  },
  isSharedView: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits([
  'map-ready',
  'map-click',
  'map-contextmenu',
  'map-zoom',
  'map-move'
])

// Reactive state
const mapRef = ref(null)
const map = ref(null)
const isReady = ref(false)

// Event handlers
const handleMapReady = (mapInstance) => {
  map.value = mapInstance
  isReady.value = true
  emit('map-ready', mapInstance)
}

const handleMapClick = (event) => {
  emit('map-click', event)
}

const handleMapContextMenu = (event) => {
  emit('map-contextmenu', event)
}

const handleMapZoom = (data) => {
  emit('map-zoom', data)
}

const handleMapMove = (data) => {
  emit('map-move', data)
}

// Public methods
const getMap = () => map.value
const getMapRef = () => mapRef.value

const setView = (center, zoom, options) => {
  mapRef.value?.setView(center, zoom, options)
}

const fitBounds = (bounds, options) => {
  mapRef.value?.fitBounds(bounds, options)
}

const invalidateSize = () => {
  mapRef.value?.invalidateSize()
}

// Expose methods and state
defineExpose({
  map: readonly(map),
  mapRef: readonly(mapRef),
  isReady: readonly(isReady),
  getMap,
  getMapRef,
  setView,
  fitBounds,
  invalidateSize
})
</script>

<style scoped>
.map-container-wrapper {
  position: relative;
  width: 100%;
  height: 100%;
  overflow: hidden;
  background-color: var(--gp-surface-light);
}

.map-controls {
  position: absolute;
  top: var(--gp-spacing-lg, 1rem);
  right: var(--gp-spacing-lg, 1rem);
  z-index: 1000;
}

/* Responsive adjustments */
@media (max-width: 768px) {
  .map-controls {
    top: var(--gp-spacing-md, 0.75rem);
    right: var(--gp-spacing-md, 0.75rem);
  }
}

/* Dark mode */
.p-dark .map-container-wrapper {
  background-color: var(--gp-surface-dark, #1e293b);
}
</style>