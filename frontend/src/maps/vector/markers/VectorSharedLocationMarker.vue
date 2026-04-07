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
  popup = new maplibregl.Popup({ offset: 18 }).setHTML(`
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

.shared-marker-popup {
  text-align: center;
}

.shared-telemetry {
  margin-top: 0.5rem;
  padding-top: 0.4rem;
  border-top: 1px solid #e5e7eb;
  text-align: left;
  min-width: 180px;
}

.shared-telemetry-title {
  font-size: 0.7rem;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  color: #6b7280;
  margin-bottom: 0.25rem;
}

.shared-telemetry-row {
  font-size: 0.75rem;
  line-height: 1.25;
}

.shared-telemetry-label {
  color: #4b5563;
  margin-right: 0.25rem;
}

.shared-telemetry-value {
  color: #111827;
  font-weight: 600;
}
</style>
