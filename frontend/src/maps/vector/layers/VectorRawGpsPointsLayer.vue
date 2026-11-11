<template></template>

<script setup>
import { onBeforeUnmount, watch } from 'vue'
import * as maplibregl from 'maplibre-gl'
import { useTimezone } from '@/composables/useTimezone'
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
  createRawGpsPopupMount,
  createRawGpsInspectionClusters,
  groupRawGpsPoints,
  formatRawGpsDateTime,
  RAW_GPS_POPUP_CLASS_NAME,
  RAW_GPS_POPUP_MAX_WIDTH,
  RAW_GPS_POPUP_OFFSET
} from '@/maps/shared/rawGpsPointInspector'

const props = defineProps({
  map: {
    type: Object,
    required: true
  },
  points: {
    type: Array,
    default: () => []
  },
  visible: {
    type: Boolean,
    default: true
  },
  inspectorContainer: { type: Object, default: null },
  selectPoint: { type: Function, default: null },
  resolveLocation: {
    type: Function,
    default: null
  }
})

const timezone = useTimezone()

const state = {
  token: nextLayerToken('gp-raw-gps'),
  sourceId: '',
  clusterLayerId: '',
  clusterCountLayerId: '',
  pointLayerId: '',
  stackLayerId: '',
  stackCountLayerId: '',
  listeners: [],
  popup: null,
  popupMount: null,
  inspectionMarkers: [],
  inspectedGroup: null,
  inspectionZoomHandler: null,
  styleLoadHandler: null,
  boundMap: null
}

state.sourceId = `${state.token}-source`
state.clusterLayerId = `${state.token}-cluster`
state.clusterCountLayerId = `${state.token}-cluster-count`
state.pointLayerId = `${state.token}-points`
state.stackLayerId = `${state.token}-stacks`
state.stackCountLayerId = `${state.token}-stack-count`

const buildCollection = () => {
  const features = groupRawGpsPoints(props.points)
    .filter(group => group.key !== state.inspectedGroup?.key)
    .map((group) => ({
      type: 'Feature',
      geometry: {
        type: 'Point',
        coordinates: [group.longitude, group.latitude]
      },
      properties: {
        groupKey: group.key,
        count: group.count,
        groupRaw: JSON.stringify(group)
      }
    }))

  return createFeatureCollection(features)
}

const parseGroup = (event) => {
  const raw = event?.features?.[0]?.properties?.groupRaw
  if (!raw) return null

  try {
    return JSON.parse(raw)
  } catch {
    return null
  }
}

const closePopup = () => {
  if (state.popup) {
    state.popup.remove()
    state.popup = null
  }
  if (state.popupMount) {
    state.popupMount.unmount()
    state.popupMount = null
  }
}

const clearInspection = () => {
  closePopup()
  if (state.inspectionZoomHandler) (state.boundMap || props.map).off('zoomend', state.inspectionZoomHandler)
  state.inspectionZoomHandler = null
  state.inspectionMarkers.forEach(marker => marker.remove())
  state.inspectionMarkers = []
  if (state.inspectedGroup) {
    state.inspectedGroup = null
    getMapLibreSource(state.boundMap || props.map, state.sourceId)?.setData(buildCollection())
  }
}

const getPopupAnchor = (coordinates) => {
  if (!isMapLibreMap(props.map)) return 'bottom'

  const point = props.map.project(coordinates)
  const container = props.map.getContainer?.()
  const width = container?.clientWidth || props.map.getCanvas()?.clientWidth || 0
  const height = container?.clientHeight || props.map.getCanvas()?.clientHeight || 0

  const verticalAnchor = point.y > height / 2 ? 'bottom' : 'top'
  const horizontalAnchor = point.x < 240 ? 'left' : point.x > width - 380 ? 'right' : ''

  return horizontalAnchor ? `${verticalAnchor}-${horizontalAnchor}` : verticalAnchor
}

const openGroupPopup = (group, pointIndex = 0) => {
  if (!isMapLibreMap(props.map) || !group) return
  const coordinates = [group.longitude, group.latitude]
  clearInspection()
  state.inspectedGroup = group
  getMapLibreSource(props.map, state.sourceId)?.setData(buildCollection())
  let selectedIndex = pointIndex
  const inspectionClusters = createRawGpsInspectionClusters()
  const renderSamples = () => {
    state.inspectionMarkers.forEach(marker => marker.remove())
    state.inspectionMarkers = inspectionClusters.getRenderedEntities(group.points, props.map.getZoom()).map(entity => {
      const element = document.createElement('button')
      element.type = 'button'
      element.className = `raw-gps-inspection-marker ${entity.isCluster ? 'raw-gps-inspection-cluster' : 'raw-gps-inspection-dot'}`
      element.textContent = entity.isCluster ? String(entity.indices.length) : ''
      element.title = entity.isCluster ? String(entity.indices.length) : formatRawGpsDateTime(timezone, group.points[entity.indices[0]].timestamp)
      element.setAttribute('aria-label', element.title)
      element.classList.toggle('is-selected', entity.indices.includes(selectedIndex))
      element.setAttribute('aria-pressed', String(entity.indices.includes(selectedIndex)))
      element.style.zIndex = '2'
      element.addEventListener('click', event => {
        event.stopPropagation()
        if (!state.popupMount) { openGroupPopup(group, entity.isCluster ? selectedIndex : entity.indices[0]); return }
        if (entity.isCluster) props.map.easeTo({ center: [entity.longitude, entity.latitude], zoom: Math.min(props.map.getZoom() + 2, props.map.getMaxZoom()), duration: 280 })
        else state.popupMount.inspectPoint(entity.indices[0])
      })
      element.addEventListener('dblclick', event => event.stopPropagation())
      return new maplibregl.Marker({ element }).setLngLat([entity.longitude, entity.latitude]).addTo(props.map)
    })
  }
  const highlightPoint = (_, index) => { selectedIndex = index; renderSamples() }
  let popup = null
  const popupMount = createRawGpsPopupMount(group, {
    timezone,
    container: props.inspectorContainer,
    resolveLocation: props.resolveLocation,
    onSelectPoint: props.selectPoint,
    activePointIndex: pointIndex,
    onInspectPoint: highlightPoint,
    onRender: () => {
      if (state.popup === popup && popup?.isOpen?.()) {
        popup.setLngLat(coordinates)
      }
    }
  })

  if (!props.inspectorContainer) {
    popup = new maplibregl.Popup({
      closeButton: true,
      closeOnClick: true,
      closeOnMove: false,
      maxWidth: RAW_GPS_POPUP_MAX_WIDTH,
      offset: RAW_GPS_POPUP_OFFSET,
      anchor: getPopupAnchor(coordinates),
      className: RAW_GPS_POPUP_CLASS_NAME
    })
      .setLngLat(coordinates)
      .setDOMContent(popupMount.element)
      .addTo(props.map)

    popup.on('close', () => {
      if (state.popup === popup) {
        state.popup = null
      }
      if (state.popupMount === popupMount) {
        state.popupMount = null
      }
      popupMount.unmount()
    })
  }

  state.popup = popup
  state.popupMount = popupMount
  state.inspectionZoomHandler = renderSamples
  props.map.on('zoomend', state.inspectionZoomHandler)
  renderSamples()
}

const registerEvents = () => {
  if (!isMapLibreMap(props.map)) return

  const handleClusterClick = async (event) => {
    const clusterId = event?.features?.[0]?.properties?.cluster_id
    const center = event?.features?.[0]?.geometry?.coordinates
    if (clusterId === undefined || clusterId === null || !Array.isArray(center)) {
      return
    }

    const source = getMapLibreSource(props.map, state.sourceId)
    if (!source || typeof source.getClusterExpansionZoom !== 'function') {
      return
    }

    try {
      const zoom = await source.getClusterExpansionZoom(clusterId)
      props.map.easeTo({ center, zoom, duration: 280 })
    } catch {
      // Ignore stale cluster ids while source data changes.
    }
  }

  const handlePointClick = (event) => {
    event.preventDefault()
    openGroupPopup(parseGroup(event))
  }

  const handleHover = () => {
    props.map.getCanvas().style.cursor = 'pointer'
  }

  const handleLeave = () => {
    props.map.getCanvas().style.cursor = ''
  }

  props.map.on('click', state.clusterLayerId, handleClusterClick)
  props.map.on('click', state.pointLayerId, handlePointClick)
  props.map.on('click', state.stackLayerId, handlePointClick)
  props.map.on('mousemove', state.clusterLayerId, handleHover)
  props.map.on('mousemove', state.pointLayerId, handleHover)
  props.map.on('mousemove', state.stackLayerId, handleHover)
  props.map.on('mouseleave', state.clusterLayerId, handleLeave)
  props.map.on('mouseleave', state.pointLayerId, handleLeave)
  props.map.on('mouseleave', state.stackLayerId, handleLeave)

  state.listeners = [
    { event: 'click', layerId: state.clusterLayerId, handler: handleClusterClick },
    { event: 'click', layerId: state.pointLayerId, handler: handlePointClick },
    { event: 'click', layerId: state.stackLayerId, handler: handlePointClick },
    { event: 'mousemove', layerId: state.clusterLayerId, handler: handleHover },
    { event: 'mousemove', layerId: state.pointLayerId, handler: handleHover },
    { event: 'mousemove', layerId: state.stackLayerId, handler: handleHover },
    { event: 'mouseleave', layerId: state.clusterLayerId, handler: handleLeave },
    { event: 'mouseleave', layerId: state.pointLayerId, handler: handleLeave },
    { event: 'mouseleave', layerId: state.stackLayerId, handler: handleLeave }
  ]
}

const unregisterEvents = () => {
  if (!isMapLibreMap(props.map)) {
    state.listeners = []
    return
  }

  state.listeners.forEach(({ event, layerId, handler }) => {
    if (hasMapLibreLayer(props.map, layerId)) {
      props.map.off(event, layerId, handler)
    }
  })

  state.listeners = []
  try {
    props.map.getCanvas().style.cursor = ''
  } catch {
    // MapLibre may already be tearing down during route changes.
  }
}

const renderLayer = () => {
  if (!isMapLibreMap(props.map)) return

  ensureClusterSource(props.map, state.sourceId, buildCollection(), {
    cluster: true,
    clusterRadius: 34,
    clusterMaxZoom: 14,
    clusterProperties: {
      raw_count: ['+', ['get', 'count']]
    }
  })

  ensureLayer(props.map, {
    id: state.clusterLayerId,
    type: 'circle',
    source: state.sourceId,
    filter: ['has', 'point_count'],
    paint: {
      'circle-color': '#1d4ed8',
      'circle-radius': ['step', ['coalesce', ['get', 'raw_count'], ['get', 'point_count']], 14, 100, 18, 1000, 22],
      'circle-stroke-color': '#dbeafe',
      'circle-stroke-width': 2,
      'circle-opacity': 0.92
    }
  })

  ensureLayer(props.map, {
    id: state.clusterCountLayerId,
    type: 'symbol',
    source: state.sourceId,
    filter: ['has', 'point_count'],
    layout: {
      'text-field': ['to-string', ['coalesce', ['get', 'raw_count'], ['get', 'point_count_abbreviated']]],
      'text-size': 11,
      'text-font': ['Open Sans Bold', 'Arial Unicode MS Bold']
    },
    paint: {
      'text-color': '#ffffff'
    }
  })

  ensureLayer(props.map, {
    id: state.pointLayerId,
    type: 'circle',
    source: state.sourceId,
    filter: ['all', ['!', ['has', 'point_count']], ['==', ['get', 'count'], 1]],
    paint: {
      'circle-color': '#2563eb',
      'circle-radius': ['interpolate', ['linear'], ['zoom'], 8, 3, 15, 5, 18, 6],
      'circle-stroke-color': '#eff6ff',
      'circle-stroke-width': 1.5,
      'circle-opacity': 0.95
    }
  })

  ensureLayer(props.map, {
    id: state.stackLayerId,
    type: 'circle',
    source: state.sourceId,
    filter: ['all', ['!', ['has', 'point_count']], ['>', ['get', 'count'], 1]],
    paint: {
      'circle-color': '#0f766e',
      'circle-radius': ['step', ['get', 'count'], 10, 10, 12, 100, 15],
      'circle-stroke-color': '#ccfbf1',
      'circle-stroke-width': 2,
      'circle-opacity': 0.96
    }
  })

  ensureLayer(props.map, {
    id: state.stackCountLayerId,
    type: 'symbol',
    source: state.sourceId,
    filter: ['all', ['!', ['has', 'point_count']], ['>', ['get', 'count'], 1]],
    layout: {
      'text-field': ['to-string', ['get', 'count']],
      'text-size': 10,
      'text-font': ['Open Sans Bold', 'Arial Unicode MS Bold']
    },
    paint: {
      'text-color': '#ffffff'
    }
  })

  setLayerVisibility(
    props.map,
    [
      state.clusterLayerId,
      state.clusterCountLayerId,
      state.pointLayerId,
      state.stackLayerId,
      state.stackCountLayerId
    ],
    props.visible
  )

  unregisterEvents()
  registerEvents()
}

const clearLayer = () => {
  unregisterEvents()
  clearInspection()

  if (state.boundMap && state.styleLoadHandler) {
    state.boundMap.off('style.load', state.styleLoadHandler)
    state.styleLoadHandler = null
  }

  const targetMap = state.boundMap || props.map
  if (!isMapLibreMap(targetMap)) {
    state.boundMap = null
    return
  }

  removeLayers(targetMap, [
    state.stackCountLayerId,
    state.stackLayerId,
    state.pointLayerId,
    state.clusterCountLayerId,
    state.clusterLayerId
  ])
  removeSources(targetMap, [state.sourceId])
  state.boundMap = null
}

watch(
  () => [props.map, props.points, props.visible],
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

watch(() => props.points, () => clearInspection(), { deep: true })
watch(() => props.visible, visible => { if (!visible) clearInspection() })
watch(() => props.selectPoint, callback => state.popupMount?.setSelectPoint(callback))

onBeforeUnmount(() => {
  clearLayer()
})
</script>
