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
import { storeToRefs } from 'pinia'
import { useI18n } from 'vue-i18n'
import L from 'leaflet'
import BaseLayer from '@/components/maps/layers/BaseLayer.vue'
import { createTimelineIcon, createHighlightedTimelineIcon } from '@/utils/mapHelpers'
import { useAuthStore } from '@/stores/auth'
import { useTimezone } from '@/composables/useTimezone'
import { createMarkerClusterGroup } from '@/maps/raster/utils/createMarkerClusterGroup'
import { groupItemsByProximity } from '@/maps/shared/nearbyPointGrouping'
import { escapeHtml } from '@/maps/shared/popupContentBuilders'
import { buildTimelineStackItems } from '@/maps/shared/timelineStackContent'
import MapInfoPopup from '@/maps/shared/popups/MapInfoPopup.vue'
import { mountMapPopup } from '@/maps/shared/popups/mountMapPopup'
import { buildTimelineItemPopupModel } from '@/maps/shared/popups/timelinePopupModels'
import {
  getMapPopupVariantClassName,
  MAP_POPUP_COMPACT_MAX_WIDTH_PX
} from '@/maps/shared/popups/mapPopupOptions'

const authStore = useAuthStore()
const { distanceUnit } = storeToRefs(authStore)
const timezone = useTimezone()
const { t } = useI18n()

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

const emit = defineEmits(['marker-click', 'marker-hover', 'marker-contextmenu'])

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

const isStayWithPlaceDetails = (item) => Boolean(
  item?.type === 'stay' &&
  (item.favoriteId || item.geocodingId)
)

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

const createStackTimelineIcon = (count, isHighlighted = false, isDimmed = false) => {
  const markerClass = [
    'timeline-stack-marker',
    isHighlighted ? 'timeline-stack-marker-highlighted' : '',
    isDimmed ? 'timeline-stack-marker-dimmed' : ''
  ].filter(Boolean).join(' ')

  return L.divIcon({
    html: `<div class="${markerClass}"><span>${count}</span></div>`,
    className: 'timeline-stack-icon',
    iconSize: L.point(isHighlighted ? 34 : 30, isHighlighted ? 34 : 30),
    iconAnchor: L.point(isHighlighted ? 17 : 15, isHighlighted ? 17 : 15)
  })
}

const formatDateTimeDisplay = (dateValue) =>
  `${timezone.formatDateDisplay(dateValue)} ${timezone.formatTime(dateValue, { withSeconds: true })}`

const createStackPopupElement = (marker, markerItems) => {
  const popupRoot = document.createElement('div')
  popupRoot.className = 'timeline-stack-popup'

  const header = document.createElement('div')
  header.className = 'stack-popup-header'
  header.textContent = t('maps.popups.timeline.eventsAtLocation', { count: markerItems.length })
  popupRoot.appendChild(header)

  const list = document.createElement('div')
  list.className = 'stack-popup-list'
  popupRoot.appendChild(list)

  const rows = buildTimelineStackItems(
    markerItems.map(({ item }) => item),
    {
      formatDateDisplay: (value) => timezone.formatDateDisplay(value),
      formatTime: (value) => timezone.formatTime(value, { withSeconds: true }),
      unit: distanceUnit.value
    }
  )

  rows.forEach((row, stackIndex) => {
    const markerItem = markerItems[stackIndex]

    const button = document.createElement('button')
    button.type = 'button'
    button.className = `timeline-stack-select ${row.typeClass}`
    button.dataset.stackItemIndex = String(stackIndex)
    button.innerHTML = `
      <div class="stack-item-time">${escapeHtml(row.dateStr)}</div>
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

    button.addEventListener('contextmenu', (domEvent) => {
      L.DomEvent.stop(domEvent)
      domEvent.preventDefault()
      domEvent.stopPropagation()
      domEvent.stopImmediatePropagation?.()

      if (!isStayWithPlaceDetails(row.item)) {
        return
      }

      marker.closePopup()
      emit('marker-contextmenu', {
        timelineItem: row.item,
        index: markerItem?.index ?? -1,
        marker,
        event: domEvent,
        latlng: marker.getLatLng?.() || null,
        type: 'stay'
      })
    })

    list.appendChild(button)
  })

  L.DomEvent.disableClickPropagation(popupRoot)
  L.DomEvent.disableScrollPropagation(popupRoot)

  return popupRoot
}

const groupTimelineItemsByCoordinates = () => {
  const candidates = props.timelineData
    .map((item, index) => ({ item, index }))
    .filter(({ item }) => hasValidCoordinates(item))

  return groupItemsByProximity(candidates, {
    getLatitude: ({ item }) => item.latitude,
    getLongitude: ({ item }) => item.longitude
  })
}

const getClusterSizeClass = (count) => {
  if (count > 100) return 'large'
  if (count > 10) return 'medium'
  return 'small'
}

const clusterContainsHighlightedItem = (cluster) => {
  if (!props.highlightedItem || !cluster?.getAllChildMarkers) {
    return false
  }

  return cluster.getAllChildMarkers().some((marker) => {
    const timelineItems = Array.isArray(marker?.options?.timelineItems)
      ? marker.options.timelineItems
      : [marker?.options?.timelineItem].filter(Boolean)

    return timelineItems.some((item) => isSameTimelineItem(props.highlightedItem, item))
  })
}

const createClusterTimelineIcon = (cluster) => {
  const count = cluster.getChildCount()
  const sizeClass = getClusterSizeClass(count)
  const containsHighlight = clusterContainsHighlightedItem(cluster)
  const isDimmed = Boolean(props.highlightedItem) && !containsHighlight
  const clusterClass = [
    'cluster-marker',
    `cluster-marker-${sizeClass}`,
    isDimmed ? 'cluster-marker-dimmed' : ''
  ].filter(Boolean).join(' ')

  return L.divIcon({
    html: `<div class="${clusterClass}"><span>${count}</span></div>`,
    className: 'custom-cluster-icon',
    iconSize: L.point(40, 40)
  })
}

// Layer management
const handleLayerReady = () => {
  // Always cluster, matching Photos' behavior - no marker-count threshold.
  markerClusterGroup.value = createMarkerClusterGroup({
    maxClusterRadius: 50, // Pixels - smaller radius means less aggressive clustering
    iconCreateFunction: createClusterTimelineIcon
  })

  if (markerClusterGroup.value && props.map) {
    props.map.addLayer(markerClusterGroup.value)
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

  const groups = groupTimelineItemsByCoordinates()
  const hasActiveHighlight = Boolean(props.highlightedItem)
  groups.forEach((group) => {
    const markerItems = group.items
    const [{ item: primaryItem, index: primaryIndex }] = markerItems
    const isStack = markerItems.length > 1
    const highlightedItem = markerItems.find(({ item }) => isSameTimelineItem(props.highlightedItem, item))
    const isHighlighted = Boolean(highlightedItem)
    const isDimmed = hasActiveHighlight && !isHighlighted

    const icon = isStack
      ? createStackTimelineIcon(markerItems.length, isHighlighted, isDimmed)
      : (isHighlighted ? createHighlightedTimelineIcon(primaryItem) : createTimelineIcon(primaryItem, { dimmed: isDimmed }))

    const marker = L.marker([group.latitude, group.longitude], {
      icon,
      timelineItem: primaryItem,
      timelineItems: markerItems.map(({ item }) => item),
      timelineIndex: primaryIndex,
      ...props.markerOptions
    })
    let popupMount = null

    if (isStack) {
      marker.bindPopup(createStackPopupElement(marker, markerItems), {
        maxWidth: MAP_POPUP_COMPACT_MAX_WIDTH_PX,
        className: getMapPopupVariantClassName('compact', 'gp-timeline-stack-popup-container')
      })

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

      marker.on('contextmenu', (e) => {
        if (!isStayWithPlaceDetails(primaryItem)) {
          return
        }

        if (e.originalEvent) {
          e.originalEvent.preventDefault()
          e.originalEvent.stopPropagation()
          e.originalEvent.stopImmediatePropagation?.()
        }

        L.DomEvent.stop(e)

        emit('marker-contextmenu', {
          timelineItem: primaryItem,
          index: primaryIndex,
          marker,
          event: e.originalEvent,
          latlng: e.latlng,
          type: 'stay'
        })
      })

      if (primaryItem.address || primaryItem.timestamp) {
        popupMount = mountMapPopup(
          MapInfoPopup,
          buildPopupModel(primaryItem)
        )
        marker.bindPopup(popupMount.element, {
          maxWidth: MAP_POPUP_COMPACT_MAX_WIDTH_PX,
          className: getMapPopupVariantClassName('compact', 'gp-timeline-popup-container')
        })
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
      isHighlighted,
      isDimmed,
      popupMount
    })
  })
}

const buildPopupModel = (item) => buildTimelineItemPopupModel(item, {
  formatDateTimeDisplay,
  unit: distanceUnit.value
})

const clearTimelineMarkers = () => {
  timelineMarkers.value.forEach(({ popupMount }) => {
    popupMount?.unmount?.()
  })

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

const updateHighlightedMarker = () => {
  timelineMarkers.value.forEach(({ marker, items, isHighlighted, isDimmed, isStack }, index) => {
    const shouldBeHighlighted = Boolean(
      props.highlightedItem &&
      items.some((item) => isSameTimelineItem(props.highlightedItem, item))
    )
    const shouldBeDimmed = Boolean(props.highlightedItem) && !shouldBeHighlighted

    if (shouldBeHighlighted !== isHighlighted || shouldBeDimmed !== isDimmed) {
      const focusedItem = items.find((item) => isSameTimelineItem(props.highlightedItem, item)) || items[0]
      const newIcon = isStack
        ? createStackTimelineIcon(items.length, shouldBeHighlighted, shouldBeDimmed)
        : (shouldBeHighlighted ? createHighlightedTimelineIcon(focusedItem) : createTimelineIcon(focusedItem, { dimmed: shouldBeDimmed }))

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
        markerData.isDimmed = shouldBeDimmed
      }
    }
  })

  markerClusterGroup.value?.refreshClusters?.()
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
watch(() => props.timelineData, () => {
  if (baseLayerRef.value?.isReady) {
    renderTimelineMarkers()
  }
}, { deep: true })

watch(() => props.highlightedItem, (newItem, oldItem) => {
  updateHighlightedMarker()
}, { deep: true })

watch(() => distanceUnit.value, () => {
  if (baseLayerRef.value?.isReady) {
    renderTimelineMarkers()
  }
})

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
/* Markers keep their light-map colours in dark mode: the base tiles don't switch theme (see tokens.css). */
.timeline-stack-icon {
  background: transparent;
  border: none;
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

.timeline-stack-marker-dimmed {
  opacity: 0.28;
  filter: grayscale(0.35) saturate(0.7);
}
</style>

<style>
/* Custom cluster marker styles */
.custom-cluster-icon {
  background: transparent;
  border: none;
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

.cluster-marker-dimmed {
  opacity: 0.28;
  filter: grayscale(0.35) saturate(0.7);
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
</style>
