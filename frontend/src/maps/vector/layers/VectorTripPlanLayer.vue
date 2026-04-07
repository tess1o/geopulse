<template></template>

<script setup>
import { readonly, ref, watch, onBeforeUnmount } from 'vue'
import {
  createFeatureCollection,
  ensureGeoJsonSource,
  ensureLayer,
  isMapLibreMap,
  nextLayerToken,
  removeLayers,
  removeSources,
  setLayerVisibility,
  toFiniteNumber
} from '@/maps/vector/utils/maplibreLayerUtils'

const props = defineProps({
  map: {
    type: Object,
    required: true
  },
  plannedItemsData: {
    type: Array,
    default: () => []
  },
  visible: {
    type: Boolean,
    default: true
  },
  markerOptions: {
    type: Object,
    default: () => ({})
  }
})

const emit = defineEmits(['plan-item-contextmenu'])

const state = {
  token: nextLayerToken('gp-trip-plan'),
  sourceId: '',
  layerId: '',
  labelLayerId: '',
  listeners: [],
  styleLoadHandler: null,
  boundMap: null
}

state.sourceId = `${state.token}-source`
state.layerId = `${state.token}-circle`
state.labelLayerId = `${state.token}-label`

const baseLayerRef = ref(null)
const planMarkers = ref([])

const buildCollection = () => {
  const features = props.plannedItemsData
    .map((item, index) => {
      const latitude = toFiniteNumber(item?.latitude)
      const longitude = toFiniteNumber(item?.longitude)

      if (latitude === null || longitude === null) {
        return null
      }

      const isMust = String(item?.priority || '').toUpperCase() === 'MUST'

      return {
        type: 'Feature',
        geometry: {
          type: 'Point',
          coordinates: [longitude, latitude]
        },
        properties: {
          itemRaw: JSON.stringify(item || {}),
          itemIndex: index,
          isMust,
          label: isMust ? 'M' : 'P'
        }
      }
    })
    .filter(Boolean)

  return createFeatureCollection(features)
}

const parseItem = (event) => {
  const raw = event?.features?.[0]?.properties?.itemRaw
  if (!raw) {
    return null
  }

  try {
    return JSON.parse(raw)
  } catch {
    return null
  }
}

const registerEvents = () => {
  if (!isMapLibreMap(props.map)) {
    return
  }

  const handleContextMenu = (event) => {
    const item = parseItem(event)
    const index = Number.parseInt(event?.features?.[0]?.properties?.itemIndex, 10)

    event?.originalEvent?.preventDefault?.()

    emit('plan-item-contextmenu', {
      item,
      index: Number.isFinite(index) ? index : -1,
      event: event?.originalEvent || event,
      latlng: event?.lngLat ? { lat: event.lngLat.lat, lng: event.lngLat.lng } : null,
      type: 'trip-plan'
    })
  }

  props.map.on('contextmenu', state.layerId, handleContextMenu)

  state.listeners = [
    { event: 'contextmenu', layerId: state.layerId, handler: handleContextMenu }
  ]
}

const unregisterEvents = () => {
  if (!isMapLibreMap(props.map)) {
    state.listeners = []
    return
  }

  state.listeners.forEach(({ event, layerId, handler }) => {
    if (props.map.getLayer(layerId)) {
      props.map.off(event, layerId, handler)
    }
  })

  state.listeners = []
}

const renderLayer = () => {
  if (!isMapLibreMap(props.map)) {
    return
  }

  const collection = buildCollection()

  ensureGeoJsonSource(props.map, state.sourceId, collection)

  ensureLayer(props.map, {
    id: state.layerId,
    type: 'circle',
    source: state.sourceId,
    paint: {
      'circle-radius': ['case', ['get', 'isMust'], 11, 9],
      'circle-color': ['case', ['get', 'isMust'], '#dc2626', '#f59e0b'],
      'circle-stroke-color': '#ffffff',
      'circle-stroke-width': 2
    }
  })

  ensureLayer(props.map, {
    id: state.labelLayerId,
    type: 'symbol',
    source: state.sourceId,
    layout: {
      'text-field': ['get', 'label'],
      'text-size': 10,
      'text-font': ['Open Sans Bold', 'Arial Unicode MS Bold']
    },
    paint: {
      'text-color': '#ffffff'
    }
  })

  setLayerVisibility(props.map, [state.layerId, state.labelLayerId], props.visible)

  unregisterEvents()
  registerEvents()
}

const clearLayer = () => {
  unregisterEvents()

  if (state.boundMap && state.styleLoadHandler) {
    state.boundMap.off('style.load', state.styleLoadHandler)
    state.styleLoadHandler = null
  }

  const targetMap = state.boundMap || props.map
  if (!isMapLibreMap(targetMap)) {
    state.boundMap = null
    return
  }

  removeLayers(targetMap, [state.labelLayerId, state.layerId])
  removeSources(targetMap, [state.sourceId])
  state.boundMap = null
}

watch(
  () => [props.map, props.plannedItemsData, props.visible],
  () => {
    if (!isMapLibreMap(props.map)) {
      clearLayer()
      return
    }

    if (state.boundMap && state.boundMap !== props.map) {
      clearLayer()
    }

    state.boundMap = props.map

    if (!state.styleLoadHandler) {
      state.styleLoadHandler = () => renderLayer()
      props.map.on('style.load', state.styleLoadHandler)
    }

    renderLayer()
  },
  { immediate: true, deep: true }
)

onBeforeUnmount(() => {
  clearLayer()
})

defineExpose({
  baseLayerRef: readonly(baseLayerRef),
  planMarkers: readonly(planMarkers),
  clearPlanMarkers: clearLayer
})
</script>
