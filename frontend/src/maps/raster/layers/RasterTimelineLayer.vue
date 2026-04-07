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
import BaseLayer from '@/components/maps/layers/BaseLayer.vue'
import { createTimelineIcon, createHighlightedTimelineIcon } from '@/utils/mapHelpers'
import { useTimezone } from '@/composables/useTimezone'
import '@/maps/shared/styles/mapPopupContent.css'
import { buildTimelineItemPopupHtml, escapeHtml } from '@/maps/shared/popupContentBuilders'
import { buildTimelineStackItems } from '@/maps/shared/timelineStackContent'

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

const isFiniteCoordinate = (value) => typeof value === 'number' && Number.isFinite(value)

const hasValidCoordinates = (item) => (
  item &&
  isFiniteCoordinate(item.latitude) &&
  isFiniteCoordinate(item.longitude)
)

const getCoordinateKey = (latitude, longitude) => `${latitude}|${longitude}`

const isSameTimelineItem = (left, right) => {
  if (!left || !right) return false

  if (left.id && right.id) {
    return left.id === right.id
  }

  return Boolean(
    left.timestamp &&
    right.timestamp &&
    left.timestamp === right.timestamp &&
    left.latitude === right.latitude &&
    left.longitude === right.longitude
  )
}

const createStackTimelineIcon = (count, isHighlighted = false) => {
  const markerClass = isHighlighted
    ? 'timeline-stack-marker timeline-stack-marker-highlighted'
    : 'timeline-stack-marker'

  return L.divIcon({
    html: `<div class="${markerClass}"><span>${count}</span></div>`,
    className: 'timeline-stack-icon',
    iconSize: L.point(isHighlighted ? 34 : 30, isHighlighted ? 34 : 30),
    iconAnchor: L.point(isHighlighted ? 17 : 15, isHighlighted ? 17 : 15)
  })
}

const formatDateTimeDisplay = (dateValue) =>
  `${timezone.formatDateDisplay(dateValue)} ${timezone.format(dateValue, 'HH:mm:ss')}`

const createStackPopupElement = (marker, markerItems) => {
  const popupRoot = document.createElement('div')
  popupRoot.className = 'timeline-stack-popup'

  const header = document.createElement('div')
  header.className = 'stack-popup-header'
  header.textContent = `${markerItems.length} events at this location`
  popupRoot.appendChild(header)

  const list = document.createElement('div')
  list.className = 'stack-popup-list'
  popupRoot.appendChild(list)

  const rows = buildTimelineStackItems(
    markerItems.map(({ item }) => item),
    {
      formatDateDisplay: (value) => timezone.formatDateDisplay(value),
      formatTime: (value) => timezone.format(value, 'HH:mm:ss')
    }
  )

  rows.forEach((row, stackIndex) => {
    const markerItem = markerItems[stackIndex]

    const button = document.createElement('button')
    button.type = 'button'
    button.className = `timeline-stack-select ${row.typeClass}`
    button.dataset.stackItemIndex = String(stackIndex)
    button.innerHTML = `
      <div class="stack-item-time">🕐 ${escapeHtml(row.dateStr)}</div>
      <div class="stack-item-title">${escapeHtml(row.title)}</div>
      ${row.subtitle ? `<div class="stack-item-subtitle">${escapeHtml(row.subtitle)}</div>` : ''}
      ${row.meta ? `<div class="stack-item-meta">${escapeHtml(row.meta)}</div>` : ''}
    `.trim()

    button.addEventListener('click', (domEvent) => {
      L.DomEvent.stop(domEvent)

      marker.closePopup()
      emit('marker-click', {
        timelineItem: row.item,
        index: markerItem?.index ?? -1,
        marker,
        event: {
          target: marker,
          originalEvent: domEvent
        }
      })
    })

    list.appendChild(button)
  })

  L.DomEvent.disableClickPropagation(popupRoot)
  L.DomEvent.disableScrollPropagation(popupRoot)

  return popupRoot
}

const groupTimelineItemsByCoordinates = () => {
  const groupedItems = new Map()

  props.timelineData.forEach((item, index) => {
    if (!hasValidCoordinates(item)) return

    const key = getCoordinateKey(item.latitude, item.longitude)
    if (!groupedItems.has(key)) {
      groupedItems.set(key, [])
    }

    groupedItems.get(key).push({ item, index })
  })

  return Array.from(groupedItems.values())
}

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
  if (!baseLayerRef.value) return

  // Always clear existing markers first so stale markers are removed when
  // a new range has no timeline data.
  clearTimelineMarkers()

  if (!hasTimelineData.value) return

  const groupedItems = groupTimelineItemsByCoordinates()
  groupedItems.forEach((markerItems) => {
    const [{ item: primaryItem, index: primaryIndex }] = markerItems
    const isStack = markerItems.length > 1
    const highlightedItem = markerItems.find(({ item }) => isSameTimelineItem(props.highlightedItem, item))
    const isHighlighted = Boolean(highlightedItem)

    const icon = isStack
      ? createStackTimelineIcon(markerItems.length, isHighlighted)
      : (isHighlighted ? createHighlightedTimelineIcon(primaryItem) : createTimelineIcon(primaryItem))

    const marker = L.marker([primaryItem.latitude, primaryItem.longitude], {
      icon,
      timelineItem: primaryItem,
      timelineItems: markerItems.map(({ item }) => item),
      timelineIndex: primaryIndex,
      ...props.markerOptions
    })

    if (isStack) {
      marker.bindPopup(createStackPopupElement(marker, markerItems), { maxWidth: 320 })

      marker.on('click', () => {
        marker.openPopup()
      })

      marker.on('mouseover', (e) => {
        emit('marker-hover', {
          timelineItem: primaryItem,
          index: primaryIndex,
          marker,
          event: e
        })
      })
    } else {
      marker.on('click', (e) => {
        emit('marker-click', {
          timelineItem: primaryItem,
          index: primaryIndex,
          marker,
          event: e
        })
      })

      marker.on('mouseover', (e) => {
        emit('marker-hover', {
          timelineItem: primaryItem,
          index: primaryIndex,
          marker,
          event: e
        })
      })

      if (primaryItem.address || primaryItem.timestamp) {
        marker.bindPopup(buildPopupContent(primaryItem))
      }
    }

    if (markerClusterGroup.value) {
      markerClusterGroup.value.addLayer(marker)
    } else {
      baseLayerRef.value.addToLayer(marker)
    }

    timelineMarkers.value.push({
      marker,
      items: markerItems.map(({ item }) => item),
      indexes: markerItems.map(({ index }) => index),
      isStack,
      isHighlighted
    })
  })
}

const buildPopupContent = (item) => buildTimelineItemPopupHtml(item, { formatDateTimeDisplay })

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
  timelineMarkers.value.forEach(({ marker, items, isHighlighted, isStack }, index) => {
    const shouldBeHighlighted = Boolean(
      props.highlightedItem &&
      items.some((item) => isSameTimelineItem(props.highlightedItem, item))
    )

    if (shouldBeHighlighted !== isHighlighted) {
      const focusedItem = items.find((item) => isSameTimelineItem(props.highlightedItem, item)) || items[0]
      const newIcon = isStack
        ? createStackTimelineIcon(items.length, shouldBeHighlighted)
        : (shouldBeHighlighted ? createHighlightedTimelineIcon(focusedItem) : createTimelineIcon(focusedItem))

      marker.setIcon(newIcon)

      // If highlighting this marker, only zoom for stay items (trips handle their own zooming)
      if (shouldBeHighlighted && focusedItem && props.map && focusedItem.type !== 'trip') {
        // Disable animation when clustering is enabled to prevent markers flying around
        const useAnimation = !markerClusterGroup.value

        // Only change zoom if currently zoomed out beyond default
        // This preserves user's zoom level when examining multiple nearby stays
        const currentZoom = props.map.getZoom()
        const defaultZoom = 16
        const targetZoom = currentZoom >= defaultZoom ? currentZoom : defaultZoom

        props.map.setView([focusedItem.latitude, focusedItem.longitude], targetZoom, {
          animate: useAnimation,
          duration: useAnimation ? 0.8 : 0
        })

        // Open popup after a short delay (or immediately if no animation)
        setTimeout(() => {
          marker.openPopup()
        }, useAnimation ? 300 : 100)
      } else if (shouldBeHighlighted && focusedItem && focusedItem.type !== 'trip') {
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
  const found = timelineMarkers.value.find(({ items }) =>
    items.some((item) => isSameTimelineItem(timelineItem, item))
  )
  return found?.marker
}

const focusOnMarker = (timelineItem) => {
  const marker = getMarkerByItem(timelineItem)
  if (marker && props.map) {
    // Disable animation when clustering is enabled to prevent markers flying around
    const useAnimation = !markerClusterGroup.value

    // Only change zoom if currently zoomed out beyond default
    // This preserves user's zoom level when examining multiple nearby stays
    const currentZoom = props.map.getZoom()
    const defaultZoom = 15
    const targetZoom = currentZoom >= defaultZoom ? currentZoom : defaultZoom

    props.map.setView(marker.getLatLng(), targetZoom, {
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
.timeline-stack-icon {
  background: transparent !important;
  border: none !important;
}

.timeline-stack-marker {
  width: 30px;
  height: 30px;
  border-radius: 999px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #0f766e 0%, #0ea5a4 100%);
  border: 2px solid #134e4a;
  color: #ffffff;
  font-size: 0.78rem;
  font-weight: 700;
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.28);
}

.timeline-stack-marker-highlighted {
  width: 34px;
  height: 34px;
  background: linear-gradient(135deg, #ea580c 0%, #f97316 100%);
  border-color: #9a3412;
}

.p-dark .timeline-stack-marker {
  background: linear-gradient(135deg, #14b8a6 0%, #0d9488 100%);
  border-color: #134e4a;
}

.p-dark .timeline-stack-marker-highlighted {
  background: linear-gradient(135deg, #fb923c 0%, #f97316 100%);
  border-color: #c2410c;
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
