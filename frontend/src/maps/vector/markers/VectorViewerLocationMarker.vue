<template>
  <!-- Native MapLibre marker managed via script. -->
</template>

<script setup>
import { onUnmounted, watch } from 'vue'
import maplibregl from 'maplibre-gl'
import { useTimezone } from '@/composables/useTimezone'
import { isMapLibreMap } from '@/maps/vector/utils/maplibreLayerUtils'
import MapInfoPopup from '@/maps/shared/popups/MapInfoPopup.vue'
import { mountMapPopup } from '@/maps/shared/popups/mountMapPopup'
import { buildViewerLocationPopupModel } from '@/maps/shared/popups/locationPopupModels'
import {
  getMapPopupVariantClassName,
  MAP_POPUP_COMPACT_MAX_WIDTH,
  MAP_POPUP_OFFSET
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
let popup = null
let popupMount = null

const hasValidCoordinates = () => {
  return Number.isFinite(Number(props.location?.latitude)) && Number.isFinite(Number(props.location?.longitude))
}

const removeMarker = () => {
  popupMount?.unmount?.()
  popupMount = null
  if (marker) {
    marker.remove()
  }
  marker = null
  popup = null
}

const createMarkerElement = () => {
  const element = document.createElement('div')
  const isFallback = props.location.source === 'fallback'
  element.className = isFallback
    ? 'maplibre-viewer-location-dot fallback'
    : 'maplibre-viewer-location-dot'
  return element
}

const createMarker = () => {
  if (!isMapLibreMap(props.map) || !hasValidCoordinates()) {
    removeMarker()
    return
  }

  removeMarker()

  popupMount = mountMapPopup(
    MapInfoPopup,
    buildViewerLocationPopupModel(props.location, { timezone })
  )
  popup = new maplibregl.Popup({
    offset: MAP_POPUP_OFFSET,
    maxWidth: MAP_POPUP_COMPACT_MAX_WIDTH,
    className: getMapPopupVariantClassName('compact', 'gp-viewer-location-popup-container')
  }).setDOMContent(popupMount.element)

  marker = new maplibregl.Marker({
    element: createMarkerElement(),
    anchor: 'center'
  })
    .setLngLat([props.location.longitude, props.location.latitude])
    .setPopup(popup)
    .addTo(props.map)
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

<style>
.maplibre-viewer-location-dot {
  width: 18px;
  height: 18px;
  border-radius: 50%;
  background: #0ea5e9;
  border: 3px solid #ffffff;
  box-shadow: 0 0 0 7px rgba(14, 165, 233, 0.22), 0 2px 8px rgba(15, 23, 42, 0.35);
}

.maplibre-viewer-location-dot.fallback {
  background: #f59e0b;
  box-shadow: 0 0 0 7px rgba(245, 158, 11, 0.24), 0 2px 8px rgba(15, 23, 42, 0.35);
}
</style>
