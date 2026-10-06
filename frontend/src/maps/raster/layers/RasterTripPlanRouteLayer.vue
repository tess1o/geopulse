<template>
  <BaseLayer
    ref="baseLayerRef"
    :map="map"
    :visible="visible"
    @layer-ready="renderLegs"
  />
</template>

<script setup>
import { onBeforeUnmount, ref, watch } from 'vue'
import L from 'leaflet'
import BaseLayer from '@/components/maps/layers/BaseLayer.vue'
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

// Same opacity as the timeline path's default polyline (PathLayer).
const ROUTE_OPACITY = 0.8
// Dashed = a straight connection, not a route to follow.
const STRAIGHT_DASH = '8 8'

const baseLayerRef = ref(null)
let polylines = []

const toLatLngs = (coordinates) => (coordinates || [])
  .filter((point) => Array.isArray(point) && Number.isFinite(point[0]) && Number.isFinite(point[1]))
  .map(([lat, lon]) => [lat, lon])

const clearLegs = () => {
  polylines.forEach((polyline) => baseLayerRef.value?.removeFromLayer(polyline))
  polylines = []
}

// Polylines live in Leaflet's overlay pane, which always sits under the marker pane, so the
// route never covers the stop markers.
const renderLegs = () => {
  if (!baseLayerRef.value) return
  clearLegs()

  ;(props.legs || []).forEach((leg) => {
    const latLngs = toLatLngs(leg?.coordinates)
    if (latLngs.length < 2) return

    const dashArray = leg.routed ? null : STRAIGHT_DASH
    const layers = []
    if (props.outline) {
      // The same casing the timeline path draws when outlines are on.
      layers.push(L.polyline(latLngs, {
        color: getOutlineColor(props.color),
        weight: props.width + PATH_OUTLINE_EXTRA_WIDTH,
        opacity: ROUTE_OPACITY,
        lineCap: 'round',
        lineJoin: 'round',
        dashArray,
        interactive: false
      }))
    }
    layers.push(L.polyline(latLngs, {
      color: props.color,
      weight: props.width,
      opacity: ROUTE_OPACITY,
      lineCap: 'round',
      lineJoin: 'round',
      dashArray,
      interactive: false
    }))

    layers.forEach((layer) => {
      baseLayerRef.value.addToLayer(layer)
      polylines.push(layer)
    })
  })
}

watch(() => [props.legs, props.color, props.width, props.outline], () => {
  if (baseLayerRef.value?.isReady) {
    renderLegs()
  }
}, { deep: true })

onBeforeUnmount(() => {
  clearLegs()
})
</script>
