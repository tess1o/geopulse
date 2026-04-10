<template></template>

<script setup>
import { onBeforeUnmount, watch } from 'vue'
import maplibregl from 'maplibre-gl'
import { useTimezone } from '@/composables/useTimezone'
import '@/maps/shared/styles/mapPopupContent.css'
import { isMapLibreMap, toFiniteNumber } from '@/maps/vector/utils/maplibreLayerUtils'
import { buildTimelineItemPopupHtml, escapeHtml } from '@/maps/shared/popupContentBuilders'
import { buildTimelineStackItems } from '@/maps/shared/timelineStackContent'
import {
  createTimelineMarkerElement,
  createTimelineStackMarkerElement
} from '@/maps/shared/timelineMarkerBuilder'

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

const state = {
  timelineMarkers: [],
  styleLoadHandler: null,
  boundMap: null,
  stackPopup: null,
  highlightedStayPopup: null,
  highlightedStayPopupTimeoutId: null,
  lastHighlightedStayKey: ''
}

const getTimelineKey = (item) => {
  if (item?.id) {
    return String(item.id)
  }

  return `${item?.timestamp || item?.startTime || 'unknown'}|${item?.latitude}|${item?.longitude}`
}

const isSameTimelineItem = (left, right) => {
  if (!left || !right) {
    return false
  }

  if (left.id && right.id) {
    return left.id === right.id
  }

  return Boolean(
    left.timestamp
    && right.timestamp
    && left.timestamp === right.timestamp
    && left.latitude === right.latitude
    && left.longitude === right.longitude
  )
}

const groupTimelineByCoordinate = () => {
  const groups = new Map()

  props.timelineData.forEach((item, index) => {
    const latitude = toFiniteNumber(item?.latitude)
    const longitude = toFiniteNumber(item?.longitude)

    if (latitude === null || longitude === null) {
      return
    }

    const key = `${latitude}|${longitude}`

    if (!groups.has(key)) {
      groups.set(key, {
        latitude,
        longitude,
        items: []
      })
    }

    groups.get(key).items.push({
      ...item,
      __timelineIndex: index,
      __timelineKey: getTimelineKey(item)
    })
  })

  return Array.from(groups.values())
}

const formatDateTimeDisplay = (dateValue) =>
  `${timezone.formatDateDisplay(dateValue)} ${timezone.format(dateValue, 'HH:mm:ss')}`

const createPopupContent = (item) => buildTimelineItemPopupHtml(item, { formatDateTimeDisplay })

const closeStackPopup = () => {
  if (state.stackPopup) {
    state.stackPopup.remove()
    state.stackPopup = null
  }
}

const closeHighlightedStayPopup = () => {
  if (state.highlightedStayPopupTimeoutId !== null) {
    clearTimeout(state.highlightedStayPopupTimeoutId)
    state.highlightedStayPopupTimeoutId = null
  }

  if (state.highlightedStayPopup) {
    state.highlightedStayPopup.remove()
    state.highlightedStayPopup = null
  }
}

const createStackPopupElement = (items, onSelect) => {
  const popupRoot = document.createElement('div')
  popupRoot.className = 'timeline-stack-popup'

  const header = document.createElement('div')
  header.className = 'stack-popup-header'
  header.textContent = `${items.length} events at this location`
  popupRoot.appendChild(header)

  const list = document.createElement('div')
  list.className = 'stack-popup-list'
  popupRoot.appendChild(list)

  const rows = buildTimelineStackItems(items, {
    formatDateDisplay: (value) => timezone.formatDateDisplay(value),
    formatTime: (value) => timezone.format(value, 'HH:mm:ss')
  })

  rows.forEach((row, stackIndex) => {
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
      domEvent.preventDefault()
      domEvent.stopPropagation()
      onSelect(row.item, domEvent)
    })

    list.appendChild(button)
  })

  popupRoot.addEventListener('click', (domEvent) => {
    domEvent.stopPropagation()
  })

  popupRoot.addEventListener('mousedown', (domEvent) => {
    domEvent.stopPropagation()
  })

  return popupRoot
}

const openStackPopupAtCoordinates = (candidateLng, candidateLat, items) => {
  if (!isMapLibreMap(props.map) || !Array.isArray(items) || items.length <= 1) {
    return
  }

  if (candidateLng === null || candidateLat === null) {
    return
  }

  closeStackPopup()

  const popupElement = createStackPopupElement(items, (selectedItem, domEvent) => {
    const parsedIndex = Number.parseInt(selectedItem?.__timelineIndex, 10)
    const index = Number.isFinite(parsedIndex) ? parsedIndex : -1

    closeStackPopup()

    emit('marker-click', {
      timelineItem: selectedItem,
      stackItems: items,
      index,
      marker: null,
      event: {
        originalEvent: domEvent,
        target: props.map,
        lngLat: {
          lng: candidateLng,
          lat: candidateLat
        }
      }
    })
  })

  state.stackPopup = new maplibregl.Popup({
    closeButton: true,
    closeOnClick: true,
    closeOnMove: false,
    maxWidth: '340px',
    offset: 16,
    className: 'gp-timeline-stack-popup-container'
  })
    .setLngLat([candidateLng, candidateLat])
    .setDOMContent(popupElement)
    .addTo(props.map)
}

const clearTimelineMarkers = () => {
  state.timelineMarkers.forEach((markerEntry) => {
    markerEntry.cleanup?.()
  })

  state.timelineMarkers = []

  if (isMapLibreMap(props.map)) {
    props.map.getCanvas().style.cursor = ''
  }
}

const findHighlightedGroupContext = () => {
  if (!props.highlightedItem) {
    return null
  }

  const targetKey = getTimelineKey(props.highlightedItem)
  if (!targetKey) {
    return null
  }

  const groups = groupTimelineByCoordinate()
  for (const group of groups) {
    const focusedItem = group.items.find((item) => (
      item.__timelineKey === targetKey || isSameTimelineItem(item, props.highlightedItem)
    ))

    if (focusedItem) {
      return { group, focusedItem, highlightedKey: targetKey }
    }
  }

  return null
}

const syncHighlightedStayFocus = () => {
  if (!isMapLibreMap(props.map) || !props.visible || !props.highlightedItem || props.highlightedItem.type === 'trip') {
    state.lastHighlightedStayKey = ''
    closeHighlightedStayPopup()
    closeStackPopup()
    return
  }

  const context = findHighlightedGroupContext()
  if (!context) {
    state.lastHighlightedStayKey = ''
    closeHighlightedStayPopup()
    closeStackPopup()
    return
  }

  if (state.lastHighlightedStayKey === context.highlightedKey) {
    return
  }

  state.lastHighlightedStayKey = context.highlightedKey

  const latitude = toFiniteNumber(context.group.latitude)
  const longitude = toFiniteNumber(context.group.longitude)
  if (latitude === null || longitude === null) {
    return
  }

  const currentZoom = props.map.getZoom()
  const defaultZoom = 16
  const targetZoom = currentZoom >= defaultZoom ? currentZoom : defaultZoom

  props.map.easeTo({
    center: [longitude, latitude],
    zoom: targetZoom,
    duration: 450
  })

  if (context.group.items.length > 1) {
    closeHighlightedStayPopup()
    setTimeout(() => {
      if (!isMapLibreMap(props.map) || state.lastHighlightedStayKey !== context.highlightedKey) {
        return
      }
      openStackPopupAtCoordinates(longitude, latitude, context.group.items)
    }, 220)
    return
  }

  closeStackPopup()
  closeHighlightedStayPopup()

  state.highlightedStayPopupTimeoutId = setTimeout(() => {
    state.highlightedStayPopupTimeoutId = null

    if (!isMapLibreMap(props.map) || state.lastHighlightedStayKey !== context.highlightedKey) {
      return
    }

    state.highlightedStayPopup = new maplibregl.Popup({
      closeButton: true,
      closeOnClick: true,
      closeOnMove: false,
      className: 'gp-timeline-popup-container',
      maxWidth: '320px',
      offset: 14
    })
      .setLngLat([longitude, latitude])
      .setHTML(createPopupContent(context.focusedItem))
      .addTo(props.map)
  }, 220)
}

const renderLayer = () => {
  if (!isMapLibreMap(props.map)) {
    return
  }

  clearTimelineMarkers()

  if (!props.visible || !Array.isArray(props.timelineData) || props.timelineData.length === 0) {
    syncHighlightedStayFocus()
    return
  }

  const groups = groupTimelineByCoordinate()

  groups.forEach((group) => {
    const primaryItem = group.items[0]
    const primaryIndex = Number.isFinite(primaryItem?.__timelineIndex) ? primaryItem.__timelineIndex : -1
    const isStack = group.items.length > 1
    const isHighlighted = Boolean(
      props.highlightedItem
      && group.items.some((item) => isSameTimelineItem(item, props.highlightedItem))
    )

    const markerSpec = isStack
      ? createTimelineStackMarkerElement({ count: group.items.length, highlighted: isHighlighted })
      : createTimelineMarkerElement({ item: primaryItem, highlighted: isHighlighted })

    markerSpec.element.style.zIndex = isHighlighted ? '340' : '320'

    const marker = new maplibregl.Marker({
      element: markerSpec.element,
      anchor: 'center',
      offset: markerSpec.offset || [0, 0]
    })
      .setLngLat([group.longitude, group.latitude])
      .addTo(props.map)

    let markerPopup = null
    if (!isStack && (primaryItem?.address || primaryItem?.timestamp)) {
      markerPopup = new maplibregl.Popup({
        closeButton: true,
        closeOnClick: true,
        closeOnMove: false,
        className: 'gp-timeline-popup-container',
        maxWidth: '320px',
        offset: 14
      }).setHTML(createPopupContent(primaryItem))
    }

    const openMarkerPopup = () => {
      if (!markerPopup || !isMapLibreMap(props.map)) {
        return
      }

      markerPopup
        .setLngLat([group.longitude, group.latitude])
        .addTo(props.map)
    }

    const closeMarkerPopup = () => {
      markerPopup?.remove()
    }

    const handleClick = (domEvent) => {
      domEvent.preventDefault()
      domEvent.stopPropagation()

      if (isStack) {
        openStackPopupAtCoordinates(group.longitude, group.latitude, group.items)
        return
      }

      emit('marker-click', {
        timelineItem: primaryItem,
        stackItems: group.items,
        index: primaryIndex,
        marker: null,
        event: {
          target: marker,
          originalEvent: domEvent,
          lngLat: {
            lng: group.longitude,
            lat: group.latitude
          }
        }
      })

      openMarkerPopup()
    }

    const handleMouseEnter = (domEvent) => {
      props.map.getCanvas().style.cursor = 'pointer'

      emit('marker-hover', {
        timelineItem: primaryItem,
        index: primaryIndex,
        marker: null,
        event: {
          target: marker,
          originalEvent: domEvent,
          lngLat: {
            lng: group.longitude,
            lat: group.latitude
          }
        }
      })
    }

    const handleMouseLeave = () => {
      props.map.getCanvas().style.cursor = ''
    }

    markerSpec.element.addEventListener('click', handleClick)
    markerSpec.element.addEventListener('mouseenter', handleMouseEnter)
    markerSpec.element.addEventListener('mouseleave', handleMouseLeave)

    state.timelineMarkers.push({
      marker,
      cleanup: () => {
        markerSpec.element.removeEventListener('click', handleClick)
        markerSpec.element.removeEventListener('mouseenter', handleMouseEnter)
        markerSpec.element.removeEventListener('mouseleave', handleMouseLeave)
        closeMarkerPopup()
        marker.remove()
      }
    })
  })

  syncHighlightedStayFocus()
}

const clearLayer = () => {
  clearTimelineMarkers()
  state.lastHighlightedStayKey = ''
  closeHighlightedStayPopup()
  closeStackPopup()

  if (state.boundMap && state.styleLoadHandler) {
    state.boundMap.off('style.load', state.styleLoadHandler)
    state.styleLoadHandler = null
  }

  state.boundMap = null
}

watch(
  () => [props.map, props.timelineData, props.highlightedItem, props.visible],
  () => {
    if (!isMapLibreMap(props.map)) {
      clearLayer()
      return
    }

    if (state.boundMap && state.boundMap !== props.map) {
      clearLayer()
    }

    state.boundMap = props.map

    if (!state.styleLoadHandler) {
      state.styleLoadHandler = () => renderLayer()
      props.map.on('style.load', state.styleLoadHandler)
    }

    renderLayer()
  },
  { immediate: true, deep: true }
)

onBeforeUnmount(() => {
  clearLayer()
})
</script>

<style>
.gp-timeline-popup-container .maplibregl-popup-content {
  padding: 0.6rem 0.7rem;
}

.gp-timeline-stack-popup-container .maplibregl-popup-content {
  padding: 0.7rem 0.8rem;
}

.p-dark .gp-timeline-stack-popup-container .maplibregl-popup-content {
  background: linear-gradient(135deg, rgba(15, 23, 42, 0.95), rgba(30, 41, 59, 0.9));
  border: 1px solid rgba(71, 85, 105, 0.3);
}

.p-dark .gp-timeline-popup-container .maplibregl-popup-content {
  background: linear-gradient(135deg, rgba(15, 23, 42, 0.95), rgba(30, 41, 59, 0.9));
  border: 1px solid rgba(71, 85, 105, 0.3);
  color: rgba(255, 255, 255, 0.95);
}

.p-dark .gp-timeline-stack-popup-container .maplibregl-popup-tip {
  border-top-color: rgba(15, 23, 42, 0.95);
}

.p-dark .gp-timeline-popup-container .maplibregl-popup-tip {
  border-top-color: rgba(15, 23, 42, 0.95);
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
  box-sizing: border-box;
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
