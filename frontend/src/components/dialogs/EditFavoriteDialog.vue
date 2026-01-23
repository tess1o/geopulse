<template>
  <Dialog v-model:visible="internalVisible"
          v-model:header="internalHeader"
          modal
          :class="isAreaFavorite ? 'gp-dialog-lg' : 'gp-dialog-sm'"
          @hide="onDialogHide">
    <div v-if="favoriteLocation" class="edit-favorite-content">
      <div class="form-field">
        <label for="name" class="field-label">Name</label>
        <InputText
          id="name"
          v-model="favoriteLocation.name"
          placeholder="Enter location name"
          class="w-full"
        />
      </div>

      <div class="form-field">
        <label for="city" class="field-label">City</label>
        <InputText
          id="city"
          v-model="favoriteLocation.city"
          placeholder="Enter city (optional)"
          class="w-full"
        />
      </div>

      <div class="form-field">
        <label for="country" class="field-label">Country</label>
        <InputText
          id="country"
          v-model="favoriteLocation.country"
          placeholder="Enter country (optional)"
          class="w-full"
        />
      </div>

      <!-- Area Bounds Section (only for AREA favorites) -->
      <div v-if="isAreaFavorite" class="bounds-section">
        <div class="bounds-header">
          <div class="bounds-title-group">
            <i class="pi pi-th-large"></i>
            <span class="bounds-title">Area Boundaries</span>
          </div>
          <Button
            :label="isDrawing() ? 'Drawing...' : 'Redraw Area'"
            icon="pi pi-pencil"
            size="small"
            @click="handleRedrawArea"
            :disabled="isDrawing()"
          />
        </div>

        <!-- Map for drawing area -->
        <div class="map-container">
          <BaseMap
            mapId="edit-area-map"
            :center="mapCenter"
            :zoom="mapZoom"
            height="100%"
            width="100%"
            @map-ready="handleMapReady"
            ref="baseMapRef"
          />
          <!-- Drawing instruction overlay -->
          <div v-if="isDrawing()" class="drawing-instruction">
            <i class="pi pi-info-circle"></i>
            <span>Click and drag on the map to draw a new rectangular area</span>
          </div>
        </div>

        <!-- Coordinates Display (read-only) -->
        <div class="bounds-info">
          <span class="bounds-info-label">Current Bounds:</span>
          <span class="bounds-info-text">
            NE: {{ favoriteLocation.northEastLat?.toFixed(6) }}, {{ favoriteLocation.northEastLon?.toFixed(6) }}
            | SW: {{ favoriteLocation.southWestLat?.toFixed(6) }}, {{ favoriteLocation.southWestLon?.toFixed(6) }}
          </span>
        </div>
      </div>
    </div>
    <template #footer>
      <Button
        label="Cancel"
        severity="secondary"
        outlined
        @click="onDialogHide"
      />
      <Button
        label="Save"
        @click="onEditButton"
      />
    </template>
  </Dialog>
</template>

<script setup>
import {ref, computed, watch, onUnmounted} from 'vue'
import Button from "primevue/button"
import Dialog from "primevue/dialog"
import InputText from "primevue/inputtext"
import BaseMap from '@/components/maps/BaseMap.vue'
import {useRectangleDrawing} from '@/composables/useRectangleDrawing'

const props = defineProps({
  visible: Boolean,
  header: String,
  favoriteLocation: Object
})

const emit = defineEmits(['edit-favorite', 'close'])

// Dialog state
const internalVisible = ref(props.visible)
const internalHeader = ref(props.header)

// Map state
const baseMapRef = ref(null)
const mapInstance = ref(null)
const currentRectangle = ref(null)
const mapCenter = ref([51.505, -0.09])
const mapZoom = ref(13)

// Computed
const isAreaFavorite = computed(() => props.favoriteLocation?.type === 'AREA')

// Rectangle drawing composable
const {
  isDrawing,
  initialize: initializeDrawing,
  startDrawing,
  stopDrawing,
  cleanupTempLayer
} = useRectangleDrawing({
  onRectangleCreated: (data) => {
    const bounds = data.bounds
    const southWest = bounds.getSouthWest()
    const northEast = bounds.getNorthEast()

    // Update the favoriteLocation with new bounds
    props.favoriteLocation.northEastLat = northEast.lat
    props.favoriteLocation.northEastLon = northEast.lng
    props.favoriteLocation.southWestLat = southWest.lat
    props.favoriteLocation.southWestLon = southWest.lng

    // Redraw the rectangle on the map
    drawCurrentArea()
  }
})

// Methods
const handleMapReady = (map) => {
  mapInstance.value = map

  // Initialize rectangle drawing
  initializeDrawing(mapInstance.value)

  // If it's an area favorite, center on it and draw it
  if (isAreaFavorite.value && props.favoriteLocation) {
    const centerLat = (props.favoriteLocation.southWestLat + props.favoriteLocation.northEastLat) / 2
    const centerLon = (props.favoriteLocation.southWestLon + props.favoriteLocation.northEastLon) / 2
    mapCenter.value = [centerLat, centerLon]

    // Fit bounds to show the entire area
    const bounds = baseMapRef.value.L.latLngBounds(
      [props.favoriteLocation.southWestLat, props.favoriteLocation.southWestLon],
      [props.favoriteLocation.northEastLat, props.favoriteLocation.northEastLon]
    )
    mapInstance.value.fitBounds(bounds, {padding: [50, 50]})

    // Draw the current area
    drawCurrentArea()
  }
}

const drawCurrentArea = () => {
  if (!mapInstance.value || !baseMapRef.value || !isAreaFavorite.value) return

  // Remove existing rectangle if any
  if (currentRectangle.value) {
    mapInstance.value.removeLayer(currentRectangle.value)
  }

  // Draw new rectangle
  const bounds = [
    [props.favoriteLocation.southWestLat, props.favoriteLocation.southWestLon],
    [props.favoriteLocation.northEastLat, props.favoriteLocation.northEastLon]
  ]

  currentRectangle.value = baseMapRef.value.L.rectangle(bounds, {
    color: '#ef4444',
    fillColor: '#ef4444',
    fillOpacity: 0.2,
    weight: 2
  }).addTo(mapInstance.value)
}

const handleRedrawArea = () => {
  if (!mapInstance.value) return

  // Remove the current rectangle
  if (currentRectangle.value) {
    mapInstance.value.removeLayer(currentRectangle.value)
    currentRectangle.value = null
  }

  // Start drawing mode
  startDrawing()
}

const onEditButton = () => {
  if (!props.favoriteLocation) return

  const basicData = {
    id: props.favoriteLocation.id,
    name: props.favoriteLocation.name,
    city: props.favoriteLocation.city,
    country: props.favoriteLocation.country,
    type: props.favoriteLocation.type
  }

  // If it's an area favorite, also include bounds data
  if (isAreaFavorite.value) {
    basicData.northEastLat = props.favoriteLocation.northEastLat
    basicData.northEastLon = props.favoriteLocation.northEastLon
    basicData.southWestLat = props.favoriteLocation.southWestLat
    basicData.southWestLon = props.favoriteLocation.southWestLon
  }

  emit('edit-favorite', basicData)
}

const onDialogHide = () => {
  internalVisible.value = false

  // Clean up drawing
  if (isDrawing()) {
    stopDrawing()
  }
  cleanupTempLayer()

  // Remove rectangle
  if (currentRectangle.value && mapInstance.value) {
    mapInstance.value.removeLayer(currentRectangle.value)
    currentRectangle.value = null
  }
}

// Watchers
watch(() => props.visible, (val) => {
  internalVisible.value = val
})

watch(internalVisible, (val) => {
  if (!val) {
    emit('close')
  }
})

// Cleanup on unmount
onUnmounted(() => {
  if (isDrawing()) {
    stopDrawing()
  }
  cleanupTempLayer()

  if (currentRectangle.value && mapInstance.value) {
    try {
      mapInstance.value.removeLayer(currentRectangle.value)
    } catch (error) {
      console.warn('Error removing rectangle:', error)
    }
  }
})
</script>

<style scoped>
.edit-favorite-content {
  padding: 0.5rem 0;
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.form-field {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
}

.field-label {
  font-weight: 600;
  font-size: 0.85rem;
  color: var(--gp-text-primary);
}

/* Bounds Section */
.bounds-section {
  margin-top: 1rem;
  padding-top: 1rem;
  border-top: 1px solid var(--gp-border-light);
}

.bounds-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 0.75rem;
}

.bounds-title-group {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  color: var(--gp-text-primary);
}

.bounds-title-group i {
  font-size: 1.1rem;
  color: var(--gp-primary);
}

.bounds-title {
  font-weight: 600;
  font-size: 1rem;
}

/* Map Container */
.map-container {
  width: 100%;
  height: 350px;
  border-radius: var(--gp-radius-medium);
  overflow: hidden;
  border: 1px solid var(--gp-border-medium);
  margin-bottom: 0.75rem;
  position: relative;
}

/* Drawing Instruction Overlay */
.drawing-instruction {
  position: absolute;
  top: 10px;
  left: 50%;
  transform: translateX(-50%);
  background: var(--gp-primary);
  color: var(--gp-neutral-white);
  padding: 0.75rem 1.25rem;
  border-radius: var(--gp-radius-medium);
  display: flex;
  align-items: center;
  gap: 0.5rem;
  z-index: 1000;
  box-shadow: var(--gp-shadow-medium);
  font-size: 0.875rem;
  font-weight: 500;
  animation: fadeIn 0.3s ease;
}

.drawing-instruction i {
  font-size: 1rem;
}

@keyframes fadeIn {
  from {
    opacity: 0;
    transform: translateX(-50%) translateY(-10px);
  }
  to {
    opacity: 1;
    transform: translateX(-50%) translateY(0);
  }
}

/* Bounds Info */
.bounds-info {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
  padding: 0.75rem;
  background: var(--gp-surface-light);
  border-radius: var(--gp-radius-medium);
  font-size: 0.875rem;
}

.bounds-info-label {
  font-weight: 600;
  color: var(--gp-text-secondary);
}

.bounds-info-text {
  font-family: monospace;
  color: var(--gp-text-primary);
  font-size: 0.8rem;
}

@media (max-width: 768px) {
  .map-container {
    height: 250px;
  }
}

/* GeoPulse Dialog Styling */
:deep(.p-dialog) {
  border-radius: var(--gp-radius-large);
  box-shadow: var(--gp-shadow-large);
  border: 1px solid var(--gp-border-medium);
}

:deep(.p-dialog-header) {
  background: var(--gp-surface-white);
  border-bottom: 1px solid var(--gp-border-light);
  border-radius: var(--gp-radius-large) var(--gp-radius-large) 0 0;
  padding: 1rem 1.25rem;
}

:deep(.p-dialog-title) {
  font-weight: 600;
  color: var(--gp-text-primary);
  font-size: 1.1rem;
}

:deep(.p-dialog-content) {
  background: var(--gp-surface-white);
  padding: 0 1.25rem;
  color: var(--gp-text-primary);
}

:deep(.p-dialog-footer) {
  background: var(--gp-surface-white);
  border-top: 1px solid var(--gp-border-light);
  border-radius: 0 0 var(--gp-radius-large) var(--gp-radius-large);
  padding: 1rem 1.25rem;
  display: flex;
  justify-content: flex-end;
  gap: 0.75rem;
}

/* Input Styling */
:deep(.p-inputtext) {
  border-radius: var(--gp-radius-medium);
  border: 1px solid var(--gp-border-medium);
  padding: 0.5rem 0.75rem;
  font-size: 0.95rem;
  transition: all 0.2s ease;
}

:deep(.p-inputtext:focus) {
  border-color: var(--gp-primary);
  box-shadow: 0 0 0 3px rgba(26, 86, 219, 0.1);
  outline: none;
}

/* Button Styling */
:deep(.p-button) {
  border-radius: var(--gp-radius-medium);
  font-weight: 600;
  padding: 0.5rem 1.25rem;
  font-size: 0.95rem;
  transition: all 0.2s ease;
}

:deep(.p-button:not(.p-button-outlined)) {
  background: var(--gp-primary);
  border-color: var(--gp-primary);
  color: var(--gp-neutral-white);
}

:deep(.p-button:not(.p-button-outlined):hover) {
  background: var(--gp-primary-hover);
  border-color: var(--gp-primary-hover);
  color: var(--gp-neutral-white);
}

:deep(.p-button.p-button-outlined) {
  border-color: var(--gp-border-medium);
  color: var(--gp-text-secondary);
}

:deep(.p-button.p-button-outlined:hover) {
  background: var(--gp-surface-light);
  border-color: var(--gp-border-dark);
  color: var(--gp-text-primary);
}

/* Dark Mode */
.p-dark :deep(.p-dialog) {
  background: var(--gp-surface-dark);
  border-color: var(--gp-border-dark);
}

.p-dark :deep(.p-dialog-header),
.p-dark :deep(.p-dialog-content),
.p-dark :deep(.p-dialog-footer) {
  background: var(--gp-surface-dark);
  border-color: var(--gp-border-dark);
}

.p-dark :deep(.p-dialog-title) {
  color: var(--gp-text-primary);
}

.p-dark :deep(.p-inputtext) {
  background: var(--gp-surface-darker);
  border-color: var(--gp-border-dark);
  color: var(--gp-text-primary);
}

.p-dark :deep(.p-inputtext:focus) {
  border-color: var(--gp-primary);
  box-shadow: 0 0 0 3px rgba(26, 86, 219, 0.2);
}

.p-dark :deep(.p-button.p-button-outlined) {
  border-color: var(--gp-border-dark);
  color: var(--gp-text-primary);
}

.p-dark :deep(.p-button.p-button-outlined:hover) {
  background: var(--gp-surface-darker);
  border-color: var(--gp-border-light);
  color: var(--gp-text-primary);
}

.p-dark :deep(.p-button:not(.p-button-outlined)) {
  background: var(--gp-primary);
  border-color: var(--gp-primary);
  color: var(--gp-neutral-white);
}

.p-dark .bounds-section {
  border-top-color: var(--gp-border-dark);
}

.p-dark .map-container {
  border-color: var(--gp-border-dark);
}

.p-dark .bounds-info {
  background: var(--gp-surface-darker);
}

/* Responsive */
@media (max-width: 1024px) {
  :deep(.p-dialog) {
    width: 90vw !important;
    max-width: 800px !important;
  }
}

@media (max-width: 768px) {
  :deep(.p-dialog) {
    width: 95vw !important;
  }

  .bounds-header {
    flex-direction: column;
    align-items: stretch;
    gap: 0.75rem;
  }

  .bounds-header > button {
    align-self: flex-end;
  }
}
</style>