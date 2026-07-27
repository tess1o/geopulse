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
  getWeatherCodeInfo,
  isWeatherSampleInTimelineItem
} from '@/utils/weatherDisplay'
import MapInfoPopup from '@/maps/shared/popups/MapInfoPopup.vue'
import { mountMapPopup } from '@/maps/shared/popups/mountMapPopup'
import { buildWeatherPopupModel } from '@/maps/shared/popups/weatherPopupModel'
import {
  getMapPopupVariantClassName,
  MAP_POPUP_COMPACT_MAX_WIDTH_PX
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
const { measureUnit } = storeToRefs(authStore)
const timezone = useTimezone()
const baseLayerRef = ref(null)
const markerEntries = []

const clearMarkers = () => {
  if (!baseLayerRef.value) return
  markerEntries.forEach(({ marker, popupMount }) => {
    popupMount?.unmount?.()
    baseLayerRef.value.removeFromLayer(marker)
  })
  markerEntries.length = 0
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
    const popupMount = mountMapPopup(
      MapInfoPopup,
      buildWeatherPopupModel(sample, {
        unit: measureUnit.value || 'METRIC',
        timezone
      })
    )

    marker.bindPopup(popupMount.element, {
      maxWidth: MAP_POPUP_COMPACT_MAX_WIDTH_PX,
      className: getMapPopupVariantClassName('compact', 'weather-map-popup-container')
    })
    markerEntries.push({ marker, popupMount })
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
</script>
