import {
  createFeatureCollection,
  ensureClusterSource,
  ensureLayer,
  getMapLibreSource,
  hasMapLibreLayer,
  isMapLibreMap,
  nextLayerToken,
  removeLayers,
  removeSources,
  setLayerVisibility
} from '@/maps/vector/utils/maplibreLayerUtils'
import {
  applyGroupExclusionFilters,
  createNativeClusterMirror,
  MIN_GROUP_INDEX_CLUSTER_PROPERTY
} from '@/maps/vector/utils/nativeClusterMirror'
import { NOTE_MARKER_COLOR } from '@/maps/shared/mapColors'

const NOTE_CLUSTER_RADIUS = 48
const NOTE_CLUSTER_MAX_ZOOM = 16
const NOTE_CLUSTER_LABEL_MIN_ZOOM = 4
const NOTE_CLUSTER_EVENT_HANDLED_KEY = '__gpNoteClusterHandled'
const NOTE_CLUSTER_DOUBLE_CLICK_EVENT_HANDLED_KEY = '__gpNoteClusterDoubleClickHandled'
const NOTE_POINT_EVENT_HANDLED_KEY = '__gpNotePointHandled'

/**
 * MapLibre note-marker clustering, mirroring usePhotoMapMarkersVector.js's
 * cluster-source/point-source pattern so Notes get the same always-on,
 * zoom-aware clustering behavior Photos already have.
 */
export function useNoteMapMarkersVector({ onGroupClick } = {}) {
  const state = {
    token: nextLayerToken('gp-notes'),
    sourceId: '',
    clusterLayerId: '',
    clusterCountLayerId: '',
    pointLayerId: '',
    pointIconLayerId: '',
    pointCountLayerId: '',
    noteImageId: '',
    listeners: [],
    styleLoadHandler: null,
    boundMap: null,
    groups: [],
    excludedIndices: new Set(),
    groupsByFeatureId: new Map(),
    visible: true
  }

  state.sourceId = `${state.token}-source`
  state.clusterLayerId = `${state.token}-cluster`
  state.clusterCountLayerId = `${state.token}-cluster-count`
  state.pointLayerId = `${state.token}-points`
  state.pointIconLayerId = `${state.token}-point-icon`
  state.pointCountLayerId = `${state.token}-point-count`
  state.noteImageId = `${state.token}-icon`

  const safeHasImage = (mapInstance, imageId) => {
    if (!imageId || typeof mapInstance?.hasImage !== 'function') {
      return false
    }
    try {
      return Boolean(mapInstance.hasImage(imageId))
    } catch {
      return false
    }
  }

  const safeAddImage = (mapInstance, imageId, imageData) => {
    if (!imageId || !imageData || state.boundMap !== mapInstance || typeof mapInstance?.addImage !== 'function') {
      return false
    }
    try {
      mapInstance.addImage(imageId, imageData, { pixelRatio: 2 })
      return true
    } catch {
      return false
    }
  }

  const safeRemoveImage = (mapInstance, imageId) => {
    if (!safeHasImage(mapInstance, imageId) || typeof mapInstance?.removeImage !== 'function') {
      return
    }
    try {
      mapInstance.removeImage(imageId)
    } catch {
      // MapLibre may drop its image registry while Vue is unmounting layers.
    }
  }

  const createNoteImage = () => {
    const size = 64
    const canvas = document.createElement('canvas')
    canvas.width = size
    canvas.height = size
    const ctx = canvas.getContext('2d')
    if (!ctx) {
      return null
    }

    ctx.fillStyle = NOTE_MARKER_COLOR
    ctx.beginPath()
    ctx.arc(32, 32, 22, 0, Math.PI * 2)
    ctx.fill()

    ctx.strokeStyle = '#ffffff'
    ctx.lineWidth = 3
    ctx.beginPath()
    ctx.moveTo(22, 22)
    ctx.lineTo(42, 22)
    ctx.moveTo(22, 32)
    ctx.lineTo(42, 32)
    ctx.moveTo(22, 42)
    ctx.lineTo(36, 42)
    ctx.stroke()

    return ctx.getImageData(0, 0, size, size)
  }

  const ensureNoteImage = (mapInstance) => {
    if (typeof mapInstance?.addImage !== 'function') {
      return false
    }

    if (!safeHasImage(mapInstance, state.noteImageId)) {
      const imageData = createNoteImage()
      if (!imageData) {
        return false
      }
      safeAddImage(mapInstance, state.noteImageId, imageData)
    }

    return safeHasImage(mapInstance, state.noteImageId)
  }

  const unregisterEvents = () => {
    if (!isMapLibreMap(state.boundMap)) {
      state.listeners = []
      return
    }

    state.listeners.forEach(({ event, layerId, handler }) => {
      if (hasMapLibreLayer(state.boundMap, layerId)) {
        state.boundMap.off(event, layerId, handler)
      }
    })

    state.listeners = []
    try {
      state.boundMap.getCanvas().style.cursor = ''
    } catch {
      // MapLibre may already be tearing down during route changes.
    }
  }

  const clearNoteMarkers = () => {
    const targetMap = state.boundMap

    unregisterEvents()

    if (targetMap && state.styleLoadHandler) {
      try {
        targetMap.off('style.load', state.styleLoadHandler)
      } catch {
        // MapLibre may already be tearing down.
      }
      state.styleLoadHandler = null
    }

    state.boundMap = null

    if (isMapLibreMap(targetMap)) {
      removeLayers(targetMap, [
        state.pointCountLayerId,
        state.pointIconLayerId,
        state.pointLayerId,
        state.clusterCountLayerId,
        state.clusterLayerId
      ])
      removeSources(targetMap, [state.sourceId])
      safeRemoveImage(targetMap, state.noteImageId)
    }

    state.groups = []
    state.groupsByFeatureId = new Map()
  }

  const buildCollection = (groups) => {
    state.groupsByFeatureId = new Map()
    const features = groups.map((group, index) => {
      const featureId = `note-${index}`
      const count = Math.max(Number(group?.notes?.length || 0), 1)
      state.groupsByFeatureId.set(featureId, group)

      return {
        type: 'Feature',
        geometry: {
          type: 'Point',
          coordinates: [group.longitude, group.latitude]
        },
        properties: {
          featureId,
          groupIndex: index,
          noteCount: count
        }
      }
    })

    return createFeatureCollection(features)
  }

  const registerEvents = (mapInstance) => {
    if (!isMapLibreMap(mapInstance)) {
      return
    }

    const clusterLayerIds = [state.clusterLayerId, state.clusterCountLayerId]
      .filter((layerId) => hasMapLibreLayer(mapInstance, layerId))
    const pointLayerIds = [state.pointLayerId, state.pointIconLayerId, state.pointCountLayerId]
      .filter((layerId) => hasMapLibreLayer(mapInstance, layerId))

    const findFeatureFromEvent = (event, layerIds, predicate = () => true) => {
      const eventFeature = event?.features?.find?.(predicate)
      if (eventFeature) {
        return eventFeature
      }

      if (!event?.point || layerIds.length === 0 || typeof mapInstance.queryRenderedFeatures !== 'function') {
        return null
      }

      return mapInstance.queryRenderedFeatures(event.point, { layers: layerIds }).find(predicate) || null
    }

    const expandCluster = async (event) => {
      const clusterFeature = findFeatureFromEvent(
        event,
        clusterLayerIds,
        (feature) => feature?.properties?.cluster_id !== undefined && feature?.properties?.cluster_id !== null
      )
      const clusterId = clusterFeature?.properties?.cluster_id
      if (clusterId === undefined || clusterId === null) {
        return
      }

      const source = getMapLibreSource(mapInstance, state.sourceId)
      if (!source || typeof source.getClusterExpansionZoom !== 'function') {
        return
      }

      try {
        const expansionZoom = await source.getClusterExpansionZoom(clusterId)
        const currentZoom = mapInstance.getZoom?.() ?? 0
        const zoom = Math.max(expansionZoom, Math.floor(currentZoom) + 1)
        const center = clusterFeature?.geometry?.coordinates
        if (Array.isArray(center)) {
          mapInstance.easeTo({ center, zoom, duration: 280 })
        }
      } catch {
        // Ignore stale cluster ids while source data is refreshing.
      }
    }

    const isAlreadyHandled = (event, key) => {
      const originalEvent = event?.originalEvent
      if (!originalEvent) {
        return false
      }
      if (originalEvent[key]) {
        return true
      }
      originalEvent[key] = true
      return false
    }

    const handleClusterClick = (event) => {
      if (isAlreadyHandled(event, NOTE_CLUSTER_EVENT_HANDLED_KEY)) {
        return
      }
      expandCluster(event)
    }

    const handleClusterDoubleClick = (event) => {
      event?.preventDefault?.()
      event?.originalEvent?.preventDefault?.()
      event?.originalEvent?.stopPropagation?.()
      if (isAlreadyHandled(event, NOTE_CLUSTER_DOUBLE_CLICK_EVENT_HANDLED_KEY)) {
        return
      }
      expandCluster(event)
    }

    const handlePointClick = (event) => {
      if (isAlreadyHandled(event, NOTE_POINT_EVENT_HANDLED_KEY)) {
        return
      }

      event?.originalEvent?.stopPropagation?.()

      const pointFeature = findFeatureFromEvent(event, pointLayerIds, (feature) => feature?.properties?.featureId)
      const featureId = pointFeature?.properties?.featureId
      const group = state.groupsByFeatureId.get(featureId)
      if (group && typeof onGroupClick === 'function') {
        onGroupClick(group)
      }
    }

    const handleHover = () => {
      mapInstance.getCanvas().style.cursor = 'pointer'
    }

    const handleLeave = () => {
      mapInstance.getCanvas().style.cursor = ''
    }

    state.listeners = []

    clusterLayerIds.forEach((layerId) => {
      mapInstance.on('click', layerId, handleClusterClick)
      mapInstance.on('dblclick', layerId, handleClusterDoubleClick)
      mapInstance.on('mousemove', layerId, handleHover)
      mapInstance.on('mouseleave', layerId, handleLeave)
      state.listeners.push(
        { event: 'click', layerId, handler: handleClusterClick },
        { event: 'dblclick', layerId, handler: handleClusterDoubleClick },
        { event: 'mousemove', layerId, handler: handleHover },
        { event: 'mouseleave', layerId, handler: handleLeave }
      )
    })

    pointLayerIds.forEach((layerId) => {
      mapInstance.on('click', layerId, handlePointClick)
      mapInstance.on('mousemove', layerId, handleHover)
      mapInstance.on('mouseleave', layerId, handleLeave)
      state.listeners.push(
        { event: 'click', layerId, handler: handlePointClick },
        { event: 'mousemove', layerId, handler: handleHover },
        { event: 'mouseleave', layerId, handler: handleLeave }
      )
    })
  }

  const applyExclusionFilters = (mapInstance) => {
    const clusterFilter = ['has', 'point_count']
    const pointFilter = ['!', ['has', 'point_count']]
    applyGroupExclusionFilters(mapInstance, {
      [state.clusterLayerId]: clusterFilter,
      [state.clusterCountLayerId]: clusterFilter,
      [state.pointLayerId]: pointFilter,
      [state.pointIconLayerId]: pointFilter,
      [state.pointCountLayerId]: ['all', pointFilter, ['>', ['get', 'noteCount'], 1]]
    }, state.excludedIndices)
  }

  const renderLayers = (mapInstance) => {
    if (!isMapLibreMap(mapInstance)) {
      return
    }

    ensureClusterSource(mapInstance, state.sourceId, buildCollection(state.groups), {
      cluster: true,
      clusterRadius: NOTE_CLUSTER_RADIUS,
      clusterMaxZoom: NOTE_CLUSTER_MAX_ZOOM,
      clusterProperties: {
        note_count: ['+', ['get', 'noteCount']],
        ...MIN_GROUP_INDEX_CLUSTER_PROPERTY
      }
    })

    const hasNoteImage = ensureNoteImage(mapInstance)

    ensureLayer(mapInstance, {
      id: state.clusterLayerId,
      type: 'circle',
      source: state.sourceId,
      filter: ['has', 'point_count'],
      paint: {
        'circle-color': NOTE_MARKER_COLOR,
        'circle-radius': ['step', ['coalesce', ['get', 'note_count'], ['get', 'point_count']], 18, 10, 21, 50, 24],
        'circle-stroke-color': '#ffffff',
        'circle-stroke-width': 2,
        'circle-opacity': 0.95
      }
    })

    ensureLayer(mapInstance, {
      id: state.clusterCountLayerId,
      type: 'symbol',
      source: state.sourceId,
      filter: ['has', 'point_count'],
      layout: {
        'text-field': [
          'step',
          ['zoom'],
          '',
          NOTE_CLUSTER_LABEL_MIN_ZOOM,
          ['to-string', ['coalesce', ['get', 'note_count'], ['get', 'point_count']]]
        ],
        'text-size': 11,
        'text-font': ['Open Sans Bold', 'Arial Unicode MS Bold'],
        'text-offset': [0, 0.55]
      },
      paint: {
        'text-color': '#ffffff'
      }
    })

    ensureLayer(mapInstance, {
      id: state.pointLayerId,
      type: 'circle',
      source: state.sourceId,
      filter: ['!', ['has', 'point_count']],
      paint: {
        'circle-color': NOTE_MARKER_COLOR,
        'circle-radius': 13,
        'circle-stroke-color': '#ffffff',
        'circle-stroke-width': 2,
        // The circle is only a fallback; hide its stroke too when the icon image is used.
        'circle-stroke-opacity': hasNoteImage ? 0 : 1,
        'circle-opacity': hasNoteImage ? 0 : 0.95
      }
    })

    if (hasNoteImage) {
      ensureLayer(mapInstance, {
        id: state.pointIconLayerId,
        type: 'symbol',
        source: state.sourceId,
        filter: ['!', ['has', 'point_count']],
        layout: {
          'icon-image': state.noteImageId,
          // 64px canvas at pixelRatio 2 with a 44px circle: 1.2 renders a ~26px marker.
          'icon-size': 1.2,
          'icon-allow-overlap': true
        }
      })
    }

    ensureLayer(mapInstance, {
      id: state.pointCountLayerId,
      type: 'symbol',
      source: state.sourceId,
      filter: ['all', ['!', ['has', 'point_count']], ['>', ['get', 'noteCount'], 1]],
      layout: {
        'text-field': ['to-string', ['get', 'noteCount']],
        'text-size': 10,
        'text-font': ['Open Sans Bold', 'Arial Unicode MS Bold'],
        'text-offset': [0.9, -0.9]
      },
      paint: {
        'text-color': '#ffffff',
        'text-halo-color': '#0f172a',
        'text-halo-width': 1.5
      }
    })

    const layerIds = [state.clusterLayerId, state.clusterCountLayerId, state.pointLayerId, state.pointCountLayerId]
    if (hasNoteImage) {
      layerIds.push(state.pointIconLayerId)
    }

    setLayerVisibility(mapInstance, layerIds, state.visible)
    applyExclusionFilters(mapInstance)

    unregisterEvents()
    registerEvents(mapInstance)
  }

  const renderNoteGroups = (mapInstance, groups = []) => {
    if (!isMapLibreMap(mapInstance)) {
      return []
    }

    if (state.boundMap && state.boundMap !== mapInstance) {
      clearNoteMarkers()
    }

    state.boundMap = mapInstance
    state.groups = groups

    if (!state.styleLoadHandler) {
      state.styleLoadHandler = () => renderLayers(mapInstance)
      mapInstance.on('style.load', state.styleLoadHandler)
    }

    renderLayers(mapInstance)
    return groups
  }

  // Synchronous access to the pre-cluster groups (for the cross-type overlap pass).
  const getCurrentGroups = () => state.groups

  // Hides groups (by index into getCurrentGroups()) that a cross-type combo marker now represents.
  const setExcludedGroupIndices = (indices) => {
    const next = new Set(indices || [])
    if (next.size === state.excludedIndices.size && [...next].every((index) => state.excludedIndices.has(index))) {
      return
    }

    state.excludedIndices = next
    if (isMapLibreMap(state.boundMap)) {
      applyExclusionFilters(state.boundMap)
    }
  }

  const clusterMirror = createNativeClusterMirror({
    clusterRadius: NOTE_CLUSTER_RADIUS,
    clusterMaxZoom: NOTE_CLUSTER_MAX_ZOOM
  })

  // Synchronous replica of what the native clustered source shows right now
  // (standalone groups and clusters), for the cross-type overlap pass.
  const getRenderedEntities = () => (
    isMapLibreMap(state.boundMap)
      ? clusterMirror.getRenderedEntities(state.groups, state.boundMap.getZoom())
      : []
  )

  return {
    clearNoteMarkers,
    renderNoteGroups,
    getCurrentGroups,
    getRenderedEntities,
    setExcludedGroupIndices
  }
}
