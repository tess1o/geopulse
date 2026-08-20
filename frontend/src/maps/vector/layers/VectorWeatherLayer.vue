<template></template>

<script setup>
import { onBeforeUnmount, watch } from 'vue'
import { storeToRefs } from 'pinia'
import maplibregl from 'maplibre-gl'
import { useAuthStore } from '@/stores/auth'
import { useTimezone } from '@/composables/useTimezone'
import {
  buildWeatherSampleTitle,
  getWeatherCodeInfo,
  isWeatherSampleInTimelineItem
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
  }
})

const authStore = useAuthStore()
const { distanceUnit, temperatureUnit } = storeToRefs(authStore)
const timezone = useTimezone()
const markerEntries = []

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
  element.title = buildWeatherSampleTitle(sample, {
    distanceUnit: distanceUnit.value || 'KILOMETERS',
    temperatureUnit: temperatureUnit.value || 'CELSIUS',
    timezone
  })
  element.innerHTML = `<i class="${info.icon}"></i>`
  return element
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

    const popupMount = mountMapPopup(
      MapInfoPopup,
      buildWeatherPopupModel(sample, {
        distanceUnit: distanceUnit.value || 'KILOMETERS',
        temperatureUnit: temperatureUnit.value || 'CELSIUS',
        timezone
      })
    )
    const marker = new maplibregl.Marker({
      element: createMarkerElement(sample),
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
  })
}

watch(
  () => [props.samples, props.visible, props.highlightedItem, distanceUnit.value, temperatureUnit.value],
  () => renderMarkers(),
  { deep: true, immediate: true }
)

onBeforeUnmount(() => {
  clearMarkers()
})
</script>
