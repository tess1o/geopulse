<template></template>

<script setup>
import { onBeforeUnmount, watch } from 'vue'
import { storeToRefs } from 'pinia'
import * as maplibregl from 'maplibre-gl'
import { useAuthStore } from '@/stores/auth'
import { useTimezone } from '@/composables/useTimezone'
import { t } from '@/locales'
import {
  buildWeatherSampleTitle,
  formatObservedTime,
  formatTemperature,
  getWeatherCodeInfo,
  isWeatherSampleInTimelineItem,
  summarizeWeatherSamples
} from '@/utils/weatherDisplay'
import { isMapLibreMap, toFiniteNumber } from '@/maps/vector/utils/maplibreLayerUtils'
import MapInfoPopup from '@/maps/shared/popups/MapInfoPopup.vue'
import { mountMapPopup } from '@/maps/shared/popups/mountMapPopup'
import { buildWeatherPopupModel } from '@/maps/shared/popups/weatherPopupModel'
import {
  getMapPopupVariantClassName,
  MAP_POPUP_COMPACT_MAX_WIDTH,
  MAP_POPUP_OFFSET
} from '@/maps/shared/popups/mapPopupOptions'
import '@/maps/shared/styles/weatherMapMarkers.css'

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
  },
  // When true, the cross-type pass decides which (grouped) weather markers are
  // drawn - via setPlacedGroups() - so weather never overlaps other markers.
  // Otherwise every sample is drawn as its own marker.
  managed: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['groups-change'])

const authStore = useAuthStore()
const { distanceUnit, temperatureUnit } = storeToRefs(authStore)
const timezone = useTimezone()
const markerEntries = []
// Managed mode: [{ indices, latitude, longitude, highlighted }] into props.samples,
// or null until the cross-type pass has placed the current samples.
let placedGroups = null

const unitOptions = () => ({
  distanceUnit: distanceUnit.value || 'KILOMETERS',
  temperatureUnit: temperatureUnit.value || 'CELSIUS',
  timezone
})

const clearMarkers = () => {
  markerEntries.forEach(({ marker, popupMount }) => {
    popupMount?.unmount?.()
    marker.remove()
  })
  markerEntries.length = 0
  if (isMapLibreMap(props.map)) {
    props.map.getCanvas().style.cursor = ''
  }
}

const isHighlighted = (sample) => Boolean(
  props.highlightedItem && isWeatherSampleInTimelineItem(sample, props.highlightedItem)
)

const createMarkerElement = (samples, highlighted) => {
  const summary = samples.length > 1 ? summarizeWeatherSamples(samples) : null
  const info = summary || getWeatherCodeInfo(samples[0].weatherCode)
  const element = document.createElement('button')
  element.type = 'button'
  element.className = [
    'weather-map-marker',
    `weather-map-marker--${info.severity}`,
    samples.length > 1 ? 'weather-map-marker--group' : '',
    highlighted ? 'weather-map-marker--highlighted' : ''
  ].filter(Boolean).join(' ')
  element.title = samples.length > 1
    ? t('maps.popups.weather.samplesHere', { count: samples.length }, samples.length)
    : buildWeatherSampleTitle(samples[0], unitOptions())
  element.innerHTML = `<i class="${info.icon}"></i>`
  return element
}

// One sample: its full details. Several: one row per sample, in time order.
const buildPopupModel = (samples) => {
  if (samples.length === 1) {
    return buildWeatherPopupModel(samples[0], unitOptions())
  }

  const summary = summarizeWeatherSamples(samples)
  const temperatureUnitValue = unitOptions().temperatureUnit
  return {
    title: t('maps.popups.weather.samplesHere', { count: samples.length }, samples.length),
    iconClass: summary?.icon || getWeatherCodeInfo(null).icon,
    rows: [...samples]
      .sort((left, right) => new Date(left.observedAt) - new Date(right.observedAt))
      .map((sample) => ({
        label: formatObservedTime(sample, timezone),
        value: [
          t(getWeatherCodeInfo(sample.weatherCode).key),
          formatTemperature(sample.temperature, temperatureUnitValue)
        ].filter(Boolean).join(' · ')
      })),
    variant: 'compact'
  }
}

const renderMarker = ({ samples, latitude, longitude, highlighted }) => {
  const popupMount = mountMapPopup(MapInfoPopup, buildPopupModel(samples))
  const marker = new maplibregl.Marker({
    element: createMarkerElement(samples, highlighted),
    anchor: 'center'
  })
    .setLngLat([longitude, latitude])
    .setPopup(new maplibregl.Popup({
      closeButton: true,
      closeOnClick: true,
      maxWidth: MAP_POPUP_COMPACT_MAX_WIDTH,
      offset: MAP_POPUP_OFFSET,
      className: getMapPopupVariantClassName('compact', 'weather-map-popup-container')
    }).setDOMContent(popupMount.element))
    .addTo(props.map)

  markerEntries.push({ marker, popupMount })
}

const renderMarkers = () => {
  clearMarkers()
  if (!props.visible || !isMapLibreMap(props.map)) {
    return
  }

  if (props.managed) {
    ;(placedGroups || []).forEach((group) => {
      const samples = group.indices.map((index) => props.samples[index]).filter(Boolean)
      if (samples.length > 0) {
        renderMarker({ ...group, samples })
      }
    })
    return
  }

  props.samples.forEach((sample) => {
    const latitude = toFiniteNumber(sample.latitude)
    const longitude = toFiniteNumber(sample.longitude)
    if (latitude !== null && longitude !== null) {
      renderMarker({ samples: [sample], latitude, longitude, highlighted: isHighlighted(sample) })
    }
  })
}

// Input for placeWeatherMarkers(): the samples to place and which belong to the highlighted item.
const getPlacementInput = () => {
  if (!props.visible || !isMapLibreMap(props.map)) {
    return { samples: [], highlightedIndices: new Set() }
  }

  const highlightedIndices = new Set()
  props.samples.forEach((sample, index) => {
    if (isHighlighted(sample)) {
      highlightedIndices.add(index)
    }
  })
  return { samples: props.samples, highlightedIndices }
}

const getPlacementKey = (groups) => (groups || [])
  .map((group) => `${group.highlighted ? 'h' : ''}${group.indices.join(',')}`)
  .join('|')

const setPlacedGroups = (groups) => {
  if (placedGroups && getPlacementKey(groups) === getPlacementKey(placedGroups)) {
    return
  }

  placedGroups = groups
  renderMarkers()
}

watch(
  () => props.samples,
  () => {
    // Placed indices point into the previous samples array.
    placedGroups = null
  }
)

watch(
  () => [props.samples, props.visible, props.highlightedItem, props.managed, distanceUnit.value, temperatureUnit.value],
  () => {
    renderMarkers()
    if (props.managed) {
      emit('groups-change')
    }
  },
  { deep: true, immediate: true }
)

onBeforeUnmount(() => {
  clearMarkers()
})

defineExpose({
  getPlacementInput,
  setPlacedGroups
})
</script>
