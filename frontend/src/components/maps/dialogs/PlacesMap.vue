<template>
  <Dialog
    v-model:visible="internalVisible"
    :header="title || 'Place Location'"
    :modal="true"
    :style="dialogStyle"
    class="places-map-dialog"
    @hide="$emit('close')"
  >
    <div class="places-map-content">
      <MapContainer
        ref="mapContainerRef"
        :map-id="`places-map-${mapId}`"
        :center="coordinates"
        :zoom="16"
        :show-controls="false"
        :height="mapHeight"
        width="100%"
        @map-ready="handleMapReady"
      >
        <template #overlays="{ map, isReady }">
          <FavoritesLayer
            v-if="map && isReady"
            :map="map"
            :favorites-data="favoriteData"
            :visible="true"
          />
        </template>
      </MapContainer>
    </div>
  </Dialog>
</template>

<script setup>
import { ref, computed, watch, nextTick } from 'vue'
import { Dialog } from 'primevue'
import { MapContainer, FavoritesLayer } from '@/components/maps'

const props = defineProps({
  coordinates: {
    type: Array,
    required: true,
    default: () => [0, 0]
  },
  showMap: {
    type: Boolean,
    default: false
  },
  title: {
    type: String,
    default: 'Place Location'
  },
  markerType: {
    type: String,
    default: 'favorite'
  }
})

const emit = defineEmits(['close'])

// State
const internalVisible = ref(props.showMap)
const mapId = ref(Math.random().toString(36).substr(2, 9))
const mapContainerRef = ref(null)
const dialogStyle = {
  width: 'min(92vw, 760px)',
  maxWidth: '760px'
}
const mapHeight = 'min(60vh, 420px)'

// Computed
const favoriteData = computed(() => {
  if (!props.coordinates || props.coordinates.length < 2) return []
  
  return [{
    id: 'place-marker',
    name: props.title || 'Selected Place',
    latitude: props.coordinates[0],
    longitude: props.coordinates[1],
    type: 'point',
    isPoint: true
  }]
})

// Methods
const handleMapReady = () => {
  // Center map on the coordinates
  if (props.coordinates && props.coordinates.length >= 2) {
    nextTick(() => {
      mapContainerRef.value?.setView?.(props.coordinates, 16)
    })
  }
}

// Watchers
watch(() => props.showMap, (val) => {
  internalVisible.value = val
})

watch(internalVisible, (val) => {
  if (!val) {
    emit('close')
  }
})
</script>

<script>
export default {
  name: 'PlacesMap'
}
</script>

<style scoped>
.places-map-content {
  width: 100%;
  height: min(60vh, 420px);
  min-height: 280px;
  border-radius: 8px;
  overflow: hidden;
}

/* Responsive design */
@media (max-width: 768px) {
  :global(.places-map-dialog.p-dialog) {
    width: calc(100vw - 1rem) !important;
    max-width: calc(100vw - 1rem) !important;
    margin: 0.5rem;
  }

  :global(.places-map-dialog .p-dialog-content) {
    padding: var(--gp-spacing-sm);
  }

  .places-map-content {
    height: min(58vh, 380px);
    min-height: 260px;
  }
}

@media (max-width: 420px) {
  .places-map-content {
    height: min(55vh, 340px);
  }
}
</style>
