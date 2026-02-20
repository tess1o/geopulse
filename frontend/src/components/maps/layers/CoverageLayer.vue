<template></template>

<script setup>
import { onBeforeUnmount, watch } from 'vue'
import L from 'leaflet'

const props = defineProps({
  map: {
    type: Object,
    default: null
  },
  cells: {
    type: Array,
    default: () => []
  },
  gridMeters: {
    type: Number,
    default: 50
  },
  visible: {
    type: Boolean,
    default: true
  },
  fillColor: {
    type: String,
    default: '#3b82f6'
  },
  minOpacity: {
    type: Number,
    default: 0.15
  },
  maxOpacity: {
    type: Number,
    default: 0.85
  },
  maxCellsToRender: {
    type: Number,
    default: 12000
  }
})

let coverageLayer = null

const removeLayer = () => {
  if (!coverageLayer) return
  try {
    const currentMap = coverageLayer._map || props.map
    if (currentMap) {
      coverageLayer.clearLayers()
      currentMap.removeLayer(coverageLayer)
    }
  } catch (e) {
    // ignore cleanup errors
  }
  coverageLayer = null
}

const getOpacity = (count, maxCount) => {
  if (!Number.isFinite(count) || count <= 0) return props.minOpacity
  if (!Number.isFinite(maxCount) || maxCount <= 0) return props.minOpacity
  const normalized = Math.log1p(count) / Math.log1p(maxCount)
  return Math.min(props.maxOpacity, Math.max(props.minOpacity, props.minOpacity + normalized * (props.maxOpacity - props.minOpacity)))
}

const renderCells = () => {
  if (!props.map) {
    removeLayer()
    return
  }

  if (!props.visible) {
    if (coverageLayer) {
      coverageLayer.clearLayers()
    }
    return
  }

  if (!coverageLayer) {
    coverageLayer = L.layerGroup().addTo(props.map)
  } else {
    coverageLayer.clearLayers()
  }

  if (!props.cells?.length) return

  const maxCells = props.maxCellsToRender > 0
    ? props.cells.slice(0, props.maxCellsToRender)
    : props.cells

  const maxCount = maxCells.reduce((max, cell) => {
    const value = Number(cell?.seenCount ?? 0)
    return value > max ? value : max
  }, 0)

  const half = props.gridMeters / 2
  const crs = props.map?.options?.crs || L.CRS.EPSG3857

  maxCells.forEach((cell) => {
    const lat = Number(cell?.latitude)
    const lon = Number(cell?.longitude)
    if (!Number.isFinite(lat) || !Number.isFinite(lon)) return

    const center = L.latLng(lat, lon)
    const centerPoint = crs.project(center)
    const swPoint = L.point(centerPoint.x - half, centerPoint.y - half)
    const nePoint = L.point(centerPoint.x + half, centerPoint.y + half)
    const sw = crs.unproject(swPoint)
    const ne = crs.unproject(nePoint)
    const bounds = L.latLngBounds(sw, ne)

    const opacity = getOpacity(Number(cell?.seenCount ?? 0), maxCount)
    L.rectangle(bounds, {
      color: props.fillColor,
      weight: 0,
      fillColor: props.fillColor,
      fillOpacity: opacity,
      interactive: false
    }).addTo(coverageLayer)
  })
}

watch(
  () => [props.map, props.visible, props.gridMeters, props.fillColor, props.minOpacity, props.maxOpacity, props.cells],
  () => renderCells()
)

onBeforeUnmount(() => {
  removeLayer()
})
</script>
