<template></template>

<script setup>
import { readonly, ref, watch, onBeforeUnmount } from 'vue'
import maplibregl from 'maplibre-gl'
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
import { createFavoritePointMarkerElement } from '@/maps/shared/favoriteMarkerBuilder'

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
  areaSourceId: '',
  areaFillLayerId: '',
  areaOutlineLayerId: '',
  styleLoadHandler: null,
  boundMap: null
}

state.areaSourceId = `${state.token}-areas-source`
state.areaFillLayerId = `${state.token}-areas-fill`
state.areaOutlineLayerId = `${state.token}-areas-outline`

const favoriteMarkers = ref([])
const areaEventHandlers = ref([])

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
  const areaFeatures = []

  props.favoritesData.forEach((favorite, index) => {
    const normalizedType = String(favorite?.type || '').toLowerCase()

    if (normalizedType === 'area' || favorite?.northEastLat !== undefined || favorite?.coordinates) {
      const areaFeature = normalizeAreaFeature(favorite, index)
      if (areaFeature) {
        areaFeatures.push(areaFeature)
      }
    }
  })

  return {
    areas: createFeatureCollection(areaFeatures)
  }
}

const buildPointEntries = () => {
  const entries = []

  props.favoritesData.forEach((favorite, index) => {
    const normalizedType = String(favorite?.type || '').toLowerCase()
    const isPointType = normalizedType === 'point'
      || (!favorite?.type && favorite?.latitude !== undefined && favorite?.longitude !== undefined)

    if (!isPointType) {
      return
    }

    const latitude = toFiniteNumber(favorite?.latitude)
    const longitude = toFiniteNumber(favorite?.longitude)
    if (latitude === null || longitude === null) {
      return
    }

    entries.push({
      favorite,
      index,
      latitude,
      longitude
    })
  })

  return entries
}

const clearPointMarkers = () => {
  favoriteMarkers.value.forEach(({ cleanup }) => {
    cleanup?.()
  })
  favoriteMarkers.value = []

  if (isMapLibreMap(props.map)) {
    props.map.getCanvas().style.cursor = ''
  }
}

const renderPointMarkers = () => {
  if (!isMapLibreMap(props.map)) {
    return
  }

  clearPointMarkers()

  if (!props.visible) {
    return
  }

  const entries = buildPointEntries()
  entries.forEach(({ favorite, index, latitude, longitude }) => {
    const markerSpec = createFavoritePointMarkerElement()
    markerSpec.element.style.zIndex = '340'

    const marker = new maplibregl.Marker({
      element: markerSpec.element,
      anchor: markerSpec.anchor || 'bottom',
      offset: markerSpec.offset || [0, 0]
    })
      .setLngLat([longitude, latitude])
      .addTo(props.map)

    const handleClick = (domEvent) => {
      domEvent.preventDefault()
      domEvent.stopPropagation()

      emit('favorite-click', {
        favorite,
        index,
        event: {
          target: marker,
          originalEvent: domEvent,
          lngLat: { lng: longitude, lat: latitude }
        },
        marker: null
      })
    }

    const handleMouseEnter = (domEvent) => {
      props.map.getCanvas().style.cursor = 'pointer'

      emit('favorite-hover', {
        favorite,
        index,
        event: {
          target: marker,
          originalEvent: domEvent,
          lngLat: { lng: longitude, lat: latitude }
        },
        marker: null
      })
    }

    const handleMouseLeave = () => {
      props.map.getCanvas().style.cursor = ''
    }

    const handleContextMenu = (domEvent) => {
      domEvent.preventDefault()
      domEvent.stopPropagation()
      domEvent.stopImmediatePropagation?.()

      emit('favorite-contextmenu', {
        favorite,
        index,
        event: domEvent,
        latlng: { lat: latitude, lng: longitude },
        type: 'point',
        marker: null
      })
    }

    markerSpec.element.addEventListener('click', handleClick)
    markerSpec.element.addEventListener('mouseenter', handleMouseEnter)
    markerSpec.element.addEventListener('mouseleave', handleMouseLeave)
    markerSpec.element.addEventListener('contextmenu', handleContextMenu)

    favoriteMarkers.value.push({
      favorite,
      marker,
      cleanup: () => {
        markerSpec.element.removeEventListener('click', handleClick)
        markerSpec.element.removeEventListener('mouseenter', handleMouseEnter)
        markerSpec.element.removeEventListener('mouseleave', handleMouseLeave)
        markerSpec.element.removeEventListener('contextmenu', handleContextMenu)
        marker.remove()
      }
    })
  })
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

  let lastContextMenuOriginalEvent = null
  const shouldSkipDuplicateContextMenu = (originalEvent) => {
    if (!originalEvent || (typeof originalEvent !== 'object' && typeof originalEvent !== 'function')) {
      return false
    }

    if (lastContextMenuOriginalEvent === originalEvent) {
      return true
    }

    lastContextMenuOriginalEvent = originalEvent
    setTimeout(() => {
      if (lastContextMenuOriginalEvent === originalEvent) {
        lastContextMenuOriginalEvent = null
      }
    }, 0)

    return false
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

  const handleAreaContextMenu = (event) => {
    const originalEvent = event?.originalEvent
    if (shouldSkipDuplicateContextMenu(originalEvent)) {
      return
    }

    event?.preventDefault?.()
    originalEvent?.preventDefault?.()
    originalEvent?.stopPropagation?.()
    originalEvent?.stopImmediatePropagation?.()
    if (originalEvent) {
      originalEvent.cancelBubble = true
    }

    const favorite = parseFavoriteFromEvent(event)
    emit('favorite-contextmenu', {
      favorite,
      index: getFavoriteIndexFromEvent(event),
      event: originalEvent || event,
      latlng: event?.lngLat
        ? { lat: event.lngLat.lat, lng: event.lngLat.lng }
        : null,
      type: 'area',
      polygon: null
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

  props.map.on('click', state.areaFillLayerId, handleAreaClick)
  props.map.on('contextmenu', state.areaFillLayerId, handleAreaContextMenu)
  props.map.on('contextmenu', state.areaOutlineLayerId, handleAreaContextMenu)
  props.map.on('mousemove', state.areaFillLayerId, handleAreaHover)
  props.map.on('mouseleave', state.areaFillLayerId, handleMouseLeave)

  areaEventHandlers.value = [
    { event: 'click', layerId: state.areaFillLayerId, handler: handleAreaClick },
    { event: 'contextmenu', layerId: state.areaFillLayerId, handler: handleAreaContextMenu },
    { event: 'contextmenu', layerId: state.areaOutlineLayerId, handler: handleAreaContextMenu },
    { event: 'mousemove', layerId: state.areaFillLayerId, handler: handleAreaHover },
    { event: 'mouseleave', layerId: state.areaFillLayerId, handler: handleMouseLeave }
  ]
}

const unregisterEvents = () => {
  if (!isMapLibreMap(props.map)) {
    areaEventHandlers.value = []
    return
  }

  areaEventHandlers.value.forEach(({ event, layerId, handler }) => {
    if (props.map.getLayer(layerId)) {
      props.map.off(event, layerId, handler)
    }
  })

  areaEventHandlers.value = []
  props.map.getCanvas().style.cursor = ''
}

const renderLayer = () => {
  if (!isMapLibreMap(props.map)) {
    return
  }

  const collections = buildCollections()

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

  setLayerVisibility(props.map, [state.areaFillLayerId, state.areaOutlineLayerId], props.visible)

  unregisterEvents()
  registerEvents()
  renderPointMarkers()
}

const clearLayer = () => {
  clearPointMarkers()
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

  removeLayers(targetMap, [state.areaOutlineLayerId, state.areaFillLayerId])
  removeSources(targetMap, [state.areaSourceId])
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

const getMarkerByFavorite = (favoriteData) => {
  const found = favoriteMarkers.value.find(({ favorite }) =>
    favorite?.id === favoriteData?.id || favorite === favoriteData
  )

  return found?.marker || null
}

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
