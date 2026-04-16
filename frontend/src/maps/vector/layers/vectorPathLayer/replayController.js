import maplibregl from 'maplibre-gl'
import { isMapLibreMap, toFiniteNumber } from '@/maps/vector/utils/maplibreLayerUtils'
import { getTripMovementIconClass } from '@/utils/timelineIconUtils'

const REPLAY_3D_PITCH = 50
const REPLAY_3D_MIN_ZOOM = 16.5
const REPLAY_3D_TARGET_ZOOM_CAP = 17.5
const REPLAY_3D_ZOOM_STEP = 2.4

export const createVectorPathReplayController = ({
  getMap
}) => {
  const state = {
    replayMarkerEntry: null,
    replayMarkerMovementType: '',
    replayLastCameraSyncMs: 0,
    replayCameraSnapshot: null,
    replay3dActive: false,
    replayUserCameraInteracting: false,
    replayUserCameraInteractionUntil: 0,
    replayUserCameraHandlers: null,
    replayWasPlaying: false,
    replayLastElapsedMs: 0
  }

  const getActiveMap = () => {
    const map = getMap?.()
    return isMapLibreMap(map) ? map : null
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

  const markReplayUserCameraInteraction = (durationMs = 600) => {
    const safeDuration = Number.isFinite(durationMs) ? Math.max(120, Number(durationMs)) : 600
    state.replayUserCameraInteracting = true
    state.replayUserCameraInteractionUntil = Math.max(
      state.replayUserCameraInteractionUntil || 0,
      Date.now() + safeDuration
    )
  }

  const clearReplayUserCameraInteraction = (cooldownMs = 220) => {
    const safeCooldown = Number.isFinite(cooldownMs) ? Math.max(0, Number(cooldownMs)) : 220
    state.replayUserCameraInteractionUntil = Date.now() + safeCooldown
    state.replayUserCameraInteracting = false
    state.replayLastCameraSyncMs = 0
  }

  const isReplayUserCameraInteracting = () => {
    if (Date.now() >= (state.replayUserCameraInteractionUntil || 0)) {
      state.replayUserCameraInteracting = false
      return false
    }

    state.replayUserCameraInteracting = true
    return true
  }

  const unregisterReplayUserCameraHandlers = () => {
    const handlers = state.replayUserCameraHandlers
    if (!handlers) {
      return
    }

    handlers.canvas?.removeEventListener('wheel', handlers.handleWheel)
    handlers.canvas?.removeEventListener('mousedown', handlers.handleMouseDown)
    handlers.canvas?.removeEventListener('touchstart', handlers.handleTouchStart)
    handlers.canvas?.removeEventListener('touchmove', handlers.handleTouchMove)
    window.removeEventListener('mouseup', handlers.handleMouseUp)
    window.removeEventListener('touchend', handlers.handleTouchEnd)

    state.replayUserCameraHandlers = null
    state.replayUserCameraInteracting = false
    state.replayUserCameraInteractionUntil = 0
  }

  const registerReplayUserCameraHandlers = () => {
    const map = getActiveMap()
    if (!map) {
      unregisterReplayUserCameraHandlers()
      return
    }

    if (state.replayUserCameraHandlers?.map === map) {
      return
    }
    unregisterReplayUserCameraHandlers()

    const canvas = map.getCanvas?.()
    if (!canvas) {
      return
    }

    const handleWheel = () => markReplayUserCameraInteraction(1100)
    const handleMouseDown = () => markReplayUserCameraInteraction(2200)
    const handleMouseUp = () => clearReplayUserCameraInteraction(260)
    const handleTouchStart = () => markReplayUserCameraInteraction(2400)
    const handleTouchMove = () => markReplayUserCameraInteraction(900)
    const handleTouchEnd = () => clearReplayUserCameraInteraction(320)

    canvas.addEventListener('wheel', handleWheel, { passive: true })
    canvas.addEventListener('mousedown', handleMouseDown, { passive: true })
    canvas.addEventListener('touchstart', handleTouchStart, { passive: true })
    canvas.addEventListener('touchmove', handleTouchMove, { passive: true })
    window.addEventListener('mouseup', handleMouseUp, { passive: true })
    window.addEventListener('touchend', handleTouchEnd, { passive: true })

    state.replayUserCameraHandlers = {
      map,
      canvas,
      handleWheel,
      handleMouseDown,
      handleMouseUp,
      handleTouchStart,
      handleTouchMove,
      handleTouchEnd
    }
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

  const snapshotReplayCameraIfNeeded = (map) => {
    if (!isMapLibreMap(map)) {
      return
    }

    if (state.replayCameraSnapshot && state.replayCameraSnapshot.map !== map) {
      // Map instance changed while in replay mode, restore stale snapshot first.
      const previousMap = state.replayCameraSnapshot.map
      if (isMapLibreMap(previousMap)) {
        try {
          if (state.replayCameraSnapshot.terrain?.source) {
            previousMap.setTerrain(state.replayCameraSnapshot.terrain)
          } else if (previousMap.getTerrain?.()) {
            previousMap.setTerrain(null)
          }
        } catch {
          // Terrain restoration is best-effort.
        }

        try {
          previousMap.easeTo({
            pitch: Number.isFinite(state.replayCameraSnapshot.pitch)
              ? state.replayCameraSnapshot.pitch
              : 0,
            bearing: Number.isFinite(state.replayCameraSnapshot.bearing)
              ? state.replayCameraSnapshot.bearing
              : previousMap.getBearing?.() || 0,
            duration: 0,
            essential: true
          })
        } catch {
          // Camera restoration is best-effort.
        }
      }
      state.replayCameraSnapshot = null
    }

    if (state.replayCameraSnapshot) {
      return
    }

    const currentTerrain = map.getTerrain?.()
    state.replayCameraSnapshot = {
      map,
      pitch: map.getPitch?.() || 0,
      bearing: map.getBearing?.() || 0,
      terrain: currentTerrain && currentTerrain.source
        ? { source: currentTerrain.source, exaggeration: currentTerrain.exaggeration }
        : null
    }
  }

  const restoreReplayCameraSnapshot = () => {
    if (!state.replayCameraSnapshot) {
      state.replay3dActive = false
      return
    }

    const snapshot = state.replayCameraSnapshot
    const snapshotMap = snapshot.map
    state.replayCameraSnapshot = null
    state.replay3dActive = false

    if (!isMapLibreMap(snapshotMap)) {
      return
    }

    try {
      if (snapshot.terrain?.source) {
        snapshotMap.setTerrain(snapshot.terrain)
      } else if (snapshotMap.getTerrain?.()) {
        snapshotMap.setTerrain(null)
      }
    } catch {
      // Terrain restoration is best-effort.
    }

    try {
      snapshotMap.easeTo({
        pitch: Number.isFinite(snapshot.pitch) ? snapshot.pitch : 0,
        bearing: Number.isFinite(snapshot.bearing) ? snapshot.bearing : snapshotMap.getBearing?.() || 0,
        duration: 250,
        essential: true
      })
    } catch {
      // Camera restoration is best-effort.
    }
  }

  const resolveRasterDemSourceId = (map) => {
    if (!isMapLibreMap(map)) {
      return null
    }

    const style = map.getStyle?.()
    const sources = style?.sources || {}

    return Object.keys(sources).find((sourceId) => sources[sourceId]?.type === 'raster-dem') || null
  }

  const syncReplay3dMode = ({
    map,
    enable3d,
    bearing = null,
    force = false
  } = {}) => {
    if (!isMapLibreMap(map)) {
      return
    }

    if (!enable3d) {
      restoreReplayCameraSnapshot()
      return
    }

    snapshotReplayCameraIfNeeded(map)

    try {
      const terrainSourceId = resolveRasterDemSourceId(map)
      if (terrainSourceId) {
        const activeTerrain = map.getTerrain?.()
        if (!activeTerrain || activeTerrain.source !== terrainSourceId) {
          map.setTerrain({ source: terrainSourceId, exaggeration: 1.2 })
        }
      }
    } catch {
      // Terrain enablement is best-effort.
    }

    const targetPitch = REPLAY_3D_PITCH
    const currentPitch = map.getPitch?.() || 0
    const shouldApplyPitch = Math.abs(currentPitch - targetPitch) > 0.45
    const shouldApplyBearing = force && Number.isFinite(bearing)
    if (!shouldApplyPitch && !shouldApplyBearing) {
      state.replay3dActive = true
      return
    }

    if (!force && isReplayUserCameraInteracting()) {
      state.replay3dActive = true
      return
    }

    try {
      map.easeTo({
        pitch: targetPitch,
        ...(shouldApplyBearing ? { bearing } : {}),
        duration: force ? 0 : 150,
        essential: true
      })
    } catch {
      // Pitch fallback is best-effort.
    }

    state.replay3dActive = true
  }

  const syncReplayCamera = ({
    map,
    cursor,
    followCamera = true,
    playing = false,
    enable3d = false,
    force = false,
    forceZoomTo3dDefault = false
  } = {}) => {
    if (!isMapLibreMap(map) || !followCamera || !cursor) {
      return
    }

    if (!playing && !force) {
      return
    }

    if (!force && isReplayUserCameraInteracting()) {
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
      cameraUpdate.pitch = REPLAY_3D_PITCH
      if (Number.isFinite(cursor.bearing)) {
        cameraUpdate.bearing = cursor.bearing
      }

      const currentZoom = Number(map.getZoom?.())
      if (
        (force || forceZoomTo3dDefault)
        && (!Number.isFinite(currentZoom) || currentZoom < REPLAY_3D_MIN_ZOOM)
      ) {
        const currentZoomValue = Number.isFinite(currentZoom) ? currentZoom : REPLAY_3D_MIN_ZOOM
        cameraUpdate.zoom = Math.min(
          REPLAY_3D_TARGET_ZOOM_CAP,
          Math.max(REPLAY_3D_MIN_ZOOM, currentZoomValue + REPLAY_3D_ZOOM_STEP)
        )
      }
    }

    try {
      map.easeTo(cameraUpdate)
    } catch {
      // Camera follow is best-effort.
    }
  }

  const syncReplayMarker = ({
    visible,
    highlightedTrip,
    replayState,
    onReplayPlaying
  }) => {
    const map = getActiveMap()
    if (!map || !visible || highlightedTrip?.type !== 'trip') {
      removeReplayMarker()
      restoreReplayCameraSnapshot()
      return
    }

    const resolvedReplayState = replayState || {}
    const replayEnabled = Boolean(resolvedReplayState.enabled)
    const replay3dEnabled = Boolean(resolvedReplayState.enable3d)
    const replayIsPlaying = Boolean(resolvedReplayState.playing)
    const replayElapsedMs = toFiniteNumber(resolvedReplayState.elapsedMs) || 0
    const cursor = resolvedReplayState.cursor
    const cursorLatitude = toFiniteNumber(cursor?.latitude)
    const cursorLongitude = toFiniteNumber(cursor?.longitude)

    if (!replayEnabled || cursorLatitude === null || cursorLongitude === null) {
      removeReplayMarker()
      restoreReplayCameraSnapshot()
      return
    }

    const movementType = String(
      resolvedReplayState.movementType || highlightedTrip?.movementType || 'UNKNOWN'
    ).toUpperCase()

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
        .addTo(map)

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
    const mapBearing = toFiniteNumber(map.getBearing?.()) || 0
    const markerRotation = (
      Boolean(resolvedReplayState.enable3d) && Boolean(resolvedReplayState.followCamera)
        ? 0
        : normalizeBearingDegrees((cursorBearing || 0) - mapBearing)
    )

    if (typeof state.replayMarkerEntry.marker.setRotation === 'function') {
      state.replayMarkerEntry.marker.setRotation(markerRotation)
    }

    if (replayIsPlaying && typeof onReplayPlaying === 'function') {
      onReplayPlaying()
    }

    const replayStarted = replayIsPlaying && !state.replayWasPlaying
    const replayRestarted = replayElapsedMs <= 1 && state.replayLastElapsedMs > 1
    const forceCameraSync = replayStarted || replayRestarted
    if (forceCameraSync) {
      state.replayLastCameraSyncMs = 0
    }

    syncReplay3dMode({
      map,
      enable3d: replay3dEnabled,
      bearing: cursorBearing !== null ? cursorBearing : null,
      force: replayStarted || replayRestarted
    })
    syncReplayCamera({
      map,
      cursor,
      followCamera: Boolean(resolvedReplayState.followCamera),
      playing: replayIsPlaying,
      enable3d: replay3dEnabled,
      force: forceCameraSync,
      forceZoomTo3dDefault: replayStarted || replayRestarted
    })

    state.replayWasPlaying = replayIsPlaying
    state.replayLastElapsedMs = replayElapsedMs
  }

  const cleanupReplay = () => {
    unregisterReplayUserCameraHandlers()
    removeReplayMarker()
    restoreReplayCameraSnapshot()
  }

  return {
    cleanupReplay,
    registerReplayUserCameraHandlers,
    syncReplayMarker,
    unregisterReplayUserCameraHandlers
  }
}
