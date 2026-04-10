<template>
  <Dialog
    v-model:visible="internalVisible"
    :header="'Edit GPS Point'"
    :modal="true"
    class="gp-dialog-md"
    @hide="$emit('close')"
  >
    <div class="edit-dialog-content">
      <!-- Map Section (always visible) -->
      <div class="map-section">
        <p class="map-instructions">Click on the map to select a new location for this GPS point.</p>
        <MapContainer
          :map-id="`gps-edit-map-${mapId}`"
          :center="mapCenter"
          :zoom="16"
          :show-controls="false"
          height="300px"
          width="100%"
          @map-ready="handleMapReady"
          @map-click="handleMapClick"
        />
      </div>

      <!-- Form Section -->
      <div class="form-section">
        <form @submit.prevent="handleSave" class="gps-edit-form">
          <!-- Location Section -->
          <div class="field-group">
            <label class="field-label">Location</label>
            <div class="location-fields">
              <div class="field">
                <label for="latitude">Latitude</label>
                <InputNumber 
                  id="latitude"
                  v-model="formData.coordinates.lat"
                  :min-fraction-digits="6"
                  :max-fraction-digits="6"
                  :min="-90"
                  :max="90"
                  placeholder="Latitude"
                  class="location-input"
                  :class="{ 'p-invalid': errors.lat }"
                  @input="updateMapMarker"
                />
                <small v-if="errors.lat" class="p-error">{{ errors.lat }}</small>
              </div>
              <div class="field">
                <label for="longitude">Longitude</label>
                <InputNumber 
                  id="longitude"
                  v-model="formData.coordinates.lng"
                  :min-fraction-digits="6"
                  :max-fraction-digits="6"
                  :min="-180"
                  :max="180"
                  placeholder="Longitude"
                  class="location-input"
                  :class="{ 'p-invalid': errors.lng }"
                  @input="updateMapMarker"
                />
                <small v-if="errors.lng" class="p-error">{{ errors.lng }}</small>
              </div>
            </div>
          </div>

          <!-- Speed Section -->
          <div class="field">
            <label for="velocity">Speed (km/h)</label>
            <InputNumber 
              id="velocity"
              v-model="formData.velocity"
              :min-fraction-digits="1"
              :max-fraction-digits="1"
              :min="0"
              placeholder="Speed in km/h"
              class="w-full"
            />
          </div>

          <!-- Accuracy Section -->
          <div class="field">
            <label for="accuracy">Accuracy (meters)</label>
            <InputNumber 
              id="accuracy"
              v-model="formData.accuracy"
              :min-fraction-digits="1"
              :max-fraction-digits="1"
              :min="0"
              placeholder="Accuracy in meters"
              class="w-full"
            />
          </div>
        </form>
      </div>
    </div>

    <!-- Dialog Footer -->
    <template #footer>
      <Button 
        label="Cancel" 
        severity="secondary" 
        @click="handleCancel" 
        :disabled="loading"
      />
      <Button 
        label="Save" 
        @click="handleSave" 
        :loading="loading"
        :disabled="!isFormValid"
      />
    </template>

  </Dialog>
</template>

<script setup>
import { ref, computed, watch, reactive, nextTick, onUnmounted } from 'vue'
import Dialog from 'primevue/dialog'
import Button from 'primevue/button'
import InputNumber from 'primevue/inputnumber'
import { MapContainer } from '@/components/maps'
import L from 'leaflet'
import maplibregl from 'maplibre-gl'
import { MAP_RENDER_MODES, resolveMapEngineModeFromInstance } from '@/maps/contracts/mapContracts'

const props = defineProps({
  visible: {
    type: Boolean,
    default: false
  },
  gpsPoint: {
    type: Object,
    default: () => null
  }
})

const emit = defineEmits(['close', 'save'])

// State
const internalVisible = ref(props.visible)
const loading = ref(false)
const mapId = ref(Math.random().toString(36).substring(2, 11))
const mapInstance = ref(null)
const mapAdapter = ref(null)
const originalLocation = ref(null)

// Form data
const formData = reactive({
  coordinates: {
    lat: 0,
    lng: 0
  },
  velocity: null,
  accuracy: null
})

// Validation errors
const errors = reactive({
  lat: '',
  lng: ''
})

// Computed
const mapCenter = computed(() => {
  if (hasValidCoordinates(Number(formData.coordinates.lat), Number(formData.coordinates.lng))) {
    return [Number(formData.coordinates.lat), Number(formData.coordinates.lng)]
  }
  return [37.7749, -122.4194] // Default to San Francisco
})

const isFormValid = computed(() => {
  return formData.coordinates.lat !== null && 
         formData.coordinates.lng !== null &&
         formData.coordinates.lat >= -90 && 
         formData.coordinates.lat <= 90 &&
         formData.coordinates.lng >= -180 && 
         formData.coordinates.lng <= 180 &&
         !errors.lat && 
         !errors.lng
})

// Methods
const initializeForm = () => {
  if (props.gpsPoint) {
    formData.coordinates.lat = props.gpsPoint.coordinates.lat
    formData.coordinates.lng = props.gpsPoint.coordinates.lng
    formData.velocity = props.gpsPoint.velocity
    formData.accuracy = props.gpsPoint.accuracy
    
    // Store original location for reference
    originalLocation.value = {
      lat: props.gpsPoint.coordinates.lat,
      lng: props.gpsPoint.coordinates.lng
    }
  }
  
  // Clear errors
  errors.lat = ''
  errors.lng = ''
}

const validateForm = () => {
  errors.lat = ''
  errors.lng = ''
  
  if (formData.coordinates.lat === null || formData.coordinates.lat === undefined) {
    errors.lat = 'Latitude is required'
  } else if (formData.coordinates.lat < -90 || formData.coordinates.lat > 90) {
    errors.lat = 'Latitude must be between -90 and 90'
  }
  
  if (formData.coordinates.lng === null || formData.coordinates.lng === undefined) {
    errors.lng = 'Longitude is required'
  } else if (formData.coordinates.lng < -180 || formData.coordinates.lng > 180) {
    errors.lng = 'Longitude must be between -180 and 180'
  }
  
  return !errors.lat && !errors.lng
}

const hasValidCoordinates = (lat, lng) => {
  return Number.isFinite(lat)
    && Number.isFinite(lng)
    && lat >= -90
    && lat <= 90
    && lng >= -180
    && lng <= 180
}

const hasDifferentLocation = (left, right) => {
  if (!left || !right) {
    return false
  }

  if (!hasValidCoordinates(left.lat, left.lng) || !hasValidCoordinates(right.lat, right.lng)) {
    return false
  }

  return Math.abs(left.lat - right.lat) > 0.000001 || Math.abs(left.lng - right.lng) > 0.000001
}

const createRasterGpsEditMapAdapter = (map) => {
  let currentMarker = null
  let originalMarker = null

  const removeMarker = (marker) => {
    if (!marker) {
      return null
    }
    map.removeLayer(marker)
    return null
  }

  const clear = () => {
    currentMarker = removeMarker(currentMarker)
    originalMarker = removeMarker(originalMarker)
  }

  const render = ({ currentLocation, originalLocationRef }) => {
    clear()

    if (hasValidCoordinates(currentLocation?.lat, currentLocation?.lng)) {
      currentMarker = L.circleMarker([currentLocation.lat, currentLocation.lng], {
        radius: 14,
        fillColor: '#ff0040',
        color: '#ffffff',
        weight: 5,
        opacity: 1,
        fillOpacity: 1
      })
        .addTo(map)
        .bindPopup('Current Location - Click on map to move')
    }

    if (hasDifferentLocation(originalLocationRef, currentLocation)) {
      originalMarker = L.circleMarker([originalLocationRef.lat, originalLocationRef.lng], {
        radius: 6,
        fillColor: '#ef4444',
        color: 'white',
        weight: 2,
        opacity: 0.9,
        fillOpacity: 0.7
      })
        .addTo(map)
        .bindPopup('Original Location')
    }
  }

  return {
    render,
    cleanup: clear
  }
}

const createVectorMarkerElement = (variant) => {
  const root = document.createElement('div')
  root.className = `gps-edit-marker gps-edit-marker--${variant}`
  return root
}

const createVectorGpsEditMapAdapter = (map) => {
  let currentMarker = null
  let originalMarker = null

  const removeMarker = (marker) => {
    if (marker) {
      marker.remove()
    }
    return null
  }

  const clear = () => {
    currentMarker = removeMarker(currentMarker)
    originalMarker = removeMarker(originalMarker)
  }

  const render = ({ currentLocation, originalLocationRef }) => {
    clear()

    if (hasValidCoordinates(currentLocation?.lat, currentLocation?.lng)) {
      currentMarker = new maplibregl.Marker({
        element: createVectorMarkerElement('current'),
        anchor: 'center'
      })
        .setLngLat([currentLocation.lng, currentLocation.lat])
        .setPopup(new maplibregl.Popup({
          closeButton: true,
          closeOnClick: true,
          closeOnMove: false,
          offset: 12
        }).setText('Current Location - Click on map to move'))
        .addTo(map)
    }

    if (hasDifferentLocation(originalLocationRef, currentLocation)) {
      originalMarker = new maplibregl.Marker({
        element: createVectorMarkerElement('original'),
        anchor: 'center'
      })
        .setLngLat([originalLocationRef.lng, originalLocationRef.lat])
        .setPopup(new maplibregl.Popup({
          closeButton: true,
          closeOnClick: true,
          closeOnMove: false,
          offset: 10
        }).setText('Original Location'))
        .addTo(map)
    }
  }

  return {
    render,
    cleanup: clear
  }
}

const createGpsEditMapAdapter = (map) => {
  const mode = resolveMapEngineModeFromInstance(map, MAP_RENDER_MODES.RASTER)
  if (mode === MAP_RENDER_MODES.VECTOR) {
    return createVectorGpsEditMapAdapter(map)
  }

  return createRasterGpsEditMapAdapter(map)
}

const renderMapMarkers = () => {
  if (!mapInstance.value || !mapAdapter.value) {
    return
  }

  mapAdapter.value.render({
    currentLocation: {
      lat: Number(formData.coordinates.lat),
      lng: Number(formData.coordinates.lng)
    },
    originalLocationRef: originalLocation.value
  })
}

const syncMapViewAndMarkers = () => {
  if (!mapInstance.value || !mapAdapter.value) {
    return
  }

  const lat = Number(formData.coordinates.lat)
  const lng = Number(formData.coordinates.lng)
  if (!hasValidCoordinates(lat, lng)) {
    mapAdapter.value.cleanup()
    return
  }

  mapInstance.value.setView([lat, lng], 16)
  renderMapMarkers()
}

const handleSave = async () => {
  if (!validateForm()) return
  
  loading.value = true
  
  try {
    const updateData = {
      coordinates: {
        lat: formData.coordinates.lat,
        lng: formData.coordinates.lng
      },
      velocity: formData.velocity,
      accuracy: formData.accuracy
    }
    
    emit('save', updateData)
  } catch (error) {
    console.error('Error saving GPS point:', error)
  } finally {
    loading.value = false
  }
}

const handleCancel = () => {
  emit('close')
}

const handleMapReady = (map) => {
  mapInstance.value = map

  if (mapAdapter.value) {
    mapAdapter.value.cleanup()
  }

  mapAdapter.value = createGpsEditMapAdapter(mapInstance.value)
  syncMapViewAndMarkers()
}

const handleMapClick = (event) => {
  if (!event?.latlng) {
    return
  }

  const { lat, lng } = event.latlng
  
  // Update form data
  formData.coordinates.lat = parseFloat(lat.toFixed(6))
  formData.coordinates.lng = parseFloat(lng.toFixed(6))

  // Update markers
  nextTick(() => {
    renderMapMarkers()
  })
}

const updateMapMarker = () => {
  if (hasValidCoordinates(Number(formData.coordinates.lat), Number(formData.coordinates.lng))) {
    // Update map center and markers when coordinates change
    nextTick(() => {
      if (mapInstance.value) {
        syncMapViewAndMarkers()
      }
    })
  } else {
    mapAdapter.value?.cleanup?.()
  }
}

// Watchers
watch(() => props.visible, (val) => {
  internalVisible.value = val
  if (val) {
    initializeForm()
    if (mapInstance.value && mapAdapter.value) {
      nextTick(() => {
        syncMapViewAndMarkers()
      })
    }
  }
})

watch(() => props.gpsPoint, () => {
  if (props.visible) {
    initializeForm()
  }
})

watch(internalVisible, (val) => {
  if (!val) {
    emit('close')
  }
})

onUnmounted(() => {
  mapAdapter.value?.cleanup?.()
  mapAdapter.value = null
})
</script>

<script>
export default {
  name: 'GpsPointEditDialog'
}
</script>

<style scoped>
.edit-dialog-content {
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: var(--gp-spacing-lg);
}

.map-section {
  padding: 0;
  border-bottom: 1px solid var(--gp-border-light);
  padding-bottom: var(--gp-spacing-lg);
}

.map-instructions {
  margin-bottom: var(--gp-spacing-md);
  color: var(--gp-text-secondary);
  font-size: 0.9rem;
  text-align: center;
  padding: 0 var(--gp-spacing-md);
}

.form-section {
  padding: 0 var(--gp-spacing-md) var(--gp-spacing-md);
}

.gps-edit-form {
  display: flex;
  flex-direction: column;
  gap: var(--gp-spacing-lg);
}

.field {
  display: flex;
  flex-direction: column;
  gap: var(--gp-spacing-sm);
}

.field-group {
  display: flex;
  flex-direction: column;
  gap: var(--gp-spacing-sm);
}

.field-label {
  font-weight: 600;
  color: var(--gp-text-primary);
  font-size: 0.9rem;
}

.location-fields {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--gp-spacing-md);
}

.location-input {
  width: 100%;
}



/* Map container border radius */
:deep(.map-container) {
  border-radius: var(--gp-radius-medium);
  overflow: hidden;
}

/* Mobile responsiveness */
@media (max-width: 768px) {
  :deep(.p-dialog) {
    width: 95vw !important;
    margin: 1rem;
  }
  
  .location-fields {
    grid-template-columns: 1fr;
    gap: var(--gp-spacing-sm);
  }
  
  .map-selection-content {
    height: 300px;
  }
}

/* Dark mode */
.p-dark .field-label {
  color: var(--gp-text-primary);
}

.p-dark .map-instructions {
  color: var(--gp-text-secondary);
}
</style>

<style>
.gps-edit-marker {
  border-radius: 999px;
  box-sizing: border-box;
  cursor: pointer;
}

.gps-edit-marker--current {
  width: 28px;
  height: 28px;
  background: #ff0040;
  border: 5px solid #ffffff;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.28);
}

.gps-edit-marker--original {
  width: 12px;
  height: 12px;
  background: #ef4444;
  border: 2px solid #ffffff;
  opacity: 0.9;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.22);
}
</style>
