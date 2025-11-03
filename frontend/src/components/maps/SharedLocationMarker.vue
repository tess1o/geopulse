<template>
  <!-- This component doesn't render anything in the template - it manages map layers directly -->
</template>

<script setup>
import {ref, watch, onUnmounted} from 'vue'
import { useTimezone } from '@/composables/useTimezone'

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
  openPopup: {
    type: Boolean,
    default: true
  }
})

// Local state
let marker = null

const createMarker = () => {
  if (marker) {
    props.map.removeLayer(marker)
  }

  if (props.avatarUrl) {
    const icon = L.divIcon({
      html: `<img src="${props.avatarUrl}" class="leaflet-avatar-icon">`,
      className: 'leaflet-avatar-icon-container',
      iconSize: [40, 40],
      iconAnchor: [20, 40],
      popupAnchor: [0, -40]
    });
    marker = L.marker([props.latitude, props.longitude], { icon });
  } else {
    marker = L.circleMarker([props.latitude, props.longitude], {
      radius: 12,
      fillColor: '#9c27b0',  // purple
      color: '#ffffff',
      weight: 3,
      opacity: 1,
      fillOpacity: 0.9
    });
  }

  marker.addTo(props.map)
      .bindPopup(`
      <div style="text-align: center;">
        <strong>${props.shareData.sharedBy}</strong><br/>
        ${props.shareData.description ? `<em>${props.shareData.description}</em><br/>` : ''}
        <small>Last seen ${timezone.timeAgo(props.shareData.sharedAt)}</small>
      </div>
    `)

  if (props.openPopup) {
    marker.openPopup()
  }
}

// Watch for prop changes and recreate marker
watch(() => [props.latitude, props.longitude, props.shareData, props.openPopup, props.avatarUrl], createMarker, {immediate: true})

onUnmounted(() => {
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