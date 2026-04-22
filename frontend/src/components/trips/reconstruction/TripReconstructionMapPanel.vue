<template>
  <div class="map-panel">
    <MapContainer
      :map-id="mapId"
      :center="center"
      :zoom="zoom"
      :show-controls="false"
      height="600px"
      width="100%"
      @map-ready="(map) => emit('map-ready', map)"
      @map-click="(event) => emit('map-click', event)"
    />

    <TripReconstructionMapOverlay
      :map="map"
      :active-segment="activeSegment"
      :active-segment-index="activeSegmentIndex"
      :context-points="contextPoints"
      :context-trip-lines="contextTripLines"
      :id-prefix="idPrefix"
      @stay-dragged="(payload) => emit('stay-dragged', payload)"
      @waypoint-dragged="(payload) => emit('waypoint-dragged', payload)"
      @waypoint-removed="(payload) => emit('waypoint-removed', payload)"
    />

    <div class="map-hint">
      <span v-if="activeSegment?.segmentType === 'TRIP'">
        Click map to append waypoint for this trip segment. Existing stays/trips are shown as context.
      </span>
      <span v-else-if="activeSegment?.segmentType === 'STAY'">
        Click map to place the stay location.
      </span>
      <span v-else>
        Select a segment to edit it on the map.
      </span>
    </div>

    <slot />
  </div>
</template>

<script setup>
import { MapContainer } from '@/components/maps'
import TripReconstructionMapOverlay from '@/components/trips/reconstruction/TripReconstructionMapOverlay.vue'
import '@/maps/shared/styles/tripReconstructionMarkers.css'

defineProps({
  mapId: {
    type: String,
    required: true
  },
  center: {
    type: Array,
    required: true
  },
  zoom: {
    type: Number,
    required: true
  },
  map: {
    type: Object,
    default: null
  },
  activeSegment: {
    type: Object,
    default: null
  },
  activeSegmentIndex: {
    type: Number,
    default: -1
  },
  contextPoints: {
    type: Array,
    default: () => []
  },
  contextTripLines: {
    type: Array,
    default: () => []
  },
  idPrefix: {
    type: String,
    default: null
  }
})

const emit = defineEmits([
  'map-ready',
  'map-click',
  'stay-dragged',
  'waypoint-dragged',
  'waypoint-removed'
])
</script>

<style scoped>
.map-panel {
  display: flex;
  flex-direction: column;
  gap: var(--gp-spacing-sm);
}

.map-hint {
  font-size: 0.84rem;
  color: var(--gp-text-secondary);
}
</style>
