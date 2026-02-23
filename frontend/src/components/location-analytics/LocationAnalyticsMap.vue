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
            :hovered-place-key="hoveredPlaceKey"
            @marker-click="handleMarkerClick"
            @open-place-details="handleOpenPlaceDetails"
          />
        </template>
      </MapContainer>

      <div v-if="showInitialLoadingOverlay" class="map-overlay">
        <div class="map-overlay-content">
          <ProgressSpinner strokeWidth="5" />
          <span>Loading places...</span>
        </div>
      </div>

      <div v-if="showRefreshIndicator" class="map-refresh-badge" aria-live="polite">
        <ProgressSpinner strokeWidth="6" />
        <span>Updating map…</span>
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
  },
  hoveredPlaceKey: {
    type: String,
    default: null
  },
  selectionFocusMode: {
    type: String,
    default: 'pan'
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
let loadingOverlayTimer = null
let suppressViewportEventsUntil = 0
const loadingIndicatorVisible = ref(false)
const CARD_SELECTION_MIN_ZOOM = 12

const getPlaceKey = (place) => `${place.type}-${place.id}`
const hasPlaces = computed(() => props.places.length > 0)

const selectedPlace = computed(() => {
  if (!props.selectedPlaceKey) return null
  return props.places.find((place) => getPlaceKey(place) === props.selectedPlaceKey) || null
})

const showInitialLoadingOverlay = computed(() => props.loading && loadingIndicatorVisible.value && !hasPlaces.value)
const showRefreshIndicator = computed(() => props.loading && loadingIndicatorVisible.value && hasPlaces.value)
const suppressViewportEvents = (durationMs = 700) => {
  suppressViewportEventsUntil = Date.now() + durationMs
}

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
    suppressViewportEvents(600)
    mapInstance.value.setView([validPlaces[0].latitude, validPlaces[0].longitude], 13, { animate: false })
    return
  }

  const bounds = validPlaces.map((place) => [place.latitude, place.longitude])
  suppressViewportEvents(600)
  mapInstance.value.fitBounds(bounds, { padding: [24, 24], animate: false, maxZoom: 13 })
}

const focusSelectedPlace = () => {
  if (!mapInstance.value || !selectedPlace.value) return
  const latLng = [selectedPlace.value.latitude, selectedPlace.value.longitude]
  const currentBounds = mapInstance.value.getBounds()
  const currentZoom = mapInstance.value.getZoom?.() ?? 0
  const shouldSoftZoom = props.selectionFocusMode === 'soft-zoom' && currentZoom < CARD_SELECTION_MIN_ZOOM
  const targetZoom = shouldSoftZoom ? CARD_SELECTION_MIN_ZOOM : currentZoom
  const isVisible = currentBounds?.contains?.(latLng)

  if (isVisible && !shouldSoftZoom) return

  suppressViewportEvents(900)

  if (targetZoom > currentZoom) {
    mapInstance.value.setView(latLng, targetZoom, { animate: true })
    return
  }

  mapInstance.value.panTo(latLng, { animate: true, duration: 0.35 })
}

const handleMapReady = (map) => {
  mapInstance.value = map

  moveHandler = () => {
    if (Date.now() < suppressViewportEventsUntil) return
    userMovedMap.value = true
    scheduleViewportEmit()
  }
  zoomHandler = () => {
    if (Date.now() < suppressViewportEventsUntil) return
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

watch(() => props.loading, (isLoading) => {
  if (loadingOverlayTimer) {
    clearTimeout(loadingOverlayTimer)
    loadingOverlayTimer = null
  }

  if (!isLoading) {
    loadingIndicatorVisible.value = false
    return
  }

  loadingOverlayTimer = setTimeout(() => {
    loadingIndicatorVisible.value = true
    loadingOverlayTimer = null
  }, 220)
}, { immediate: true })

onBeforeUnmount(() => {
  if (viewportDebounceTimer) {
    clearTimeout(viewportDebounceTimer)
    viewportDebounceTimer = null
  }
  if (loadingOverlayTimer) {
    clearTimeout(loadingOverlayTimer)
    loadingOverlayTimer = null
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

.map-refresh-badge {
  position: absolute;
  top: 0.75rem;
  right: 0.75rem;
  z-index: 520;
  display: inline-flex;
  align-items: center;
  gap: 0.45rem;
  padding: 0.35rem 0.55rem;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.92);
  border: 1px solid var(--gp-border-light);
  color: var(--gp-text-secondary);
  font-size: 0.78rem;
  pointer-events: none;
  box-shadow: 0 4px 14px rgba(15, 23, 42, 0.12);
}

.map-refresh-badge :deep(.p-progressspinner) {
  width: 0.95rem;
  height: 0.95rem;
}

.map-overlay-content {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.75rem;
  color: var(--gp-text-secondary);
}

:global(.p-dark) .map-overlay {
  background: rgba(15, 23, 42, 0.7);
}

:global(.p-dark) .map-refresh-badge {
  background: rgba(15, 23, 42, 0.92);
  border-color: rgba(148, 163, 184, 0.2);
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
