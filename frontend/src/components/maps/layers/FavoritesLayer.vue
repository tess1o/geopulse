<template>
  <BaseLayer
    ref="baseLayerRef"
    :map="map"
    :visible="visible"
    @layer-ready="handleLayerReady"
  />
</template>

<script setup>
import { ref, watch, computed, readonly } from 'vue'
import L from 'leaflet'
import BaseLayer from './BaseLayer.vue'
import { createFavoriteIcon } from '@/utils/mapHelpers'

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

// State
const baseLayerRef = ref(null)
const favoriteMarkers = ref([])

// Computed
const hasFavoritesData = computed(() => {
  return  props.favoritesData && props.favoritesData.length > 0
})

// Layer management
const handleLayerReady = (layerGroup) => {
  if (hasFavoritesData.value) {
    renderFavoriteMarkers()
  }
}

const renderFavoriteMarkers = () => {
  if (!baseLayerRef.value || !hasFavoritesData.value) return

  // Clear existing markers
  clearFavoriteMarkers()

  props.favoritesData.forEach((favorite, index) => {
    renderFavoriteItem(favorite, index)
  })
}

const renderFavoriteItem = (favorite, index) => {
  // Handle different type formats (POINT/point, AREA/area)
  const normalizedType = favorite.type?.toLowerCase()
  
  if (normalizedType === 'point' || (!favorite.type && favorite.latitude !== undefined && favorite.longitude !== undefined)) {
    renderFavoritePoint(favorite, index)
  } else if (normalizedType === 'area' || (favorite.northEastLat !== undefined && favorite.southWestLat !== undefined)) {
    renderFavoriteArea(favorite, index)
  }
}

const renderFavoritePoint = (favorite, index) => {
  // Handle different coordinate formats (latitude/longitude vs longitude/latitude)
  const lat = favorite.latitude
  const lng = favorite.longitude
  
  if (!lat || !lng) return

  // Create favorite icon
  const icon = createFavoriteIcon(favorite.icon || 'star')

  // Create marker
  const marker = L.marker([lat, lng], {
    icon,
    favorite,
    favoriteIndex: index,
    ...props.markerOptions
  })

  // Add event listeners
  marker.on('click', (e) => {
    emit('favorite-click', {
      favorite,
      index,
      marker,
      event: e
    })
  })

  marker.on('mouseover', (e) => {
    emit('favorite-hover', {
      favorite,
      index,
      marker,
      event: e
    })
  })

  // Add context menu for edit/delete
  marker.on('contextmenu', (e) => {
    // Prevent all event propagation
    if (e.originalEvent) {
      e.originalEvent.preventDefault()
      e.originalEvent.stopPropagation()
      e.originalEvent.stopImmediatePropagation()
    }
    
    // Also prevent Leaflet event propagation
    L.DomEvent.stop(e)
    
    emit('favorite-contextmenu', {
      favorite,
      index,
      event: e.originalEvent,
      latlng: e.latlng,
      type: 'point'
    })
  })

  // Add tooltip on hover
  marker.bindTooltip(favorite.name || 'Favorite Place', {
    permanent: false,
    direction: 'top',
    offset: [0, -10]
  })

  // Add to layer and track
  baseLayerRef.value.addToLayer(marker)
  favoriteMarkers.value.push({
    marker,
    favorite,
    index,
    type: 'point'
  })
}

const renderFavoriteArea = (favorite, index) => {
  let coordinates = []
  
  // Handle different coordinate formats
  if (favorite.coordinates && Array.isArray(favorite.coordinates)) {
    coordinates = favorite.coordinates
  } else if (favorite.northEastLat !== undefined && favorite.southWestLat !== undefined) {
    // Convert bounds to rectangle coordinates
    const ne = [favorite.northEastLat, favorite.northEastLon]
    const sw = [favorite.southWestLat, favorite.southWestLon]
    const nw = [favorite.northEastLat, favorite.southWestLon]
    const se = [favorite.southWestLat, favorite.northEastLon]
    coordinates = [ne, se, sw, nw] // Rectangle coordinates
  }
  
  if (!coordinates.length) return

  // Create polygon from coordinates
  const polygon = L.polygon(coordinates, {
    color: favorite.color || '#ff6b6b',
    fillColor: favorite.fillColor || '#ff6b6b',
    fillOpacity: 0.2,
    weight: 2,
    favorite,
    favoriteIndex: index
  })

  // Add event listeners
  polygon.on('click', (e) => {
    emit('favorite-click', {
      favorite,
      index,
      polygon,
      event: e
    })
  })

  polygon.on('mouseover', (e) => {
    polygon.setStyle({
      fillOpacity: 0.4,
      weight: 3
    })
    emit('favorite-hover', {
      favorite,
      index,
      polygon,
      event: e
    })
  })

  polygon.on('mouseout', () => {
    polygon.setStyle({
      fillOpacity: 0.2,
      weight: 2
    })
  })

  // Add context menu for edit/delete
  polygon.on('contextmenu', (e) => {
    // Prevent all event propagation for polygons
    if (e.originalEvent) {
      e.originalEvent.preventDefault()
      e.originalEvent.stopPropagation()
      e.originalEvent.stopImmediatePropagation()
    }
    
    // Also prevent Leaflet event propagation
    L.DomEvent.stop(e)
    
    emit('favorite-contextmenu', {
      favorite,
      index,
      event: e.originalEvent,
      latlng: e.latlng,
      type: 'area'
    })
  })

  // Add tooltip on hover
  polygon.bindTooltip(favorite.name || 'Favorite Area', {
    permanent: false,
    direction: 'center'
  })

  // Add to layer and track
  baseLayerRef.value.addToLayer(polygon)
  favoriteMarkers.value.push({
    polygon,
    favorite,
    index,
    type: 'area'
  })
}


const clearFavoriteMarkers = () => {
  favoriteMarkers.value.forEach(({ marker, polygon }) => {
    const layer = marker || polygon
    baseLayerRef.value?.removeFromLayer(layer)
  })
  favoriteMarkers.value = []
}

const getMarkerByFavorite = (favoriteData) => {
  const found = favoriteMarkers.value.find(({ favorite }) => 
    favorite.id === favoriteData.id || favorite === favoriteData
  )
  return found?.marker || found?.polygon
}

const focusOnFavorite = (favoriteData) => {
  const layer = getMarkerByFavorite(favoriteData)
  if (layer && props.map) {
    if (layer.getLatLng) {
      // Point marker
      props.map.setView(layer.getLatLng(), 15)
    } else if (layer.getBounds) {
      // Area polygon
      props.map.fitBounds(layer.getBounds())
    }
    layer.openPopup()
  }
}

const updateFavorite = (favoriteId, updatedData) => {
  const favoriteMarker = favoriteMarkers.value.find(({ favorite }) => favorite.id === favoriteId)
  if (favoriteMarker) {
    // Update favorite data
    favoriteMarker.favorite = { ...favoriteMarker.favorite, ...updatedData }
    
    // Update popup content
    const layer = favoriteMarker.marker || favoriteMarker.polygon
    const newPopupContent = createFavoritePopupContent(favoriteMarker.favorite)
    layer.setPopupContent(newPopupContent)
  }
}

const removeFavorite = (favoriteId) => {
  const index = favoriteMarkers.value.findIndex(({ favorite }) => favorite.id === favoriteId)
  if (index !== -1) {
    const { marker, polygon } = favoriteMarkers.value[index]
    const layer = marker || polygon
    baseLayerRef.value?.removeFromLayer(layer)
    favoriteMarkers.value.splice(index, 1)
  }
}

// Watch for data changes
watch(() => props.favoritesData, () => {
  if (baseLayerRef.value?.isReady) {
    renderFavoriteMarkers()
  }
}, { deep: true })

// Expose methods
defineExpose({
  baseLayerRef,
  favoriteMarkers: readonly(favoriteMarkers),
  getMarkerByFavorite,
  focusOnFavorite,
  updateFavorite,
  removeFavorite,
  clearFavoriteMarkers
})
</script>

