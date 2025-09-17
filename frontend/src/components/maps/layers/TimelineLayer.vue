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
import { createTimelineIcon, createHighlightedTimelineIcon } from '@/utils/mapHelpers'
import {formatDuration} from "@/utils/calculationsHelpers";

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

// Computed
const hasTimelineData = computed(() => props.timelineData && props.timelineData.length > 0)

// Layer management
const handleLayerReady = (layerGroup) => {
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

    // Add to layer and track
    baseLayerRef.value.addToLayer(marker)
    timelineMarkers.value.push({
      marker,
      item,
      index,
      isHighlighted
    })
  })
}

const createPopupContent = (item) => {
  const date = new Date(item.timestamp)
  const durationText = item.stayDuration ? formatDuration(item.stayDuration) : null
  
  return `
    <div class="timeline-popup">
      <div class="popup-location">${item.locationName}</div>
      <div class="popup-time">${date.toLocaleString()}</div>
      ${durationText ? `<div class="popup-duration">Stay duration: ${durationText}</div>` : ''}
    </div>
  `.trim()
}

const clearTimelineMarkers = () => {
  timelineMarkers.value.forEach(({ marker }) => {
    baseLayerRef.value?.removeFromLayer(marker)
  })
  timelineMarkers.value = []
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
        props.map.setView([item.latitude, item.longitude], 16, {
          animate: true,
          duration: 0.8
        })
        
        // Open popup after a short delay to let zoom complete
        setTimeout(() => {
          marker.openPopup()
        }, 300)
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
    props.map.setView(marker.getLatLng(), 15)
    marker.openPopup()
  }
}

// Watch for data changes
watch(() => props.timelineData, () => {
  if (baseLayerRef.value?.isReady) {
    renderTimelineMarkers()
  }
}, { deep: true })

watch(() => props.highlightedItem, (newItem, oldItem) => {
  updateHighlightedMarker()
}, { deep: true })

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