<template>
  <BaseLayer
    ref="baseLayerRef"
    :map="map"
    :visible="visible"
    @layer-ready="renderMarkers"
  />
</template>

<script setup>
import { onBeforeUnmount, ref, watch } from 'vue'
import { storeToRefs } from 'pinia'
import L from 'leaflet'
import BaseLayer from '@/components/maps/layers/BaseLayer.vue'
import { useAuthStore } from '@/stores/auth'
import { useTimezone } from '@/composables/useTimezone'
import {
  buildWeatherSampleTitle,
  formatObservedTime,
  formatPrecipitation,
  formatTemperature,
  formatWindSpeed,
  getWeatherCodeInfo,
  isWeatherSampleInTimelineItem
} from '@/utils/weatherDisplay'

const props = defineProps({
  map: {
    type: Object,
    required: true
  },
  samples: {
    type: Array,
    default: () => []
  },
  visible: {
    type: Boolean,
    default: false
  },
  highlightedItem: {
    type: Object,
    default: null
  }
})

const authStore = useAuthStore()
const { measureUnit } = storeToRefs(authStore)
const timezone = useTimezone()
const baseLayerRef = ref(null)
const markers = []

const clearMarkers = () => {
  if (!baseLayerRef.value) return
  markers.forEach(marker => baseLayerRef.value.removeFromLayer(marker))
  markers.length = 0
}

const isHighlighted = (sample) => (
  props.highlightedItem && isWeatherSampleInTimelineItem(sample, props.highlightedItem)
)

const createIcon = (sample) => {
  const info = getWeatherCodeInfo(sample.weatherCode)
  const highlighted = isHighlighted(sample)
  return L.divIcon({
    className: 'weather-marker-icon',
    html: `<div class="weather-map-marker weather-map-marker--${info.severity} ${highlighted ? 'weather-map-marker--highlighted' : ''}"><i class="${info.icon}"></i></div>`,
    iconSize: L.point(highlighted ? 34 : 28, highlighted ? 34 : 28),
    iconAnchor: L.point(highlighted ? 17 : 14, highlighted ? 17 : 14)
  })
}

const createPopupHtml = (sample) => {
  const info = getWeatherCodeInfo(sample.weatherCode)
  const unit = measureUnit.value || 'METRIC'
  const precipitation = formatPrecipitation(sample.precipitation, unit)
  const precipitationRow = precipitation
    ? `<div class="weather-map-popup-row"><span>Precipitation</span><strong>${escapeHtml(precipitation)}</strong></div>`
    : ''

  return `
    <div class="weather-map-popup">
      <div class="weather-map-popup-title"><i class="${info.icon}"></i> ${escapeHtml(info.label)}</div>
      <div class="weather-map-popup-row"><span>Observed</span><strong>${escapeHtml(formatObservedTime(sample, timezone))}</strong></div>
      <div class="weather-map-popup-row"><span>Temperature</span><strong>${escapeHtml(formatTemperature(sample.temperature, unit) || 'n/a')}</strong></div>
      ${precipitationRow}
      <div class="weather-map-popup-row"><span>Wind</span><strong>${escapeHtml(formatWindSpeed(sample.windSpeed, unit) || 'n/a')}</strong></div>
    </div>
  `
}

const renderMarkers = () => {
  if (!baseLayerRef.value) return
  clearMarkers()

  props.samples.forEach((sample) => {
    const latitude = Number(sample.latitude)
    const longitude = Number(sample.longitude)
    if (!Number.isFinite(latitude) || !Number.isFinite(longitude)) {
      return
    }

    const marker = L.marker([latitude, longitude], {
      icon: createIcon(sample),
      keyboard: false,
      title: buildWeatherSampleTitle(sample, measureUnit.value || 'METRIC', timezone)
    })

    marker.bindPopup(createPopupHtml(sample), {
      maxWidth: 260,
      className: 'weather-map-popup-container'
    })
    markers.push(marker)
    baseLayerRef.value.addToLayer(marker)
  })
}

watch(
  () => [props.samples, props.highlightedItem, measureUnit.value],
  () => renderMarkers(),
  { deep: true }
)

onBeforeUnmount(() => {
  clearMarkers()
})

function escapeHtml(value) {
  return String(value ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#039;')
}
</script>

<style>
.weather-map-marker {
  width: 28px;
  height: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 999px;
  background: #f8fafc;
  border: 2px solid #0f766e;
  color: #0f766e;
  box-shadow: 0 2px 8px rgba(15, 23, 42, 0.28);
  box-sizing: border-box;
}

.weather-map-marker--clear {
  border-color: #ca8a04;
  color: #ca8a04;
}

.weather-map-marker--rain,
.weather-map-marker--storm,
.weather-map-marker--snow {
  border-color: #2563eb;
  color: #2563eb;
}

.weather-map-marker--highlighted {
  width: 34px;
  height: 34px;
  border-width: 3px;
  box-shadow: 0 0 0 4px rgba(37, 99, 235, 0.18), 0 3px 10px rgba(15, 23, 42, 0.32);
}

.weather-map-popup {
  min-width: 210px;
  color: var(--gp-text-primary);
}

.weather-map-popup-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-weight: 800;
  margin-bottom: 8px;
}

.weather-map-popup-row {
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 12px;
  margin-top: 5px;
  font-size: 0.82rem;
}

.weather-map-popup-row span {
  color: var(--gp-text-secondary);
}

.weather-map-popup-container .leaflet-popup-content-wrapper,
.weather-map-popup-container .leaflet-popup-tip {
  background: var(--gp-surface-white);
  color: var(--gp-text-primary);
  border: 1px solid var(--gp-border-medium);
}

.weather-map-popup-container .leaflet-popup-content {
  color: var(--gp-text-primary);
}

.weather-map-popup-container .leaflet-popup-close-button {
  color: var(--gp-text-secondary);
}
</style>
