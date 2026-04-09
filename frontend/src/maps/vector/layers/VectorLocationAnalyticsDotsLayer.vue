<template></template>

<script setup>
import { onBeforeUnmount, watch } from 'vue'
import {
  createFeatureCollection,
  ensureClusterSource,
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
  places: {
    type: Array,
    default: () => []
  },
  visible: {
    type: Boolean,
    default: true
  },
  selectedPlaceKey: {
    type: String,
    default: null
  },
  hoveredPlaceKey: {
    type: String,
    default: null
  }
})

const emit = defineEmits(['marker-click', 'open-place-details'])
const CLUSTER_LABEL_MIN_ZOOM = 8
const CLUSTER_LABEL_MAX_COUNT = 49

const state = {
  token: nextLayerToken('gp-location-analytics'),
  sourceId: '',
  clusterLayerId: '',
  clusterCountLayerId: '',
  pointLayerId: '',
  selectedLayerId: '',
  hoveredLayerId: '',
  listeners: [],
  styleLoadHandler: null,
  boundMap: null
}

state.sourceId = `${state.token}-source`
state.clusterLayerId = `${state.token}-cluster`
state.clusterCountLayerId = `${state.token}-cluster-count`
state.pointLayerId = `${state.token}-points`
state.selectedLayerId = `${state.token}-selected`
state.hoveredLayerId = `${state.token}-hovered`

const getPlaceKey = (place) => `${place.type}-${place.id}`

const buildCollection = () => {
  const features = props.places
    .map((place) => {
      const latitude = toFiniteNumber(place?.latitude)
      const longitude = toFiniteNumber(place?.longitude)
      if (latitude === null || longitude === null) {
        return null
      }

      return {
        type: 'Feature',
        geometry: {
          type: 'Point',
          coordinates: [longitude, latitude]
        },
        properties: {
          placeKey: getPlaceKey(place),
          placeRaw: JSON.stringify(place || {}),
          visitCount: Number(place?.visitCount || 0)
        }
      }
    })
    .filter(Boolean)

  return createFeatureCollection(features)
}

const parsePlace = (event) => {
  const raw = event?.features?.[0]?.properties?.placeRaw
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

  const handleClusterClick = (event) => {
    const clusterId = event?.features?.[0]?.properties?.cluster_id
    if (clusterId === undefined || clusterId === null) {
      return
    }

    const source = props.map.getSource(state.sourceId)
    if (!source || typeof source.getClusterExpansionZoom !== 'function') {
      return
    }

    source.getClusterExpansionZoom(clusterId, (error, zoom) => {
      if (error) {
        return
      }

      const center = event?.features?.[0]?.geometry?.coordinates
      if (!Array.isArray(center)) {
        return
      }

      props.map.easeTo({ center, zoom, duration: 280 })
    })
  }

  const handleMarkerClick = (event) => {
    const place = parsePlace(event)
    if (place) {
      emit('marker-click', place)
    }
  }

  const handleMarkerDoubleClick = (event) => {
    const place = parsePlace(event)
    if (place) {
      emit('open-place-details', place)
    }
  }

  const handleHover = () => {
    props.map.getCanvas().style.cursor = 'pointer'
  }

  const handleLeave = () => {
    props.map.getCanvas().style.cursor = ''
  }

  props.map.on('click', state.clusterLayerId, handleClusterClick)
  props.map.on('click', state.pointLayerId, handleMarkerClick)
  props.map.on('dblclick', state.pointLayerId, handleMarkerDoubleClick)
  props.map.on('mousemove', state.pointLayerId, handleHover)
  props.map.on('mouseleave', state.pointLayerId, handleLeave)

  state.listeners = [
    { event: 'click', layerId: state.clusterLayerId, handler: handleClusterClick },
    { event: 'click', layerId: state.pointLayerId, handler: handleMarkerClick },
    { event: 'dblclick', layerId: state.pointLayerId, handler: handleMarkerDoubleClick },
    { event: 'mousemove', layerId: state.pointLayerId, handler: handleHover },
    { event: 'mouseleave', layerId: state.pointLayerId, handler: handleLeave }
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
  props.map.getCanvas().style.cursor = ''
}

const updateHighlightFilters = () => {
  if (!isMapLibreMap(props.map)) {
    return
  }

  if (props.map.getLayer(state.selectedLayerId)) {
    props.map.setFilter(
      state.selectedLayerId,
      ['all', ['!', ['has', 'point_count']], ['==', ['get', 'placeKey'], props.selectedPlaceKey || '__none__']]
    )
  }

  if (props.map.getLayer(state.hoveredLayerId)) {
    props.map.setFilter(
      state.hoveredLayerId,
      ['all', ['!', ['has', 'point_count']], ['==', ['get', 'placeKey'], props.hoveredPlaceKey || '__none__']]
    )
  }
}

const renderLayer = () => {
  if (!isMapLibreMap(props.map)) {
    return
  }

  const collection = buildCollection()

  ensureClusterSource(props.map, state.sourceId, collection, {
    cluster: true,
    clusterRadius: 28,
    clusterMaxZoom: 12
  })

  ensureLayer(props.map, {
    id: state.clusterLayerId,
    type: 'circle',
    source: state.sourceId,
    filter: ['has', 'point_count'],
    paint: {
      'circle-color': '#ff8a3d',
      // Match raster cluster visual scale: size by zoom, not by child count.
      'circle-radius': ['step', ['zoom'], 7, 6, 8, 8, 9, 10, 10],
      'circle-stroke-color': '#ffe5d0',
      // MapLibre requires zoom expressions to be top-level step/interpolate.
      // Match raster behavior: compact (thin) below label zoom, labeled clusters can be thicker.
      'circle-stroke-width': [
        'step',
        ['zoom'],
        1.5,
        CLUSTER_LABEL_MIN_ZOOM,
        ['case', ['<=', ['get', 'point_count'], CLUSTER_LABEL_MAX_COUNT], 2, 1.5]
      ]
    }
  })

  ensureLayer(props.map, {
    id: state.clusterCountLayerId,
    type: 'symbol',
    source: state.sourceId,
    filter: ['has', 'point_count'],
    layout: {
      'text-field': [
        'step',
        ['zoom'],
        '',
        CLUSTER_LABEL_MIN_ZOOM,
        ['case', ['<=', ['get', 'point_count'], CLUSTER_LABEL_MAX_COUNT], ['to-string', ['get', 'point_count_abbreviated']], '']
      ],
      'text-size': 10,
      'text-font': ['Open Sans Bold', 'Arial Unicode MS Bold']
    },
    paint: {
      'text-color': '#ffffff'
    }
  })

  ensureLayer(props.map, {
    id: state.pointLayerId,
    type: 'circle',
    source: state.sourceId,
    filter: ['!', ['has', 'point_count']],
    paint: {
      'circle-color': '#ff5e00',
      'circle-radius': 6,
      'circle-stroke-color': '#ffe5d0',
      'circle-stroke-width': 2,
      'circle-opacity': 0.95
    }
  })

  ensureLayer(props.map, {
    id: state.selectedLayerId,
    type: 'circle',
    source: state.sourceId,
    filter: ['all', ['!', ['has', 'point_count']], ['==', ['get', 'placeKey'], props.selectedPlaceKey || '__none__']],
    paint: {
      'circle-color': '#2563eb',
      'circle-radius': 11,
      'circle-stroke-color': '#ffffff',
      'circle-stroke-width': 3,
      'circle-opacity': 0.9
    }
  })

  ensureLayer(props.map, {
    id: state.hoveredLayerId,
    type: 'circle',
    source: state.sourceId,
    filter: ['all', ['!', ['has', 'point_count']], ['==', ['get', 'placeKey'], props.hoveredPlaceKey || '__none__']],
    paint: {
      'circle-color': '#f59e0b',
      'circle-radius': 10,
      'circle-stroke-color': '#ffffff',
      'circle-stroke-width': 2,
      'circle-opacity': 0.88
    }
  })

  updateHighlightFilters()

  setLayerVisibility(
    props.map,
    [
      state.clusterLayerId,
      state.clusterCountLayerId,
      state.pointLayerId,
      state.selectedLayerId,
      state.hoveredLayerId
    ],
    props.visible
  )

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

  removeLayers(targetMap, [
    state.hoveredLayerId,
    state.selectedLayerId,
    state.pointLayerId,
    state.clusterCountLayerId,
    state.clusterLayerId
  ])
  removeSources(targetMap, [state.sourceId])

  state.boundMap = null
}

watch(
  () => [props.map, props.places, props.visible],
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

watch(
  () => [props.selectedPlaceKey, props.hoveredPlaceKey],
  () => {
    updateHighlightFilters()
  }
)

onBeforeUnmount(() => {
  clearLayer()
})
</script>
