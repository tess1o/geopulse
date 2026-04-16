<template>
  <BaseLayer
      ref="baseLayerRef"
      :map="map"
      :visible="visible"
      @layer-ready="handleLayerReady"
  />
</template>

<script setup>
import {ref, watch, computed, readonly, onBeforeUnmount} from 'vue'
import L from 'leaflet'
import BaseLayer from '@/components/maps/layers/BaseLayer.vue'
import {createHighlightedPathStartMarker, createHighlightedPathEndMarker} from '@/utils/mapHelpers'
import {formatDuration, formatDistance} from "@/utils/calculationsHelpers";
import {useTimezone} from '@/composables/useTimezone'
import '@/maps/shared/styles/mapPopupContent.css'
import {
  buildTripHoverTooltipHtml,
  buildTripPopupHtml
} from '@/maps/shared/popupContentBuilders'
import {
  buildTripHoverContext as buildSharedTripHoverContext,
  projectTripHoverContext,
  resolveTripHoverTiming as resolveSharedTripHoverTiming
} from '@/maps/shared/tripHoverMath'
import {
  normalizePathPoints,
  reconstructTripPathPoints,
  areSameCoordinate
} from '@/utils/tripPathReconstruction'
import {
  buildHighlightedTripSegments
} from '@/maps/shared/highlightedTripSpeedBands'

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
  }
})

const emit = defineEmits(['path-click', 'path-hover', 'trip-marker-click', 'highlighted-trip-click'])

// State
const baseLayerRef = ref(null)
const pathLayers = ref([])

// Computed
const hasPathData = computed(() => props.pathData && props.pathData.length > 0)

// Layer management
const handleLayerReady = () => {
  if (hasPathData.value) {
    renderPaths()
  }
}

const renderPaths = () => {
  if (!baseLayerRef.value) return

  // Always clear existing paths first so stale polylines are removed when
  // a new range has no path data.
  clearPaths()

  if (!hasPathData.value) return

  props.pathData.forEach((pathGroup, groupIndex) => {
    const latlngs = pathGroup.map(point => [point.latitude, point.longitude])

    const polyline = L.polyline(latlngs, {
      ...props.pathOptions,
      pathId: pathGroup.id || groupIndex
    })

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

watch(() => props.pathData, () => {
  if (baseLayerRef.value?.isReady) {
    renderPaths()
  }
}, {deep: true})

// Trip-specific state
const tripPathLayer = ref(null)
const tripVisualPathLayers = ref([])
const tripStartMarker = ref(null)
const tripEndMarker = ref(null)
let tripHoverContext = null
let refreshTripHoverContext = null
let tripHoverContextMapHandler = null
let tripPopupTimeoutId = null

const clearTripPopupTimeout = () => {
  if (tripPopupTimeoutId) {
    clearTimeout(tripPopupTimeoutId)
    tripPopupTimeoutId = null
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

const clearHighlightedTripLayers = () => {
  clearTripPopupTimeout()
  clearTripHoverState()

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

watch(() => props.highlightedTrip, (newTrip) => {
  clearHighlightedTripLayers()

  if (newTrip && newTrip.type === 'trip') {
    const tripPath = reconstructTripPath(newTrip)
    if (!tripPath || tripPath.length < 2) {
      console.warn('Could not reconstruct trip path - insufficient GPS points')
      return
    }

    const tripStartPathPoint = tripPath[0]
    const tripEndPathPoint = tripPath[tripPath.length - 1]
    const startPoint = tripStartPathPoint
    const endPoint = tripEndPathPoint
    const sameEndpoint = areSameCoordinate(startPoint, endPoint)
    const tripCoords = tripPath.map(point => [point.latitude, point.longitude])

    const highlightedSegments = buildHighlightedTripSegments(tripPath)

    tripVisualPathLayers.value = highlightedSegments.segments.map((segment) => L.polyline(segment.latLngs, {
      color: segment.color,
      weight: 6,
      opacity: 1,
      lineCap: 'round',
      lineJoin: 'round',
      interactive: false
    }))

    tripPathLayer.value = L.polyline(tripCoords, {
      color: '#000000',
      weight: 16,
      opacity: 0,
      lineCap: 'round',
      lineJoin: 'round'
    })

    tripStartMarker.value = createHighlightedPathStartMarker(
      startPoint.latitude,
      startPoint.longitude,
      true,
      sameEndpoint ? { transform: 'translateX(-14px)' } : {}
    )

    tripEndMarker.value = createHighlightedPathEndMarker(
      endPoint.latitude,
      endPoint.longitude,
      true,
      sameEndpoint ? { transform: 'translateX(14px)' } : {}
    )

    // Keep start/end trip markers above timeline/cluster markers.
    tripStartMarker.value.setZIndexOffset(4200)
    tripEndMarker.value.setZIndexOffset(4100)

    if (sameEndpoint) {
      tripStartMarker.value.setZIndexOffset(4210)
      tripEndMarker.value.setZIndexOffset(4200)
    }

    const tripInfo = buildTripPopupContent(newTrip)

    const startTime = timezone.fromUtc(newTrip.timestamp)
    const endTime = startTime.add(newTrip.tripDuration, 'second')

    const startInfo = `
      <div class="trip-popup">
        <div class="trip-title trip-start">
          🚀 Trip Start
        </div>
        <div class="trip-detail">
          Start: ${formatDateTimeDisplay(startTime.toISOString())}
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
          End: ${formatDateTimeDisplay(endTime.toISOString())}
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

    tripStartMarker.value.on('click', (e) => {
      emit('trip-marker-click', {
        tripData: newTrip,
        markerType: 'start',
        event: e
      })
    })

    tripStartMarker.value.on('mouseover', function () {
      this.openPopup()
    })
    tripStartMarker.value.on('mouseout', function () {
      this.closePopup()
    })

    tripEndMarker.value.on('click', (e) => {
      emit('trip-marker-click', {
        tripData: newTrip,
        markerType: 'end',
        event: e
      })
    })

    tripEndMarker.value.on('mouseover', function () {
      this.openPopup()
    })
    tripEndMarker.value.on('mouseout', function () {
      this.closePopup()
    })

    tripHoverContext = buildTripHoverContext(tripPath, props.map)
    refreshTripHoverContext = () => {
      if (tripHoverContext && props.map) {
        refreshTripHoverContextProjection(tripHoverContext, props.map)
      }
    }
    refreshTripHoverContext()

    tripHoverContextMapHandler = () => {
      refreshTripHoverContext?.()
    }
    props.map?.on?.('zoomend', tripHoverContextMapHandler)
    props.map?.on?.('moveend', tripHoverContextMapHandler)

    tripPathLayer.value.on('mousemove', (e) => {
      // While inspecting hover timing, hide the static highlighted-trip popup.
      clearTripPopupTimeout()
      tripPathLayer.value?.closePopup?.()

      const hoverTiming = resolveTripHoverTiming(tripHoverContext, e.latlng, props.map)
      if (!hoverTiming) {
        hideTripHoverTooltip(tripPathLayer.value)
        return
      }

      showTripHoverTooltip(tripPathLayer.value, props.map, newTrip, hoverTiming)
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

    tripPathLayer.value.bindPopup(tripInfo)
    tripStartMarker.value.bindPopup(startInfo)
    tripEndMarker.value.bindPopup(endInfo)

    tripVisualPathLayers.value.forEach((visualLayer) => {
      baseLayerRef.value?.addToLayer(visualLayer)
    })
    baseLayerRef.value?.addToLayer(tripPathLayer.value)
    baseLayerRef.value?.addToLayer(tripStartMarker.value)
    baseLayerRef.value?.addToLayer(tripEndMarker.value)

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
        tripPathLayer.value?.openPopup()
      }, 500)
    }
  }
}, {deep: true})

const reconstructTripPath = (trip) => {
  if (!trip || !props.pathData || props.pathData.length === 0) {
    return null
  }

  const normalizedPoints = normalizePathPoints(props.pathData)
  const { points } = reconstructTripPathPoints(trip, normalizedPoints)
  return points
}

watch(() => props.pathOptions, () => {
  if (baseLayerRef.value?.isReady) {
    renderPaths()
  }
}, {deep: true})

onBeforeUnmount(() => {
  clearHighlightedTripLayers()
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
