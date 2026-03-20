<template>
  <BaseLayer
      ref="baseLayerRef"
      :map="map"
      :visible="visible"
      @layer-ready="handleLayerReady"
  />
</template>

<script setup>
import {readonly, computed, ref, watch} from 'vue'
import L from 'leaflet'
import BaseLayer from './BaseLayer.vue'
import {createAvatarDivIcon, createFriendIcon} from '@/utils/mapHelpers'
import {createBasicFriendPopup} from '@/utils/friendPopupBuilder'
import {useTimezone} from '@/composables/useTimezone'

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
const timezone = useTimezone()

const baseLayerRef = ref(null)
const friendMarkers = ref([])
const trailLayers = ref([])

const trailColorPalette = [
  '#E53935',
  '#43A047',
  '#1E88E5',
  '#FDD835',
  '#8E24AA',
  '#F57C00',
  '#00ACC1',
  '#3949AB',
  '#6D4C41',
  '#546E7A',
  '#00897B',
  '#6A1B9A'
]

const friendColorLookup = {}

const getColorByFriend = (friend, index) => {
  const key = String(friend?.friendId || friend?.userId || friend?.id || friend?.email || `friend-${index}`)
  if (!friendColorLookup[key]) {
    const hash = key
        .split('')
        .reduce((acc, char) => (acc * 31 + char.charCodeAt(0)) % trailColorPalette.length, 0)

    friendColorLookup[key] = trailColorPalette[hash]
  }

  return friendColorLookup[key]
}

const getFriendLookupKey = (friend) => {
  return friend?.friendId || friend?.userId || friend?.id || friend?.email
}

const toCoordinatePoint = (point) => {
  if (!point) return null

  const lat = point.latitude
  const lng = point.longitude

  if (typeof lat !== 'number' || typeof lng !== 'number' || Number.isNaN(lat) || Number.isNaN(lng)) {
    return null
  }

  return [lat, lng]
}

const getFriendTrailPoints = (friend) => {
  const key = getFriendLookupKey(friend)
  const trail = props.friendTrails?.[key] || props.friendTrails?.[String(key)] || []
  if (!Array.isArray(trail)) return []

  return trail
      .map(point => ({
        latLng: toCoordinatePoint(point),
        timestamp: point.timestamp
      }))
      .filter(point => point.latLng)
}

const createTrailTooltipContent = (friend, points) => {
  if (!points.length) return 'Friend location trail'

  const startTimestamp = points[0].timestamp
  const endTimestamp = points[points.length - 1].timestamp
  const friendName = friend.fullName || friend.name || friend.email || friend.friendId || 'Friend'

  if (!startTimestamp || !endTimestamp) {
    return `${friendName} trail`
  }

  const startDate = timezone.formatDateDisplay(startTimestamp)
  const endDate = timezone.formatDateDisplay(endTimestamp)
  const startTime = timezone.formatTime(startTimestamp)
  const endTime = timezone.formatTime(endTimestamp)

  if (!startDate || !endDate || !startTime || !endTime) {
    return `${friendName} trail`
  }

  const sameDay = timezone.isSameDay(startTimestamp, endTimestamp)

  const timeWindow = sameDay
      ? `${startDate}, ${startTime} to ${endTime}`
      : `${startDate}, ${startTime} to ${endDate}, ${endTime}`

  return `${friendName}: ${timeWindow}`
}

const hasFriendsData = computed(() => props.friendsData && props.friendsData.length > 0)

const handleLayerReady = () => {
  if (hasFriendsData.value) {
    renderFriendLayers()
  }
}

const renderFriendLayers = () => {
  if (!baseLayerRef.value || !hasFriendsData.value) {
    return
  }

  clearFriendMarkers()
  clearFriendTrails()

  props.friendsData.forEach((friend, index) => {
    const lat = friend.latitude || friend.lastLatitude
    const lng = friend.longitude || friend.lastLongitude
    if (!lat || !lng || typeof lat !== 'number' || typeof lng !== 'number') {
      return
    }

    const color = friend.trailColor || getColorByFriend(friend, index)
    const icon = friend.avatar ? createAvatarDivIcon({
      avatarPath: friend.avatar,
      size: 40
    }) : createFriendIcon({
      color,
      gradientEnd: color
    })

    const marker = L.marker([lat, lng], {
      icon,
      friend,
      friendIndex: index,
      ...props.markerOptions
    })

    marker.on('click', (e) => {
      emit('friend-click', {
        friend,
        index,
        marker,
        event: e
      })
    })

    marker.on('mouseover', (e) => {
      emit('friend-hover', {
        friend,
        index,
        marker,
        event: e
      })
    })

    const popupContent = createBasicFriendPopup(friend)
    marker.bindPopup(popupContent)

    baseLayerRef.value.addToLayer(marker)
    friendMarkers.value.push({
      marker,
      friend,
      index
    })
  })

  renderFriendTrails()
}

const renderFriendTrails = () => {
  if (!baseLayerRef.value || !hasFriendsData.value || !props.showTrails) {
    return
  }

  props.friendsData.forEach((friend, index) => {
    const trailData = getFriendTrailPoints(friend)
    if (!trailData || trailData.length < 2) {
      return
    }

    const coordinates = trailData.map(point => point.latLng).filter(Boolean)
    if (!coordinates.length) {
      return
    }

    const line = L.polyline(coordinates, {
      color: friend.trailColor || getColorByFriend(friend, index),
      weight: 4,
      opacity: 0.8,
      lineCap: 'round',
      lineJoin: 'round'
    })

    const tooltipContent = createTrailTooltipContent(friend, trailData)
    if (tooltipContent) {
      line.bindTooltip(tooltipContent, {
        className: 'friend-trail-tooltip',
        sticky: true,
        direction: 'top'
      })
    }

    baseLayerRef.value.addToLayer(line)
    trailLayers.value.push({
      line,
      friend,
      index
    })
  })
}

const clearFriendMarkers = () => {
  friendMarkers.value.forEach(({marker}) => {
    baseLayerRef.value?.removeFromLayer(marker)
  })
  friendMarkers.value = []
}

const clearFriendTrails = () => {
  trailLayers.value.forEach(({line}) => {
    baseLayerRef.value?.removeFromLayer(line)
  })
  trailLayers.value = []
}

const getMarkerByFriend = (friendData) => {
  const targetKey = friendData?.friendId || friendData?.userId || friendData?.id || friendData?.email
  if (!targetKey) return undefined

  const found = friendMarkers.value.find(({friend}) => {
    const friendKey = friend?.friendId || friend?.userId || friend?.id || friend?.email
    return friendKey && friendKey === targetKey
  })
  return found?.marker
}

const focusOnFriend = (friendData, options = {}) => {
  const marker = getMarkerByFriend(friendData)
  if (marker && props.map) {
    const {
      zoom = 17,
      openPopup = true
    } = options

    props.map.setView(marker.getLatLng(), zoom)
    if (openPopup) {
      marker.openPopup()
    }
    return true
  }
  return false
}

const updateFriendLocation = (friendId, newLocation) => {
  const key = friendId
  const friendMarker = friendMarkers.value.find(({friend}) => {
    const matchKey = friend?.friendId || friend?.userId || friend?.id || friend?.email
    return matchKey && matchKey === key
  })

  if (friendMarker && newLocation.latitude && newLocation.longitude) {
    friendMarker.marker.setLatLng([newLocation.latitude, newLocation.longitude])
    friendMarker.friend = {...friendMarker.friend, ...newLocation}
    const newPopupContent = createBasicFriendPopup(friendMarker.friend)
    friendMarker.marker.setPopupContent(newPopupContent)
  }
}

watch(() => props.friendsData, () => {
  if (baseLayerRef.value?.isReady) {
    renderFriendLayers()
  }
}, {deep: true})

watch(() => props.friendTrails, () => {
  if (baseLayerRef.value?.isReady) {
    renderFriendLayers()
  }
}, {deep: true})

watch(() => props.showTrails, () => {
  if (baseLayerRef.value?.isReady) {
    renderFriendLayers()
  }
}, {immediate: true})

defineExpose({
  baseLayerRef,
  friendMarkers: readonly(friendMarkers),
  trailLayers: readonly(trailLayers),
  getMarkerByFriend,
  focusOnFriend,
  updateFriendLocation,
  clearFriendMarkers
})
</script>

<style>
@import '@/styles/friendPopup.css';

.leaflet-tooltip.friend-trail-tooltip {
  background: rgba(17, 24, 39, 0.98) !important;
  border: 1px solid rgba(255, 255, 255, 0.12) !important;
  color: #f8fafc !important;
  min-width: 220px !important;
  max-width: 420px !important;
  white-space: normal !important;
  word-break: normal !important;
  overflow-wrap: normal !important;
  line-height: 1.35 !important;
}

.leaflet-tooltip.friend-trail-tooltip::before {
  border-top-color: rgba(17, 24, 39, 0.98) !important;
}
</style>
