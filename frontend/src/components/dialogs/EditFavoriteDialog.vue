<template>
  <Dialog v-model:visible="internalVisible"
          v-model:header="internalHeader"
          modal
          :class="isAreaFavorite ? 'gp-dialog-lg' : 'gp-dialog-sm'"
          @hide="onDialogHide">
    <div v-if="favoriteLocation" class="edit-favorite-content">
      <div class="form-field">
        <label for="name" class="field-label">{{ t('favoritesGeocodingDialogs.editFavorite.nameLabel') }}</label>
        <InputText
          id="name"
          v-model="favoriteLocation.name"
          :placeholder="t('favoritesGeocodingDialogs.editFavorite.namePlaceholder')"
          class="w-full"
        />
      </div>

      <div class="form-field">
        <label for="city" class="field-label">{{ t('favoritesGeocodingDialogs.editFavorite.cityLabel') }}</label>
        <InputText
          id="city"
          v-model="favoriteLocation.city"
          :placeholder="t('favoritesGeocodingDialogs.editFavorite.cityPlaceholder')"
          class="w-full"
        />
      </div>

      <div class="form-field">
        <label for="country" class="field-label">{{ t('favoritesGeocodingDialogs.editFavorite.countryLabel') }}</label>
        <InputText
          id="country"
          v-model="favoriteLocation.country"
          :placeholder="t('favoritesGeocodingDialogs.editFavorite.countryPlaceholder')"
          class="w-full"
        />
      </div>

      <!-- Area Bounds Section (only for AREA favorites) -->
      <div v-if="isAreaFavorite" class="bounds-section">
        <div class="bounds-header">
          <div class="bounds-title-group">
            <i class="pi pi-th-large"></i>
            <span class="bounds-title">{{ t('favoritesGeocodingDialogs.editFavorite.areaBoundariesTitle') }}</span>
          </div>
          <Button
            :label="isDrawing() ? t('favoritesGeocodingDialogs.editFavorite.drawing') : t('favoritesGeocodingDialogs.editFavorite.redrawArea')"
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
            <span>{{ t('favoritesGeocodingDialogs.editFavorite.drawInstruction') }}</span>
          </div>
        </div>

        <!-- Coordinates Display (read-only) -->
        <div class="bounds-info">
          <span class="bounds-info-label">{{ t('favoritesGeocodingDialogs.editFavorite.currentBoundsLabel') }}</span>
          <span class="bounds-info-text">
            {{ t('favoritesGeocodingDialogs.editFavorite.neLabel') }} {{ favoriteLocation.northEastLat?.toFixed(6) }}, {{ favoriteLocation.northEastLon?.toFixed(6) }}
            | {{ t('favoritesGeocodingDialogs.editFavorite.swLabel') }} {{ favoriteLocation.southWestLat?.toFixed(6) }}, {{ favoriteLocation.southWestLon?.toFixed(6) }}
          </span>
        </div>
      </div>
    </div>
    <template #footer>
      <Button
        :label="t('common.cancel')"
        severity="secondary"
        outlined
        @click="onDialogHide"
      />
      <Button
        :label="t('favoritesGeocodingDialogs.editFavorite.save')"
        @click="onEditButton"
      />
    </template>
  </Dialog>
</template>

<script setup>
import {ref, computed, watch, onUnmounted} from 'vue'
import { useI18n } from 'vue-i18n'
import Button from "primevue/button"
import Dialog from "primevue/dialog"
import InputText from "primevue/inputtext"
import BaseMap from '@/components/maps/BaseMap.vue'
import { useRectangleDrawingRuntime } from '@/composables/useRectangleDrawingRuntime'
import { createFavoriteAreaOverlayAdapter } from '@/maps/favoritesEdit/runtime/createFavoriteAreaOverlayAdapter'
import {
  getAreaCenterLatLng,
  toLeafletBounds
} from '@/maps/favoritesManagement/shared/favoritesManagementGeometry'

const { t } = useI18n()

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
const areaOverlayAdapter = ref(null)
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
} = useRectangleDrawingRuntime({
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

const clearCurrentAreaOverlay = () => {
  areaOverlayAdapter.value?.clear?.()
}

const initializeAreaOverlayAdapter = (map) => {
  areaOverlayAdapter.value?.destroy?.()
  const adapter = createFavoriteAreaOverlayAdapter(map)
  adapter.initialize(map)
  areaOverlayAdapter.value = adapter
}

// Methods
const handleMapReady = (map) => {
  mapInstance.value = map

  // Initialize rectangle drawing
  initializeDrawing(mapInstance.value)
  initializeAreaOverlayAdapter(mapInstance.value)

  // If it's an area favorite, center on it and draw it
  if (isAreaFavorite.value && props.favoriteLocation) {
    const center = getAreaCenterLatLng(props.favoriteLocation)
    if (center) {
      mapCenter.value = [center.lat, center.lng]
    }

    // Fit bounds to show the entire area
    const bounds = toLeafletBounds(props.favoriteLocation)
    if (bounds) {
      mapInstance.value.fitBounds(bounds, {padding: [50, 50]})
    }

    // Draw the current area
    drawCurrentArea()
  }
}

const drawCurrentArea = () => {
  if (!mapInstance.value || !isAreaFavorite.value) return

  areaOverlayAdapter.value?.draw?.(props.favoriteLocation)
}

const handleRedrawArea = () => {
  if (!mapInstance.value) return

  clearCurrentAreaOverlay()

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

  clearCurrentAreaOverlay()
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

watch(
  () => [
    props.favoriteLocation?.northEastLat,
    props.favoriteLocation?.northEastLon,
    props.favoriteLocation?.southWestLat,
    props.favoriteLocation?.southWestLon
  ],
  () => {
    if (isAreaFavorite.value && mapInstance.value) {
      drawCurrentArea()
    }
  }
)

// Cleanup on unmount
onUnmounted(() => {
  if (isDrawing()) {
    stopDrawing()
  }
  cleanupTempLayer()

  try {
    clearCurrentAreaOverlay()
  } catch (error) {
    console.warn('Error clearing area overlay:', error)
  }

  areaOverlayAdapter.value?.destroy?.()
  areaOverlayAdapter.value = null
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
  border-top: 1px solid var(--gp-border);
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
  border: 1px solid var(--gp-border);
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
  background: var(--gp-surface-ground);
  border-radius: var(--gp-radius-medium);
  font-size: 0.875rem;
}

.bounds-info-label {
  font-weight: 600;
  color: var(--gp-text-secondary);
}

.bounds-info-text {
  font-family: var(--gp-font-mono);
  color: var(--gp-text-primary);
  font-size: 0.8rem;
}

@media (max-width: 768px) {
  .map-container {
    height: 250px;
  }
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

/* Responsive */
@media (max-width: 768px) {
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
