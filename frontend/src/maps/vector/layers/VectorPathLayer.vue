<template></template>

<script setup>
import { onBeforeUnmount, watch } from 'vue'
import maplibregl from 'maplibre-gl'
import { useTimezone } from '@/composables/useTimezone'
import '@/maps/shared/styles/mapPopupContent.css'
import {
  ensureGeoJsonSource,
  ensureLayer,
  isMapLibreMap,
  nextLayerToken,
  removeLayers,
  removeSources,
  setLayerVisibility
} from '@/maps/vector/utils/maplibreLayerUtils'
import {
  buildTripEndpointPopupHtml,
  buildTripPopupHtml
} from '@/maps/shared/popupContentBuilders'
import { createTripEndpointMarkerElement } from '@/maps/shared/tripEndpointMarkerBuilder'
import {
  buildHighlightedData,
  buildPathCollection,
  highlightedLineColorExpression,
  resolvePopupAnchorCoordinate
} from '@/maps/vector/layers/vectorPathLayer/dataBuilders'
import { createVectorPathHoverController } from '@/maps/vector/layers/vectorPathLayer/hoverController'
import { createVectorPathReplayController } from '@/maps/vector/layers/vectorPathLayer/replayController'
import {
  HIGHLIGHTED_TRIP_BACKGROUND_OPACITY,
  HIGHLIGHTED_TRIP_VECTOR_HIT_WEIGHT,
  buildReplayEmission,
  getHighlightedTripKey
} from '@/maps/shared/highlightedTripData'

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
  },
  replayState: {
    type: Object,
    default: null
  },
  focusHighlightedTrip: {
    type: Boolean,
    default: true
  },
  inspectionEnabled: {
    type: Boolean,
    default: true
  },
  allowPathDataTripFallback: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits([
  'path-click',
  'path-hover',
  'trip-marker-click',
  'highlighted-trip-click',
  'highlighted-trip-replay-data'
])

const state = {
  token: nextLayerToken('gp-path'),
  sourceId: '',
  lineLayerId: '',
  highlightedSourceId: '',
  highlightedLineLayerId: '',
  highlightedHitLayerId: '',
  highlightedStartEndpointMarker: null,
  highlightedEndEndpointMarker: null,
  listeners: [],
  styleLoadHandler: null,
  boundMap: null,
  highlightedTripPopup: null,
  highlightedTripPopupKey: '',
  highlightedTripPopupTimeoutId: null,
  highlightedTripPopupAutoHideTimeoutId: null,
  lastReplayEmissionKey: '',
  highlightedTouchInspecting: false,
  highlightedTouchMoveHandler: null,
  highlightedTouchEndHandler: null
}

state.sourceId = `${state.token}-source`
state.lineLayerId = `${state.token}-line`
state.highlightedSourceId = `${state.token}-highlighted-source`
state.highlightedLineLayerId = `${state.token}-highlighted-line`
state.highlightedHitLayerId = `${state.token}-highlighted-hit`

const formatDateTimeDisplay = (dateValue) => (
  `${timezone.formatDateDisplay(dateValue)} ${timezone.formatTime(dateValue, { withSeconds: true })}`
)

const hoverController = createVectorPathHoverController({
  getMap: () => props.map,
  formatDateTimeDisplay
})

const replayController = createVectorPathReplayController({
  getMap: () => props.map
})

const buildTripPopupContent = (trip) => buildTripPopupHtml(trip, { formatDateTimeDisplay })

const HIGHLIGHTED_TRIP_POPUP_AUTO_HIDE_DESKTOP_MS = 10000
const HIGHLIGHTED_TRIP_POPUP_AUTO_HIDE_MOBILE_MS = 5000
const HIGHLIGHTED_TRIP_NON_CAR_COLOR = '#ef4444'
const HIGHLIGHTED_TRIP_NON_CAR_DASH = [0.6, 1.8]
const HIGHLIGHTED_TRIP_CAR_SOLID_DASH = [1, 0]

const isCarMovementType = (movementType) => String(movementType || '').trim().toUpperCase() === 'CAR'
const isTripItem = (item) => item?.type === 'trip'
const hasFocusedHighlightedTrip = () => (
  props.focusHighlightedTrip
  && isTripItem(props.highlightedTrip)
)

const resolveHighlightedTripPopupAutoHideMs = () => {
  if (typeof window === 'undefined' || typeof window.matchMedia !== 'function') {
    return HIGHLIGHTED_TRIP_POPUP_AUTO_HIDE_DESKTOP_MS
  }

  const isMobileViewport = window.matchMedia('(max-width: 768px)').matches
  const isTouchPrimary = window.matchMedia('(pointer: coarse)').matches

  return (isMobileViewport || isTouchPrimary)
    ? HIGHLIGHTED_TRIP_POPUP_AUTO_HIDE_MOBILE_MS
    : HIGHLIGHTED_TRIP_POPUP_AUTO_HIDE_DESKTOP_MS
}

const clearHighlightedTripPopupAutoHideTimeout = () => {
  if (state.highlightedTripPopupAutoHideTimeoutId !== null) {
    clearTimeout(state.highlightedTripPopupAutoHideTimeoutId)
    state.highlightedTripPopupAutoHideTimeoutId = null
  }
}

const scheduleHighlightedTripPopupAutoHide = (tripKey) => {
  clearHighlightedTripPopupAutoHideTimeout()

  state.highlightedTripPopupAutoHideTimeoutId = setTimeout(() => {
    state.highlightedTripPopupAutoHideTimeoutId = null

    if (state.highlightedTripPopupKey !== tripKey) {
      return
    }

    closeHighlightedTripPopup()
  }, resolveHighlightedTripPopupAutoHideMs())
}

const closeHighlightedTripPopup = () => {
  if (state.highlightedTripPopupTimeoutId !== null) {
    clearTimeout(state.highlightedTripPopupTimeoutId)
    state.highlightedTripPopupTimeoutId = null
  }
  clearHighlightedTripPopupAutoHideTimeout()

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

const buildTripEndpointPopupContent = (trip, markerType) => (
  buildTripEndpointPopupHtml(trip, markerType, { formatDateTimeDisplay })
)

const createHighlightedEndpointMarker = ({
  markerType,
  latitude,
  longitude,
  styleOverrides = {},
  zIndex = null
}) => {
  if (!isMapLibreMap(props.map) || !isTripItem(props.highlightedTrip)) {
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
    if (props.replayState?.playing) {
      return
    }
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

  if (!isMapLibreMap(props.map) || !props.visible || !isTripItem(props.highlightedTrip)) {
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
    maxZoom: 16,
    animate: false
  })
}

const syncHighlightedTripPopup = (lineCoordinates) => {
  if (props.replayState?.suppressTripPopup) {
    closeHighlightedTripPopup()
    return
  }

  if (!isMapLibreMap(props.map) || !isTripItem(props.highlightedTrip) || !props.visible || lineCoordinates.length < 2) {
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
    scheduleHighlightedTripPopupAutoHide(tripKey)
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

    scheduleHighlightedTripPopupAutoHide(tripKey)
  }, 120)
}

const emitReplayPathData = (highlightedData) => {
  const replayEmission = buildReplayEmission({
    trip: props.highlightedTrip,
    tripPathPoints: highlightedData?.replayPathPoints,
    previousEmissionKey: state.lastReplayEmissionKey
  })

  if (!replayEmission.changed) {
    return
  }

  state.lastReplayEmissionKey = replayEmission.emissionKey
  emit('highlighted-trip-replay-data', replayEmission.payload)
}

const syncReplayMarker = () => {
  replayController.syncReplayMarker({
    visible: props.visible,
    highlightedTrip: props.highlightedTrip,
    replayState: props.replayState,
    onReplayPlaying: () => {
      closeHighlightedTripPopup()
      hoverController.hideTripHoverTooltip()
    }
  })
}

const clearHighlightedTouchInspection = () => {
  state.highlightedTouchInspecting = false

  if (isMapLibreMap(props.map)) {
    if (state.highlightedTouchMoveHandler) {
      props.map.off('touchmove', state.highlightedTouchMoveHandler)
    }
    if (state.highlightedTouchEndHandler) {
      props.map.off('touchend', state.highlightedTouchEndHandler)
      props.map.off('touchcancel', state.highlightedTouchEndHandler)
    }
    props.map.dragPan?.enable?.()
  }

  state.highlightedTouchMoveHandler = null
  state.highlightedTouchEndHandler = null
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
    if (!isTripItem(props.highlightedTrip)) {
      return
    }

    emit('highlighted-trip-click', {
      tripData: props.highlightedTrip
    })
  }

  const handleHighlightedLineMouseMove = (event) => {
    if (!props.inspectionEnabled) {
      return
    }

    props.map.getCanvas().style.cursor = 'pointer'

    hoverController.handleHighlightedLineMouseMove({
      event,
      trip: isTripItem(props.highlightedTrip) ? props.highlightedTrip : null,
      onBeforeTooltipUpdate: closeHighlightedTripPopup
    })
  }

  const handleHighlightedLineMouseLeave = () => {
    props.map.getCanvas().style.cursor = ''
    hoverController.hideTripHoverTooltip()
  }

  const handleHighlightedLineTouchStart = (event) => {
    if (!props.inspectionEnabled || !isTripItem(props.highlightedTrip)) {
      return
    }

    event?.preventDefault?.()
    event?.originalEvent?.preventDefault?.()
    event?.originalEvent?.stopPropagation?.()
    state.highlightedTouchInspecting = true
    props.map.dragPan?.disable?.()

    hoverController.handleHighlightedLineMouseMove({
      event,
      trip: props.highlightedTrip,
      onBeforeTooltipUpdate: closeHighlightedTripPopup
    })

    if (!state.highlightedTouchMoveHandler) {
      state.highlightedTouchMoveHandler = (moveEvent) => {
        if (!state.highlightedTouchInspecting) {
          return
        }

        moveEvent?.preventDefault?.()
        moveEvent?.originalEvent?.preventDefault?.()

        hoverController.handleHighlightedLineMouseMove({
          event: moveEvent,
          trip: props.highlightedTrip,
          onBeforeTooltipUpdate: closeHighlightedTripPopup
        })
      }
      props.map.on('touchmove', state.highlightedTouchMoveHandler)
    }

    if (!state.highlightedTouchEndHandler) {
      state.highlightedTouchEndHandler = () => {
        clearHighlightedTouchInspection()
        hoverController.hideTripHoverTooltip()
      }
      props.map.on('touchend', state.highlightedTouchEndHandler)
      props.map.on('touchcancel', state.highlightedTouchEndHandler)
    }
  }

  if (!hasFocusedHighlightedTrip()) {
    props.map.on('click', state.lineLayerId, handlePathClick)
    props.map.on('mousemove', state.lineLayerId, handlePathHover)
    props.map.on('mouseleave', state.lineLayerId, handlePathLeave)
  }

  props.map.on('click', state.highlightedHitLayerId, handleHighlightedLineClick)
  props.map.on('mousemove', state.highlightedHitLayerId, handleHighlightedLineMouseMove)
  props.map.on('mouseleave', state.highlightedHitLayerId, handleHighlightedLineMouseLeave)
  props.map.on('touchstart', state.highlightedHitLayerId, handleHighlightedLineTouchStart)

  state.listeners = [
    ...(!hasFocusedHighlightedTrip()
      ? [
          { event: 'click', layerId: state.lineLayerId, handler: handlePathClick },
          { event: 'mousemove', layerId: state.lineLayerId, handler: handlePathHover },
          { event: 'mouseleave', layerId: state.lineLayerId, handler: handlePathLeave }
        ]
      : []),
    { event: 'click', layerId: state.highlightedHitLayerId, handler: handleHighlightedLineClick },
    { event: 'mousemove', layerId: state.highlightedHitLayerId, handler: handleHighlightedLineMouseMove },
    { event: 'mouseleave', layerId: state.highlightedHitLayerId, handler: handleHighlightedLineMouseLeave },
    { event: 'touchstart', layerId: state.highlightedHitLayerId, handler: handleHighlightedLineTouchStart }
  ]
}

const syncReplayFocusLayerVisibility = () => {
  if (!isMapLibreMap(props.map)) {
    return
  }

  const replayFocusMode = Boolean(props.replayState?.playing)
  setLayerVisibility(props.map, [state.lineLayerId], props.visible && !replayFocusMode)
  setLayerVisibility(props.map, [state.highlightedLineLayerId, state.highlightedHitLayerId], props.visible)
}

const unregisterEvents = () => {
  clearHighlightedTouchInspection()

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

  replayController.registerReplayUserCameraHandlers()
  const highlightedTrip = isTripItem(props.highlightedTrip) ? props.highlightedTrip : null
  const highlightedTripUsesSpeedBands = isCarMovementType(highlightedTrip?.movementType)
  const highlightedLineColor = highlightedTripUsesSpeedBands
    ? highlightedLineColorExpression
    : HIGHLIGHTED_TRIP_NON_CAR_COLOR
  const highlightedLineDashArray = highlightedTripUsesSpeedBands
    ? HIGHLIGHTED_TRIP_CAR_SOLID_DASH
    : HIGHLIGHTED_TRIP_NON_CAR_DASH

  const pathCollection = buildPathCollection(props.pathData)
  const highlighted = buildHighlightedData({
    highlightedTrip,
    pathData: props.pathData,
    allowPathDataFallback: props.allowPathDataTripFallback
  })
  const highlightedLineCoordinates = highlighted.fullLineCoordinates || []

  ensureGeoJsonSource(props.map, state.sourceId, pathCollection)
  ensureGeoJsonSource(props.map, state.highlightedSourceId, highlighted.lineCollection)

  ensureLayer(props.map, {
    id: state.lineLayerId,
    type: 'line',
    source: state.sourceId,
    paint: {
      'line-color': props.pathOptions.color || '#007bff',
      'line-width': props.pathOptions.weight || 4,
      'line-opacity': hasFocusedHighlightedTrip()
        ? HIGHLIGHTED_TRIP_BACKGROUND_OPACITY
        : (props.pathOptions.opacity ?? 0.8)
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
      'line-color': highlightedLineColor,
      'line-dasharray': highlightedLineDashArray,
      'line-width': 6,
      'line-opacity': 1
    }
  })

  ensureLayer(props.map, {
    id: state.highlightedHitLayerId,
    type: 'line',
    source: state.highlightedSourceId,
    layout: {
      'line-join': 'round',
      'line-cap': 'round'
    },
    paint: {
      'line-color': '#000000',
      'line-width': HIGHLIGHTED_TRIP_VECTOR_HIT_WEIGHT,
      'line-opacity': 0.01
    }
  })

  if (props.map.getLayer(state.highlightedLineLayerId)) {
    props.map.setPaintProperty(
      state.highlightedLineLayerId,
      'line-color',
      highlightedLineColor
    )
    props.map.setPaintProperty(state.highlightedLineLayerId, 'line-dasharray', highlightedLineDashArray)
  }

  if (props.map.getLayer(state.lineLayerId)) {
    props.map.setPaintProperty(
      state.lineLayerId,
      'line-opacity',
      hasFocusedHighlightedTrip()
        ? HIGHLIGHTED_TRIP_BACKGROUND_OPACITY
        : (props.pathOptions.opacity ?? 0.8)
    )
  }

  syncReplayFocusLayerVisibility()

  syncHighlightedTripPopup(highlightedLineCoordinates)
  syncHighlightedEndpointMarkers(highlighted.endpointMarkers)
  hoverController.syncTripHoverContext(highlightedTrip, highlighted.hoverPathPoints)
  emitReplayPathData(highlighted)
  syncReplayMarker()

  unregisterEvents()
  registerEvents()
}

const clearLayer = () => {
  unregisterEvents()
  hoverController.clearTripHoverState()
  closeHighlightedTripPopup()
  removeHighlightedEndpointMarkers()
  replayController.cleanupReplay()

  if (state.lastReplayEmissionKey !== '') {
    state.lastReplayEmissionKey = ''
    emit('highlighted-trip-replay-data', { tripKey: '', points: [] })
  }

  if (state.boundMap && state.styleLoadHandler) {
    state.boundMap.off('style.load', state.styleLoadHandler)
    state.styleLoadHandler = null
  }

  const targetMap = state.boundMap || props.map
  if (!isMapLibreMap(targetMap)) {
    state.boundMap = null
    return
  }

  removeLayers(targetMap, [state.highlightedHitLayerId, state.highlightedLineLayerId, state.lineLayerId])
  removeSources(targetMap, [state.highlightedSourceId, state.sourceId])
  state.boundMap = null
}

watch(
  () => [
    props.map,
    props.pathData,
    props.highlightedTrip,
    props.pathOptions,
    props.visible,
    props.focusHighlightedTrip,
    props.inspectionEnabled,
    props.allowPathDataTripFallback
  ],
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

watch(
  () => props.replayState,
  () => {
    syncReplayFocusLayerVisibility()
    syncReplayMarker()
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
