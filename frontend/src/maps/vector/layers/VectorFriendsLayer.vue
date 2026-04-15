<template></template>

<script setup>
import maplibregl from 'maplibre-gl'
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
import { createBasicFriendPopup } from '@/utils/friendPopupBuilder'

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
  trailSourceId: '',
  trailLayerId: '',
  styleLoadHandler: null,
  boundMap: null
}

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

const getFriendAvatar = (friend) => {
  if (!friend) return null
  return friend.avatar || friend.avatarUrl || null
}

const getColorByFriend = (friend, index) => {
  const key = String(getFriendLookupKey(friend) || `friend-${index}`)
  const hash = key
    .split('')
    .reduce((acc, character) => (acc * 31 + character.charCodeAt(0)) % trailColorPalette.length, 0)

  return trailColorPalette[hash]
}

const getFriendLabel = (friend) => {
  return (friend?.fullName || friend?.name || friend?.email || '?').slice(0, 1).toUpperCase()
}

const buildTrailCollection = () => {
  const trailFeatures = []

  props.friendsData.forEach((friend, index) => {
    const friendKey = String(getFriendLookupKey(friend) || `friend-${index}`)
    const color = friend?.trailColor || getColorByFriend(friend, index)

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

  return createFeatureCollection(trailFeatures)
}

const setMapCursor = (value) => {
  if (!isMapLibreMap(props.map)) {
    return
  }

  props.map.getCanvas().style.cursor = value
}

const createFriendMarkerElement = ({ friend, color, label }) => {
  const markerElement = document.createElement('button')
  markerElement.type = 'button'
  markerElement.className = 'gp-vector-friend-marker'
  markerElement.style.setProperty('--gp-friend-marker-color', color)
  markerElement.setAttribute('aria-label', friend?.fullName || friend?.name || friend?.email || 'Friend')

  const avatarUrl = getFriendAvatar(friend)
  if (avatarUrl) {
    markerElement.classList.add('gp-vector-friend-marker--avatar')
    const avatarImage = document.createElement('img')
    avatarImage.src = avatarUrl
    avatarImage.alt = friend?.fullName || friend?.name || friend?.email || 'Friend avatar'

    avatarImage.addEventListener('error', () => {
      markerElement.classList.remove('gp-vector-friend-marker--avatar')
      markerElement.classList.add('gp-vector-friend-marker--fallback')
      markerElement.textContent = label
    }, { once: true })

    markerElement.appendChild(avatarImage)
    return markerElement
  }

  markerElement.classList.add('gp-vector-friend-marker--fallback')
  markerElement.textContent = label
  return markerElement
}

const createFriendPopup = (friend) => {
  return new maplibregl.Popup({
    closeButton: true,
    closeOnClick: false,
    offset: 24,
    className: 'gp-friend-popup'
  }).setHTML(createBasicFriendPopup(friend))
}

const clearFriendMarkers = () => {
  friendMarkers.value.forEach(({ marker }) => {
    marker.remove()
  })

  friendMarkers.value = []
  setMapCursor('')
}

const renderFriendMarkers = () => {
  clearFriendMarkers()

  if (!isMapLibreMap(props.map) || !props.visible) {
    return
  }

  props.friendsData.forEach((friend, index) => {
    const latitude = toFiniteNumber(friend?.latitude ?? friend?.lastLatitude)
    const longitude = toFiniteNumber(friend?.longitude ?? friend?.lastLongitude)

    if (latitude === null || longitude === null) {
      return
    }

    const key = String(getFriendLookupKey(friend) || `friend-${index}`)
    const color = friend?.trailColor || getColorByFriend(friend, index)
    const label = getFriendLabel(friend)

    const markerElement = createFriendMarkerElement({ friend, color, label })
    const marker = new maplibregl.Marker({
      element: markerElement,
      anchor: 'center',
      ...props.markerOptions
    })
      .setLngLat([longitude, latitude])
      .addTo(props.map)

    const popup = createFriendPopup(friend)
    marker.setPopup(popup)

    const handleClick = (event) => {
      event.stopPropagation()
      marker.togglePopup()
      emit('friend-click', {
        friend,
        index,
        marker,
        event
      })
    }

    const handleMouseEnter = (event) => {
      setMapCursor('pointer')
      emit('friend-hover', {
        friend,
        index,
        marker,
        event
      })
    }

    const handleMouseLeave = () => {
      setMapCursor('')
    }

    markerElement.addEventListener('click', handleClick)
    markerElement.addEventListener('mouseenter', handleMouseEnter)
    markerElement.addEventListener('mouseleave', handleMouseLeave)

    friendMarkers.value.push({
      key,
      friend,
      marker,
      popup,
      index,
      handlers: {
        click: handleClick,
        mouseenter: handleMouseEnter,
        mouseleave: handleMouseLeave
      }
    })
  })
}

const renderLayer = () => {
  if (!isMapLibreMap(props.map)) {
    return
  }

  const trailCollection = buildTrailCollection()

  ensureGeoJsonSource(props.map, state.trailSourceId, trailCollection)

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

  setLayerVisibility(
    props.map,
    [state.trailLayerId],
    props.visible && props.showTrails
  )

  trailLayers.value = trailCollection.features || []
  renderFriendMarkers()
}

const clearLayer = () => {
  if (state.boundMap && state.styleLoadHandler) {
    state.boundMap.off('style.load', state.styleLoadHandler)
    state.styleLoadHandler = null
  }

  clearFriendMarkers()

  const targetMap = state.boundMap || props.map
  if (!isMapLibreMap(targetMap)) {
    state.boundMap = null
    trailLayers.value = []
    return
  }

  removeLayers(targetMap, [state.trailLayerId])
  removeSources(targetMap, [state.trailSourceId])

  state.boundMap = null
  trailLayers.value = []
}

const getMarkerByFriend = (friendData) => {
  const targetKey = String(getFriendLookupKey(friendData) || '')
  if (!targetKey) {
    return undefined
  }

  return friendMarkers.value.find(({ key }) => key === targetKey)?.marker
}

const focusOnFriend = (friendData, options = {}) => {
  if (!isMapLibreMap(props.map) || !friendData) {
    return false
  }

  const marker = getMarkerByFriend(friendData)
  if (marker) {
    const targetZoom = Number.isFinite(options.zoom) ? options.zoom : 17
    const markerPosition = marker.getLngLat()
    props.map.easeTo({
      center: [markerPosition.lng, markerPosition.lat],
      zoom: targetZoom,
      duration: 320
    })

    if (options.openPopup === true && typeof marker.togglePopup === 'function') {
      if (!marker.getPopup()?.isOpen()) {
        marker.togglePopup()
      }
    }
    return true
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

<style>
@import '@/styles/friendPopup.css';

.gp-vector-friend-marker {
  width: 40px;
  height: 40px;
  border-radius: 999px;
  border: 3px solid var(--gp-surface-white, #ffffff);
  box-shadow: 0 2px 7px rgba(0, 0, 0, 0.35);
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  padding: 0;
  line-height: 1;
  background: var(--gp-friend-marker-color, #1E88E5);
  color: #ffffff;
  font-size: 0.95rem;
  font-weight: 700;
}

.gp-vector-friend-marker img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.gp-vector-friend-marker:focus-visible {
  outline: 2px solid var(--gp-primary, #1a56db);
  outline-offset: 2px;
}

.gp-friend-popup.maplibregl-popup .maplibregl-popup-content {
  padding: 0.625rem;
  margin: 0;
  border-radius: 12px;
  border: 1px solid var(--gp-border-light, #e2e8f0);
  background: var(--gp-surface-white, #ffffff);
  box-shadow: var(--gp-shadow-large, 0 10px 25px rgba(15, 23, 42, 0.2));
}

.gp-friend-popup.maplibregl-popup-anchor-top .maplibregl-popup-tip {
  border-bottom-color: var(--gp-surface-white, #ffffff);
}

.gp-friend-popup.maplibregl-popup-anchor-bottom .maplibregl-popup-tip {
  border-top-color: var(--gp-surface-white, #ffffff);
}

.gp-friend-popup.maplibregl-popup-anchor-left .maplibregl-popup-tip {
  border-right-color: var(--gp-surface-white, #ffffff);
}

.gp-friend-popup.maplibregl-popup-anchor-right .maplibregl-popup-tip {
  border-left-color: var(--gp-surface-white, #ffffff);
}

.p-dark .gp-vector-friend-marker {
  border-color: var(--gp-border-dark, rgba(148, 163, 184, 0.55));
}

.p-dark .gp-friend-popup.maplibregl-popup .maplibregl-popup-content {
  border-color: var(--gp-border-dark, rgba(148, 163, 184, 0.35));
  background: var(--gp-surface-dark, #1e293b);
}

.p-dark .gp-friend-popup.maplibregl-popup-anchor-top .maplibregl-popup-tip {
  border-bottom-color: var(--gp-surface-dark, #1e293b);
}

.p-dark .gp-friend-popup.maplibregl-popup-anchor-bottom .maplibregl-popup-tip {
  border-top-color: var(--gp-surface-dark, #1e293b);
}

.p-dark .gp-friend-popup.maplibregl-popup-anchor-left .maplibregl-popup-tip {
  border-right-color: var(--gp-surface-dark, #1e293b);
}

.p-dark .gp-friend-popup.maplibregl-popup-anchor-right .maplibregl-popup-tip {
  border-left-color: var(--gp-surface-dark, #1e293b);
}
</style>
