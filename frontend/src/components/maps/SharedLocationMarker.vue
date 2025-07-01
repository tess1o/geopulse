<template>
  <!-- This component doesn't render anything in the template - it manages map layers directly -->
</template>

<script setup>
import { ref, watch, onUnmounted } from 'vue'
import { timeAgo } from '@/utils/dateHelpers'
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
  
  marker = L.marker([props.latitude, props.longitude])
    .addTo(props.map)
    .bindPopup(`
      <div style="text-align: center;">
        <strong>${props.shareData.shareName || 'Shared Location'}</strong><br/>
        ${props.shareData.description ? `<em>${props.shareData.description}</em><br/>` : ''}
        <small>Last seen ${timeAgo(props.shareData.sharedAt)}</small>
      </div>
    `)
  
  if (props.openPopup) {
    marker.openPopup()
  }
}

// Watch for prop changes and recreate marker
watch(() => [props.latitude, props.longitude, props.shareData, props.openPopup], createMarker, { immediate: true })

onUnmounted(() => {
  if (marker && props.map) {
    props.map.removeLayer(marker)
  }
})
</script>