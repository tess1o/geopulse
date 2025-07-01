<template>
  <BaseMarker
    :map="map"
    :lat-lng="[friend.latitude, friend.longitude]"
    :icon="markerIcon"
    :options="markerOptions"
    :visible="visible"
    :popup="popupConfig"
    @marker-click="handleMarkerClick"
    @marker-hover="handleMarkerHover"
  />
</template>

<script setup>
import { computed } from 'vue'
import BaseMarker from './BaseMarker.vue'
import { createAvatarDivIcon, createCustomDivIcon, MARKER_COLORS, MARKER_SIZES } from '@/utils/mapHelpers'
import { createPopupConfig } from '@/utils/friendPopupBuilder'

const props = defineProps({
  map: {
    type: Object,
    required: true
  },
  friend: {
    type: Object,
    required: true
  },
  visible: {
    type: Boolean,
    default: true
  },
  showPopup: {
    type: Boolean,
    default: true
  }
})

const emit = defineEmits(['marker-click', 'marker-hover'])

// Computed
const markerIcon = computed(() => {
  // Use avatar if available, otherwise fallback to generic friend icon
  if (props.friend.avatar || props.friend.avatarUrl) {
    return createAvatarDivIcon({
      avatarPath: props.friend.avatar || props.friend.avatarUrl,
      size: 40
    })
  }
  
  return createCustomDivIcon({
    color: MARKER_COLORS.FRIEND,
    icon: 'fas fa-user',
    size: MARKER_SIZES.HIGHLIGHT,
    className: 'custom-marker friend-marker',
    shape: 'circle',
    customStyle: {
      background: `linear-gradient(135deg, ${MARKER_COLORS.FRIEND}, #F39C12)`,
      border: '3px solid white'
    }
  })
})

const markerOptions = computed(() => ({
  friend: props.friend,
  alt: `${props.friend.name || props.friend.username || 'Friend'} location`
}))

const popupConfig = computed(() => {
  if (!props.showPopup) return null

  return createPopupConfig(props.friend, {
    detailed: true,
    actions: {
      message: (friendId) => {
        console.log('Message friend:', friendId)
        // Implement messaging functionality
      },
      locate: (friendId) => {
        console.log('Locate friend:', friendId)
        // Implement location functionality
      }
    },
    popupOptions: {
      closeButton: true,
      autoClose: false,
      className: 'friend-popup'
    }
  })
})


// Event handlers
const handleMarkerClick = (event) => {
  emit('marker-click', {
    friend: props.friend,
    event
  })
}

const handleMarkerHover = (event) => {
  emit('marker-hover', {
    friend: props.friend,
    event
  })
}
</script>

<style>
@import '@/styles/friendPopup.css';
</style>