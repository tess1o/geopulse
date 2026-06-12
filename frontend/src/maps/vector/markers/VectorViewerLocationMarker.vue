<template>
  <!-- Native MapLibre marker managed via script. -->
</template>

<script setup>
import { onUnmounted, watch } from 'vue'
import maplibregl from 'maplibre-gl'
import { useTimezone } from '@/composables/useTimezone'
import { isMapLibreMap } from '@/maps/vector/utils/maplibreLayerUtils'

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
  if (marker) {
    marker.remove()
  }
  marker = null
  popup = null
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

  popup = new maplibregl.Popup({
    offset: 16,
    className: 'gp-viewer-location-popup-container'
  }).setHTML(buildPopupHtml())

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

.gp-viewer-location-popup-container .maplibregl-popup-content {
  padding: 0.6rem 0.7rem;
  background: rgba(255, 255, 255, 0.97);
  border: 1px solid rgba(148, 163, 184, 0.65);
  color: #0f172a;
}

.gp-viewer-location-popup-container .viewer-location-popup {
  text-align: center;
}

.p-dark .gp-viewer-location-popup-container .maplibregl-popup-content {
  background: linear-gradient(135deg, rgba(15, 23, 42, 0.97), rgba(30, 41, 59, 0.94));
  border: 1px solid rgba(71, 85, 105, 0.55);
  color: rgba(255, 255, 255, 0.95);
  box-shadow: 0 10px 30px rgba(2, 6, 23, 0.45);
}

.p-dark .gp-viewer-location-popup-container .maplibregl-popup-tip {
  border-top-color: rgba(15, 23, 42, 0.95);
}

.p-dark .gp-viewer-location-popup-container .maplibregl-popup-close-button {
  color: rgba(226, 232, 240, 0.9);
}

.p-dark .gp-viewer-location-popup-container .maplibregl-popup-close-button:hover {
  color: #ffffff;
}
</style>
