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
import { useTimezone } from '@/composables/useTimezone';

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
const timezone = useTimezone()
const shareData = computed(() => ({
  shareName: 'Your Current Location',
  sharedBy: 'You',
  description: '',
  sharedAt: props.location?.timestamp || timezone.now().toISOString()
}))
</script>

<style>
/* No custom styles needed - using SharedLocationMarker */
</style>