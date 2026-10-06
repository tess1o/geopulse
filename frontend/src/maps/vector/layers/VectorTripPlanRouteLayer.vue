<template></template>

<script setup>
import { onBeforeUnmount, watch } from 'vue'
import {
  createFeatureCollection,
  ensureGeoJsonSource,
  ensureLayer,
  hasMapLibreLayer,
  isMapLibreMap,
  nextLayerToken,
  removeLayers,
  removeSources,
  setLayerVisibility
} from '@/maps/vector/utils/maplibreLayerUtils'
import { PATH_OUTLINE_EXTRA_WIDTH, getOutlineColor } from '@/maps/shared/mapAppearance'

const props = defineProps({
  map: {
    type: Object,
    required: true
  },
  legs: {
    type: Array,
    default: () => []
  },
  visible: {
    type: Boolean,
    default: true
  },
  color: {
    type: String,
    required: true
  },
  width: {
    type: Number,
    required: true
  },
  outline: {
    type: Boolean,
    default: false
  }
})

// Same opacity as the timeline path's default line (PathLayer).
const ROUTE_OPACITY = 0.8
// In line widths: dashed = a straight connection, not a route to follow.
const STRAIGHT_DASH = [2, 2]

const token = nextLayerToken('gp-trip-plan-route')
const sourceId = `${token}-source`
const routedCasingLayerId = `${token}-routed-casing`
const straightCasingLayerId = `${token}-straight-casing`
const routedLayerId = `${token}-routed`
const straightLayerId = `${token}-straight`
const casingLayerIds = [routedCasingLayerId, straightCasingLayerId]
const lineLayerIds = [routedLayerId, straightLayerId]
const layerIds = [...casingLayerIds, ...lineLayerIds]

let boundMap = null
let styleLoadHandler = null

const buildCollection = () => createFeatureCollection((props.legs || [])
  .map((leg) => {
    const coordinates = (leg?.coordinates || [])
      .filter((point) => Array.isArray(point) && Number.isFinite(point[0]) && Number.isFinite(point[1]))
      // Legs carry [lat, lon]; GeoJSON wants [lon, lat].
      .map(([lat, lon]) => [lon, lat])
    if (coordinates.length < 2) return null
    return {
      type: 'Feature',
      geometry: { type: 'LineString', coordinates },
      properties: { routed: Boolean(leg.routed) }
    }
  })
  .filter(Boolean))

const ROUTED_FILTER = ['==', ['get', 'routed'], true]
const STRAIGHT_FILTER = ['==', ['get', 'routed'], false]
const LINE_LAYOUT = { 'line-cap': 'round', 'line-join': 'round' }

// Routed and straight legs are separate layers because a dash pattern is a per-layer property.
// This component is mounted before the stop markers, so its layers are added first and sit
// underneath them.
const renderLayers = () => {
  if (!isMapLibreMap(props.map)) return

  ensureGeoJsonSource(props.map, sourceId, buildCollection())

  ensureLayer(props.map, { id: routedCasingLayerId, type: 'line', source: sourceId, filter: ROUTED_FILTER, layout: LINE_LAYOUT, paint: {} })
  ensureLayer(props.map, { id: straightCasingLayerId, type: 'line', source: sourceId, filter: STRAIGHT_FILTER, layout: LINE_LAYOUT, paint: { 'line-dasharray': STRAIGHT_DASH } })
  ensureLayer(props.map, { id: routedLayerId, type: 'line', source: sourceId, filter: ROUTED_FILTER, layout: LINE_LAYOUT, paint: {} })
  ensureLayer(props.map, { id: straightLayerId, type: 'line', source: sourceId, filter: STRAIGHT_FILTER, layout: LINE_LAYOUT, paint: { 'line-dasharray': STRAIGHT_DASH } })

  // ensureLayer leaves an existing layer alone, so the appearance is (re)applied every render.
  const setPaint = (layerId, name, value) => {
    if (hasMapLibreLayer(props.map, layerId)) {
      props.map.setPaintProperty(layerId, name, value)
    }
  }
  casingLayerIds.forEach((layerId) => {
    setPaint(layerId, 'line-color', getOutlineColor(props.color))
    setPaint(layerId, 'line-width', props.width + PATH_OUTLINE_EXTRA_WIDTH)
    setPaint(layerId, 'line-opacity', ROUTE_OPACITY)
  })
  lineLayerIds.forEach((layerId) => {
    setPaint(layerId, 'line-color', props.color)
    setPaint(layerId, 'line-width', props.width)
    setPaint(layerId, 'line-opacity', ROUTE_OPACITY)
  })

  setLayerVisibility(props.map, lineLayerIds, props.visible)
  // The casing is the timeline path's optional outline.
  setLayerVisibility(props.map, casingLayerIds, props.visible && props.outline)
}

const clearLayers = () => {
  if (boundMap && styleLoadHandler) {
    boundMap.off('style.load', styleLoadHandler)
    styleLoadHandler = null
  }
  const targetMap = boundMap || props.map
  if (isMapLibreMap(targetMap)) {
    removeLayers(targetMap, [...layerIds].reverse())
    removeSources(targetMap, [sourceId])
  }
  boundMap = null
}

watch(
  () => [props.map, props.legs, props.visible, props.color, props.width, props.outline],
  () => {
    if (!isMapLibreMap(props.map)) {
      clearLayers()
      return
    }
    if (boundMap && boundMap !== props.map) {
      clearLayers()
    }
    boundMap = props.map
    if (!styleLoadHandler) {
      styleLoadHandler = () => renderLayers()
      props.map.on('style.load', styleLoadHandler)
    }
    renderLayers()
  },
  { immediate: true, deep: true }
)

onBeforeUnmount(() => {
  clearLayers()
})
</script>
