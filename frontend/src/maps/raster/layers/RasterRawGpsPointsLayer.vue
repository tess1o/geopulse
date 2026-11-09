<template>
  <BaseLayer
    ref="baseLayerRef"
    :map="map"
    :visible="visible"
    @layer-ready="handleLayerReady"
  />
</template>

<script setup>
import { onBeforeUnmount, ref, watch } from 'vue'
import L from 'leaflet'
import 'leaflet.markercluster'
import '@/styles/vendor/leaflet-plugins.css'
import BaseLayer from '@/components/maps/layers/BaseLayer.vue'
import { useTimezone } from '@/composables/useTimezone'
import {
  createRawGpsPopupMount,
  createRawGpsInspectionClusters,
  groupRawGpsPoints,
  formatRawGpsDateTime,
  RAW_GPS_POPUP_CLASS_NAME,
  RAW_GPS_POPUP_MAX_WIDTH_PX
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

const baseLayerRef = ref(null)
const markerClusterGroup = ref(null)
const markers = []
const timezone = useTimezone()
let popup = null, popupMount = null, expandedMarker = null, inspectionZoomHandler = null, inspectionMap = null
const inspectionMarkers = []
const inspectionClusters = createRawGpsInspectionClusters()

const closePopup = () => {
  popup?.remove()
  popupMount?.unmount()
  popup = null
  popupMount = null
}

const clearInspection = (restoreOverview = true) => {
  closePopup()
  if (inspectionZoomHandler) inspectionMap.off('zoomend', inspectionZoomHandler)
  inspectionZoomHandler = null
  inspectionMap = null
  inspectionMarkers.forEach(marker => marker.remove())
  inspectionMarkers.length = 0
  if (expandedMarker && restoreOverview) markerClusterGroup.value?.addLayer(expandedMarker)
  expandedMarker = null
}

const openGroup = (group, pointIndex = 0) => {
  clearInspection()
  expandedMarker = markers.find(marker => marker.options.rawGpsGroup.key === group.key)
  if (expandedMarker) markerClusterGroup.value?.removeLayer(expandedMarker)
  let selectedIndex = pointIndex
  const renderSamples = () => {
    inspectionMarkers.forEach(marker => marker.remove())
    inspectionMarkers.length = 0
    inspectionClusters.getRenderedEntities(group.points, props.map.getZoom()).forEach(entity => {
      const marker = L.marker([entity.latitude, entity.longitude], {
        icon: createRawPointIcon(entity.indices.length),
        title: entity.isCluster ? String(entity.indices.length) : formatRawGpsDateTime(timezone, group.points[entity.indices[0]].timestamp),
        bubblingMouseEvents: false,
        rawGpsSampleIndices: entity.indices,
        zIndexOffset: 5000
      }).addTo(props.map)
      const element = marker.getElement()
      element?.classList.add('raw-gps-inspection-marker')
      element?.classList.toggle('is-selected', entity.indices.includes(selectedIndex))
      element?.setAttribute('aria-pressed', String(entity.indices.includes(selectedIndex)))
      marker.on('click', () => {
        if (!popupMount) { openGroup(group, entity.isCluster ? selectedIndex : entity.indices[0]); return }
        if (entity.isCluster) props.map.setView([entity.latitude, entity.longitude], Math.min(props.map.getZoom() + 2, props.map.getMaxZoom()))
        else popupMount.inspectPoint(entity.indices[0])
      })
      inspectionMarkers.push(marker)
    })
  }
  const highlightPoint = (_, index) => { selectedIndex = index; renderSamples() }
  popupMount = createRawGpsPopupMount(group, {
    timezone,
    container: props.inspectorContainer,
    resolveLocation: props.resolveLocation,
    onSelectPoint: props.selectPoint,
    activePointIndex: pointIndex,
    onInspectPoint: highlightPoint,
    onRender: () => popup?.update()
  })
  if (!props.inspectorContainer) {
    popup = L.popup({
      maxWidth: RAW_GPS_POPUP_MAX_WIDTH_PX,
      closeButton: true,
      autoPan: true,
      className: RAW_GPS_POPUP_CLASS_NAME
    }).setLatLng([group.latitude, group.longitude]).setContent(popupMount.element).openOn(props.map)
    const mountedPopup = popupMount
    popup.on('remove', () => {
      mountedPopup.unmount()
      if (popupMount === mountedPopup) { popupMount = null; popup = null }
    })
  }
  inspectionZoomHandler = renderSamples
  inspectionMap = props.map
  props.map.on('zoomend', inspectionZoomHandler)
  renderSamples()
}

const createRawPointIcon = (count = 1) => {
  const isStack = count > 1
  const size = isStack ? Math.min(34, Math.max(22, 20 + String(count).length * 4)) : 12
  const className = isStack ? 'raw-gps-stack-icon' : 'raw-gps-dot-icon'
  const html = isStack
    ? `<div class="raw-gps-stack-marker"><span>${count}</span></div>`
    : '<div class="raw-gps-dot-marker"></div>'

  return L.divIcon({
    className,
    html,
    iconSize: [size, size],
    iconAnchor: [Math.floor(size / 2), Math.floor(size / 2)]
  })
}

const createClusterIcon = (cluster) => {
  const count = cluster.getAllChildMarkers()
    .reduce((total, marker) => total + Number(marker?.options?.rawGpsCount || 1), 0)
  const sizeClass = count > 1000 ? 'large' : count > 100 ? 'medium' : 'small'
  return L.divIcon({
    html: `<div class="raw-gps-cluster raw-gps-cluster-${sizeClass}"><span>${count}</span></div>`,
    className: 'raw-gps-cluster-icon',
    iconSize: L.point(42, 42)
  })
}

const clearMarkers = () => {
  clearInspection(false)
  if (markerClusterGroup.value) {
    markerClusterGroup.value.clearLayers()
  } else if (baseLayerRef.value) {
    markers.forEach((marker) => baseLayerRef.value.removeFromLayer(marker))
  }
  markers.length = 0
}

const renderMarkers = () => {
  if (!baseLayerRef.value) return

  clearMarkers()
  const groups = groupRawGpsPoints(props.points)

  groups.forEach((group) => {
    const marker = L.marker([group.latitude, group.longitude], {
      icon: createRawPointIcon(group.count),
      keyboard: false,
      rawGpsGroup: group,
      rawGpsCount: group.count,
      bubblingMouseEvents: false
    })

    marker.on('click', () => openGroup(group))

    markers.push(marker)
    if (markerClusterGroup.value) {
      markerClusterGroup.value.addLayer(marker)
    } else {
      baseLayerRef.value.addToLayer(marker)
    }
  })
}

const handleLayerReady = () => {
  if (markerClusterGroup.value) { renderMarkers(); return }
  markerClusterGroup.value = L.markerClusterGroup({
    maxClusterRadius: 42,
    spiderfyOnMaxZoom: false,
    spiderfyOnEveryZoom: false,
    showCoverageOnHover: false,
    zoomToBoundsOnClick: true,
    disableClusteringAtZoom: 16,
    chunkedLoading: true,
    chunkInterval: 200,
    chunkDelay: 50,
    animate: false,
    animateAddingMarkers: false,
    removeOutsideVisibleBounds: true,
    iconCreateFunction: createClusterIcon
  })

  if (props.map && props.visible) {
    props.map.addLayer(markerClusterGroup.value)
  }

  renderMarkers()
}

watch(
  () => props.visible,
  (visible) => {
    if (!visible) clearInspection()
    if (!props.map || !markerClusterGroup.value) return
    if (visible && !props.map.hasLayer(markerClusterGroup.value)) {
      props.map.addLayer(markerClusterGroup.value)
    } else if (!visible && props.map.hasLayer(markerClusterGroup.value)) {
      props.map.removeLayer(markerClusterGroup.value)
    }
  }
)

watch(
  () => props.points,
  () => renderMarkers(),
  { deep: true }
)

watch(() => props.map, () => clearInspection(false))
watch(() => props.selectPoint, callback => popupMount?.setSelectPoint(callback))

onBeforeUnmount(() => {
  clearMarkers()
  if (props.map && markerClusterGroup.value && props.map.hasLayer(markerClusterGroup.value)) {
    props.map.removeLayer(markerClusterGroup.value)
  }
  markerClusterGroup.value = null
})
</script>

<style>
.raw-gps-dot-marker {
  width: 12px;
  height: 12px;
  border-radius: 999px;
  background: #2563eb;
  border: 2px solid #eff6ff;
  box-shadow: 0 1px 5px rgba(15, 23, 42, 0.3);
  box-sizing: border-box;
}

.raw-gps-stack-marker,
.raw-gps-cluster {
  border-radius: 999px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #ffffff;
  font-size: 0.72rem;
  font-weight: 700;
  box-shadow: 0 2px 8px rgba(15, 23, 42, 0.26);
  box-sizing: border-box;
}

.raw-gps-stack-marker {
  min-width: 24px;
  height: 24px;
  padding: 0 5px;
  background: #0f766e;
  border: 2px solid #ccfbf1;
}

.raw-gps-cluster {
  width: 40px;
  height: 40px;
  background: #1d4ed8;
  border: 2px solid #dbeafe;
}

.raw-gps-cluster-medium {
  width: 44px;
  height: 44px;
}

.raw-gps-cluster-large {
  width: 48px;
  height: 48px;
}
</style>
