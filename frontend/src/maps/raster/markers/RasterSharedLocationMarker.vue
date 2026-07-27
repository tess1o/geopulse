<template>
  <!-- This component doesn't render anything in the template - it manages map layers directly -->
</template>

<script setup>
import {ref, watch, onUnmounted} from 'vue'
import { useTimezone } from '@/composables/useTimezone'
import MapInfoPopup from '@/maps/shared/popups/MapInfoPopup.vue'
import { mountMapPopup } from '@/maps/shared/popups/mountMapPopup'
import { buildSharedLocationPopupModel } from '@/maps/shared/popups/locationPopupModels'
import {
  getMapPopupVariantClassName,
  MAP_POPUP_COMPACT_MAX_WIDTH_PX
} from '@/maps/shared/popups/mapPopupOptions'

const timezone = useTimezone()
import L from 'leaflet'

// Props
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
  markerType: {
    type: String,
    default: ''
  },
  openPopup: {
    type: Boolean,
    default: true
  }
})

// Local state
let marker = null
let popupMount = null

const createMarker = () => {
  popupMount?.unmount?.()
  popupMount = null
  if (marker) {
    props.map.removeLayer(marker)
  }

  if (props.avatarUrl) {
    const markerClassName = props.markerType
      ? `leaflet-avatar-icon-container ${props.markerType}-marker`
      : 'leaflet-avatar-icon-container'
    const icon = L.divIcon({
      html: `<img src="${props.avatarUrl}" class="leaflet-avatar-icon">`,
      className: markerClassName,
      iconSize: [40, 40],
      iconAnchor: [20, 40],
      popupAnchor: [0, -40]
    });
    marker = L.marker([props.latitude, props.longitude], { icon, markerType: props.markerType });
  } else {
    marker = L.circleMarker([props.latitude, props.longitude], {
      radius: 12,
      fillColor: '#9c27b0',  // purple
      color: '#ffffff',
      weight: 3,
      opacity: 1,
      fillOpacity: 0.9,
      className: props.markerType ? `${props.markerType}-marker` : undefined,
      markerType: props.markerType
    });
  }

  popupMount = mountMapPopup(
    MapInfoPopup,
    buildSharedLocationPopupModel(props.shareData, { timezone })
  )
  marker.addTo(props.map)

  const markerElement = marker.getElement?.()
  if (props.markerType && typeof markerElement?.setAttribute === 'function') {
    markerElement.setAttribute('data-marker-type', props.markerType)
  }

  marker.bindPopup(popupMount.element, {
    maxWidth: MAP_POPUP_COMPACT_MAX_WIDTH_PX,
    className: getMapPopupVariantClassName('compact', 'gp-shared-location-popup-container')
  })

  if (props.openPopup) {
    marker.openPopup()
  }
}

// Watch for prop changes and recreate marker
watch(() => [props.latitude, props.longitude, props.shareData, props.openPopup, props.avatarUrl, props.markerType], createMarker, {immediate: true})

onUnmounted(() => {
  popupMount?.unmount?.()
  popupMount = null
  if (marker && props.map) {
    props.map.removeLayer(marker)
  }
})
</script>

<style>
.leaflet-avatar-icon-container {
  background: transparent;
  border: none;
}

.leaflet-avatar-icon {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  border: 3px solid #ffffff;
  box-shadow: 0 2px 5px rgba(0,0,0,0.5);
}

</style>
