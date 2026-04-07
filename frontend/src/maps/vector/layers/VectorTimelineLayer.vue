<template></template>

<script setup>
import { onBeforeUnmount, watch } from 'vue'
import maplibregl from 'maplibre-gl'
import { useTimezone } from '@/composables/useTimezone'
import '@/maps/shared/styles/mapPopupContent.css'
import {
  createFeatureCollection,
  ensureClusterSource,
  ensureLayer,
  isMapLibreMap,
  nextLayerToken,
  removeLayers,
  removeSources,
  setLayerVisibility,
  toFiniteNumber
} from '@/maps/vector/utils/maplibreLayerUtils'
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

const state = {
  token: nextLayerToken('gp-timeline'),
  sourceId: '',
  clusterLayerId: '',
  clusterCountLayerId: '',
  unclusteredLayerId: '',
  unclusteredIconLayerId: '',
  unclusteredCountLayerId: '',
  highlightedLayerId: '',
  listeners: [],
  styleLoadHandler: null,
  boundMap: null,
  stackPopup: null,
  highlightedStayPopup: null,
  highlightedStayPopupTimeoutId: null,
  lastHighlightedStayKey: '',
  sourceClustered: null,
  lastPointClickEvent: null,
  lastClusterClickEvent: null
}

state.sourceId = `${state.token}-source`
state.clusterLayerId = `${state.token}-cluster`
state.clusterCountLayerId = `${state.token}-cluster-count`
state.unclusteredLayerId = `${state.token}-point`
state.unclusteredIconLayerId = `${state.token}-point-icon`
state.unclusteredCountLayerId = `${state.token}-point-count`
state.highlightedLayerId = `${state.token}-point-highlight`

const resolveTripGlyph = (movementType) => {
  const normalized = String(movementType || 'UNKNOWN').toUpperCase()
  switch (normalized) {
    case 'WALK':
      return '🚶'
    case 'RUNNING':
      return '🏃'
    case 'BICYCLE':
      return '🚴'
    case 'CAR':
      return '🚗'
    case 'TRAIN':
      return '🚊'
    case 'FLIGHT':
      return '✈'
    default:
      return '↗'
  }
}

const resolveMarkerGlyph = (item) => {
  if (item?.type === 'trip') {
    return resolveTripGlyph(item?.movementType)
  }

  if (item?.type === 'dataGap') {
    return '?'
  }

  return '📍'
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

    const key = `${latitude.toFixed(6)}|${longitude.toFixed(6)}`

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

  return groups
}

const buildCollection = () => {
  const features = []
  const groups = groupTimelineByCoordinate()

  groups.forEach((group, groupKey) => {
    const items = group.items
    const primary = items[items.length - 1] || items[0]

    features.push({
      type: 'Feature',
      geometry: {
        type: 'Point',
        coordinates: [group.longitude, group.latitude]
      },
      properties: {
        groupKey,
        itemCount: items.length,
        primaryRaw: JSON.stringify(primary || {}),
        itemsRaw: JSON.stringify(items),
        stackIndexesRaw: JSON.stringify(items.map((item) => (
          Number.isFinite(item?.__timelineIndex) ? item.__timelineIndex : -1
        ))),
        timelineKeys: items.map((item) => String(item?.__timelineKey || '')),
        itemType: primary?.type || 'stay',
        markerGlyph: resolveMarkerGlyph(primary),
        timelineKey: primary?.__timelineKey || '',
        timelineIndex: Number.isFinite(primary?.__timelineIndex) ? primary.__timelineIndex : -1
      }
    })
  })

  return createFeatureCollection(features)
}

const getHighlightedTimelineKey = () => {
  if (!props.highlightedItem) {
    return null
  }

  return getTimelineKey(props.highlightedItem)
}

const createHighlightedFilter = (highlightedTimelineKey) => {
  const key = highlightedTimelineKey || '__none__'

  return [
    'all',
    ['!', ['has', 'point_count']],
    [
      'any',
      ['==', ['get', 'timelineKey'], key],
      ['in', key, ['get', 'timelineKeys']]
    ]
  ]
}

const resolveTimelineItemByIndex = (index) => {
  if (!Number.isFinite(index) || index < 0 || index >= props.timelineData.length) {
    return null
  }

  const item = props.timelineData[index]
  if (!item) {
    return null
  }

  return {
    ...item,
    __timelineIndex: index,
    __timelineKey: getTimelineKey(item)
  }
}

const parsePrimaryFromEvent = (event) => {
  const index = Number.parseInt(event?.features?.[0]?.properties?.timelineIndex, 10)
  const byIndex = resolveTimelineItemByIndex(index)
  if (byIndex) {
    return byIndex
  }

  const raw = event?.features?.[0]?.properties?.primaryRaw
  if (!raw) {
    return null
  }

  try {
    return JSON.parse(raw)
  } catch {
    return null
  }
}

const parseItemsFromEvent = (event) => {
  const stackIndexesRaw = event?.features?.[0]?.properties?.stackIndexesRaw
  if (stackIndexesRaw) {
    try {
      const indexes = JSON.parse(stackIndexesRaw)
      if (Array.isArray(indexes)) {
        const resolved = indexes
          .map((value) => resolveTimelineItemByIndex(Number.parseInt(value, 10)))
          .filter(Boolean)
        if (resolved.length > 0) {
          return resolved
        }
      }
    } catch {
      // Fallback to raw payload parsing below.
    }
  }

  const raw = event?.features?.[0]?.properties?.itemsRaw
  if (!raw) {
    return []
  }

  try {
    return JSON.parse(raw)
  } catch {
    return []
  }
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
        target: props.map
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

const openStackPopup = (event, items) => {
  const featureCoordinates = event?.features?.[0]?.geometry?.coordinates
  const candidateLng = Array.isArray(featureCoordinates) ? toFiniteNumber(featureCoordinates[0]) : toFiniteNumber(event?.lngLat?.lng)
  const candidateLat = Array.isArray(featureCoordinates) ? toFiniteNumber(featureCoordinates[1]) : toFiniteNumber(event?.lngLat?.lat)
  openStackPopupAtCoordinates(candidateLng, candidateLat, items)
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
  for (const group of groups.values()) {
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

  const useAnimation = !state.sourceClustered
  const currentZoom = props.map.getZoom()
  const defaultZoom = 16
  const targetZoom = currentZoom >= defaultZoom ? currentZoom : defaultZoom

  if (useAnimation) {
    props.map.easeTo({
      center: [longitude, latitude],
      zoom: targetZoom,
      duration: 800
    })
  } else {
    props.map.jumpTo({
      center: [longitude, latitude],
      zoom: targetZoom
    })
  }

  if (context.group.items.length > 1) {
    closeHighlightedStayPopup()
    setTimeout(() => {
      if (!isMapLibreMap(props.map) || state.lastHighlightedStayKey !== context.highlightedKey) {
        return
      }
      openStackPopupAtCoordinates(longitude, latitude, context.group.items)
    }, useAnimation ? 300 : 100)
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
  }, useAnimation ? 300 : 100)
}

const registerEvents = () => {
  if (!isMapLibreMap(props.map)) {
    return
  }

  const shouldSkipDuplicateClick = (key, originalEvent) => {
    if (!originalEvent || (typeof originalEvent !== 'object' && typeof originalEvent !== 'function')) {
      return false
    }

    if (state[key] === originalEvent) {
      return true
    }

    state[key] = originalEvent
    setTimeout(() => {
      if (state[key] === originalEvent) {
        state[key] = null
      }
    }, 0)
    return false
  }

  const handleClusterClick = (event) => {
    if (shouldSkipDuplicateClick('lastClusterClickEvent', event?.originalEvent)) {
      return
    }

    const clusterId = event?.features?.[0]?.properties?.cluster_id
    if (clusterId === undefined || clusterId === null) {
      return
    }

    const source = props.map.getSource(state.sourceId)
    if (!source || typeof source.getClusterExpansionZoom !== 'function') {
      return
    }

    source.getClusterExpansionZoom(clusterId, (error, zoom) => {
      if (error) {
        return
      }

      const center = event?.features?.[0]?.geometry?.coordinates
      if (!Array.isArray(center)) {
        return
      }

      props.map.easeTo({
        center,
        zoom,
        duration: 280
      })
    })
  }

  const handlePointClick = (event) => {
    if (shouldSkipDuplicateClick('lastPointClickEvent', event?.originalEvent)) {
      return
    }

    const timelineItem = parsePrimaryFromEvent(event)
    const items = parseItemsFromEvent(event)
    const index = Number.parseInt(event?.features?.[0]?.properties?.timelineIndex, 10)

    if (items.length > 1) {
      openStackPopup(event, items)
      return
    }

    closeStackPopup()

    emit('marker-click', {
      timelineItem,
      stackItems: items,
      index: Number.isFinite(index) ? index : -1,
      marker: null,
      event
    })
  }

  const handlePointHover = (event) => {
    props.map.getCanvas().style.cursor = 'pointer'

    const timelineItem = parsePrimaryFromEvent(event)
    const index = Number.parseInt(event?.features?.[0]?.properties?.timelineIndex, 10)

    emit('marker-hover', {
      timelineItem,
      index: Number.isFinite(index) ? index : -1,
      marker: null,
      event
    })
  }

  const handlePointerLeave = () => {
    props.map.getCanvas().style.cursor = ''
  }

  const handleClusterHover = () => {
    props.map.getCanvas().style.cursor = 'pointer'
  }

  props.map.on('click', state.clusterLayerId, handleClusterClick)
  props.map.on('click', state.clusterCountLayerId, handleClusterClick)
  props.map.on('mousemove', state.clusterLayerId, handleClusterHover)
  props.map.on('mousemove', state.clusterCountLayerId, handleClusterHover)
  props.map.on('click', state.unclusteredLayerId, handlePointClick)
  props.map.on('click', state.unclusteredIconLayerId, handlePointClick)
  props.map.on('click', state.unclusteredCountLayerId, handlePointClick)
  props.map.on('mousemove', state.unclusteredLayerId, handlePointHover)
  props.map.on('mousemove', state.unclusteredIconLayerId, handlePointHover)
  props.map.on('mousemove', state.unclusteredCountLayerId, handlePointHover)
  props.map.on('mouseleave', state.clusterLayerId, handlePointerLeave)
  props.map.on('mouseleave', state.clusterCountLayerId, handlePointerLeave)
  props.map.on('mouseleave', state.unclusteredLayerId, handlePointerLeave)
  props.map.on('mouseleave', state.unclusteredIconLayerId, handlePointerLeave)
  props.map.on('mouseleave', state.unclusteredCountLayerId, handlePointerLeave)

  state.listeners = [
    { event: 'click', layerId: state.clusterLayerId, handler: handleClusterClick },
    { event: 'click', layerId: state.clusterCountLayerId, handler: handleClusterClick },
    { event: 'mousemove', layerId: state.clusterLayerId, handler: handleClusterHover },
    { event: 'mousemove', layerId: state.clusterCountLayerId, handler: handleClusterHover },
    { event: 'click', layerId: state.unclusteredLayerId, handler: handlePointClick },
    { event: 'click', layerId: state.unclusteredIconLayerId, handler: handlePointClick },
    { event: 'click', layerId: state.unclusteredCountLayerId, handler: handlePointClick },
    { event: 'mousemove', layerId: state.unclusteredLayerId, handler: handlePointHover },
    { event: 'mousemove', layerId: state.unclusteredIconLayerId, handler: handlePointHover },
    { event: 'mousemove', layerId: state.unclusteredCountLayerId, handler: handlePointHover },
    { event: 'mouseleave', layerId: state.clusterLayerId, handler: handlePointerLeave },
    { event: 'mouseleave', layerId: state.clusterCountLayerId, handler: handlePointerLeave },
    { event: 'mouseleave', layerId: state.unclusteredLayerId, handler: handlePointerLeave },
    { event: 'mouseleave', layerId: state.unclusteredIconLayerId, handler: handlePointerLeave },
    { event: 'mouseleave', layerId: state.unclusteredCountLayerId, handler: handlePointerLeave }
  ]
}

const unregisterEvents = () => {
  if (!isMapLibreMap(props.map)) {
    state.listeners = []
    return
  }

  state.listeners.forEach(({ event, layerId, handler }) => {
    if (props.map.getLayer(layerId)) {
      props.map.off(event, layerId, handler)
    }
  })

  state.listeners = []
  props.map.getCanvas().style.cursor = ''
}

const bringTimelineLayersToFront = () => {
  if (!isMapLibreMap(props.map)) {
    return
  }

  // Keep timeline markers and labels above path layers for readability.
  const orderedLayerIds = [
    state.clusterLayerId,
    state.unclusteredLayerId,
    state.highlightedLayerId,
    state.unclusteredIconLayerId,
    state.clusterCountLayerId,
    state.unclusteredCountLayerId
  ]

  orderedLayerIds.forEach((layerId) => {
    if (props.map.getLayer(layerId)) {
      props.map.moveLayer(layerId)
    }
  })
}

const renderLayer = () => {
  if (!isMapLibreMap(props.map)) {
    return
  }

  const collection = buildCollection()
  const highlightedTimelineKey = getHighlightedTimelineKey()
  const shouldUseClustering = Array.isArray(props.timelineData) && props.timelineData.length >= 50

  if (state.sourceClustered !== null && state.sourceClustered !== shouldUseClustering) {
    unregisterEvents()
    removeLayers(props.map, [
      state.highlightedLayerId,
      state.unclusteredIconLayerId,
      state.unclusteredCountLayerId,
      state.unclusteredLayerId,
      state.clusterCountLayerId,
      state.clusterLayerId
    ])
    removeSources(props.map, [state.sourceId])
  }

  ensureClusterSource(props.map, state.sourceId, collection, {
    cluster: shouldUseClustering,
    clusterRadius: 45,
    clusterMaxZoom: 14
  })
  state.sourceClustered = shouldUseClustering

  ensureLayer(props.map, {
    id: state.clusterLayerId,
    type: 'circle',
    source: state.sourceId,
    filter: ['has', 'point_count'],
    paint: {
      'circle-color': '#1d4ed8',
      'circle-radius': [
        'step',
        ['get', 'point_count'],
        16,
        10, 20,
        40, 24
      ],
      'circle-stroke-color': '#ffffff',
      'circle-stroke-width': 2
    }
  })

  ensureLayer(props.map, {
    id: state.clusterCountLayerId,
    type: 'symbol',
    source: state.sourceId,
    filter: ['has', 'point_count'],
    layout: {
      'text-field': ['get', 'point_count_abbreviated'],
      'text-size': 12,
      'text-font': ['Open Sans Bold', 'Arial Unicode MS Bold']
    },
    paint: {
      'text-color': '#ffffff'
    }
  })

  ensureLayer(props.map, {
    id: state.unclusteredLayerId,
    type: 'circle',
    source: state.sourceId,
    filter: ['!', ['has', 'point_count']],
    paint: {
      'circle-color': [
        'case',
        ['>', ['to-number', ['get', 'itemCount']], 1], '#0f766e',
        ['==', ['get', 'itemType'], 'trip'], '#10b981',
        ['==', ['get', 'itemType'], 'dataGap'], '#f59e0b',
        '#1a56db'
      ],
      'circle-radius': [
        'case',
        ['>', ['to-number', ['get', 'itemCount']], 1], 15,
        ['==', ['get', 'itemType'], 'trip'], 13,
        ['==', ['get', 'itemType'], 'dataGap'], 11,
        12
      ],
      'circle-stroke-color': [
        'case',
        ['>', ['to-number', ['get', 'itemCount']], 1], '#134e4a',
        '#ffffff'
      ],
      'circle-stroke-width': [
        'case',
        ['>', ['to-number', ['get', 'itemCount']], 1], 2,
        3
      ],
      'circle-opacity': 0.95
    }
  })

  ensureLayer(props.map, {
    id: state.unclusteredIconLayerId,
    type: 'symbol',
    source: state.sourceId,
    filter: ['all', ['!', ['has', 'point_count']], ['<=', ['to-number', ['get', 'itemCount']], 1]],
    layout: {
      'text-field': ['get', 'markerGlyph'],
      'text-size': 13,
      'text-font': ['Open Sans Bold', 'Arial Unicode MS Bold'],
      'text-allow-overlap': true,
      'text-ignore-placement': true
    },
    paint: {
      'text-color': '#ffffff'
    }
  })

  ensureLayer(props.map, {
    id: state.unclusteredCountLayerId,
    type: 'symbol',
    source: state.sourceId,
    filter: ['all', ['!', ['has', 'point_count']], ['>', ['to-number', ['get', 'itemCount']], 1]],
    layout: {
      'text-field': ['to-string', ['get', 'itemCount']],
      'text-size': 11,
      'text-font': ['Open Sans Bold', 'Arial Unicode MS Bold']
    },
    paint: {
      'text-color': '#ffffff'
    }
  })

  ensureLayer(props.map, {
    id: state.highlightedLayerId,
    type: 'circle',
    source: state.sourceId,
    filter: createHighlightedFilter(highlightedTimelineKey),
    paint: {
      'circle-color': '#22c55e',
      'circle-radius': 13,
      'circle-stroke-color': '#ffffff',
      'circle-stroke-width': 3,
      'circle-opacity': 0.95
    }
  })

  if (props.map.getLayer(state.highlightedLayerId)) {
    props.map.setFilter(
      state.highlightedLayerId,
      createHighlightedFilter(highlightedTimelineKey)
    )
  }

  setLayerVisibility(
    props.map,
    [
      state.clusterLayerId,
      state.clusterCountLayerId,
      state.unclusteredLayerId,
      state.unclusteredIconLayerId,
      state.unclusteredCountLayerId,
      state.highlightedLayerId
    ],
    props.visible
  )

  bringTimelineLayersToFront()
  syncHighlightedStayFocus()

  unregisterEvents()
  registerEvents()
}

const clearLayer = () => {
  unregisterEvents()
  state.lastHighlightedStayKey = ''
  closeHighlightedStayPopup()
  closeStackPopup()

  if (state.boundMap && state.styleLoadHandler) {
    state.boundMap.off('style.load', state.styleLoadHandler)
    state.styleLoadHandler = null
  }

  const targetMap = state.boundMap || props.map
  if (!isMapLibreMap(targetMap)) {
    state.boundMap = null
    return
  }

  removeLayers(targetMap, [
    state.highlightedLayerId,
    state.unclusteredIconLayerId,
    state.unclusteredCountLayerId,
    state.unclusteredLayerId,
    state.clusterCountLayerId,
    state.clusterLayerId
  ])
  removeSources(targetMap, [state.sourceId])

  state.boundMap = null
  state.sourceClustered = null
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
</style>
