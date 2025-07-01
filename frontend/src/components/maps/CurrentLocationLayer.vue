<template>
  <!-- This component doesn't render anything in the template - it manages map layers directly -->
  <SharedLocationMarker
    v-if="map && location"
    :map="map"
    :latitude="location.latitude"
    :longitude="location.longitude"
    :share-data="shareData"
    :open-popup="false"
  />
</template>

<script setup>
import { computed } from 'vue'
import SharedLocationMarker from './SharedLocationMarker.vue'

// Props
const props = defineProps({
  map: {
    type: Object,
    required: true
  },
  location: {
    type: Object,
    required: true
  }
})

// Computed shareData for current location
const shareData = computed(() => ({
  shareName: 'Your Current Location',
  description: '',
  sharedAt: props.location?.timestamp || new Date().toISOString()
}))
</script>

<style>
/* No custom styles needed - using SharedLocationMarker */
</style>