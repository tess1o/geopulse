<template></template>

<script setup>
import { onBeforeUnmount, watch } from 'vue'
import {
  createFeatureCollection,
  ensureGeoJsonSource,
  ensureLayer,
  isMapLibreMap,
  nextLayerToken,
  removeLayers,
  removeSources,
  setLayerVisibility
} from '@/maps/vector/utils/maplibreLayerUtils'

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

const state = {
  token: nextLayerToken('gp-coverage'),
  sourceId: '',
  layerId: '',
  styleLoadHandler: null,
  boundMap: null
}

state.sourceId = `${state.token}-source`
state.layerId = `${state.token}-layer`

const getOpacity = (count, maxCount) => {
  if (!Number.isFinite(count) || count <= 0) return props.minOpacity
  if (!Number.isFinite(maxCount) || maxCount <= 0) return props.minOpacity

  const normalized = Math.log1p(count) / Math.log1p(maxCount)
  return Math.min(props.maxOpacity, Math.max(props.minOpacity, props.minOpacity + normalized * (props.maxOpacity - props.minOpacity)))
}

const unproject = (x, y) => {
  const radius = 6378137
  return [x / radius * 180 / Math.PI, (2 * Math.atan(Math.exp(y / radius)) - Math.PI / 2) * 180 / Math.PI]
}

const buildCollection = () => {
  const maxCells = props.maxCellsToRender > 0
    ? props.cells.slice(0, props.maxCellsToRender)
    : props.cells

  if (!Array.isArray(maxCells) || maxCells.length === 0) {
    return createFeatureCollection([])
  }

  const maxCount = maxCells.reduce((maximum, cell) => {
    const value = Number(cell?.[2] ?? 0)
    return value > maximum ? value : maximum
  }, 0)

  const grid = props.gridMeters

  const features = maxCells
    .map((cell) => {
      if (!Array.isArray(cell)) return null
      const [cellX, cellY, seenCount] = cell

      if (!Number.isFinite(cellX) || !Number.isFinite(cellY)) {
        return null
      }

      const sw = unproject(cellX * grid, cellY * grid)
      const ne = unproject((cellX + 1) * grid, (cellY + 1) * grid)
      const opacity = getOpacity(Number(seenCount ?? 0), maxCount)

      return {
        type: 'Feature',
        geometry: {
          type: 'Polygon',
          coordinates: [[
            sw,
            [ne[0], sw[1]],
            ne,
            [sw[0], ne[1]],
            sw
          ]]
        },
        properties: {
          opacity
        }
      }
    })
    .filter(Boolean)

  return createFeatureCollection(features)
}

const renderLayer = () => {
  if (!isMapLibreMap(props.map)) {
    return
  }

  const collection = buildCollection()

  ensureGeoJsonSource(props.map, state.sourceId, collection)

  ensureLayer(props.map, {
    id: state.layerId,
    type: 'fill',
    source: state.sourceId,
    paint: {
      'fill-color': props.fillColor,
      'fill-opacity': ['coalesce', ['get', 'opacity'], props.minOpacity]
    }
  })

  setLayerVisibility(props.map, [state.layerId], props.visible)
}

const clearLayer = () => {
  if (state.boundMap && state.styleLoadHandler) {
    state.boundMap.off('style.load', state.styleLoadHandler)
    state.styleLoadHandler = null
  }

  const targetMap = state.boundMap || props.map
  if (!isMapLibreMap(targetMap)) {
    state.boundMap = null
    return
  }

  removeLayers(targetMap, [state.layerId])
  removeSources(targetMap, [state.sourceId])
  state.boundMap = null
}

watch(
  () => [
    props.map,
    props.cells,
    props.gridMeters,
    props.visible,
    props.fillColor,
    props.minOpacity,
    props.maxOpacity,
    props.maxCellsToRender
  ],
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
  { immediate: true }
)

onBeforeUnmount(() => {
  clearLayer()
})
</script>
