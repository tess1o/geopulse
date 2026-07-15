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
import 'leaflet.markercluster/dist/MarkerCluster.css'
import 'leaflet.markercluster/dist/MarkerCluster.Default.css'
import BaseLayer from '@/components/maps/layers/BaseLayer.vue'
import { useTimezone } from '@/composables/useTimezone'
import {
  createRawGpsPopupElement,
  groupRawGpsPoints,
  RAW_GPS_POPUP_CLASS_NAME,
  RAW_GPS_POPUP_MAX_WIDTH_PX
} from '@/maps/shared/rawGpsPointInspector'
import '@/maps/shared/styles/rawGpsPointPopup.css'

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
  resolveLocation: {
    type: Function,
    default: null
  }
})

const baseLayerRef = ref(null)
const markerClusterGroup = ref(null)
const markers = []
const timezone = useTimezone()

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
      rawGpsCount: group.count
    })

    marker.on('click', () => {
      let popup = null
      const popupContent = createRawGpsPopupElement(group, {
        timezone,
        resolveLocation: props.resolveLocation,
        onRender: () => {
          popup?.update?.()
        }
      })

      popup = marker.bindPopup(popupContent, {
        maxWidth: RAW_GPS_POPUP_MAX_WIDTH_PX,
        closeButton: true,
        autoPan: true,
        className: RAW_GPS_POPUP_CLASS_NAME
      }).openPopup().getPopup()
    })

    markers.push(marker)
    if (markerClusterGroup.value) {
      markerClusterGroup.value.addLayer(marker)
    } else {
      baseLayerRef.value.addToLayer(marker)
    }
  })
}

const handleLayerReady = () => {
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
