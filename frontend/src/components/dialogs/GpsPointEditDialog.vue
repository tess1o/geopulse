<template>
  <Dialog
    v-model:visible="internalVisible"
    :header="'Edit GPS Point'"
    :modal="true"
    :style="{ width: '50vw', minWidth: '400px' }"
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
import { ref, computed, watch, reactive, nextTick } from 'vue'
import Dialog from 'primevue/dialog'
import Button from 'primevue/button'
import InputNumber from 'primevue/inputnumber'
import { MapContainer } from '@/components/maps'
import L from 'leaflet'

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
const currentMarker = ref(null)
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
  if (formData.coordinates.lat && formData.coordinates.lng) {
    return [formData.coordinates.lat, formData.coordinates.lng]
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
  console.log('Map ready:', map)
  mapInstance.value = map
  
  // Set initial view and markers
  if (formData.coordinates.lat && formData.coordinates.lng) {
    setTimeout(() => {
      console.log('Setting initial view and markers')
      map.setView([formData.coordinates.lat, formData.coordinates.lng], 16)
      
      // Add markers after a short delay to ensure map is fully loaded
      setTimeout(() => {
        addMarkers()
      }, 200)
    }, 100)
  }
}

const addMarkers = () => {
  if (!mapInstance.value) {
    console.log('Map instance not available')
    return
  }
  
  if (!L) {
    console.log('Leaflet not available')
    return
  }
  
  console.log('Adding markers to map', formData.coordinates)
  
  // Remove existing current marker
  if (currentMarker.value) {
    mapInstance.value.removeLayer(currentMarker.value)
    currentMarker.value = null
  }
  
  // Remove all existing markers with our custom class
  mapInstance.value.eachLayer((layer) => {
    if (layer.options && (layer.options.isEditMarker || layer.options.isOriginalMarker)) {
      mapInstance.value.removeLayer(layer)
    }
  })
  
  // Add current location marker using a prominent marker style
  if (formData.coordinates.lat && formData.coordinates.lng) {
    // Create a prominent marker with pulsing effect
    currentMarker.value = L.circleMarker([formData.coordinates.lat, formData.coordinates.lng], {
      radius: 14,
      fillColor: '#ff0040',
      color: '#ffffff',
      weight: 5,
      opacity: 1,
      fillOpacity: 1,
      isEditMarker: true
    })
      .addTo(mapInstance.value)
      .bindPopup('Current Location - Click on map to move')
      
    console.log('Added current location marker at:', [formData.coordinates.lat, formData.coordinates.lng])
  }
  
  // Add original location marker for reference (if different)
  if (originalLocation.value && 
      (Math.abs(originalLocation.value.lat - formData.coordinates.lat) > 0.000001 ||
       Math.abs(originalLocation.value.lng - formData.coordinates.lng) > 0.000001)) {
    
    L.circleMarker([originalLocation.value.lat, originalLocation.value.lng], {
      radius: 6,
      fillColor: '#ef4444',
      color: 'white',
      weight: 2,
      opacity: 0.9,
      fillOpacity: 0.7,
      isOriginalMarker: true
    })
      .addTo(mapInstance.value)
      .bindPopup('Original Location')
      
    console.log('Added original location marker at:', [originalLocation.value.lat, originalLocation.value.lng])
  }
}

const handleMapClick = (event) => {
  console.log('Map clicked:', event.latlng)
  const { lat, lng } = event.latlng
  
  // Update form data
  formData.coordinates.lat = parseFloat(lat.toFixed(6))
  formData.coordinates.lng = parseFloat(lng.toFixed(6))
  
  console.log('Updated form data:', formData.coordinates)
  
  // Update markers
  nextTick(() => {
    addMarkers()
  })
}

const updateMapMarker = () => {
  if (formData.coordinates.lat && formData.coordinates.lng) {
    // Update map center and markers when coordinates change
    nextTick(() => {
      if (mapInstance.value) {
        mapInstance.value.setView([formData.coordinates.lat, formData.coordinates.lng], 16)
        addMarkers()
      }
    })
  }
}

// Watchers
watch(() => props.visible, (val) => {
  internalVisible.value = val
  if (val) {
    initializeForm()
    // If map is already ready, add markers
    if (mapInstance.value && formData.coordinates.lat && formData.coordinates.lng) {
      setTimeout(() => {
        mapInstance.value.setView([formData.coordinates.lat, formData.coordinates.lng], 16)
        setTimeout(() => {
          addMarkers()
        }, 200)
      }, 100)
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