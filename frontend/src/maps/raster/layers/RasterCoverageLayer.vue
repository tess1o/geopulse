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
let renderer = null
let currentMap = null
const cellLayers = new Map()

const removeLayer = () => {
  if (!coverageLayer) return
  try {
    const layerMap = coverageLayer._map || currentMap || props.map
    if (layerMap) {
      coverageLayer.clearLayers()
      layerMap.removeLayer(coverageLayer)
      renderer?.remove()
    }
  } catch (e) {
    // ignore cleanup errors
  }
  cellLayers.clear()
  coverageLayer = null
  renderer = null
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
    renderer = L.canvas({ padding: 0.5 })
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
    const value = Number(cell?.[2] ?? 0)
    return value > max ? value : max
  }, 0)

  const grid = props.gridMeters
  const crs = L.CRS.EPSG3857
  const visibleKeys = new Set()

  maxCells.forEach((cell) => {
    if (!Array.isArray(cell)) return
    const [cellX, cellY, seenCount] = cell
    if (!Number.isFinite(cellX) || !Number.isFinite(cellY)) return

    const key = `${cellX}:${cellY}`
    visibleKeys.add(key)

    const swPoint = L.point(cellX * grid, cellY * grid)
    const nePoint = L.point((cellX + 1) * grid, (cellY + 1) * grid)
    const sw = crs.unproject(swPoint)
    const ne = crs.unproject(nePoint)
    const bounds = L.latLngBounds(sw, ne)

    const opacity = getOpacity(Number(seenCount ?? 0), maxCount)
    const style = {
      renderer,
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
