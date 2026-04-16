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
import {
  buildHighlightedTripSegments,
  HIGHLIGHTED_TRIP_SPEED_BAND_COLORS
} from '@/maps/shared/highlightedTripSpeedBands'
import { getTripMovementIconClass } from '@/utils/timelineIconUtils'

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
  highlightedHoverMapHandler: null,
  replayMarkerEntry: null,
  replayMarkerMovementType: '',
  replayLastCameraSyncMs: 0,
  replayCameraSnapshot: null,
  replay3dActive: false,
  replayWasPlaying: false,
  replayLastElapsedMs: 0,
  lastReplayEmissionKey: ''
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

const buildReplayMarkerElement = (movementType = 'UNKNOWN') => {
  const iconClass = getTripMovementIconClass(movementType)
  const markerElement = document.createElement('div')
  markerElement.className = 'gp-trip-replay-marker'
  markerElement.innerHTML = `
    <div class="gp-trip-replay-marker-inner">
      <i class="${iconClass}"></i>
    </div>
  `.trim()

  return markerElement
}

const removeReplayMarker = () => {
  state.replayWasPlaying = false
  state.replayLastElapsedMs = 0
  state.replayLastCameraSyncMs = 0

  if (!state.replayMarkerEntry) {
    return
  }

  state.replayMarkerEntry.marker.remove()
  state.replayMarkerEntry = null
  state.replayMarkerMovementType = ''
}

const normalizeBearingDegrees = (bearing) => {
  if (!Number.isFinite(bearing)) {
    return 0
  }

  let normalized = bearing % 360
  if (normalized > 180) {
    normalized -= 360
  } else if (normalized < -180) {
    normalized += 360
  }

  return normalized
}

const snapshotReplayCameraIfNeeded = () => {
  if (!isMapLibreMap(props.map) || state.replayCameraSnapshot) {
    return
  }

  const currentTerrain = props.map.getTerrain?.()
  state.replayCameraSnapshot = {
    pitch: props.map.getPitch?.() || 0,
    bearing: props.map.getBearing?.() || 0,
    terrain: currentTerrain && currentTerrain.source
      ? { source: currentTerrain.source, exaggeration: currentTerrain.exaggeration }
      : null
  }
}

const restoreReplayCameraSnapshot = () => {
  if (!isMapLibreMap(props.map) || !state.replayCameraSnapshot) {
    state.replayCameraSnapshot = null
    return
  }

  const snapshot = state.replayCameraSnapshot
  state.replayCameraSnapshot = null
  state.replay3dActive = false

  try {
    if (snapshot.terrain?.source) {
      props.map.setTerrain(snapshot.terrain)
    } else if (props.map.getTerrain?.()) {
      props.map.setTerrain(null)
    }
  } catch {
    // Terrain restoration is best-effort.
  }

  try {
    props.map.easeTo({
      pitch: Number.isFinite(snapshot.pitch) ? snapshot.pitch : 0,
      bearing: Number.isFinite(snapshot.bearing) ? snapshot.bearing : props.map.getBearing?.() || 0,
      duration: 250,
      essential: true
    })
  } catch {
    // Camera restoration is best-effort.
  }
}

const resolveRasterDemSourceId = () => {
  if (!isMapLibreMap(props.map)) {
    return null
  }

  const style = props.map.getStyle?.()
  const sources = style?.sources || {}

  return Object.keys(sources).find((sourceId) => sources[sourceId]?.type === 'raster-dem') || null
}

const syncReplay3dMode = ({ enable3d, bearing = null } = {}) => {
  if (!isMapLibreMap(props.map)) {
    return
  }

  if (!enable3d) {
    restoreReplayCameraSnapshot()
    return
  }

  snapshotReplayCameraIfNeeded()

  try {
    const terrainSourceId = resolveRasterDemSourceId()
    if (terrainSourceId) {
      const activeTerrain = props.map.getTerrain?.()
      if (!activeTerrain || activeTerrain.source !== terrainSourceId) {
        props.map.setTerrain({ source: terrainSourceId, exaggeration: 1.2 })
      }
    }
  } catch {
    // Terrain enablement is best-effort.
  }

  try {
    props.map.easeTo({
      pitch: 55,
      bearing: Number.isFinite(bearing) ? bearing : props.map.getBearing?.() || 0,
      duration: 150,
      essential: true
    })
  } catch {
    // Pitch fallback is best-effort.
  }

  state.replay3dActive = true
}

const syncReplayCamera = ({ cursor, followCamera = true, playing = false, enable3d = false, force = false } = {}) => {
  if (!isMapLibreMap(props.map) || !followCamera || !cursor) {
    return
  }

  if (!playing && !force) {
    return
  }

  const cursorLatitude = toFiniteNumber(cursor.latitude)
  const cursorLongitude = toFiniteNumber(cursor.longitude)
  if (cursorLatitude === null || cursorLongitude === null) {
    return
  }

  const now = Date.now()
  if (!force && playing && now - state.replayLastCameraSyncMs < 170) {
    return
  }
  state.replayLastCameraSyncMs = now

  const cameraUpdate = {
    center: [cursorLongitude, cursorLatitude],
    duration: force ? 0 : (playing ? 170 : 120),
    essential: true
  }

  if (enable3d) {
    cameraUpdate.pitch = 55
    if (Number.isFinite(cursor.bearing)) {
      cameraUpdate.bearing = cursor.bearing
    }
  }

  try {
    props.map.easeTo(cameraUpdate)
  } catch {
    // Camera follow is best-effort.
  }
}

const syncReplayMarker = () => {
  if (!isMapLibreMap(props.map) || !props.visible || props.highlightedTrip?.type !== 'trip') {
    removeReplayMarker()
    restoreReplayCameraSnapshot()
    return
  }

  const replayState = props.replayState || {}
  const replayEnabled = Boolean(replayState.enabled)
  const replayIsPlaying = Boolean(replayState.playing)
  const replayElapsedMs = toFiniteNumber(replayState.elapsedMs) || 0
  const cursor = replayState.cursor
  const cursorLatitude = toFiniteNumber(cursor?.latitude)
  const cursorLongitude = toFiniteNumber(cursor?.longitude)

  if (!replayEnabled || cursorLatitude === null || cursorLongitude === null) {
    removeReplayMarker()
    restoreReplayCameraSnapshot()
    return
  }

  const movementType = String(replayState.movementType || props.highlightedTrip?.movementType || 'UNKNOWN').toUpperCase()
  if (!state.replayMarkerEntry || state.replayMarkerMovementType !== movementType) {
    removeReplayMarker()

    const markerElement = buildReplayMarkerElement(movementType)
    const marker = new maplibregl.Marker({
      element: markerElement,
      anchor: 'center',
      rotation: 0,
      rotationAlignment: 'viewport',
      pitchAlignment: 'viewport'
    })
      .setLngLat([cursorLongitude, cursorLatitude])
      .addTo(props.map)

    state.replayMarkerEntry = {
      marker,
      element: markerElement
    }
    state.replayMarkerMovementType = movementType
  } else {
    state.replayMarkerEntry.marker.setLngLat([cursorLongitude, cursorLatitude])
  }

  const iconElement = state.replayMarkerEntry.element.querySelector('i')
  if (iconElement) {
    iconElement.className = getTripMovementIconClass(movementType)
  }

  const cursorBearing = toFiniteNumber(cursor?.bearing)
  const mapBearing = toFiniteNumber(props.map.getBearing?.()) || 0
  const markerRotation = (
    Boolean(replayState.enable3d) && Boolean(replayState.followCamera)
      ? 0
      : normalizeBearingDegrees((cursorBearing || 0) - mapBearing)
  )
  if (typeof state.replayMarkerEntry.marker.setRotation === 'function') {
    state.replayMarkerEntry.marker.setRotation(markerRotation)
  }

  if (replayIsPlaying) {
    closeHighlightedTripPopup()
    hideTripHoverTooltip()
  }

  const replayStarted = replayIsPlaying && !state.replayWasPlaying
  const replayRestarted = replayElapsedMs <= 1 && state.replayLastElapsedMs > 1
  const forceCameraSync = replayStarted || replayRestarted
  if (forceCameraSync) {
    state.replayLastCameraSyncMs = 0
  }

  syncReplay3dMode({
    enable3d: Boolean(replayState.enable3d),
    bearing: cursorBearing !== null ? cursorBearing : null
  })
  syncReplayCamera({
    cursor,
    followCamera: Boolean(replayState.followCamera),
    playing: replayIsPlaying,
    enable3d: Boolean(replayState.enable3d),
    force: forceCameraSync
  })

  state.replayWasPlaying = replayIsPlaying
  state.replayLastElapsedMs = replayElapsedMs
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
    maxZoom: 16,
    animate: false
  })
}

const syncHighlightedTripPopup = (lineCoordinates) => {
  if (props.replayState?.suppressTripPopup) {
    closeHighlightedTripPopup()
    return
  }

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
      hoverPathPoints: [],
      fullLineCoordinates: [],
      replayPathPoints: []
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
      hoverPathPoints: [],
      fullLineCoordinates: [],
      replayPathPoints: []
    }
  }

  const renderedTripPoints = tripPoints.map((point) => ({ ...point }))
  const hoverPathPoints = renderedTripPoints.map((point) => ({
    latitude: point.latitude,
    longitude: point.longitude,
    timestamp: point.timestamp || null
  }))

  const lineCoordinates = renderedTripPoints.map((point) => [point.longitude, point.latitude])

  const startPoint = resolveTripMarkerPoint(props.highlightedTrip, 'start', {
    latitude: renderedTripPoints[0].latitude,
    longitude: renderedTripPoints[0].longitude
  })

  const endPoint = resolveTripMarkerPoint(props.highlightedTrip, 'end', {
    latitude: renderedTripPoints[renderedTripPoints.length - 1].latitude,
    longitude: renderedTripPoints[renderedTripPoints.length - 1].longitude
  })

  if (startPoint && !areSameCoordinate(renderedTripPoints[0], startPoint)) {
    lineCoordinates[0] = [startPoint.longitude, startPoint.latitude]
    renderedTripPoints[0] = {
      ...renderedTripPoints[0],
      latitude: startPoint.latitude,
      longitude: startPoint.longitude
    }
    hoverPathPoints[0] = {
      ...hoverPathPoints[0],
      latitude: startPoint.latitude,
      longitude: startPoint.longitude
    }
  }

  if (endPoint && !areSameCoordinate(renderedTripPoints[renderedTripPoints.length - 1], endPoint)) {
    lineCoordinates[lineCoordinates.length - 1] = [endPoint.longitude, endPoint.latitude]
    renderedTripPoints[renderedTripPoints.length - 1] = {
      ...renderedTripPoints[renderedTripPoints.length - 1],
      latitude: endPoint.latitude,
      longitude: endPoint.longitude
    }
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

  const highlightedSegments = buildHighlightedTripSegments(renderedTripPoints)

  const lineCollection = createFeatureCollection(highlightedSegments.segments.map((segment) => ({
    type: 'Feature',
    geometry: {
      type: 'LineString',
      coordinates: segment.coordinates
    },
    properties: {
      speedBand: segment.speedBand,
      tripRaw: JSON.stringify(props.highlightedTrip || {})
    }
  })))

  return {
    lineCollection,
    endpointMarkers,
    hoverPathPoints,
    fullLineCoordinates: lineCoordinates,
    replayPathPoints: renderedTripPoints
  }
}

const normalizeReplayPathPoints = (tripPathPoints) => (
  (Array.isArray(tripPathPoints) ? tripPathPoints : [])
    .map((point) => {
      const latitude = toFiniteNumber(point?.latitude)
      const longitude = toFiniteNumber(point?.longitude)
      if (latitude === null || longitude === null) {
        return null
      }

      const timestamp = (
        point?.timestamp
        || (Number.isFinite(point?._timestampMs) ? new Date(point._timestampMs).toISOString() : null)
      )

      return {
        latitude,
        longitude,
        altitude: toFiniteNumber(point?.altitude),
        timestamp
      }
    })
    .filter(Boolean)
)

const emitReplayPathData = (highlightedData) => {
  if (!props.highlightedTrip || props.highlightedTrip.type !== 'trip') {
    if (state.lastReplayEmissionKey !== '') {
      state.lastReplayEmissionKey = ''
      emit('highlighted-trip-replay-data', { tripKey: '', points: [] })
    }
    return
  }

  const tripKey = getHighlightedTripKey(props.highlightedTrip)
  const replayPathPoints = normalizeReplayPathPoints(highlightedData?.replayPathPoints)
  const firstPoint = replayPathPoints[0]
  const lastPoint = replayPathPoints[replayPathPoints.length - 1]
  const emissionKey = [
    tripKey,
    replayPathPoints.length,
    firstPoint?.timestamp || '',
    lastPoint?.timestamp || ''
  ].join('|')

  if (state.lastReplayEmissionKey === emissionKey) {
    return
  }

  state.lastReplayEmissionKey = emissionKey
  emit('highlighted-trip-replay-data', {
    tripKey,
    points: replayPathPoints
  })
}

const highlightedLineColorExpression = [
  'match',
  ['get', 'speedBand'],
  'red', HIGHLIGHTED_TRIP_SPEED_BAND_COLORS.red,
  'yellow', HIGHLIGHTED_TRIP_SPEED_BAND_COLORS.yellow,
  'green', HIGHLIGHTED_TRIP_SPEED_BAND_COLORS.green,
  'unknown', HIGHLIGHTED_TRIP_SPEED_BAND_COLORS.unknown,
  HIGHLIGHTED_TRIP_SPEED_BAND_COLORS.unknown
]

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

const syncReplayFocusLayerVisibility = () => {
  if (!isMapLibreMap(props.map)) {
    return
  }

  const replayFocusMode = Boolean(props.replayState?.playing)
  setLayerVisibility(props.map, [state.lineLayerId], props.visible && !replayFocusMode)
  setLayerVisibility(props.map, [state.highlightedLineLayerId], props.visible)
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
      'line-color': highlightedLineColorExpression,
      'line-width': 6,
      'line-opacity': 1
    }
  })

  syncReplayFocusLayerVisibility()

  syncHighlightedTripPopup(highlightedLineCoordinates)
  syncHighlightedEndpointMarkers(highlighted.endpointMarkers)
  syncTripHoverContext(highlighted.hoverPathPoints)
  emitReplayPathData(highlighted)
  syncReplayMarker()

  unregisterEvents()
  registerEvents()
}

const clearLayer = () => {
  unregisterEvents()
  clearTripHoverState()
  closeHighlightedTripPopup()
  removeHighlightedEndpointMarkers()
  removeReplayMarker()
  restoreReplayCameraSnapshot()

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

  removeLayers(
    targetMap,
    [state.highlightedLineLayerId, state.lineLayerId]
  )
  removeSources(targetMap, [state.highlightedSourceId, state.sourceId])
  state.boundMap = null
}

watch(
  () => [
    props.map,
    props.pathData,
    props.highlightedTrip,
    props.pathOptions,
    props.visible
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

.gp-trip-replay-marker {
  width: 38px;
  height: 38px;
  border-radius: 999px;
  display: flex;
  align-items: center;
  justify-content: center;
  pointer-events: none;
  z-index: 450;
}

.gp-trip-replay-marker-inner {
  width: 100%;
  height: 100%;
  border-radius: 999px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 2px solid rgba(255, 255, 255, 0.92);
  background: linear-gradient(135deg, #2563eb 0%, #1d4ed8 100%);
  color: #ffffff;
  box-shadow: 0 8px 20px rgba(15, 23, 42, 0.35), 0 0 0 2px rgba(37, 99, 235, 0.3);
  transform-origin: center center;
  transition: transform 0.14s linear;
}

.gp-trip-replay-marker-inner i {
  font-size: 1rem;
  line-height: 1;
}

.p-dark .gp-trip-replay-marker-inner {
  border-color: rgba(241, 245, 249, 0.92);
  background: linear-gradient(135deg, #3b82f6 0%, #2563eb 100%);
  box-shadow: 0 10px 24px rgba(2, 6, 23, 0.58), 0 0 0 2px rgba(59, 130, 246, 0.35);
}
</style>
