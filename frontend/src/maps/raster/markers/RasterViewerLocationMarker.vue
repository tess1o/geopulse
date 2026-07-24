<template>
  <!-- Marker is managed directly on the Leaflet map. -->
</template>

<script setup>
import { onUnmounted, watch } from 'vue'
import L from 'leaflet'
import { useTimezone } from '@/composables/useTimezone'
import MapInfoPopup from '@/maps/shared/popups/MapInfoPopup.vue'
import { mountMapPopup } from '@/maps/shared/popups/mountMapPopup'
import { buildViewerLocationPopupModel } from '@/maps/shared/popups/locationPopupModels'
import {
  getMapPopupVariantClassName,
  MAP_POPUP_COMPACT_MAX_WIDTH_PX
} from '@/maps/shared/popups/mapPopupOptions'

const timezone = useTimezone()

const props = defineProps({
  map: {
    type: Object,
    required: true
  },
  location: {
    type: Object,
    required: true
  }
})

let marker = null
let accuracyCircle = null
let popupMount = null

const hasValidCoordinates = () => {
  return Number.isFinite(Number(props.location?.latitude)) && Number.isFinite(Number(props.location?.longitude))
}

const removeMarker = () => {
  popupMount?.unmount?.()
  popupMount = null
  if (accuracyCircle && props.map) {
    props.map.removeLayer(accuracyCircle)
  }
  if (marker && props.map) {
    props.map.removeLayer(marker)
  }
  accuracyCircle = null
  marker = null
}

const createMarker = () => {
  removeMarker()

  if (!hasValidCoordinates()) {
    return
  }

  const latLng = [props.location.latitude, props.location.longitude]
  const isFallback = props.location.source === 'fallback'
  const color = isFallback ? '#f59e0b' : '#0ea5e9'

  if (props.location.accuracy && !isFallback) {
    accuracyCircle = L.circle(latLng, {
      radius: props.location.accuracy,
      color,
      weight: 1,
      opacity: 0.35,
      fillColor: color,
      fillOpacity: 0.12
    }).addTo(props.map)
  }

  marker = L.circleMarker(latLng, {
    radius: 9,
    fillColor: color,
    color: '#ffffff',
    weight: 3,
    opacity: 1,
    fillOpacity: 1
  })
  popupMount = mountMapPopup(
    MapInfoPopup,
    buildViewerLocationPopupModel(props.location, { timezone })
  )

  marker
    .addTo(props.map)
    .bindPopup(popupMount.element, {
      maxWidth: MAP_POPUP_COMPACT_MAX_WIDTH_PX,
      className: getMapPopupVariantClassName('compact', 'gp-viewer-location-popup-container')
    })
}

watch(
  () => [props.map, props.location?.latitude, props.location?.longitude, props.location?.timestamp, props.location?.source, props.location?.accuracy],
  createMarker,
  { immediate: true }
)

onUnmounted(() => {
  removeMarker()
})
</script>
