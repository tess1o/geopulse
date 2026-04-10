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

const escapeHtml = (value) => {
  return String(value ?? '')
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('\"', '&quot;')
    .replaceAll('\'', '&#39;')
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
      <div class=\"shared-telemetry-row\">
        <span class=\"shared-telemetry-label\">${escapeHtml(item.label)}:</span>
        <span class=\"shared-telemetry-value\">${escapeHtml(formatTelemetryValue(item))}</span>
      </div>
    `)
    .join('')

  return `
    <div class=\"shared-telemetry\">
      <div class=\"shared-telemetry-title\">Telemetry</div>
      ${rows}
    </div>
  `
}

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
      <div class="shared-marker-popup">
        <strong>${escapeHtml(props.shareData.sharedBy)}</strong><br/>
        ${props.shareData.description ? `<em>${escapeHtml(props.shareData.description)}</em><br/>` : ''}
        <small>Last seen ${escapeHtml(timezone.timeAgo(props.shareData.sharedAt))}</small>
        ${buildTelemetryHtml()}
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
