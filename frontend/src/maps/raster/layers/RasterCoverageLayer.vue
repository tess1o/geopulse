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
    default: '#1d4ed8'
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
let currentMap = null
const cellLayers = new Map()

const removeLayer = () => {
  if (!coverageLayer) return
  try {
    const layerMap = coverageLayer._map || currentMap || props.map
    if (layerMap) {
      coverageLayer.clearLayers()
      layerMap.removeLayer(coverageLayer)
    }
  } catch (e) {
    // ignore cleanup errors
  }
  cellLayers.clear()
  coverageLayer = null
  currentMap = null
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
    removeLayer()
    return
  }

  if (currentMap && currentMap !== props.map) {
    removeLayer()
  }

  if (!coverageLayer) {
    coverageLayer = L.layerGroup().addTo(props.map)
    currentMap = props.map
  }

  const maxCells = props.maxCellsToRender > 0
    ? props.cells.slice(0, props.maxCellsToRender)
    : props.cells

  if (!maxCells?.length) {
    if (cellLayers.size > 0) {
      coverageLayer.clearLayers()
      cellLayers.clear()
    }
    return
  }

  const maxCount = maxCells.reduce((max, cell) => {
    const value = Number(cell?.seenCount ?? 0)
    return value > max ? value : max
  }, 0)

  const half = props.gridMeters / 2
  const crs = props.map?.options?.crs || L.CRS.EPSG3857
  const visibleKeys = new Set()

  maxCells.forEach((cell) => {
    const lat = Number(cell?.latitude)
    const lon = Number(cell?.longitude)
    if (!Number.isFinite(lat) || !Number.isFinite(lon)) return

    const key = `${lat.toFixed(6)}:${lon.toFixed(6)}`
    visibleKeys.add(key)

    const center = L.latLng(lat, lon)
    const centerPoint = crs.project(center)
    const swPoint = L.point(centerPoint.x - half, centerPoint.y - half)
    const nePoint = L.point(centerPoint.x + half, centerPoint.y + half)
    const sw = crs.unproject(swPoint)
    const ne = crs.unproject(nePoint)
    const bounds = L.latLngBounds(sw, ne)

    const opacity = getOpacity(Number(cell?.seenCount ?? 0), maxCount)
    const style = {
      color: props.fillColor,
      weight: 0,
      fillColor: props.fillColor,
      fillOpacity: opacity,
      interactive: false
    }

    const existing = cellLayers.get(key)
    if (existing) {
      existing.setBounds(bounds)
      existing.setStyle(style)
    } else {
      const rectangle = L.rectangle(bounds, style).addTo(coverageLayer)
      cellLayers.set(key, rectangle)
    }
  })

  for (const [key, rectangle] of cellLayers.entries()) {
    if (visibleKeys.has(key)) continue
    coverageLayer.removeLayer(rectangle)
    cellLayers.delete(key)
  }
}

watch(
  () => [props.map, props.visible, props.gridMeters, props.fillColor, props.minOpacity, props.maxOpacity, props.cells],
  () => renderCells()
)

onBeforeUnmount(() => {
  removeLayer()
})
</script>
