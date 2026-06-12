<template>
  <!-- Marker is managed directly on the Leaflet map. -->
</template>

<script setup>
import { onUnmounted, watch } from 'vue'
import L from 'leaflet'
import { useTimezone } from '@/composables/useTimezone'

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

const hasValidCoordinates = () => {
  return Number.isFinite(Number(props.location?.latitude)) && Number.isFinite(Number(props.location?.longitude))
}

const escapeHtml = (value) => {
  return String(value ?? '')
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#39;')
}

const removeMarker = () => {
  if (accuracyCircle && props.map) {
    props.map.removeLayer(accuracyCircle)
  }
  if (marker && props.map) {
    props.map.removeLayer(marker)
  }
  accuracyCircle = null
  marker = null
}

const buildPopupHtml = () => {
  const isFallback = props.location.source === 'fallback'
  const title = props.location.label || (isFallback ? 'Your last known GeoPulse location' : 'Your location')
  const timeLabel = props.location.timestamp
    ? `${isFallback ? 'Last recorded' : 'Updated'} ${timezone.timeAgo(props.location.timestamp)}`
    : ''
  const accuracyLabel = props.location.accuracy
    ? `<small>Accuracy about ${Math.round(props.location.accuracy)} m</small>`
    : ''

  return `
    <div class="viewer-location-popup">
      <strong>${escapeHtml(title)}</strong>
      ${timeLabel ? `<br/><small>${escapeHtml(timeLabel)}</small>` : ''}
      ${accuracyLabel ? `<br/>${accuracyLabel}` : ''}
    </div>
  `
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

  marker
    .addTo(props.map)
    .bindPopup(buildPopupHtml(), {
      className: 'gp-viewer-location-popup-container'
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

<style>
.viewer-location-popup {
  text-align: center;
}

.p-dark .gp-viewer-location-popup-container .leaflet-popup-content-wrapper {
  background: linear-gradient(135deg, rgba(15, 23, 42, 0.97), rgba(30, 41, 59, 0.94));
  border: 1px solid rgba(71, 85, 105, 0.55);
  color: rgba(255, 255, 255, 0.95);
  box-shadow: 0 10px 30px rgba(2, 6, 23, 0.45);
}

.p-dark .gp-viewer-location-popup-container .leaflet-popup-tip {
  background: rgba(15, 23, 42, 0.95);
}

.p-dark .gp-viewer-location-popup-container .leaflet-popup-close-button {
  color: rgba(226, 232, 240, 0.9);
}

.p-dark .gp-viewer-location-popup-container .leaflet-popup-close-button:hover {
  color: #ffffff;
}
</style>
