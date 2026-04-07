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
  setLayerVisibility,
  toFiniteNumber
} from '@/maps/vector/utils/maplibreLayerUtils'

const props = defineProps({
  map: {
    type: Object,
    default: null
  },
  points: {
    type: Array,
    default: () => []
  },
  valueKey: {
    type: [String, Function],
    default: 'durationSeconds'
  },
  minWeight: {
    type: Number,
    default: 0.05
  },
  gamma: {
    type: Number,
    default: 0.6
  },
  radius: {
    type: Number,
    default: 32
  },
  blur: {
    type: Number,
    default: 24
  },
  minOpacity: {
    type: Number,
    default: 0.3
  },
  max: {
    type: Number,
    default: 1.0
  },
  gradient: {
    type: Object,
    default: () => ({
      0.0: '#2563eb',
      0.35: '#22c55e',
      0.6: '#eab308',
      0.8: '#f97316',
      1.0: '#dc2626'
    })
  },
  lockMaxZoom: {
    type: Boolean,
    default: true
  },
  enabled: {
    type: Boolean,
    default: true
  }
})

const state = {
  token: nextLayerToken('gp-heatmap'),
  sourceId: '',
  layerId: '',
  styleLoadHandler: null,
  boundMap: null
}

state.sourceId = `${state.token}-source`
state.layerId = `${state.token}-layer`

const getValue = (point) => {
  if (typeof props.valueKey === 'function') {
    return props.valueKey(point)
  }

  return point?.[props.valueKey]
}

const buildCollection = () => {
  if (!Array.isArray(props.points) || props.points.length === 0) {
    return createFeatureCollection([])
  }

  const values = props.points
    .map((point) => getValue(point))
    .filter((value) => Number.isFinite(value) && value > 0)

  const maxValue = Math.max(...values, 1)

  const features = props.points
    .map((point) => {
      const latitude = toFiniteNumber(point?.lat ?? point?.latitude)
      const longitude = toFiniteNumber(point?.lng ?? point?.longitude)

      if (latitude === null || longitude === null) {
        return null
      }

      const raw = Number(getValue(point)) || 0
      const linear = Math.min(1, raw / maxValue)
      const weight = Math.max(props.minWeight, Math.pow(linear, props.gamma))

      return {
        type: 'Feature',
        geometry: {
          type: 'Point',
          coordinates: [longitude, latitude]
        },
        properties: {
          weight,
          raw
        }
      }
    })
    .filter(Boolean)

  return createFeatureCollection(features)
}

const buildHeatmapColorExpression = () => {
  const entries = Object.entries(props.gradient || {})
    .map(([stop, color]) => [Number(stop), color])
    .filter(([stop, color]) => Number.isFinite(stop) && typeof color === 'string')
    .sort((left, right) => left[0] - right[0])

  if (entries.length === 0) {
    return [
      'interpolate',
      ['linear'],
      ['heatmap-density'],
      0, 'rgba(37,99,235,0)',
      0.3, '#22c55e',
      0.6, '#eab308',
      0.8, '#f97316',
      1, '#dc2626'
    ]
  }

  const expression = ['interpolate', ['linear'], ['heatmap-density']]
  entries.forEach(([stop, color]) => {
    expression.push(stop, color)
  })

  return expression
}

const renderLayer = () => {
  if (!isMapLibreMap(props.map)) {
    return
  }

  const collection = buildCollection()

  ensureGeoJsonSource(props.map, state.sourceId, collection)

  ensureLayer(props.map, {
    id: state.layerId,
    type: 'heatmap',
    source: state.sourceId,
    maxzoom: props.lockMaxZoom ? Math.round(props.map.getZoom?.() || 24) : 24,
    paint: {
      'heatmap-weight': ['coalesce', ['get', 'weight'], props.minWeight],
      'heatmap-intensity': [
        'interpolate',
        ['linear'],
        ['zoom'],
        0, 0.7,
        12, 1,
        16, 1.2
      ],
      'heatmap-radius': [
        'interpolate',
        ['linear'],
        ['zoom'],
        0, Math.max(2, props.radius * 0.35),
        9, Math.max(4, props.radius * 0.7),
        16, Math.max(6, props.radius)
      ],
      'heatmap-opacity': props.minOpacity,
      'heatmap-color': buildHeatmapColorExpression()
    }
  })

  setLayerVisibility(props.map, [state.layerId], props.enabled)
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

const syncHeatmapLayer = () => {
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
}

watch(
  () => [
    props.map,
    props.valueKey,
    props.minWeight,
    props.gamma,
    props.radius,
    props.blur,
    props.minOpacity,
    props.max,
    props.lockMaxZoom,
    props.enabled
  ],
  syncHeatmapLayer,
  { immediate: true }
)

watch(() => props.points, syncHeatmapLayer, { deep: true })
watch(() => props.gradient, syncHeatmapLayer, { deep: true })

onBeforeUnmount(() => {
  clearLayer()
})
</script>
