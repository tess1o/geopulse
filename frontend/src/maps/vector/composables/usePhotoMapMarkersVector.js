import { ref } from 'vue'
import maplibregl from 'maplibre-gl'
import {
  createFeatureCollection,
  ensureClusterSource,
  ensureLayer,
  isMapLibreMap,
  nextLayerToken,
  removeLayers,
  removeSources,
  setLayerVisibility
} from '@/maps/vector/utils/maplibreLayerUtils'
import {
  buildPhotoGroupsFromPhotos,
  buildPhotoMarkerClickPayload,
  getPhotoMarkerCount,
  normalizePhotoMarkerGroups
} from '@/maps/shared/photoMarkerGroups'

const PHOTO_CLUSTER_RADIUS = 48
const PHOTO_CLUSTER_MAX_ZOOM = 16
const PHOTO_CLUSTER_LABEL_MIN_ZOOM = 4

function createFocusedMarkerElement() {
  const wrapper = document.createElement('div')
  wrapper.className = 'gp-photo-map-focused-marker-wrapper'

  const marker = document.createElement('div')
  marker.className = 'gp-photo-map-focused-marker'
  marker.innerHTML = '<i class="pi pi-camera"></i>'

  wrapper.appendChild(marker)
  return wrapper
}

export function usePhotoMapMarkersVector({ emit } = {}) {
  const focusMarker = ref(null)
  const state = {
    token: nextLayerToken('gp-immich-photos'),
    sourceId: '',
    clusterLayerId: '',
    clusterIconLayerId: '',
    clusterCountLayerId: '',
    pointLayerId: '',
    pointIconLayerId: '',
    pointCountLayerId: '',
    cameraImageId: '',
    listeners: [],
    styleLoadHandler: null,
    boundMap: null,
    groups: [],
    groupsByFeatureId: new Map(),
    visible: true
  }

  state.sourceId = `${state.token}-source`
  state.clusterLayerId = `${state.token}-cluster`
  state.clusterIconLayerId = `${state.token}-cluster-icon`
  state.clusterCountLayerId = `${state.token}-cluster-count`
  state.pointLayerId = `${state.token}-points`
  state.pointIconLayerId = `${state.token}-point-icon`
  state.pointCountLayerId = `${state.token}-point-count`
  state.cameraImageId = `${state.token}-camera`

  const emitPhotoClick = (payload) => {
    if (typeof emit === 'function') {
      emit('photo-click', payload)
    }
  }

  const clearPhotoMarkers = () => {
    unregisterEvents()

    if (state.boundMap && state.styleLoadHandler) {
      state.boundMap.off('style.load', state.styleLoadHandler)
      state.styleLoadHandler = null
    }

    const targetMap = state.boundMap
    if (isMapLibreMap(targetMap)) {
      removeLayers(targetMap, [
        state.pointCountLayerId,
        state.pointIconLayerId,
        state.pointLayerId,
        state.clusterCountLayerId,
        state.clusterIconLayerId,
        state.clusterLayerId
      ])
      removeSources(targetMap, [state.sourceId])
      if (typeof targetMap.hasImage === 'function' && targetMap.hasImage(state.cameraImageId)) {
        targetMap.removeImage(state.cameraImageId)
      }
    }

    state.boundMap = null
    state.groups = []
    state.groupsByFeatureId = new Map()
  }

  const clearFocusMarker = () => {
    if (focusMarker.value) {
      focusMarker.value.remove()
      focusMarker.value = null
    }
  }

  const unregisterEvents = () => {
    if (!isMapLibreMap(state.boundMap)) {
      state.listeners = []
      return
    }

    state.listeners.forEach(({ event, layerId, handler }) => {
      if (state.boundMap.getLayer(layerId)) {
        state.boundMap.off(event, layerId, handler)
      }
    })

    state.listeners = []
    state.boundMap.getCanvas().style.cursor = ''
  }

  const emitGroupClick = (group) => {
    emitPhotoClick(buildPhotoMarkerClickPayload(group))
  }

  const buildCollection = (groups) => {
    state.groupsByFeatureId = new Map()
    const features = groups.map((group, index) => {
      const featureId = `photo-${index}`
      const count = getPhotoMarkerCount(group)
      state.groupsByFeatureId.set(featureId, group)

      return {
        type: 'Feature',
        geometry: {
          type: 'Point',
          coordinates: [group.longitude, group.latitude]
        },
        properties: {
          featureId,
          markerKey: group.markerKey || `${group.latitude},${group.longitude}`,
          photoCount: count
        }
      }
    })

    return createFeatureCollection(features)
  }

  const createCameraImage = () => {
    const size = 64
    const canvas = document.createElement('canvas')
    canvas.width = size
    canvas.height = size
    const ctx = canvas.getContext('2d')

    ctx.fillStyle = '#ffffff'
    ctx.fillRect(14, 22, 36, 26)
    ctx.fillRect(22, 16, 16, 8)
    ctx.fillRect(40, 18, 8, 6)

    ctx.fillStyle = '#2563eb'
    ctx.beginPath()
    ctx.arc(32, 35, 9, 0, Math.PI * 2)
    ctx.fill()

    ctx.fillStyle = '#ffffff'
    ctx.beginPath()
    ctx.arc(32, 35, 5, 0, Math.PI * 2)
    ctx.fill()

    return canvas
  }

  const ensureCameraImage = (mapInstance) => {
    if (typeof mapInstance.hasImage !== 'function' || typeof mapInstance.addImage !== 'function') {
      return
    }

    if (!mapInstance.hasImage(state.cameraImageId)) {
      mapInstance.addImage(state.cameraImageId, createCameraImage(), { pixelRatio: 2 })
    }
  }

  const registerEvents = (mapInstance) => {
    if (!isMapLibreMap(mapInstance)) {
      return
    }

    const handleClusterClick = (event) => {
      const clusterId = event?.features?.[0]?.properties?.cluster_id
      if (clusterId === undefined || clusterId === null) {
        return
      }

      const source = mapInstance.getSource(state.sourceId)
      if (!source || typeof source.getClusterExpansionZoom !== 'function') {
        return
      }

      source.getClusterExpansionZoom(clusterId, (error, zoom) => {
        if (error) {
          return
        }

        const center = event?.features?.[0]?.geometry?.coordinates
        if (Array.isArray(center)) {
          mapInstance.easeTo({ center, zoom, duration: 280 })
        }
      })
    }

    const handlePointClick = (event) => {
      const featureId = event?.features?.[0]?.properties?.featureId
      const group = state.groupsByFeatureId.get(featureId)
      if (group) {
        emitGroupClick(group)
      }
    }

    const handleHover = () => {
      mapInstance.getCanvas().style.cursor = 'pointer'
    }

    const handleLeave = () => {
      mapInstance.getCanvas().style.cursor = ''
    }

    mapInstance.on('click', state.clusterLayerId, handleClusterClick)
    mapInstance.on('click', state.pointLayerId, handlePointClick)
    mapInstance.on('mousemove', state.clusterLayerId, handleHover)
    mapInstance.on('mousemove', state.pointLayerId, handleHover)
    mapInstance.on('mouseleave', state.clusterLayerId, handleLeave)
    mapInstance.on('mouseleave', state.pointLayerId, handleLeave)

    state.listeners = [
      { event: 'click', layerId: state.clusterLayerId, handler: handleClusterClick },
      { event: 'click', layerId: state.pointLayerId, handler: handlePointClick },
      { event: 'mousemove', layerId: state.clusterLayerId, handler: handleHover },
      { event: 'mousemove', layerId: state.pointLayerId, handler: handleHover },
      { event: 'mouseleave', layerId: state.clusterLayerId, handler: handleLeave },
      { event: 'mouseleave', layerId: state.pointLayerId, handler: handleLeave }
    ]
  }

  const renderLayers = (mapInstance) => {
    if (!isMapLibreMap(mapInstance)) {
      return
    }

    ensureClusterSource(mapInstance, state.sourceId, buildCollection(state.groups), {
      cluster: true,
      clusterRadius: PHOTO_CLUSTER_RADIUS,
      clusterMaxZoom: PHOTO_CLUSTER_MAX_ZOOM,
      clusterProperties: {
        photo_count: ['+', ['get', 'photoCount']]
      }
    })
    ensureCameraImage(mapInstance)

    ensureLayer(mapInstance, {
      id: state.clusterLayerId,
      type: 'circle',
      source: state.sourceId,
      filter: ['has', 'point_count'],
      paint: {
        'circle-color': '#2563eb',
        'circle-radius': ['step', ['coalesce', ['get', 'photo_count'], ['get', 'point_count']], 18, 10, 21, 50, 24],
        'circle-stroke-color': '#ffffff',
        'circle-stroke-width': 2,
        'circle-opacity': 0.95
      }
    })

    ensureLayer(mapInstance, {
      id: state.clusterIconLayerId,
      type: 'symbol',
      source: state.sourceId,
      filter: ['has', 'point_count'],
      layout: {
        'icon-image': state.cameraImageId,
        'icon-size': 0.34,
        'icon-offset': [0, -5],
        'icon-allow-overlap': true
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
          PHOTO_CLUSTER_LABEL_MIN_ZOOM,
          ['to-string', ['coalesce', ['get', 'photo_count'], ['get', 'point_count']]]
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
        'circle-color': '#2563eb',
        'circle-radius': 13,
        'circle-stroke-color': '#ffffff',
        'circle-stroke-width': 2,
        'circle-opacity': 0.95
      }
    })

    ensureLayer(mapInstance, {
      id: state.pointIconLayerId,
      type: 'symbol',
      source: state.sourceId,
      filter: ['!', ['has', 'point_count']],
      layout: {
        'icon-image': state.cameraImageId,
        'icon-size': 0.32,
        'icon-allow-overlap': true
      }
    })

    ensureLayer(mapInstance, {
      id: state.pointCountLayerId,
      type: 'symbol',
      source: state.sourceId,
      filter: ['all', ['!', ['has', 'point_count']], ['>', ['get', 'photoCount'], 1]],
      layout: {
        'text-field': ['to-string', ['get', 'photoCount']],
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

    setLayerVisibility(mapInstance, [
      state.clusterLayerId,
      state.clusterIconLayerId,
      state.clusterCountLayerId,
      state.pointLayerId,
      state.pointIconLayerId,
      state.pointCountLayerId
    ], state.visible)

    unregisterEvents()
    registerEvents(mapInstance)
  }

  const renderGroups = (mapInstance, groups = []) => {
    if (!isMapLibreMap(mapInstance)) {
      return []
    }

    if (state.boundMap && state.boundMap !== mapInstance) {
      clearPhotoMarkers()
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

  const renderPhotoMarkers = (mapInstance, photos = []) => {
    return renderGroups(mapInstance, buildPhotoGroupsFromPhotos(photos))
  }

  const renderPhotoMarkerGroups = (mapInstance, markerGroups = []) => {
    return renderGroups(mapInstance, normalizePhotoMarkerGroups(markerGroups))
  }

  const focusOnCoordinates = (mapInstance, latitude, longitude, zoom = 16) => {
    if (!mapInstance || typeof latitude !== 'number' || typeof longitude !== 'number') {
      return
    }

    clearFocusMarker()

    focusMarker.value = new maplibregl.Marker({
      element: createFocusedMarkerElement(),
      anchor: 'bottom'
    })
      .setLngLat([longitude, latitude])
      .addTo(mapInstance)

    const currentZoom = mapInstance.getZoom?.() ?? 0
    const targetZoom = Math.max(currentZoom, zoom)
    mapInstance.easeTo({ center: [longitude, latitude], zoom: targetZoom, duration: 300 })
  }

  const focusOnPhoto = (mapInstance, photo, zoom = 16) => {
    if (!mapInstance || typeof photo?.latitude !== 'number' || typeof photo?.longitude !== 'number') {
      return
    }

    clearFocusMarker()

    const element = createFocusedMarkerElement()
    element.addEventListener('click', () => {
      emitPhotoClick({
        photos: [photo],
        indices: [0],
        initialIndex: 0
      })
    })

    focusMarker.value = new maplibregl.Marker({
      element,
      anchor: 'bottom'
    })
      .setLngLat([photo.longitude, photo.latitude])
      .addTo(mapInstance)

    const currentZoom = mapInstance.getZoom?.() ?? 0
    const targetZoom = Math.max(currentZoom, zoom)
    mapInstance.easeTo({ center: [photo.longitude, photo.latitude], zoom: targetZoom, duration: 300 })
  }

  return {
    clearPhotoMarkers,
    clearFocusMarker,
    renderPhotoMarkers,
    renderPhotoMarkerGroups,
    focusOnCoordinates,
    focusOnPhoto
  }
}
