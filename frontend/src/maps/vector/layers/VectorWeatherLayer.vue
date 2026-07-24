<template></template>

<script setup>
import { onBeforeUnmount, watch } from 'vue'
import { storeToRefs } from 'pinia'
import maplibregl from 'maplibre-gl'
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
import { isMapLibreMap, toFiniteNumber } from '@/maps/vector/utils/maplibreLayerUtils'

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
const markers = []

const clearMarkers = () => {
  markers.forEach(marker => marker.remove())
  markers.length = 0
  if (isMapLibreMap(props.map)) {
    props.map.getCanvas().style.cursor = ''
  }
}

const isHighlighted = (sample) => (
  props.highlightedItem && isWeatherSampleInTimelineItem(sample, props.highlightedItem)
)

const createMarkerElement = (sample) => {
  const info = getWeatherCodeInfo(sample.weatherCode)
  const element = document.createElement('button')
  element.type = 'button'
  element.className = [
    'weather-map-marker',
    `weather-map-marker--${info.severity}`,
    isHighlighted(sample) ? 'weather-map-marker--highlighted' : ''
  ].filter(Boolean).join(' ')
  element.title = buildWeatherSampleTitle(sample, measureUnit.value || 'METRIC', timezone)
  element.innerHTML = `<i class="${info.icon}"></i>`
  return element
}

const createPopupElement = (sample) => {
  const info = getWeatherCodeInfo(sample.weatherCode)
  const unit = measureUnit.value || 'METRIC'
  const precipitation = formatPrecipitation(sample.precipitation, unit)
  const precipitationRow = precipitation
    ? `<div class="weather-map-popup-row"><span>Precipitation</span><strong>${escapeHtml(precipitation)}</strong></div>`
    : ''
  const root = document.createElement('div')
  root.className = 'weather-map-popup'
  root.innerHTML = `
    <div class="weather-map-popup-title"><i class="${info.icon}"></i> ${escapeHtml(info.label)}</div>
    <div class="weather-map-popup-row"><span>Observed</span><strong>${escapeHtml(formatObservedTime(sample, timezone))}</strong></div>
    <div class="weather-map-popup-row"><span>Temperature</span><strong>${escapeHtml(formatTemperature(sample.temperature, unit) || 'n/a')}</strong></div>
    ${precipitationRow}
    <div class="weather-map-popup-row"><span>Wind</span><strong>${escapeHtml(formatWindSpeed(sample.windSpeed, unit) || 'n/a')}</strong></div>
  `
  return root
}

const renderMarkers = () => {
  clearMarkers()
  if (!props.visible || !isMapLibreMap(props.map)) {
    return
  }

  props.samples.forEach((sample) => {
    const latitude = toFiniteNumber(sample.latitude)
    const longitude = toFiniteNumber(sample.longitude)
    if (latitude === null || longitude === null) {
      return
    }

    const marker = new maplibregl.Marker({
      element: createMarkerElement(sample),
      anchor: 'center'
    })
      .setLngLat([longitude, latitude])
      .setPopup(new maplibregl.Popup({
        closeButton: true,
        closeOnClick: true,
        maxWidth: '260px',
        offset: 18,
        className: 'weather-map-popup-container'
      }).setDOMContent(createPopupElement(sample)))
      .addTo(props.map)

    markers.push(marker)
  })
}

watch(
  () => [props.samples, props.visible, props.highlightedItem, measureUnit.value],
  () => renderMarkers(),
  { deep: true, immediate: true }
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
  cursor: pointer;
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

.weather-map-popup-container .maplibregl-popup-content {
  background: var(--gp-surface-white);
  color: var(--gp-text-primary);
  border: 1px solid var(--gp-border-medium);
  box-shadow: var(--gp-shadow-medium);
}

.weather-map-popup-container .maplibregl-popup-close-button {
  color: var(--gp-text-secondary);
}

.weather-map-popup-container.maplibregl-popup-anchor-bottom .maplibregl-popup-tip {
  border-top-color: var(--gp-surface-white);
}

.weather-map-popup-container.maplibregl-popup-anchor-top .maplibregl-popup-tip {
  border-bottom-color: var(--gp-surface-white);
}

.weather-map-popup-container.maplibregl-popup-anchor-left .maplibregl-popup-tip {
  border-right-color: var(--gp-surface-white);
}

.weather-map-popup-container.maplibregl-popup-anchor-right .maplibregl-popup-tip {
  border-left-color: var(--gp-surface-white);
}
</style>
