<template>
  <Dialog
    v-model:visible="internalVisible"
    :header="title || 'Place Location'"
    :modal="true"
    :style="{ width: '50vw' }"
    @hide="$emit('close')"
  >
    <div class="places-map-content">
      <MapContainer
        :map-id="`places-map-${mapId}`"
        :center="coordinates"
        :zoom="16"
        :show-controls="true"
        height="400px"
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
const handleMapReady = (map) => {
  // Center map on the coordinates
  if (props.coordinates && props.coordinates.length >= 2) {
    nextTick(() => {
      map.setView(props.coordinates, 16)
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
  height: 400px;
  border-radius: 8px;
  overflow: hidden;
}

/* Responsive design */
@media (max-width: 768px) {
  :deep(.p-dialog) {
    width: 95vw !important;
    margin: 1rem;
  }
  
  .places-map-content {
    height: 300px;
  }
}
</style>