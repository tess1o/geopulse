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
    replayLastElapsedMs: 0,
    prevReplay3dEnabled: false,
    pendingReplayEntryCameraForce: false,
    pendingReplay3dModeForce: false,
    replayStartZoom: null,
    replay3dTerrainReady: true,
    replay3dTerrainIdleListener: null
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

  const resetReplayTransitionTracking = () => {
    state.replayWasPlaying = false
    state.replayLastElapsedMs = 0
    state.replayLastCameraSyncMs = 0
    state.prevReplay3dEnabled = false
    state.pendingReplayEntryCameraForce = false
    state.pendingReplay3dModeForce = false
    state.replayStartZoom = null
    state.replay3dTerrainReady = true
  }

  const clearReplayTerrainReadyListener = () => {
    const listenerState = state.replay3dTerrainIdleListener
    if (!listenerState?.map || typeof listenerState.handler !== 'function') {
      state.replay3dTerrainIdleListener = null
      return
    }

    try {
      listenerState.map.off?.('idle', listenerState.handler)
    } catch {
      // Idle listener cleanup is best-effort.
    }
    state.replay3dTerrainIdleListener = null
  }

  const armReplayTerrainReadyListener = (map) => {
    if (!isMapLibreMap(map)) {
      state.replay3dTerrainReady = true
      clearReplayTerrainReadyListener()
      return
    }

    clearReplayTerrainReadyListener()
    state.replay3dTerrainReady = false

    const handleIdle = () => {
      state.replay3dTerrainReady = true
      if (state.replay3dTerrainIdleListener?.handler === handleIdle) {
        state.replay3dTerrainIdleListener = null
      }
    }

    state.replay3dTerrainIdleListener = { map, handler: handleIdle }
    map.once?.('idle', handleIdle)
  }

  const resolveReplayForced3dZoom = (zoomSeed) => {
    const numericZoomSeed = Number.isFinite(zoomSeed) ? Number(zoomSeed) : REPLAY_3D_MIN_ZOOM
    return Math.min(
      REPLAY_3D_TARGET_ZOOM_CAP,
      Math.max(REPLAY_3D_MIN_ZOOM, numericZoomSeed + REPLAY_3D_ZOOM_STEP)
    )
  }

  const removeReplayMarker = ({ resetReplaySession = true } = {}) => {
    if (resetReplaySession) {
      resetReplayTransitionTracking()
    }

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
        duration: 0,
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
    const result = {
      terrainJustEnabled: false,
      terrainSourceId: null
    }

    if (!isMapLibreMap(map)) {
      return result
    }

    if (!enable3d) {
      state.replay3dTerrainReady = true
      clearReplayTerrainReadyListener()
      restoreReplayCameraSnapshot()
      return result
    }

    snapshotReplayCameraIfNeeded(map)

    try {
      const terrainSourceId = resolveRasterDemSourceId(map)
      result.terrainSourceId = terrainSourceId
      if (terrainSourceId) {
        const activeTerrain = map.getTerrain?.()
        if (!activeTerrain || activeTerrain.source !== terrainSourceId) {
          map.setTerrain({ source: terrainSourceId, exaggeration: 1.2 })
          result.terrainJustEnabled = true
        }
      }
    } catch {
      // Terrain enablement is best-effort.
    }

    if (result.terrainJustEnabled) {
      armReplayTerrainReadyListener(map)
      // Defer 3D camera transforms until terrain is fully initialized.
      return result
    }
    if (!result.terrainSourceId) {
      state.replay3dTerrainReady = true
      clearReplayTerrainReadyListener()
    }

    const targetPitch = REPLAY_3D_PITCH
    const currentPitch = map.getPitch?.() || 0
    const effectiveForce = force && !result.terrainJustEnabled
    const shouldApplyPitch = Math.abs(currentPitch - targetPitch) > 0.45
    const shouldApplyBearing = effectiveForce && Number.isFinite(bearing)
    if (!shouldApplyPitch && !shouldApplyBearing) {
      state.replay3dActive = true
      return result
    }

    if (!effectiveForce && isReplayUserCameraInteracting()) {
      state.replay3dActive = true
      return result
    }

    const threeDUpdate = {
      pitch: targetPitch,
      ...(shouldApplyBearing ? { bearing } : {}),
      duration: effectiveForce ? 0 : 150,
      essential: true
    }

    try {
      if (effectiveForce) {
        const { duration: _duration, essential: _essential, ...forcedUpdate } = threeDUpdate
        map.jumpTo(forcedUpdate)
      } else {
        map.easeTo(threeDUpdate)
      }
    } catch {
      // Pitch fallback is best-effort.
    }

    state.replay3dActive = true
    return result
  }

  const syncReplayCamera = ({
    map,
    cursor,
    followCamera = true,
    playing = false,
    enable3d = false,
    force = false,
    forceZoomTo3dDefault = false,
    preferred3dEntryZoom = null,
    forceReplayEntryReason = null
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
      const zoomSeed = Number.isFinite(preferred3dEntryZoom)
        ? Number(preferred3dEntryZoom)
        : currentZoom
      if (forceZoomTo3dDefault) {
        if (forceReplayEntryReason === 'toggle3d') {
          cameraUpdate.zoom = REPLAY_3D_TARGET_ZOOM_CAP
        } else if (!Number.isFinite(zoomSeed) || zoomSeed < REPLAY_3D_MIN_ZOOM) {
          cameraUpdate.zoom = resolveReplayForced3dZoom(zoomSeed)
        }
      }
    }

    try {
      if (force) {
        const { duration: _duration, essential: _essential, ...forcedUpdate } = cameraUpdate
        map.jumpTo(forcedUpdate)
      } else {
        map.easeTo(cameraUpdate)
      }
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
      if (!replay3dEnabled || !replayIsPlaying) {
        state.pendingReplayEntryCameraForce = false
      }
      if (!replayEnabled) {
        removeReplayMarker()
        restoreReplayCameraSnapshot()
      } else {
        // Keep transition-tracking and camera state across brief cursor gaps.
        removeReplayMarker({ resetReplaySession: false })
      }
      return
    }

    if (!replay3dEnabled || !replayIsPlaying) {
      state.pendingReplayEntryCameraForce = false
      state.pendingReplay3dModeForce = false
    }

    const movementType = String(
      resolvedReplayState.movementType || highlightedTrip?.movementType || 'UNKNOWN'
    ).toUpperCase()

    if (!state.replayMarkerEntry || state.replayMarkerMovementType !== movementType) {
      removeReplayMarker({ resetReplaySession: false })

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
    const currentMapZoom = toFiniteNumber(map.getZoom?.())
    if ((replayStarted || replayRestarted) && currentMapZoom !== null) {
      state.replayStartZoom = currentMapZoom
    } else if (state.replayStartZoom === null && replayIsPlaying && currentMapZoom !== null) {
      state.replayStartZoom = currentMapZoom
    }
    const replay3dToggledOnWhilePlaying = (
      replayIsPlaying
      && replay3dEnabled
      && !state.prevReplay3dEnabled
    )
    const forceReplayEntryCameraRequested = (
      replayStarted
      || replayRestarted
      || replay3dToggledOnWhilePlaying
    )
    if (replay3dToggledOnWhilePlaying) {
      state.pendingReplayEntryCameraForce = true
      state.pendingReplay3dModeForce = true
    }
    const replay3dModeSync = syncReplay3dMode({
      map,
      enable3d: replay3dEnabled,
      bearing: cursorBearing !== null ? cursorBearing : null,
      force: forceReplayEntryCameraRequested || state.pendingReplay3dModeForce
    })
    if (state.pendingReplay3dModeForce && !replay3dModeSync.terrainJustEnabled) {
      state.pendingReplay3dModeForce = false
    }
    const terrainSourceId = replay3dModeSync.terrainSourceId
    const terrainSourceLoadState = terrainSourceId
      ? map.isSourceLoaded?.(terrainSourceId)
      : true
    const terrainSourceLoaded = terrainSourceLoadState === true
    const shouldPromoteTerrainReadyFromSourceLoad = (
      replay3dEnabled
      && !replay3dModeSync.terrainJustEnabled
      && !state.replay3dTerrainReady
      && Boolean(terrainSourceId)
      && terrainSourceLoaded
    )
    if (shouldPromoteTerrainReadyFromSourceLoad) {
      state.replay3dTerrainReady = true
      clearReplayTerrainReadyListener()
    }
    const terrainReadyForEntryForce = (
      !terrainSourceId
      || terrainSourceLoadState !== false
    )
    const replay3dCameraReady = (
      replay3dEnabled
      && terrainReadyForEntryForce
      && !replay3dModeSync.terrainJustEnabled
      && state.replay3dTerrainReady
    )
    const applyPendingReplayEntryForce = (
      state.pendingReplayEntryCameraForce
      && replayIsPlaying
      && replay3dCameraReady
    )
    const forceReplayEntryCamera = (
      replayStarted
      || replayRestarted
      || (replay3dToggledOnWhilePlaying && replay3dCameraReady)
      || applyPendingReplayEntryForce
    )
    const forceReplayEntryReason = replayStarted
      ? 'start'
      : replayRestarted
        ? 'restart'
        : forceReplayEntryCamera
          ? 'toggle3d'
          : null
    if (forceReplayEntryCamera) {
      state.replayLastCameraSyncMs = 0
      state.pendingReplayEntryCameraForce = false
    }

    syncReplayCamera({
      map,
      cursor,
      followCamera: Boolean(resolvedReplayState.followCamera),
      playing: replayIsPlaying,
      enable3d: replay3dCameraReady,
      force: forceReplayEntryCamera,
      forceZoomTo3dDefault: forceReplayEntryCamera,
      preferred3dEntryZoom: state.replayStartZoom,
      forceReplayEntryReason
    })

    state.replayWasPlaying = replayIsPlaying
    state.replayLastElapsedMs = replayElapsedMs
    state.prevReplay3dEnabled = replay3dEnabled
  }

  const cleanupReplay = () => {
    unregisterReplayUserCameraHandlers()
    clearReplayTerrainReadyListener()
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
