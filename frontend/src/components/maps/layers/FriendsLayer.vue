<template>
  <component
    :is="activeComponent"
    ref="implRef"
    :map="map"
    :friends-data="friendsData"
    :friend-trails="friendTrails"
    :show-trails="showTrails"
    :visible="visible"
    :marker-options="markerOptions"
    @friend-click="(payload) => emit('friend-click', payload)"
    @friend-hover="(payload) => emit('friend-hover', payload)"
  />
</template>

<script setup>
import { computed, ref } from 'vue'
import RasterFriendsLayer from '@/maps/raster/layers/RasterFriendsLayer.vue'
import VectorFriendsLayer from '@/maps/vector/layers/VectorFriendsLayer.vue'
import { MAP_RENDER_MODES, resolveMapEngineModeFromInstance } from '@/maps/contracts/mapContracts'

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

const implRef = ref(null)
const mapMode = computed(() => resolveMapEngineModeFromInstance(props.map, MAP_RENDER_MODES.RASTER))
const activeComponent = computed(() => mapMode.value === MAP_RENDER_MODES.VECTOR ? VectorFriendsLayer : RasterFriendsLayer)

const getMarkerByFriend = (...args) => implRef.value?.getMarkerByFriend?.(...args)
const focusOnFriend = (...args) => implRef.value?.focusOnFriend?.(...args)
const updateFriendLocation = (...args) => implRef.value?.updateFriendLocation?.(...args)
const clearFriendMarkers = (...args) => implRef.value?.clearFriendMarkers?.(...args)

defineExpose({
  implRef,
  getMarkerByFriend,
  focusOnFriend,
  updateFriendLocation,
  clearFriendMarkers
})
</script>
