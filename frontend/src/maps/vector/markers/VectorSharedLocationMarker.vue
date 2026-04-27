<template>
  <!-- Native MapLibre marker component: render managed via script -->
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

const escapeHtml = (value) => {
  return String(value ?? '')
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#39;')
}

const formatTelemetryValue = (item) => {
  if (!item) return '-'
  const value = item.value ?? '-'
  if (!item.unit) return value
  if (item.unit === '%') return `${value}${item.unit}`
  return `${value} ${item.unit}`
}

const buildTelemetryHtml = () => {
  if (!Array.isArray(props.shareData?.telemetry) || props.shareData.telemetry.length === 0) {
    return ''
  }

  const rows = props.shareData.telemetry
    .map((item) => `
      <div class="shared-telemetry-row">
        <span class="shared-telemetry-label">${escapeHtml(item.label)}:</span>
        <span class="shared-telemetry-value">${escapeHtml(formatTelemetryValue(item))}</span>
      </div>
    `)
    .join('')

  return `
    <div class="shared-telemetry">
      <div class="shared-telemetry-title">Telemetry</div>
      ${rows}
    </div>
  `
}

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
  popup = new maplibregl.Popup({
    offset: 18,
    className: 'gp-shared-location-popup-container'
  }).setHTML(`
    <div class="shared-marker-popup">
      <strong>${escapeHtml(props.shareData.sharedBy)}</strong><br/>
      ${props.shareData.description ? `<em>${escapeHtml(props.shareData.description)}</em><br/>` : ''}
      <small>Last seen ${escapeHtml(timezone.timeAgo(props.shareData.sharedAt))}</small>
      ${buildTelemetryHtml()}
    </div>
  `)

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

.gp-shared-location-popup-container .maplibregl-popup-content {
  padding: 0.65rem 0.75rem;
  background: rgba(255, 255, 255, 0.97);
  border: 1px solid rgba(148, 163, 184, 0.65);
  color: #0f172a;
}

.gp-shared-location-popup-container .maplibregl-popup-tip {
  border-top-color: rgba(255, 255, 255, 0.97);
}

.gp-shared-location-popup-container .maplibregl-popup-close-button {
  color: #64748b;
}

.gp-shared-location-popup-container .maplibregl-popup-close-button:hover {
  color: #0f172a;
}

.gp-shared-location-popup-container .shared-marker-popup {
  text-align: center;
}

.gp-shared-location-popup-container .shared-telemetry {
  margin-top: 0.5rem;
  padding-top: 0.4rem;
  border-top: 1px solid #e5e7eb;
  text-align: left;
  min-width: 180px;
}

.gp-shared-location-popup-container .shared-telemetry-title {
  font-size: 0.7rem;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  color: #6b7280;
  margin-bottom: 0.25rem;
}

.gp-shared-location-popup-container .shared-telemetry-row {
  font-size: 0.75rem;
  line-height: 1.25;
}

.gp-shared-location-popup-container .shared-telemetry-label {
  color: #4b5563;
  margin-right: 0.25rem;
}

.gp-shared-location-popup-container .shared-telemetry-value {
  color: #111827;
  font-weight: 600;
}

.p-dark .gp-shared-location-popup-container .maplibregl-popup-content {
  background: linear-gradient(135deg, rgba(15, 23, 42, 0.97), rgba(30, 41, 59, 0.94));
  border: 1px solid rgba(71, 85, 105, 0.55);
  color: rgba(255, 255, 255, 0.95);
  box-shadow: 0 10px 30px rgba(2, 6, 23, 0.45);
}

.p-dark .gp-shared-location-popup-container .maplibregl-popup-tip {
  border-top-color: rgba(15, 23, 42, 0.95);
}

.p-dark .gp-shared-location-popup-container .maplibregl-popup-close-button {
  color: rgba(226, 232, 240, 0.9);
}

.p-dark .gp-shared-location-popup-container .maplibregl-popup-close-button:hover {
  color: #ffffff;
}

.p-dark .gp-shared-location-popup-container .shared-telemetry {
  border-top-color: rgba(255, 255, 255, 0.2);
}

.p-dark .gp-shared-location-popup-container .shared-telemetry-title,
.p-dark .gp-shared-location-popup-container .shared-telemetry-label {
  color: rgba(255, 255, 255, 0.8);
}

.p-dark .gp-shared-location-popup-container .shared-telemetry-value {
  color: rgba(255, 255, 255, 0.95);
}
</style>
