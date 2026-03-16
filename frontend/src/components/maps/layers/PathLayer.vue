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
import BaseLayer from './BaseLayer.vue'
import {createHighlightedPathStartMarker, createHighlightedPathEndMarker} from '@/utils/mapHelpers'
import {formatDuration, formatDistance} from "@/utils/calculationsHelpers";
import {useTimezone} from '@/composables/useTimezone'
import {
  normalizePathPoints,
  reconstructTripPathPoints,
  resolveTripMarkerPoint,
  areSameCoordinate
} from '@/utils/tripPathReconstruction'

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

const clamp01 = (value) => Math.min(1, Math.max(0, value))
const EXACT_HOVER_THRESHOLD_PX = 10

const formatDateTimeDisplay = (dateValue) =>
  `${timezone.formatDateDisplay(dateValue)} ${timezone.format(dateValue, 'HH:mm:ss')}`

const toPointTimestampMs = (point) => {
  if (Number.isFinite(point?._timestampMs)) {
    return point._timestampMs
  }
  if (!point?.timestamp) {
    return null
  }
  const parsed = Date.parse(point.timestamp)
  return Number.isNaN(parsed) ? null : parsed
}

const buildTripPopupContent = (trip) => {
  const startMs = Date.parse(trip.timestamp)
  const durationSeconds = Number.isFinite(Number(trip.tripDuration)) ? Number(trip.tripDuration) : 0
  const endMs = Number.isFinite(startMs) ? startMs + Math.max(0, durationSeconds) * 1000 : null

  const startText = Number.isFinite(startMs)
    ? formatDateTimeDisplay(new Date(startMs).toISOString())
    : 'Unknown'
  const endText = Number.isFinite(endMs)
    ? formatDateTimeDisplay(new Date(endMs).toISOString())
    : 'Unknown'

  return `
    <div class="trip-popup">
      <div class="trip-title">
        ${trip.movementType || 'Movement'} Trip
      </div>
      <div class="trip-detail">
        Start: ${startText}
      </div>
      <div class="trip-detail">
        End: ${endText}
      </div>
      <div class="trip-detail">
        Duration: ${formatDuration(trip.tripDuration)}
      </div>
      <div class="trip-detail">
        Distance: ${formatDistance(trip.distanceMeters || 0)}
      </div>
      <div class="trip-detail trip-detail-hint">
        Hover the highlighted route to see when you were there.
      </div>
    </div>
  `
}

const buildTripHoverContext = (tripPath, map) => {
  if (!Array.isArray(tripPath) || tripPath.length < 2 || !map?.distance) {
    return null
  }

  const latLngPoints = tripPath.map(point => L.latLng(point.latitude, point.longitude))
  const timestampsMs = tripPath.map((point) => toPointTimestampMs(point))
  const cumulativeDistancesMeters = []
  let distanceFromStartMeters = 0

  for (let index = 0; index < latLngPoints.length; index += 1) {
    if (index > 0) {
      distanceFromStartMeters += map.distance(latLngPoints[index - 1], latLngPoints[index])
    }
    cumulativeDistancesMeters.push(distanceFromStartMeters)
  }

  return {
    latLngPoints,
    timestampsMs,
    cumulativeDistancesMeters,
    projectedPoints: [],
    segments: []
  }
}

const refreshTripHoverContextProjection = (context, map) => {
  if (!context || !map?.latLngToLayerPoint) return

  context.projectedPoints = context.latLngPoints.map((latLng) => map.latLngToLayerPoint(latLng))
  context.segments = []

  for (let index = 0; index < context.projectedPoints.length - 1; index += 1) {
    const startProjected = context.projectedPoints[index]
    const endProjected = context.projectedPoints[index + 1]
    const deltaX = endProjected.x - startProjected.x
    const deltaY = endProjected.y - startProjected.y
    const segmentLengthSq = deltaX * deltaX + deltaY * deltaY
    const segmentLengthMeters = context.cumulativeDistancesMeters[index + 1] - context.cumulativeDistancesMeters[index]

    context.segments.push({
      index,
      startProjected,
      deltaX,
      deltaY,
      segmentLengthSq,
      segmentLengthMeters
    })
  }
}

const resolveTripHoverTiming = (context, latLng, map) => {
  if (!context || !latLng || !map?.latLngToLayerPoint || !context.projectedPoints.length) {
    return null
  }

  const hoverPoint = map.latLngToLayerPoint(latLng)
  const exactThresholdSq = EXACT_HOVER_THRESHOLD_PX * EXACT_HOVER_THRESHOLD_PX

  let nearestExact = null
  for (let index = 0; index < context.projectedPoints.length; index += 1) {
    const timestampMs = context.timestampsMs[index]
    if (!Number.isFinite(timestampMs)) continue

    const projected = context.projectedPoints[index]
    const distanceSq = (
      (hoverPoint.x - projected.x) * (hoverPoint.x - projected.x) +
      (hoverPoint.y - projected.y) * (hoverPoint.y - projected.y)
    )

    if (!nearestExact || distanceSq < nearestExact.distanceSq) {
      nearestExact = {
        distanceSq,
        timeMs: timestampMs,
        distanceFromStartMeters: context.cumulativeDistancesMeters[index],
        snappedLatLng: context.latLngPoints[index]
      }
    }
  }

  if (nearestExact && nearestExact.distanceSq <= exactThresholdSq) {
    return {
      ...nearestExact,
      mode: 'exact'
    }
  }

  let bestSegmentMatch = null

  for (let index = 0; index < context.segments.length; index += 1) {
    const segment = context.segments[index]
    const projectionFactor = segment.segmentLengthSq > 0
      ? clamp01(
        (
          ((hoverPoint.x - segment.startProjected.x) * segment.deltaX) +
          ((hoverPoint.y - segment.startProjected.y) * segment.deltaY)
        ) / segment.segmentLengthSq
      )
      : 0

    const projectedX = segment.startProjected.x + projectionFactor * segment.deltaX
    const projectedY = segment.startProjected.y + projectionFactor * segment.deltaY
    const distanceSq = (
      (hoverPoint.x - projectedX) * (hoverPoint.x - projectedX) +
      (hoverPoint.y - projectedY) * (hoverPoint.y - projectedY)
    )

    const timeA = context.timestampsMs[segment.index]
    const timeB = context.timestampsMs[segment.index + 1]
    let estimatedTimeMs = null
    if (Number.isFinite(timeA) && Number.isFinite(timeB)) {
      estimatedTimeMs = Math.round(timeA + ((timeB - timeA) * projectionFactor))
    } else if (Number.isFinite(timeA)) {
      estimatedTimeMs = timeA
    } else if (Number.isFinite(timeB)) {
      estimatedTimeMs = timeB
    }

    if (!bestSegmentMatch || distanceSq < bestSegmentMatch.distanceSq) {
      bestSegmentMatch = {
        distanceSq,
        timeMs: estimatedTimeMs,
        distanceFromStartMeters: context.cumulativeDistancesMeters[segment.index] + (segment.segmentLengthMeters * projectionFactor),
        snappedLatLng: map.layerPointToLatLng(L.point(projectedX, projectedY))
      }
    }
  }

  if (!Number.isFinite(bestSegmentMatch?.timeMs)) {
    return nearestExact
      ? {
        ...nearestExact,
        mode: 'exact'
      }
      : null
  }

  return {
    ...bestSegmentMatch,
    mode: 'estimated'
  }
}

const buildTripHoverTooltipContent = (trip, hoverTiming) => {
  if (!hoverTiming || !Number.isFinite(hoverTiming.timeMs)) {
    return ''
  }

  const startMs = Date.parse(trip.timestamp)
  const offsetSeconds = Number.isFinite(startMs)
    ? Math.max(0, Math.round((hoverTiming.timeMs - startMs) / 1000))
    : null
  const confidenceLabel = hoverTiming.mode === 'exact' ? 'Exact GPS point' : 'Estimated between points'
  const confidenceClass = hoverTiming.mode === 'exact' ? 'exact' : 'estimated'

  return `
    <div class="trip-hover-tooltip">
      <div class="trip-hover-time">
        ${formatDateTimeDisplay(new Date(hoverTiming.timeMs).toISOString())}
      </div>
      <div class="trip-hover-confidence ${confidenceClass}">
        ${confidenceLabel}
      </div>
      ${Number.isFinite(offsetSeconds) ? `
      <div class="trip-hover-offset">
        From trip start: ${formatDuration(offsetSeconds)}
      </div>
      ` : ''}
    </div>
  `
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

    const startPoint = resolveTripMarkerPoint(newTrip, 'start', tripPath[0])
    const endPoint = resolveTripMarkerPoint(newTrip, 'end', tripPath[tripPath.length - 1])
    const sameEndpoint = areSameCoordinate(startPoint, endPoint)
    const tripCoords = tripPath.map(point => [point.latitude, point.longitude])

    // Keep highlighted polyline endpoints aligned with start/end markers.
    if (tripCoords.length >= 2) {
      tripCoords[0] = [startPoint.latitude, startPoint.longitude]
      tripCoords[tripCoords.length - 1] = [endPoint.latitude, endPoint.longitude]
    }

    tripPathLayer.value = L.polyline(tripCoords, {
      color: '#ff6b6b',
      weight: 6,
      opacity: 1,
      dashArray: '10, 5'
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

    if (sameEndpoint) {
      tripStartMarker.value.setZIndexOffset(20)
      tripEndMarker.value.setZIndexOffset(10)
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

<style>
/* Trip popup styling */
.trip-popup {
  font-family: var(--font-family, system-ui);
  min-width: 180px;
  font-size: 0.875rem;
  line-height: 1.4;
}

.trip-title {
  font-weight: 600;
  color: var(--gp-text-primary, #1e293b);
  margin-bottom: 0.5rem;
  font-size: 1rem;
}

.trip-title.trip-start {
  color: var(--gp-success, #22c55e);
}

.trip-title.trip-end {
  color: var(--gp-danger, #ef4444);
}

.trip-detail {
  color: var(--gp-text-secondary, #64748b);
  font-size: 0.8rem;
  margin-bottom: 0.25rem;
}

.trip-detail:last-child {
  margin-bottom: 0;
}

.trip-detail.trip-detail-hint {
  margin-top: 0.5rem;
  font-style: italic;
}

.trip-hover-tooltip {
  min-width: 170px;
  font-size: 0.78rem;
  line-height: 1.35;
}

.trip-hover-time {
  font-weight: 600;
  color: var(--gp-text-primary, #1e293b);
}

.trip-hover-confidence {
  margin-top: 0.2rem;
  font-weight: 600;
}

.trip-hover-confidence.exact {
  color: var(--gp-success, #22c55e);
}

.trip-hover-confidence.estimated {
  color: #d97706;
}

.trip-hover-offset {
  margin-top: 0.2rem;
  color: var(--gp-text-secondary, #64748b);
}

.leaflet-tooltip.trip-hover-tooltip-container {
  background: rgba(255, 255, 255, 0.96) !important;
  border: 1px solid rgba(148, 163, 184, 0.65) !important;
  color: #0f172a !important;
}

.leaflet-tooltip.trip-hover-tooltip-container::before {
  border-top-color: rgba(255, 255, 255, 0.96) !important;
}

/* Dark theme overrides for trip popups */
.p-dark .leaflet-popup-content-wrapper .trip-popup .trip-title {
  color: rgba(255, 255, 255, 0.95) !important;
}

.p-dark .leaflet-popup-content-wrapper .trip-popup .trip-title.trip-start {
  color: var(--gp-success-light, #4ade80) !important;
}

.p-dark .leaflet-popup-content-wrapper .trip-popup .trip-title.trip-end {
  color: var(--gp-danger-light, #f87171) !important;
}

.p-dark .leaflet-popup-content-wrapper .trip-popup .trip-detail {
  color: rgba(255, 255, 255, 0.8) !important;
}

.p-dark .leaflet-tooltip.trip-hover-tooltip-container .trip-hover-time {
  color: rgba(255, 255, 255, 0.95) !important;
}

.p-dark .leaflet-tooltip.trip-hover-tooltip-container .trip-hover-offset {
  color: rgba(255, 255, 255, 0.8) !important;
}

.p-dark .leaflet-tooltip.trip-hover-tooltip-container {
  background: linear-gradient(135deg, rgba(15, 23, 42, 0.95), rgba(30, 41, 59, 0.9)) !important;
  border: 1px solid rgba(71, 85, 105, 0.3) !important;
  color: rgba(255, 255, 255, 0.95) !important;
}

.p-dark .leaflet-tooltip.trip-hover-tooltip-container::before {
  border-top-color: rgba(15, 23, 42, 0.95) !important;
}

/* Light theme - ensure good contrast */
.trip-popup .trip-title {
  color: #1e293b !important;
}

.trip-popup .trip-title.trip-start {
  color: #22c55e !important;
}

.trip-popup .trip-title.trip-end {
  color: #ef4444 !important;
}

.trip-popup .trip-detail {
  color: #475569 !important;
}
</style>
