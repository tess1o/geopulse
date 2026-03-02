<template>
  <BaseCard title="Location">
    <div class="place-map-container">
      <MapContainer
        ref="mapContainerRef"
        :map-id="mapId"
        :center="mapCenter"
        :zoom="mapZoom"
        :map-options="mapOptions"
        :show-controls="false"
        @map-ready="handleMapReady"
      >
        <template #overlays="{ map, isReady }">
          <!-- Point Marker -->
          <div v-if="map && isReady && geometry.type === 'point'" ref="pointMarkerRef"></div>

          <!-- Area Rectangle -->
          <div v-if="map && isReady && geometry.type === 'area'" ref="areaRectangleRef"></div>
        </template>
      </MapContainer>
    </div>
  </BaseCard>
</template>

<script setup>
import { ref, computed, watch, nextTick, onBeforeUnmount } from 'vue'
import BaseCard from '@/components/ui/base/BaseCard.vue'
import { MapContainer } from '@/components/maps'
import L from 'leaflet'
import { usePhotoMapMarkers } from '@/composables/usePhotoMapMarkers'
import '@/styles/photo-map-markers.css'

const props = defineProps({
  geometry: {
    type: Object,
    required: true
  },
  locationName: {
    type: String,
    default: 'Place'
  },
  photos: {
    type: Array,
    default: () => []
  },
  photoMarkerGroups: {
    type: Array,
    default: () => []
  }
})

const emit = defineEmits(['photo-click'])

const mapContainerRef = ref(null)
const pointMarkerRef = ref(null)
const areaRectangleRef = ref(null)
const map = ref(null)
const marker = ref(null)
const rectangle = ref(null)

const mapId = `place-details-map-${Math.random().toString(36).slice(2, 11)}`
const mapOptions = {
  tap: false
}
const {
  clearPhotoMarkers,
  clearFocusMarker,
  renderPhotoMarkers: renderMapPhotoMarkers,
  renderPhotoMarkerGroups: renderMapPhotoMarkerGroups,
  focusOnCoordinates: focusOnMapCoordinates,
  focusOnPhoto: focusOnMapPhoto
} = usePhotoMapMarkers({ emit })

const mapCenter = computed(() => {
  if (props.geometry.latitude && props.geometry.longitude) {
    return [props.geometry.latitude, props.geometry.longitude]
  }
  return [51.505, -0.09] // Default center
})

const mapZoom = computed(() => {
  return props.geometry.type === 'area' ? 13 : 15
})

const handleMapBackgroundClick = () => {
  clearFocusMarker()
}

const handleMapReady = (mapInstance) => {
  map.value = mapInstance
  map.value.on('click', handleMapBackgroundClick)

  nextTick(() => {
    if (props.geometry.type === 'point') {
      addPointMarker()
    } else if (props.geometry.type === 'area') {
      addAreaRectangle()
    }
    renderPhotoMarkers()
  })
}

const addPointMarker = () => {
  if (!map.value || !props.geometry.latitude || !props.geometry.longitude) return

  // Remove existing marker
  if (marker.value) {
    marker.value.remove()
  }

  // Create marker
  marker.value = L.marker([props.geometry.latitude, props.geometry.longitude], {
    icon: L.divIcon({
      className: 'place-marker',
      html: '<div class="place-marker-icon"><i class="pi pi-map-marker"></i></div>',
      iconSize: [40, 40],
      iconAnchor: [20, 40]
    })
  }).addTo(map.value)

  marker.value.bindPopup(`<strong>${props.locationName}</strong>`)

  // Center map on marker
  map.value.setView([props.geometry.latitude, props.geometry.longitude], mapZoom.value)
}

const addAreaRectangle = () => {
  if (!map.value || !props.geometry.northEast || !props.geometry.southWest) return

  // Remove existing rectangle
  if (rectangle.value) {
    rectangle.value.remove()
  }

  // Create rectangle
  const bounds = [
    [props.geometry.southWest[0], props.geometry.southWest[1]],
    [props.geometry.northEast[0], props.geometry.northEast[1]]
  ]

  rectangle.value = L.rectangle(bounds, {
    color: '#3b82f6',
    weight: 2,
    fillColor: '#3b82f6',
    fillOpacity: 0.2
  }).addTo(map.value)

  rectangle.value.bindPopup(`<strong>${props.locationName}</strong><br>Area`)

  // Fit map to rectangle bounds
  map.value.fitBounds(bounds, { padding: [50, 50] })
}

const renderPhotoMarkers = () => {
  if (!map.value) return

  if (Array.isArray(props.photoMarkerGroups) && props.photoMarkerGroups.length > 0) {
    renderMapPhotoMarkerGroups(map.value, props.photoMarkerGroups)
    return
  }

  renderMapPhotoMarkers(map.value, props.photos)
}

const focusOnCoordinates = (latitude, longitude, zoom = 16) => {
  focusOnMapCoordinates(map.value, latitude, longitude, zoom)
}

const focusOnPhoto = (photo, zoom = 16) => {
  focusOnMapPhoto(map.value, photo, zoom)
}

// Watch for geometry changes
watch(() => props.geometry, () => {
  nextTick(() => {
    if (props.geometry.type === 'point') {
      addPointMarker()
    } else if (props.geometry.type === 'area') {
      addAreaRectangle()
    }
  })
}, { deep: true })

watch(() => props.photos, () => {
  nextTick(() => {
    renderPhotoMarkers()
  })
})

watch(() => props.photoMarkerGroups, () => {
  nextTick(() => {
    renderPhotoMarkers()
  })
}, { deep: false })

onBeforeUnmount(() => {
  clearPhotoMarkers()
  if (map.value) {
    map.value.off('click', handleMapBackgroundClick)
  }
  if (marker.value) {
    marker.value.remove()
    marker.value = null
  }
  if (rectangle.value) {
    rectangle.value.remove()
    rectangle.value = null
  }
  clearFocusMarker()
})

defineExpose({
  focusOnCoordinates,
  focusOnPhoto
})
</script>

<style scoped>
.place-map-container {
  width: 100%;
  height: 400px;
  border-radius: var(--gp-radius-medium);
  overflow: hidden;
}

/* Responsive design */
@media (max-width: 768px) {
  .place-map-container {
    height: 300px;
  }
}
</style>

<style>
/* Place marker styles (not scoped) */
.place-marker {
  background: transparent;
  border: none;
}

.place-marker-icon {
  width: 40px;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--gp-primary);
  color: white;
  border-radius: 50% 50% 50% 0;
  transform: rotate(-45deg);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.3);
}

.place-marker-icon i {
  transform: rotate(45deg);
  font-size: 1.5rem;
}
</style>
