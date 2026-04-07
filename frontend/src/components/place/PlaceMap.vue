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
          <FavoritesLayer
            v-if="map && isReady"
            :map="map"
            :favorites-data="placeFavoriteData"
            :visible="true"
          />
        </template>
      </MapContainer>
    </div>
  </BaseCard>
</template>

<script setup>
import { ref, computed, watch, nextTick, onBeforeUnmount } from 'vue'
import BaseCard from '@/components/ui/base/BaseCard.vue'
import { FavoritesLayer, MapContainer } from '@/components/maps'
import { usePhotoMapMarkersRuntime } from '@/maps/runtime/usePhotoMapMarkersRuntime'
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
const map = ref(null)

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
} = usePhotoMapMarkersRuntime({ emit })

const mapCenter = computed(() => {
  if (props.geometry.latitude && props.geometry.longitude) {
    return [props.geometry.latitude, props.geometry.longitude]
  }
  return [51.505, -0.09] // Default center
})

const mapZoom = computed(() => {
  return props.geometry.type === 'area' ? 13 : 15
})

const placeFavoriteData = computed(() => {
  if (!props.geometry) {
    return []
  }

  if (props.geometry.type === 'point') {
    const latitude = Number(props.geometry.latitude)
    const longitude = Number(props.geometry.longitude)
    if (!Number.isFinite(latitude) || !Number.isFinite(longitude)) {
      return []
    }

    return [
      {
        id: 'place-point',
        name: props.locationName || 'Place',
        type: 'point',
        latitude,
        longitude
      }
    ]
  }

  if (props.geometry.type === 'area' && Array.isArray(props.geometry.northEast) && Array.isArray(props.geometry.southWest)) {
    const northEastLat = Number(props.geometry.northEast[0])
    const northEastLon = Number(props.geometry.northEast[1])
    const southWestLat = Number(props.geometry.southWest[0])
    const southWestLon = Number(props.geometry.southWest[1])

    if ([northEastLat, northEastLon, southWestLat, southWestLon].every((value) => Number.isFinite(value))) {
      return [
        {
          id: 'place-area',
          name: props.locationName || 'Place Area',
          type: 'area',
          northEastLat,
          northEastLon,
          southWestLat,
          southWestLon
        }
      ]
    }
  }

  return []
})

const handleMapBackgroundClick = () => {
  clearFocusMarker()
}

const focusGeometry = () => {
  if (!map.value) {
    return
  }

  if (props.geometry.type === 'point') {
    const latitude = Number(props.geometry.latitude)
    const longitude = Number(props.geometry.longitude)
    if (Number.isFinite(latitude) && Number.isFinite(longitude)) {
      mapContainerRef.value?.setView?.([latitude, longitude], mapZoom.value)
    }
    return
  }

  if (props.geometry.type === 'area' && Array.isArray(props.geometry.northEast) && Array.isArray(props.geometry.southWest)) {
    const bounds = [
      [props.geometry.southWest[0], props.geometry.southWest[1]],
      [props.geometry.northEast[0], props.geometry.northEast[1]]
    ]
    mapContainerRef.value?.fitBounds?.(bounds, { padding: [50, 50] })
  }
}

const handleMapReady = (mapInstance) => {
  map.value = mapInstance
  map.value.on('click', handleMapBackgroundClick)

  nextTick(() => {
    focusGeometry()
    renderPhotoMarkers()
  })
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
    focusGeometry()
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
