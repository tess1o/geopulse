<template></template>

<script setup>
import { onBeforeUnmount, watch } from 'vue'
import maplibregl from 'maplibre-gl'
import { useTimezone } from '@/composables/useTimezone'
import { formatDistance, formatDuration } from '@/utils/calculationsHelpers'
import '@/maps/shared/styles/mapPopupContent.css'
import {
  createFeatureCollection,
  ensureGeoJsonSource,
  ensureLayer,
  isMapLibreMap,
  nextLayerToken,
  removeLayers,
  removeSources,
  setLayerVisibility,
  toFiniteNumber
} from '@/maps/vector/utils/maplibreLayerUtils'
import {
  normalizePathPoints,
  reconstructTripPathPoints,
  resolveTripMarkerPoint,
  areSameCoordinate
} from '@/utils/tripPathReconstruction'
import {
  buildTripHoverTooltipHtml,
  buildTripPopupHtml
} from '@/maps/shared/popupContentBuilders'
import { createTripEndpointMarkerElement } from '@/maps/shared/tripEndpointMarkerBuilder'
import {
  buildTripHoverContext as buildSharedTripHoverContext,
  projectTripHoverContext,
  resolveTripHoverTiming as resolveSharedTripHoverTiming
} from '@/maps/shared/tripHoverMath'

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

const emit = defineEmits(['path-click', 'path-hover', 'trip-marker-click', 'highlighted-trip-click'])

const state = {
  token: nextLayerToken('gp-path'),
  sourceId: '',
  lineLayerId: '',
  highlightedSourceId: '',
  highlightedLineLayerId: '',
  highlightedStartEndpointMarker: null,
  highlightedEndEndpointMarker: null,
  listeners: [],
  styleLoadHandler: null,
  boundMap: null,
  highlightedTripPopup: null,
  highlightedTripPopupKey: '',
  highlightedTripPopupTimeoutId: null,
  highlightedHoverPopup: null,
  highlightedHoverContext: null,
  highlightedHoverMapHandler: null
}

state.sourceId = `${state.token}-source`
state.lineLayerId = `${state.token}-line`
state.highlightedSourceId = `${state.token}-highlighted-source`
state.highlightedLineLayerId = `${state.token}-highlighted-line`

const formatDateTimeDisplay = (dateValue) =>
  `${timezone.formatDateDisplay(dateValue)} ${timezone.formatTime(dateValue, { withSeconds: true })}`

const buildTripPopupContent = (trip) => buildTripPopupHtml(trip, { formatDateTimeDisplay })

const buildTripHoverContext = (tripPathPoints) => buildSharedTripHoverContext(tripPathPoints)

const refreshTripHoverContextProjection = (context, map) => {
  if (!context || !isMapLibreMap(map)) {
    return
  }

  projectTripHoverContext(context, {
    project: (point) => map.project([point.longitude, point.latitude])
  })
}

const resolveTripHoverTiming = (context, lngLat, map) => {
  if (!context || !lngLat || !isMapLibreMap(map) || !context.projectedPoints.length) {
    return null
  }

  const resolved = resolveSharedTripHoverTiming(context, lngLat, {
    toProjectedPoint: (cursorLngLat) => map.project([cursorLngLat.lng, cursorLngLat.lat]),
    unproject: ({ x, y }) => {
      const lngLatValue = map.unproject([x, y])
      return {
        latitude: lngLatValue.lat,
        longitude: lngLatValue.lng
      }
    }
  })

  if (!resolved) {
    return null
  }

  return {
    ...resolved,
    snappedLngLat: {
      lng: resolved.snappedPoint.longitude,
      lat: resolved.snappedPoint.latitude
    }
  }
}

const buildTripHoverTooltipContent = (trip, hoverTiming) => (
  buildTripHoverTooltipHtml(trip, hoverTiming, { formatDateTimeDisplay })
)

const showTripHoverTooltip = (trip, hoverTiming) => {
  if (!isMapLibreMap(props.map) || !hoverTiming) {
    return
  }

  if (!state.highlightedHoverPopup) {
    state.highlightedHoverPopup = new maplibregl.Popup({
      closeButton: false,
      closeOnClick: false,
      closeOnMove: false,
      offset: 14,
      className: 'trip-hover-tooltip-container'
    })
  }

  state.highlightedHoverPopup
    .setLngLat([hoverTiming.snappedLngLat.lng, hoverTiming.snappedLngLat.lat])
    .setHTML(buildTripHoverTooltipContent(trip, hoverTiming))
    .addTo(props.map)
}

const hideTripHoverTooltip = () => {
  if (state.highlightedHoverPopup) {
    state.highlightedHoverPopup.remove()
    state.highlightedHoverPopup = null
  }
}

const clearTripHoverState = () => {
  hideTripHoverTooltip()

  if (state.highlightedHoverMapHandler && isMapLibreMap(props.map)) {
    props.map.off('zoom', state.highlightedHoverMapHandler)
    props.map.off('move', state.highlightedHoverMapHandler)
  }

  state.highlightedHoverMapHandler = null
  state.highlightedHoverContext = null
}

const syncTripHoverContext = (tripPathPoints) => {
  clearTripHoverState()

  if (!isMapLibreMap(props.map) || !props.highlightedTrip || props.highlightedTrip.type !== 'trip') {
    return
  }

  state.highlightedHoverContext = buildTripHoverContext(tripPathPoints)
  if (!state.highlightedHoverContext) {
    return
  }

  refreshTripHoverContextProjection(state.highlightedHoverContext, props.map)
  state.highlightedHoverMapHandler = () => {
    if (state.highlightedHoverContext && isMapLibreMap(props.map)) {
      refreshTripHoverContextProjection(state.highlightedHoverContext, props.map)
    }
  }
  props.map.on('zoom', state.highlightedHoverMapHandler)
  props.map.on('move', state.highlightedHoverMapHandler)
}

const getHighlightedTripKey = (trip) => {
  if (!trip) {
    return ''
  }

  if (trip.id) {
    return String(trip.id)
  }

  return [
    trip.timestamp,
    trip.latitude,
    trip.longitude,
    trip.endLatitude,
    trip.endLongitude,
    trip.tripDuration,
    trip.distanceMeters
  ].join('|')
}

const closeHighlightedTripPopup = () => {
  if (state.highlightedTripPopupTimeoutId !== null) {
    clearTimeout(state.highlightedTripPopupTimeoutId)
    state.highlightedTripPopupTimeoutId = null
  }

  if (state.highlightedTripPopup) {
    state.highlightedTripPopup.remove()
    state.highlightedTripPopup = null
  }

  state.highlightedTripPopupKey = ''
}

const removeHighlightedEndpointMarkers = () => {
  const markerKeys = ['highlightedStartEndpointMarker', 'highlightedEndEndpointMarker']

  markerKeys.forEach((markerKey) => {
    const markerEntry = state[markerKey]
    if (!markerEntry) {
      return
    }

    markerEntry.cleanup?.()
    state[markerKey] = null
  })
}

const buildTripEndpointPopupContent = (trip, markerType) => {
  const startTime = timezone.fromUtc(trip.timestamp)
  const endTime = startTime.add(trip.tripDuration, 'second')

  if (markerType === 'start') {
    return `
      <div class="trip-popup">
        <div class="trip-title trip-start">
          🚀 Trip Start
        </div>
        <div class="trip-detail">
          Start: ${formatDateTimeDisplay(startTime.toISOString())}
        </div>
        <div class="trip-detail">
          Duration: ${formatDuration(trip.tripDuration)}
        </div>
        <div class="trip-detail">
          Distance: ${formatDistance(trip.distanceMeters || 0)}
        </div>
        <div class="trip-detail">
          Mode: ${trip.movementType || 'Unknown'}
        </div>
      </div>
    `
  }

  return `
    <div class="trip-popup">
      <div class="trip-title trip-end">
        🏁 Trip End
      </div>
      <div class="trip-detail">
        End: ${formatDateTimeDisplay(endTime.toISOString())}
      </div>
      <div class="trip-detail">
        Duration: ${formatDuration(trip.tripDuration)}
      </div>
      <div class="trip-detail">
        Distance: ${formatDistance(trip.distanceMeters || 0)}
      </div>
      <div class="trip-detail">
        Mode: ${trip.movementType || 'Unknown'}
      </div>
    </div>
  `
}

const createHighlightedEndpointMarker = ({
  markerType,
  latitude,
  longitude,
  styleOverrides = {},
  zIndex = null
}) => {
  if (!isMapLibreMap(props.map) || !props.highlightedTrip) {
    return null
  }

  const markerElement = createTripEndpointMarkerElement({
    markerType,
    instant: true,
    styleOverrides
  })
  const resolvedZIndex = Number.isFinite(Number(zIndex))
    ? Number(zIndex)
    : (markerType === 'start' ? 420 : 410)
  markerElement.style.zIndex = String(resolvedZIndex)

  const marker = new maplibregl.Marker({
    element: markerElement,
    anchor: 'center',
    // Raster marker anchors are effectively shifted by border width (3px).
    // Keep the same visual pin-point parity in vector mode.
    offset: [-3, -3]
  })
    .setLngLat([longitude, latitude])
    .addTo(props.map)

  const popup = new maplibregl.Popup({
    closeButton: false,
    closeOnClick: false,
    closeOnMove: false,
    className: 'gp-trip-popup-container',
    offset: 14
  }).setHTML(buildTripEndpointPopupContent(props.highlightedTrip, markerType))

  const openPopup = () => {
    popup.setLngLat([longitude, latitude]).addTo(props.map)
  }
  const closePopup = () => {
    popup.remove()
  }
  const handleClick = (domEvent) => {
    domEvent.preventDefault()
    domEvent.stopPropagation()

    emit('trip-marker-click', {
      tripData: props.highlightedTrip,
      markerType,
      event: {
        target: marker,
        originalEvent: domEvent,
        lngLat: { lng: longitude, lat: latitude }
      }
    })
  }

  markerElement.addEventListener('mouseenter', openPopup)
  markerElement.addEventListener('mouseleave', closePopup)
  markerElement.addEventListener('click', handleClick)

  return {
    marker,
    popup,
    cleanup: () => {
      markerElement.removeEventListener('mouseenter', openPopup)
      markerElement.removeEventListener('mouseleave', closePopup)
      markerElement.removeEventListener('click', handleClick)
      popup.remove()
      marker.remove()
    }
  }
}

const syncHighlightedEndpointMarkers = (endpointMarkers) => {
  removeHighlightedEndpointMarkers()

  if (!isMapLibreMap(props.map) || !props.visible || !props.highlightedTrip) {
    return
  }

  const markers = Array.isArray(endpointMarkers) ? endpointMarkers : []
  markers.forEach((endpointMarker) => {
    const markerEntry = createHighlightedEndpointMarker(endpointMarker)
    if (!markerEntry) {
      return
    }

    if (endpointMarker.markerType === 'start') {
      state.highlightedStartEndpointMarker = markerEntry
    } else if (endpointMarker.markerType === 'end') {
      state.highlightedEndEndpointMarker = markerEntry
    }
  })
}

const resolveHighlightedLineCoordinates = (lineCollection) => {
  const coordinates = lineCollection?.features?.[0]?.geometry?.coordinates
  if (!Array.isArray(coordinates)) {
    return []
  }

  return coordinates.filter((coord) => (
    Array.isArray(coord)
    && Number.isFinite(toFiniteNumber(coord[0]))
    && Number.isFinite(toFiniteNumber(coord[1]))
  ))
}

const resolvePopupAnchorCoordinate = (lineCoordinates) => {
  if (!Array.isArray(lineCoordinates) || lineCoordinates.length === 0) {
    return null
  }

  return lineCoordinates[Math.floor(lineCoordinates.length / 2)] || lineCoordinates[0]
}

const fitHighlightedTripBounds = (lineCoordinates) => {
  if (!isMapLibreMap(props.map) || lineCoordinates.length < 2) {
    return
  }

  const bounds = new maplibregl.LngLatBounds(lineCoordinates[0], lineCoordinates[0])
  lineCoordinates.forEach((coord) => {
    bounds.extend(coord)
  })

  props.map.fitBounds(bounds, {
    padding: 20,
    animate: false
  })
}

const syncHighlightedTripPopup = (lineCoordinates) => {
  if (!isMapLibreMap(props.map) || !props.highlightedTrip || !props.visible || lineCoordinates.length < 2) {
    closeHighlightedTripPopup()
    return
  }

  const popupCoordinate = resolvePopupAnchorCoordinate(lineCoordinates)
  if (!popupCoordinate) {
    closeHighlightedTripPopup()
    return
  }

  const tripKey = getHighlightedTripKey(props.highlightedTrip)
  if (!tripKey) {
    closeHighlightedTripPopup()
    return
  }

  if (state.highlightedTripPopup && state.highlightedTripPopupKey === tripKey) {
    state.highlightedTripPopup
      .setLngLat(popupCoordinate)
      .setHTML(buildTripPopupContent(props.highlightedTrip))
    return
  }

  closeHighlightedTripPopup()
  state.highlightedTripPopupKey = tripKey
  fitHighlightedTripBounds(lineCoordinates)

  state.highlightedTripPopupTimeoutId = setTimeout(() => {
    state.highlightedTripPopupTimeoutId = null

    if (!isMapLibreMap(props.map) || state.highlightedTripPopupKey !== tripKey) {
      return
    }

    state.highlightedTripPopup = new maplibregl.Popup({
      closeButton: false,
      closeOnClick: false,
      closeOnMove: false,
      className: 'gp-trip-popup-container'
    })
      .setLngLat(popupCoordinate)
      .setHTML(buildTripPopupContent(props.highlightedTrip))
      .addTo(props.map)
  }, 120)
}

const normalizePathCoordinates = (pathGroup) => {
  if (!Array.isArray(pathGroup)) {
    return []
  }

  return pathGroup
    .map((point) => {
      const latitude = toFiniteNumber(point?.latitude)
      const longitude = toFiniteNumber(point?.longitude)

      if (latitude === null || longitude === null) {
        return null
      }

      return [longitude, latitude]
    })
    .filter(Boolean)
}

const buildPathCollection = () => {
  const features = []

  props.pathData.forEach((pathGroup, pathIndex) => {
    const coordinates = normalizePathCoordinates(pathGroup)
    if (coordinates.length < 2) {
      return
    }

    features.push({
      type: 'Feature',
      geometry: {
        type: 'LineString',
        coordinates
      },
      properties: {
        pathIndex,
        pathRaw: JSON.stringify(pathGroup || [])
      }
    })
  })

  return createFeatureCollection(features)
}

const buildHighlightedData = () => {
  if (!props.highlightedTrip) {
    return {
      lineCollection: createFeatureCollection([]),
      endpointMarkers: [],
      hoverPathPoints: []
    }
  }

  const normalizedPath = normalizePathPoints(props.pathData)
  const { points: reconstructedPoints } = reconstructTripPathPoints(props.highlightedTrip, normalizedPath)

  let tripPoints = reconstructedPoints
  if (!tripPoints || tripPoints.length < 2) {
    const startLat = toFiniteNumber(props.highlightedTrip?.latitude)
    const startLon = toFiniteNumber(props.highlightedTrip?.longitude)
    const endLat = toFiniteNumber(props.highlightedTrip?.endLatitude)
    const endLon = toFiniteNumber(props.highlightedTrip?.endLongitude)

    if ([startLat, startLon, endLat, endLon].every((value) => value !== null)) {
      tripPoints = [
        { latitude: startLat, longitude: startLon },
        { latitude: endLat, longitude: endLon }
      ]
    }
  }

  if (!tripPoints || tripPoints.length < 2) {
    return {
      lineCollection: createFeatureCollection([]),
      endpointMarkers: [],
      hoverPathPoints: []
    }
  }

  const hoverPathPoints = tripPoints.map((point) => ({
    latitude: point.latitude,
    longitude: point.longitude,
    timestamp: point.timestamp || null
  }))

  const lineCoordinates = tripPoints.map((point) => [point.longitude, point.latitude])

  const startPoint = resolveTripMarkerPoint(props.highlightedTrip, 'start', {
    latitude: tripPoints[0].latitude,
    longitude: tripPoints[0].longitude
  })

  const endPoint = resolveTripMarkerPoint(props.highlightedTrip, 'end', {
    latitude: tripPoints[tripPoints.length - 1].latitude,
    longitude: tripPoints[tripPoints.length - 1].longitude
  })

  if (startPoint && !areSameCoordinate(tripPoints[0], startPoint)) {
    lineCoordinates[0] = [startPoint.longitude, startPoint.latitude]
    hoverPathPoints[0] = {
      ...hoverPathPoints[0],
      latitude: startPoint.latitude,
      longitude: startPoint.longitude
    }
  }

  if (endPoint && !areSameCoordinate(tripPoints[tripPoints.length - 1], endPoint)) {
    lineCoordinates[lineCoordinates.length - 1] = [endPoint.longitude, endPoint.latitude]
    hoverPathPoints[hoverPathPoints.length - 1] = {
      ...hoverPathPoints[hoverPathPoints.length - 1],
      latitude: endPoint.latitude,
      longitude: endPoint.longitude
    }
  }

  const sameEndpoint = Boolean(startPoint && endPoint && areSameCoordinate(startPoint, endPoint))
  const endpointMarkers = []

  if (startPoint) {
    endpointMarkers.push({
      markerType: 'start',
      latitude: startPoint.latitude,
      longitude: startPoint.longitude,
      zIndex: sameEndpoint ? 421 : 420,
      styleOverrides: sameEndpoint ? { transform: 'translateX(-14px)' } : {}
    })
  }

  if (endPoint) {
    endpointMarkers.push({
      markerType: 'end',
      latitude: endPoint.latitude,
      longitude: endPoint.longitude,
      zIndex: sameEndpoint ? 420 : 410,
      styleOverrides: sameEndpoint ? { transform: 'translateX(14px)' } : {}
    })
  }

  const lineCollection = createFeatureCollection([
    {
      type: 'Feature',
      geometry: {
        type: 'LineString',
        coordinates: lineCoordinates
      },
      properties: {
        tripRaw: JSON.stringify(props.highlightedTrip || {})
      }
    }
  ])

  return {
    lineCollection,
    endpointMarkers,
    hoverPathPoints
  }
}

const registerEvents = () => {
  if (!isMapLibreMap(props.map)) {
    return
  }

  const handlePathClick = (event) => {
    const feature = event?.features?.[0]
    const index = Number.parseInt(feature?.properties?.pathIndex, 10)

    let pathData = []
    try {
      pathData = JSON.parse(feature?.properties?.pathRaw || '[]')
    } catch {
      pathData = []
    }

    emit('path-click', {
      pathData,
      pathIndex: Number.isFinite(index) ? index : -1,
      event
    })
  }

  const handlePathHover = (event) => {
    props.map.getCanvas().style.cursor = 'pointer'
    const feature = event?.features?.[0]
    const index = Number.parseInt(feature?.properties?.pathIndex, 10)

    emit('path-hover', {
      pathIndex: Number.isFinite(index) ? index : -1,
      event
    })
  }

  const handlePathLeave = () => {
    props.map.getCanvas().style.cursor = ''
  }

  const handleHighlightedLineClick = () => {
    emit('highlighted-trip-click', {
      tripData: props.highlightedTrip
    })
  }

  const handleHighlightedLineMouseMove = (event) => {
    props.map.getCanvas().style.cursor = 'pointer'
    closeHighlightedTripPopup()

    if (!state.highlightedHoverContext || !props.highlightedTrip) {
      hideTripHoverTooltip()
      return
    }

    const hoverTiming = resolveTripHoverTiming(state.highlightedHoverContext, event?.lngLat, props.map)
    if (!hoverTiming) {
      hideTripHoverTooltip()
      return
    }

    showTripHoverTooltip(props.highlightedTrip, hoverTiming)
  }

  const handleHighlightedLineMouseLeave = () => {
    props.map.getCanvas().style.cursor = ''
    hideTripHoverTooltip()
  }

  props.map.on('click', state.lineLayerId, handlePathClick)
  props.map.on('mousemove', state.lineLayerId, handlePathHover)
  props.map.on('mouseleave', state.lineLayerId, handlePathLeave)

  props.map.on('click', state.highlightedLineLayerId, handleHighlightedLineClick)
  props.map.on('mousemove', state.highlightedLineLayerId, handleHighlightedLineMouseMove)
  props.map.on('mouseleave', state.highlightedLineLayerId, handleHighlightedLineMouseLeave)

  state.listeners = [
    { event: 'click', layerId: state.lineLayerId, handler: handlePathClick },
    { event: 'mousemove', layerId: state.lineLayerId, handler: handlePathHover },
    { event: 'mouseleave', layerId: state.lineLayerId, handler: handlePathLeave },
    { event: 'click', layerId: state.highlightedLineLayerId, handler: handleHighlightedLineClick },
    { event: 'mousemove', layerId: state.highlightedLineLayerId, handler: handleHighlightedLineMouseMove },
    { event: 'mouseleave', layerId: state.highlightedLineLayerId, handler: handleHighlightedLineMouseLeave }
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

const renderLayer = () => {
  if (!isMapLibreMap(props.map)) {
    return
  }

  const pathCollection = buildPathCollection()
  const highlighted = buildHighlightedData()
  const highlightedLineCoordinates = resolveHighlightedLineCoordinates(highlighted.lineCollection)

  ensureGeoJsonSource(props.map, state.sourceId, pathCollection)
  ensureGeoJsonSource(props.map, state.highlightedSourceId, highlighted.lineCollection)

  ensureLayer(props.map, {
    id: state.lineLayerId,
    type: 'line',
    source: state.sourceId,
    paint: {
      'line-color': props.pathOptions.color || '#007bff',
      'line-width': props.pathOptions.weight || 4,
      'line-opacity': props.pathOptions.opacity ?? 0.8
    }
  })

  ensureLayer(props.map, {
    id: state.highlightedLineLayerId,
    type: 'line',
    source: state.highlightedSourceId,
    layout: {
      'line-join': 'round',
      'line-cap': 'round'
    },
    paint: {
      'line-color': '#ff5f66',
      'line-width': 6,
      'line-opacity': 1,
      'line-dasharray': [0.6, 1.6]
    }
  })

  setLayerVisibility(
    props.map,
    [
      state.lineLayerId,
      state.highlightedLineLayerId
    ],
    props.visible
  )

  syncHighlightedTripPopup(highlightedLineCoordinates)
  syncHighlightedEndpointMarkers(highlighted.endpointMarkers)
  syncTripHoverContext(highlighted.hoverPathPoints)

  unregisterEvents()
  registerEvents()
}

const clearLayer = () => {
  unregisterEvents()
  clearTripHoverState()
  closeHighlightedTripPopup()
  removeHighlightedEndpointMarkers()

  if (state.boundMap && state.styleLoadHandler) {
    state.boundMap.off('style.load', state.styleLoadHandler)
    state.styleLoadHandler = null
  }

  const targetMap = state.boundMap || props.map
  if (!isMapLibreMap(targetMap)) {
    state.boundMap = null
    return
  }

  removeLayers(
    targetMap,
    [state.highlightedLineLayerId, state.lineLayerId]
  )
  removeSources(targetMap, [state.highlightedSourceId, state.sourceId])
  state.boundMap = null
}

watch(
  () => [props.map, props.pathData, props.highlightedTrip, props.pathOptions, props.visible],
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
.gp-trip-popup-container .maplibregl-popup-content {
  padding: 0.65rem 0.75rem;
  background: rgba(255, 255, 255, 0.97);
  border: 1px solid rgba(148, 163, 184, 0.65);
  color: #0f172a;
}

.gp-trip-popup-container .maplibregl-popup-tip {
  border-top-color: rgba(255, 255, 255, 0.97);
}

.p-dark .gp-trip-popup-container .maplibregl-popup-content {
  background: linear-gradient(135deg, rgba(15, 23, 42, 0.97), rgba(30, 41, 59, 0.94));
  border: 1px solid rgba(71, 85, 105, 0.55);
  color: rgba(255, 255, 255, 0.95);
  box-shadow: 0 10px 30px rgba(2, 6, 23, 0.45);
}

.p-dark .gp-trip-popup-container .maplibregl-popup-tip {
  border-top-color: rgba(15, 23, 42, 0.95);
}
</style>
