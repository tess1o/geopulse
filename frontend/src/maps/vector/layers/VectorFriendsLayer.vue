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
  friendsData: {
    type: Array,
    default: () => []
  },
  friendTrails: {
    type: Object,
    default: () => ({})
  },
  showTrails: {
    type: Boolean,
    default: true
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

const emit = defineEmits(['friend-click', 'friend-hover'])

const state = {
  token: nextLayerToken('gp-friends'),
  friendSourceId: '',
  friendLayerId: '',
  friendLabelLayerId: '',
  trailSourceId: '',
  trailLayerId: '',
  listeners: [],
  styleLoadHandler: null,
  boundMap: null
}

state.friendSourceId = `${state.token}-source`
state.friendLayerId = `${state.token}-circle`
state.friendLabelLayerId = `${state.token}-label`
state.trailSourceId = `${state.token}-trails-source`
state.trailLayerId = `${state.token}-trails`

const baseLayerRef = ref(null)
const friendMarkers = ref([])
const trailLayers = ref([])

const trailColorPalette = [
  '#E53935', '#43A047', '#1E88E5', '#FDD835', '#8E24AA', '#F57C00',
  '#00ACC1', '#3949AB', '#6D4C41', '#546E7A', '#00897B', '#6A1B9A'
]

const getFriendLookupKey = (friend) => {
  return friend?.friendId || friend?.userId || friend?.id || friend?.email
}

const getColorByFriend = (friend, index) => {
  const key = String(getFriendLookupKey(friend) || `friend-${index}`)
  const hash = key
    .split('')
    .reduce((acc, character) => (acc * 31 + character.charCodeAt(0)) % trailColorPalette.length, 0)

  return trailColorPalette[hash]
}

const buildFriendCollections = () => {
  const friendFeatures = []
  const trailFeatures = []

  props.friendsData.forEach((friend, index) => {
    const latitude = toFiniteNumber(friend?.latitude ?? friend?.lastLatitude)
    const longitude = toFiniteNumber(friend?.longitude ?? friend?.lastLongitude)

    if (latitude === null || longitude === null) {
      return
    }

    const friendKey = String(getFriendLookupKey(friend) || `friend-${index}`)
    const color = friend?.trailColor || getColorByFriend(friend, index)
    const label = (friend?.fullName || friend?.name || friend?.email || '?').slice(0, 1).toUpperCase()

    friendFeatures.push({
      type: 'Feature',
      geometry: {
        type: 'Point',
        coordinates: [longitude, latitude]
      },
      properties: {
        friendKey,
        friendRaw: JSON.stringify(friend || {}),
        friendIndex: index,
        color,
        label
      }
    })

    const trail = props.friendTrails?.[friendKey] || props.friendTrails?.[String(friendKey)] || []
    if (!props.showTrails || !Array.isArray(trail)) {
      return
    }

    const coordinates = trail
      .map((point) => {
        const trailLatitude = toFiniteNumber(point?.latitude)
        const trailLongitude = toFiniteNumber(point?.longitude)
        if (trailLatitude === null || trailLongitude === null) {
          return null
        }

        return [trailLongitude, trailLatitude]
      })
      .filter(Boolean)

    if (coordinates.length < 2) {
      return
    }

    trailFeatures.push({
      type: 'Feature',
      geometry: {
        type: 'LineString',
        coordinates
      },
      properties: {
        friendKey,
        color
      }
    })
  })

  return {
    friends: createFeatureCollection(friendFeatures),
    trails: createFeatureCollection(trailFeatures)
  }
}

const parseFriend = (event) => {
  const raw = event?.features?.[0]?.properties?.friendRaw
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

  const handleClick = (event) => {
    const friend = parseFriend(event)
    const index = Number.parseInt(event?.features?.[0]?.properties?.friendIndex, 10)

    emit('friend-click', {
      friend,
      index: Number.isFinite(index) ? index : -1,
      marker: null,
      event
    })
  }

  const handleHover = (event) => {
    props.map.getCanvas().style.cursor = 'pointer'

    const friend = parseFriend(event)
    const index = Number.parseInt(event?.features?.[0]?.properties?.friendIndex, 10)

    emit('friend-hover', {
      friend,
      index: Number.isFinite(index) ? index : -1,
      marker: null,
      event
    })
  }

  const handleLeave = () => {
    props.map.getCanvas().style.cursor = ''
  }

  props.map.on('click', state.friendLayerId, handleClick)
  props.map.on('mousemove', state.friendLayerId, handleHover)
  props.map.on('mouseleave', state.friendLayerId, handleLeave)

  state.listeners = [
    { event: 'click', layerId: state.friendLayerId, handler: handleClick },
    { event: 'mousemove', layerId: state.friendLayerId, handler: handleHover },
    { event: 'mouseleave', layerId: state.friendLayerId, handler: handleLeave }
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

const renderLayer = () => {
  if (!isMapLibreMap(props.map)) {
    return
  }

  const collections = buildFriendCollections()

  ensureGeoJsonSource(props.map, state.friendSourceId, collections.friends)
  ensureGeoJsonSource(props.map, state.trailSourceId, collections.trails)

  ensureLayer(props.map, {
    id: state.trailLayerId,
    type: 'line',
    source: state.trailSourceId,
    paint: {
      'line-color': ['coalesce', ['get', 'color'], '#1E88E5'],
      'line-width': 4,
      'line-opacity': 0.82
    }
  })

  ensureLayer(props.map, {
    id: state.friendLayerId,
    type: 'circle',
    source: state.friendSourceId,
    paint: {
      'circle-radius': 12,
      'circle-color': ['coalesce', ['get', 'color'], '#1E88E5'],
      'circle-stroke-color': '#ffffff',
      'circle-stroke-width': 2
    }
  })

  ensureLayer(props.map, {
    id: state.friendLabelLayerId,
    type: 'symbol',
    source: state.friendSourceId,
    layout: {
      'text-field': ['get', 'label'],
      'text-size': 11,
      'text-font': ['Open Sans Bold', 'Arial Unicode MS Bold']
    },
    paint: {
      'text-color': '#ffffff'
    }
  })

  setLayerVisibility(
    props.map,
    [state.friendLayerId, state.friendLabelLayerId],
    props.visible
  )

  setLayerVisibility(
    props.map,
    [state.trailLayerId],
    props.visible && props.showTrails
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

  removeLayers(targetMap, [state.friendLabelLayerId, state.friendLayerId, state.trailLayerId])
  removeSources(targetMap, [state.friendSourceId, state.trailSourceId])
  state.boundMap = null
}

const getMarkerByFriend = () => null

const focusOnFriend = (friendData, options = {}) => {
  if (!isMapLibreMap(props.map) || !friendData) {
    return false
  }

  const latitude = toFiniteNumber(friendData?.lastLatitude ?? friendData?.latitude)
  const longitude = toFiniteNumber(friendData?.lastLongitude ?? friendData?.longitude)

  if (latitude === null || longitude === null) {
    return false
  }

  const targetZoom = Number.isFinite(options.zoom) ? options.zoom : 17
  props.map.easeTo({ center: [longitude, latitude], zoom: targetZoom, duration: 320 })
  return true
}

const updateFriendLocation = () => {
  renderLayer()
}

watch(
  () => [props.map, props.friendsData, props.friendTrails, props.showTrails, props.visible],
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
  friendMarkers: readonly(friendMarkers),
  trailLayers: readonly(trailLayers),
  getMarkerByFriend,
  focusOnFriend,
  updateFriendLocation,
  clearFriendMarkers: clearLayer
})
</script>
