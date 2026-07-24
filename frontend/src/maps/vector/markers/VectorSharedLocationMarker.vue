<template>
  <!-- Native MapLibre marker component: render managed via script -->
</template>

<script setup>
import { onUnmounted, watch } from 'vue'
import maplibregl from 'maplibre-gl'
import { useTimezone } from '@/composables/useTimezone'
import { isMapLibreMap } from '@/maps/vector/utils/maplibreLayerUtils'
import MapInfoPopup from '@/maps/shared/popups/MapInfoPopup.vue'
import { mountMapPopup } from '@/maps/shared/popups/mountMapPopup'
import { buildSharedLocationPopupModel } from '@/maps/shared/popups/locationPopupModels'
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
  latitude: {
    type: Number,
    required: true
  },
  longitude: {
    type: Number,
    required: true
  },
  shareData: {
    type: Object,
    default: () => ({})
  },
  avatarUrl: {
    type: String,
    default: null
  },
  openPopup: {
    type: Boolean,
    default: true
  }
})

let marker = null
let popup = null
let popupMount = null

const createMarkerElement = () => {
  const element = document.createElement('div')

  if (props.avatarUrl) {
    element.className = 'maplibre-avatar-icon-container'
    element.innerHTML = `<img src="${props.avatarUrl}" class="maplibre-avatar-icon" alt="Avatar">`
    return { element, anchor: 'bottom' }
  }

  element.className = 'maplibre-shared-location-dot'
  return { element, anchor: 'center' }
}

const removeMarker = () => {
  popupMount?.unmount?.()
  popupMount = null
  if (marker) {
    marker.remove()
    marker = null
  }

  popup = null
}

const createMarker = () => {
  if (!isMapLibreMap(props.map)) {
    removeMarker()
    return
  }

  removeMarker()

  const markerConfig = createMarkerElement()
  popupMount = mountMapPopup(
    MapInfoPopup,
    buildSharedLocationPopupModel(props.shareData, { timezone })
  )
  popup = new maplibregl.Popup({
    offset: MAP_POPUP_OFFSET,
    maxWidth: MAP_POPUP_COMPACT_MAX_WIDTH,
    className: getMapPopupVariantClassName('compact', 'gp-shared-location-popup-container')
  }).setDOMContent(popupMount.element)

  marker = new maplibregl.Marker(markerConfig)
    .setLngLat([props.longitude, props.latitude])
    .setPopup(popup)
    .addTo(props.map)

  if (props.openPopup) {
    marker.togglePopup()
  }
}

watch(
  () => [props.map, props.latitude, props.longitude, props.shareData, props.openPopup, props.avatarUrl],
  () => {
    createMarker()
  },
  { immediate: true, deep: true }
)

onUnmounted(() => {
  removeMarker()
})
</script>

<style>
.maplibre-avatar-icon-container {
  background: transparent;
  border: none;
}

.maplibre-avatar-icon {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  border: 3px solid #ffffff;
  box-shadow: 0 2px 5px rgba(0, 0, 0, 0.5);
}

.maplibre-shared-location-dot {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  background: #9c27b0;
  border: 3px solid #ffffff;
  box-shadow: 0 2px 5px rgba(0, 0, 0, 0.25);
}
</style>
