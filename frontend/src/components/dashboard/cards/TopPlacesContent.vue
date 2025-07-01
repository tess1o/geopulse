<template>
  <!-- No Data State -->
  <div v-if="noDataAvailable" class="no-data-container">
    <div class="no-data-content">
      <i class="pi pi-map-marker no-data-icon"></i>
      <h3 class="no-data-title">No Places Data</h3>
      <p class="no-data-message">
        There are no places visited during this period.
      </p>
    </div>
  </div>

  <!-- Places List -->
  <div v-else class="places-content">
    <div class="places-list">
      <div
        v-for="place in placesArray"
        :key="place.name"
        class="place-item"
        @click="showPlaceOnMap(place.coordinates, place.name)"
      >
        <div class="place-info">
          <div class="place-icon">
            <i class="pi pi-map-marker"></i>
          </div>
          <div class="place-details">
            <div class="place-name">{{ place.name }}</div>
          </div>
        </div>

        <div class="place-stats">
          <div class="place-visits">{{ place.visits }} visits</div>
          <div class="place-duration">{{ formatDuration(place.duration) }} total</div>
        </div>
      </div>
    </div>

    <!-- Map Dialog -->
    <PlacesMap
      :showMap="showMapPopup"
      :coordinates="selectedCoordinates"
      :title="selectedPlaceName"
      marker-type="favorite"
      @close="closeMapPopup"
    />
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { formatDuration } from '@/utils/calculationsHelpers'
import PlacesMap from '@/components/maps/dialogs/PlacesMap.vue'

const props = defineProps({
  places: {
    type: [Object, Array],
    default: () => ([])
  }
})

// Reactive state
const selectedCoordinates = ref([0, 0])
const selectedPlaceName = ref('')
const showMapPopup = ref(false)

// Computed properties
const noDataAvailable = computed(() => {
  if (Array.isArray(props.places)) {
    return props.places.length === 0
  }
  return Object.keys(props.places).length === 0
})

const placesArray = computed(() => {
  if (Array.isArray(props.places)) {
    return props.places
  }
  // Convert object to array if needed
  return Object.values(props.places)
})

// Methods
const showPlaceOnMap = (coordinates, placeName = 'Selected Location') => {
  if (!coordinates || coordinates.length < 2) {
    console.warn('Invalid coordinates provided:', coordinates)
    return
  }

  selectedCoordinates.value = coordinates
  selectedPlaceName.value = placeName
  showMapPopup.value = true
}

const closeMapPopup = () => {
  showMapPopup.value = false
  selectedCoordinates.value = [0, 0]
  selectedPlaceName.value = ''
}
</script>

<style scoped>
/* Places Content */
.places-content {
  width: 100%;
}

.places-list {
  display: flex;
  flex-direction: column;
  gap: var(--gp-spacing-sm);
}

.place-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--gp-spacing-md);
  border-radius: var(--gp-radius-medium);
  cursor: pointer;
  transition: all 0.2s ease;
  border: 1px solid transparent;
}

.place-item:hover {
  background-color: var(--gp-surface-light);
  border-color: var(--gp-primary-light);
  transform: translateX(2px);
}

.place-info {
  display: flex;
  align-items: center;
  gap: var(--gp-spacing-md);
  flex: 1;
  min-width: 0;
}

.place-icon {
  width: 32px;
  height: 32px;
  background: var(--gp-timeline-green);
  border: 1px solid var(--gp-secondary-light);
  border-radius: var(--gp-radius-medium);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  color: var(--gp-secondary);
}

.place-details {
  flex: 1;
  min-width: 0;
}

.place-name {
  font-weight: 500;
  color: var(--gp-text-primary);
  font-size: 0.875rem;
  line-height: 1.3;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.place-stats {
  text-align: right;
  font-size: 0.8rem;
  flex-shrink: 0;
  line-height: 1.3;
}

.place-visits {
  font-weight: 600;
  color: var(--gp-primary);
  margin-bottom: 0.125rem;
}

.place-duration {
  color: var(--gp-text-secondary);
  font-weight: 500;
}

/* No Data State */
.no-data-container {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 120px;
  padding: var(--gp-spacing-lg);
}

.no-data-content {
  text-align: center;
}

.no-data-icon {
  font-size: 2rem;
  color: var(--gp-text-muted);
  margin-bottom: var(--gp-spacing-md);
  display: block;
}

.no-data-title {
  font-size: 1rem;
  font-weight: 600;
  color: var(--gp-text-secondary);
  margin: 0 0 var(--gp-spacing-sm);
}

.no-data-message {
  font-size: 0.875rem;
  color: var(--gp-text-muted);
  margin: 0;
  max-width: 250px;
  line-height: 1.4;
}

/* Dark Mode */
.p-dark .place-item:hover {
  background-color: var(--gp-surface-darker);
}

.p-dark .place-name {
  color: var(--gp-text-primary);
}

.p-dark .place-visits {
  color: var(--gp-primary);
}

.p-dark .place-duration {
  color: var(--gp-text-secondary);
}

.p-dark .no-data-icon {
  color: var(--gp-text-muted);
}

.p-dark .no-data-title {
  color: var(--gp-text-secondary);
}

.p-dark .no-data-message {
  color: var(--gp-text-muted);
}

/* Responsive adjustments */
@media (max-width: 640px) {
  .place-item {
    padding: var(--gp-spacing-sm);
    flex-direction: column;
    align-items: flex-start;
    gap: var(--gp-spacing-sm);
  }

  .place-info {
    width: 100%;
  }

  .place-stats {
    text-align: left;
    width: 100%;
    padding-left: 2.5rem; /* Align with place name */
  }

  .place-icon {
    width: 28px;
    height: 28px;
  }

  .place-name {
    font-size: 0.8rem;
  }

  .place-stats {
    font-size: 0.75rem;
  }
}
</style>