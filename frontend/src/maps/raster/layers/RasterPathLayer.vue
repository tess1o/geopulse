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
import BaseLayer from '@/components/maps/layers/BaseLayer.vue'
import { createHighlightedPathStartMarker, createHighlightedPathEndMarker } from '@/utils/mapHelpers'
import { useTimezone } from '@/composables/useTimezone'
import '@/maps/shared/styles/mapPopupContent.css'
import {
  buildTripEndpointPopupHtml,
  buildTripHoverTooltipHtml,
  buildTripPopupHtml
} from '@/maps/shared/popupContentBuilders'
import {
  buildTripHoverContext as buildSharedTripHoverContext,
  projectTripHoverContext,
  resolveTripHoverTiming as resolveSharedTripHoverTiming
} from '@/maps/shared/tripHoverMath'
import {
  HIGHLIGHTED_TRIP_BACKGROUND_OPACITY,
  HIGHLIGHTED_TRIP_LINE_WEIGHT,
  HIGHLIGHTED_TRIP_RASTER_HIT_WEIGHT,
  HIGHLIGHTED_TRIP_END_Z_INDEX,
  HIGHLIGHTED_TRIP_START_Z_INDEX,
  buildHighlightedTripData,
  buildReplayEmission
} from '@/maps/shared/highlightedTripData'
import { createRasterPathReplayController } from '@/maps/raster/layers/rasterPathLayer/replayController'

const timezone = useTimezone()
const HIGHLIGHTED_TRIP_POPUP_AUTO_HIDE_DESKTOP_MS = 10000
const HIGHLIGHTED_TRIP_POPUP_AUTO_HIDE_MOBILE_MS = 5000
const HIGHLIGHTED_TRIP_NON_CAR_COLOR = '#ef4444'
const HIGHLIGHTED_TRIP_NON_CAR_DASH = '1 8'

const isCarMovementType = (movementType) => String(movementType || '').trim().toUpperCase() === 'CAR'

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

// State
const baseLayerRef = ref(null)
const pathLayers = ref([])

// Computed
const hasPathData = computed(() => props.pathData && props.pathData.length > 0)
const hasFocusedHighlightedTrip = computed(() => (
  props.focusHighlightedTrip
  && props.highlightedTrip?.type === 'trip'
))

// Layer management
const handleLayerReady = () => {
  renderAll()
}

const renderPaths = () => {
  if (!baseLayerRef.value) return

  // Always clear existing paths first so stale polylines are removed when
  // a new range has no path data.
  clearPaths()

  if (!hasPathData.value) return

  props.pathData.forEach((pathGroup, groupIndex) => {
    const latlngs = pathGroup.map(point => [point.latitude, point.longitude])
    const basePathOptions = hasFocusedHighlightedTrip.value
      ? {
          ...props.pathOptions,
          opacity: HIGHLIGHTED_TRIP_BACKGROUND_OPACITY,
          interactive: false
        }
      : props.pathOptions

    const polyline = L.polyline(latlngs, {
      ...basePathOptions,
      pathId: pathGroup.id || groupIndex
    })

    if (!hasFocusedHighlightedTrip.value) {
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
    }

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

const formatDateTimeDisplay = (dateValue) =>
  `${timezone.formatDateDisplay(dateValue)} ${timezone.formatTime(dateValue, { withSeconds: true })}`

const buildTripPopupContent = (trip) => {
  return buildTripPopupHtml(trip, { formatDateTimeDisplay })
}

const buildTripHoverContext = (tripPath, map) => {
  if (!Array.isArray(tripPath) || tripPath.length < 2 || !map?.distance) {
    return null
  }

  return buildSharedTripHoverContext(tripPath, {
    distance: (left, right) => map.distance(
      L.latLng(left.latitude, left.longitude),
      L.latLng(right.latitude, right.longitude)
    )
  })
}

const refreshTripHoverContextProjection = (context, map) => {
  if (!context || !map?.latLngToLayerPoint) return

  projectTripHoverContext(context, {
    project: (point) => map.latLngToLayerPoint(L.latLng(point.latitude, point.longitude))
  })
}

const resolveTripHoverTiming = (context, latLng, map) => {
  if (!context || !latLng || !map?.latLngToLayerPoint || !context.projectedPoints.length) {
    return null
  }

  const resolved = resolveSharedTripHoverTiming(context, latLng, {
    toProjectedPoint: (cursorLatLng) => map.latLngToLayerPoint(cursorLatLng),
    unproject: ({ x, y }) => {
      const latLngValue = map.layerPointToLatLng(L.point(x, y))
      return {
        latitude: latLngValue.lat,
        longitude: latLngValue.lng
      }
    }
  })

  if (!resolved) {
    return null
  }

  return {
    ...resolved,
    snappedLatLng: L.latLng(resolved.snappedPoint.latitude, resolved.snappedPoint.longitude)
  }
}

const buildTripHoverTooltipContent = (trip, hoverTiming) => {
  return buildTripHoverTooltipHtml(trip, hoverTiming, { formatDateTimeDisplay })
}

const showTripHoverTooltip = (tripLayer, map, trip, hoverTiming) => {
  if (!tripLayer || !map || !hoverTiming) return

  if (!tripLayer.getTooltip()) {
    tripLayer.bindTooltip('', {
      direction: 'top',
      sticky: true,
      opacity: 0.95,
      className: 'trip-hover-tooltip-container'
    })
  }

  tripLayer.setTooltipContent(buildTripHoverTooltipContent(trip, hoverTiming))
  tripLayer.openTooltip(hoverTiming.snappedLatLng)
}

const hideTripHoverTooltip = (tripLayer) => {
  if (!tripLayer) return
  tripLayer.closeTooltip()
}

// Trip-specific state
const tripPathLayer = ref(null)
const tripVisualPathLayers = ref([])
const tripStartMarker = ref(null)
const tripEndMarker = ref(null)
let tripHoverContext = null
let refreshTripHoverContext = null
let tripHoverContextMapHandler = null
let tripPopupTimeoutId = null
let tripPopupAutoHideTimeoutId = null
let tripTouchInspecting = false
let tripTouchMoveHandler = null
let tripTouchEndHandler = null
let lastReplayEmissionKey = ''
const replayController = createRasterPathReplayController({
  getMap: () => props.map,
  getLayerHost: () => baseLayerRef.value,
  getHighlightedTrip: () => props.highlightedTrip,
  isVisible: () => props.visible,
  onReplayPlaying: () => {
    props.map?.closePopup?.()
    hideTripHoverTooltip(tripPathLayer.value)
  }
})

const clearTripPopupTimeout = () => {
  if (tripPopupTimeoutId) {
    clearTimeout(tripPopupTimeoutId)
    tripPopupTimeoutId = null
  }
}

const clearTripPopupAutoHideTimeout = () => {
  if (tripPopupAutoHideTimeoutId) {
    clearTimeout(tripPopupAutoHideTimeoutId)
    tripPopupAutoHideTimeoutId = null
  }
}

const clearTripHoverState = () => {
  if (tripPathLayer.value) {
    hideTripHoverTooltip(tripPathLayer.value)
  }

  if (tripHoverContextMapHandler && props.map) {
    props.map.off('zoomend', tripHoverContextMapHandler)
    props.map.off('moveend', tripHoverContextMapHandler)
  }

  tripHoverContext = null
  refreshTripHoverContext = null
  tripHoverContextMapHandler = null
}

const clearTripTouchInspection = () => {
  tripTouchInspecting = false
  if (tripTouchMoveHandler && props.map) {
    props.map.off('touchmove', tripTouchMoveHandler)
  }
  if (tripTouchEndHandler && props.map) {
    props.map.off('touchend', tripTouchEndHandler)
    props.map.off('touchcancel', tripTouchEndHandler)
  }
  tripTouchMoveHandler = null
  tripTouchEndHandler = null
  props.map?.dragging?.enable?.()
}

const clearHighlightedTripLayers = () => {
  clearTripPopupTimeout()
  clearTripPopupAutoHideTimeout()
  clearTripHoverState()
  clearTripTouchInspection()
  clearReplayMarker()

  // Stop in-progress zoom/pan animations before removing layers.
  // Leaflet can otherwise dispatch zoom animation callbacks to markers
  // that have already been detached, causing "_map is null" errors.
  props.map?.stop?.()
  props.map?.closePopup?.()

  // Leaflet scroll-wheel zoom uses a deferred timer. Rapid highlight switching
  // can remove layers before that timer fires, which then triggers zoomanim on
  // detached overlays/markers.
  const wheelZoom = props.map?.scrollWheelZoom
  if (wheelZoom?._timer) {
    clearTimeout(wheelZoom._timer)
    wheelZoom._timer = null
  }

  if (tripPathLayer.value) {
    baseLayerRef.value?.removeFromLayer(tripPathLayer.value)
    tripPathLayer.value = null
  }
  tripVisualPathLayers.value.forEach((visualLayer) => {
    baseLayerRef.value?.removeFromLayer(visualLayer)
  })
  tripVisualPathLayers.value = []
  if (tripStartMarker.value) {
    baseLayerRef.value?.removeFromLayer(tripStartMarker.value)
    tripStartMarker.value = null
  }
  if (tripEndMarker.value) {
    baseLayerRef.value?.removeFromLayer(tripEndMarker.value)
    tripEndMarker.value = null
  }
}

const emitReplayPathData = (tripPath) => {
  const replayEmission = buildReplayEmission({
    trip: props.highlightedTrip,
    tripPathPoints: tripPath,
    previousEmissionKey: lastReplayEmissionKey
  })

  if (!replayEmission.changed) {
    return
  }

  lastReplayEmissionKey = replayEmission.emissionKey
  emit('highlighted-trip-replay-data', replayEmission.payload)
}

const clearReplayMarker = () => replayController.clearReplayMarker()

const syncReplayMarker = () => {
  replayController.syncReplayMarker({
    replayState: props.replayState
  })
}

const showInspectionTooltipAtLatLng = (latlng, trip) => {
  if (!props.inspectionEnabled || !tripPathLayer.value) {
    return
  }

  clearTripPopupTimeout()
  clearTripPopupAutoHideTimeout()
  tripPathLayer.value?.closePopup?.()

  const hoverTiming = resolveTripHoverTiming(tripHoverContext, latlng, props.map)
  if (!hoverTiming) {
    hideTripHoverTooltip(tripPathLayer.value)
    return
  }

  showTripHoverTooltip(tripPathLayer.value, props.map, trip, hoverTiming)
}

const resolveTouchLatLng = (event) => {
  const originalEvent = event?.originalEvent
  const touch = originalEvent?.touches?.[0] || originalEvent?.changedTouches?.[0]
  if (!touch || !props.map?.mouseEventToLatLng) {
    return event?.latlng || null
  }
  return props.map.mouseEventToLatLng(touch)
}

const renderHighlightedTrip = (newTrip) => {
  clearHighlightedTripLayers()

  if (!baseLayerRef.value?.isReady) {
    return
  }

  if (newTrip && newTrip.type === 'trip') {
    const highlightedData = buildHighlightedTripData({
      highlightedTrip: newTrip,
      pathData: props.pathData,
      allowPathDataFallback: props.allowPathDataTripFallback
    })
    const tripPath = highlightedData.renderedTripPoints
    if (!tripPath || tripPath.length < 2) {
      console.warn('Could not reconstruct trip path - insufficient GPS points')
      emitReplayPathData([])
      return
    }

    const tripCoords = tripPath.map(point => [point.latitude, point.longitude])
    const startEndpoint = highlightedData.endpointMarkers.find((marker) => marker.markerType === 'start')
    const endEndpoint = highlightedData.endpointMarkers.find((marker) => marker.markerType === 'end')

    if (isCarMovementType(newTrip.movementType)) {
      tripVisualPathLayers.value = highlightedData.highlightedSegments.segments.map((segment) => L.polyline(segment.latLngs, {
        color: segment.color,
        weight: HIGHLIGHTED_TRIP_LINE_WEIGHT,
        opacity: 1,
        lineCap: 'round',
        lineJoin: 'round',
        interactive: false
      }))
    } else {
      tripVisualPathLayers.value = [L.polyline(tripCoords, {
        color: HIGHLIGHTED_TRIP_NON_CAR_COLOR,
        dashArray: HIGHLIGHTED_TRIP_NON_CAR_DASH,
        weight: HIGHLIGHTED_TRIP_LINE_WEIGHT,
        opacity: 1,
        lineCap: 'round',
        lineJoin: 'round',
        interactive: false
      })]
    }

    tripPathLayer.value = L.polyline(tripCoords, {
      color: '#000000',
      weight: HIGHLIGHTED_TRIP_RASTER_HIT_WEIGHT,
      opacity: 0,
      lineCap: 'round',
      lineJoin: 'round'
    })

    if (startEndpoint) {
      tripStartMarker.value = createHighlightedPathStartMarker(
        startEndpoint.latitude,
        startEndpoint.longitude,
        true,
        startEndpoint.styleOverrides || {}
      )
    }

    if (endEndpoint) {
      tripEndMarker.value = createHighlightedPathEndMarker(
        endEndpoint.latitude,
        endEndpoint.longitude,
        true,
        endEndpoint.styleOverrides || {}
      )
    }

    // Keep start/end trip markers above timeline/cluster markers.
    tripStartMarker.value?.setZIndexOffset((startEndpoint?.zIndex || HIGHLIGHTED_TRIP_START_Z_INDEX) * 10)
    tripEndMarker.value?.setZIndexOffset((endEndpoint?.zIndex || HIGHLIGHTED_TRIP_END_Z_INDEX) * 10)

    const tripInfo = buildTripPopupContent(newTrip)
    const startInfo = buildTripEndpointPopupHtml(newTrip, 'start', { formatDateTimeDisplay })
    const endInfo = buildTripEndpointPopupHtml(newTrip, 'end', { formatDateTimeDisplay })

    tripStartMarker.value?.on('click', (e) => {
      emit('trip-marker-click', {
        tripData: newTrip,
        markerType: 'start',
        event: e
      })
    })

    tripStartMarker.value?.on('mouseover', function () {
      this.openPopup()
    })
    tripStartMarker.value?.on('mouseout', function () {
      this.closePopup()
    })

    tripEndMarker.value?.on('click', (e) => {
      emit('trip-marker-click', {
        tripData: newTrip,
        markerType: 'end',
        event: e
      })
    })

    tripEndMarker.value?.on('mouseover', function () {
      this.openPopup()
    })
    tripEndMarker.value?.on('mouseout', function () {
      this.closePopup()
    })

    tripHoverContext = buildTripHoverContext(tripPath, props.map)
    refreshTripHoverContext = () => {
      if (tripHoverContext && props.map) {
        refreshTripHoverContextProjection(tripHoverContext, props.map)
      }
    }
    refreshTripHoverContext()
    emitReplayPathData(tripPath)

    tripHoverContextMapHandler = () => {
      refreshTripHoverContext?.()
    }
    props.map?.on?.('zoomend', tripHoverContextMapHandler)
    props.map?.on?.('moveend', tripHoverContextMapHandler)

    tripPathLayer.value.on('mousemove', (e) => {
      // While inspecting hover timing, hide the static highlighted-trip popup.
      showInspectionTooltipAtLatLng(e.latlng, newTrip)
    })

    tripPathLayer.value.on('mouseout', () => {
      hideTripHoverTooltip(tripPathLayer.value)
    })

    tripPathLayer.value.on('click', (e) => {
      e?.originalEvent?.preventDefault?.()
      e?.originalEvent?.stopPropagation?.()
      props.map?.closePopup?.()
      emit('highlighted-trip-click', {
        tripData: newTrip,
        event: e
      })
    })

    tripPathLayer.value.on('touchstart', (e) => {
      if (!props.inspectionEnabled) return
      e?.originalEvent?.preventDefault?.()
      e?.originalEvent?.stopPropagation?.()
      tripTouchInspecting = true
      props.map?.dragging?.disable?.()
      showInspectionTooltipAtLatLng(resolveTouchLatLng(e), newTrip)

      if (!tripTouchMoveHandler) {
        tripTouchMoveHandler = (moveEvent) => {
          if (!tripTouchInspecting) return
          moveEvent?.originalEvent?.preventDefault?.()
          showInspectionTooltipAtLatLng(resolveTouchLatLng(moveEvent), newTrip)
        }
        props.map?.on?.('touchmove', tripTouchMoveHandler)
      }

      if (!tripTouchEndHandler) {
        tripTouchEndHandler = () => {
          clearTripTouchInspection()
        }
        props.map?.on?.('touchend', tripTouchEndHandler)
        props.map?.on?.('touchcancel', tripTouchEndHandler)
      }
    })

    tripPathLayer.value.bindPopup(tripInfo)
    tripStartMarker.value?.bindPopup(startInfo)
    tripEndMarker.value?.bindPopup(endInfo)

    tripVisualPathLayers.value.forEach((visualLayer) => {
      baseLayerRef.value?.addToLayer(visualLayer)
    })
    baseLayerRef.value?.addToLayer(tripPathLayer.value)
    if (tripStartMarker.value) {
      baseLayerRef.value?.addToLayer(tripStartMarker.value)
    }
    if (tripEndMarker.value) {
      baseLayerRef.value?.addToLayer(tripEndMarker.value)
    }
    syncReplayMarker()

    if (props.map) {
      const bounds = L.latLngBounds(tripCoords)
      props.map.fitBounds(bounds, {
        padding: [20, 20],
        // Animated zoom can race with trip layer replacement when users click
        // multiple trips in quick succession, leaving detached markers in the
        // zoom animation callback queue.
        animate: false
      })

      tripPopupTimeoutId = setTimeout(() => {
        tripPopupTimeoutId = null
        tripPathLayer.value?.openPopup?.()
        clearTripPopupAutoHideTimeout()
        tripPopupAutoHideTimeoutId = setTimeout(() => {
          tripPopupAutoHideTimeoutId = null
          tripPathLayer.value?.closePopup?.()
        }, resolveHighlightedTripPopupAutoHideMs())
      }, 500)
    }
    return
  }

  emitReplayPathData([])
}

const renderAll = () => {
  if (!baseLayerRef.value?.isReady) {
    return
  }

  renderPaths()
  renderHighlightedTrip(props.highlightedTrip)
}

watch(() => props.replayState, () => {
  syncReplayMarker()
}, { deep: true })

watch(
  () => [
    props.pathData,
    props.highlightedTrip,
    props.pathOptions,
    props.focusHighlightedTrip,
    props.allowPathDataTripFallback
  ],
  renderAll,
  { deep: true }
)

onBeforeUnmount(() => {
  clearHighlightedTripLayers()
  if (lastReplayEmissionKey !== '') {
    emit('highlighted-trip-replay-data', { tripKey: '', points: [] })
  }
})

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
