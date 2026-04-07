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
  favoritesData: {
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

const emit = defineEmits(['favorite-click', 'favorite-hover', 'favorite-edit', 'favorite-delete', 'favorite-contextmenu'])

const state = {
  token: nextLayerToken('gp-favorites'),
  pointSourceId: '',
  areaSourceId: '',
  areaFillLayerId: '',
  areaOutlineLayerId: '',
  pointLayerId: '',
  pointLabelLayerId: '',
  styleLoadHandler: null,
  boundMap: null
}

state.pointSourceId = `${state.token}-points-source`
state.areaSourceId = `${state.token}-areas-source`
state.areaFillLayerId = `${state.token}-areas-fill`
state.areaOutlineLayerId = `${state.token}-areas-outline`
state.pointLayerId = `${state.token}-points`
state.pointLabelLayerId = `${state.token}-points-label`

const favoriteMarkers = ref([])

const normalizePointFeature = (favorite, index) => {
  const latitude = toFiniteNumber(favorite?.latitude)
  const longitude = toFiniteNumber(favorite?.longitude)
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
      favoriteIndex: index,
      favoriteId: favorite?.id ?? `favorite-point-${index}`,
      favoriteType: 'point',
      favoriteRaw: JSON.stringify(favorite || {}),
      name: favorite?.name || 'Favorite Place'
    }
  }
}

const normalizeAreaRing = (favorite) => {
  if (Array.isArray(favorite?.coordinates) && favorite.coordinates.length >= 3) {
    const ring = favorite.coordinates
      .map((coordinate) => {
        if (!Array.isArray(coordinate) || coordinate.length < 2) {
          return null
        }

        const latitude = toFiniteNumber(coordinate[0])
        const longitude = toFiniteNumber(coordinate[1])
        if (latitude === null || longitude === null) {
          return null
        }

        return [longitude, latitude]
      })
      .filter(Boolean)

    if (ring.length < 3) {
      return null
    }

    const first = ring[0]
    const last = ring[ring.length - 1]
    if (first[0] !== last[0] || first[1] !== last[1]) {
      ring.push([...first])
    }

    return ring
  }

  const northEastLat = toFiniteNumber(favorite?.northEastLat)
  const northEastLon = toFiniteNumber(favorite?.northEastLon)
  const southWestLat = toFiniteNumber(favorite?.southWestLat)
  const southWestLon = toFiniteNumber(favorite?.southWestLon)

  if (northEastLat === null || northEastLon === null || southWestLat === null || southWestLon === null) {
    return null
  }

  return [
    [southWestLon, southWestLat],
    [northEastLon, southWestLat],
    [northEastLon, northEastLat],
    [southWestLon, northEastLat],
    [southWestLon, southWestLat]
  ]
}

const normalizeAreaFeature = (favorite, index) => {
  const ring = normalizeAreaRing(favorite)
  if (!ring) {
    return null
  }

  return {
    type: 'Feature',
    geometry: {
      type: 'Polygon',
      coordinates: [ring]
    },
    properties: {
      favoriteIndex: index,
      favoriteId: favorite?.id ?? `favorite-area-${index}`,
      favoriteType: 'area',
      favoriteRaw: JSON.stringify(favorite || {}),
      name: favorite?.name || 'Favorite Area'
    }
  }
}

const buildCollections = () => {
  const pointFeatures = []
  const areaFeatures = []

  props.favoritesData.forEach((favorite, index) => {
    const normalizedType = String(favorite?.type || '').toLowerCase()

    if (normalizedType === 'point' || (!favorite?.type && favorite?.latitude !== undefined && favorite?.longitude !== undefined)) {
      const pointFeature = normalizePointFeature(favorite, index)
      if (pointFeature) {
        pointFeatures.push(pointFeature)
      }
      return
    }

    if (normalizedType === 'area' || favorite?.northEastLat !== undefined || favorite?.coordinates) {
      const areaFeature = normalizeAreaFeature(favorite, index)
      if (areaFeature) {
        areaFeatures.push(areaFeature)
      }
    }
  })

  return {
    points: createFeatureCollection(pointFeatures),
    areas: createFeatureCollection(areaFeatures)
  }
}

const parseFavoriteFromEvent = (event) => {
  const properties = event?.features?.[0]?.properties
  if (!properties?.favoriteRaw) {
    return null
  }

  try {
    return JSON.parse(properties.favoriteRaw)
  } catch {
    return null
  }
}

const getFavoriteIndexFromEvent = (event) => {
  const raw = event?.features?.[0]?.properties?.favoriteIndex
  const parsed = Number.parseInt(raw, 10)
  return Number.isFinite(parsed) ? parsed : -1
}

const registerEvents = () => {
  if (!isMapLibreMap(props.map)) {
    return
  }

  const handlePointClick = (event) => {
    const favorite = parseFavoriteFromEvent(event)
    emit('favorite-click', {
      favorite,
      index: getFavoriteIndexFromEvent(event),
      event,
      marker: null
    })
  }

  const handleAreaClick = (event) => {
    const favorite = parseFavoriteFromEvent(event)
    emit('favorite-click', {
      favorite,
      index: getFavoriteIndexFromEvent(event),
      event,
      polygon: null
    })
  }

  const handlePointHover = (event) => {
    props.map.getCanvas().style.cursor = 'pointer'
    const favorite = parseFavoriteFromEvent(event)
    emit('favorite-hover', {
      favorite,
      index: getFavoriteIndexFromEvent(event),
      event,
      marker: null
    })
  }

  const handleAreaHover = (event) => {
    props.map.getCanvas().style.cursor = 'pointer'
    const favorite = parseFavoriteFromEvent(event)
    emit('favorite-hover', {
      favorite,
      index: getFavoriteIndexFromEvent(event),
      event,
      polygon: null
    })
  }

  const handleMouseLeave = () => {
    props.map.getCanvas().style.cursor = ''
  }

  props.map.on('click', state.pointLayerId, handlePointClick)
  props.map.on('click', state.areaFillLayerId, handleAreaClick)
  props.map.on('mousemove', state.pointLayerId, handlePointHover)
  props.map.on('mousemove', state.areaFillLayerId, handleAreaHover)
  props.map.on('mouseleave', state.pointLayerId, handleMouseLeave)
  props.map.on('mouseleave', state.areaFillLayerId, handleMouseLeave)

  favoriteMarkers.value = [
    { event: 'click', layerId: state.pointLayerId, handler: handlePointClick },
    { event: 'click', layerId: state.areaFillLayerId, handler: handleAreaClick },
    { event: 'mousemove', layerId: state.pointLayerId, handler: handlePointHover },
    { event: 'mousemove', layerId: state.areaFillLayerId, handler: handleAreaHover },
    { event: 'mouseleave', layerId: state.pointLayerId, handler: handleMouseLeave },
    { event: 'mouseleave', layerId: state.areaFillLayerId, handler: handleMouseLeave }
  ]
}

const unregisterEvents = () => {
  if (!isMapLibreMap(props.map)) {
    favoriteMarkers.value = []
    return
  }

  favoriteMarkers.value.forEach(({ event, layerId, handler }) => {
    if (props.map.getLayer(layerId)) {
      props.map.off(event, layerId, handler)
    }
  })

  favoriteMarkers.value = []
  props.map.getCanvas().style.cursor = ''
}

const renderLayer = () => {
  if (!isMapLibreMap(props.map)) {
    return
  }

  const collections = buildCollections()

  ensureGeoJsonSource(props.map, state.pointSourceId, collections.points)
  ensureGeoJsonSource(props.map, state.areaSourceId, collections.areas)

  ensureLayer(props.map, {
    id: state.areaFillLayerId,
    type: 'fill',
    source: state.areaSourceId,
    paint: {
      'fill-color': '#ff6b6b',
      'fill-opacity': 0.22
    }
  })

  ensureLayer(props.map, {
    id: state.areaOutlineLayerId,
    type: 'line',
    source: state.areaSourceId,
    paint: {
      'line-color': '#ff6b6b',
      'line-width': 2,
      'line-opacity': 0.85
    }
  })

  ensureLayer(props.map, {
    id: state.pointLayerId,
    type: 'circle',
    source: state.pointSourceId,
    paint: {
      'circle-radius': 7,
      'circle-color': '#f59e0b',
      'circle-stroke-color': '#ffffff',
      'circle-stroke-width': 2
    }
  })

  ensureLayer(props.map, {
    id: state.pointLabelLayerId,
    type: 'symbol',
    source: state.pointSourceId,
    layout: {
      'text-field': '★',
      'text-size': 10,
      'text-font': ['Open Sans Bold', 'Arial Unicode MS Bold'],
      'text-offset': [0, 0.05],
      'text-anchor': 'center'
    },
    paint: {
      'text-color': '#111827'
    }
  })

  setLayerVisibility(
    props.map,
    [state.areaFillLayerId, state.areaOutlineLayerId, state.pointLayerId, state.pointLabelLayerId],
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

  removeLayers(targetMap, [state.pointLabelLayerId, state.pointLayerId, state.areaOutlineLayerId, state.areaFillLayerId])
  removeSources(targetMap, [state.pointSourceId, state.areaSourceId])
  state.boundMap = null
}

watch(
  () => [props.map, props.favoritesData, props.visible],
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
  { deep: true, immediate: true }
)

const getMarkerByFavorite = () => null

const focusOnFavorite = (favoriteData) => {
  if (!isMapLibreMap(props.map) || !favoriteData) {
    return false
  }

  const pointLat = toFiniteNumber(favoriteData.latitude)
  const pointLng = toFiniteNumber(favoriteData.longitude)

  if (pointLat !== null && pointLng !== null) {
    props.map.easeTo({ center: [pointLng, pointLat], zoom: 15, duration: 350 })
    return true
  }

  const ring = normalizeAreaRing(favoriteData)
  if (ring && ring.length > 3) {
    let west = Infinity
    let south = Infinity
    let east = -Infinity
    let north = -Infinity

    ring.forEach(([lng, lat]) => {
      if (lng < west) west = lng
      if (lng > east) east = lng
      if (lat < south) south = lat
      if (lat > north) north = lat
    })

    if (Number.isFinite(west) && Number.isFinite(south) && Number.isFinite(east) && Number.isFinite(north)) {
      props.map.fitBounds([[west, south], [east, north]], { padding: 40, duration: 350 })
      return true
    }
  }

  return false
}

const updateFavorite = () => {
  renderLayer()
}

const removeFavorite = () => {
  renderLayer()
}

onBeforeUnmount(() => {
  clearLayer()
})

defineExpose({
  favoriteMarkers: readonly(favoriteMarkers),
  getMarkerByFavorite,
  focusOnFavorite,
  updateFavorite,
  removeFavorite,
  clearFavoriteMarkers: clearLayer
})
</script>
