<template>
  <VectorSharedLocationMarker
    v-if="map && location"
    :map="map"
    :latitude="location.latitude"
    :longitude="location.longitude"
    :share-data="shareData"
    :avatar-url="location.avatar"
    :open-popup="false"
  />
</template>

<script setup>
import { computed } from 'vue'
import VectorSharedLocationMarker from '@/maps/vector/markers/VectorSharedLocationMarker.vue'
import { useTimezone } from '@/composables/useTimezone'

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

const timezone = useTimezone()

const shareData = computed(() => ({
  shareName: 'Your Current Location',
  sharedBy: 'You',
  description: '',
  sharedAt: props.location?.timestamp || timezone.now().toISOString(),
  telemetry: props.location?.telemetryCurrentPopup || []
}))
</script>
