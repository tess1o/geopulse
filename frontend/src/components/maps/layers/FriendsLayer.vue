<template>
  <BaseLayer
      ref="baseLayerRef"
      :map="map"
      :visible="visible"
      @layer-ready="handleLayerReady"
  />
</template>

<script setup>
import {ref, watch, computed, readonly} from 'vue'
import L from 'leaflet'
import BaseLayer from './BaseLayer.vue'
import {createAvatarDivIcon, createFriendIcon} from '@/utils/mapHelpers'
import {createBasicFriendPopup} from '@/utils/friendPopupBuilder'

const props = defineProps({
  map: {
    type: Object,
    required: true
  },
  friendsData: {
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

const emit = defineEmits(['friend-click', 'friend-hover'])

// State
const baseLayerRef = ref(null)
const friendMarkers = ref([])

// Computed
const hasFriendsData = computed(() => props.friendsData && props.friendsData.length > 0)

// Layer management
const handleLayerReady = (layerGroup) => {
  if (hasFriendsData.value) {
    renderFriendMarkers()
  }
}

const renderFriendMarkers = () => {
  if (!baseLayerRef.value || !hasFriendsData.value) {
    return
  }

  // Clear existing markers
  clearFriendMarkers()

  props.friendsData.forEach((friend, index) => {
    // Check both latitude/longitude and lastLatitude/lastLongitude
    const lat = friend.latitude || friend.lastLatitude
    const lng = friend.longitude || friend.lastLongitude
    
    if (!lat || !lng || typeof lat !== 'number' || typeof lng !== 'number') {
      return
    }

    // Create friend icon
    const icon = friend.avatar ? createAvatarDivIcon({
      avatarPath: friend.avatar,
      size: 40
    }) : createFriendIcon()
    
    // Create marker with correct coordinates
    const marker = L.marker([lat, lng], {
      icon,
      friend,
      friendIndex: index,
      ...props.markerOptions
    })

    // Add event listeners
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

    // Add popup with friend info
    const popupContent = createBasicFriendPopup(friend)
    marker.bindPopup(popupContent)

    // Add to layer and track
    baseLayerRef.value.addToLayer(marker)
    friendMarkers.value.push({
      marker,
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

const getMarkerByFriend = (friendData) => {
  const found = friendMarkers.value.find(({friend}) =>
      friend.id === friendData.id || friend === friendData
  )
  return found?.marker
}

const focusOnFriend = (friendData) => {
  const marker = getMarkerByFriend(friendData)
  if (marker && props.map) {
    props.map.setView(marker.getLatLng(), 15)
    marker.openPopup()
  }
}

const updateFriendLocation = (friendId, newLocation) => {
  const friendMarker = friendMarkers.value.find(({friend}) => friend.id === friendId)
  if (friendMarker && newLocation.latitude && newLocation.longitude) {
    friendMarker.marker.setLatLng([newLocation.latitude, newLocation.longitude])

    // Update friend data
    friendMarker.friend = {...friendMarker.friend, ...newLocation}

    // Update popup content
    const newPopupContent = createBasicFriendPopup(friendMarker.friend)
    friendMarker.marker.setPopupContent(newPopupContent)
  }
}

// Watch for data changes
watch(() => props.friendsData, () => {
  if (baseLayerRef.value?.isReady) {
    renderFriendMarkers()
  }
}, {deep: true})

// Expose methods
defineExpose({
  baseLayerRef,
  friendMarkers: readonly(friendMarkers),
  getMarkerByFriend,
  focusOnFriend,
  updateFriendLocation,
  clearFriendMarkers
})
</script>

<style>
@import '@/styles/friendPopup.css';
</style>