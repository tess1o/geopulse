<template>
  <div class="location-analytics-map">
    <div class="map-frame">
      <MapContainer
        map-id="location-analytics-map"
        :center="mapCenter"
        :zoom="mapZoom"
        :show-controls="false"
        @map-ready="handleMapReady"
      >
        <template #overlays="{ map, isReady }">
          <LocationAnalyticsDotsLayer
            v-if="map && isReady"
            :map="map"
            :places="places"
            :selected-place-key="selectedPlaceKey"
            @marker-click="handleMarkerClick"
            @open-place-details="handleOpenPlaceDetails"
          />
        </template>
      </MapContainer>

      <div v-if="loading" class="map-overlay">
        <div class="map-overlay-content">
          <ProgressSpinner strokeWidth="5" />
          <span>Loading places...</span>
        </div>
      </div>

      <div v-else-if="places.length === 0" class="map-overlay map-empty">
        <div class="map-overlay-content">
          <i class="pi pi-map-marker"></i>
          <span>No places found in this view.</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import ProgressSpinner from 'primevue/progressspinner'
import { MapContainer } from '@/components/maps'
import LocationAnalyticsDotsLayer from '@/components/maps/layers/LocationAnalyticsDotsLayer.vue'

const props = defineProps({
  places: {
    type: Array,
    default: () => []
  },
  loading: {
    type: Boolean,
    default: false
  },
  selectedPlaceKey: {
    type: String,
    default: null
  }
})

const emit = defineEmits(['viewport-change', 'place-click', 'open-place-details'])

const mapInstance = ref(null)
const mapCenter = ref([20, 0])
const mapZoom = ref(2)
const userMovedMap = ref(false)
let viewportDebounceTimer = null
let moveHandler = null
let zoomHandler = null

const getPlaceKey = (place) => `${place.type}-${place.id}`

const selectedPlace = computed(() => {
  if (!props.selectedPlaceKey) return null
  return props.places.find((place) => getPlaceKey(place) === props.selectedPlaceKey) || null
})

const emitViewport = () => {
  if (!mapInstance.value) return
  const bounds = mapInstance.value.getBounds()
  emit('viewport-change', {
    minLat: bounds.getSouth(),
    maxLat: bounds.getNorth(),
    minLon: bounds.getWest(),
    maxLon: bounds.getEast(),
    zoom: mapInstance.value.getZoom()
  })
}

const scheduleViewportEmit = () => {
  if (viewportDebounceTimer) {
    clearTimeout(viewportDebounceTimer)
  }
  viewportDebounceTimer = setTimeout(() => {
    emitViewport()
  }, 220)
}

const fitToPlaces = () => {
  if (!mapInstance.value || props.places.length === 0 || userMovedMap.value) return

  const validPlaces = props.places.filter(
    (place) => typeof place.latitude === 'number' && typeof place.longitude === 'number'
  )
  if (validPlaces.length === 0) return
  if (validPlaces.length === 1) {
    mapInstance.value.setView([validPlaces[0].latitude, validPlaces[0].longitude], 13, { animate: false })
    return
  }

  const bounds = validPlaces.map((place) => [place.latitude, place.longitude])
  mapInstance.value.fitBounds(bounds, { padding: [24, 24], animate: false, maxZoom: 13 })
}

const focusSelectedPlace = () => {
  if (!mapInstance.value || !selectedPlace.value) return

  const currentZoom = mapInstance.value.getZoom()
  const targetZoom = Math.max(currentZoom, 18)
  mapInstance.value.setView(
    [selectedPlace.value.latitude, selectedPlace.value.longitude],
    targetZoom,
    { animate: true }
  )
}

const handleMapReady = (map) => {
  mapInstance.value = map

  moveHandler = () => {
    userMovedMap.value = true
    scheduleViewportEmit()
  }
  zoomHandler = () => {
    userMovedMap.value = true
    scheduleViewportEmit()
  }

  map.on('moveend', moveHandler)
  map.on('zoomend', zoomHandler)

  // Trigger initial fetch for current bounds.
  emitViewport()
}

const handleMarkerClick = (place) => {
  emit('place-click', place)
}

const handleOpenPlaceDetails = (place) => {
  emit('open-place-details', place)
}

watch(() => props.places, () => {
  fitToPlaces()
}, { deep: false })

watch(() => props.selectedPlaceKey, (nextKey, previousKey) => {
  if (!nextKey || nextKey === previousKey) return
  focusSelectedPlace()
})

onBeforeUnmount(() => {
  if (viewportDebounceTimer) {
    clearTimeout(viewportDebounceTimer)
    viewportDebounceTimer = null
  }

  if (mapInstance.value && moveHandler) {
    mapInstance.value.off('moveend', moveHandler)
  }
  if (mapInstance.value && zoomHandler) {
    mapInstance.value.off('zoomend', zoomHandler)
  }
})
</script>

<style scoped>
.location-analytics-map {
  width: 100%;
}

.map-frame {
  position: relative;
  height: clamp(360px, 60vh, 700px);
  border-radius: var(--gp-radius-medium);
  overflow: hidden;
  border: 1px solid var(--gp-border-light);
}

.map-overlay {
  position: absolute;
  inset: 0;
  background: rgba(255, 255, 255, 0.8);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 500;
}

.map-overlay-content {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.75rem;
  color: var(--gp-text-secondary);
}

.map-empty .map-overlay-content i {
  font-size: 1.75rem;
  color: var(--gp-primary);
}

:global(.p-dark) .map-overlay {
  background: rgba(15, 23, 42, 0.7);
}

@media (max-height: 940px) {
  .map-frame {
    height: clamp(330px, 54vh, 600px);
  }
}

@media (max-height: 840px) {
  .map-frame {
    height: clamp(300px, 49vh, 540px);
  }
}
</style>
