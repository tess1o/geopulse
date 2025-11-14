<template>
  <BaseLayer
    ref="baseLayerRef"
    :map="map"
    :visible="visible"
    @layer-ready="handleLayerReady"
  />
</template>

<script setup>
import { ref, watch, computed, readonly, onBeforeUnmount } from 'vue'
import L from 'leaflet'
import 'leaflet.markercluster'
import 'leaflet.markercluster/dist/MarkerCluster.css'
import 'leaflet.markercluster/dist/MarkerCluster.Default.css'
import BaseLayer from './BaseLayer.vue'
import { createTimelineIcon, createHighlightedTimelineIcon } from '@/utils/mapHelpers'
import {formatDuration} from "@/utils/calculationsHelpers";
import { useTimezone } from '@/composables/useTimezone'

const timezone = useTimezone()

const props = defineProps({
  map: {
    type: Object,
    required: true
  },
  timelineData: {
    type: Array,
    default: () => []
  },
  visible: {
    type: Boolean,
    default: true
  },
  highlightedItem: {
    type: Object,
    default: null
  },
  markerOptions: {
    type: Object,
    default: () => ({})
  }
})

const emit = defineEmits(['marker-click', 'marker-hover'])

// State
const baseLayerRef = ref(null)
const timelineMarkers = ref([])
const markerClusterGroup = ref(null)

// Computed
const hasTimelineData = computed(() => props.timelineData && props.timelineData.length > 0)

// Layer management
const handleLayerReady = (layerGroup) => {
  // Only use clustering if we have many markers (50+)
  // For smaller datasets, clustering adds complexity without benefit
  const shouldUseClustering = props.timelineData && props.timelineData.length >= 50

  if (shouldUseClustering) {
    // Initialize marker cluster group with custom options
    markerClusterGroup.value = L.markerClusterGroup({
      maxClusterRadius: 50, // Pixels - smaller radius means less aggressive clustering
      spiderfyOnMaxZoom: false, // Disable spiderfy - it causes markers to fly around
      spiderfyOnEveryZoom: false, // Don't spiderfy on zoom changes
      showCoverageOnHover: false, // Don't show cluster coverage polygon on hover
      zoomToBoundsOnClick: true, // Zoom into cluster on click instead of spiderfying
      disableClusteringAtZoom: 16, // Disable clustering when zoomed in close
      chunkedLoading: true, // Better performance for large datasets
      chunkInterval: 200, // ms between processing chunks
      chunkDelay: 50, // ms delay before processing next chunk
      animate: false, // Disable all cluster animations to prevent markers flying around
      animateAddingMarkers: false, // Disable animation when adding markers
      removeOutsideVisibleBounds: true, // Remove markers outside visible bounds for better performance
      iconCreateFunction: function(cluster) {
        const count = cluster.getChildCount()
        let sizeClass = 'small'
        if (count > 100) sizeClass = 'large'
        else if (count > 10) sizeClass = 'medium'

        return L.divIcon({
          html: `<div class="cluster-marker cluster-marker-${sizeClass}"><span>${count}</span></div>`,
          className: 'custom-cluster-icon',
          iconSize: L.point(40, 40)
        })
      }
    })

    // Add cluster group to the map
    if (props.map) {
      props.map.addLayer(markerClusterGroup.value)
    }

    console.log(`TimelineLayer: Clustering enabled (${props.timelineData.length} markers)`)
  } else {
    console.log(`TimelineLayer: Clustering disabled (${props.timelineData?.length || 0} markers - threshold is 50)`)
  }

  if (hasTimelineData.value) {
    renderTimelineMarkers()
  }
}

const renderTimelineMarkers = () => {
  if (!baseLayerRef.value || !hasTimelineData.value) return

  // Clear existing markers
  clearTimelineMarkers()

  props.timelineData.forEach((item, index) => {
    if (!item.latitude || !item.longitude) return

    // Determine if this marker should be highlighted
    const isHighlighted = props.highlightedItem && (
      (props.highlightedItem.id && item.id && props.highlightedItem.id === item.id) ||
      (props.highlightedItem.timestamp && item.timestamp && props.highlightedItem.timestamp === item.timestamp &&
       props.highlightedItem.latitude === item.latitude && props.highlightedItem.longitude === item.longitude)
    )

    // Create appropriate icon
    const icon = isHighlighted ?
      createHighlightedTimelineIcon() :
      createTimelineIcon()

    // Create marker
    const marker = L.marker([item.latitude, item.longitude], {
      icon,
      timelineItem: item,
      timelineIndex: index,
      ...props.markerOptions
    })

    // Add event listeners
    marker.on('click', (e) => {
      emit('marker-click', {
        timelineItem: item,
        index,
        marker,
        event: e
      })
    })

    marker.on('mouseover', (e) => {
      emit('marker-hover', {
        timelineItem: item,
        index,
        marker,
        event: e
      })
    })

    // Add popup if item has relevant data
    if (item.address || item.timestamp) {
      const popupContent = createPopupContent(item)
      marker.bindPopup(popupContent)
    }

    // Add to cluster group if clustering is enabled, otherwise add directly to layer
    if (markerClusterGroup.value) {
      markerClusterGroup.value.addLayer(marker)
    } else {
      baseLayerRef.value.addToLayer(marker)
    }

    timelineMarkers.value.push({
      marker,
      item,
      index,
      isHighlighted
    })
  })
}

const createPopupContent = (item) => {
  const dateStr = timezone.format(item.timestamp, 'YYYY-MM-DD HH:mm:ss')

  // Handle different item types
  if (item.type === 'stay') {
    const durationText = item.stayDuration ? formatDuration(item.stayDuration) : null
    const locationName = item.locationName || 'Unknown Location'

    return `
      <div class="timeline-popup">
        <div class="popup-location">${locationName}</div>
        <div class="popup-time">${dateStr}</div>
        ${durationText ? `<div class="popup-duration">Stay duration: ${durationText}</div>` : ''}
      </div>
    `.trim()
  } else if (item.type === 'trip') {
    const durationText = item.tripDuration ? formatDuration(item.tripDuration) : null
    const distanceText = item.totalDistanceMeters ? `${(item.totalDistanceMeters / 1000).toFixed(1)} km` : null
    const movementType = item.movementType || 'Unknown'

    return `
      <div class="timeline-popup">
        <div class="popup-location">Trip (${movementType})</div>
        <div class="popup-time">${dateStr}</div>
        ${durationText ? `<div class="popup-duration">Duration: ${durationText}</div>` : ''}
        ${distanceText ? `<div class="popup-duration">Distance: ${distanceText}</div>` : ''}
      </div>
    `.trim()
  } else if (item.type === 'dataGap') {
    return `
      <div class="timeline-popup">
        <div class="popup-location">Data Gap</div>
        <div class="popup-time">${dateStr}</div>
      </div>
    `.trim()
  }

  // Fallback
  return `
    <div class="timeline-popup">
      <div class="popup-time">${dateStr}</div>
    </div>
  `.trim()
}

const clearTimelineMarkers = () => {
  if (markerClusterGroup.value) {
    // Clear all markers from cluster group
    markerClusterGroup.value.clearLayers()
  } else if (baseLayerRef.value) {
    // Clear markers from base layer when not using clustering
    timelineMarkers.value.forEach(({ marker }) => {
      baseLayerRef.value.removeFromLayer(marker)
    })
  }
  timelineMarkers.value = []
}

const reinitializeLayer = (shouldUseClustering) => {
  // Clear existing markers first
  clearTimelineMarkers()

  // Remove existing cluster group if present
  if (markerClusterGroup.value && props.map) {
    props.map.removeLayer(markerClusterGroup.value)
    markerClusterGroup.value.clearLayers()
    markerClusterGroup.value = null
  }

  // Create cluster group if needed
  if (shouldUseClustering) {
    markerClusterGroup.value = L.markerClusterGroup({
      maxClusterRadius: 50,
      spiderfyOnMaxZoom: false, // Disable spiderfy - it causes markers to fly around
      spiderfyOnEveryZoom: false, // Don't spiderfy on zoom changes
      showCoverageOnHover: false,
      zoomToBoundsOnClick: true, // Zoom into cluster on click instead of spiderfying
      disableClusteringAtZoom: 16,
      chunkedLoading: true,
      chunkInterval: 200,
      chunkDelay: 50,
      animate: false, // Disable all cluster animations to prevent markers flying around
      animateAddingMarkers: false, // Disable animation when adding markers
      removeOutsideVisibleBounds: true, // Remove markers outside visible bounds for better performance
      iconCreateFunction: function(cluster) {
        const count = cluster.getChildCount()
        let sizeClass = 'small'
        if (count > 100) sizeClass = 'large'
        else if (count > 10) sizeClass = 'medium'

        return L.divIcon({
          html: `<div class="cluster-marker cluster-marker-${sizeClass}"><span>${count}</span></div>`,
          className: 'custom-cluster-icon',
          iconSize: L.point(40, 40)
        })
      }
    })

    if (props.map) {
      props.map.addLayer(markerClusterGroup.value)
    }

    console.log(`TimelineLayer: Clustering enabled (${props.timelineData.length} markers)`)
  } else {
    console.log(`TimelineLayer: Clustering disabled (${props.timelineData?.length || 0} markers)`)
  }

  // Render markers with new configuration
  renderTimelineMarkers()
}

const updateHighlightedMarker = () => {
  timelineMarkers.value.forEach(({ marker, item, isHighlighted }, index) => {
    const shouldBeHighlighted = props.highlightedItem && (
      (props.highlightedItem.id && item.id && props.highlightedItem.id === item.id) ||
      (props.highlightedItem.timestamp && item.timestamp && props.highlightedItem.timestamp === item.timestamp &&
       props.highlightedItem.latitude === item.latitude && props.highlightedItem.longitude === item.longitude)
    )

    if (shouldBeHighlighted !== isHighlighted) {
      const newIcon = shouldBeHighlighted ?
        createHighlightedTimelineIcon() :
        createTimelineIcon()

      marker.setIcon(newIcon)

      // If highlighting this marker, only zoom for stay items (trips handle their own zooming)
      if (shouldBeHighlighted && props.map && item.type !== 'trip') {
        // Disable animation when clustering is enabled to prevent markers flying around
        const useAnimation = !markerClusterGroup.value
        props.map.setView([item.latitude, item.longitude], 16, {
          animate: useAnimation,
          duration: useAnimation ? 0.8 : 0
        })

        // Open popup after a short delay (or immediately if no animation)
        setTimeout(() => {
          marker.openPopup()
        }, useAnimation ? 300 : 100)
      } else if (shouldBeHighlighted && item.type !== 'trip') {
        // For stay items without map, just open popup
        setTimeout(() => {
          marker.openPopup()
        }, 100)
      }

      // Update the stored isHighlighted state
      const markerData = timelineMarkers.value[index]
      if (markerData) {
        markerData.isHighlighted = shouldBeHighlighted
      }
    }
  })
}

const getMarkerByItem = (timelineItem) => {
  const found = timelineMarkers.value.find(({ item }) => 
    (timelineItem.id && item.id && timelineItem.id === item.id) ||
    (timelineItem.timestamp && item.timestamp && timelineItem.timestamp === item.timestamp &&
     timelineItem.latitude === item.latitude && timelineItem.longitude === item.longitude)
  )
  return found?.marker
}

const focusOnMarker = (timelineItem) => {
  const marker = getMarkerByItem(timelineItem)
  if (marker && props.map) {
    // Disable animation when clustering is enabled to prevent markers flying around
    const useAnimation = !markerClusterGroup.value
    props.map.setView(marker.getLatLng(), 15, {
      animate: useAnimation,
      duration: useAnimation ? 0.5 : 0
    })
    marker.openPopup()
  }
}

// Watch for data changes
watch(() => props.timelineData, (newData, oldData) => {
  if (baseLayerRef.value?.isReady) {
    // Check if we need to toggle clustering based on data size
    const newDataLength = newData?.length || 0
    const oldDataLength = oldData?.length || 0
    const oldShouldCluster = oldDataLength >= 50
    const newShouldCluster = newDataLength >= 50

    // If clustering requirement has changed, reinitialize the layer
    if (oldShouldCluster !== newShouldCluster) {
      console.log(`TimelineLayer: Clustering requirement changed (${oldDataLength} -> ${newDataLength} markers)`)
      reinitializeLayer(newShouldCluster)
    } else {
      renderTimelineMarkers()
    }
  }
}, { deep: true })

watch(() => props.highlightedItem, (newItem, oldItem) => {
  updateHighlightedMarker()
}, { deep: true })

// Watch for visibility changes
watch(() => props.visible, (isVisible) => {
  if (markerClusterGroup.value && props.map) {
    if (isVisible) {
      props.map.addLayer(markerClusterGroup.value)
    } else {
      props.map.removeLayer(markerClusterGroup.value)
    }
  }
})

// Cleanup on unmount
onBeforeUnmount(() => {
  if (markerClusterGroup.value && props.map) {
    props.map.removeLayer(markerClusterGroup.value)
    markerClusterGroup.value.clearLayers()
    markerClusterGroup.value = null
  }
})

// Expose methods
defineExpose({
  baseLayerRef,
  timelineMarkers: readonly(timelineMarkers),
  getMarkerByItem,
  focusOnMarker,
  clearTimelineMarkers
})
</script>

<style>
/* Popup styling */
.timeline-popup {
  font-family: var(--font-family, system-ui);
  font-size: 0.875rem;
  line-height: 1.4;
  min-width: 180px;
}

.popup-location {
  font-weight: 600;
  color: var(--gp-text-primary, #1e293b);
  margin-bottom: 0.5rem;
  font-size: 1rem;
}

.popup-address {
  font-weight: 600;
  color: var(--gp-text-primary, #1e293b);
  margin-bottom: 0.5rem;
}

.popup-time {
  color: var(--gp-text-secondary, #64748b);
  font-size: 0.8rem;
  margin-bottom: 0.25rem;
}

.popup-duration {
  color: var(--gp-primary, #1a56db);
  font-size: 0.85rem;
  font-weight: 500;
  margin-bottom: 0.25rem;
}

.popup-activity {
  color: var(--gp-text-muted, #94a3b8);
  font-size: 0.8rem;
}

/* Dark theme overrides for popups */
.p-dark .leaflet-popup-content-wrapper {
  background: linear-gradient(135deg, rgba(15, 23, 42, 0.95), rgba(30, 41, 59, 0.9)) !important;
  border: 1px solid rgba(71, 85, 105, 0.3) !important;
  color: rgba(255, 255, 255, 0.95) !important;
}

.p-dark .leaflet-popup-tip {
  background: rgba(15, 23, 42, 0.95) !important;
  border: none !important;
}

.p-dark .timeline-popup {
  background: transparent !important;
}

.p-dark .timeline-popup .popup-location,
.p-dark .timeline-popup .popup-address {
  color: rgba(255, 255, 255, 0.95) !important;
}

.p-dark .timeline-popup .popup-time {
  color: rgba(255, 255, 255, 0.8) !important;
}

.p-dark .timeline-popup .popup-duration {
  color: var(--gp-primary-light, #60a5fa) !important;
}

.p-dark .timeline-popup .popup-activity {
  color: rgba(255, 255, 255, 0.7) !important;
}

/* Light theme - ensure good contrast */
.timeline-popup .popup-location,
.timeline-popup .popup-address {
  color: #1e293b !important;
}

.timeline-popup .popup-time {
  color: #475569 !important;
}

.timeline-popup .popup-duration {
  color: #1a56db !important;
}

.timeline-popup .popup-activity {
  color: #64748b !important;
}
</style>

<style>
/* Custom cluster marker styles */
.custom-cluster-icon {
  background: transparent !important;
  border: none !important;
}

.cluster-marker {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
  font-size: 14px;
  color: white;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.3);
  transition: transform 0.2s ease;
}

.cluster-marker:hover {
  transform: scale(1.1);
}

.cluster-marker span {
  z-index: 1;
}

/* Small clusters (2-10 items) */
.cluster-marker-small {
  background: linear-gradient(135deg, #3b82f6 0%, #2563eb 100%);
  border: 3px solid #1e40af;
}

/* Medium clusters (11-100 items) */
.cluster-marker-medium {
  background: linear-gradient(135deg, #f59e0b 0%, #d97706 100%);
  border: 3px solid #b45309;
  width: 48px;
  height: 48px;
  font-size: 15px;
}

/* Large clusters (100+ items) */
.cluster-marker-large {
  background: linear-gradient(135deg, #ef4444 0%, #dc2626 100%);
  border: 3px solid #991b1b;
  width: 56px;
  height: 56px;
  font-size: 16px;
}

/* Dark mode adjustments */
.p-dark .cluster-marker-small {
  background: linear-gradient(135deg, #60a5fa 0%, #3b82f6 100%);
  border-color: #2563eb;
}

.p-dark .cluster-marker-medium {
  background: linear-gradient(135deg, #fbbf24 0%, #f59e0b 100%);
  border-color: #d97706;
}

.p-dark .cluster-marker-large {
  background: linear-gradient(135deg, #f87171 0%, #ef4444 100%);
  border-color: #dc2626;
}
</style>