<template>
  <div class="location-analytics-map">
    <div class="map-frame">
      <MapContainer
        ref="mapContainerRef"
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

const mapContainerRef = ref(null)
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
const hasAppliedInitialAutoFit = ref(false)

const getPlaceKey = (place) => `${place.type}-${place.id}`
const hasPlaces = computed(() => props.places.length > 0)

const toFiniteCoordinate = (value) => {
  const parsed = Number(value)
  return Number.isFinite(parsed) ? parsed : null
}

const isValidLatitude = (value) => Number.isFinite(value) && value >= -90 && value <= 90
const isValidLongitude = (value) => Number.isFinite(value) && value >= -180 && value <= 180

const selectedPlace = computed(() => {
  if (!props.selectedPlaceKey) return null
  return props.places.find((place) => getPlaceKey(place) === props.selectedPlaceKey) || null
})

const setMapView = (center, zoom, options = {}) => {
  if (typeof mapContainerRef.value?.setView !== 'function') {
    return false
  }

  mapContainerRef.value.setView(center, zoom, options)
  return true
}

const normalizeBoundsPoints = (bounds) => {
  if (!Array.isArray(bounds)) {
    console.warn('[LocationAnalyticsMap] fitBounds skipped: bounds is not an array', bounds)
    return null
  }

  const normalized = bounds
    .map((point, index) => {
      if (!Array.isArray(point) || point.length < 2) {
        console.warn('[LocationAnalyticsMap] fitBounds dropped non-point entry', { index, point })
        return null
      }

      const latitude = toFiniteCoordinate(point[0])
      const longitude = toFiniteCoordinate(point[1])
      if (
        latitude === null
        || longitude === null
        || !isValidLatitude(latitude)
        || !isValidLongitude(longitude)
      ) {
        console.warn('[LocationAnalyticsMap] fitBounds dropped invalid coordinate', {
          index,
          rawPoint: point,
          latitude,
          longitude
        })
        return null
      }

      return [latitude, longitude]
    })
    .filter(Boolean)

  return normalized.length > 0 ? normalized : null
}

const fitMapBounds = (bounds, options = {}) => {
  if (typeof mapContainerRef.value?.fitBounds !== 'function') {
    console.warn('[LocationAnalyticsMap] fitBounds skipped: mapContainerRef.fitBounds is unavailable')
    return false
  }

  const normalizedBounds = normalizeBoundsPoints(bounds)
  if (!normalizedBounds) {
    console.warn('[LocationAnalyticsMap] fitBounds skipped: no valid bounds points after normalization')
    return false
  }

  try {
    console.info('[LocationAnalyticsMap] fitBounds call', {
      pointsCount: normalizedBounds.length,
      firstPoint: normalizedBounds[0],
      lastPoint: normalizedBounds[normalizedBounds.length - 1],
      options
    })
    mapContainerRef.value.fitBounds(normalizedBounds, options)
  } catch (error) {
    console.error('[LocationAnalyticsMap] fitBounds failed', {
      error,
      normalizedBounds,
      options
    })
    return false
  }

  return true
}

const isCoordinateVisibleInBounds = (bounds, latitude, longitude) => {
  if (!bounds || !Number.isFinite(latitude) || !Number.isFinite(longitude)) {
    return false
  }

  const south = bounds.getSouth?.()
  const north = bounds.getNorth?.()
  const west = bounds.getWest?.()
  const east = bounds.getEast?.()

  if (![south, north, west, east].every(Number.isFinite)) {
    return false
  }

  return latitude >= south && latitude <= north && longitude >= west && longitude <= east
}

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
  const shouldSkipForUserMove = userMovedMap.value && hasAppliedInitialAutoFit.value
  if (!mapInstance.value || props.places.length === 0 || shouldSkipForUserMove) {
    console.info('[LocationAnalyticsMap] fitToPlaces skipped', {
      hasMap: Boolean(mapInstance.value),
      placesCount: props.places.length,
      userMovedMap: userMovedMap.value,
      hasAppliedInitialAutoFit: hasAppliedInitialAutoFit.value
    })
    return
  }

  const invalidPlaces = []
  const validPlaces = props.places
    .map((place, index) => {
      const latitude = toFiniteCoordinate(place?.latitude)
      const longitude = toFiniteCoordinate(place?.longitude)
      if (
        latitude === null
        || longitude === null
        || !isValidLatitude(latitude)
        || !isValidLongitude(longitude)
      ) {
        invalidPlaces.push({
          index,
          id: place?.id,
          type: place?.type,
          rawLatitude: place?.latitude,
          rawLongitude: place?.longitude,
          normalizedLatitude: latitude,
          normalizedLongitude: longitude
        })
        return null
      }

      return {
        ...place,
        latitude,
        longitude
      }
    })
    .filter(Boolean)

  console.info('[LocationAnalyticsMap] fitToPlaces normalization summary', {
    totalPlaces: props.places.length,
    validPlaces: validPlaces.length,
    invalidPlaces: invalidPlaces.length
  })

  if (invalidPlaces.length > 0) {
    console.warn('[LocationAnalyticsMap] invalid places sample', invalidPlaces.slice(0, 10))
  }

  if (validPlaces.length === 0) {
    console.warn('[LocationAnalyticsMap] fitToPlaces aborted: no valid places after normalization')
    return
  }
  if (validPlaces.length === 1) {
    suppressViewportEvents(600)
    const didSetView = setMapView([validPlaces[0].latitude, validPlaces[0].longitude], 13, { animate: false })
    if (didSetView) {
      hasAppliedInitialAutoFit.value = true
    }
    emitViewport()
    return
  }

  let south = Infinity
  let west = Infinity
  let north = -Infinity
  let east = -Infinity

  validPlaces.forEach((place) => {
    const latitude = place.latitude
    const longitude = place.longitude
    if (latitude < south) south = latitude
    if (latitude > north) north = latitude
    if (longitude < west) west = longitude
    if (longitude > east) east = longitude
  })

  if (![south, west, north, east].every(Number.isFinite)) {
    console.error('[LocationAnalyticsMap] fitToPlaces aborted: computed bbox is invalid', {
      south,
      west,
      north,
      east
    })
    return
  }

  const bounds = [
    [south, west],
    [north, east]
  ]

  console.info('[LocationAnalyticsMap] fitToPlaces bbox', { south, west, north, east })
  suppressViewportEvents(600)
  const didFitBounds = fitMapBounds(bounds, { padding: [24, 24], animate: false, maxZoom: 13 })
  if (didFitBounds) {
    hasAppliedInitialAutoFit.value = true
  }
  emitViewport()
}

const focusSelectedPlace = () => {
  if (!mapInstance.value || !selectedPlace.value) return

  const latitude = Number(selectedPlace.value.latitude)
  const longitude = Number(selectedPlace.value.longitude)
  if (!Number.isFinite(latitude) || !Number.isFinite(longitude)) {
    return
  }

  const center = [latitude, longitude]
  const currentBounds = mapInstance.value.getBounds()
  const currentZoom = mapInstance.value.getZoom?.() ?? 0
  const shouldSoftZoom = props.selectionFocusMode === 'soft-zoom' && currentZoom < CARD_SELECTION_MIN_ZOOM
  const targetZoom = shouldSoftZoom ? CARD_SELECTION_MIN_ZOOM : currentZoom
  const isVisible = isCoordinateVisibleInBounds(currentBounds, latitude, longitude)

  if (isVisible && !shouldSoftZoom) return

  suppressViewportEvents(900)

  setMapView(center, targetZoom, {
    animate: true,
    duration: targetZoom > currentZoom ? 0.45 : 0.35
  })
}

const handleMapReady = (map) => {
  mapInstance.value = map
  userMovedMap.value = false
  hasAppliedInitialAutoFit.value = false

  moveHandler = () => {
    if (Date.now() < suppressViewportEventsUntil) {
      scheduleViewportEmit()
      return
    }
    userMovedMap.value = true
    scheduleViewportEmit()
  }
  zoomHandler = () => {
    if (Date.now() < suppressViewportEventsUntil) {
      scheduleViewportEmit()
      return
    }
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
  if (!props.places?.length) {
    hasAppliedInitialAutoFit.value = false
  }
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
