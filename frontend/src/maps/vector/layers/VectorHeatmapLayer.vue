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
  profile: {
    type: String,
    default: 'default'
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

const PROFILE_NAMES = {
  STAYS: 'stays',
  TRIPS: 'trips',
  DEFAULT: 'default'
}

const PROFILE_CONFIGS = {
  [PROFILE_NAMES.STAYS]: {
    radiusScale: 0.84,
    blurScale: 0.36,
    lockIntensity: 1.5,
    baseIntensity: 1.65,
    opacityScale: 2.1,
    firstVisibleStop: 0.01,
    firstColorAlpha: 0.74
  },
  [PROFILE_NAMES.TRIPS]: {
    radiusScale: 0.8,
    blurScale: 0.28,
    lockIntensity: 1.3,
    baseIntensity: 1.38,
    opacityScale: 1.7,
    firstVisibleStop: 0.01,
    firstColorAlpha: 0.66
  },
  [PROFILE_NAMES.DEFAULT]: {
    radiusScale: 0.82,
    blurScale: 0.32,
    lockIntensity: 1.35,
    baseIntensity: 1.5,
    opacityScale: 1.85,
    firstVisibleStop: 0.01,
    firstColorAlpha: 0.68
  }
}

state.sourceId = `${state.token}-source`
state.layerId = `${state.token}-layer`

const getValue = (point) => {
  if (typeof props.valueKey === 'function') {
    return props.valueKey(point)
  }

  return point?.[props.valueKey]
}

const resolveProfileName = () => {
  const profile = String(props.profile || PROFILE_NAMES.DEFAULT).toLowerCase()
  if (profile === PROFILE_NAMES.STAYS || profile === PROFILE_NAMES.TRIPS) {
    return profile
  }

  return PROFILE_NAMES.DEFAULT
}

const buildCollection = (profileConfig) => {
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
      // Same weight transform as raster heatmap.
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

const toRgba = (color, alpha) => {
  if (typeof color !== 'string') {
    return color
  }

  const normalizedAlpha = Math.max(0, Math.min(1, alpha))
  const hex = color.trim()
  if (!hex.startsWith('#')) {
    return color
  }

  const raw = hex.slice(1)
  const expand = (value) => (value.length === 1 ? value + value : value)
  const hasValidHexLength = raw.length === 3 || raw.length === 6
  if (!hasValidHexLength) {
    return color
  }

  const r = Number.parseInt(expand(raw.slice(0, raw.length === 3 ? 1 : 2)), 16)
  const g = Number.parseInt(expand(raw.slice(raw.length === 3 ? 1 : 2, raw.length === 3 ? 2 : 4)), 16)
  const b = Number.parseInt(expand(raw.slice(raw.length === 3 ? 2 : 4, raw.length === 3 ? 3 : 6)), 16)
  if (!Number.isFinite(r) || !Number.isFinite(g) || !Number.isFinite(b)) {
    return color
  }

  return `rgba(${r},${g},${b},${normalizedAlpha})`
}

const buildHeatmapColorExpression = (profileConfig) => {
  const entries = Object.entries(props.gradient || {})
    .map(([stop, color]) => [Number(stop), color])
    .filter(([stop, color]) => Number.isFinite(stop) && typeof color === 'string')
    .sort((left, right) => left[0] - right[0])

  const expression = [
    'interpolate',
    ['linear'],
    ['heatmap-density'],
    // Keep density 0 fully transparent to avoid tinting the entire map canvas.
    0, 'rgba(37,99,235,0)'
  ]

  const defaultStops = [
    [0, '#2563eb'],
    [0.35, '#22c55e'],
    [0.6, '#eab308'],
    [0.8, '#f97316'],
    [1, '#dc2626']
  ]
  const normalizedStops = entries.length > 0 ? entries : defaultStops

  // Keep density 0 transparent and then ramp in using the same gradient stops as raster.
  const firstColor = normalizedStops[0]?.[1] || '#2563eb'
  expression.push(profileConfig.firstVisibleStop, toRgba(firstColor, profileConfig.firstColorAlpha))

  let previousStop = profileConfig.firstVisibleStop

  normalizedStops
    .filter(([stop]) => stop > 0)
    .forEach(([stop, color]) => {
      const monotonicStop = Math.min(1, Math.max(previousStop + 0.001, stop))
      expression.push(monotonicStop, color)
      previousStop = monotonicStop
    })

  if (previousStop < 1) {
    expression.push(1, normalizedStops[normalizedStops.length - 1][1])
  }

  return expression
}

const renderLayer = () => {
  if (!isMapLibreMap(props.map)) {
    return
  }

  const profileName = resolveProfileName()
  const profileConfig = PROFILE_CONFIGS[profileName] || PROFILE_CONFIGS[PROFILE_NAMES.DEFAULT]
  const collection = buildCollection(profileConfig)
  // MapLibre has no explicit blur prop in heatmap paint; approximate Leaflet radius+blur impact here.
  const baseRadius = Math.max(2, (props.radius * profileConfig.radiusScale) + (props.blur * profileConfig.blurScale))
  const heatmapIntensity = props.lockMaxZoom
    ? profileConfig.lockIntensity
    : [
        'interpolate',
        ['linear'],
        ['zoom'],
        0, Math.max(0.6, profileConfig.baseIntensity * 0.85),
        12, profileConfig.baseIntensity,
        18, profileConfig.baseIntensity * 1.06
      ]
  const heatmapRadius = props.lockMaxZoom
    ? Math.max(6, baseRadius)
    : [
        'interpolate',
        ['linear'],
        ['zoom'],
        0, Math.max(2, baseRadius * 0.75),
        9, Math.max(4, baseRadius * 0.9),
        16, Math.max(6, baseRadius)
      ]
  const heatmapOpacity = Math.max(0.08, props.minOpacity * profileConfig.opacityScale)
  const heatmapPaint = {
    'heatmap-weight': ['coalesce', ['get', 'weight'], props.minWeight],
    'heatmap-intensity': heatmapIntensity,
    'heatmap-radius': heatmapRadius,
    'heatmap-opacity': heatmapOpacity,
    'heatmap-color': buildHeatmapColorExpression(profileConfig)
  }

  ensureGeoJsonSource(props.map, state.sourceId, collection)

  ensureLayer(props.map, {
    id: state.layerId,
    type: 'heatmap',
    source: state.sourceId,
    maxzoom: 24,
    paint: heatmapPaint
  })

  // ensureLayer does not mutate existing layer paint/range, so keep them in sync explicitly.
  if (props.map.getLayer(state.layerId)) {
    Object.entries(heatmapPaint).forEach(([property, value]) => {
      props.map.setPaintProperty(state.layerId, property, value)
    })
    if (typeof props.map.setLayerZoomRange === 'function') {
      props.map.setLayerZoomRange(state.layerId, 0, 24)
    }
  }

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
    props.profile,
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
