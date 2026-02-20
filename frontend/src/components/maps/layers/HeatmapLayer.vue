<template></template>

<script setup>
import { onBeforeUnmount, onMounted, watch } from 'vue'
import L from 'leaflet'
import 'leaflet.heat'

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
      1.0: '#dc2626',
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

let heatLayer = null
let zoomHandler = null

const removeHeatLayer = () => {
  if (!heatLayer) return
  try {
    const currentMap = heatLayer._map || props.map
    if (heatLayer._frame && L?.Util?.cancelAnimFrame) {
      L.Util.cancelAnimFrame(heatLayer._frame)
      heatLayer._frame = null
    }
    if (currentMap) {
      if (zoomHandler) {
        currentMap.off('zoomend', zoomHandler)
        zoomHandler = null
      }
      currentMap.removeLayer(heatLayer)
    }
  } catch (e) {
    // ignore – map may already be gone
  }
  heatLayer = null
}

const getValue = (point) => {
  if (typeof props.valueKey === 'function') {
    return props.valueKey(point)
  }
  return point?.[props.valueKey]
}

const buildHeatPoints = () => {
  if (!props.points?.length) return []

  const values = props.points
    .map((p) => getValue(p))
    .filter((v) => Number.isFinite(v) && v > 0)

  const maxVal = Math.max(...values, 1)

  return props.points
    .map((p) => {
      const lat = Number(p.lat ?? p.latitude)
      const lng = Number(p.lng ?? p.longitude)
      if (!Number.isFinite(lat) || !Number.isFinite(lng)) return null

      const raw = Number(getValue(p)) || 0
      const linear = Math.min(1, raw / maxVal)
      const weight = Math.max(props.minWeight, Math.pow(linear, props.gamma))
      return [lat, lng, weight]
    })
    .filter(Boolean)
}

const renderHeatLayer = () => {
  if (!props.map) {
    removeHeatLayer()
    return
  }

  try {
    if (!props.enabled) {
      if (heatLayer) {
        heatLayer.setLatLngs([])
        heatLayer.redraw()
      }
      return
    }

    const points = buildHeatPoints()
    const shouldShow = points.length > 0

    if (!L?.heatLayer) {
      console.warn('HeatmapLayer: leaflet.heat not available')
      return
    }

    if (heatLayer && heatLayer._map && heatLayer._map !== props.map) {
      removeHeatLayer()
    }

    const zoom = props.map.getZoom()
    const options = {
      radius: props.radius,
      blur: props.blur,
      maxZoom: props.lockMaxZoom ? zoom : undefined,
      max: props.max,
      minOpacity: props.minOpacity,
      gradient: props.gradient
    }

    if (!heatLayer) {
      if (!shouldShow) return
      heatLayer = L.heatLayer(points, options).addTo(props.map)
      if (props.lockMaxZoom) {
        zoomHandler = () => {
          if (!heatLayer) return
          heatLayer.setOptions({ maxZoom: props.map.getZoom() })
          heatLayer.redraw()
        }
        props.map.on('zoomend', zoomHandler)
      }
      return
    }

    if (!shouldShow) {
      heatLayer.setLatLngs([])
      heatLayer.redraw()
      return
    }

    heatLayer.setLatLngs(points)
    heatLayer.setOptions(options)
    heatLayer.redraw()
  } catch (e) {
    console.warn('HeatmapLayer: could not render heat layer', e)
  }
}

watch(
  () => [
    props.map,
    props.enabled,
    props.valueKey,
    props.minWeight,
    props.gamma,
    props.radius,
    props.blur,
    props.minOpacity,
    props.max,
    props.gradient,
    props.lockMaxZoom
  ],
  () => renderHeatLayer()
)

watch(
  () => props.points,
  () => renderHeatLayer()
)

onMounted(() => {
  renderHeatLayer()
})

onBeforeUnmount(() => {
  removeHeatLayer()
})
</script>
