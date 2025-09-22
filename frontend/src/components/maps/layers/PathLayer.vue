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
import { createHighlightedPathStartMarker, createHighlightedPathEndMarker } from '@/utils/mapHelpers'
import {formatDuration, formatDistance} from "@/utils/calculationsHelpers";
import { useTimezone } from '@/composables/useTimezone'

const timezone = useTimezone()

const props = defineProps({
  map: {
    type: Object,
    required: true
  },
  pathData: {
    type: Array,
    default: () => []
  },
  visible: {
    type: Boolean,
    default: true
  },
  highlightedTrip: {
    type: Object,
    default: null
  },
  pathOptions: {
    type: Object,
    default: () => ({
      color: '#007bff',
      weight: 4,
      opacity: 0.8,
      smoothFactor: 1
    })
  }
})

const emit = defineEmits(['path-click', 'path-hover', 'trip-marker-click'])

// State
const baseLayerRef = ref(null)
const pathLayers = ref([])

// Computed
const hasPathData = computed(() => props.pathData && props.pathData.length > 0)
// Removed - using timezone composable directly

// Layer management
const handleLayerReady = (layerGroup) => {
  if (hasPathData.value) {
    renderPaths()
  }
}

const renderPaths = () => {
  if (!baseLayerRef.value || !hasPathData.value) return

  // Clear existing paths
  clearPaths()

  props.pathData.forEach((pathGroup, groupIndex) => {

    // Convert path data to LatLng array
    const latlngs = pathGroup.map(point => [point.latitude, point.longitude])

    // Create polyline
    const polyline = L.polyline(latlngs, {
      ...props.pathOptions,
      pathId: pathGroup.id || groupIndex
    })

    // Add event listeners
    polyline.on('click', (e) => {
      emit('path-click', {
        pathData: pathGroup,
        pathIndex: groupIndex,
        event: e
      })
    })

    polyline.on('mouseover', (e) => {
      polyline.setStyle({
        weight: props.pathOptions.weight + 2,
        opacity: 1
      })
      emit('path-hover', {
        pathData: pathGroup,
        pathIndex: groupIndex,
        event: e
      })
    })

    polyline.on('mouseout', () => {
      polyline.setStyle(props.pathOptions)
    })

    // Add to layer and track
    baseLayerRef.value.addToLayer(polyline)
    pathLayers.value.push(polyline)
  })
}

const clearPaths = () => {
  pathLayers.value.forEach(layer => {
    baseLayerRef.value?.removeFromLayer(layer)
  })
  pathLayers.value = []
}

const highlightPath = (pathIndex) => {
  if (pathLayers.value[pathIndex]) {
    pathLayers.value[pathIndex].setStyle({
      color: '#ff6b6b',
      weight: props.pathOptions.weight + 2,
      opacity: 1
    })
  }
}

const unhighlightPath = (pathIndex) => {
  if (pathLayers.value[pathIndex]) {
    pathLayers.value[pathIndex].setStyle(props.pathOptions)
  }
}

const unhighlightAllPaths = () => {
  pathLayers.value.forEach(layer => {
    layer.setStyle(props.pathOptions)
  })
}

// Watch for data changes
watch(() => props.pathData, () => {
  if (baseLayerRef.value?.isReady) {
    renderPaths()
  }
}, { deep: true })

// Trip-specific state
const tripPathLayer = ref(null)
const tripStartMarker = ref(null)
const tripEndMarker = ref(null)

// Watch for trip highlighting
watch(() => props.highlightedTrip, (newTrip, oldTrip) => {
  // Remove previous trip path and markers
  if (tripPathLayer.value) {
    baseLayerRef.value?.removeFromLayer(tripPathLayer.value)
    tripPathLayer.value = null
  }
  if (tripStartMarker.value) {
    baseLayerRef.value?.removeFromLayer(tripStartMarker.value)
    tripStartMarker.value = null
  }
  if (tripEndMarker.value) {
    baseLayerRef.value?.removeFromLayer(tripEndMarker.value)
    tripEndMarker.value = null
  }
  
  if (newTrip && newTrip.type === 'trip' && newTrip.path && newTrip.path.length > 1) {
    
    // Create trip path coordinates
    const tripCoords = newTrip.path.map(point => [point.latitude, point.longitude])
    
    // Create highlighted trip polyline
    tripPathLayer.value = L.polyline(tripCoords, {
      color: '#ff6b6b',
      weight: 6,
      opacity: 1,
      dashArray: '10, 5'
    })
    
    // Add to layer
    baseLayerRef.value?.addToLayer(tripPathLayer.value)
    
    // Add start and end markers using centralized functions
    const startPoint = newTrip.path[0]
    const endPoint = newTrip.path[newTrip.path.length - 1]
    
    tripStartMarker.value = createHighlightedPathStartMarker(
      startPoint.latitude, 
      startPoint.longitude, 
      true // instant appearance
    )
    
    tripEndMarker.value = createHighlightedPathEndMarker(
      endPoint.latitude, 
      endPoint.longitude, 
      true // instant appearance
    )
    
    // Add popup to trip path
    const tripInfo = `
      <div class="trip-popup">
        <div class="trip-title">
          ${newTrip.movementType || 'Movement'} Trip
        </div>
        <div class="trip-detail">
          Duration: ${formatDuration(newTrip.tripDuration)}
        </div>
        <div class="trip-detail">
          Distance: ${formatDistance(newTrip.distanceMeters || 0)}
        </div>
        <div class="trip-detail">
          ${timezone.format(newTrip.timestamp, 'YYYY-MM-DD HH:mm:ss')}
        </div>
      </div>
    `
    
    // Create detailed tooltips for start and end markers
    const startTime = timezone.fromUtc(newTrip.timestamp);
    const endTime = startTime.add(newTrip.tripDuration, 'second');
    
    const startInfo = `
      <div class="trip-popup">
        <div class="trip-title trip-start">
          🚀 Trip Start
        </div>
        <div class="trip-detail">
          Start: ${startTime.format('YYYY-MM-DD HH:mm:ss')}
        </div>
        <div class="trip-detail">
          Duration: ${formatDuration(newTrip.tripDuration)}
        </div>
        <div class="trip-detail">
          Distance: ${formatDistance(newTrip.distanceMeters || 0)}
        </div>
        <div class="trip-detail">
          Mode: ${newTrip.movementType || 'Unknown'}
        </div>
      </div>
    `

    const endInfo = `
      <div class="trip-popup">
        <div class="trip-title trip-end">
          🏁 Trip End
        </div>
        <div class="trip-detail">
          End: ${endTime.format('YYYY-MM-DD HH:mm:ss')}
        </div>
        <div class="trip-detail">
          Duration: ${formatDuration(newTrip.tripDuration)}
        </div>
        <div class="trip-detail">
          Distance: ${formatDistance(newTrip.distanceMeters || 0)}
        </div>
        <div class="trip-detail">
          Mode: ${newTrip.movementType || 'Unknown'}
        </div>
      </div>
    `
    
    // Add click handlers to trip markers
    tripStartMarker.value.on('click', (e) => {
      emit('trip-marker-click', {
        tripData: newTrip,
        markerType: 'start',
        event: e
      })
    })

    tripStartMarker.value.on('mouseover', function (e) {
      this.openPopup();
    });
    tripStartMarker.value.on('mouseout', function (e) {
      this.closePopup();
    });
    
    tripEndMarker.value.on('click', (e) => {
      emit('trip-marker-click', {
        tripData: newTrip,
        markerType: 'end', 
        event: e
      })
    })

    tripEndMarker.value.on('mouseover', function (e) {
      this.openPopup();
    });
    tripEndMarker.value.on('mouseout', function (e) {
      this.closePopup();
    });
    
    tripPathLayer.value.bindPopup(tripInfo)
    tripStartMarker.value.bindPopup(startInfo)
    tripEndMarker.value.bindPopup(endInfo)
    
    baseLayerRef.value?.addToLayer(tripStartMarker.value)
    baseLayerRef.value?.addToLayer(tripEndMarker.value)
    
    // Zoom to trip bounds
    if (props.map) {
      const bounds = L.latLngBounds(tripCoords)
      props.map.fitBounds(bounds, { 
        padding: [20, 20],
        animate: true,
        duration: 0.8
      })
      
      // Open trip info popup after zoom
      setTimeout(() => {
        tripPathLayer.value?.openPopup()
      }, 500)
    }
  }
}, { deep: true })

watch(() => props.pathOptions, () => {
  if (baseLayerRef.value?.isReady) {
    renderPaths()
  }
}, { deep: true })

// Expose methods
defineExpose({
  baseLayerRef,
  pathLayers: readonly(pathLayers),
  highlightPath,
  unhighlightPath,
  unhighlightAllPaths,
  clearPaths
})
</script>

<style>
/* Trip popup styling */
.trip-popup {
  font-family: var(--font-family, system-ui);
  min-width: 180px;
  font-size: 0.875rem;
  line-height: 1.4;
}

.trip-title {
  font-weight: 600;
  color: var(--gp-text-primary, #1e293b);
  margin-bottom: 0.5rem;
  font-size: 1rem;
}

.trip-title.trip-start {
  color: var(--gp-success, #22c55e);
}

.trip-title.trip-end {
  color: var(--gp-danger, #ef4444);
}

.trip-detail {
  color: var(--gp-text-secondary, #64748b);
  font-size: 0.8rem;
  margin-bottom: 0.25rem;
}

.trip-detail:last-child {
  margin-bottom: 0;
}

/* Dark theme overrides for trip popups */
.p-dark .leaflet-popup-content-wrapper .trip-popup .trip-title {
  color: rgba(255, 255, 255, 0.95) !important;
}

.p-dark .leaflet-popup-content-wrapper .trip-popup .trip-title.trip-start {
  color: var(--gp-success-light, #4ade80) !important;
}

.p-dark .leaflet-popup-content-wrapper .trip-popup .trip-title.trip-end {
  color: var(--gp-danger-light, #f87171) !important;
}

.p-dark .leaflet-popup-content-wrapper .trip-popup .trip-detail {
  color: rgba(255, 255, 255, 0.8) !important;
}

/* Light theme - ensure good contrast */
.trip-popup .trip-title {
  color: #1e293b !important;
}

.trip-popup .trip-title.trip-start {
  color: #22c55e !important;
}

.trip-popup .trip-title.trip-end {
  color: #ef4444 !important;
}

.trip-popup .trip-detail {
  color: #475569 !important;
}
</style>